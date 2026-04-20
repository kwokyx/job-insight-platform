"""
任务相关Schema
"""
from datetime import datetime
from typing import Optional, List, Dict, Any
from pydantic import BaseModel, Field, validator
import json

from .base import BaseSchema


# ==================== 任务相关 ====================

class TaskCreate(BaseSchema):
    """创建任务请求"""
    task_name: str = Field(..., min_length=1, max_length=100, description="任务名称")
    channel: str = Field("zhaopin", description="采集渠道: zhaopin/boss")
    keywords: Optional[List[str]] = Field(None, description="搜索关键词列表")
    city: Optional[List[str]] = Field(None, description="采集城市列表")
    page_count: int = Field(3, ge=1, le=100, description="每个关键词/城市/分类组合采集页数")
    priority: int = Field(5, ge=1, le=10, description="调度优先级，数值越小优先级越高")
    schedule_type: str = Field("IMMEDIATE", description="调度类型: IMMEDIATE/SCHEDULED_TEMPLATE/SCHEDULED_RUN")
    schedule_mode: Optional[str] = Field(None, description="前端简化调度模式: IMMEDIATE/SCHEDULED")
    cron_expression: Optional[str] = Field(None, description="Cron表达式")
    schedule_enabled: bool = Field(True, description="定时任务是否启用")
    schedule_timezone: str = Field("Asia/Shanghai", description="调度时区")
    schedule_preset: Optional[str] = Field(None, description="简单频率: DAILY/WEEKLY/MONTHLY")
    schedule_time: Optional[str] = Field(None, description="简单频率时间 HH:mm")
    schedule_days: Optional[List[int]] = Field(None, description="周几或每月日期")
    incremental: bool = Field(False, description="是否增量采集")
    baseline_task_id: Optional[str] = Field(None, description="增量任务基线任务ID")
    incremental_page_limit: int = Field(2, ge=1, le=20, description="增量采集页数")
    stale_page_threshold: int = Field(1, ge=1, le=10, description="连续旧页阈值")
    lookback_hours: int = Field(72, ge=1, le=720, description="发布时间回看小时数")
    create_user: Optional[str] = Field(None, description="创建人")

    @validator('keywords')
    def validate_keywords(cls, v):
        if v is not None and len(v) > 100:
            raise ValueError("关键词数量不能超过100个")
        return v

    @validator('city')
    def validate_city(cls, v):
        if v is not None and len(v) > 50:
            raise ValueError("城市数量不能超过50个")
        return v

    @validator('schedule_type')
    def validate_schedule_type(cls, v):
        if v not in ["IMMEDIATE", "SCHEDULED_TEMPLATE", "SCHEDULED_RUN"]:
            raise ValueError("调度类型必须是IMMEDIATE、SCHEDULED_TEMPLATE或SCHEDULED_RUN")
        return v

    @validator('schedule_mode')
    def validate_schedule_mode(cls, v):
        if v is not None and v not in ["IMMEDIATE", "SCHEDULED"]:
            raise ValueError("schedule_mode必须是IMMEDIATE或SCHEDULED")
        return v

    @validator('schedule_preset')
    def validate_schedule_preset(cls, v):
        if v is not None and v not in ["DAILY", "WEEKLY", "MONTHLY"]:
            raise ValueError("schedule_preset必须是DAILY、WEEKLY或MONTHLY")
        return v


class TaskUpdate(BaseSchema):
    """更新任务请求"""
    task_name: Optional[str] = Field(None, min_length=1, max_length=100, description="任务名称")
    priority: Optional[int] = Field(None, ge=1, le=10, description="调度优先级")
    status: Optional[int] = Field(None, ge=0, le=3, description="状态")
    schedule_mode: Optional[str] = Field(None, description="前端简化调度模式")
    schedule_enabled: Optional[bool] = Field(None, description="定时任务是否启用")
    schedule_timezone: Optional[str] = Field(None, description="调度时区")
    schedule_preset: Optional[str] = Field(None, description="简单频率")
    schedule_time: Optional[str] = Field(None, description="简单频率时间")
    schedule_days: Optional[List[int]] = Field(None, description="周几或每月日期")
    incremental: Optional[bool] = Field(None, description="是否增量采集")
    baseline_task_id: Optional[str] = Field(None, description="增量基线任务ID")
    incremental_page_limit: Optional[int] = Field(None, ge=1, le=20, description="增量采集页数")
    stale_page_threshold: Optional[int] = Field(None, ge=1, le=10, description="连续旧页阈值")
    lookback_hours: Optional[int] = Field(None, ge=1, le=720, description="发布时间回看小时数")


