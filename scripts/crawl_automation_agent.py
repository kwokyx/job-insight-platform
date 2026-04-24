import json
import os
import socket
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
AUTOMATION_HOME = Path(os.environ.get("CRAWL_AUTOMATION_HOME", str(REPO_ROOT / "tmp" / "automation")))
COMMAND_DIR = AUTOMATION_HOME / "commands"
RESULT_DIR = AUTOMATION_HOME / "results"
HEARTBEAT_FILE = AUTOMATION_HOME / "agent-heartbeat.json"

PYTHON_CANDIDATES = [
    os.environ.get("ZHAOPIN_AUTH_PYTHON", "").strip(),
    sys.executable,
    r"D:\Python\python.exe",
    r"C:\Users\32020\AppData\Local\Programs\Python\Python39\python.exe",
]


def now_text() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def ensure_dirs() -> None:
    COMMAND_DIR.mkdir(parents=True, exist_ok=True)
    RESULT_DIR.mkdir(parents=True, exist_ok=True)


def write_json(path: Path, payload: dict) -> None:
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def update_heartbeat(extra: dict | None = None) -> None:
    payload = {
        "updatedAt": now_text(),
        "hostname": socket.gethostname(),
        "pid": os.getpid(),
        "repoRoot": str(REPO_ROOT),
        "automationHome": str(AUTOMATION_HOME),
        "commandDir": str(COMMAND_DIR),
        "resultDir": str(RESULT_DIR),
    }
    if extra:
        payload.update(extra)
    write_json(HEARTBEAT_FILE, payload)


def resolve_python() -> str:
    seen: set[str] = set()
    for raw in PYTHON_CANDIDATES:
        if not raw:
            continue
        candidate = Path(raw).expanduser()
        normalized = str(candidate).lower()
        if normalized in seen:
            continue
        seen.add(normalized)
        if candidate.exists():
            return str(candidate.resolve())
    raise RuntimeError("no usable python interpreter found")


def command_for(action: str, payload: dict) -> list[str]:
    python_exe = payload.get("pythonCommand", "").strip() or resolve_python()
    python_parts = python_exe.split()
    if action == "WATCHDOG":
        script = payload.get("watchdogScript", "scripts/watch_zhaopin_auth.py")
    elif action == "SYNC_AUTH_SNAPSHOT":
        script = payload.get("syncScript", "scripts/sync_zhaopin_auth_snapshot.py")
    else:
        raise RuntimeError(f"unsupported action: {action}")
    return [*python_parts, script]


def execute_command(payload: dict) -> dict:
    action = str(payload.get("action", "")).strip()
    raw_workspace = str(payload.get("workspace", "")).strip()
    workspace = Path(raw_workspace) if raw_workspace else REPO_ROOT
    if not workspace.exists():
        workspace = REPO_ROOT
    python_executable = payload.get("pythonCommand", "").strip() or resolve_python()
    args = command_for(action, payload)
    started_at = now_text()
    completed = subprocess.run(
        args,
        cwd=str(workspace),
        capture_output=True,
        text=True,
        timeout=900,
        check=False,
    )
    stdout = (completed.stdout or "").strip()
    stderr = (completed.stderr or "").strip()
    success = completed.returncode == 0
    return {
      "id": payload.get("id"),
      "action": action,
      "triggerSource": payload.get("triggerSource"),
      "workspace": str(workspace),
      "pythonExecutable": python_executable,
      "scriptPath": args[-1] if args else "",
      "startedAt": started_at,
      "finishedAt": now_text(),
      "status": "SUCCESS" if success else "FAILED",
      "message": stdout or stderr or ("success" if success else "command failed"),
      "exitCode": completed.returncode,
      "stdout": stdout,
      "stderr": stderr,
    }


def process_once() -> bool:
    command_files = sorted(COMMAND_DIR.glob("*.json"))
    if not command_files:
        return False

    command_file = command_files[0]
    payload = read_json(command_file)
    update_heartbeat({"currentCommandId": payload.get("id"), "currentAction": payload.get("action")})
    try:
        result = execute_command(payload)
    except Exception as exc:
        result = {
            "id": payload.get("id"),
            "action": payload.get("action"),
            "triggerSource": payload.get("triggerSource"),
            "startedAt": now_text(),
            "finishedAt": now_text(),
            "status": "FAILED",
            "message": str(exc),
            "exitCode": -1,
            "stdout": "",
            "stderr": str(exc),
        }

    write_json(RESULT_DIR / f"{payload.get('id', command_file.stem)}.json", result)
    command_file.unlink(missing_ok=True)
    update_heartbeat({"lastCommandId": result.get("id"), "lastCommandStatus": result.get("status")})
    return True


def main() -> int:
    ensure_dirs()
    print(f"[crawl-agent] watching {COMMAND_DIR}")
    while True:
        update_heartbeat()
        handled = process_once()
        time.sleep(2 if handled else 5)


if __name__ == "__main__":
    raise SystemExit(main())
