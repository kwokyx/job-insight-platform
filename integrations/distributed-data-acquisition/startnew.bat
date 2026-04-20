@echo off
chcp 65001 > nul
echo ========================================
echo 分布式数据采集系统启动脚本
echo ========================================

REM 获取当前目录
set PROJECT_ROOT=%CD%
echo 项目根目录: %PROJECT_ROOT%

REM 激活虚拟环境
echo 激活虚拟环境...
call %PROJECT_ROOT%\venv\Scripts\activate.bat

REM 设置 Python 路径
set PYTHONPATH=%PROJECT_ROOT%\scheduler-center;%PROJECT_ROOT%\crawler-node;%PYTHONPATH%

REM 启动调度中心
echo.
echo [1/2] 启动调度中心...
start "Scheduler Center" cmd /k "cd /d %PROJECT_ROOT%\scheduler-center && set PYTHONPATH=%PROJECT_ROOT%\scheduler-center && %PROJECT_ROOT%\venv\Scripts\python.exe run.py"

timeout /t 3 /nobreak > nul

REM 启动爬虫节点
echo.
echo [2/2] 启动爬虫节点...
start "Crawler Node" cmd /k "cd /d %PROJECT_ROOT%\crawler-node && set PYTHONPATH=%PROJECT_ROOT%\crawler-node && %PROJECT_ROOT%\venv\Scripts\python.exe main.py"

echo.
echo 启动完成！
echo 调度中心: http://localhost:8000/docs
echo.
pause