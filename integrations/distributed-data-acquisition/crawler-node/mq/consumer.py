"""
消息队列消费者（爬虫节点）
"""
import json
import threading
import pika
from typing import Callable, Dict, Any
from pika.exceptions import AMQPConnectionError

from config import config
from core.logger import log


class TaskConsumer:
    """任务消费者"""

    def __init__(self, worker):
        self.worker = worker
        self.connection = None
        self.channel = None
        self.consuming = False
        self.thread = None
        self.queue = "task.queue"
        self._stopping = threading.Event()

    def connect(self):
        """连接RabbitMQ"""
        try:
            credentials = pika.PlainCredentials(
                config.RABBITMQ_USER,
                config.RABBITMQ_PASSWORD
            )
            parameters = pika.ConnectionParameters(
                host=config.RABBITMQ_HOST,
                port=config.RABBITMQ_PORT,
                virtual_host=config.RABBITMQ_VHOST,
                credentials=credentials,
                heartbeat=600,
                blocked_connection_timeout=300,
                socket_timeout=10,
                connection_attempts=3,
                retry_delay=5
            )

            self.connection = pika.BlockingConnection(parameters)
            self.channel = self.connection.channel()

            # 声明队列
            self.channel.queue_declare(
                queue=self.queue,
                durable=True,
                arguments={
                    "x-max-priority": 10  # 支持优先级
                }
            )

            # 设置公平分发
            self.channel.basic_qos(prefetch_count=1)

            log.info("RabbitMQ消费者连接成功")
            return True

        except AMQPConnectionError as e:
            log.error(f"RabbitMQ消费者连接失败: {e}")
            return False

    def process_message(self, ch, method, properties, body):
        """处理任务消息"""
        try:
            # 解析消息
            message = json.loads(body.decode('utf-8'))
            log.info(f"收到任务消息: {message.get('shard_id')}")

            # 将任务添加到Worker队列
            self.worker.add_task(message)

            # 确认消息
            ch.basic_ack(delivery_tag=method.delivery_tag)

        except Exception as e:
            log.error(f"处理任务消息失败: {e}")
            # 拒绝消息，不重新入队
            ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)

    def start_consuming(self):
        """开始消费消息"""
        try:
            self._stopping.clear()
            if not self.connect():
                return

            # 开始消费
            self.channel.basic_consume(
                queue=self.queue,
                on_message_callback=self.process_message,
                auto_ack=False
            )

            self.consuming = True
            log.info(f"开始消费任务队列: {self.queue}")

            # 启动消费循环
            self.channel.start_consuming()

        except Exception as e:
            if self._stopping.is_set():
                log.info(f"消费者停止过程中连接已关闭: {e}")
            else:
                log.error(f"消费消息失败: {e}")
                raise
        finally:
            self.consuming = False
            self._close_connection()

    def start(self):
        """启动消费者线程"""
        if self.thread and self.thread.is_alive():
            log.warning("消费者线程已经在运行中")
            return

        self.thread = threading.Thread(
            target=self.start_consuming,
            name="TaskConsumerThread",
            daemon=True
        )
        self.thread.start()
        log.info("任务消费者线程已启动")

    def stop(self):
        """停止消费者"""
        self._stopping.set()
        self.consuming = False
        if self.connection and not self.connection.is_closed:
            try:
                self.connection.add_callback_threadsafe(self._stop_consuming_safely)
            except Exception:
                self._close_connection()

        log.info("任务消费者已停止")

    def _stop_consuming_safely(self):
        """在消费线程所属连接上下文中停止消费。"""
        if self.channel and self.channel.is_open:
            try:
                self.channel.stop_consuming()
            except Exception:
                pass

    def _close_connection(self):
        """关闭channel和连接，避免跨线程直接中断消费循环。"""
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

    def join(self, timeout=None):
        """等待消费者线程结束"""
        if self.thread and self.thread.is_alive():
            self.thread.join(timeout)

    def is_alive(self):
        """检查消费者线程是否存活"""
        return self.thread is not None and self.thread.is_alive()
