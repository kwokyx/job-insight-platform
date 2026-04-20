"""
系统配置端点
"""
from datetime import datetime
import json
import os
import subprocess
import sys
from pathlib import Path
from typing import Optional, List, Dict, Any
from fastapi import APIRouter, Depends, HTTPException, Query, Body
from sqlalchemy.orm import Session
from sqlalchemy import desc

from app.db.session import get_db
from app.schemas.base import ResponseSchema, PaginatedResponse
from app.models.task import SystemConfig
from app.core.logger import log

router = APIRouter()

ZHAOPIN_AUTH_KEYS = {
    "token_mme": "zhaopin.token_mme",
    "token_c1k": "zhaopin.token_c1k",
    "cookie": "zhaopin.cookie",
    "page_request_id": "zhaopin.page_request_id",
    "request_id": "zhaopin.request_id",
    "client_id": "zhaopin.client_id",
    "auth_updated_at": "zhaopin.auth_updated_at",
    "auth_status": "zhaopin.auth_status",
}


def _mask_sensitive_value(value: str) -> str:
    raw = str(value or "").strip()
    if not raw:
        return ""
    if len(raw) <= 8:
        return "*" * len(raw)
    return f"{raw[:4]}***{raw[-4:]}"


def _upsert_config(
    db: Session,
    config_key: str,
    config_value: str,
    channel: str = "zhaopin",
    status: int = 1,
) -> None:
    existing = db.query(SystemConfig).filter(SystemConfig.config_key == config_key).first()
    if existing:
        existing.config_value = config_value
        existing.channel = channel
        existing.status = status
    else:
        db.add(
            SystemConfig(
                config_key=config_key,
                config_value=config_value,
                channel=channel,
                status=status,
            )
        )


def _get_channel_config_dict(db: Session, channel: str) -> Dict[str, str]:
    configs = db.query(SystemConfig).filter(
        SystemConfig.channel == channel,
        SystemConfig.status == 1,
    ).all()
    return {item.config_key: item.config_value for item in configs}


def _build_auth_status_payload(configs: Dict[str, str]) -> Dict[str, Any]:
    cookie_value = configs.get(ZHAOPIN_AUTH_KEYS["cookie"], "")
    return {
        "status": configs.get(ZHAOPIN_AUTH_KEYS["auth_status"], "missing"),
        "updated_at": configs.get(ZHAOPIN_AUTH_KEYS["auth_updated_at"], ""),
        "token_mme": _mask_sensitive_value(configs.get(ZHAOPIN_AUTH_KEYS["token_mme"], "")),
        "token_c1k": _mask_sensitive_value(configs.get(ZHAOPIN_AUTH_KEYS["token_c1k"], "")),
        "page_request_id": configs.get(ZHAOPIN_AUTH_KEYS["page_request_id"], ""),
        "request_id": _mask_sensitive_value(configs.get(ZHAOPIN_AUTH_KEYS["request_id"], "")),
        "client_id": _mask_sensitive_value(configs.get(ZHAOPIN_AUTH_KEYS["client_id"], "")),
        "cookie_present": bool(str(cookie_value or "").strip()),
        "cookie_preview": _mask_sensitive_value(cookie_value),
    }


