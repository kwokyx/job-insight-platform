"""
消息队列消费者
"""
import json
import threading
import time
from datetime import datetime
import pika
from typing import Dict, Any, Callable, Optional
from pika.exceptions import AMQPConnectionError
from sqlalchemy import MetaData, Table, insert, select, update

from config import settings
from app.core.logger import log
from app.db.session import get_db
from app.services.task_service import TaskService
from app.services.hdfs_storage import HdfsBatchWriter


class MQConsumer:
    """消息队列消费者基类"""

    def __init__(self):
        self.connection = None
        self.channel = None
        self.consuming = False
        self.thread = None

    def connect(self):
        """连接RabbitMQ"""
        try:
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

            self.connection = pika.BlockingConnection(parameters)
            self.channel = self.connection.channel()

            log.info("RabbitMQ消费者连接成功")
            return True

        except AMQPConnectionError as e:
            log.error(f"RabbitMQ消费者连接失败: {e}")
            return False

    def start_consuming(self, queue: str, callback: Callable):
        """开始消费消息"""
        try:
            if not self.connect():
                return

            # 声明队列
            self.channel.queue_declare(queue=queue, durable=True)

            # 设置公平分发
            self.channel.basic_qos(prefetch_count=1)

            # 开始消费
            self.channel.basic_consume(
                queue=queue,
                on_message_callback=callback,
                auto_ack=False
            )

            self.consuming = True
            log.info(f"开始消费队列: {queue}")

            # 启动消费循环
            self.channel.start_consuming()

        except Exception as e:
            log.error(f"消费消息失败: {e}")
            self.consuming = False
            raise

    def stop_consuming(self):
        """停止消费"""
        self.consuming = False
        if self.channel and self.channel.is_open:
            self.channel.stop_consuming()
        if self.connection and not self.connection.is_closed:
            self.connection.close()
        log.info("RabbitMQ消费者已停止")

    def start_in_thread(self, queue: str, callback: Callable):
        """在线程中启动消费者"""
        self.thread = threading.Thread(
            target=self.start_consuming,
            args=(queue, callback),
            daemon=True
        )
        self.thread.start()
        log.info(f"消费者线程已启动: {queue}")


# class HeartbeatConsumer(MQConsumer):
#     """心跳消费者"""

#     def __init__(self):
#         super().__init__()
#         self.queue = "heartbeat.queue"

#     def process_message(self, ch, method, properties, body):
#         """处理心跳消息"""
#         try:
#             # 解析消息
#             message = json.loads(body.decode('utf-8'))
#             log.debug(f"收到心跳消息: {message.get('worker_id')}")

#             # 更新节点状态
#             self.update_worker_status(message)

#             # 确认消息
#             ch.basic_ack(delivery_tag=method.delivery_tag)

#         except Exception as e:
#             log.error(f"处理心跳消息失败: {e}")
#             ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)

#     def update_worker_status(self, message: Dict[str, Any]):
#         """更新节点状态"""
#         try:
#             from app.db.session import get_db_session
#             from app.models.task import CrawlWorker
#             from datetime import datetime

#             with get_db_session() as db:
#                 worker_id = message.get("worker_id")
#                 if not worker_id:
#                     return

#                 worker = db.query(CrawlWorker).filter(CrawlWorker.worker_id == worker_id).first()

#                 if not worker:
#                     # 创建新节点
#                     worker = CrawlWorker(
#                         worker_id=worker_id,
#                         ip=message.get("ip", "unknown"),
#                         status=1,
#                         cpu_usage=int(message.get("cpu_usage", 0)),
#                         memory_usage=int(message.get("memory_usage", 0)),
#                         current_task_id=message.get("current_task_id"),
#                         last_heartbeat=datetime.now()
#                     )
#                     db.add(worker)
#                     log.info(f"注册新节点: {worker_id}")
#                 else:
#                     # 更新节点状态
#                     worker.cpu_usage = int(message.get("cpu_usage", 0))
#                     worker.memory_usage = int(message.get("memory_usage", 0))
#                     worker.current_task_id = message.get("current_task_id")
#                     worker.last_heartbeat = datetime.now()
#                     worker.status = 1  # 在线

