"""
任务管理端点
"""
from datetime import datetime
import json
from typing import Optional, List
from fastapi import APIRouter, Depends, HTTPException, Query, BackgroundTasks
from sqlalchemy.orm import Session
from sqlalchemy import desc, func

from app.db.session import get_db
from app.schemas.base import ResponseSchema, PaginatedResponse, PaginationSchema
from app.schemas.task import TaskCreate, TaskUpdate, TaskResponse, TaskListResponse, TaskShardResponse
from app.models.task import CrawlTask, CrawlTaskShard
from app.services.task_service import TaskService
from app.core.logger import log

router = APIRouter()


def _decode_json_list(value):
    """将数据库中的JSON数组字符串还原为列表。"""
    if value is None or isinstance(value, list):
        return value
    if isinstance(value, str):
        try:
            parsed = json.loads(value)
            return parsed if isinstance(parsed, list) else value
        except json.JSONDecodeError:
            return [item.strip() for item in value.split(",") if item.strip()]
    return value


def _normalize_task_dict(task_dict):
    """兼容旧库字段名，统一为响应Schema使用的字段名。"""
    if "create_time" in task_dict and "created_at" not in task_dict:
        task_dict["created_at"] = task_dict.pop("create_time")
    if "update_time" in task_dict and "updated_at" not in task_dict:
        task_dict["updated_at"] = task_dict.pop("update_time")
    return task_dict


def _model_to_dict(model):
    """将SQLAlchemy模型转换为字典，避免依赖未实现的to_dict。"""
    data = {}
    for column in model.__table__.columns:
        value = getattr(model, column.key, None)
        data[column.name] = value
    return data


@router.get("/", response_model=ResponseSchema)
async def get_tasks(
    page: int = Query(1, ge=1, description="页码"),
    size: int = Query(20, ge=1, le=100, description="每页大小"),
    status: Optional[int] = Query(None, ge=0, le=3, description="状态"),
    channel: Optional[str] = Query(None, description="渠道"),
    keyword: Optional[str] = Query(None, description="关键词搜索"),
    db: Session = Depends(get_db),
):
    """获取任务列表"""
    try:
        # 构建查询
        query = db.query(CrawlTask)

        # 过滤条件
        if status is not None:
            query = query.filter(CrawlTask.status == status)
        if channel:
            query = query.filter(CrawlTask.channel == channel)
        if keyword:
            query = query.filter(CrawlTask.task_name.contains(keyword))

        # 获取总数
        total = query.count()

        # 分页查询
        tasks = query.order_by(desc(CrawlTask.created_at)) \
            .offset((page - 1) * size) \
            .limit(size) \
            .all()

        # 转换为响应模型
        task_list = []
        for task in tasks:
            task_dict = _normalize_task_dict(_model_to_dict(task))
            task_dict["keywords"] = _decode_json_list(task_dict.get("keywords"))
            task_dict["city"] = _decode_json_list(task_dict.get("city"))
            task_dict["schedule_enabled"] = bool(task_dict.get("schedule_enabled", 1))
            task_dict["incremental"] = bool(task_dict.get("incremental", 0))
            task_dict["schedule_days"] = _decode_json_list(task_dict.get("schedule_days"))
            task_list.append(TaskResponse(**task_dict))

        return PaginatedResponse.success(
            items=task_list,
            total=total,
            page=page,
            size=size
        )
    except Exception as e:
        log.error(f"获取任务列表失败: {e}")
        return ResponseSchema.error(message="获取任务列表失败")


@router.get("/{task_id}", response_model=ResponseSchema)
async def get_task(task_id: str, db: Session = Depends(get_db)):
    """获取任务详情"""
    try:
        task = db.query(CrawlTask).filter(CrawlTask.task_id == task_id).first()
        if not task:
            return ResponseSchema.error(code=404, message="任务不存在")

        task_dict = _normalize_task_dict(_model_to_dict(task))
        task_dict["keywords"] = _decode_json_list(task_dict.get("keywords"))
        task_dict["city"] = _decode_json_list(task_dict.get("city"))
        task_dict["schedule_enabled"] = bool(task_dict.get("schedule_enabled", 1))
        task_dict["incremental"] = bool(task_dict.get("incremental", 0))
        task_dict["schedule_days"] = _decode_json_list(task_dict.get("schedule_days"))

        return ResponseSchema.success(data=TaskResponse(**task_dict))
    except Exception as e:
        log.error(f"获取任务详情失败: {e}")
        return ResponseSchema.error(message="获取任务详情失败")


@router.post("/", response_model=ResponseSchema)
async def create_task(
    task_data: TaskCreate,
    background_tasks: BackgroundTasks,
    db: Session = Depends(get_db),
):
    """创建采集任务"""
    try:
        # 调用任务服务创建任务
        task_service = TaskService(db)
        task = task_service.create_task(task_data)

        # 立即任务和定时执行子任务需要预先拆分
        if task.schedule_type != "SCHEDULED_TEMPLATE":
            background_tasks.add_task(task_service.process_task, task.task_id)

        return ResponseSchema.success(
            data={"task_id": task.task_id, "status": task.status, "schedule_type": task.schedule_type},
            message="任务创建成功"
        )
    except Exception as e:
        log.error(f"创建任务失败: {e}")
        return ResponseSchema.error(message=f"创建任务失败: {e}")


@router.put("/{task_id}", response_model=ResponseSchema)
async def update_task(
    task_id: str,
    task_update: TaskUpdate,
    db: Session = Depends(get_db),
):
    """更新任务"""
    try:
        task = db.query(CrawlTask).filter(CrawlTask.task_id == task_id).first()
        if not task:
            return ResponseSchema.error(code=404, message="任务不存在")

        # 更新字段
        update_data = task_update.dict(exclude_unset=True)
        for field, value in update_data.items():
            if value is not None:
                setattr(task, field, value)

        task.updated_at = datetime.now()
        db.commit()

        return ResponseSchema.success(message="任务更新成功")
    except Exception as e:
        db.rollback()
        log.error(f"更新任务失败: {e}")
        return ResponseSchema.error(message="更新任务失败")


