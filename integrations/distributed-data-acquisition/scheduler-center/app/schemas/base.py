"""
Pydantic基类
"""
from datetime import datetime
from typing import Optional, Any, Dict
from pydantic import BaseModel, Field, validator
import json


class BaseSchema(BaseModel):
    """基础Schema"""

    class Config:
        orm_mode = True
        from_attributes = True
        arbitrary_types_allowed = True
        json_encoders = {
            datetime: lambda v: v.strftime("%Y-%m-%d %H:%M:%S") if v else None
        }


class ResponseSchema(BaseSchema):
    """标准响应结构"""
    code: int = Field(200, description="状态码")
    message: str = Field("success", description="消息")
    data: Optional[Any] = Field(None, description="数据")

    @classmethod
    def success(cls, data: Any = None, message: str = "success"):
        """成功响应"""
        return cls(code=200, message=message, data=data)

    @classmethod
    def error(cls, code: int = 400, message: str = "error", data: Any = None):
        """错误响应"""
        return cls(code=code, message=message, data=data)


class PaginationSchema(BaseSchema):
    """分页参数"""
    page: int = Field(1, ge=1, description="页码")
    size: int = Field(20, ge=1, le=100, description="每页大小")
    total: Optional[int] = Field(None, description="总记录数")


class PaginatedResponse(ResponseSchema):
    """分页响应"""
    data: Optional[Dict[str, Any]] = Field(None, description="分页数据")

    @classmethod
    def success(cls, items: list, total: int, page: int = 1, size: int = 20):
        """成功分页响应"""
        return cls(
            code=200,
            message="success",
            data={
                "items": items,
                "total": total,
                "page": page,
                "size": size,
                "pages": (total + size - 1) // size
            }
        )