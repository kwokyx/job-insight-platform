"""
数据库模型基类
"""
from datetime import datetime
from typing import Optional
from sqlalchemy import Column, DateTime, String, func
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.dialects.mysql import BIGINT

Base = declarative_base()


class BaseModel(Base):
    """抽象基类"""
    __abstract__ = True

    # 通用字段
    id = Column(BIGINT(unsigned=True), primary_key=True, autoincrement=True)
    created_at = Column(DateTime, default=datetime.now, nullable=False, comment="创建时间")
    updated_at = Column(DateTime, default=datetime.now, onupdate=datetime.now, nullable=False, comment="更新时间")
    created_by = Column(String(50), nullable=True, comment="创建人")
    updated_by = Column(String(50), nullable=True, comment="更新人")

    def to_dict(self):
        """转换为字典"""
        return {c.name: getattr(self, c.name) for c in self.__table__.columns}