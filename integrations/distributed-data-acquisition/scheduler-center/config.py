"""
配置模块
"""
import os
from typing import Optional
from pydantic_settings import BaseSettings
from pydantic import RedisDsn, PostgresDsn, MySQLDsn, validator


class Settings(BaseSettings):
    """应用配置"""

    # 应用配置
    APP_NAME: str = "分布式数据采集调度中心"
    APP_VERSION: str = "1.0.0"
    DEBUG: bool = os.getenv("DEBUG", "False").lower() == "true"

    # 服务配置
    HOST: str = os.getenv("HOST", "0.0.0.0")
    PORT: int = int(os.getenv("PORT", 8000))

    # 数据库配置
    DATABASE_URL: str = os.getenv(
        "DATABASE_URL",
        "mysql+pymysql://career:career2026@127.0.0.1:3307/career_platform?charset=utf8mb4"
    )
    JOB_DATA_TABLE: str = os.getenv("JOB_DATA_TABLE", "crawl_job_posting")

    # Redis配置
    REDIS_URL: str = os.getenv("REDIS_URL", "redis://localhost:6379/0")

    # RabbitMQ配置
    RABBITMQ_HOST: str = os.getenv("RABBITMQ_HOST", "localhost")
    RABBITMQ_PORT: int = int(os.getenv("RABBITMQ_PORT", 5672))
    RABBITMQ_USER: str = os.getenv("RABBITMQ_USER", "admin")
    RABBITMQ_PASSWORD: str = os.getenv("RABBITMQ_PASSWORD", "admin123")
    RABBITMQ_VHOST: str = os.getenv("RABBITMQ_VHOST", "/")

    # HDFS配置
    HDFS_ENABLED: bool = os.getenv("HDFS_ENABLED", "false").lower() == "true"
    HDFS_HOST: str = os.getenv("HDFS_HOST", "hadoop001")
    HDFS_PORT: int = int(os.getenv("HDFS_PORT", 8020))
    HDFS_WEB_PORT: int = int(os.getenv("HDFS_WEB_PORT", 9870))
    HDFS_USER: str = os.getenv("HDFS_USER", "hadoop")
    HDFS_BASE_PATH: str = os.getenv("HDFS_BASE_PATH", "/data/recruitment_crawler/ods/job_detail")
    HDFS_CHANNEL: str = os.getenv("HDFS_CHANNEL", "zhaopin")
    HDFS_HTTP_TIMEOUT_SECONDS: int = int(os.getenv("HDFS_HTTP_TIMEOUT_SECONDS", 30))
    LOCAL_STAGING_DIR: str = os.getenv("LOCAL_STAGING_DIR", "storage/hdfs_staging")
    HDFS_WRITE_BATCH_SIZE: int = int(os.getenv("HDFS_WRITE_BATCH_SIZE", 1000))
    HDFS_FLUSH_INTERVAL_SECONDS: int = int(os.getenv("HDFS_FLUSH_INTERVAL_SECONDS", 60))
    HDFS_WRITE_MODE: str = os.getenv("HDFS_WRITE_MODE", "dual_write")
    HDFS_REDIRECT_HOST_MAP: str = os.getenv(
        "HDFS_REDIRECT_HOST_MAP",
        "hadoop001=192.168.152.151,hadoop002=192.168.152.152,hadoop003=192.168.152.153",
    )

    # JWT配置
    SECRET_KEY: str = os.getenv("SECRET_KEY", "your-secret-key-change-in-production")
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24 * 7  # 7天

    # 爬虫配置
    HEARTBEAT_INTERVAL: int = 30  # 心跳间隔(秒)
    HEARTBEAT_TIMEOUT: int = 90   # 心跳超时(秒)
    SHARD_MAX_PAGES: int = 100    # 分片最大页数
    MAX_RETRY_COUNT: int = 3      # 最大重试次数
    PROXY_CHECK_INTERVAL: int = 10 * 60  # 代理检查间隔(秒)
    TOKEN_CHECK_INTERVAL: int = 60 * 60  # Token检查间隔(秒)

    # 日志配置
    LOG_LEVEL: str = os.getenv("LOG_LEVEL", "INFO")
    LOG_FILE: str = os.getenv("LOG_FILE", "logs/scheduler.log")

    # CORS配置
    CORS_ORIGINS: list = [
        "http://localhost:3000",
        "http://localhost:5173",
    ]

    @property
    def rabbitmq_url(self) -> str:
        """RabbitMQ连接URL"""
        return f"amqp://{self.RABBITMQ_USER}:{self.RABBITMQ_PASSWORD}@{self.RABBITMQ_HOST}:{self.RABBITMQ_PORT}{self.RABBITMQ_VHOST}"

    class Config:
        env_file = ".env"
        case_sensitive = True
        extra = "allow"


settings = Settings()
