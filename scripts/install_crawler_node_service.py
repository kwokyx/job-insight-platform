import textwrap
from pathlib import Path

import paramiko


HOSTS = [
    ("hadoop001", "192.168.200.151"),
    ("hadoop002", "192.168.200.152"),
    ("hadoop003", "192.168.200.153"),
]
USERNAME = "root"
PASSWORD = "root"

REMOTE_SERVICE_PATH = "/etc/systemd/system/crawler-node.service"
REMOTE_APP_DIR = "/opt/crawler-node/releases/current"
REMOTE_ENV_FILE = "/opt/crawler-node/releases/.env"

SERVICE_CONTENT = textwrap.dedent(
    """\
    [Unit]
    Description=Distributed Crawler Node
    After=network-online.target
    Wants=network-online.target

    [Service]
    Type=simple
    User=root
    WorkingDirectory=/opt/crawler-node/releases/current
    EnvironmentFile=/opt/crawler-node/releases/.env
    ExecStartPre=/usr/bin/mkdir -p /opt/crawler-node/releases/current/logs
    ExecStart=/opt/crawler-node/releases/current/.venv/bin/python /opt/crawler-node/releases/current/main.py
    Restart=always
    RestartSec=5
    KillMode=process
    TimeoutStopSec=30

    [Install]
    WantedBy=multi-user.target
    """
)


def run_command(client: paramiko.SSHClient, command: str, timeout: int = 60) -> str:
    wrapped_command = f"bash -lc {command!r}"
    stdin, stdout, stderr = client.exec_command(wrapped_command, timeout=timeout)
    exit_code = stdout.channel.recv_exit_status()
    output = stdout.read().decode("utf-8", errors="ignore")
    error = stderr.read().decode("utf-8", errors="ignore")
    if exit_code != 0:
        raise RuntimeError(f"command failed({exit_code}): {command}\nstdout:\n{output}\nstderr:\n{error}")
    return output + error


def upload_service_file(client: paramiko.SSHClient) -> None:
    temp_path = "/tmp/crawler-node.service"
    sftp = client.open_sftp()
    try:
        with sftp.file(temp_path, "w") as remote_file:
            remote_file.write(SERVICE_CONTENT)
        sftp.chmod(temp_path, 0o644)
    finally:
        sftp.close()

    run_command(client, f"mv {temp_path} {REMOTE_SERVICE_PATH}")


def normalize_env_file(client: paramiko.SSHClient) -> None:
    run_command(
        client,
        f'prefix=$(od -An -tx1 -N3 {REMOTE_ENV_FILE} | tr -d " \\n"); '
        f'if [ "$prefix" = "efbbbf" ]; then tail -c +4 {REMOTE_ENV_FILE} > {REMOTE_ENV_FILE}.tmp && mv {REMOTE_ENV_FILE}.tmp {REMOTE_ENV_FILE}; fi',
    )


def install_service(hostname: str, ip: str) -> None:
    print(f"=== {hostname} {ip} ===")
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(
        ip,
        username=USERNAME,
        password=PASSWORD,
        timeout=10,
        banner_timeout=10,
        auth_timeout=10,
    )
    try:
        run_command(client, f"test -f {REMOTE_ENV_FILE}")
        run_command(client, f"test -x {REMOTE_APP_DIR}/.venv/bin/python")
        run_command(client, f"test -f {REMOTE_APP_DIR}/main.py")

        normalize_env_file(client)
        upload_service_file(client)

        run_command(client, "systemctl daemon-reload")
        run_command(client, "systemctl disable crawler-node >/dev/null 2>&1 || true")
        run_command(
            client,
            r"pgrep -f '^/opt/crawler-node/releases/current/.venv/bin/python /opt/crawler-node/releases/current/main.py$' | xargs -r kill -TERM",
        )
        run_command(client, "systemctl enable crawler-node")
        run_command(client, "systemctl restart crawler-node", timeout=90)
        status = run_command(
            client,
            "systemctl status crawler-node --no-pager --full | sed -n '1,20p'",
            timeout=60,
        )
        safe_status = (
            status.replace("\ufeff", "")
            .encode("gbk", errors="ignore")
            .decode("gbk", errors="ignore")
        )
        print(safe_status.strip())
    finally:
        client.close()


def main() -> None:
    for hostname, ip in HOSTS:
        install_service(hostname, ip)


if __name__ == "__main__":
    main()