#                 db.commit()

#         except Exception as e:
#             log.error(f"更新节点状态失败: {e}")
    

#     def start(self):
#         """启动心跳消费者"""
#         self.start_in_thread(self.queue, self.process_message)

class HeartbeatConsumer(MQConsumer):
    """心跳消费者"""

    def __init__(self):
        super().__init__()
        self.queue = "heartbeat.queue"

    def process_message(self, ch, method, properties, body):
        """处理心跳消息"""
        try:
            # 解析消息
            message = json.loads(body.decode('utf-8'))
            log.debug(f"收到心跳消息: {message.get('worker_id')}")

            # 更新节点状态
            self.update_worker_status(message)

            # 确认消息
            ch.basic_ack(delivery_tag=method.delivery_tag)

        except Exception as e:
            log.error(f"处理心跳消息失败: {e}")
            ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)

    def update_worker_status(self, message: Dict[str, Any]):
        """更新节点状态 - 修复IP重复问题"""
        try:
            from app.db.session import get_db_session
            from app.models.task import CrawlWorker
            from datetime import datetime

            with get_db_session() as db:
                worker_id = message.get("worker_id")
                if not worker_id:
                    return

                # 先按 worker_id 查找
                worker = db.query(CrawlWorker).filter(CrawlWorker.worker_id == worker_id).first()

                if worker:
                    # 更新现有节点
                    worker.cpu_usage = int(message.get("cpu_usage", 0))
                    worker.memory_usage = int(message.get("memory_usage", 0))
                    worker.current_task_id = message.get("current_task_id")
                    worker.last_heartbeat = datetime.now()
                    worker.status = 1  # 在线
                    # 如果IP变化了也更新
                    if message.get("ip"):
                        worker.ip = message.get("ip")
                    log.info(f"更新节点心跳: {worker_id}")
                else:
                    # 检查IP是否已被其他节点使用
                    ip = message.get("ip", "unknown")
                    existing_by_ip = db.query(CrawlWorker).filter(CrawlWorker.ip == ip).first()
                    
                    if existing_by_ip:
                        # 不要直接修改主键 worker_id。任务分片、日志等表通过外键引用它，
                        # 直接改主键会触发外键约束失败，并导致后续状态更新异常。
                        log.warning(
                            f"IP {ip} 已被节点 {existing_by_ip.worker_id} 使用，"
                            f"收到新节点ID {worker_id} 的心跳，沿用已有节点记录更新状态"
                        )
                        existing_by_ip.cpu_usage = int(message.get("cpu_usage", 0))
                        existing_by_ip.memory_usage = int(message.get("memory_usage", 0))
                        existing_by_ip.current_task_id = message.get("current_task_id")
                        existing_by_ip.last_heartbeat = datetime.now()
                        existing_by_ip.status = 1
                        if message.get("ip"):
                            existing_by_ip.ip = message.get("ip")
                    else:
                        # 创建新节点
                        worker = CrawlWorker(
                            worker_id=worker_id,
                            ip=ip,
                            status=1,
                            cpu_usage=int(message.get("cpu_usage", 0)),
                            memory_usage=int(message.get("memory_usage", 0)),
                            current_task_id=message.get("current_task_id"),
                            last_heartbeat=datetime.now()
                        )
                        db.add(worker)
                        log.info(f"注册新节点: {worker_id}")

                db.commit()
                log.debug(f"节点状态更新成功: {worker_id}")

        except Exception as e:
            log.error(f"更新节点状态失败: {e}")
            # 不抛出异常，避免影响消息确认

    def start(self):
        """启动心跳消费者"""
        self.start_in_thread(self.queue, self.process_message)


