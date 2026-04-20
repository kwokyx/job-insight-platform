# -*- coding: utf-8 -*-
"""
智联鉴权刷新脚本，可供调度中心或本地命令行调用。
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

from dotenv import load_dotenv

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from core.logger import setup_logger
from utils.zhaopin_auth import run_edge_auth_refresh


def _load_root_dotenv() -> None:
    project_root = Path(__file__).resolve().parent.parent
    dotenv_path = project_root / ".env"
    if dotenv_path.exists():
        load_dotenv(dotenv_path=dotenv_path, override=False)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="刷新智联招聘鉴权信息")
    parser.add_argument("--timeout", type=int, default=180, help="等待人工登录的最长秒数")
    parser.add_argument("--persist-env", action="store_true", help="刷新成功后回写到项目根目录 .env")
    parser.add_argument("--json-output", action="store_true", help="仅打印 JSON 结果")
    parser.add_argument("--force", action="store_true", help="忽略现有 .env 鉴权信息，强制重新从 Edge 获取")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    _load_root_dotenv()
    setup_logger()
    result = run_edge_auth_refresh(
        persist_env=bool(args.persist_env),
        timeout_seconds=max(args.timeout, 30),
        force_refresh=bool(args.force),
    )
    print(json.dumps(result, ensure_ascii=False))
    return 0 if result.get("status") == "active" else 1


if __name__ == "__main__":
    raise SystemExit(main())
