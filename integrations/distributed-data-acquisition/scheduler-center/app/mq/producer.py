"""
消息队列生产者
"""
import json
import pika
from typing import Dict, Any
from pika.exceptions import AMQPConnectionError

from config import settings
from app.core.logger import log


class MQProducer:
    """消息队列生产者"""

    def __init__(self):
        self.connection = None
        self.channel = None
        self.connect()

    def connect(self):
        """连接RabbitMQ"""
        try:
            # 创建连接参数
            credentials = pika.PlainCredentials(
                settings.RABBITMQ_USER,
                settings.RABBITMQ_PASSWORD
            )
            parameters = pika.ConnectionParameters(
                host=settings.RABBITMQ_HOST,
                port=settings.RABBITMQ_PORT,
                virtual_host=settings.RABBITMQ_VHOST,
                credentials=credentials,
                heartbeat=600,
                blocked_connection_timeout=300
            )

            # 建立连接
            self.connection = pika.BlockingConnection(parameters)
            self.channel = self.connection.channel()

            # 声明Exchange
            self.channel.exchange_declare(
                exchange="task.exchange",
                exchange_type="direct",
                durable=True
            )
            self.channel.exchange_declare(
                exchange="heartbeat.exchange",
                exchange_type="fanout",
                durable=True
            )
            self.channel.exchange_declare(
                exchange="data.exchange",
                exchange_type="topic",
                durable=True
            )

            # 声明Queue
            self.channel.queue_declare(
                queue="task.queue",
                durable=True,
                arguments={
                    "x-max-priority": 10  # 支持优先级
                }
            )
            self.channel.queue_declare(
                queue="heartbeat.queue",
                durable=True
            )
            self.channel.queue_declare(
                queue="data.queue",
                durable=True
            )

            # 绑定Queue到Exchange
            self.channel.queue_bind(
                queue="task.queue",
                exchange="task.exchange",
                routing_key="task.shard"
            )
            self.channel.queue_bind(
                queue="heartbeat.queue",
                exchange="heartbeat.exchange",
                routing_key="heartbeat"
            )
            self.channel.queue_bind(
                queue="data.queue",
                exchange="data.exchange",
                routing_key="data.job.#"
            )

            log.info("RabbitMQ连接成功")

        except AMQPConnectionError as e:
            log.error(f"RabbitMQ连接失败: {e}")
            self.connection = None
            self.channel = None
            raise

    def ensure_connection(self):
        """确保连接有效"""
        if self.connection is None or self.connection.is_closed:
            log.warning("RabbitMQ连接已关闭，尝试重连")
            self.connect()

    def send_task_shard(self, message: Dict[str, Any]):
        """发送任务分片消息"""
        try:
            self.ensure_connection()

            # 设置消息属性
            properties = pika.BasicProperties(
                delivery_mode=2,  # 持久化消息
                content_type="application/json",
                priority=message.get("priority", 5)
            )

            # 发布消息
            self.channel.basic_publish(
                exchange="task.exchange",
                routing_key="task.shard",
                body=json.dumps(message, ensure_ascii=False),
                properties=properties
            )

            log.debug(f"发送任务分片消息: {message.get('shard_id')}")

        except Exception as e:
            log.error(f"发送任务分片消息失败: {e}")
            raise

    def send_heartbeat(self, message: Dict[str, Any]):
        """发送心跳消息"""
        try:
            self.ensure_connection()

            properties = pika.BasicProperties(
                delivery_mode=2,
                content_type="application/json"
            )

            self.channel.basic_publish(
                exchange="heartbeat.exchange",
                routing_key="heartbeat",
                body=json.dumps(message, ensure_ascii=False),
                properties=properties
            )

            log.debug(f"发送心跳消息: {message.get('worker_id')}")

        except Exception as e:
            log.error(f"发送心跳消息失败: {e}")
            raise

    def send_job_data(self, message: Dict[str, Any]):
        """发送职位数据消息"""
        try:
            self.ensure_connection()

            properties = pika.BasicProperties(
                delivery_mode=2,
                content_type="application/json"
            )

            # 根据城市路由
            city = message.get("job_city", "unknown")
            routing_key = f"data.job.{city}"

            self.channel.basic_publish(
                exchange="data.exchange",
                routing_key=routing_key,
                body=json.dumps(message, ensure_ascii=False),
                properties=properties
            )

            log.debug(f"发送职位数据消息: {message.get('url_obj_id')}")

        except Exception as e:
            log.error(f"发送职位数据消息失败: {e}")
            raise

    def close(self):
        """关闭连接"""
        try:
            if self.connection and not self.connection.is_closed:
                self.connection.close()
                log.info("RabbitMQ连接已关闭")
        except Exception as e:
            log.error(f"关闭RabbitMQ连接失败: {e}")

    def __del__(self):
        """析构函数"""
        self.close()


# 全局生产者实例
producer = MQProducer()