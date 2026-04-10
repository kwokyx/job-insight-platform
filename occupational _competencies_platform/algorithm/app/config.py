"""
算法引擎配置
"""
import os
from dataclasses import dataclass, field
from functools import lru_cache


@dataclass
class Settings:
    # MySQL
    mysql_host: str = os.getenv("MYSQL_HOST", "localhost")
    mysql_port: int = int(os.getenv("MYSQL_PORT", "3306"))
    mysql_database: str = os.getenv("MYSQL_DATABASE", "career_platform")
    mysql_username: str = os.getenv("MYSQL_USERNAME", "root")
    mysql_password: str = os.getenv("MYSQL_PASSWORD", "career2026")

    # Redis
    redis_host: str = os.getenv("REDIS_HOST", "localhost")
    redis_port: int = int(os.getenv("REDIS_PORT", "6379"))

    # 模型文件目录
    model_dir: str = os.getenv("MODEL_DIR", "app/models")

    @property
    def mysql_url(self) -> str:
        return (
            f"mysql+pymysql://{self.mysql_username}:{self.mysql_password}"
            f"@{self.mysql_host}:{self.mysql_port}/{self.mysql_database}"
            f"?charset=utf8mb4"
        )


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
