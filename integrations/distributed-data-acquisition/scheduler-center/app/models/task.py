"""
采集任务模型
"""
import os
from datetime import datetime
from sqlalchemy import Column, String, Text, Integer, DateTime, ForeignKey, UniqueConstraint
from sqlalchemy.dialects.mysql import BIGINT, TINYINT
from sqlalchemy.orm import relationship

from .base import Base


class CrawlTask(Base):
    """采集任务主表"""
    __tablename__ = "crawl_task"
    __table_args__ = {"comment": "采集任务主表"}

    # 主键使用UUID字符串
    task_id = Column(String(64), primary_key=True, comment="任务ID，UUID")
    parent_task_id = Column(String(64), ForeignKey("crawl_task.task_id"), nullable=True, comment="父任务ID")
    task_name = Column(String(100), nullable=False, comment="任务名称")
    channel = Column(String(32), nullable=False, comment="采集渠道: zhaopin/boss等")
    keywords = Column(Text, nullable=True, comment="搜索关键词，JSON数组")
    city = Column(String(100), nullable=True, comment="采集城市，JSON数组")
    page_count = Column(Integer, nullable=False, default=3, comment="每个维度组合采集页数")
    schedule_type = Column(String(32), nullable=False, default="IMMEDIATE", comment="调度类型: IMMEDIATE/SCHEDULED_TEMPLATE/SCHEDULED_RUN")
    cron_expression = Column(String(100), nullable=True, comment="Cron表达式")
    schedule_timezone = Column(String(64), nullable=False, default="Asia/Shanghai", comment="调度时区")
    schedule_enabled = Column(TINYINT, nullable=False, default=1, comment="定时任务是否启用")
    schedule_preset = Column(String(20), nullable=True, comment="简单频率: DAILY/WEEKLY/MONTHLY")
    schedule_time = Column(String(10), nullable=True, comment="简单频率对应的时间 HH:mm")
    schedule_days = Column(Text, nullable=True, comment="周几或每月日期，JSON数组")
    next_run_time = Column(DateTime, nullable=True, comment="下一次执行时间")
    last_run_time = Column(DateTime, nullable=True, comment="上一次触发时间")
    incremental = Column(TINYINT, nullable=False, default=0, comment="是否增量采集")
    baseline_task_id = Column(String(64), ForeignKey("crawl_task.task_id"), nullable=True, comment="增量采集基线任务ID")
    incremental_page_limit = Column(Integer, nullable=False, default=2, comment="增量采集页数")
    stale_page_threshold = Column(Integer, nullable=False, default=2, comment="连续旧页阈值")
    lookback_hours = Column(Integer, nullable=False, default=72, comment="发布时间回看小时数")
    last_success_at = Column(DateTime, nullable=True, comment="最近一次成功完成时间")
    watermark_publish_date = Column(DateTime, nullable=True, comment="发布时间水位线")
    watermark_crawl_time = Column(DateTime, nullable=True, comment="采集时间水位线")
    status = Column(TINYINT, nullable=False, default=0, comment="状态: 0待执行 1运行中 2完成 3失败")
    priority = Column(Integer, nullable=False, default=5, comment="调度优先级，数值越小优先级越高")
    total_count = Column(Integer, nullable=False, default=0, comment="总任务数")
    finished_count = Column(Integer, nullable=False, default=0, comment="完成数")
    new_count = Column(Integer, nullable=False, default=0, comment="新增职位数")
    updated_count = Column(Integer, nullable=False, default=0, comment="更新职位数")
    duplicate_count = Column(Integer, nullable=False, default=0, comment="去重数")
    start_time = Column(DateTime, nullable=True, comment="任务启动时间")
    end_time = Column(DateTime, nullable=True, comment="任务结束时间")
    create_user = Column(String(50), nullable=True, comment="任务创建人")
    # 兼容现有数据库中的旧字段名 create_time/update_time
    created_at = Column("create_time", DateTime, nullable=True, default=datetime.now, comment="创建时间")
    updated_at = Column("update_time", DateTime, nullable=True, default=datetime.now, onupdate=datetime.now, comment="更新时间")

    # 关联关系
    shards = relationship("CrawlTaskShard", back_populates="task", cascade="all, delete-orphan")
    logs = relationship("CrawlTaskLog", back_populates="task", cascade="all, delete-orphan")
    parent = relationship("CrawlTask", remote_side=[task_id], backref="children", foreign_keys=[parent_task_id])


