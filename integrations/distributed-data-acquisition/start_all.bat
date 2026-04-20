@echo off
echo ============================================
echo 分布式数据采集系统启动脚本
echo RabbitMQ配置：admin/admin123
echo ============================================
echo.

echo 1. 启动调度中心（使用.env配置）...
start "调度中心" cmd /k "cd /d f:\Spring26\HuaDi\DistributedDataAcquisition_V3 && venv\Scripts\activate.bat && cd scheduler-center && python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload"

timeout /t 8 /nobreak

echo 2. 启动爬虫节点1（设置环境变量）...
start "爬虫节点1" cmd /k "cd /d f:\Spring26\HuaDi\DistributedDataAcquisition_V3 && venv\Scripts\activate.bat && set NODE_ID=worker_001 && set NODE_NAME=爬虫节点1 && set SCHEDULER_HOST=localhost && set RABBITMQ_HOST=127.0.0.1 && set RABBITMQ_USER=admin && set RABBITMQ_PASSWORD=admin123 && set RABBITMQ_PASS=admin123 && cd crawler-node && python main.py"

timeout /t 3 /nobreak

echo 3. 启动爬虫节点2（设置环境变量）...
start "爬虫节点2" cmd /k "cd /d f:\Spring26\HuaDi\DistributedDataAcquisition_V3 && venv\Scripts\activate.bat && set NODE_ID=worker_002 && set NODE_NAME=爬虫节点2 && set SCHEDULER_HOST=localhost && set RABBITMQ_HOST=127.0.0.1 && set RABBITMQ_USER=admin && set RABBITMQ_PASSWORD=admin123 && set RABBITMQ_PASS=admin123 && cd crawler-node && python main.py"

echo.
echo ============================================
echo 所有服务已启动！
echo.
echo 访问地址：
echo 调度中心API：http://localhost:8000/api/tasks
echo API文档：http://localhost:8000/docs
echo RabbitMQ管理：http://localhost:15672 (admin/admin123)
echo.
echo 按任意键退出...
pause