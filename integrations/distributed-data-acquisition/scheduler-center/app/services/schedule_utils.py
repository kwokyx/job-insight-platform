"""
定时调度工具
"""
import json
from datetime import datetime, timedelta
from typing import Iterable, List, Optional, Tuple
from zoneinfo import ZoneInfo


DEFAULT_TIMEZONE = "Asia/Shanghai"


def normalize_schedule_mode(schedule_mode: Optional[str], schedule_type: Optional[str]) -> str:
    """统一前端/内部调度模式。"""
    if schedule_mode == "SCHEDULED":
        return "SCHEDULED_TEMPLATE"
    if schedule_mode == "IMMEDIATE":
        return "IMMEDIATE"
    if schedule_type in {"IMMEDIATE", "SCHEDULED_TEMPLATE", "SCHEDULED_RUN"}:
        return schedule_type
    return "IMMEDIATE"


def normalize_schedule_time(schedule_time: Optional[str]) -> str:
    """规范化时间 HH:mm。"""
    if not schedule_time:
        return "00:00"
    parts = schedule_time.split(":")
    if len(parts) != 2:
        raise ValueError("schedule_time格式必须为HH:mm")
    hour = int(parts[0])
    minute = int(parts[1])
    if hour < 0 or hour > 23 or minute < 0 or minute > 59:
        raise ValueError("schedule_time时间范围无效")
    return f"{hour:02d}:{minute:02d}"


def normalize_schedule_days(schedule_days: Optional[Iterable[int]]) -> Optional[List[int]]:
    if schedule_days is None:
        return None
    normalized = sorted({int(day) for day in schedule_days})
    return normalized or None


def build_schedule(
    schedule_type: str,
    schedule_preset: Optional[str],
    schedule_time: Optional[str],
    schedule_days: Optional[Iterable[int]],
    cron_expression: Optional[str],
    schedule_timezone: Optional[str],
    now: Optional[datetime] = None,
) -> Tuple[Optional[str], Optional[str], Optional[List[int]], Optional[str], Optional[datetime]]:
    """构建cron与下次触发时间。"""
    if schedule_type == "IMMEDIATE":
        return None, None, None, schedule_timezone or DEFAULT_TIMEZONE, None

    timezone_name = schedule_timezone or DEFAULT_TIMEZONE
    local_tz = ZoneInfo(timezone_name)
    current = now.astimezone(local_tz) if now else datetime.now(local_tz)
    preset = schedule_preset or "DAILY"
    normalized_time = normalize_schedule_time(schedule_time)
    normalized_days = normalize_schedule_days(schedule_days)

    if cron_expression:
        cron = cron_expression
    else:
        cron = build_cron_expression(preset, normalized_time, normalized_days)

    next_run_time = calculate_next_run_time(preset, normalized_time, normalized_days, timezone_name, current)
    return preset, normalized_time, normalized_days, cron, next_run_time


def build_cron_expression(schedule_preset: str, schedule_time: str, schedule_days: Optional[List[int]]) -> str:
    hour, minute = _split_time(schedule_time)
    if schedule_preset == "DAILY":
        return f"{minute} {hour} * * *"
    if schedule_preset == "WEEKLY":
        days = schedule_days or [1]
        cron_days = ",".join(str(day % 7) for day in days)
        return f"{minute} {hour} * * {cron_days}"
    if schedule_preset == "MONTHLY":
        days = schedule_days or [1]
        cron_days = ",".join(str(day) for day in days)
        return f"{minute} {hour} {cron_days} * *"
    raise ValueError("schedule_preset必须为DAILY、WEEKLY或MONTHLY")


def calculate_next_run_time(
    schedule_preset: str,
    schedule_time: str,
    schedule_days: Optional[List[int]],
    schedule_timezone: str = DEFAULT_TIMEZONE,
    base_time: Optional[datetime] = None,
) -> datetime:
    tz = ZoneInfo(schedule_timezone)
    current = base_time.astimezone(tz) if base_time else datetime.now(tz)
    hour, minute = _split_time(schedule_time)

    if schedule_preset == "DAILY":
        candidate = current.replace(hour=hour, minute=minute, second=0, microsecond=0)
        if candidate <= current:
            candidate += timedelta(days=1)
        return candidate

    if schedule_preset == "WEEKLY":
        days = schedule_days or [1]
        python_days = [((day - 1) % 7) for day in days]
        for offset in range(0, 8):
            candidate = current + timedelta(days=offset)
            if candidate.weekday() in python_days:
                run_at = candidate.replace(hour=hour, minute=minute, second=0, microsecond=0)
                if run_at > current:
                    return run_at
        fallback = current + timedelta(days=7)
        return fallback.replace(hour=hour, minute=minute, second=0, microsecond=0)

    if schedule_preset == "MONTHLY":
        days = schedule_days or [1]
        year = current.year
        month = current.month
        for _ in range(0, 14):
            for day in days:
                try:
                    candidate = datetime(year, month, day, hour, minute, tzinfo=tz)
                except ValueError:
                    continue
                if candidate > current:
                    return candidate
            if month == 12:
                month = 1
                year += 1
            else:
                month += 1

    raise ValueError("无法计算下一次执行时间")


def dumps_schedule_days(schedule_days: Optional[List[int]]) -> Optional[str]:
    return json.dumps(schedule_days, ensure_ascii=False) if schedule_days else None


def loads_schedule_days(schedule_days: Optional[str]) -> Optional[List[int]]:
    if not schedule_days:
        return None
    if isinstance(schedule_days, list):
        return [int(item) for item in schedule_days]
    return [int(item) for item in json.loads(schedule_days)]


def _split_time(schedule_time: str) -> Tuple[int, int]:
    hour_str, minute_str = normalize_schedule_time(schedule_time).split(":")
    return int(hour_str), int(minute_str)