class TaskResponse(BaseSchema):
    """任务响应"""
    task_id: str = Field(..., description="任务ID")
    parent_task_id: Optional[str] = Field(None, description="父任务ID")
    task_name: str = Field(..., description="任务名称")
    channel: str = Field(..., description="采集渠道")
    keywords: Optional[List[str]] = Field(None, description="搜索关键词列表")
    city: Optional[List[str]] = Field(None, description="采集城市列表")
    page_count: int = Field(..., description="每个维度组合采集页数")
    schedule_type: str = Field(..., description="调度类型")
    cron_expression: Optional[str] = Field(None, description="Cron表达式")
    schedule_enabled: bool = Field(..., description="是否启用定时")
    schedule_timezone: str = Field(..., description="调度时区")
    schedule_preset: Optional[str] = Field(None, description="简单频率")
    schedule_time: Optional[str] = Field(None, description="简单频率时间")
    schedule_days: Optional[List[int]] = Field(None, description="周几或每月日期")
    next_run_time: Optional[datetime] = Field(None, description="下一次执行时间")
    last_run_time: Optional[datetime] = Field(None, description="最近一次触发时间")
    incremental: bool = Field(..., description="是否增量采集")
    baseline_task_id: Optional[str] = Field(None, description="增量任务基线任务ID")
    incremental_page_limit: int = Field(..., description="增量采集页数")
    stale_page_threshold: int = Field(..., description="连续旧页阈值")
    lookback_hours: int = Field(..., description="发布时间回看小时数")
    last_success_at: Optional[datetime] = Field(None, description="最近一次成功完成时间")
    watermark_publish_date: Optional[datetime] = Field(None, description="发布时间水位线")
    watermark_crawl_time: Optional[datetime] = Field(None, description="采集时间水位线")
    status: int = Field(..., description="状态: 0待执行 1运行中 2完成 3失败")
    priority: int = Field(..., description="调度优先级")
    total_count: int = Field(..., description="总任务数")
    finished_count: int = Field(..., description="完成数")
    new_count: int = Field(..., description="新增职位数")
    updated_count: int = Field(..., description="更新职位数")
    duplicate_count: int = Field(..., description="去重数")
    start_time: Optional[datetime] = Field(None, description="任务启动时间")
    end_time: Optional[datetime] = Field(None, description="任务结束时间")
    create_user: Optional[str] = Field(None, description="创建人")
    created_at: Optional[datetime] = Field(None, description="创建时间")
    updated_at: Optional[datetime] = Field(None, description="更新时间")


class TaskListResponse(BaseSchema):
    """任务列表响应"""
    items: List[TaskResponse] = Field(..., description="任务列表")
    total: int = Field(..., description="总记录数")
    page: int = Field(..., description="页码")
    size: int = Field(..., description="每页大小")
    pages: int = Field(..., description="总页数")


# ==================== 任务分片相关 ====================

class TaskShardResponse(BaseSchema):
    """任务分片响应"""
    shard_id: str = Field(..., description="分片ID")
    task_id: str = Field(..., description="任务ID")
    page: int = Field(..., description="采集页码")
    keyword: Optional[str] = Field(None, description="单分片关键词")
    city: Optional[str] = Field(None, description="单分片城市")
    category_code: Optional[str] = Field(None, description="职位分类编码")
    status: int = Field(..., description="状态")
    retry_count: int = Field(..., description="重试次数")
    stop_reason: Optional[str] = Field(None, description="提前终止原因")
    new_count: int = Field(..., description="新增职位数")
    updated_count: int = Field(..., description="更新职位数")
    duplicate_count: int = Field(..., description="重复职位数")
    worker_id: Optional[str] = Field(None, description="执行节点ID")
    start_time: Optional[datetime] = Field(None, description="开始时间")
    end_time: Optional[datetime] = Field(None, description="完成时间")
    created_at: Optional[datetime] = Field(None, description="创建时间")
    updated_at: Optional[datetime] = Field(None, description="更新时间")


# ==================== 节点相关 ====================

class WorkerRegister(BaseSchema):
    """节点注册请求"""
    worker_id: str = Field(..., description="节点ID")
    ip: str = Field(..., description="节点IP")
    cpu_cores: int = Field(..., description="CPU核心数")
    memory_total: int = Field(..., description="总内存(MB)")


class WorkerHeartbeat(BaseSchema):
    """节点心跳请求"""
    worker_id: str = Field(..., description="节点ID")
    cpu_usage: float = Field(..., ge=0, le=100, description="CPU使用率")
    memory_usage: float = Field(..., ge=0, le=100, description="内存使用率")
    current_tasks: int = Field(..., ge=0, description="当前任务数")
    status: str = Field("ONLINE", description="节点状态")


