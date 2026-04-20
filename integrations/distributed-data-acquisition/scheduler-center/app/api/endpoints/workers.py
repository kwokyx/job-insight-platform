"""
节点管理端点
"""
from datetime import datetime, timedelta
from typing import Optional, List
from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from sqlalchemy import desc, func

from app.db.session import get_db
from app.schemas.base import ResponseSchema, PaginatedResponse
from app.schemas.task import WorkerResponse, WorkerStats
from app.models.task import CrawlWorker, CrawlTaskShard
from app.core.logger import log

router = APIRouter()


@router.get("/", response_model=ResponseSchema)
async def get_workers(
    page: int = Query(1, ge=1, description="页码"),
    size: int = Query(20, ge=1, le=100, description="每页大小"),
    status: Optional[int] = Query(None, ge=0, le=1, description="状态"),
    db: Session = Depends(get_db),
):
    """获取节点列表"""
    try:
        # 构建查询
        query = db.query(CrawlWorker)

        # 过滤条件
        if status is not None:
            query = query.filter(CrawlWorker.status == status)

        # 获取总数
        total = query.count()

        # 分页查询
        workers = query.order_by(desc(CrawlWorker.last_heartbeat)) \
            .offset((page - 1) * size) \
            .limit(size) \
            .all()

        # 转换为响应模型
        worker_list = [WorkerResponse(**worker.to_dict()) for worker in workers]

        return PaginatedResponse.success(
            items=worker_list,
            total=total,
            page=page,
            size=size
        )
    except Exception as e:
        log.error(f"获取节点列表失败: {e}")
        return ResponseSchema.error(message="获取节点列表失败")


@router.get("/{worker_id}", response_model=ResponseSchema)
async def get_worker(worker_id: str, db: Session = Depends(get_db)):
    """获取节点详情"""
    try:
        worker = db.query(CrawlWorker).filter(CrawlWorker.worker_id == worker_id).first()
        if not worker:
            return ResponseSchema.error(code=404, message="节点不存在")

        # 获取节点统计信息
        stats = get_worker_stats(worker, db)

        response_data = worker.to_dict()
        response_data["stats"] = stats.dict()

        return ResponseSchema.success(data=response_data)
    except Exception as e:
        log.error(f"获取节点详情失败: {e}")
        return ResponseSchema.error(message="获取节点详情失败")


@router.delete("/{worker_id}", response_model=ResponseSchema)
async def delete_worker(worker_id: str, db: Session = Depends(get_db)):
    """删除节点"""
    try:
        worker = db.query(CrawlWorker).filter(CrawlWorker.worker_id == worker_id).first()
        if not worker:
            return ResponseSchema.error(code=404, message="节点不存在")

        # 检查节点是否有正在执行的任务
        running_shards = db.query(CrawlTaskShard).filter(
            CrawlTaskShard.worker_id == worker_id,
            CrawlTaskShard.status == 1  # 运行中
        ).count()

        if running_shards > 0:
            return ResponseSchema.error(message=f"节点有{running_shards}个运行中的任务，不能删除")

        db.delete(worker)
        db.commit()

        return ResponseSchema.success(message="节点删除成功")
    except Exception as e:
        db.rollback()
        log.error(f"删除节点失败: {e}")
        return ResponseSchema.error(message="删除节点失败")


@router.post("/{worker_id}/heartbeat", response_model=ResponseSchema)
async def receive_heartbeat(
    worker_id: str,
    cpu_usage: float = Query(..., ge=0, le=100, description="CPU使用率"),
    memory_usage: float = Query(..., ge=0, le=100, description="内存使用率"),
    current_tasks: int = Query(..., ge=0, description="当前任务数"),
    db: Session = Depends(get_db),
):
    """接收节点心跳"""
    try:
        worker = db.query(CrawlWorker).filter(CrawlWorker.worker_id == worker_id).first()

        if not worker:
            # 节点不存在，创建新节点
            worker = CrawlWorker(
                worker_id=worker_id,
                ip="unknown",  # TODO: 从请求中获取IP
                status=1,
                cpu_usage=int(cpu_usage),
                memory_usage=int(memory_usage),
                current_task_id=None,
                last_heartbeat=datetime.now()
            )
            db.add(worker)
            log.info(f"注册新节点: {worker_id}")
        else:
            # 更新节点状态
            worker.cpu_usage = int(cpu_usage)
            worker.memory_usage = int(memory_usage)
            worker.last_heartbeat = datetime.now()
            worker.status = 1  # 在线

        db.commit()

        return ResponseSchema.success(message="心跳接收成功")
    except Exception as e:
        db.rollback()
        log.error(f"处理心跳失败: {e}")
        return ResponseSchema.error(message="处理心跳失败")


