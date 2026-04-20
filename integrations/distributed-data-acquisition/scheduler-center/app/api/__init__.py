"""
API路由
"""
from fastapi import APIRouter
from app.api.endpoints import tasks, workers, monitor, logs, config

# 创建主路由
api_router = APIRouter()

# 注册子路由
api_router.include_router(tasks.router, prefix="/tasks", tags=["任务管理"])
api_router.include_router(workers.router, prefix="/workers", tags=["节点管理"])
api_router.include_router(monitor.router, prefix="/monitor", tags=["监控统计"])
api_router.include_router(logs.router, prefix="/logs", tags=["日志查询"])
api_router.include_router(config.router, prefix="/config", tags=["系统配置"])