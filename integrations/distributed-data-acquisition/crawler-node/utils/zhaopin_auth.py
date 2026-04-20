"""
智联招聘鉴权读取与刷新工具。
"""
from __future__ import annotations

import json
import os
import sys
import threading
import time
import uuid
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, Optional
from urllib.parse import parse_qs, urlparse

import requests
from dotenv import set_key
from selenium import webdriver
from selenium.common.exceptions import WebDriverException
from selenium.webdriver.edge.options import Options as EdgeOptions
from selenium.webdriver.edge.service import Service as EdgeService
from webdriver_manager.microsoft import EdgeChromiumDriverManager

from config import config
from core.logger import log


AUTH_CONFIG_KEYS = {
    "token_mme": "zhaopin.token_mme",
    "token_c1k": "zhaopin.token_c1k",
    "cookie": "zhaopin.cookie",
    "page_request_id": "zhaopin.page_request_id",
    "request_id": "zhaopin.request_id",
    "client_id": "zhaopin.client_id",
    "auth_updated_at": "zhaopin.auth_updated_at",
    "auth_status": "zhaopin.auth_status",
}

DEFAULT_TOKEN_MME = "MmEwMD="
DEFAULT_TOKEN_C1K = "c1K5tw0w6_="


@dataclass
class ZhaopinAuthSnapshot:
    token_mme: str = ""
    token_c1k: str = ""
    cookie: str = ""
    page_request_id: str = ""
    request_id: str = ""
    client_id: str = ""
    updated_at: str = ""
    status: str = "unknown"
    source: str = "unknown"
    message: str = ""
    needs_manual_intervention: bool = False
    raw: Dict[str, Any] = field(default_factory=dict)

    def is_usable(self) -> bool:
        return bool(self.cookie.strip()) and bool(self.token_mme.strip()) and bool(self.token_c1k.strip())

    def is_default(self) -> bool:
        return (
            self.token_mme.strip() in ("", DEFAULT_TOKEN_MME)
            or self.token_c1k.strip() in ("", DEFAULT_TOKEN_C1K)
            or not self.cookie.strip()
        )

    def to_config_payload(self) -> Dict[str, str]:
        now = self.updated_at or time.strftime("%Y-%m-%d %H:%M:%S")
        return {
            AUTH_CONFIG_KEYS["token_mme"]: self.token_mme,
            AUTH_CONFIG_KEYS["token_c1k"]: self.token_c1k,
            AUTH_CONFIG_KEYS["cookie"]: self.cookie,
            AUTH_CONFIG_KEYS["page_request_id"]: self.page_request_id,
            AUTH_CONFIG_KEYS["request_id"]: self.request_id,
            AUTH_CONFIG_KEYS["client_id"]: self.client_id,
            AUTH_CONFIG_KEYS["auth_updated_at"]: now,
            AUTH_CONFIG_KEYS["auth_status"]: self.status or "active",
        }


class SchedulerConfigClient:
    """读取/触发调度中心中的智联鉴权配置。"""

    def __init__(self, timeout: int = 15):
        self.base_url = config.scheduler_base_url.rstrip("/")
        self.timeout = timeout

    def fetch_channel_configs(self) -> Optional[Dict[str, str]]:
        url = f"{self.base_url}/config/channel/zhaopin"
        try:
            response = requests.get(url, timeout=self.timeout)
            response.raise_for_status()
            payload = response.json()
            data = payload.get("data")
            if isinstance(data, dict):
                return data
        except Exception as exc:
            log.warning(f"从调度中心读取智联配置失败: {exc}")
        return None

    def trigger_refresh(self, reason: str, persist_env: bool = True) -> Optional[Dict[str, Any]]:
        url = f"{self.base_url}/config/zhaopin/auth/refresh"
        try:
            response = requests.post(
                url,
                json={
                    "reason": reason,
                    "triggered_by": config.NODE_ID,
                    "persist_env": persist_env,
                },
                timeout=max(self.timeout, 60),
            )
            response.raise_for_status()
            payload = response.json()
            if payload.get("code") == 200:
                return payload.get("data")
            log.warning(f"调度中心刷新智联鉴权失败: {payload}")
        except Exception as exc:
            log.warning(f"调用调度中心刷新智联鉴权失败: {exc}")
        return None


