"""
心跳管理器
"""
import threading
import time
import psutil
from datetime import datetime
from typing import Dict, Any

from config import config
from core.logger import log
from mq.producer import MQProducer


class HeartbeatManager:
    """心跳管理器"""

    def __init__(self, worker):
        self.worker = worker
        self.producer = MQProducer()
        self.running = False
        self.thread = None
        self.interval = config.HEARTBEAT_INTERVAL

    def start(self):
        """启动心跳管理器"""
        if self.thread and self.thread.is_alive():
            log.warning("心跳管理器已经在运行中")
            return

        self.running = True
        self.thread = threading.Thread(
            target=self._heartbeat_loop,
            name="HeartbeatThread",
            daemon=True
        )
        self.thread.start()
        log.info("心跳管理器已启动")

    def stop(self):
        """停止心跳管理器"""
        self.running = False
        if self.thread and self.thread.is_alive():
            self.thread.join(timeout=5)
        log.info("心跳管理器已停止")

    def join(self, timeout=None):
        """等待心跳线程结束"""
        if self.thread and self.thread.is_alive():
            self.thread.join(timeout)

    def is_alive(self):
        """检查心跳线程是否存活"""
        return self.thread is not None and self.thread.is_alive()

    def _heartbeat_loop(self):
        """心跳循环"""
        log.info(f"心跳线程启动，间隔{self.interval}秒")

        while self.running:
            try:
                # 发送心跳
                self.send_heartbeat()

                # 等待间隔时间
                for _ in range(self.interval):
                    if not self.running:
                        break
                    time.sleep(1)

            except Exception as e:
                log.error(f"心跳发送失败: {e}")
                time.sleep(5)  # 发生错误时等待5秒再重试

        log.info("心跳线程结束")

    def send_heartbeat(self):
        """发送心跳"""
        try:
            # 获取系统状态
            system_stats = self._get_system_stats()

            # 构建心跳消息
            heartbeat = {
                "worker_id": config.NODE_ID,
                "node_name": config.NODE_NAME,
                "ip": self._get_ip_address(),
                "cpu_usage": system_stats["cpu_usage"],
                "memory_usage": system_stats["memory_usage"],
                "current_tasks": system_stats["current_tasks"],
                "queue_size": system_stats["queue_size"],
                "total_tasks": system_stats["total_tasks"],
                "completed_tasks": system_stats["completed_tasks"],
                "failed_tasks": system_stats["failed_tasks"],
                "total_data": system_stats["total_data"],
                "status": "ONLINE",
                "timestamp": datetime.now().isoformat()
            }

            # 发送消息
            self.producer.send_heartbeat(heartbeat)

            log.debug(f"心跳发送成功: {config.NODE_ID}")

        except Exception as e:
            log.error(f"发送心跳失败: {e}")
            raise

    def _get_system_stats(self) -> Dict[str, Any]:
        """获取系统统计信息"""
        try:
            # 获取Worker统计
            worker_stats = self.worker.get_system_stats()

            # 如果Worker没有提供统计，使用默认值
            if not worker_stats:
                worker_stats = {
                    "current_tasks": 0,
                    "queue_size": 0,
                    "total_tasks": 0,
                    "completed_tasks": 0,
                    "failed_tasks": 0,
                    "total_data": 0,
                }

            # 获取系统级统计
            cpu_percent = psutil.cpu_percent(interval=1)
            memory = psutil.virtual_memory()
            memory_percent = memory.percent

            return {
                "cpu_usage": cpu_percent,
                "memory_usage": memory_percent,
                "current_tasks": worker_stats.get("current_tasks", 0),
                "queue_size": worker_stats.get("queue_size", 0),
                "total_tasks": worker_stats.get("total_tasks", 0),
                "completed_tasks": worker_stats.get("completed_tasks", 0),
                "failed_tasks": worker_stats.get("failed_tasks", 0),
                "total_data": worker_stats.get("total_data", 0),
            }

        except Exception as e:
            log.error(f"获取系统统计失败: {e}")
            return {
                "cpu_usage": 0,
                "memory_usage": 0,
                "current_tasks": 0,
                "queue_size": 0,
                "total_tasks": 0,
                "completed_tasks": 0,
                "failed_tasks": 0,
                "total_data": 0,
            }

    def _get_ip_address(self) -> str:
        """获取IP地址"""
        try:
            import socket
            # 获取本机IP地址
            hostname = socket.gethostname()
            ip_address = socket.gethostbyname(hostname)
            return ip_address
        except Exception:
            return "127.0.0.1"