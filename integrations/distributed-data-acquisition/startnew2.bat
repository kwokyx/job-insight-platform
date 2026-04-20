@echo off
echo ============================================
echo 分布式数据采集系统启动脚本
echo RabbitMQ配置：admin/admin123
echo ============================================
echo.

echo 1. 启动调度中心...
start "调度中心" cmd /k "cd /d f:\Spring26\HuaDi\DistributedDataAcquisition_V3 && venv\Scripts\activate.bat && cd scheduler-center && python -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload"

timeout /t 5 /nobreak

echo 2. 启动爬虫节点1...
start "爬虫节点1" cmd /k "cd /d f:\Spring26\HuaDi\DistributedDataAcquisition_V3 && venv\Scripts\activate.bat && set NODE_ID=worker_001 && cd crawler-node && python main.py"

timeout /t 3 /nobreak

echo 3. 启动爬虫节点2...
start "爬虫节点2" cmd /k "cd /d f:\Spring26\HuaDi\DistributedDataAcquisition_V3 && venv\Scripts\activate.bat && set NODE_ID=worker_002 && cd crawler-node && python main.py"

echo.
echo ============================================
echo 所有服务已启动！
echo 调度中心API：http://localhost:8000/api/tasks
echo API文档：http://localhost:8000/docs
echo RabbitMQ管理：http://localhost:15672 (admin/admin123)
echo ============================================
pause