@echo off
setlocal
cd /d C:\Users\32020\Desktop\occupational _competencies_platform
set "PYTHON_EXE="
if exist "D:\Python\python.exe" set "PYTHON_EXE=D:\Python\python.exe"
if not defined PYTHON_EXE if exist "C:\Users\32020\AppData\Local\Programs\Python\Python39\python.exe" set "PYTHON_EXE=C:\Users\32020\AppData\Local\Programs\Python\Python39\python.exe"

if defined PYTHON_EXE (
  start "crawl-automation-agent" "%PYTHON_EXE%" scripts\crawl_automation_agent.py
) else (
  start "crawl-automation-agent" py -3 scripts\crawl_automation_agent.py
)
endlocal