class CrawlTaskShard(Base):
    """任务分片表"""
    __tablename__ = "crawl_task_shard"
    __table_args__ = (
        UniqueConstraint(
            "task_id",
            "page",
            "keyword",
            "city",
            "category_code",
            name="uq_shard_task_page",
        ),
        {"comment": "任务分片表"},
    )

    shard_id = Column(String(64), primary_key=True, comment="分片唯一标识")
    task_id = Column(String(64), ForeignKey("crawl_task.task_id"), nullable=False, comment="关联主任务")
    page = Column(Integer, nullable=False, comment="采集页码")
    keyword = Column(String(100), nullable=True, comment="单分片关键词")
    city = Column(String(100), nullable=True, comment="单分片城市")
    category_code = Column(String(50), nullable=True, comment="职位分类编码")
    status = Column(TINYINT, nullable=False, default=0, comment="状态: 0待执行 1运行中 2完成 3失败")
    retry_count = Column(Integer, nullable=False, default=0, comment="重试次数")
    stop_reason = Column(String(50), nullable=True, comment="提前终止原因")
    new_count = Column(Integer, nullable=False, default=0, comment="新增职位数")
    updated_count = Column(Integer, nullable=False, default=0, comment="更新职位数")
    duplicate_count = Column(Integer, nullable=False, default=0, comment="重复职位数")
    worker_id = Column(String(64), ForeignKey("crawl_worker.worker_id"), nullable=True, comment="执行该分片的爬虫节点")
    start_time = Column(DateTime, nullable=True, comment="分片执行开始时间")
    end_time = Column(DateTime, nullable=True, comment="分片执行完成时间")

    # 关联关系
    task = relationship("CrawlTask", back_populates="shards")
    worker = relationship("CrawlWorker", back_populates="shards")
    logs = relationship("CrawlTaskLog", back_populates="shard")


class CrawlWorker(Base):
    """爬虫节点表"""
    __tablename__ = "crawl_worker"
    __table_args__ = {"comment": "爬虫节点表"}

    worker_id = Column(String(64), primary_key=True, comment="爬虫节点唯一标识")
    ip = Column(String(50), nullable=False, comment="节点服务器IP")
    status = Column(TINYINT, nullable=False, default=1, comment="状态: 0离线 1在线")
    current_task_id = Column(String(64), nullable=True, comment="节点正在执行的任务")
    cpu_usage = Column(Integer, nullable=False, default=0, comment="CPU使用率百分比")
    memory_usage = Column(Integer, nullable=False, default=0, comment="内存使用率百分比")
    last_heartbeat = Column(DateTime, nullable=False, default=datetime.now, comment="节点心跳上报时间")

    # 关联关系
    shards = relationship("CrawlTaskShard", back_populates="worker")
    proxies = relationship("CrawlProxy", back_populates="worker")


class CrawlProxy(Base):
    """代理IP池表"""
    __tablename__ = "crawl_proxy"
    __table_args__ = {"comment": "代理IP池表"}

    id = Column(BIGINT(unsigned=True), primary_key=True, autoincrement=True, comment="自增主键")
    proxy_ip = Column(String(50), nullable=False, comment="代理服务器IP:端口")
    protocol = Column(String(10), nullable=False, default="http", comment="协议: http/https")
    status = Column(TINYINT, nullable=False, default=1, comment="状态: 0不可用 1可用")
    fail_count = Column(Integer, nullable=False, default=0, comment="失败次数")
    success_rate = Column(Integer, nullable=False, default=100, comment="成功率百分比")
    avg_response_time = Column(Integer, nullable=False, default=0, comment="平均响应时间(ms)")
    last_used_time = Column(DateTime, nullable=True, comment="代理最后使用时间")
    worker_id = Column(String(64), ForeignKey("crawl_worker.worker_id"), nullable=True, comment="绑定的爬虫节点")

    # 关联关系
    worker = relationship("CrawlWorker", back_populates="proxies")


