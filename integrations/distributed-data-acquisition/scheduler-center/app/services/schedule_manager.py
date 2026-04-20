"""
定时任务调度线程
"""
import threading
import time
from datetime import datetime

from app.core.logger import log
from app.db.session import get_db_session
from app.services.task_service import TaskService


class ScheduleManager:
    """轻量定时任务扫描器。"""

    def __init__(self, interval_seconds: int = 30):
        self.interval_seconds = interval_seconds
        self.running = False
        self.thread = None

    def start(self):
        if self.thread and self.thread.is_alive():
            return
        self.running = True
        self.thread = threading.Thread(target=self._run_loop, name="ScheduleManagerThread", daemon=True)
        self.thread.start()
        log.info(f"定时调度线程已启动，扫描间隔{self.interval_seconds}秒")

    def stop(self):
        self.running = False
        if self.thread and self.thread.is_alive():
            self.thread.join(timeout=5)
        log.info("定时调度线程已停止")

    def _run_loop(self):
        while self.running:
            try:
                self.scan_and_dispatch()
            except Exception as exc:
                log.error(f"定时调度扫描失败: {exc}")
            time.sleep(self.interval_seconds)

    def scan_and_dispatch(self):
        with get_db_session() as db:
            task_service = TaskService(db)
            templates = task_service.get_due_templates(datetime.now())
            for template in templates:
                log.info(f"命中到期定时模板: task_id={template.task_id}, task_name={template.task_name}")
                task_service.create_scheduled_run(template.task_id)