def _run_zhaopin_auth_refresh(persist_env: bool, timeout_seconds: int) -> Dict[str, Any]:
    project_root = Path(__file__).resolve().parents[3]
    explicit_script = os.getenv("ZHAOPIN_AUTH_REFRESH_SCRIPT", "").strip()
    candidate_paths = []
    if explicit_script:
        candidate_paths.append(Path(explicit_script))
    candidate_paths.extend([
        project_root / "refresh_zhaopin_auth.py",
        project_root / "crawler-node" / "refresh_zhaopin_auth.py",
        project_root.parent / "crawler-node" / "refresh_zhaopin_auth.py",
    ])
    script_path = next((path for path in candidate_paths if path.exists()), None)
    if script_path is None:
        searched = ", ".join(str(path) for path in candidate_paths)
        raise RuntimeError(
            f"未找到智联鉴权刷新脚本，请在宿主机完成浏览器鉴权并通过配置中心回写，已搜索路径: {searched}"
        )

    command = [
        sys.executable,
        str(script_path),
        "--json-output",
        "--timeout",
        str(max(timeout_seconds, 30)),
    ]
    if persist_env:
        command.append("--persist-env")

    completed = subprocess.run(
        command,
        cwd=str(script_path.parent),
        capture_output=True,
        text=True,
        timeout=max(timeout_seconds + 30, 90),
        check=False,
    )
    stdout = (completed.stdout or "").strip().splitlines()
    stderr = (completed.stderr or "").strip()
    if not stdout:
        raise RuntimeError(stderr or "智联鉴权刷新脚本未输出结果")

    raw_json = stdout[-1]
    try:
        result = json.loads(raw_json)
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"刷新脚本输出不是有效 JSON: {raw_json}") from exc

    if completed.returncode != 0 and result.get("status") != "active":
        raise RuntimeError(result.get("message") or stderr or "智联鉴权刷新失败")
    return result


@router.get("/", response_model=ResponseSchema)
async def get_configs(
    page: int = Query(1, ge=1, description="页码"),
    size: int = Query(20, ge=1, le=100, description="每页大小"),
    channel: Optional[str] = Query(None, description="渠道"),
    status: Optional[int] = Query(None, ge=0, le=1, description="状态"),
    keyword: Optional[str] = Query(None, description="关键词搜索"),
    db: Session = Depends(get_db),
):
    """获取配置列表"""
    try:
        # 构建查询
        query = db.query(SystemConfig)

        # 过滤条件
        if channel:
            query = query.filter(SystemConfig.channel == channel)
        if status is not None:
            query = query.filter(SystemConfig.status == status)
        if keyword:
            query = query.filter(SystemConfig.config_key.contains(keyword))

        # 获取总数
        total = query.count()

        # 分页查询
        configs = query.order_by(desc(SystemConfig.updated_at)) \
            .offset((page - 1) * size) \
            .limit(size) \
            .all()

        # 转换为字典
        config_list = [config.to_dict() for config in configs]

        return PaginatedResponse.success(
            items=config_list,
            total=total,
            page=page,
            size=size
        )
    except Exception as e:
        log.error(f"获取配置列表失败: {e}")
        return ResponseSchema.error(message="获取配置列表失败")


@router.get("/zhaopin/auth/status", response_model=ResponseSchema)
async def get_zhaopin_auth_status(db: Session = Depends(get_db)):
    """获取智联鉴权状态摘要。"""
    try:
        configs = _get_channel_config_dict(db, "zhaopin")
        return ResponseSchema.success(data=_build_auth_status_payload(configs))
    except Exception as e:
        log.error(f"获取智联鉴权状态失败: {e}")
        return ResponseSchema.error(message="获取智联鉴权状态失败")


