import json
import tempfile
from pathlib import Path
from typing import Dict, Iterable, Tuple
from urllib import request

import paramiko


REPO_ROOT = Path(r"C:\Users\32020\Desktop\occupational _competencies_platform")
SNAPSHOT_PATH = REPO_ROOT / "tmp_zhaopin_auth.json"
HOST_ENV_PATHS = [
    REPO_ROOT / ".env",
    REPO_ROOT / "integrations" / "distributed-data-acquisition" / ".env",
]
SCHEDULER_BATCH_URL = "http://localhost:8001/api/config/batch"
SCHEDULER_CHANNEL_URL = "http://localhost:8001/api/config/channel/zhaopin"

VM_HOSTS = [
    ("hadoop001", "192.168.200.151"),
    ("hadoop002", "192.168.200.152"),
    ("hadoop003", "192.168.200.153"),
]
VM_ENV_PATH = "/opt/crawler-node/releases/.env"
VM_SERVICE = "crawler-node"

AUTH_ENV_MAP = {
    "token_mme": "ZHAOPIN_TOKEN_MME",
    "token_c1k": "ZHAOPIN_TOKEN_C1K",
    "cookie": "ZHAOPIN_COOKIE",
    "page_request_id": "ZHAOPIN_PAGE_REQUEST_ID",
    "request_id": "ZHAOPIN_REQUEST_ID",
    "client_id": "ZHAOPIN_CLIENT_ID",
}

AUTH_CONFIG_MAP = {
    "token_mme": "zhaopin.token_mme",
    "token_c1k": "zhaopin.token_c1k",
    "cookie": "zhaopin.cookie",
    "page_request_id": "zhaopin.page_request_id",
    "request_id": "zhaopin.request_id",
    "client_id": "zhaopin.client_id",
    "auth_status": "zhaopin.auth_status",
    "auth_updated_at": "zhaopin.auth_updated_at",
}


def load_snapshot() -> Dict[str, str]:
    try:
        with request.urlopen(SCHEDULER_CHANNEL_URL, timeout=30) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
        if payload.get("code") == 200 and isinstance(payload.get("data"), dict):
            configs = payload["data"]
            snapshot = {
                "token_mme": str(configs.get("zhaopin.token_mme", "") or ""),
                "token_c1k": str(configs.get("zhaopin.token_c1k", "") or ""),
                "cookie": str(configs.get("zhaopin.cookie", "") or ""),
                "page_request_id": str(configs.get("zhaopin.page_request_id", "") or ""),
                "request_id": str(configs.get("zhaopin.request_id", "") or ""),
                "client_id": str(configs.get("zhaopin.client_id", "") or ""),
                "auth_status": str(configs.get("zhaopin.auth_status", "") or ""),
                "auth_updated_at": str(configs.get("zhaopin.auth_updated_at", "") or ""),
            }
            if snapshot["cookie"]:
                return snapshot
    except Exception:
        pass

    raw_text = SNAPSHOT_PATH.read_text(encoding="utf-8-sig")
    json_line = next((line for line in reversed(raw_text.splitlines()) if line.strip().startswith("{")), "{}")
    payload = json.loads(json_line)
    fields = payload.get("auth_fields", {}) if isinstance(payload.get("auth_fields"), dict) else {}
    snapshot = {key: str(fields.get(key, "") or "") for key in AUTH_ENV_MAP}
    snapshot["auth_status"] = str(payload.get("status", "") or "")
    snapshot["auth_updated_at"] = str(payload.get("updated_at", "") or "")
    return snapshot


def update_env_text(text: str, updates: Dict[str, str]) -> str:
    normalized = text.replace("\ufeff", "")
    lines = normalized.splitlines()
    index_map = {}
    for idx, line in enumerate(lines):
        if "=" not in line or line.lstrip().startswith("#"):
            continue
        key = line.split("=", 1)[0].strip()
        index_map[key] = idx

    for key, value in updates.items():
        safe_value = value.replace("\r", "").replace("\n", "")
        line = f"{key}={safe_value}"
        if key in index_map:
            lines[index_map[key]] = line
        else:
            lines.append(line)

    return "\n".join(lines).strip() + "\n"


def write_host_env(snapshot: Dict[str, str]) -> None:
    updates = {env_key: snapshot[source_key] for source_key, env_key in AUTH_ENV_MAP.items()}
    updates.update(
        {
            "ZHAOPIN_AUTH_EDGE_USE_PROFILE": "true",
            "ZHAOPIN_AUTH_EDGE_USER_DATA_DIR": str(
                Path.home() / "AppData" / "Local" / "Microsoft" / "Edge" / "User Data"
            ),
            "ZHAOPIN_AUTH_EDGE_PROFILE_DIR": "Default",
            "ZHAOPIN_AUTH_HEADLESS": "false",
            "ZHAOPIN_AUTH_KEEP_BROWSER_OPEN": "false",
            "EDGE_DRIVER_PATH": r"C:\Users\32020\tools\msedgedriver\147.0.3912.72\msedgedriver.exe",
            "EDGE_BINARY_PATH": r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
        }
    )
    for env_path in HOST_ENV_PATHS:
        current = env_path.read_text(encoding="utf-8") if env_path.exists() else ""
        env_path.write_text(update_env_text(current, updates), encoding="utf-8")


