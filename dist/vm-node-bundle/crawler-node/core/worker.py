"""
Worker节点
"""
import threading
import time
import psutil
import uuid
from typing import Optional, Dict, Any, List
from datetime import datetime
from queue import Queue, Empty
from threading import Lock

from config import config
from core.logger import log
from core.spider.zhaopin_spider import ZhaopinSpider
from mq.producer import MQProducer
from utils.http_client import HttpClient


class WorkerNode:
    """Worker节点"""

    def __init__(self):
        self.node_id = config.NODE_ID
        self.node_name = config.NODE_NAME
        self.current_tasks: List[str] = []  # 当前正在执行的任务分片ID
        self.task_queue = Queue(maxsize=config.MAX_CONCURRENT_TASKS * 2)
        self.running = False
        self.worker_threads: List[threading.Thread] = []
        self.incremental_state_lock = Lock()
        self.incremental_group_state: Dict[tuple, Dict[str, Any]] = {}

        # 组件
        self.http_client = HttpClient()
        self.mq_producer = MQProducer()
        self.spider = ZhaopinSpider(self.http_client)

        # 统计信息
        self.stats = {
            "total_tasks": 0,
            "completed_tasks": 0,
            "failed_tasks": 0,
            "total_data": 0,
            "start_time": datetime.now()
        }

        log.info(f"Worker节点初始化: {self.node_id}")

    def start(self):
        """启动Worker"""
        if self.running:
            log.warning("Worker已经在运行中")
            return

        self.running = True

        # 启动工作线程
        for i in range(config.MAX_CONCURRENT_TASKS):
            thread = threading.Thread(
                target=self._worker_loop,
                name=f"WorkerThread-{i}",
                daemon=True
            )
            thread.start()
            self.worker_threads.append(thread)

        log.info(f"Worker已启动，工作线程数: {config.MAX_CONCURRENT_TASKS}")

    def shutdown(self):
        """关闭Worker"""
        self.running = False

        # 等待工作线程结束
        for thread in self.worker_threads:
            if thread.is_alive():
                thread.join(timeout=5)

        # 关闭组件
        self.http_client.close()
        self.mq_producer.close()

        log.info("Worker已关闭")

    def add_task(self, task_data: Dict[str, Any]):
        """添加任务到队列"""
        try:
            self.task_queue.put(task_data, block=True, timeout=5)
            log.debug(f"任务已添加到队列: {task_data.get('shard_id')}")
        except Exception as e:
            log.error(f"添加任务到队列失败: {e}")
            raise

    def get_system_stats(self) -> Dict[str, Any]:
        """获取系统统计信息"""
        try:
            # CPU使用率
            cpu_percent = psutil.cpu_percent(interval=1)

            # 内存使用率
            memory = psutil.virtual_memory()
            memory_percent = memory.percent

            # 磁盘使用率
            disk = psutil.disk_usage('/')
            disk_percent = disk.percent

            # 网络IO
            net_io = psutil.net_io_counters()

            return {
                "cpu_usage": cpu_percent,
                "memory_usage": memory_percent,
                "disk_usage": disk_percent,
                "bytes_sent": net_io.bytes_sent,
                "bytes_recv": net_io.bytes_recv,
                "current_tasks": len(self.current_tasks),
                "queue_size": self.task_queue.qsize(),
                "total_tasks": self.stats["total_tasks"],
                "completed_tasks": self.stats["completed_tasks"],
                "failed_tasks": self.stats["failed_tasks"],
                "total_data": self.stats["total_data"],
                "uptime": (datetime.now() - self.stats["start_time"]).total_seconds()
            }

        except Exception as e:
            log.error(f"获取系统统计失败: {e}")
            return {}

    def _worker_loop(self):
        """工作线程循环"""
        thread_name = threading.current_thread().name
        log.debug(f"工作线程启动: {thread_name}")

        while self.running:
            try:
                # 从队列获取任务
                task_data = self.task_queue.get(timeout=1)
                if task_data is None:  # 退出信号
                    break

                # 处理任务
                self._process_task(task_data)

                # 标记任务完成
                self.task_queue.task_done()

            except Empty:
                continue  # 队列为空，继续等待
            except Exception as e:
                log.error(f"工作线程异常: {e}")
                time.sleep(1)

        log.debug(f"工作线程结束: {thread_name}")

    def _process_task(self, task_data: Dict[str, Any]):
        """处理任务"""
        shard_id = task_data.get("shard_id")
        task_id = task_data.get("task_id")
        keyword = task_data.get("keyword")
        city = task_data.get("city")
        category_code = task_data.get("category_code")
        page = task_data.get("page", 1)
        incremental = bool(task_data.get("incremental", False))
        watermark_publish_date = self._parse_datetime_value(task_data.get("watermark_publish_date"))
        stale_page_threshold = int(task_data.get("stale_page_threshold", 2) or 2)
        lookback_hours = int(task_data.get("lookback_hours", 72) or 72)
        search_keyword = task_data.get("search_keyword") or keyword
        group_key = (task_id, keyword, city, category_code)

        log.info(f"开始处理任务分片: shard_id={shard_id}, keyword={keyword}, city={city}, page={page}")

        try:
            # 添加到当前任务列表
            self.current_tasks.append(shard_id)

            # 更新统计
            self.stats["total_tasks"] += 1

            if incremental and self._should_skip_group(group_key):
                stop_reason = self._get_group_stop_reason(group_key) or "ALL_OLD_PUBLISH_DATE"
                self._send_task_result(shard_id, task_id, "completed", 0, stop_reason=stop_reason)
                self.stats["completed_tasks"] += 1
                log.info(f"跳过增量分片: shard_id={shard_id}, stop_reason={stop_reason}")
                return

            # 执行爬虫任务
            job_data_list = self.spider.crawl(
                keyword=keyword,
                city=city,
                category_code=category_code,
                page=page,
                search_keyword=search_keyword,
            )

            stop_reason = None
            new_count = 0
            updated_count = 0
            duplicate_count = 0

            if incremental:
                page_state = self._evaluate_incremental_page(
                    group_key=group_key,
                    watermark_publish_date=watermark_publish_date,
                    lookback_hours=lookback_hours,
                    stale_page_threshold=stale_page_threshold,
                    job_data_list=job_data_list,
                )
                stop_reason = page_state.get("stop_reason")
                new_count = page_state.get("new_count", 0)
                updated_count = page_state.get("updated_count", 0)
                duplicate_count = page_state.get("duplicate_count", 0)

            # 发送采集到的数据
            for job_data in job_data_list:
                # 添加任务ID
                job_data["task_id"] = task_id
                job_data["shard_id"] = shard_id
                if watermark_publish_date and job_data.get("publish_date") and job_data["publish_date"] > watermark_publish_date:
                    job_data["is_new_candidate"] = True
                    job_data["is_update_candidate"] = False
                    job_data["is_old_candidate"] = False
                else:
                    job_data["is_new_candidate"] = False
                    job_data["is_update_candidate"] = False
                    job_data["is_old_candidate"] = True

                # 发送到消息队列
                self.mq_producer.send_job_data(job_data)

                # 更新统计
                self.stats["total_data"] += 1

            # 发送任务完成消息
            self._send_task_result(
                shard_id,
                task_id,
                "completed",
                len(job_data_list),
                stop_reason=stop_reason,
                new_count=new_count,
                updated_count=updated_count,
                duplicate_count=duplicate_count,
            )

            # 更新统计
            self.stats["completed_tasks"] += 1

            log.info(f"任务分片完成: shard_id={shard_id}, 采集数据{len(job_data_list)}条")

        except Exception as e:
            log.error(f"处理任务分片失败: shard_id={shard_id}, error={e}")

            # 发送任务失败消息
            self._send_task_result(shard_id, task_id, "failed", 0, str(e))

            # 更新统计
            self.stats["failed_tasks"] += 1

        finally:
            # 从当前任务列表中移除
            if shard_id in self.current_tasks:
                self.current_tasks.remove(shard_id)

    def _send_task_result(self, shard_id: str, task_id: str, status: str, data_count: int,
                          error_msg: str = "", stop_reason: str = "",
                          new_count: int = 0, updated_count: int = 0, duplicate_count: int = 0):
        """发送任务结果"""
        try:
            message = {
                "shard_id": shard_id,
                "task_id": task_id,
                "status": 2 if status == "completed" else 3,  # 2=完成, 3=失败
                "worker_id": self.node_id,
                "data_count": data_count,
                "error_message": error_msg,
                "stop_reason": stop_reason,
                "new_count": new_count,
                "updated_count": updated_count,
                "duplicate_count": duplicate_count,
                "timestamp": datetime.now().isoformat()
            }

            self.mq_producer.send_task_result(message)
            log.debug(f"任务结果已发送: shard_id={shard_id}, status={status}")

        except Exception as e:
            log.error(f"发送任务结果失败: {e}")

    def _evaluate_incremental_page(self, group_key: tuple, watermark_publish_date: Optional[datetime],
                                   lookback_hours: int, stale_page_threshold: int,
                                   job_data_list: List[Dict[str, Any]]) -> Dict[str, Any]:
        now = datetime.now()
        state = self._get_group_state(group_key)
        stop_reason = None
        new_count = 0
        duplicate_count = len(job_data_list)

        if not job_data_list:
            state["stale_pages"] += 1
            stop_reason = "NO_RESULTS"
        else:
            old_publish_dates = []
            for job_data in job_data_list:
                publish_date = job_data.get("publish_date")
                is_new_candidate = False
                if publish_date and watermark_publish_date and publish_date > watermark_publish_date:
                    is_new_candidate = True
                elif publish_date is None:
                    is_new_candidate = False
                else:
                    old_publish_dates.append(publish_date)

                if is_new_candidate:
                    new_count += 1

            duplicate_count = max(len(job_data_list) - new_count, 0)

            all_old_publish_date = bool(job_data_list) and watermark_publish_date is not None and len(old_publish_dates) == len(job_data_list)
            all_outside_lookback = bool(old_publish_dates) and all(
                (now - publish_date).total_seconds() > lookback_hours * 3600
                for publish_date in old_publish_dates
            )

            if new_count > 0:
                state["stale_pages"] = 0
            elif all_old_publish_date or all_outside_lookback:
                state["stale_pages"] += 1
                stop_reason = "LOOKBACK_EXCEEDED" if all_outside_lookback else "ALL_OLD_PUBLISH_DATE"
            else:
                state["stale_pages"] = 0

        if state["stale_pages"] >= stale_page_threshold:
            state["stopped"] = True
            state["stop_reason"] = stop_reason or "ALL_OLD_PUBLISH_DATE"
            stop_reason = state["stop_reason"]

        return {
            "stop_reason": stop_reason,
            "new_count": new_count,
            "updated_count": 0,
            "duplicate_count": duplicate_count,
        }

    def _get_group_state(self, group_key: tuple) -> Dict[str, Any]:
        with self.incremental_state_lock:
            if group_key not in self.incremental_group_state:
                self.incremental_group_state[group_key] = {
                    "stale_pages": 0,
                    "stopped": False,
                    "stop_reason": "",
                }
            return self.incremental_group_state[group_key]

    def _should_skip_group(self, group_key: tuple) -> bool:
        with self.incremental_state_lock:
            state = self.incremental_group_state.get(group_key, {})
            return bool(state.get("stopped"))

    def _get_group_stop_reason(self, group_key: tuple) -> str:
        with self.incremental_state_lock:
            state = self.incremental_group_state.get(group_key, {})
            return str(state.get("stop_reason", ""))

    def _parse_datetime_value(self, value: Any) -> Optional[datetime]:
        if isinstance(value, datetime):
            return value
        if not value:
            return None
        for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%dT%H:%M:%S", "%Y-%m-%dT%H:%M:%S.%f"):
            try:
                return datetime.strptime(str(value), fmt)
            except ValueError:
                continue
        return None