class LocalEnvWriter:
    """将智联鉴权字段回写到项目根目录 .env。"""

    def __init__(self):
        self.dotenv_path = Path(__file__).resolve().parents[2] / ".env"

    def persist(self, snapshot: ZhaopinAuthSnapshot) -> None:
        if not self.dotenv_path.exists():
            self.dotenv_path.touch()
        env_mapping = {
            "ZHAOPIN_TOKEN_MME": snapshot.token_mme,
            "ZHAOPIN_TOKEN_C1K": snapshot.token_c1k,
            "ZHAOPIN_COOKIE": snapshot.cookie,
            "ZHAOPIN_PAGE_REQUEST_ID": snapshot.page_request_id,
            "ZHAOPIN_REQUEST_ID": snapshot.request_id,
            "ZHAOPIN_CLIENT_ID": snapshot.client_id,
            "ZHAOPIN_AUTH_UPDATED_AT": snapshot.updated_at or time.strftime("%Y-%m-%d %H:%M:%S"),
            "ZHAOPIN_AUTH_STATUS": snapshot.status or "active",
        }
        for key, value in env_mapping.items():
            set_key(str(self.dotenv_path), key, value or "")


class EdgeAuthRefresher:
    """使用 Edge 浏览器自动提取智联鉴权字段。"""

    def __init__(self):
        self.login_url = os.getenv("ZHAOPIN_AUTH_URL", "https://sou.zhaopin.com/?jl=530&kw=Python")
        self.headless = os.getenv("ZHAOPIN_AUTH_HEADLESS", "false").lower() == "true"
        self.keep_browser_open = os.getenv("ZHAOPIN_AUTH_KEEP_BROWSER_OPEN", "true").lower() == "true"
        default_user_data_dir = os.path.join(
            os.environ.get("LOCALAPPDATA", ""),
            "Microsoft",
            "Edge",
            "User Data",
        )
        self.use_profile = os.getenv("ZHAOPIN_AUTH_EDGE_USE_PROFILE", "true").lower() == "true"
        self.user_data_dir = os.getenv("ZHAOPIN_AUTH_EDGE_USER_DATA_DIR", default_user_data_dir).strip()
        self.profile_dir = os.getenv("ZHAOPIN_AUTH_EDGE_PROFILE_DIR", "Default").strip()
        self._performance_log_warning_emitted = False

    def refresh(self, timeout_seconds: int = 180) -> ZhaopinAuthSnapshot:
        driver = None
        started_at = time.time()
        keep_window_open = False
        try:
            driver = self._build_driver()
            driver.get(self.login_url)
            time.sleep(5)

            last_snapshot = ZhaopinAuthSnapshot(
                status="pending_manual_login",
                source="edge",
                updated_at=self._now_str(),
                message="等待浏览器登录态可用",
                needs_manual_intervention=True,
            )
            while time.time() - started_at < timeout_seconds:
                snapshot = self._extract_snapshot(driver)
                if snapshot.is_usable():
                    snapshot.status = "active"
                    snapshot.source = "edge"
                    snapshot.updated_at = self._now_str()
                    snapshot.message = "已从 Edge 浏览器提取智联鉴权信息"
                    snapshot.needs_manual_intervention = False
                    keep_window_open = self.keep_browser_open and not self.headless
                    if keep_window_open:
                        log.info("已提取到智联鉴权信息，按配置保留 Edge 浏览器窗口，便于继续保持登录态")
                    return snapshot
                last_snapshot = snapshot
                log.info("等待智联登录态可用，请在 Edge 浏览器中完成登录或验证码验证")
                time.sleep(3)

            last_snapshot.status = "timeout"
            last_snapshot.updated_at = self._now_str()
            last_snapshot.message = "等待人工登录超时"
            last_snapshot.needs_manual_intervention = True
            return last_snapshot
        except Exception as exc:
            log.error(f"使用 Edge 刷新智联鉴权失败: {exc}")
            return ZhaopinAuthSnapshot(
                status="failed",
                source="edge",
                updated_at=self._now_str(),
                message=str(exc),
                needs_manual_intervention=False,
            )
        finally:
            if driver is not None and not keep_window_open:
                try:
                    driver.quit()
                except Exception:
                    pass

    def _build_driver(self) -> webdriver.Edge:
        options = EdgeOptions()
        options.use_chromium = True
        options.add_argument("--disable-blink-features=AutomationControlled")
        options.add_argument("--start-maximized")
        if self.headless:
            options.add_argument("--headless=new")
        options.add_experimental_option("excludeSwitches", ["enable-automation"])
        options.add_experimental_option("useAutomationExtension", False)
        if self.keep_browser_open and not self.headless:
            options.add_experimental_option("detach", True)
        options.set_capability("goog:loggingPrefs", {"performance": "ALL"})
        if self.use_profile and self.user_data_dir and os.path.isdir(self.user_data_dir):
            options.add_argument(f"--user-data-dir={self.user_data_dir}")
            if self.profile_dir:
                options.add_argument(f"--profile-directory={self.profile_dir}")
        edge_binary = os.getenv("EDGE_BINARY_PATH", "").strip()
        if edge_binary:
            options.binary_location = edge_binary

        edge_driver_path = os.getenv("EDGE_DRIVER_PATH", "").strip()
        if edge_driver_path:
            service = EdgeService(edge_driver_path)
            return webdriver.Edge(service=service, options=options)

        try:
            return webdriver.Edge(options=options)
        except WebDriverException as first_error:
            log.warning(f"直接启动 Edge 失败，尝试使用 webdriver-manager: {first_error}")
            service = EdgeService(EdgeChromiumDriverManager().install())
            return webdriver.Edge(service=service, options=options)

    def _extract_snapshot(self, driver: webdriver.Edge) -> ZhaopinAuthSnapshot:
        cookies = driver.get_cookies()
        cookie_map = {
            str(item.get("name", "")).strip(): str(item.get("value", "")).strip()
            for item in cookies
            if item.get("name")
        }
        cookie_str = "; ".join(
            f"{item.get('name', '').strip()}={item.get('value', '').strip()}"
            for item in cookies
            if item.get("name")
        )
        storages = self._collect_storage(driver)
        network_info = self._collect_network_auth(driver)

        token_mme = (
            network_info.get("token_mme")
            or storages.get("local", {}).get("MmEwMD")
            or storages.get("session", {}).get("MmEwMD")
            or cookie_map.get("at")
            or ""
        )
        token_c1k = (
            network_info.get("token_c1k")
            or storages.get("local", {}).get("c1K5tw0w6_")
            or storages.get("session", {}).get("c1K5tw0w6_")
            or cookie_map.get("rt")
            or ""
        )
        page_request_id = network_info.get("page_request_id") or self._generate_page_request_id()
        request_id = network_info.get("request_id") or uuid.uuid4().hex
        client_id = (
            network_info.get("client_id")
            or storages.get("local", {}).get("x-zp-client-id")
            or storages.get("session", {}).get("x-zp-client-id")
            or cookie_map.get("x-zp-client-id")
            or str(uuid.uuid4())
        )
        return ZhaopinAuthSnapshot(
            token_mme=str(token_mme).strip(),
            token_c1k=str(token_c1k).strip(),
            cookie=cookie_str.strip(),
            page_request_id=str(page_request_id).strip(),
            request_id=str(request_id).strip(),
            client_id=str(client_id).strip(),
            raw={
                "storage": storages,
                "network": network_info,
            },
            needs_manual_intervention=not (cookie_str.strip() and token_mme and token_c1k),
        )

    def _collect_storage(self, driver: webdriver.Edge) -> Dict[str, Dict[str, Any]]:
        script = """
            const localData = {};
            const sessionData = {};
            for (let i = 0; i < window.localStorage.length; i++) {
                const key = window.localStorage.key(i);
                localData[key] = window.localStorage.getItem(key);
            }
            for (let i = 0; i < window.sessionStorage.length; i++) {
                const key = window.sessionStorage.key(i);
                sessionData[key] = window.sessionStorage.getItem(key);
            }
            return { local: localData, session: sessionData };
        """
        try:
            data = driver.execute_script(script)
            if isinstance(data, dict):
                return {
                    "local": data.get("local", {}) if isinstance(data.get("local"), dict) else {},
                    "session": data.get("session", {}) if isinstance(data.get("session"), dict) else {},
                }
        except Exception as exc:
            log.warning(f"读取浏览器 storage 失败: {exc}")
        return {"local": {}, "session": {}}

    def _collect_network_auth(self, driver: webdriver.Edge) -> Dict[str, str]:
        result: Dict[str, str] = {}
        try:
            performance_logs = driver.get_log("performance")
        except Exception as exc:
            if not self._performance_log_warning_emitted:
                log.warning(f"读取 Edge performance 日志失败，将仅使用 storage/cookie 提取鉴权字段: {exc}")
                self._performance_log_warning_emitted = True
            return result

        for entry in performance_logs:
            try:
                message = json.loads(entry.get("message", "{}")).get("message", {})
                method = message.get("method")
                params = message.get("params", {})
                request = params.get("request", {})
                url = str(request.get("url", ""))
                headers = request.get("headers", {}) if isinstance(request.get("headers"), dict) else {}
                if method != "Network.requestWillBeSent":
                    continue
                if "fe-api.zhaopin.com/c/i/search/positions" not in url:
                    continue
                parsed = urlparse(url)
                query = parse_qs(parsed.query)
                if query.get("MmEwMD"):
                    result["token_mme"] = query["MmEwMD"][0]
                if query.get("c1K5tw0w6_"):
                    result["token_c1k"] = query["c1K5tw0w6_"][0]
                if headers.get("x-zp-page-request-id"):
                    result["page_request_id"] = str(headers["x-zp-page-request-id"])
                if headers.get("x-zp-request-id"):
                    result["request_id"] = str(headers["x-zp-request-id"])
                if headers.get("x-zp-client-id"):
                    result["client_id"] = str(headers["x-zp-client-id"])
            except Exception:
                continue
        return result

    def _generate_page_request_id(self) -> str:
        return f"{uuid.uuid4().hex}-{int(time.time() * 1000)}-{uuid.uuid4().hex[:6]}"

    def _now_str(self) -> str:
        return time.strftime("%Y-%m-%d %H:%M:%S")