@router.post("/zhaopin/auth/refresh", response_model=ResponseSchema)
async def refresh_zhaopin_auth(
    reason: str = Body("manual_refresh", description="刷新触发原因"),
    triggered_by: str = Body("unknown", description="触发来源"),
    persist_env: bool = Body(True, description="是否兼容回写本地 .env"),
    timeout_seconds: int = Body(180, ge=30, le=900, description="等待人工登录超时时间"),
    db: Session = Depends(get_db),
):
    """触发一次智联鉴权刷新并写回配置中心。"""
    try:
        result = _run_zhaopin_auth_refresh(persist_env=persist_env, timeout_seconds=timeout_seconds)
        auth_fields = result.get("auth_fields", {}) if isinstance(result.get("auth_fields"), dict) else {}
        if not auth_fields.get("token_mme") or not auth_fields.get("token_c1k") or not auth_fields.get("cookie"):
            return ResponseSchema.error(message=result.get("message", "智联鉴权刷新未拿到完整字段"), data=result)

        update_mapping = {
            ZHAOPIN_AUTH_KEYS["token_mme"]: str(auth_fields.get("token_mme", "") or ""),
            ZHAOPIN_AUTH_KEYS["token_c1k"]: str(auth_fields.get("token_c1k", "") or ""),
            ZHAOPIN_AUTH_KEYS["cookie"]: str(auth_fields.get("cookie", "") or ""),
            ZHAOPIN_AUTH_KEYS["page_request_id"]: str(auth_fields.get("page_request_id", "") or ""),
            ZHAOPIN_AUTH_KEYS["request_id"]: str(auth_fields.get("request_id", "") or ""),
            ZHAOPIN_AUTH_KEYS["client_id"]: str(auth_fields.get("client_id", "") or ""),
            ZHAOPIN_AUTH_KEYS["auth_updated_at"]: str(result.get("updated_at", datetime.now().strftime("%Y-%m-%d %H:%M:%S"))),
            ZHAOPIN_AUTH_KEYS["auth_status"]: str(result.get("status", "active") or "active"),
        }
        for config_key, config_value in update_mapping.items():
            _upsert_config(db, config_key=config_key, config_value=config_value, channel="zhaopin", status=1)
        db.commit()

        payload = {
            "status": result.get("status", "active"),
            "message": result.get("message", "智联鉴权刷新成功"),
            "updated_at": result.get("updated_at"),
            "triggered_by": triggered_by,
            "reason": reason,
            "persist_env": persist_env,
            "needs_manual_intervention": bool(result.get("needs_manual_intervention", False)),
            "auth_fields": auth_fields,
        }
        log.info(f"智联鉴权刷新成功: triggered_by={triggered_by}, reason={reason}")
        return ResponseSchema.success(data=payload, message="智联鉴权刷新成功")
    except Exception as e:
        db.rollback()
        log.error(f"刷新智联鉴权失败: {e}")
        return ResponseSchema.error(message=f"刷新智联鉴权失败: {e}")


@router.get("/{config_key}", response_model=ResponseSchema)
async def get_config(config_key: str, db: Session = Depends(get_db)):
    """获取配置详情"""
    try:
        config = db.query(SystemConfig).filter(SystemConfig.config_key == config_key).first()
        if not config:
            return ResponseSchema.error(code=404, message="配置不存在")

        # 检查配置是否过期
        if config.expire_time and config.expire_time < datetime.now():
            config.status = 0
            db.commit()
            log.warning(f"配置已过期: {config_key}")

        return ResponseSchema.success(data=config.to_dict())
    except Exception as e:
        log.error(f"获取配置详情失败: {e}")
        return ResponseSchema.error(message="获取配置详情失败")


@router.post("/", response_model=ResponseSchema)
async def create_config(
    config_key: str = Body(..., description="配置键"),
    config_value: str = Body(..., description="配置值"),
    channel: str = Body("default", description="渠道"),
    status: int = Body(1, ge=0, le=1, description="状态"),
    expire_time: Optional[datetime] = Body(None, description="过期时间"),
    db: Session = Depends(get_db),
):
    """创建配置"""
    try:
        # 检查配置是否已存在
        existing_config = db.query(SystemConfig).filter(SystemConfig.config_key == config_key).first()
        if existing_config:
            return ResponseSchema.error(message="配置已存在")

        # 创建配置
        config = SystemConfig(
            config_key=config_key,
            config_value=config_value,
            channel=channel,
            status=status,
            expire_time=expire_time
        )

        db.add(config)
        db.commit()

        log.info(f"创建配置成功: {config_key}")

        return ResponseSchema.success(message="配置创建成功")
    except Exception as e:
        db.rollback()
        log.error(f"创建配置失败: {e}")
        return ResponseSchema.error(message="创建配置失败")