class SystemConfig(Base):
    """系统配置表"""
    __tablename__ = "system_config"
    __table_args__ = {"comment": "系统配置表"}

    id = Column(BIGINT(unsigned=True), primary_key=True, autoincrement=True, comment="自增主键")
    config_key = Column(String(100), nullable=False, unique=True, comment="配置项唯一键")
    config_value = Column(Text, nullable=False, comment="配置项内容")
    channel = Column(String(32), nullable=False, default="default", comment="渠道: default/zhaopin/boss")
    status = Column(TINYINT, nullable=False, default=1, comment="状态: 0禁用 1启用")
    expire_time = Column(DateTime, nullable=True, comment="配置有效期")

    # 不需要updated_at，因为基类已包含


class CrawlTaskLog(Base):
    """任务日志表"""
    __tablename__ = "crawl_task_log"
    __table_args__ = {"comment": "任务日志表"}

    log_id = Column(BIGINT(unsigned=True), primary_key=True, autoincrement=True, comment="自增主键")
    task_id = Column(String(64), ForeignKey("crawl_task.task_id"), nullable=False, comment="关联采集任务")
    shard_id = Column(String(64), ForeignKey("crawl_task_shard.shard_id"), nullable=True, comment="关联任务分片")
    worker_id = Column(String(64), ForeignKey("crawl_worker.worker_id"), nullable=True, comment="关联爬虫节点")
    level = Column(String(10), nullable=False, default="INFO", comment="日志级别: DEBUG/INFO/WARN/ERROR")
    message = Column(Text, nullable=False, comment="日志详细信息")

    # 关联关系
    task = relationship("CrawlTask", back_populates="logs")
    shard = relationship("CrawlTaskShard", back_populates="logs")
    worker = relationship("CrawlWorker")


class JobData(Base):
    """职位数据表（原分布式数据采集入库表）"""
    __tablename__ = os.getenv("JOB_DATA_TABLE", "crawl_job_posting")
    __table_args__ = {"comment": "职位数据表"}

    url = Column(String(500), nullable=True, comment="招聘职位原始页面链接")
    url_obj_id = Column(String(100), primary_key=True, comment="来源平台职位唯一标识，可用于去重")
    title = Column(String(200), nullable=False, comment="招聘职位名称")
    salary_min = Column(Integer, nullable=True, default=0, comment="最低薪资")
    salary_max = Column(Integer, nullable=True, default=0, comment="最高薪资")
    salary_raw = Column(String(50), nullable=True, comment="页面原始薪资格式")
    job_city = Column(String(50), nullable=False, comment="职位工作城市")
    district = Column(String(50), nullable=True, comment="工作区县")
    street_name = Column(String(100), nullable=True, comment="街道")
    experience_year = Column(String(50), nullable=True, comment="工作经验要求")
    education_need = Column(String(50), nullable=True, comment="学历要求")
    publish_date = Column(DateTime, nullable=True, comment="职位发布时间")
    job_welfare = Column(Text, nullable=True, comment="页面提取的福利信息")
    job_labels = Column(Text, nullable=True, comment="页面提取的标签信息，原始存储")
    job_skill_tags = Column(Text, nullable=True, comment="技能标签")
    job_keywords = Column(Text, nullable=True, comment="合并去重后的关键词标签")
    requirements = Column(Text, nullable=True, comment="任职要求")
    position_info = Column(Text, nullable=True, comment="岗位职责与任职要求原文")
    job_classification = Column(String(100), nullable=True, comment="页面原始职位分类")
    address = Column(String(500), nullable=True, comment="企业地址")
    company_name = Column(String(100), nullable=True, comment="招聘公司名称")
    company_size = Column(String(50), nullable=True, comment="公司规模信息")
    company_type = Column(String(50), nullable=True, comment="企业性质")
    company_finance = Column(String(50), nullable=True, comment="公司融资阶段或融资信息")
    industry_name = Column(String(100), nullable=True, comment="公司行业")
    industry_code = Column(String(50), nullable=True, comment="行业编码")
    company_url = Column(String(500), nullable=True, comment="公司主页")
    crawl_time = Column(DateTime, nullable=False, default=datetime.now, comment="数据采集时间")
    crawl_update_time = Column(DateTime, nullable=True, comment="本条采集记录更新时间")
    company_logo = Column(String(500), nullable=True, comment="公司logo图片URL")
    task_id = Column(String(64), ForeignKey("crawl_task.task_id"), nullable=True, comment="关联采集任务")