class ZhaopinAuthManager:
    """crawler 侧统一鉴权读取、缓存与刷新入口。"""

    def __init__(self):
        self.scheduler_client = SchedulerConfigClient(timeout=int(os.getenv("ZHAOPIN_AUTH_HTTP_TIMEOUT", "15")))
        self.env_writer = LocalEnvWriter()
        self.refresher = EdgeAuthRefresher()
        self.cache_ttl = int(os.getenv("ZHAOPIN_AUTH_CACHE_TTL_SECONDS", "120"))
        self.persist_env = os.getenv("ZHAOPIN_AUTH_PERSIST_ENV", "true").lower() == "true"
        self.allow_local_refresh = os.getenv("ZHAOPIN_AUTH_ALLOW_LOCAL_REFRESH", "true").lower() == "true"
        self.refresh_timeout_seconds = int(os.getenv("ZHAOPIN_AUTH_REFRESH_TIMEOUT_SECONDS", "180"))
        self._lock = threading.Lock()
        self._refresh_lock = threading.Lock()
        self._cached_snapshot: Optional[ZhaopinAuthSnapshot] = None
        self._cached_at = 0.0

    def get_snapshot(self, force_refresh: bool = False) -> ZhaopinAuthSnapshot:
        with self._lock:
            if (
                not force_refresh
                and self._cached_snapshot is not None
                and time.time() - self._cached_at < self.cache_ttl
            ):
                return self._cached_snapshot

        snapshot = self._load_from_config_center() or self._load_from_local_env()
        if not snapshot.is_usable():
            log.warning("未读取到可用的智联鉴权快照，将继续使用当前本地配置兜底")
        self._cache_snapshot(snapshot)
        self._apply_runtime_config(snapshot)
        return snapshot

    def get_local_snapshot(self) -> ZhaopinAuthSnapshot:
        snapshot = self._load_from_local_env()
        self._cache_snapshot(snapshot)
        self._apply_runtime_config(snapshot)
        return snapshot

    def refresh_auth(self, reason: str) -> ZhaopinAuthSnapshot:
        with self._refresh_lock:
            log.warning(f"触发智联鉴权刷新: reason={reason}")
            refresh_result = self.scheduler_client.trigger_refresh(reason=reason, persist_env=self.persist_env)
            if refresh_result:
                snapshot = self._snapshot_from_refresh_result(refresh_result, source="scheduler_refresh")
                if snapshot.is_usable():
                    self._cache_snapshot(snapshot)
                    self._apply_runtime_config(snapshot)
                    return snapshot

            if not self.allow_local_refresh:
                snapshot = ZhaopinAuthSnapshot(
                    status="manual_refresh_required",
                    source="config_center_only",
                    updated_at=time.strftime("%Y-%m-%d %H:%M:%S"),
                    message="local browser refresh disabled on this node",
                    needs_manual_intervention=True,
                )
                self._cache_snapshot(snapshot)
                return snapshot

            snapshot = self.refresher.refresh(timeout_seconds=self.refresh_timeout_seconds)
            if snapshot.is_usable():
                if self.persist_env:
                    self.env_writer.persist(snapshot)
                self._cache_snapshot(snapshot)
                self._apply_runtime_config(snapshot)
                return snapshot
            self._cache_snapshot(snapshot)
            return snapshot

    def should_refresh(
        self,
        response: Optional[Any],
        response_data: Optional[Dict[str, Any]],
        empty_result: bool = False,
    ) -> Optional[str]:
        snapshot = self.get_snapshot(force_refresh=False)
        if response is not None and getattr(response, "status_code", None) in (401, 403):
            return f"http_{response.status_code}"
        if isinstance(response_data, dict):
            data = response_data.get("data", {})
            if isinstance(data, dict) and data.get("isVerification") == 1:
                return "is_verification"
            message = str(response_data.get("message", "") or "").lower()
            if "login" in message or "auth" in message or "验证" in message:
                return f"api_message:{response_data.get('message')}"
        if snapshot.is_default():
            return "missing_auth_fields"
        if empty_result and not snapshot.is_usable():
            return "empty_results_with_invalid_auth"
        return None

    def _load_from_config_center(self) -> Optional[ZhaopinAuthSnapshot]:
        configs = self.scheduler_client.fetch_channel_configs()
        if not configs:
            return None
        snapshot = ZhaopinAuthSnapshot(
            token_mme=str(configs.get(AUTH_CONFIG_KEYS["token_mme"], "") or "").strip(),
            token_c1k=str(configs.get(AUTH_CONFIG_KEYS["token_c1k"], "") or "").strip(),
            cookie=str(configs.get(AUTH_CONFIG_KEYS["cookie"], "") or "").strip(),
            page_request_id=str(configs.get(AUTH_CONFIG_KEYS["page_request_id"], "") or "").strip(),
            request_id=str(configs.get(AUTH_CONFIG_KEYS["request_id"], "") or "").strip(),
            client_id=str(configs.get(AUTH_CONFIG_KEYS["client_id"], "") or "").strip(),
            updated_at=str(configs.get(AUTH_CONFIG_KEYS["auth_updated_at"], "") or "").strip(),
            status=str(configs.get(AUTH_CONFIG_KEYS["auth_status"], "") or "active").strip(),
            source="config_center",
        )
        return snapshot

    def _load_from_local_env(self) -> ZhaopinAuthSnapshot:
        return ZhaopinAuthSnapshot(
            token_mme=str(config.ZHAOPIN_TOKEN_MME or "").strip(),
            token_c1k=str(config.ZHAOPIN_TOKEN_C1K or "").strip(),
            cookie=str(config.ZHAOPIN_COOKIE or "").strip(),
            page_request_id=str(config.ZHAOPIN_PAGE_REQUEST_ID or "").strip(),
            request_id=str(config.ZHAOPIN_REQUEST_ID or "").strip(),
            client_id=str(config.ZHAOPIN_CLIENT_ID or "").strip(),
            status="active" if config.ZHAOPIN_COOKIE else "missing",
            source="local_env",
        )

    def _snapshot_from_refresh_result(self, result: Dict[str, Any], source: str) -> ZhaopinAuthSnapshot:
        auth_fields = result.get("auth_fields", {}) if isinstance(result.get("auth_fields"), dict) else {}
        return ZhaopinAuthSnapshot(
            token_mme=str(auth_fields.get("token_mme", "") or "").strip(),
            token_c1k=str(auth_fields.get("token_c1k", "") or "").strip(),
            cookie=str(auth_fields.get("cookie", "") or "").strip(),
            page_request_id=str(auth_fields.get("page_request_id", "") or "").strip(),
            request_id=str(auth_fields.get("request_id", "") or "").strip(),
            client_id=str(auth_fields.get("client_id", "") or "").strip(),
            updated_at=str(result.get("updated_at", "") or "").strip(),
            status=str(result.get("status", "") or "active").strip(),
            source=source,
            message=str(result.get("message", "") or "").strip(),
            needs_manual_intervention=bool(result.get("needs_manual_intervention", False)),
        )

    def _cache_snapshot(self, snapshot: ZhaopinAuthSnapshot) -> None:
        with self._lock:
            self._cached_snapshot = snapshot
            self._cached_at = time.time()

    def _apply_runtime_config(self, snapshot: ZhaopinAuthSnapshot) -> None:
        if snapshot.token_mme:
            config.ZHAOPIN_TOKEN_MME = snapshot.token_mme
        if snapshot.token_c1k:
            config.ZHAOPIN_TOKEN_C1K = snapshot.token_c1k
        if snapshot.cookie:
            config.ZHAOPIN_COOKIE = snapshot.cookie
        if snapshot.page_request_id:
            config.ZHAOPIN_PAGE_REQUEST_ID = snapshot.page_request_id
        if snapshot.request_id:
            config.ZHAOPIN_REQUEST_ID = snapshot.request_id
        if snapshot.client_id:
            config.ZHAOPIN_CLIENT_ID = snapshot.client_id


