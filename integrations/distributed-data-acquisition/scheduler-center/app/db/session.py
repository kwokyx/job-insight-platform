"""
数据库会话管理
"""
from contextlib import contextmanager
from typing import Generator

from sqlalchemy import create_engine, inspect, text
from sqlalchemy.orm import Session, sessionmaker

from app.config import settings


engine = create_engine(
    settings.DATABASE_URL,
    pool_pre_ping=True,
    pool_recycle=3600,
    echo=settings.DEBUG,
    pool_size=20,
    max_overflow=0,
)

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def get_db() -> Generator[Session, None, None]:
    """FastAPI dependency for a database session."""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


@contextmanager
def get_db_session():
    """Context-manager form of the database session."""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def create_tables():
    """Create all tables in development environments."""
    from app.models.base import Base

    Base.metadata.create_all(bind=engine)


def sync_schema():
    """Backfill missing columns in reused legacy tables."""
    crawl_task_columns = {
        "page_count": "ALTER TABLE crawl_task ADD COLUMN page_count INT NOT NULL DEFAULT 3 AFTER city",
        "schedule_type": "ALTER TABLE crawl_task ADD COLUMN schedule_type VARCHAR(32) NOT NULL DEFAULT 'IMMEDIATE' AFTER page_count",
        "cron_expression": "ALTER TABLE crawl_task ADD COLUMN cron_expression VARCHAR(100) NULL AFTER schedule_type",
        "schedule_timezone": "ALTER TABLE crawl_task ADD COLUMN schedule_timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai' AFTER cron_expression",
        "schedule_enabled": "ALTER TABLE crawl_task ADD COLUMN schedule_enabled TINYINT NOT NULL DEFAULT 1 AFTER schedule_timezone",
        "schedule_preset": "ALTER TABLE crawl_task ADD COLUMN schedule_preset VARCHAR(20) NULL AFTER schedule_enabled",
        "schedule_time": "ALTER TABLE crawl_task ADD COLUMN schedule_time VARCHAR(10) NULL AFTER schedule_preset",
        "schedule_days": "ALTER TABLE crawl_task ADD COLUMN schedule_days TEXT NULL AFTER schedule_time",
        "next_run_time": "ALTER TABLE crawl_task ADD COLUMN next_run_time DATETIME NULL AFTER schedule_days",
        "last_run_time": "ALTER TABLE crawl_task ADD COLUMN last_run_time DATETIME NULL AFTER next_run_time",
        "incremental": "ALTER TABLE crawl_task ADD COLUMN incremental TINYINT NOT NULL DEFAULT 0 AFTER last_run_time",
        "baseline_task_id": "ALTER TABLE crawl_task ADD COLUMN baseline_task_id VARCHAR(64) NULL AFTER incremental",
        "incremental_page_limit": "ALTER TABLE crawl_task ADD COLUMN incremental_page_limit INT NOT NULL DEFAULT 2 AFTER baseline_task_id",
        "stale_page_threshold": "ALTER TABLE crawl_task ADD COLUMN stale_page_threshold INT NOT NULL DEFAULT 2 AFTER incremental_page_limit",
        "lookback_hours": "ALTER TABLE crawl_task ADD COLUMN lookback_hours INT NOT NULL DEFAULT 72 AFTER stale_page_threshold",
        "last_success_at": "ALTER TABLE crawl_task ADD COLUMN last_success_at DATETIME NULL AFTER lookback_hours",
        "watermark_publish_date": "ALTER TABLE crawl_task ADD COLUMN watermark_publish_date DATETIME NULL AFTER last_success_at",
        "watermark_crawl_time": "ALTER TABLE crawl_task ADD COLUMN watermark_crawl_time DATETIME NULL AFTER watermark_publish_date",
        "new_count": "ALTER TABLE crawl_task ADD COLUMN new_count INT NOT NULL DEFAULT 0 AFTER finished_count",
        "updated_count": "ALTER TABLE crawl_task ADD COLUMN updated_count INT NOT NULL DEFAULT 0 AFTER new_count",
        "duplicate_count": "ALTER TABLE crawl_task ADD COLUMN duplicate_count INT NOT NULL DEFAULT 0 AFTER updated_count",
    }
    crawl_task_shard_columns = {
        "stop_reason": "ALTER TABLE crawl_task_shard ADD COLUMN stop_reason VARCHAR(50) NULL AFTER retry_count",
        "new_count": "ALTER TABLE crawl_task_shard ADD COLUMN new_count INT NOT NULL DEFAULT 0 AFTER stop_reason",
        "updated_count": "ALTER TABLE crawl_task_shard ADD COLUMN updated_count INT NOT NULL DEFAULT 0 AFTER new_count",
        "duplicate_count": "ALTER TABLE crawl_task_shard ADD COLUMN duplicate_count INT NOT NULL DEFAULT 0 AFTER updated_count",
    }
    crawl_job_posting_columns = {
        "district": "ALTER TABLE crawl_job_posting ADD COLUMN district VARCHAR(50) NULL AFTER job_city",
        "street_name": "ALTER TABLE crawl_job_posting ADD COLUMN street_name VARCHAR(100) NULL AFTER district",
        "job_skill_tags": "ALTER TABLE crawl_job_posting ADD COLUMN job_skill_tags TEXT NULL AFTER job_labels",
        "job_keywords": "ALTER TABLE crawl_job_posting ADD COLUMN job_keywords TEXT NULL AFTER job_skill_tags",
        "requirements": "ALTER TABLE crawl_job_posting ADD COLUMN requirements TEXT NULL AFTER job_keywords",
        "address": "ALTER TABLE crawl_job_posting ADD COLUMN address VARCHAR(500) NULL AFTER job_classification",
        "company_type": "ALTER TABLE crawl_job_posting ADD COLUMN company_type VARCHAR(50) NULL AFTER company_size",
        "industry_name": "ALTER TABLE crawl_job_posting ADD COLUMN industry_name VARCHAR(100) NULL AFTER company_finance",
        "industry_code": "ALTER TABLE crawl_job_posting ADD COLUMN industry_code VARCHAR(50) NULL AFTER industry_name",
        "company_url": "ALTER TABLE crawl_job_posting ADD COLUMN company_url VARCHAR(500) NULL AFTER industry_code",
    }

    inspector = inspect(engine)
    existing_tables = set(inspector.get_table_names())

    def sync_missing_columns(connection, table_name: str, column_mapping: dict) -> None:
        existing_columns = {column["name"] for column in inspector.get_columns(table_name)}
        for column_name, ddl in column_mapping.items():
            if column_name not in existing_columns:
                connection.execute(text(ddl))

    with engine.begin() as connection:
        if "crawl_task" in existing_tables:
            sync_missing_columns(connection, "crawl_task", crawl_task_columns)

        if "crawl_task_shard" in existing_tables:
            sync_missing_columns(connection, "crawl_task_shard", crawl_task_shard_columns)

        if "crawl_job_posting" in existing_tables:
            sync_missing_columns(connection, "crawl_job_posting", crawl_job_posting_columns)


def drop_tables():
    """Drop all tables in development environments."""
    from app.models.base import Base

    Base.metadata.drop_all(bind=engine)
