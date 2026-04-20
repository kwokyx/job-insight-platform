"""
爬虫节点配置
"""
import os
import socket
import uuid
from typing import Optional


class Config:
    """配置类"""

    # 节点配置
    NODE_ID: str = os.getenv("NODE_ID", f"worker_{socket.gethostname()}_{uuid.uuid4().hex[:8]}")
    NODE_NAME: str = os.getenv("NODE_NAME", "爬虫节点")
    MAX_CONCURRENT_TASKS: int = int(os.getenv("MAX_CONCURRENT_TASKS", 5))

    # 调度中心配置
    SCHEDULER_HOST: str = os.getenv("SCHEDULER_HOST", "localhost").strip()
    SCHEDULER_PORT: int = int(os.getenv("SCHEDULER_PORT", 8001))
    SCHEDULER_API_PREFIX: str = os.getenv("SCHEDULER_API_PREFIX", "/api")

    # RabbitMQ配置
    RABBITMQ_HOST: str = os.getenv("RABBITMQ_HOST", "127.0.0.1")
    RABBITMQ_PORT: int = int(os.getenv("RABBITMQ_PORT", 5672))
    RABBITMQ_USER: str = os.getenv("RABBITMQ_USER", "admin")
    RABBITMQ_PASSWORD: str = os.getenv("RABBITMQ_PASSWORD") or os.getenv("RABBITMQ_PASS", "admin123")
    RABBITMQ_VHOST: str = os.getenv("RABBITMQ_VHOST", "/")

    # 数据库配置
    MYSQL_HOST: str = os.getenv("MYSQL_HOST", "localhost")
    MYSQL_PORT: int = int(os.getenv("MYSQL_PORT", 3307))
    MYSQL_USER: str = os.getenv("MYSQL_USER", "career")
    MYSQL_PASSWORD: str = os.getenv("MYSQL_PASSWORD", "career2026")
    MYSQL_DATABASE: str = os.getenv("MYSQL_DATABASE", "career_platform")

    # Redis配置
    REDIS_HOST: str = os.getenv("REDIS_HOST", "localhost")
    REDIS_PORT: int = int(os.getenv("REDIS_PORT", 6379))
    REDIS_PASSWORD: str = os.getenv("REDIS_PASSWORD", "")
    REDIS_DB: int = int(os.getenv("REDIS_DB", 0))

    # 爬虫配置
    SPIDER_CONCURRENT_REQUESTS: int = int(os.getenv("SPIDER_CONCURRENT_REQUESTS", 8))
    SPIDER_DOWNLOAD_DELAY: float = float(os.getenv("SPIDER_DOWNLOAD_DELAY", 1.0))
    SPIDER_RETRY_TIMES: int = int(os.getenv("SPIDER_RETRY_TIMES", 3))

    # 智联招聘配置
    ZHAOPIN_API_URL: str = "https://fe-api.zhaopin.com/c/i/search/positions"
    ZHAOPIN_TOKEN_MME: str = os.getenv("ZHAOPIN_TOKEN_MME", "MmEwMD=")  # 需要定期更新
    ZHAOPIN_TOKEN_C1K: str = os.getenv("ZHAOPIN_TOKEN_C1K", "c1K5tw0w6_=")  # 需要定期更新
    ZHAOPIN_COOKIE: str = os.getenv("ZHAOPIN_COOKIE", "")
    ZHAOPIN_REFERER: str = os.getenv("ZHAOPIN_REFERER", "https://www.zhaopin.com/")
    ZHAOPIN_ORIGIN: str = os.getenv("ZHAOPIN_ORIGIN", "https://www.zhaopin.com")
    ZHAOPIN_PAGE_REQUEST_ID: str = os.getenv("ZHAOPIN_PAGE_REQUEST_ID", "")
    ZHAOPIN_REQUEST_ID: str = os.getenv("ZHAOPIN_REQUEST_ID", "")
    ZHAOPIN_CLIENT_ID: str = os.getenv("ZHAOPIN_CLIENT_ID", "")
    ZHAOPIN_AUTH_CACHE_TTL_SECONDS: int = int(os.getenv("ZHAOPIN_AUTH_CACHE_TTL_SECONDS", 120))
    ZHAOPIN_AUTH_HTTP_TIMEOUT: int = int(os.getenv("ZHAOPIN_AUTH_HTTP_TIMEOUT", 15))
    ZHAOPIN_AUTH_REFRESH_TIMEOUT_SECONDS: int = int(os.getenv("ZHAOPIN_AUTH_REFRESH_TIMEOUT_SECONDS", 180))
    ZHAOPIN_AUTH_PERSIST_ENV: bool = os.getenv("ZHAOPIN_AUTH_PERSIST_ENV", "true").lower() == "true"
    ZHAOPIN_CLUSTER_CODES_PATH: Optional[str] = os.getenv("ZHAOPIN_CLUSTER_CODES_PATH")
    ZHAOPIN_CLUSTERS_BY_LEVEL_PATH: Optional[str] = os.getenv("ZHAOPIN_CLUSTERS_BY_LEVEL_PATH")
    ZHAOPIN_CLUSTER_HIERARCHY_PATH: Optional[str] = os.getenv("ZHAOPIN_CLUSTER_HIERARCHY_PATH")

    # 代理配置
    PROXY_ENABLED: bool = os.getenv("PROXY_ENABLED", "false").lower() == "true"
    PROXY_POOL_URL: Optional[str] = os.getenv("PROXY_POOL_URL")

    # 心跳配置
    HEARTBEAT_INTERVAL: int = int(os.getenv("HEARTBEAT_INTERVAL", 30))  # 秒

    # 日志配置
    LOG_LEVEL: str = os.getenv("LOG_LEVEL", "INFO")
    LOG_FILE: str = os.getenv("LOG_FILE", "logs/crawler.log")

    # 浏览器配置
    HEADLESS: bool = os.getenv("HEADLESS", "true").lower() == "true"
    BROWSER_TYPE: str = os.getenv("BROWSER_TYPE", "edge")  # chrome, firefox, edge

    @property
    def scheduler_base_url(self) -> str:
        """调度中心基础URL"""
        return f"http://{self.SCHEDULER_HOST}:{self.SCHEDULER_PORT}{self.SCHEDULER_API_PREFIX}"

    @property
    def mysql_url(self) -> str:
        """MySQL连接URL"""
        return f"mysql+pymysql://{self.MYSQL_USER}:{self.MYSQL_PASSWORD}@{self.MYSQL_HOST}:{self.MYSQL_PORT}/{self.MYSQL_DATABASE}?charset=utf8mb4"

    @property
    def redis_url(self) -> str:
        """Redis连接URL"""
        auth = f":{self.REDIS_PASSWORD}@" if self.REDIS_PASSWORD else ""
        return f"redis://{auth}{self.REDIS_HOST}:{self.REDIS_PORT}/{self.REDIS_DB}"

    @property
    def rabbitmq_url(self) -> str:
        """RabbitMQ连接URL"""
        return f"amqp://{self.RABBITMQ_USER}:{self.RABBITMQ_PASSWORD}@{self.RABBITMQ_HOST}:{self.RABBITMQ_PORT}{self.RABBITMQ_VHOST}"


config = Config()
