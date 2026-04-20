"""
任务服务
"""
import uuid
import json
from datetime import datetime
from typing import List, Dict, Any, Optional
from sqlalchemy.orm import Session
from sqlalchemy import func

from app.models.task import CrawlTask, CrawlTaskShard, CrawlWorker, JobData
from app.schemas.task import TaskCreate, TaskShardMessage
from app.mq.producer import MQProducer
from app.core.logger import log
from app.services.schedule_utils import (
    build_schedule,
    dumps_schedule_days,
    loads_schedule_days,
    normalize_schedule_mode,
)

ZHAOPIN_CITY_MAP = {
    "北京": "530",
    "上海": "538",
    "广州": "763",
    "深圳": "765",
    "杭州": "653",
    "南京": "635",
    "武汉": "736",
    "成都": "801",
    "重庆": "551",
    "天津": "531",
}


class TaskService:
    """任务服务"""

    def __init__(self, db: Session):
        self.db = db
        self.mq_producer = MQProducer()

    def create_task(self, task_data: TaskCreate) -> CrawlTask:
        """创建采集任务"""
        try:
            # 生成任务ID
            task_id = f"task_{uuid.uuid4().hex[:16]}"
            schedule_type = normalize_schedule_mode(task_data.schedule_mode, task_data.schedule_type)
            schedule_preset, schedule_time, schedule_days, cron_expression, next_run_time = build_schedule(
                schedule_type=schedule_type,
                schedule_preset=task_data.schedule_preset,
                schedule_time=task_data.schedule_time,
                schedule_days=task_data.schedule_days,
                cron_expression=task_data.cron_expression,
                schedule_timezone=task_data.schedule_timezone,
            )

            normalized_cities = self.normalize_cities(task_data.channel, task_data.city)
            baseline_task_id = task_data.baseline_task_id
            if task_data.incremental and not baseline_task_id:
                baseline_task_id = self.find_baseline_task_id(
                    channel=task_data.channel,
                    keywords=task_data.keywords,
                    cities=normalized_cities,
                )

            # 创建任务记录
            task = CrawlTask(
                task_id=task_id,
                task_name=task_data.task_name,
                channel=task_data.channel,
                keywords=json.dumps(task_data.keywords, ensure_ascii=False) if task_data.keywords else None,
                city=json.dumps(normalized_cities, ensure_ascii=False) if normalized_cities else None,
                page_count=task_data.page_count,
                schedule_type=schedule_type,
                cron_expression=cron_expression,
                schedule_timezone=task_data.schedule_timezone,
                schedule_enabled=1 if task_data.schedule_enabled else 0,
                schedule_preset=schedule_preset,
                schedule_time=schedule_time,
                schedule_days=dumps_schedule_days(schedule_days),
                next_run_time=next_run_time,
                incremental=1 if task_data.incremental else 0,
                baseline_task_id=baseline_task_id,
                incremental_page_limit=task_data.incremental_page_limit,
                stale_page_threshold=task_data.stale_page_threshold,
                lookback_hours=task_data.lookback_hours,
                priority=task_data.priority,
                status=0,  # 待执行
                total_count=0,
                finished_count=0,
                new_count=0,
                updated_count=0,
                duplicate_count=0,
                create_user=task_data.create_user,
            )

            self.db.add(task)
            self.db.commit()
            self.db.refresh(task)

            log.info(f"创建任务成功: task_id={task_id}, task_name={task_data.task_name}")

            return task

        except Exception as e:
            self.db.rollback()
            log.error(f"创建任务失败: {e}")
            raise

    def normalize_cities(self, channel: str, cities: Optional[List[str]]) -> Optional[List[str]]:
        """Normalize city values before persistence and distribution."""
        if not cities:
            return cities

        normalized: List[str] = []
        for city in cities:
            raw = str(city or "").strip()
            if not raw:
                continue
            if channel == "zhaopin":
                if raw.isdigit():
                    normalized.append(raw)
                else:
                    normalized.append(ZHAOPIN_CITY_MAP.get(raw, raw))
            else:
                normalized.append(raw)
        return normalized or None

    def process_task(self, task_id: str):
        """处理任务：拆分任务并分发分片"""
        try:
            task = self.db.query(CrawlTask).filter(CrawlTask.task_id == task_id).first()
            if not task:
                log.error(f"任务不存在: {task_id}")
                return

            if task.schedule_type == "SCHEDULED_TEMPLATE":
                log.info(f"定时模板任务无需立即拆分: task_id={task_id}")
                return

            # 任务拆分
            shards = self.split_task(task)

            # 更新任务总数
            task.total_count = len(shards)
            self.db.commit()

            log.info(f"任务拆分完成: task_id={task_id}, 分片数={len(shards)}")

            # 如果任务状态是运行中，则分发分片
            if task.status == 1:
                self.distribute_shards(shards)

        except Exception as e:
            log.error(f"处理任务失败: {e}")
            raise

    def split_task(self, task: CrawlTask) -> List[CrawlTaskShard]:
        """拆分任务为分片"""
        shards = []
        batch = []

        try:
            # 解析关键词和城市
            keywords = json.loads(task.keywords) if task.keywords else [None]
            cities = json.loads(task.city) if task.city else [None]
            page_count = getattr(task, "page_count", 3) or 3
            if task.incremental:
                page_count = getattr(task, "incremental_page_limit", 2) or 2

            # 智联招聘分类码（示例）
            category_codes = self.get_category_codes(task.channel)

            # 生成分片
            for keyword in keywords:
                for city in cities:
                    for category_code in category_codes:
                        for page in range(1, page_count + 1):
                            shard_id = f"shard_{uuid.uuid4().hex[:16]}"
                            shard = CrawlTaskShard(
                                shard_id=shard_id,
                                task_id=task.task_id,
                                page=page,
                                keyword=keyword,
                                city=city,
                                category_code=category_code,
                                status=0,  # 待执行
                                retry_count=0,
                                stop_reason=None,
                                new_count=0,
                                updated_count=0,
                                duplicate_count=0,
                            )
                            shards.append(shard)
                            batch.append(shard)

                            # 批量插入，每100个提交一次
                            if len(batch) == 100:
                                self.db.bulk_save_objects(batch)
                                self.db.commit()
                                batch = []

            # 插入剩余分片
            if batch:
                self.db.bulk_save_objects(batch)
                self.db.commit()

            log.info(f"任务拆分完成: task_id={task.task_id}, 分片数={len(shards)}")

            return shards

        except Exception as e:
            log.error(f"拆分任务失败: {e}")
            raise

    def get_category_codes(self, channel: str) -> List[str]:
        """获取职位分类码"""
        # TODO: 从数据库或配置中获取
        if channel == "zhaopin":
            # 智联招聘主要分类（示例）
            return [
                "100001",  # 计算机/互联网/通信
                "100002",  # 销售/客服/技术支持
                "100003",  # 会计/金融/银行/保险
                "100004",  # 生产/营运/采购/物流
                "100005",  # 生物/制药/医疗/护理
                "100006",  # 广告/市场/媒体/艺术
                "100007",  # 建筑/房地产/物业
                "100008",  # 人事/行政/高级管理
                "100009",  # 咨询/法律/教育/科研
                "100010",  # 服务业
            ]
        else:
            return ["default"]

    def distribute_shards(self, shards: List[CrawlTaskShard]):
        """分发任务分片到消息队列"""
        try:
            task = None
            if shards:
                task = self.db.query(CrawlTask).filter(CrawlTask.task_id == shards[0].task_id).first()
            for shard in shards:
                # 创建分片消息
                message = TaskShardMessage(
                    shard_id=shard.shard_id,
                    task_id=shard.task_id,
                    keyword=shard.keyword,
                    city=shard.city,
                    category_code=shard.category_code,
                    page=shard.page,
                    retry_count=shard.retry_count,
                    incremental=bool(getattr(task, "incremental", False)),
                    baseline_task_id=getattr(task, "baseline_task_id", None),
                    watermark_publish_date=getattr(task, "watermark_publish_date", None),
                    watermark_crawl_time=getattr(task, "watermark_crawl_time", None),
                    stale_page_threshold=getattr(task, "stale_page_threshold", 2),
                    lookback_hours=getattr(task, "lookback_hours", 72),
                    search_keyword=shard.keyword,
                )

                # 发送到消息队列
                self.mq_producer.send_task_shard(message.dict())

                # 更新分片状态为运行中
                shard.status = 1
                shard.start_time = datetime.now()

            # 批量更新分片状态
            self.db.commit()

            log.info(f"分片分发完成: 分片数={len(shards)}")

        except Exception as e:
            log.error(f"分发分片失败: {e}")
            raise

    def start_task(self, task_id: str):
        """启动任务"""
        try:
            task = self.db.query(CrawlTask).filter(CrawlTask.task_id == task_id).first()
            if not task:
                log.error(f"任务不存在: {task_id}")
                return

            if task.schedule_type == "SCHEDULED_TEMPLATE":
                log.warning(f"定时模板任务不能直接启动: task_id={task_id}")
                return

            # 获取任务的所有分片
            shards = self.db.query(CrawlTaskShard).filter(
                CrawlTaskShard.task_id == task_id,
                CrawlTaskShard.status == 0  # 待执行
            ).all()

            if not shards:
                log.warning(f"任务没有待执行的分片: task_id={task_id}")
                return

            # 分发分片
            self.distribute_shards(shards)

            log.info(f"任务启动成功: task_id={task_id}, 分片数={len(shards)}")

        except Exception as e:
            log.error(f"启动任务失败: {e}")
            raise

    def update_shard_status(self, shard_id: str, status: int, worker_id: Optional[str] = None,
                            stop_reason: Optional[str] = None, new_count: Optional[int] = None,
                            updated_count: Optional[int] = None, duplicate_count: Optional[int] = None):
        """更新分片状态"""
        try:
            shard = self.db.query(CrawlTaskShard).filter(CrawlTaskShard.shard_id == shard_id).first()
            if not shard:
                log.error(f"分片不存在: {shard_id}")
                return

            # 更新状态
            shard.status = status
            if worker_id:
                worker_exists = self.db.query(CrawlWorker.worker_id).filter(
                    CrawlWorker.worker_id == worker_id
                ).first()
                if worker_exists:
                    shard.worker_id = worker_id
                else:
                    log.warning(
                        f"分片回写时忽略不存在的节点ID: shard_id={shard_id}, worker_id={worker_id}"
                    )
            if stop_reason:
                shard.stop_reason = stop_reason
            if new_count is not None:
                shard.new_count = new_count
            if updated_count is not None:
                shard.updated_count = updated_count
            if duplicate_count is not None:
                shard.duplicate_count = duplicate_count

            if status == 2:  # 完成
                shard.end_time = datetime.now()
            elif status == 3:  # 失败
                shard.retry_count += 1
                shard.end_time = datetime.now()

            self.db.commit()

            # 更新任务统计
            self.update_task_stats(shard.task_id)

            log.info(f"更新分片状态: shard_id={shard_id}, status={status}")

        except Exception as e:
            log.error(f"更新分片状态失败: {e}")
            raise

    def update_task_stats(self, task_id: str):
        """更新任务统计信息"""
        try:
            task = self.db.query(CrawlTask).filter(CrawlTask.task_id == task_id).first()
            if not task:
                return

            # 统计分片状态
            stats = self.db.query(
                CrawlTaskShard.status,
                func.count(CrawlTaskShard.shard_id).label("count")
            ).filter(CrawlTaskShard.task_id == task_id) \
             .group_by(CrawlTaskShard.status) \
             .all()

            # 计算完成数和去重数（这里简化处理，实际需要根据数据去重统计）
            finished_count = 0
            for stat in stats:
                if stat.status == 2:  # 完成
                    finished_count = stat.count

            task.finished_count = finished_count
            aggregate_counts = self.db.query(
                func.coalesce(func.sum(CrawlTaskShard.new_count), 0).label("new_count"),
                func.coalesce(func.sum(CrawlTaskShard.updated_count), 0).label("updated_count"),
                func.coalesce(func.sum(CrawlTaskShard.duplicate_count), 0).label("duplicate_count")
            ).filter(CrawlTaskShard.task_id == task_id).first()

            task.new_count = int(aggregate_counts.new_count or 0)
            task.updated_count = int(aggregate_counts.updated_count or 0)
            task.duplicate_count = int(aggregate_counts.duplicate_count or 0)

            # 检查任务是否完成
            if task.finished_count >= task.total_count and task.total_count > 0:
                task.status = 2  # 完成
                task.end_time = datetime.now()
                task.last_success_at = datetime.now()
                task.watermark_crawl_time = datetime.now()
                max_publish_date = self.db.query(func.max(JobData.publish_date)).filter(
                    JobData.task_id == task.task_id
                ).scalar()
                if max_publish_date and (
                    task.watermark_publish_date is None or max_publish_date > task.watermark_publish_date
                ):
                    task.watermark_publish_date = max_publish_date

            self.db.commit()

        except Exception as e:
            log.error(f"更新任务统计失败: {e}")
            raise

    def find_baseline_task_id(
        self,
        channel: str,
        keywords: Optional[List[str]],
        cities: Optional[List[str]]
    ) -> Optional[str]:
        """查找最近成功任务作为增量基线。"""
        candidates = self.db.query(CrawlTask).filter(
            CrawlTask.channel == channel,
            CrawlTask.status == 2
        ).order_by(CrawlTask.last_success_at.desc(), CrawlTask.end_time.desc(), CrawlTask.created_at.desc()).all()

        normalized_keywords = json.dumps(keywords, ensure_ascii=False) if keywords else None
        normalized_cities = json.dumps(cities, ensure_ascii=False) if cities else None

        for candidate in candidates:
            if candidate.keywords == normalized_keywords and candidate.city == normalized_cities:
                return candidate.task_id
        return candidates[0].task_id if candidates else None

    def create_scheduled_run(self, template_task_id: str) -> Optional[CrawlTask]:
        """由定时模板生成执行任务。"""
        template = self.db.query(CrawlTask).filter(CrawlTask.task_id == template_task_id).first()
        if not template or template.schedule_type != "SCHEDULED_TEMPLATE":
            return None

        keywords = json.loads(template.keywords) if template.keywords else None
        cities = json.loads(template.city) if template.city else None
        schedule_days = loads_schedule_days(template.schedule_days)
        baseline_task_id = template.baseline_task_id or self.find_baseline_task_id(template.channel, keywords, cities)

        task_data = TaskCreate(
            task_name=f"{template.task_name}_{datetime.now().strftime('%Y%m%d%H%M%S')}",
            channel=template.channel,
            keywords=keywords,
            city=cities,
            page_count=template.page_count,
            priority=template.priority,
            schedule_type="SCHEDULED_RUN",
            schedule_mode=None,
            cron_expression=template.cron_expression,
            schedule_enabled=bool(template.schedule_enabled),
            schedule_timezone=template.schedule_timezone,
            schedule_preset=template.schedule_preset,
            schedule_time=template.schedule_time,
            schedule_days=schedule_days,
            incremental=bool(template.incremental),
            baseline_task_id=baseline_task_id,
            incremental_page_limit=template.incremental_page_limit,
            stale_page_threshold=template.stale_page_threshold,
            lookback_hours=template.lookback_hours,
            create_user=template.create_user,
        )
        scheduled_run = self.create_task(task_data)
        scheduled_run.parent_task_id = template.task_id
        scheduled_run.schedule_type = "SCHEDULED_RUN"
        scheduled_run.status = 1
        scheduled_run.start_time = datetime.now()

        if baseline_task_id:
            baseline_task = self.db.query(CrawlTask).filter(CrawlTask.task_id == baseline_task_id).first()
            if baseline_task:
                scheduled_run.watermark_publish_date = baseline_task.watermark_publish_date
                scheduled_run.watermark_crawl_time = baseline_task.watermark_crawl_time

        template.last_run_time = datetime.now()
        _, _, normalized_days, cron_expression, next_run_time = build_schedule(
            schedule_type="SCHEDULED_TEMPLATE",
            schedule_preset=template.schedule_preset,
            schedule_time=template.schedule_time,
            schedule_days=schedule_days,
            cron_expression=template.cron_expression,
            schedule_timezone=template.schedule_timezone,
            now=datetime.now(),
        )
        template.schedule_days = dumps_schedule_days(normalized_days)
        template.cron_expression = cron_expression
        template.next_run_time = next_run_time

        self.db.commit()
        self.db.refresh(scheduled_run)
        self.process_task(scheduled_run.task_id)
        return scheduled_run

    def get_due_templates(self, now: Optional[datetime] = None) -> List[CrawlTask]:
        """获取到期的定时模板任务。"""
        current = now or datetime.now()
        return self.db.query(CrawlTask).filter(
            CrawlTask.schedule_type == "SCHEDULED_TEMPLATE",
            CrawlTask.schedule_enabled == 1,
            CrawlTask.next_run_time.isnot(None),
            CrawlTask.next_run_time <= current
        ).all()