@router.get("/{worker_id}/stats", response_model=ResponseSchema)
async def get_worker_statistics(worker_id: str, db: Session = Depends(get_db)):
    """获取节点统计信息"""
    try:
        worker = db.query(CrawlWorker).filter(CrawlWorker.worker_id == worker_id).first()
        if not worker:
            return ResponseSchema.error(code=404, message="节点不存在")

        stats = get_worker_stats(worker, db)
        return ResponseSchema.success(data=stats.dict())
    except Exception as e:
        log.error(f"获取节点统计失败: {e}")
        return ResponseSchema.error(message="获取节点统计失败")


@router.get("/{worker_id}/shards", response_model=ResponseSchema)
async def get_worker_shards(
    worker_id: str,
    page: int = Query(1, ge=1, description="页码"),
    size: int = Query(20, ge=1, le=100, description="每页大小"),
    status: Optional[int] = Query(None, ge=0, le=3, description="状态"),
    db: Session = Depends(get_db),
):
    """获取节点任务分片"""
    try:
        worker = db.query(CrawlWorker).filter(CrawlWorker.worker_id == worker_id).first()
        if not worker:
            return ResponseSchema.error(code=404, message="节点不存在")

        # 构建查询
        query = db.query(CrawlTaskShard).filter(CrawlTaskShard.worker_id == worker_id)

        if status is not None:
            query = query.filter(CrawlTaskShard.status == status)

        # 获取总数
        total = query.count()

        # 分页查询
        shards = query.order_by(desc(CrawlTaskShard.start_time)) \
            .offset((page - 1) * size) \
            .limit(size) \
            .all()

        # 转换为字典
        shard_list = [shard.to_dict() for shard in shards]

        return PaginatedResponse.success(
            items=shard_list,
            total=total,
            page=page,
            size=size
        )
    except Exception as e:
        log.error(f"获取节点分片失败: {e}")
        return ResponseSchema.error(message="获取节点分片失败")


def get_worker_stats(worker: CrawlWorker, db: Session) -> WorkerStats:
    """获取节点统计信息"""
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

    # 检查节点是否离线
    status_text = "ONLINE"
    if worker.status == 0:
        status_text = "OFFLINE"
    elif worker.last_heartbeat < datetime.now() - timedelta(seconds=90):
        status_text = "TIMEOUT"

    return WorkerStats(
        worker_id=worker.worker_id,
        status=status_text,
        cpu_usage=worker.cpu_usage,
        memory_usage=worker.memory_usage,
        current_tasks=db.query(CrawlTaskShard).filter(
            CrawlTaskShard.worker_id == worker.worker_id,
            CrawlTaskShard.status == 1  # 运行中
        ).count(),
        total_shards=total_shards,
        completed_shards=completed_shards
    )


@router.post("/cleanup", response_model=ResponseSchema)
async def cleanup_workers(db: Session = Depends(get_db)):
    """清理离线节点"""
    try:
        # 查找超时节点（最后心跳时间超过90秒）
        timeout_threshold = datetime.now() - timedelta(seconds=90)
        offline_workers = db.query(CrawlWorker).filter(
            CrawlWorker.last_heartbeat < timeout_threshold,
            CrawlWorker.status == 1
        ).all()

        cleaned_count = 0
        for worker in offline_workers:
            worker.status = 0  # 标记为离线
            cleaned_count += 1

        if cleaned_count > 0:
            db.commit()
            log.info(f"清理离线节点: {cleaned_count}个")

        return ResponseSchema.success(
            message=f"清理完成，标记{cleaned_count}个节点为离线"
        )
    except Exception as e:
        db.rollback()
        log.error(f"清理节点失败: {e}")
        return ResponseSchema.error(message="清理节点失败")