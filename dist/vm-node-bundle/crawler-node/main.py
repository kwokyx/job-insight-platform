"""
爬虫节点主程序
"""
import sys
import os
import time
import threading
import signal
import json
from pathlib import Path
from datetime import datetime
from typing import Optional, Dict, Any

from dotenv import load_dotenv

# 添加当前目录到Python路径
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))


def _load_root_dotenv() -> Dict[str, Any]:
    """加载项目根目录 .env，供 crawler-node 使用。"""
    project_root = Path(__file__).resolve().parent.parent
    dotenv_path = project_root / ".env"
    dotenv_exists = dotenv_path.exists()
    dotenv_loaded = False

    if dotenv_exists:
        dotenv_loaded = load_dotenv(dotenv_path=dotenv_path, override=False)

    return {
        "dotenv_loaded": dotenv_loaded,
        "dotenv_path": str(dotenv_path),
        "dotenv_exists": dotenv_exists,
    }


DOTENV_STATUS = _load_root_dotenv()

from config import config
from core.logger import setup_logger, log
from core.worker import WorkerNode
from mq.consumer import TaskConsumer
from utils.heartbeat import HeartbeatManager


class CrawlerNode:
    """爬虫节点"""

    def __init__(self):
        self.worker: Optional[WorkerNode] = None
        self.task_consumer: Optional[TaskConsumer] = None
        self.heartbeat_manager: Optional[HeartbeatManager] = None
        self.running = False

    def initialize(self):
        """初始化节点"""
        try:
            # 设置日志
            setup_logger()

            log.info(f"启动爬虫节点: {config.NODE_ID}")
            log.info(f"调度中心: {config.scheduler_base_url}")
            self._log_zhaopin_env_status()

            # 创建Worker节点
            self.worker = WorkerNode()
            self.worker.start()

            # 创建任务消费者
            self.task_consumer = TaskConsumer(self.worker)
            self.task_consumer.start()

            # 创建心跳管理器
            self.heartbeat_manager = HeartbeatManager(self.worker)
            self.heartbeat_manager.start()

            # 注册信号处理
            self.setup_signal_handlers()

            self.running = True
            log.info("爬虫节点初始化完成")

        except Exception as e:
            log.error(f"节点初始化失败: {e}")
            raise

    def _log_zhaopin_env_status(self) -> None:
        """输出智联鉴权配置加载状态，帮助确认 .env 是否生效。"""
        auth_summary = {
            "token_mme_default": config.ZHAOPIN_TOKEN_MME == "MmEwMD=",
            "token_c1k_default": config.ZHAOPIN_TOKEN_C1K == "c1K5tw0w6_=",
            "cookie_present": bool(config.ZHAOPIN_COOKIE.strip()),
            "page_request_id_present": bool(config.ZHAOPIN_PAGE_REQUEST_ID.strip()),
            "request_id_present": bool(config.ZHAOPIN_REQUEST_ID.strip()),
            "client_id_present": bool(config.ZHAOPIN_CLIENT_ID.strip()),
            "dotenv_loaded": DOTENV_STATUS["dotenv_loaded"],
            "dotenv_path": DOTENV_STATUS["dotenv_path"],
        }
        log.info(f"智联鉴权配置摘要: {json.dumps(auth_summary, ensure_ascii=False)}")

        missing_items = []
        if auth_summary["token_mme_default"]:
            missing_items.append("ZHAOPIN_TOKEN_MME")
        if auth_summary["token_c1k_default"]:
            missing_items.append("ZHAOPIN_TOKEN_C1K")
        if not auth_summary["cookie_present"]:
            missing_items.append("ZHAOPIN_COOKIE")
        if not auth_summary["page_request_id_present"]:
            missing_items.append("ZHAOPIN_PAGE_REQUEST_ID")

        if not DOTENV_STATUS["dotenv_exists"]:
            log.warning(f"项目根目录 .env 不存在: {DOTENV_STATUS['dotenv_path']}")
        elif not DOTENV_STATUS["dotenv_loaded"]:
            log.warning(
                "项目根目录 .env 存在，但当前进程没有从该文件加载新变量；"
                "可能是环境变量已由外部启动脚本注入，或 .env 内容无可用键值"
            )

        if missing_items:
            log.warning(
                "未加载到有效智联鉴权配置，缺失或仍为默认值的字段: "
                f"{', '.join(missing_items)}"
            )
        else:
            log.info("已加载到有效智联鉴权配置")

    def setup_signal_handlers(self):
        """设置信号处理"""
        signal.signal(signal.SIGINT, self.signal_handler)
        signal.signal(signal.SIGTERM, self.signal_handler)

    def signal_handler(self, signum, frame):
        """信号处理函数"""
        log.info(f"收到信号 {signum}，正在关闭节点...")
        self.shutdown()

    def run(self):
        """运行节点"""
        try:
            self.initialize()

            # 主循环
            while self.running:
                time.sleep(1)

                # 检查组件状态
                if self.task_consumer and not self.task_consumer.is_alive():
                    log.error("任务消费者线程已停止，尝试重启...")
                    self.task_consumer.start()

                if self.heartbeat_manager and not self.heartbeat_manager.is_alive():
                    log.error("心跳管理器线程已停止，尝试重启...")
                    self.heartbeat_manager.start()

        except KeyboardInterrupt:
            log.info("收到键盘中断，正在关闭节点...")
        except Exception as e:
            log.error(f"节点运行异常: {e}")
        finally:
            self.shutdown()

    def shutdown(self):
        """关闭节点"""
        if not self.running:
            return

        self.running = False
        log.info("正在关闭爬虫节点...")

        try:
            # 停止心跳管理器
            if self.heartbeat_manager:
                self.heartbeat_manager.stop()
                self.heartbeat_manager.join(timeout=5)
                log.info("心跳管理器已停止")

            # 停止任务消费者
            if self.task_consumer:
                self.task_consumer.stop()
                self.task_consumer.join(timeout=5)
                log.info("任务消费者已停止")

            # 停止Worker
            if self.worker:
                self.worker.shutdown()
                log.info("Worker已停止")

            log.info("爬虫节点关闭完成")

        except Exception as e:
            log.error(f"关闭节点时发生错误: {e}")

        finally:
            sys.exit(0)


def main():
    """主函数"""
    # 检查Python版本
    if sys.version_info < (3, 7):
        print("错误: 需要Python 3.7或更高版本")
        sys.exit(1)

    # 创建节点并运行
    node = CrawlerNode()
    node.run()


if __name__ == "__main__":
    main()
