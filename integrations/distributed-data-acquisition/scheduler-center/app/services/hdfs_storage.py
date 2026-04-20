"""
HDFS JSONL batch writer for job detail records.
"""
from __future__ import annotations

import json
import os
import threading
from collections import defaultdict
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional
from urllib.parse import quote, urlsplit, urlunsplit

import httpx

from config import settings
from app.core.logger import log


class JobRecordProjector:
    """Project MQ messages into the fixed 21-field HDFS payload."""

    FIELDS = [
        "url",
        "url_obj_id",
        "title",
        "salary_min",
        "salary_max",
        "salary_raw",
        "job_city",
        "experience_year",
        "education_need",
        "publish_date",
        "job_welfare",
        "job_labels",
        "position_info",
        "job_classification",
        "company_name",
        "company_size",
        "company_finance",
        "crawl_time",
        "crawl_update_time",
        "company_logo",
        "task_id",
    ]

    DATETIME_FIELDS = {"publish_date", "crawl_time", "crawl_update_time"}
    INTEGER_FIELDS = {"salary_min", "salary_max"}
    DATETIME_FORMAT = "%Y-%m-%d %H:%M:%S"

    def project(self, message: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        url_obj_id = message.get("url_obj_id")
        if not url_obj_id:
            return None

        record: Dict[str, Any] = {}
        for field in self.FIELDS:
            value = message.get(field)
            if field in self.DATETIME_FIELDS:
                record[field] = self._format_datetime(value, default_now=(field == "crawl_time"))
            elif field in self.INTEGER_FIELDS:
                record[field] = self._normalize_integer(value)
            else:
                record[field] = value

        return record

    def partition_date(self, record: Dict[str, Any]) -> str:
        crawl_time = record.get("crawl_time")
        parsed = self._parse_datetime(crawl_time) if crawl_time else None
        target = parsed or datetime.now()
        return target.strftime("%Y-%m-%d")

    def serialize(self, record: Dict[str, Any]) -> str:
        return json.dumps(record, ensure_ascii=False)

    def _format_datetime(self, value: Any, default_now: bool = False) -> Optional[str]:
        parsed = self._parse_datetime(value)
        if parsed is None and default_now:
            parsed = datetime.now()
        return parsed.strftime(self.DATETIME_FORMAT) if parsed else None

    def _normalize_integer(self, value: Any) -> Optional[int]:
        if value in (None, ""):
            return None
        try:
            return int(value)
        except (TypeError, ValueError):
            log.warning(f"无法将值转换为整数，已写入 null: {value}")
            return None

    def _parse_datetime(self, value: Any) -> Optional[datetime]:
        if isinstance(value, datetime):
            return value
        if value in (None, ""):
            return None
        for fmt in (
            "%Y-%m-%d %H:%M:%S",
            "%Y-%m-%dT%H:%M:%S",
            "%Y-%m-%dT%H:%M:%S.%f",
            "%Y-%m-%d",
        ):
            try:
                return datetime.strptime(str(value), fmt)
            except ValueError:
                continue
        try:
            return datetime.fromisoformat(str(value))
        except ValueError:
            log.warning(f"无法解析时间字段，已写入 null: {value}")
            return None


class WebHdfsClient:
    """Minimal WebHDFS client used by the scheduler center."""

    def __init__(self):
        self.base_url = f"http://{settings.HDFS_HOST}:{settings.HDFS_WEB_PORT}/webhdfs/v1"
        self.user_name = settings.HDFS_USER
        self.timeout = settings.HDFS_HTTP_TIMEOUT_SECONDS
        self.redirect_host_map = self._parse_host_map(settings.HDFS_REDIRECT_HOST_MAP)
        self.client = httpx.Client(timeout=self.timeout, trust_env=False)

    def mkdirs(self, path: str) -> None:
        response = self.client.put(
            self._build_url(path, "MKDIRS"),
            params={"user.name": self.user_name},
        )
        response.raise_for_status()
        payload = response.json()
        if not payload.get("boolean", False):
            raise RuntimeError(f"创建 HDFS 目录失败: {path}")

    def upload_file(self, local_path: Path, remote_path: str, overwrite: bool = False) -> None:
        create_response = self.client.put(
            self._build_url(remote_path, "CREATE"),
            params={
                "user.name": self.user_name,
                "overwrite": str(overwrite).lower(),
            },
            follow_redirects=False,
        )
        if create_response.status_code not in (307, 201):
            create_response.raise_for_status()

        location = create_response.headers.get("location")
        if not location:
            raise RuntimeError(f"WebHDFS CREATE 未返回重定向地址: {remote_path}")
        location = self._rewrite_redirect_location(location)

        with local_path.open("rb") as handle:
            upload_response = self.client.put(
                location,
                content=handle.read(),
                headers={"content-type": "application/octet-stream"},
            )
        upload_response.raise_for_status()

    def close(self) -> None:
        self.client.close()

    def _build_url(self, path: str, op: str) -> str:
        normalized = path if path.startswith("/") else f"/{path}"
        quoted = quote(normalized, safe="/=")
        return f"{self.base_url}{quoted}?op={op}"

    def _parse_host_map(self, raw_value: str) -> Dict[str, str]:
        result: Dict[str, str] = {}
        for item in (raw_value or "").split(","):
            item = item.strip()
            if not item or "=" not in item:
                continue
            hostname, ip = item.split("=", 1)
            hostname = hostname.strip()
            ip = ip.strip()
            if hostname and ip:
                result[hostname] = ip
        return result

    def _rewrite_redirect_location(self, location: str) -> str:
        parts = urlsplit(location)
        hostname = parts.hostname
        if not hostname:
            return location
        mapped = self.redirect_host_map.get(hostname)
        if not mapped:
            return location

        port = f":{parts.port}" if parts.port else ""
        rewritten_netloc = f"{mapped}{port}"
        rewritten = urlunsplit((parts.scheme, rewritten_netloc, parts.path, parts.query, parts.fragment))
        log.info(f"WebHDFS 重定向地址已改写: {hostname} -> {mapped}")
        return rewritten


class HdfsBatchWriter:
    """Buffer job detail records locally and flush them to HDFS as JSONL files."""

    def __init__(self):
        self.enabled = bool(settings.HDFS_ENABLED and settings.HDFS_WRITE_MODE in {"dual_write", "hdfs_only"})
        self.projector = JobRecordProjector()
        self.buffer: List[Dict[str, Any]] = []
        self.lock = threading.Lock()
        self.stop_event = threading.Event()
        self.thread: Optional[threading.Thread] = None
        self.sequence = 0
        self.staging_dir = Path(settings.LOCAL_STAGING_DIR).resolve()
        self.webhdfs = WebHdfsClient() if self.enabled else None
        self.staging_dir.mkdir(parents=True, exist_ok=True)

    def start(self) -> None:
        if not self.enabled:
            log.info("HDFS 双写未启用，跳过 HDFS 批量写入线程")
            return
        if self.thread and self.thread.is_alive():
            return
        self.stop_event.clear()
        self.thread = threading.Thread(target=self._flush_loop, name="HdfsBatchWriterThread", daemon=True)
        self.thread.start()
        log.info(
            "HDFS 批量写入线程已启动: "
            f"host={settings.HDFS_HOST}, web_port={settings.HDFS_WEB_PORT}, "
            f"base_path={settings.HDFS_BASE_PATH}, batch_size={settings.HDFS_WRITE_BATCH_SIZE}, "
            f"flush_interval={settings.HDFS_FLUSH_INTERVAL_SECONDS}s"
        )

    def stop(self) -> None:
        if not self.enabled:
            return
        self.stop_event.set()
        if self.thread and self.thread.is_alive():
            self.thread.join(timeout=5)
        self.flush_pending(reason="shutdown")
        if self.webhdfs:
            self.webhdfs.close()
        log.info("HDFS 批量写入线程已停止")

    def enqueue(self, message: Dict[str, Any]) -> None:
        if not self.enabled:
            return
        record = self.projector.project(message)
        if record is None:
            log.warning("HDFS 写入跳过缺少 url_obj_id 的职位消息")
            return

        batch_to_flush: Optional[List[Dict[str, Any]]] = None
        with self.lock:
            self.buffer.append(record)
            if len(self.buffer) >= settings.HDFS_WRITE_BATCH_SIZE:
                batch_to_flush = self._take_buffer_locked()

        if batch_to_flush:
            self._flush_batch(batch_to_flush, reason="size")

    def flush_pending(self, reason: str = "manual") -> None:
        if not self.enabled:
            return
        batch_to_flush: Optional[List[Dict[str, Any]]] = None
        with self.lock:
            if self.buffer:
                batch_to_flush = self._take_buffer_locked()

        if batch_to_flush:
            self._flush_batch(batch_to_flush, reason=reason)

    def _flush_loop(self) -> None:
        while not self.stop_event.wait(settings.HDFS_FLUSH_INTERVAL_SECONDS):
            try:
                self.flush_pending(reason="interval")
            except Exception as exc:
                log.error(f"HDFS 定时刷盘失败: {exc}")

    def _take_buffer_locked(self) -> List[Dict[str, Any]]:
        batch = self.buffer
        self.buffer = []
        return batch

    def _flush_batch(self, batch: List[Dict[str, Any]], reason: str) -> None:
        grouped: Dict[str, List[Dict[str, Any]]] = defaultdict(list)
        for record in batch:
            grouped[self.projector.partition_date(record)].append(record)

        for partition_date, records in grouped.items():
            self.sequence += 1
            timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
            file_name = f"part-{timestamp}-{self.sequence:03d}.jsonl"
            staging_path = self.staging_dir / file_name
            remote_dir = f"{settings.HDFS_BASE_PATH}/channel={settings.HDFS_CHANNEL}/dt={partition_date}"
            remote_path = f"{remote_dir}/{file_name}"

            self._write_staging_file(staging_path, records)
            try:
                assert self.webhdfs is not None
                self.webhdfs.mkdirs(remote_dir)
                self.webhdfs.upload_file(staging_path, remote_path, overwrite=False)
                staging_path.unlink(missing_ok=True)
                log.info(
                    f"HDFS 批次上传成功: partition={partition_date}, records={len(records)}, "
                    f"reason={reason}, path={remote_path}"
                )
            except Exception as exc:
                log.error(
                    f"HDFS 批次上传失败: partition={partition_date}, records={len(records)}, "
                    f"reason={reason}, staging={staging_path}, target={remote_path}, error={exc}"
                )

    def _write_staging_file(self, path: Path, records: Iterable[Dict[str, Any]]) -> None:
        with path.open("w", encoding="utf-8", newline="\n") as handle:
            for record in records:
                handle.write(self.projector.serialize(record))
                handle.write("\n")
