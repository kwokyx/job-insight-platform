import json
import os
import subprocess
import sys
from datetime import datetime, timedelta
from pathlib import Path
from urllib import request


REPO_ROOT = Path(r"C:\Users\32020\Desktop\occupational _competencies_platform")
INTEGRATION_ROOT = REPO_ROOT / "integrations" / "distributed-data-acquisition"
REFRESH_SCRIPT = INTEGRATION_ROOT / "crawler-node" / "refresh_zhaopin_auth.py"
SYNC_SCRIPT = REPO_ROOT / "scripts" / "sync_zhaopin_auth_snapshot.py"
STATUS_URL = "http://localhost:8001/api/config/zhaopin/auth/status"
MAX_AUTH_AGE_HOURS = 12
PYTHON_CANDIDATES = [
    os.environ.get("ZHAOPIN_AUTH_PYTHON", "").strip(),
    sys.executable,
    r"D:\Python\python.exe",
    r"C:\Users\32020\AppData\Local\Programs\Python\Python39\python.exe",
]


def get_runtime_python() -> Path:
    seen: set[str] = set()
    for raw_path in PYTHON_CANDIDATES:
        if not raw_path:
            continue
        candidate = Path(raw_path).expanduser()
        normalized = str(candidate).lower()
        if normalized in seen:
            continue
        seen.add(normalized)
        if candidate.exists():
            return candidate.resolve()
    raise RuntimeError("no usable python interpreter found for auth watchdog")


def get_status() -> dict:
    with request.urlopen(STATUS_URL, timeout=30) as resp:
        payload = json.loads(resp.read().decode("utf-8"))
    if payload.get("code") != 200 or not isinstance(payload.get("data"), dict):
        raise RuntimeError(f"failed to fetch auth status: {payload}")
    return payload["data"]


def is_stale(status: dict) -> bool:
    updated_at = str(status.get("updated_at", "") or "").strip()
    if not updated_at:
        return True
    try:
        dt = datetime.strptime(updated_at, "%Y-%m-%d %H:%M:%S")
    except ValueError:
        return True
    return datetime.now() - dt > timedelta(hours=MAX_AUTH_AGE_HOURS)


def run_command(args: list[str], cwd: Path) -> str:
    completed = subprocess.run(
        args,
        cwd=str(cwd),
        capture_output=True,
        text=True,
        timeout=600,
        check=False,
    )
    stdout = (completed.stdout or "").strip()
    stderr = (completed.stderr or "").strip()
    if completed.returncode != 0:
        raise RuntimeError(f"command failed: {' '.join(args)}\nstdout:\n{stdout}\nstderr:\n{stderr}")
    return stdout


def try_refresh() -> str:
    args = [
        str(get_runtime_python()),
        str(REFRESH_SCRIPT),
        "--force",
        "--persist-env",
        "--json-output",
        "--timeout",
        "90",
    ]
    return run_command(args, cwd=REFRESH_SCRIPT.parent)


def run_sync() -> str:
    args = [str(get_runtime_python()), str(SYNC_SCRIPT)]
    return run_command(args, cwd=REPO_ROOT)


def main() -> int:
    status = get_status()
    needs_refresh = (not bool(status.get("cookie_present"))) or is_stale(status)

    if needs_refresh:
        print("auth status stale or missing, trying local Edge refresh")
        try:
            refresh_output = try_refresh()
            print(refresh_output)
        except Exception as exc:
            print(f"local Edge refresh failed, fallback to sync current snapshot: {exc}")
    else:
        print("auth status still fresh, skip local Edge refresh")

    sync_output = run_sync()
    print(sync_output)

    final_status = get_status()
    print(json.dumps(final_status, ensure_ascii=False))
    if final_status.get("cookie_present"):
        return 0
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
