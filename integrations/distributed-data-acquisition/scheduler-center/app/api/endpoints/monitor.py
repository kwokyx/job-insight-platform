"""
监控统计端点
"""
from datetime import datetime, timedelta, date
from typing import List
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from sqlalchemy import func, and_

from app.db.session import get_db
from app.schemas.base import ResponseSchema
from app.schemas.task import DashboardResponse, DashboardStats, WorkerStats, TaskTrendItem
from app.models.task import CrawlTask, CrawlWorker, CrawlTaskShard, JobData
from app.core.logger import log

router = APIRouter()


@router.get("/dashboard", response_model=ResponseSchema)
async def get_dashboard(db: Session = Depends(get_db)):
    """获取监控仪表盘数据"""
    try:
        # 基本统计
        total_tasks = db.query(CrawlTask).count()
        running_tasks = db.query(CrawlTask).filter(CrawlTask.status == 1).count()
        online_workers = db.query(CrawlWorker).filter(CrawlWorker.status == 1).count()

        # 今日采集量
        today_start = datetime.combine(date.today(), datetime.min.time())
        today_collected = db.query(JobData).filter(
            JobData.crawl_time >= today_start
        ).count()

        # 成功率（今日完成的分片 / 今日总分片）
        today_shards_completed = db.query(CrawlTaskShard).filter(
            CrawlTaskShard.status == 2,
            CrawlTaskShard.end_time >= today_start
        ).count()

        today_shards_total = db.query(CrawlTaskShard).filter(
            CrawlTaskShard.start_time >= today_start
        ).count()

        success_rate = 0.0
        if today_shards_total > 0:
            success_rate = round(today_shards_completed / today_shards_total * 100, 2)

        # 异常数量（今日失败的分片）
        error_count = db.query(CrawlTaskShard).filter(
            CrawlTaskShard.status == 3,
            CrawlTaskShard.end_time >= today_start
        ).count()

        # 构建统计对象
        stats = DashboardStats(
            total_tasks=total_tasks,
            running_tasks=running_tasks,
            online_workers=online_workers,
            today_collected=today_collected,
            success_rate=success_rate,
            error_count=error_count
        )

        # 节点统计
        worker_stats = get_worker_stats_list(db)

        # 任务趋势（最近7天）
        task_trend = get_task_trend(db)

        # 构建响应
        dashboard_data = DashboardResponse(
            stats=stats,
            worker_stats=worker_stats,
            task_trend=task_trend
        )

        return ResponseSchema.success(data=dashboard_data.dict())

    except Exception as e:
        log.error(f"获取仪表盘数据失败: {e}")
        return ResponseSchema.error(message="获取仪表盘数据失败")


@router.get("/task-stats", response_model=ResponseSchema)
async def get_task_statistics(db: Session = Depends(get_db)):
    """获取任务统计"""
    try:
        # 任务状态分布
        status_distribution = db.query(
            CrawlTask.status,
            func.count(CrawlTask.task_id).label("count")
        ).group_by(CrawlTask.status).all()

        # 渠道分布
        channel_distribution = db.query(
            CrawlTask.channel,
            func.count(CrawlTask.task_id).label("count")
        ).group_by(CrawlTask.channel).all()

        # 每日任务完成趋势（最近30天）
        trend_data = []
        for i in range(30):
            day = date.today() - timedelta(days=i)
            day_start = datetime.combine(day, datetime.min.time())
            day_end = datetime.combine(day, datetime.max.time())

            completed_tasks = db.query(CrawlTask).filter(
                CrawlTask.status == 2,
                CrawlTask.end_time >= day_start,
                CrawlTask.end_time <= day_end
            ).count()

            trend_data.append({
                "date": day.strftime("%Y-%m-%d"),
                "count": completed_tasks
            })

        trend_data.reverse()

        result = {
            "status_distribution": [
                {"status": stat.status, "count": stat.count}
                for stat in status_distribution
            ],
            "channel_distribution": [
                {"channel": chan.channel, "count": chan.count}
                for chan in channel_distribution
            ],
            "trend": trend_data
        }

        return ResponseSchema.success(data=result)

    except Exception as e:
        log.error(f"获取任务统计失败: {e}")
        return ResponseSchema.error(message="获取任务统计失败")