def run_edge_auth_refresh(
    persist_env: bool = True,
    timeout_seconds: int = 180,
    force_refresh: bool = False,
) -> Dict[str, Any]:
    auth_manager = ZhaopinAuthManager()
    env_writer = LocalEnvWriter()

    if not force_refresh:
        snapshot = auth_manager.get_local_snapshot()
        if snapshot.is_usable():
            return {
                "status": snapshot.status or "active",
                "message": "已复用项目根目录 .env 中的现有智联鉴权信息",
                "updated_at": snapshot.updated_at or time.strftime("%Y-%m-%d %H:%M:%S"),
                "needs_manual_intervention": False,
                "source": snapshot.source or "local_env",
                "auth_fields": {
                    "token_mme": snapshot.token_mme,
                    "token_c1k": snapshot.token_c1k,
                    "cookie": snapshot.cookie,
                    "page_request_id": snapshot.page_request_id,
                    "request_id": snapshot.request_id,
                    "client_id": snapshot.client_id,
                },
            }

    manager = EdgeAuthRefresher()
    snapshot = manager.refresh(timeout_seconds=timeout_seconds)
    if snapshot.is_usable() and persist_env:
        env_writer.persist(snapshot)
    return {
        "status": snapshot.status,
        "message": snapshot.message,
        "updated_at": snapshot.updated_at or time.strftime("%Y-%m-%d %H:%M:%S"),
        "needs_manual_intervention": snapshot.needs_manual_intervention,
        "source": snapshot.source,
        "auth_fields": {
            "token_mme": snapshot.token_mme,
            "token_c1k": snapshot.token_c1k,
            "cookie": snapshot.cookie,
            "page_request_id": snapshot.page_request_id,
            "request_id": snapshot.request_id,
            "client_id": snapshot.client_id,
        },
    }