class DataConsumer(MQConsumer):
    """数据消费者（接收采集的职位数据）"""

    def __init__(self):
        super().__init__()
        self.queue = "data.queue"
        self._job_data_table = None
        self._job_data_columns = set()
        self.hdfs_writer = HdfsBatchWriter()

    def process_message(self, ch, method, properties, body):
        """处理数据消息"""
        try:
            # 解析消息
            message = json.loads(body.decode('utf-8'))
            log.debug(f"收到数据消息: {message.get('url_obj_id')}")

            # 保存职位数据
            self.save_job_data(message)

            # 确认消息
            ch.basic_ack(delivery_tag=method.delivery_tag)

        except Exception as e:
            log.error(f"处理数据消息失败: {e}")
            ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)

    def save_job_data(self, message: Dict[str, Any]):
        """保存职位数据"""
        try:
            from app.db.session import get_db_session

            with get_db_session() as db:
                job_data_table = self._get_job_data_table(db)
                available_columns = self._job_data_columns
                url_obj_id = message.get("url_obj_id")
                if not url_obj_id:
                    log.warning("职位消息缺少 url_obj_id，跳过入库")
                    return

                existing = db.execute(
                    select(job_data_table).where(job_data_table.c.url_obj_id == url_obj_id).limit(1)
                ).mappings().first()
                shard_id = message.get("shard_id")
                task_id = message.get("task_id")
                action = "duplicate"

                if existing:
                    updates = {}
                    for key in self._tracked_fields():
                        if key not in available_columns:
                            continue
                        value = message.get(key)
                        if value is not None and existing.get(key) != value:
                            updates[key] = value
                    if task_id and "task_id" in available_columns:
                        updates["task_id"] = task_id
                    if updates:
                        if "crawl_update_time" in available_columns:
                            updates["crawl_update_time"] = datetime.now()
                        db.execute(
                            update(job_data_table)
                            .where(job_data_table.c.url_obj_id == url_obj_id)
                            .values(**updates)
                        )
                        action = "updated"
                        log.debug(f"更新职位数据: {url_obj_id}")
                    else:
                        action = "duplicate"
                        log.debug(f"职位数据无变化: {url_obj_id}")
                else:
                    # 创建新数据
                    payload = self._build_job_data_payload(message, available_columns)
                    db.execute(insert(job_data_table).values(**payload))
                    action = "new"
                    log.debug(f"新增职位数据: {url_obj_id}")

                db.commit()
                self._update_shard_statistics(db, shard_id, task_id, action)
                db.commit()
                self.hdfs_writer.enqueue(message)

        except Exception as e:
            log.error(f"保存职位数据失败: {e}")

    def _get_job_data_table(self, db):
        """按数据库现有结构反射职位明细表，避免代码模型字段超前导致查询失败。"""
        if self._job_data_table is None:
            metadata = MetaData()
            table_name = settings.JOB_DATA_TABLE
            self._job_data_table = Table(table_name, metadata, autoload_with=db.get_bind())
            self._job_data_columns = set(self._job_data_table.columns.keys())
            log.info(f"按数据库实际字段加载 {table_name} 表结构: {sorted(self._job_data_columns)}")
        return self._job_data_table

    def _tracked_fields(self):
        return [
            "url",
            "title",
            "salary_min",
            "salary_max",
            "salary_raw",
            "job_city",
            "district",
            "street_name",
            "experience_year",
            "education_need",
            "publish_date",
            "job_welfare",
            "job_labels",
            "job_skill_tags",
            "job_keywords",
            "requirements",
            "position_info",
            "job_classification",
            "address",
            "company_name",
            "company_size",
            "company_type",
            "company_finance",
            "industry_name",
            "industry_code",
            "company_url",
            "company_logo",
        ]

    def _build_job_data_payload(self, message: Dict[str, Any], available_columns: set[str]) -> Dict[str, Any]:
        allowed_fields = set(self._tracked_fields() + [
            "url_obj_id",
            "task_id",
            "crawl_time",
            "crawl_update_time",
        ])
        payload = {
            key: value
            for key, value in message.items()
            if key in allowed_fields and key in available_columns
        }
        for field in ("publish_date", "crawl_time", "crawl_update_time"):
            if field in payload:
                payload[field] = self._parse_datetime_value(payload.get(field))
        return payload

    def _parse_datetime_value(self, value: Any) -> Optional[datetime]:
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
            log.warning(f"无法解析时间字段，保留为空: {value}")
            return None

    def _update_shard_statistics(self, db, shard_id: Optional[str], task_id: Optional[str], action: str):
        from app.models.task import CrawlTaskShard, CrawlTask

        if shard_id:
            shard = db.query(CrawlTaskShard).filter(CrawlTaskShard.shard_id == shard_id).first()
            if shard:
                if action == "new":
                    shard.new_count += 1
                elif action == "updated":
                    shard.updated_count += 1
                else:
                    shard.duplicate_count += 1

        if task_id:
            task = db.query(CrawlTask).filter(CrawlTask.task_id == task_id).first()
            if task:
                if action == "new":
                    task.new_count += 1
                elif action == "updated":
                    task.updated_count += 1
                else:
                    task.duplicate_count += 1

    def start(self):
        """启动数据消费者"""
        self.hdfs_writer.start()
        self.start_in_thread(self.queue, self.process_message)

    def stop_consuming(self):
        """停止消费并刷出剩余 HDFS 缓冲。"""
        super().stop_consuming()
        try:
            self.hdfs_writer.stop()
        except Exception as exc:
            log.error(f"停止 HDFS 批量写入器失败: {exc}")


