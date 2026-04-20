# -*- coding: utf-8 -*-
"""
智联招聘搜索接口最小化诊断脚本
"""
import argparse
import json
import os
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional

from dotenv import load_dotenv

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from config import config
from core.logger import setup_logger, log
from core.spider.zhaopin_spider import ZhaopinSpider
from utils.http_client import HttpClient


def _load_root_dotenv() -> Dict[str, Any]:
    """加载项目根目录 .env，确保诊断脚本与主程序行为一致。"""
    project_root = Path(__file__).resolve().parent.parent
    dotenv_path = project_root / ".env"
    dotenv_exists = dotenv_path.exists()
    dotenv_loaded = False

    if dotenv_exists:
        dotenv_loaded = load_dotenv(dotenv_path=dotenv_path, override=False)

    return {
        "dotenv_loaded": dotenv_loaded,
        "dotenv_exists": dotenv_exists,
        "dotenv_path": str(dotenv_path),
    }


DOTENV_STATUS = _load_root_dotenv()


def build_cases() -> List[Dict[str, Any]]:
    return [
        {"name": "python_keyword_only", "keyword": "Python"},
        {"name": "python_beijing", "keyword": "Python", "city": "北京"},
        {"name": "python_beijing_category", "keyword": "Python", "city": "北京", "category_code": "19000200100000"},
        {"name": "java_beijing", "keyword": "Java", "city": "北京"},
        {"name": "testing_beijing", "keyword": "测试", "city": "北京"},
        {"name": "operation_beijing", "keyword": "运营", "city": "北京"},
    ]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="诊断智联搜索接口返回 0 条的原因")
    parser.add_argument("--case", dest="case_name", help="仅运行指定案例名称")
    parser.add_argument("--limit", type=int, default=0, help="最多运行前 N 个案例")
    return parser.parse_args()


def run_case(spider: ZhaopinSpider, case: Dict[str, Any]) -> Dict[str, Any]:
    keyword = case.get("keyword")
    city = case.get("city")
    category_code = case.get("category_code")
    page = int(case.get("page", 1) or 1)
    result = spider.crawl(
        keyword=keyword,
        city=city,
        category_code=category_code,
        page=page,
        search_keyword=keyword,
    )
    sample_titles = [item.get("title", "") for item in result[:3]]
    return {
        "name": case["name"],
        "keyword": keyword,
        "city": city,
        "category_code": category_code,
        "page": page,
        "count": len(result),
        "sample_titles": sample_titles,
    }


def log_zhaopin_env_status() -> Dict[str, Any]:
    """输出诊断脚本当前读取到的智联鉴权状态。"""
    auth_summary = {
        "token_mme_default": config.ZHAOPIN_TOKEN_MME == "MmEwMD=",
        "token_c1k_default": config.ZHAOPIN_TOKEN_C1K == "c1K5tw0w6_=",
        "cookie_present": bool(config.ZHAOPIN_COOKIE.strip()),
        "page_request_id_present": bool(config.ZHAOPIN_PAGE_REQUEST_ID.strip()),
        "request_id_present": bool(config.ZHAOPIN_REQUEST_ID.strip()),
        "client_id_present": bool(config.ZHAOPIN_CLIENT_ID.strip()),
        "dotenv_loaded": DOTENV_STATUS["dotenv_loaded"],
        "dotenv_path": DOTENV_STATUS["dotenv_path"],
    }
    log.info(f"智联鉴权配置摘要: {json.dumps(auth_summary, ensure_ascii=False)}")

    missing_items = []
    if auth_summary["token_mme_default"]:
        missing_items.append("ZHAOPIN_TOKEN_MME")
    if auth_summary["token_c1k_default"]:
        missing_items.append("ZHAOPIN_TOKEN_C1K")
    if not auth_summary["cookie_present"]:
        missing_items.append("ZHAOPIN_COOKIE")
    if not auth_summary["page_request_id_present"]:
        missing_items.append("ZHAOPIN_PAGE_REQUEST_ID")

    if not DOTENV_STATUS["dotenv_exists"]:
        log.warning(f"项目根目录 .env 不存在: {DOTENV_STATUS['dotenv_path']}")
    elif not DOTENV_STATUS["dotenv_loaded"]:
        log.warning(
            "项目根目录 .env 存在，但诊断脚本没有从该文件加载新变量；"
            "可能是环境变量已由外部启动脚本注入，或 .env 内容无可用键值"
        )

    if missing_items:
        log.warning(
            "未加载到有效智联鉴权配置，缺失或仍为默认值的字段: "
            f"{', '.join(missing_items)}"
        )
    else:
        log.info("已加载到有效智联鉴权配置")

    return auth_summary


def log_verification_diagnosis(auth_summary: Dict[str, Any]) -> None:
    """当命中智联验证态时，给出更明确的定位信息。"""
    auth_valid = not (
        auth_summary["token_mme_default"]
        or auth_summary["token_c1k_default"]
        or not auth_summary["cookie_present"]
        or not auth_summary["page_request_id_present"]
    )
    if auth_valid:
        return

    if not auth_summary["dotenv_loaded"]:
        log.warning(
            "如果日志中出现 isVerification=1，优先检查诊断脚本是否成功加载根目录 .env，"
            "当前状态更像是配置未注入，而不是 token 单纯过期。"
        )
        return

    log.warning(
        "如果日志中出现 isVerification=1，且 .env 已成功加载，则更可能是浏览器登录态过期或"
        " token/page_request_id 与当前 cookie 不匹配，需要重新同步智联登录信息。"
    )


def main() -> int:
    args = parse_args()
    setup_logger()
    auth_summary = log_zhaopin_env_status()
    log_verification_diagnosis(auth_summary)
    client = HttpClient()
    spider = ZhaopinSpider(client)
    summary: List[Dict[str, Any]] = []
    cases = build_cases()
    if args.case_name:
        cases = [case for case in cases if case["name"] == args.case_name]
    if args.limit and args.limit > 0:
        cases = cases[:args.limit]

    if not cases:
        print(json.dumps({"error": "未找到匹配的诊断案例"}, ensure_ascii=False))
        return 1

    try:
        for case in cases:
            log.info(f"开始诊断案例: {case}")
            case_result = run_case(spider, case)
            summary.append(case_result)
            log.info(f"诊断结果: {json.dumps(case_result, ensure_ascii=False)}")
    finally:
        client.close()

    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