def update_scheduler_config(snapshot: Dict[str, str]) -> None:
    configs = []
    for key, config_key in AUTH_CONFIG_MAP.items():
        configs.append(
            {
                "config_key": config_key,
                "config_value": snapshot.get(key, ""),
                "channel": "zhaopin",
                "status": 1,
            }
        )

    req = request.Request(
        SCHEDULER_BATCH_URL,
        data=json.dumps(configs, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with request.urlopen(req, timeout=30) as resp:
        payload = json.loads(resp.read().decode("utf-8"))
    if payload.get("code") != 200:
        raise RuntimeError(f"scheduler config update failed: {payload}")


def update_vm_env(hostname: str, ip: str, snapshot: Dict[str, str]) -> None:
    updates = {env_key: snapshot[source_key] for source_key, env_key in AUTH_ENV_MAP.items()}
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(ip, username="root", password="root", timeout=10, banner_timeout=10, auth_timeout=10)
    try:
        current_payload = {
            "cookie": "",
            "token_mme": "",
            "token_c1k": "",
            "page_request_id": "",
            "request_id": "",
            "client_id": "",
        }
        stdin, stdout, stderr = client.exec_command(
            f"cat {VM_ENV_PATH}",
            timeout=30,
        )
        current_env = stdout.read().decode("utf-8", errors="ignore")
        for line in current_env.splitlines():
            if "=" not in line:
                continue
            key, value = line.split("=", 1)
            key = key.strip()
            value = value.strip()
            if key == "ZHAOPIN_COOKIE":
                current_payload["cookie"] = value
            elif key == "ZHAOPIN_TOKEN_MME":
                current_payload["token_mme"] = value
            elif key == "ZHAOPIN_TOKEN_C1K":
                current_payload["token_c1k"] = value
            elif key == "ZHAOPIN_PAGE_REQUEST_ID":
                current_payload["page_request_id"] = value
            elif key == "ZHAOPIN_REQUEST_ID":
                current_payload["request_id"] = value
            elif key == "ZHAOPIN_CLIENT_ID":
                current_payload["client_id"] = value

        if all(current_payload.get(key) == snapshot.get(key, "") for key in current_payload):
            print(f"=== {hostname} {ip} ===")
            print("auth unchanged, skip restart")
            return

        remote_script_path = f"/tmp/update_zhaopin_env_{hostname}.py"
        local_script = Path(tempfile.gettempdir()) / f"update_zhaopin_env_{hostname}.py"
        script_text = f"""from pathlib import Path
path = Path({VM_ENV_PATH!r})
text = path.read_text(encoding='utf-8-sig') if path.exists() else ''
updates = {json.dumps(updates, ensure_ascii=False)}
lines = text.splitlines()
index_map = {{}}
for idx, line in enumerate(lines):
    if '=' not in line or line.lstrip().startswith('#'):
        continue
    key = line.split('=', 1)[0].strip()
    index_map[key] = idx
for key, value in updates.items():
    safe = str(value).replace('\\r', '').replace('\\n', '')
    row = f"{{key}}={{safe}}"
    if key in index_map:
        lines[index_map[key]] = row
    else:
        lines.append(row)
path.write_text('\\n'.join(lines).strip() + '\\n', encoding='utf-8')
"""
        local_script.write_text(script_text, encoding="utf-8")
        sftp = client.open_sftp()
        try:
            sftp.put(str(local_script), remote_script_path)
        finally:
            sftp.close()

        command = (
            f"python {remote_script_path} && "
            f"rm -f {remote_script_path} && "
            f"systemctl restart {VM_SERVICE} && "
            f"systemctl is-active {VM_SERVICE} && "
            f"tail -n 8 /opt/crawler-node/releases/current/logs/crawler.log"
        )
        stdin, stdout, stderr = client.exec_command(command, timeout=60)
        exit_code = stdout.channel.recv_exit_status()
        output = stdout.read().decode("utf-8", errors="ignore")
        error = stderr.read().decode("utf-8", errors="ignore")
        if exit_code != 0:
            raise RuntimeError(f"{hostname} restart failed: {error or output}")
        print(f"=== {hostname} {ip} ===")
        print(output.strip())
    finally:
        client.close()


def main() -> None:
    snapshot = load_snapshot()
    write_host_env(snapshot)
    update_scheduler_config(snapshot)
    for hostname, ip in VM_HOSTS:
        update_vm_env(hostname, ip, snapshot)
    print("auth snapshot synchronized")


if __name__ == "__main__":
    main()
