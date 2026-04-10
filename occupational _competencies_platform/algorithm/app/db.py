"""
数据库连接管理
"""
from contextlib import contextmanager

from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker

from app.config import get_settings

_engine = None
_SessionLocal = None


def get_engine():
    global _engine
    if _engine is None:
        settings = get_settings()
        _engine = create_engine(
            settings.mysql_url,
            pool_size=10,
            max_overflow=20,
            pool_recycle=3600,
            echo=False,
        )
    return _engine


def get_session_factory():
    global _SessionLocal
    if _SessionLocal is None:
        _SessionLocal = sessionmaker(bind=get_engine(), autocommit=False, autoflush=False)
    return _SessionLocal


@contextmanager
def get_db():
    """获取数据库会话的上下文管理器"""
    session = get_session_factory()()
    try:
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()


def execute_query(sql: str, params: dict = None):
    """执行查询并返回字典列表"""
    with get_db() as session:
        result = session.execute(text(sql), params or {})
        columns = result.keys()
        return [dict(zip(columns, row)) for row in result.fetchall()]