@router.put("/{config_key}", response_model=ResponseSchema)
async def update_config(
    config_key: str,
    config_value: str = Body(..., description="配置值"),
    channel: Optional[str] = Body(None, description="渠道"),
    status: Optional[int] = Body(None, ge=0, le=1, description="状态"),
    expire_time: Optional[datetime] = Body(None, description="过期时间"),
    db: Session = Depends(get_db),
):
    """更新配置"""
    try:
        config = db.query(SystemConfig).filter(SystemConfig.config_key == config_key).first()
        if not config:
            return ResponseSchema.error(code=404, message="配置不存在")

        # 更新字段
        config.config_value = config_value
        if channel is not None:
            config.channel = channel
        if status is not None:
            config.status = status
        if expire_time is not None:
            config.expire_time = expire_time

        db.commit()

        log.info(f"更新配置成功: {config_key}")

        return ResponseSchema.success(message="配置更新成功")
    except Exception as e:
        db.rollback()
        log.error(f"更新配置失败: {e}")
        return ResponseSchema.error(message="更新配置失败")


@router.delete("/{config_key}", response_model=ResponseSchema)
async def delete_config(config_key: str, db: Session = Depends(get_db)):
    """删除配置"""
    try:
        config = db.query(SystemConfig).filter(SystemConfig.config_key == config_key).first()
        if not config:
            return ResponseSchema.error(code=404, message="配置不存在")

        db.delete(config)
        db.commit()

        log.info(f"删除配置成功: {config_key}")

        return ResponseSchema.success(message="配置删除成功")
    except Exception as e:
        db.rollback()
        log.error(f"删除配置失败: {e}")
        return ResponseSchema.error(message="删除配置失败")


@router.get("/channel/{channel}", response_model=ResponseSchema)
async def get_channel_configs(channel: str, db: Session = Depends(get_db)):
    """获取渠道配置"""
    try:
        configs = db.query(SystemConfig).filter(
            SystemConfig.channel == channel,
            SystemConfig.status == 1
        ).all()

        # 过滤过期配置
        valid_configs = []
        for config in configs:
            if config.expire_time and config.expire_time < datetime.now():
                config.status = 0
                db.commit()
                log.warning(f"配置已过期: {config.config_key}")
            else:
                valid_configs.append(config)

        # 转换为字典
        config_dict = {config.config_key: config.config_value for config in valid_configs}

        return ResponseSchema.success(data=config_dict)
    except Exception as e:
        log.error(f"获取渠道配置失败: {e}")
        return ResponseSchema.error(message="获取渠道配置失败")


@router.post("/batch", response_model=ResponseSchema)
async def batch_update_configs(
    configs: List[dict] = Body(..., description="配置列表"),
    db: Session = Depends(get_db),
):
    """批量更新配置"""
    try:
        updated_count = 0
        created_count = 0

        for config_data in configs:
            config_key = config_data.get("config_key")
            config_value = config_data.get("config_value")
            channel = config_data.get("channel", "default")
            status = config_data.get("status", 1)

            if not config_key or not config_value:
                continue

            # 查找现有配置
            existing_config = db.query(SystemConfig).filter(SystemConfig.config_key == config_key).first()

            if existing_config:
                # 更新现有配置
                existing_config.config_value = config_value
                existing_config.channel = channel
                existing_config.status = status
                updated_count += 1
            else:
                # 创建新配置
                config = SystemConfig(
                    config_key=config_key,
                    config_value=config_value,
                    channel=channel,
                    status=status
                )
                db.add(config)
                created_count += 1

        db.commit()

        log.info(f"批量更新配置: 创建{created_count}个，更新{updated_count}个")

        return ResponseSchema.success(
            message=f"批量更新完成，创建{created_count}个配置，更新{updated_count}个配置"
        )
    except Exception as e:
        db.rollback()
        log.error(f"批量更新配置失败: {e}")
        return ResponseSchema.error(message="批量更新配置失败")