class WorkerResponse(BaseSchema):
    """节点响应"""
    worker_id: str = Field(..., description="节点ID")
    ip: str = Field(..., description="节点IP")
    status: int = Field(..., description="状态: 0离线 1在线")
    current_task_id: Optional[str] = Field(None, description="当前任务ID")
    cpu_usage: int = Field(..., description="CPU使用率百分比")
    memory_usage: int = Field(..., description="内存使用率百分比")
    last_heartbeat: datetime = Field(..., description="最后心跳时间")
    created_at: Optional[datetime] = Field(None, description="创建时间")
    updated_at: Optional[datetime] = Field(None, description="更新时间")


# ==================== 监控相关 ====================

class DashboardStats(BaseSchema):
    """仪表盘统计"""
    total_tasks: int = Field(..., description="总任务数")
    running_tasks: int = Field(..., description="运行中任务数")
    online_workers: int = Field(..., description="在线节点数")
    today_collected: int = Field(..., description="今日采集量")
    success_rate: float = Field(..., description="成功率")
    error_count: int = Field(..., description="异常数量")


class WorkerStats(BaseSchema):
    """节点统计"""
    worker_id: str = Field(..., description="节点ID")
    status: str = Field(..., description="状态: ONLINE/OFFLINE")
    cpu_usage: float = Field(..., description="CPU使用率")
    memory_usage: float = Field(..., description="内存使用率")
    current_tasks: int = Field(..., description="当前任务数")
    total_shards: int = Field(..., description="总分片数")
    completed_shards: int = Field(..., description="已完成分片数")


class TaskTrendItem(BaseSchema):
    """任务趋势项"""
    date: str = Field(..., description="日期")
    count: int = Field(..., description="数量")


class DashboardResponse(BaseSchema):
    """仪表盘响应"""
    stats: DashboardStats = Field(..., description="统计信息")
    worker_stats: List[WorkerStats] = Field(..., description="节点统计列表")
    task_trend: List[TaskTrendItem] = Field(..., description="任务趋势")


# ==================== 消息队列相关 ====================

class TaskShardMessage(BaseSchema):
    """任务分片消息"""
    shard_id: str = Field(..., description="分片ID")
    task_id: str = Field(..., description="任务ID")
    keyword: Optional[str] = Field(None, description="关键词")
    city: Optional[str] = Field(None, description="城市")
    category_code: Optional[str] = Field(None, description="分类码")
    page: int = Field(..., description="页码")
    retry_count: int = Field(0, description="重试次数")
    incremental: bool = Field(False, description="是否增量采集")
    baseline_task_id: Optional[str] = Field(None, description="增量基线任务ID")
    watermark_publish_date: Optional[datetime] = Field(None, description="发布时间水位线")
    watermark_crawl_time: Optional[datetime] = Field(None, description="采集时间水位线")
    stale_page_threshold: int = Field(2, description="连续旧页阈值")
    lookback_hours: int = Field(72, description="发布时间回看小时数")
    search_keyword: Optional[str] = Field(None, description="搜索关键词，用于关键词标签")


class JobDataMessage(BaseSchema):
    """职位数据消息"""
    url_obj_id: str = Field(..., description="职位唯一标识")
    title: str = Field(..., description="职位标题")
    salary_min: Optional[int] = Field(None, description="最低薪资")
    salary_max: Optional[int] = Field(None, description="最高薪资")
    salary_raw: Optional[str] = Field(None, description="原始薪资")
    job_city: str = Field(..., description="工作城市")
    district: Optional[str] = Field(None, description="工作区县")
    street_name: Optional[str] = Field(None, description="街道")
    experience_year: Optional[str] = Field(None, description="经验要求")
    education_need: Optional[str] = Field(None, description="学历要求")
    publish_date: Optional[datetime] = Field(None, description="发布时间")
    job_welfare: Optional[str] = Field(None, description="职位福利")
    job_labels: Optional[str] = Field(None, description="职位标签")
    job_skill_tags: Optional[str] = Field(None, description="技能标签")
    job_keywords: Optional[str] = Field(None, description="关键词标签")
    requirements: Optional[str] = Field(None, description="任职要求")
    position_info: Optional[str] = Field(None, description="职位描述")
    job_classification: Optional[str] = Field(None, description="职位分类")
    address: Optional[str] = Field(None, description="企业地址")
    company_name: Optional[str] = Field(None, description="公司名称")
    company_size: Optional[str] = Field(None, description="公司规模")
    company_type: Optional[str] = Field(None, description="企业性质")
    company_finance: Optional[str] = Field(None, description="公司融资情况")
    industry_name: Optional[str] = Field(None, description="行业名称")
    industry_code: Optional[str] = Field(None, description="行业编码")
    company_url: Optional[str] = Field(None, description="公司主页")
    company_logo: Optional[str] = Field(None, description="公司Logo")
    task_id: Optional[str] = Field(None, description="任务ID")
    shard_id: Optional[str] = Field(None, description="分片ID")
    crawl_time: datetime = Field(default_factory=datetime.now, description="采集时间")