@router.delete("/{task_id}", response_model=ResponseSchema)
async def delete_task(task_id: str, db: Session = Depends(get_db)):
    """删除任务"""
    try:
        task = db.query(CrawlTask).filter(CrawlTask.task_id == task_id).first()
        if not task:
            return ResponseSchema.error(code=404, message="任务不存在")

        # 只有未运行的任务可以删除
        if task.status == 1:  # 运行中
            return ResponseSchema.error(message="运行中的任务不能删除")

        db.delete(task)
        db.commit()

        return ResponseSchema.success(message="任务删除成功")
    except Exception as e:
        db.rollback()
        log.error(f"删除任务失败: {e}")
        return ResponseSchema.error(message="删除任务失败")


@router.post("/{task_id}/start", response_model=ResponseSchema)
async def start_task(
    task_id: str,
    background_tasks: BackgroundTasks,
    db: Session = Depends(get_db),
):
    """启动任务"""
    try:
        task = db.query(CrawlTask).filter(CrawlTask.task_id == task_id).first()
        if not task:
            return ResponseSchema.error(code=404, message="任务不存在")

        if task.status != 0:  # 非待执行状态
            return ResponseSchema.error(message="任务当前状态不能启动")

        if task.schedule_type == "SCHEDULED_TEMPLATE":
            return ResponseSchema.error(message="定时模板任务不能手动启动执行")

        # 更新任务状态
        task.status = 1
        task.start_time = datetime.now()
        db.commit()

        # 异步启动任务处理
        task_service = TaskService(db)
        background_tasks.add_task(task_service.start_task, task_id)

        return ResponseSchema.success(message="任务启动成功")
    except Exception as e:
        db.rollback()
        log.error(f"启动任务失败: {e}")
        return ResponseSchema.error(message="启动任务失败")


@router.post("/{task_id}/pause", response_model=ResponseSchema)
async def pause_task(task_id: str, db: Session = Depends(get_db)):
    """暂停任务"""
    try:
        task = db.query(CrawlTask).filter(CrawlTask.task_id == task_id).first()
        if not task:
            return ResponseSchema.error(code=404, message="任务不存在")

        if task.status != 1:  # 非运行中状态
            return ResponseSchema.error(message="只有运行中的任务可以暂停")

        # 更新任务状态为待执行（允许重新启动）
        task.status = 0
        db.commit()

        return ResponseSchema.success(message="任务暂停成功")
    except Exception as e:
        db.rollback()
        log.error(f"暂停任务失败: {e}")
        return ResponseSchema.error(message="暂停任务失败")


@router.get("/{task_id}/shards", response_model=ResponseSchema)
async def get_task_shards(
    task_id: str,
    page: int = Query(1, ge=1, description="页码"),
    size: int = Query(20, ge=1, le=100, description="每页大小"),
    status: Optional[int] = Query(None, ge=0, le=3, description="状态"),
    db: Session = Depends(get_db),
):
    """获取任务分片列表"""
    try:
        # 验证任务存在
        task = db.query(CrawlTask).filter(CrawlTask.task_id == task_id).first()
        if not task:
            return ResponseSchema.error(code=404, message="任务不存在")

        # 构建查询
        query = db.query(CrawlTaskShard).filter(CrawlTaskShard.task_id == task_id)

        if status is not None:
            query = query.filter(CrawlTaskShard.status == status)

        # 获取总数
        total = query.count()

        # 分页查询
        shards = query.order_by(CrawlTaskShard.page) \
            .offset((page - 1) * size) \
            .limit(size) \
            .all()

        # 转换为响应模型
        shard_list = [TaskShardResponse(**_normalize_task_dict(_model_to_dict(shard))) for shard in shards]

        return PaginatedResponse.success(
            items=shard_list,
            total=total,
            page=page,
            size=size
        )
    except Exception as e:
        log.error(f"获取任务分片失败: {e}")
        return ResponseSchema.error(message="获取任务分片失败")


@router.get("/{task_id}/stats", response_model=ResponseSchema)
async def get_task_stats(task_id: str, db: Session = Depends(get_db)):
    """获取任务统计信息"""
    try:
        task = db.query(CrawlTask).filter(CrawlTask.task_id == task_id).first()
        if not task:
            return ResponseSchema.error(code=404, message="任务不存在")

        # 统计分片状态
        shard_stats = db.query(
            CrawlTaskShard.status,
            func.count(CrawlTaskShard.shard_id).label("count")
        ).filter(CrawlTaskShard.task_id == task_id) \
         .group_by(CrawlTaskShard.status) \
         .all()

        stats = {
            "total_shards": sum(stat.count for stat in shard_stats),
            "pending_shards": 0,
            "running_shards": 0,
            "completed_shards": 0,
            "failed_shards": 0,
            "new_count": task.new_count,
            "updated_count": task.updated_count,
            "duplicate_count": task.duplicate_count,
        }

        for stat in shard_stats:
            if stat.status == 0:
                stats["pending_shards"] = stat.count
            elif stat.status == 1:
                stats["running_shards"] = stat.count
            elif stat.status == 2:
                stats["completed_shards"] = stat.count
            elif stat.status == 3:
                stats["failed_shards"] = stat.count

        return ResponseSchema.success(data=stats)
    except Exception as e:
        log.error(f"获取任务统计失败: {e}")
        return ResponseSchema.error(message="获取任务统计失败")