@router.get("/worker-stats", response_model=ResponseSchema)
async def get_worker_statistics(db: Session = Depends(get_db)):
    """获取节点统计"""
    try:
        worker_stats = get_worker_stats_list(db)
        return ResponseSchema.success(data=worker_stats)
    except Exception as e:
        log.error(f"获取节点统计失败: {e}")
        return ResponseSchema.error(message="获取节点统计失败")


@router.get("/data-stats", response_model=ResponseSchema)
async def get_data_statistics(db: Session = Depends(get_db)):
    """获取数据统计"""
    try:
        # 总数据量
        total_data = db.query(JobData).count()

        # 今日新增
        today_start = datetime.combine(date.today(), datetime.min.time())
        today_new = db.query(JobData).filter(JobData.crawl_time >= today_start).count()

        # 城市分布
        city_distribution = db.query(
            JobData.job_city,
            func.count(JobData.url_obj_id).label("count")
        ).group_by(JobData.job_city) \
         .order_by(func.count(JobData.url_obj_id).desc()) \
         .limit(10) \
         .all()

        # 薪资分布
        salary_distribution = db.query(
            func.floor(JobData.salary_min / 1000).label("salary_range"),
            func.count(JobData.url_obj_id).label("count")
        ).filter(JobData.salary_min > 0) \
         .group_by(func.floor(JobData.salary_min / 1000)) \
         .order_by(func.floor(JobData.salary_min / 1000)) \
         .all()

        result = {
            "total_data": total_data,
            "today_new": today_new,
            "city_distribution": [
                {"city": city.job_city, "count": city.count}
                for city in city_distribution
            ],
            "salary_distribution": [
                {"range": f"{salary.salary_range}k-{salary.salary_range + 1}k", "count": salary.count}
                for salary in salary_distribution
            ]
        }

        return ResponseSchema.success(data=result)

    except Exception as e:
        log.error(f"获取数据统计失败: {e}")
        return ResponseSchema.error(message="获取数据统计失败")


def get_worker_stats_list(db: Session) -> List[WorkerStats]:
    """获取节点统计列表"""
    workers = db.query(CrawlWorker).all()
    worker_stats_list = []

    for worker in workers:
        # 统计分片状态
        shard_stats = db.query(
            CrawlTaskShard.status,
            func.count(CrawlTaskShard.shard_id).label("count")
        ).filter(CrawlTaskShard.worker_id == worker.worker_id) \
         .group_by(CrawlTaskShard.status) \
         .all()

        total_shards = 0
        completed_shards = 0

        for stat in shard_stats:
            total_shards += stat.count
            if stat.status == 2:  # 完成
                completed_shards = stat.count

        # 检查节点状态
        status_text = "ONLINE"
        if worker.status == 0:
            status_text = "OFFLINE"
        elif worker.last_heartbeat < datetime.now() - timedelta(seconds=90):
            status_text = "TIMEOUT"

        worker_stats = WorkerStats(
            worker_id=worker.worker_id,
            status=status_text,
            cpu_usage=worker.cpu_usage,
            memory_usage=worker.memory_usage,
            current_tasks=db.query(CrawlTaskShard).filter(
                CrawlTaskShard.worker_id == worker.worker_id,
                CrawlTaskShard.status == 1
            ).count(),
            total_shards=total_shards,
            completed_shards=completed_shards
        )

        worker_stats_list.append(worker_stats)

    return worker_stats_list


def get_task_trend(db: Session, days: int = 7) -> List[TaskTrendItem]:
    """获取任务趋势"""
    trend = []
    for i in range(days):
        day = date.today() - timedelta(days=i)
        day_start = datetime.combine(day, datetime.min.time())
        day_end = datetime.combine(day, datetime.max.time())

        # 当日采集的数据量
        collected_count = db.query(JobData).filter(
            JobData.crawl_time >= day_start,
            JobData.crawl_time <= day_end
        ).count()

        trend.append(TaskTrendItem(
            date=day.strftime("%Y-%m-%d"),
            count=collected_count
        ))

    trend.reverse()  # 按时间顺序排列
    return trend