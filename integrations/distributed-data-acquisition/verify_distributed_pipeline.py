"""
分布式闭环验证脚本。

功能：
1. 在当前进程内启动 crawler worker 和 task consumer
2. 通过 RabbitMQ 投递一个测试分片
3. 监听 task.result 和 data.exchange
4. 输出 data_count、职位消息数量和关键字段样例
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import threading
import time
import uuid
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional

import pika
from dotenv import load_dotenv


PROJECT_ROOT = Path(__file__).resolve().parent
CRAWLER_ROOT = PROJECT_ROOT / "crawler-node"
sys.path.insert(0, str(CRAWLER_ROOT))

from config import config  # type: ignore  # noqa: E402
from core.logger import setup_logger, log  # type: ignore  # noqa: E402
from core.worker import WorkerNode  # type: ignore  # noqa: E402
from mq.consumer import TaskConsumer  # type: ignore  # noqa: E402


def load_root_dotenv() -> None:
    dotenv_path = PROJECT_ROOT / ".env"
    if dotenv_path.exists():
        load_dotenv(dotenv_path=dotenv_path, override=False)


def build_rabbitmq_parameters() -> pika.ConnectionParameters:
    credentials = pika.PlainCredentials(
        config.RABBITMQ_USER,
        config.RABBITMQ_PASSWORD,
    )
    return pika.ConnectionParameters(
        host=config.RABBITMQ_HOST,
        port=config.RABBITMQ_PORT,
        virtual_host=config.RABBITMQ_VHOST,
        credentials=credentials,
        heartbeat=600,
        blocked_connection_timeout=300,
        socket_timeout=10,
        connection_attempts=3,
        retry_delay=5,
    )


@dataclass
class VerificationState:
    task_id: str
    shard_id: str
    data_messages: List[Dict[str, Any]] = field(default_factory=list)
    task_result: Optional[Dict[str, Any]] = None
    task_result_event: threading.Event = field(default_factory=threading.Event)


class ExchangeListener:
    def __init__(self, state: VerificationState):
        self.state = state
        self.connection: Optional[pika.BlockingConnection] = None
        self.channel: Optional[pika.adapters.blocking_connection.BlockingChannel] = None
        self.thread: Optional[threading.Thread] = None
        self.stop_event = threading.Event()
        self.data_queue_name: Optional[str] = None
        self.task_result_queue_name: Optional[str] = None

    def start(self) -> None:
        self.thread = threading.Thread(target=self._run, name="VerificationListener", daemon=True)
        self.thread.start()
        self._wait_until_ready()

    def _wait_until_ready(self, timeout: float = 10.0) -> None:
        start = time.time()
        while time.time() - start < timeout:
            if self.channel and self.data_queue_name and self.task_result_queue_name:
                return
            time.sleep(0.1)
        raise TimeoutError("监听队列初始化超时")

    def _run(self) -> None:
        self.connection = pika.BlockingConnection(build_rabbitmq_parameters())
        self.channel = self.connection.channel()

        self.channel.exchange_declare(exchange="task.exchange", exchange_type="direct", durable=True)
        self.channel.exchange_declare(exchange="data.exchange", exchange_type="topic", durable=True)

        data_queue = self.channel.queue_declare(queue="", exclusive=True, auto_delete=True)
        self.data_queue_name = data_queue.method.queue
        self.channel.queue_bind(
            queue=self.data_queue_name,
            exchange="data.exchange",
            routing_key="data.job.#",
        )

        task_result_queue = self.channel.queue_declare(queue="", exclusive=True, auto_delete=True)
        self.task_result_queue_name = task_result_queue.method.queue
        self.channel.queue_bind(
            queue=self.task_result_queue_name,
            exchange="task.exchange",
            routing_key="task.result",
        )

        self.channel.basic_consume(
            queue=self.data_queue_name,
            on_message_callback=self._on_data_message,
            auto_ack=False,
        )
        self.channel.basic_consume(
            queue=self.task_result_queue_name,
            on_message_callback=self._on_task_result,
            auto_ack=False,
        )

        while not self.stop_event.is_set():
            self.connection.process_data_events(time_limit=1)

    def _on_data_message(self, ch, method, properties, body) -> None:
        try:
            message = json.loads(body.decode("utf-8"))
            if message.get("task_id") == self.state.task_id:
                self.state.data_messages.append(message)
            ch.basic_ack(delivery_tag=method.delivery_tag)
        except Exception:
            ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)

    def _on_task_result(self, ch, method, properties, body) -> None:
        try:
            message = json.loads(body.decode("utf-8"))
            if message.get("task_id") == self.state.task_id and message.get("shard_id") == self.state.shard_id:
                self.state.task_result = message
                self.state.task_result_event.set()
            ch.basic_ack(delivery_tag=method.delivery_tag)
        except Exception:
            ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)

    def stop(self) -> None:
        self.stop_event.set()
        if self.thread and self.thread.is_alive():
            self.thread.join(timeout=5)
        if self.channel and self.channel.is_open:
            try:
                self.channel.close()
            except Exception:
                pass
        if self.connection and not self.connection.is_closed:
            try:
                self.connection.close()
            except Exception:
                pass


def publish_test_shard(message: Dict[str, Any]) -> None:
    connection = pika.BlockingConnection(build_rabbitmq_parameters())
    channel = connection.channel()

    channel.exchange_declare(exchange="task.exchange", exchange_type="direct", durable=True)
    channel.queue_declare(
        queue="task.queue",
        durable=True,
        arguments={"x-max-priority": 10},
    )
    channel.queue_bind(queue="task.queue", exchange="task.exchange", routing_key="task.shard")

    properties = pika.BasicProperties(
        delivery_mode=2,
        content_type="application/json",
        priority=message.get("priority", 5),
    )
    channel.basic_publish(
        exchange="task.exchange",
        routing_key="task.shard",
        body=json.dumps(message, ensure_ascii=False),
        properties=properties,
    )
    connection.close()


def build_task_message(args: argparse.Namespace) -> Dict[str, Any]:
    task_id = args.task_id or f"verify-task-{uuid.uuid4().hex[:8]}"
    shard_id = args.shard_id or f"verify-shard-{uuid.uuid4().hex[:8]}"
    return {
        "task_id": task_id,
        "shard_id": shard_id,
        "keyword": args.keyword,
        "search_keyword": args.search_keyword or args.keyword,
        "city": args.city,
        "category_code": args.category_code,
        "page": args.page,
        "priority": args.priority,
        "incremental": False,
    }


def build_summary(state: VerificationState) -> Dict[str, Any]:
    sample_fields = [
        "task_id",
        "url",
        "url_obj_id",
        "title",
        "crawl_update_time",
        "company_logo",
        "company_name",
        "job_city",
    ]
    sample_message = state.data_messages[0] if state.data_messages else {}
    sample_preview = {key: sample_message.get(key) for key in sample_fields if key in sample_message}

    task_result = state.task_result or {}
    expected_data_count = int(task_result.get("data_count", 0) or 0)
    actual_message_count = len(state.data_messages)

    return {
        "success": expected_data_count > 0 and actual_message_count > 0,
        "task_id": state.task_id,
        "shard_id": state.shard_id,
        "task_result": task_result,
        "data_message_count": actual_message_count,
        "data_count_match": expected_data_count == actual_message_count,
        "sample_data_preview": sample_preview,
        "sample_data": sample_message,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="执行一次分布式闭环验证")
    parser.add_argument("--keyword", default="Python", help="搜索关键词")
    parser.add_argument("--search-keyword", dest="search_keyword", help="传给 search_keyword 的值")
    parser.add_argument("--city", default="北京", help="城市")
    parser.add_argument("--category-code", dest="category_code", default=None, help="职位类别编码")
    parser.add_argument("--page", type=int, default=1, help="页码")
    parser.add_argument("--priority", type=int, default=5, help="任务优先级")
    parser.add_argument("--timeout", type=int, default=120, help="等待 task.result 的超时时间（秒）")
    parser.add_argument("--post-result-wait", type=int, default=5, help="收到 task.result 后继续等待 data 消息的秒数")
    parser.add_argument("--task-id", dest="task_id", help="自定义 task_id")
    parser.add_argument("--shard-id", dest="shard_id", help="自定义 shard_id")
    return parser.parse_args()


def main() -> int:
    load_root_dotenv()
    setup_logger()
    args = parse_args()
    task_message = build_task_message(args)
    state = VerificationState(task_id=task_message["task_id"], shard_id=task_message["shard_id"])
    listener = ExchangeListener(state)
    worker = WorkerNode()
    consumer = TaskConsumer(worker)

    log.info(f"准备执行闭环验证: {json.dumps(task_message, ensure_ascii=False)}")

    try:
        listener.start()
        worker.start()
        consumer.start()

        time.sleep(2)
        publish_test_shard(task_message)
        log.info("测试分片已投递，等待 task.result 和 data 消息")

        if not state.task_result_event.wait(timeout=args.timeout):
            summary = {
                "success": False,
                "task_id": state.task_id,
                "shard_id": state.shard_id,
                "error": f"等待 task.result 超时（{args.timeout}s）",
                "data_message_count": len(state.data_messages),
            }
            print(json.dumps(summary, ensure_ascii=False, indent=2))
            return 1

        time.sleep(max(args.post_result_wait, 0))
        summary = build_summary(state)
        print(json.dumps(summary, ensure_ascii=False, indent=2, default=str))
        return 0 if summary["success"] else 2

    finally:
        try:
            consumer.stop()
            consumer.join(timeout=5)
        except Exception:
            pass
        try:
            worker.shutdown()
        except Exception:
            pass
        try:
            listener.stop()
        except Exception:
            pass


if __name__ == "__main__":
    raise SystemExit(main())