class TaskResultConsumer(MQConsumer):
    """任务结果消费者（接收分片执行结果）"""

    def __init__(self):
        super().__init__()
        self.queue = "task.result.queue"

    def process_message(self, ch, method, properties, body):
        """处理任务结果消息"""
        try:
            # 解析消息
            message = json.loads(body.decode('utf-8'))
            log.debug(f"收到任务结果: {message.get('shard_id')}")

            # 更新分片状态
            self.update_shard_status(message)

            # 确认消息
            ch.basic_ack(delivery_tag=method.delivery_tag)

        except Exception as e:
            log.error(f"处理任务结果失败: {e}")
            ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)

    def update_shard_status(self, message: Dict[str, Any]):
        """更新分片状态"""
        try:
            from app.db.session import get_db_session
            from app.services.task_service import TaskService

            with get_db_session() as db:
                task_service = TaskService(db)

                shard_id = message.get("shard_id")
                status = message.get("status")  # 2=完成, 3=失败
                worker_id = message.get("worker_id")
                stop_reason = message.get("stop_reason")
                new_count = message.get("new_count")
                updated_count = message.get("updated_count")
                duplicate_count = message.get("duplicate_count")

                if not shard_id or status not in [2, 3]:
                    log.warning(f"无效的任务结果消息: {message}")
                    return

                task_service.update_shard_status(
                    shard_id,
                    status,
                    worker_id,
                    stop_reason=stop_reason,
                    new_count=new_count,
                    updated_count=updated_count,
                    duplicate_count=duplicate_count,
                )

        except Exception as e:
            log.error(f"更新分片状态失败: {e}")

    def start(self):
        """启动任务结果消费者"""
        # 声明队列
        if not self.connect():
            return

        self.channel.queue_declare(queue=self.queue, durable=True)
        self.channel.queue_bind(
            queue=self.queue,
            exchange="task.exchange",
            routing_key="task.result"
        )

        self.start_in_thread(self.queue, self.process_message)


def start_all_consumers():
    """启动所有消费者"""
    try:
        # 启动心跳消费者
        heartbeat_consumer = HeartbeatConsumer()
        heartbeat_consumer.start()

        # 启动数据消费者
        data_consumer = DataConsumer()
        data_consumer.start()

        # 启动任务结果消费者
        task_result_consumer = TaskResultConsumer()
        task_result_consumer.start()

        log.info("所有消息队列消费者已启动")

        return {
            "heartbeat": heartbeat_consumer,
            "data": data_consumer,
            "task_result": task_result_consumer
        }

    except Exception as e:
        log.error(f"启动消费者失败: {e}")
        raise


# 全局消费者实例
consumers = None

def init_consumers():
    """初始化消费者（在应用启动时调用）"""
    global consumers
    if consumers is None:
        consumers = start_all_consumers()
    return consumers

def stop_consumers():
    """停止所有消费者"""
    global consumers
    if consumers:
        for name, consumer in consumers.items():
            try:
                consumer.stop_consuming()
            except Exception as e:
                log.error(f"停止消费者{name}失败: {e}")
        consumers = None
        log.info("所有消费者已停止")
