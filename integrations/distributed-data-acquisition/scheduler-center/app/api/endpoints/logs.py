"""
日志查询端点
"""
from datetime import datetime, timedelta
from typing import Optional
from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session
from sqlalchemy import desc, func

from app.db.session import get_db
from app.schemas.base import ResponseSchema, PaginatedResponse
from app.models.task import CrawlTaskLog
from app.core.logger import log

router = APIRouter()


@router.get("/", response_model=ResponseSchema)
async def get_logs(
    page: int = Query(1, ge=1, description="页码"),
    size: int = Query(20, ge=1, le=100, description="每页大小"),
    level: Optional[str] = Query(None, description="日志级别"),
    task_id: Optional[str] = Query(None, description="任务ID"),
    worker_id: Optional[str] = Query(None, description="节点ID"),
    start_time: Optional[datetime] = Query(None, description="开始时间"),
    end_time: Optional[datetime] = Query(None, description="结束时间"),
    keyword: Optional[str] = Query(None, description="关键词搜索"),
    db: Session = Depends(get_db),
):
    """查询日志"""
    try:
        # 构建查询
        query = db.query(CrawlTaskLog)

        # 过滤条件
        if level:
            query = query.filter(CrawlTaskLog.level == level)
        if task_id:
            query = query.filter(CrawlTaskLog.task_id == task_id)
        if worker_id:
            query = query.filter(CrawlTaskLog.worker_id == worker_id)
        if start_time:
            query = query.filter(CrawlTaskLog.created_at >= start_time)
        if end_time:
            query = query.filter(CrawlTaskLog.created_at <= end_time)
        if keyword:
            query = query.filter(CrawlTaskLog.message.contains(keyword))

        # 获取总数
        total = query.count()

        # 分页查询
        logs = query.order_by(desc(CrawlTaskLog.created_at)) \
            .offset((page - 1) * size) \
            .limit(size) \
            .all()

        # 转换为字典
        log_list = [log_item.to_dict() for log_item in logs]

        return PaginatedResponse.success(
            items=log_list,
            total=total,
            page=page,
            size=size
        )
    except Exception as e:
        log.error(f"查询日志失败: {e}")
        return ResponseSchema.error(message="查询日志失败")


@router.get("/stats", response_model=ResponseSchema)
async def get_log_statistics(
    days: int = Query(7, ge=1, le=30, description="统计天数"),
    db: Session = Depends(get_db),
):
    """获取日志统计"""
    try:
        end_date = datetime.now()
        start_date = end_date - timedelta(days=days)

        # 按级别统计
        level_stats = db.query(
            CrawlTaskLog.level,
            func.count(CrawlTaskLog.log_id).label("count")
        ).filter(
            CrawlTaskLog.created_at >= start_date,
            CrawlTaskLog.created_at <= end_date
        ).group_by(CrawlTaskLog.level).all()

        # 按天统计
        daily_stats = db.query(
            func.date(CrawlTaskLog.created_at).label("date"),
            func.count(CrawlTaskLog.log_id).label("count")
        ).filter(
            CrawlTaskLog.created_at >= start_date,
            CrawlTaskLog.created_at <= end_date
        ).group_by(func.date(CrawlTaskLog.created_at)) \
         .order_by(func.date(CrawlTaskLog.created_at)) \
         .all()

        # 按任务统计
        task_stats = db.query(
            CrawlTaskLog.task_id,
            func.count(CrawlTaskLog.log_id).label("count")
        ).filter(
            CrawlTaskLog.created_at >= start_date,
            CrawlTaskLog.created_at <= end_date
        ).group_by(CrawlTaskLog.task_id) \
         .order_by(func.count(CrawlTaskLog.log_id).desc()) \
         .limit(10) \
         .all()

        result = {
            "level_stats": [
                {"level": stat.level, "count": stat.count}
                for stat in level_stats
            ],
            "daily_stats": [
                {"date": stat.date.strftime("%Y-%m-%d"), "count": stat.count}
                for stat in daily_stats
            ],
            "task_stats": [
                {"task_id": stat.task_id, "count": stat.count}
                for stat in task_stats
            ]
        }

        return ResponseSchema.success(data=result)

    except Exception as e:
        log.error(f"获取日志统计失败: {e}")
        return ResponseSchema.error(message="获取日志统计失败")


@router.delete("/cleanup", response_model=ResponseSchema)
async def cleanup_logs(
    days: int = Query(30, ge=1, description="保留天数"),
    db: Session = Depends(get_db),
):
    """清理旧日志"""
    try:
        cutoff_date = datetime.now() - timedelta(days=days)

        # 删除旧日志
        deleted_count = db.query(CrawlTaskLog).filter(
            CrawlTaskLog.created_at < cutoff_date
        ).delete()

        db.commit()

        log.info(f"清理日志: 删除{deleted_count}条{days}天前的日志")

        return ResponseSchema.success(
            message=f"清理完成，删除{deleted_count}条日志"
        )
    except Exception as e:
        db.rollback()
        log.error(f"清理日志失败: {e}")
        return ResponseSchema.error(message="清理日志失败")