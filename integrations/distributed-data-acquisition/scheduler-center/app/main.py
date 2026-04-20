"""
FastAPI主应用
"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
import uvicorn

from config import settings
from app.api import api_router
from app.db.session import create_tables, sync_schema
from app.core.logger import setup_logger

schedule_manager = None


def create_application() -> FastAPI:
    """创建FastAPI应用"""
    # 设置日志
    setup_logger()

    # 创建应用
    app = FastAPI(
        title=settings.APP_NAME,
        version=settings.APP_VERSION,
        docs_url="/docs" if settings.DEBUG else None,
        redoc_url="/redoc" if settings.DEBUG else None,
    )

    # 配置CORS
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.CORS_ORIGINS,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # 注册路由
    app.include_router(api_router, prefix="/api")

    # 注册启动事件
    @app.on_event("startup")
    async def startup_event():
        """应用启动事件"""
        # 创建数据库表（开发环境）
        if settings.DEBUG:
            create_tables()

        sync_schema()

        # 启动消息队列消费者
        from app.mq.consumer import init_consumers
        from app.services.schedule_manager import ScheduleManager
        try:
            init_consumers()
            print("消息队列消费者已启动")
        except Exception as e:
            print(f"启动消息队列消费者失败: {e}")

        global schedule_manager
        try:
            schedule_manager = ScheduleManager()
            schedule_manager.start()
            print("定时调度线程已启动")
        except Exception as e:
            print(f"启动定时调度线程失败: {e}")

        print(f"{settings.APP_NAME} v{settings.APP_VERSION} 启动成功")

    @app.on_event("shutdown")
    async def shutdown_event():
        """应用关闭事件"""
        # 停止消息队列消费者
        from app.mq.consumer import stop_consumers
        try:
            stop_consumers()
            print("消息队列消费者已停止")
        except Exception as e:
            print(f"停止消息队列消费者失败: {e}")

        global schedule_manager
        if schedule_manager:
            try:
                schedule_manager.stop()
                print("定时调度线程已停止")
            except Exception as e:
                print(f"停止定时调度线程失败: {e}")
            schedule_manager = None

        print("应用关闭")

    return app


# 创建应用实例
app = create_application()

if __name__ == "__main__":
    uvicorn.run(
        "app.main:app",
        host=settings.HOST,
        port=settings.PORT,
        reload=settings.DEBUG,
        log_level=settings.LOG_LEVEL.lower(),
    )
