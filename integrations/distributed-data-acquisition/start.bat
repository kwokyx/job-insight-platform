@echo off
setlocal

echo ============================================
echo Distributed crawler startup script
echo RabbitMQ: admin/admin123
echo ============================================
echo.

echo 1. Start scheduler...
start "scheduler" cmd /k "cd /d F:\Spring26\HuaDi\DistributedDataAcquisition_V3\scheduler-center && ..\venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload"

timeout /t 8 /nobreak

echo 2. Start crawler node 1...
start "crawler-node-1" cmd /k "cd /d F:\Spring26\HuaDi\DistributedDataAcquisition_V3\crawler-node && set NODE_ID=worker_001 && set NODE_NAME=worker_001 && set SCHEDULER_HOST=localhost && set RABBITMQ_HOST=127.0.0.1 && set RABBITMQ_PORT=5672 && set RABBITMQ_USER=admin && set RABBITMQ_PASSWORD=admin123 && set RABBITMQ_PASS=admin123 && ..\venv\Scripts\python.exe main.py"

timeout /t 3 /nobreak

echo 3. Start crawler node 2...
start "crawler-node-2" cmd /k "cd /d F:\Spring26\HuaDi\DistributedDataAcquisition_V3\crawler-node && set NODE_ID=worker_002 && set NODE_NAME=worker_002 && set SCHEDULER_HOST=localhost && set RABBITMQ_HOST=127.0.0.1 && set RABBITMQ_PORT=5672 && set RABBITMQ_USER=admin && set RABBITMQ_PASSWORD=admin123 && set RABBITMQ_PASS=admin123 && ..\venv\Scripts\python.exe main.py"

echo.
echo ============================================
echo All services started.
echo Scheduler API: http://localhost:8000/api/tasks
echo API docs: http://localhost:8000/docs
echo RabbitMQ UI: http://localhost:15672
echo ============================================
pause
