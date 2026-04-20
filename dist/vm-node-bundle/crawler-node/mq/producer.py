"""
消息队列生产者（爬虫节点）
"""
import json
from datetime import date, datetime
from typing import Dict, Any

import pika
from pika.exceptions import AMQPConnectionError

from config import config
from core.logger import log
import os

class MQProducer:
    """消息队列生产者"""

    def __init__(self):
        self.connection = None
        self.channel = None
        self.connect()

    def connect(self):
        print("\n" + "="*50)
        print("🐇 RabbitMQ 连接参数调试信息：")
        print(f"主机: {os.getenv('RABBITMQ_HOST', 'localhost')}")
        print(f"端口: {os.getenv('RABBITMQ_PORT', 5672)}")
        print(f"用户: {os.getenv('RABBITMQ_USER', '未设置')}")
        print(f"密码: {os.getenv('RABBITMQ_PASS', '未设置')}")
        print("--- Config对象值 ---")
        print(f"主机: {config.RABBITMQ_HOST}")
        print(f"端口: {config.RABBITMQ_PORT}")
        print(f"用户: {config.RABBITMQ_USER}")
        print(f"密码: {config.RABBITMQ_PASSWORD}")
        print(f"虚拟主机: {config.RABBITMQ_VHOST}")
        print("="*50 + "\n")
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

            # 声明Exchange
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
            self.channel.exchange_declare(
                exchange="task.exchange",
                exchange_type="direct",
                durable=True
            )

            log.info("RabbitMQ生产者连接成功")

        except AMQPConnectionError as e:
            log.error(f"RabbitMQ生产者连接失败: {e}")
            self.connection = None
            self.channel = None
            raise

    def ensure_connection(self):
        """确保连接有效"""
        if self.connection is None or self.connection.is_closed:
            log.warning("RabbitMQ连接已关闭，尝试重连")
            self.connect()

    def _serialize_message(self, message: Dict[str, Any]) -> str:
        """统一序列化消息，兼容 datetime/date 字段。"""
        return json.dumps(message, ensure_ascii=False, default=self._json_default)

    @staticmethod
    def _json_default(value: Any) -> Any:
        if isinstance(value, (datetime, date)):
            return value.isoformat()
        return str(value)

    def send_heartbeat(self, message: Dict[str, Any]):
        """发送心跳消息"""
        try:
            self.ensure_connection()

            properties = pika.BasicProperties(
                delivery_mode=2,  # 持久化消息
                content_type="application/json"
            )

            self.channel.basic_publish(
                exchange="heartbeat.exchange",
                routing_key="heartbeat",
                body=self._serialize_message(message),
                properties=properties
            )

            log.debug(f"发送心跳消息: {config.NODE_ID}")

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
                body=self._serialize_message(message),
                properties=properties
            )

            log.debug(f"发送职位数据消息: {message.get('url_obj_id')}")

        except Exception as e:
            log.error(f"发送职位数据消息失败: {e}")
            raise

    def send_task_result(self, message: Dict[str, Any]):
        """发送任务结果消息"""
        try:
            self.ensure_connection()

            properties = pika.BasicProperties(
                delivery_mode=2,
                content_type="application/json"
            )

            self.channel.basic_publish(
                exchange="task.exchange",
                routing_key="task.result",
                body=self._serialize_message(message),
                properties=properties
            )

            log.debug(f"发送任务结果消息: {message.get('shard_id')}")

        except Exception as e:
            log.error(f"发送任务结果消息失败: {e}")
            raise

    def close(self):
        """关闭连接"""
        try:
            if self.connection and not self.connection.is_closed:
                self.connection.close()
                log.info("RabbitMQ生产者连接已关闭")
        except Exception as e:
            log.error(f"关闭RabbitMQ连接失败: {e}")

    def __del__(self):
        """析构函数"""
        self.close()
