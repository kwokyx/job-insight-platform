"""
技能演化追踪模块
================
追踪特定技能在不同时间窗口的需求变化
输出生命周期标签: "emerging"(新兴) / "growing"(增长) / "stable"(稳定) / "declining"(衰退)

POST /algorithm/skills/evolution
"""
from typing import List, Optional

import numpy as np
from fastapi import APIRouter
from pydantic import BaseModel

from app.db import execute_query

router = APIRouter()


class SkillEvolutionPoint(BaseModel):
    period: str
    count: int
    growth_rate: Optional[float] = None  # 环比增长率


class SkillEvolutionResult(BaseModel):
    skill: str
    lifecycle: str          # emerging / growing / stable / declining
    lifecycle_zh: str       # 新兴 / 增长 / 稳定 / 衰退
    confidence: float       # 0~1
    current_demand: int
    trend_slope: float
    history: List[SkillEvolutionPoint]
    summary: str


class SkillEvolutionRequest(BaseModel):
    skills: List[str]
    window_months: int = 12  # 分析窗口（月数）


class SkillEvolutionResponse(BaseModel):
    window_months: int
    results: List[SkillEvolutionResult]


LIFECYCLE_MAP = {
    "emerging": "新兴",
    "growing": "增长",
    "stable": "稳定",
    "declining": "衰退",
}


def _classify_lifecycle(history_counts: List[int]) -> tuple[str, float, float]:
    """
    基于历史月度需求量序列，推断技能生命周期阶段
    返回 (lifecycle, confidence, trend_slope)
    """
    if len(history_counts) < 3:
        return "stable", 0.3, 0.0

    values = np.array(history_counts, dtype=float)
    x = np.arange(len(values), dtype=float)
    coeffs = np.polyfit(x, values, 1)
    slope = coeffs[0]
    mean_val = float(values.mean()) or 1.0
    relative_slope = slope / max(mean_val, 1)

    # 检查首次出现时间（新兴：仅最近几个月才出现）
    first_nonzero = next((i for i, v in enumerate(history_counts) if v > 0), None)
    is_recent = first_nonzero is not None and first_nonzero >= len(history_counts) - 3

    # 置信度基于数据量
    confidence = min(0.95, len([v for v in history_counts if v > 0]) / max(len(history_counts), 1))

    if is_recent and mean_val < 20:
        lifecycle = "emerging"
        confidence = min(confidence, 0.7)
    elif relative_slope > 0.05:
        lifecycle = "growing"
    elif relative_slope < -0.05:
        lifecycle = "declining"
    else:
        lifecycle = "stable"

    return lifecycle, round(confidence, 2), round(float(slope), 4)


@router.post("/evolution", response_model=SkillEvolutionResponse)
def track_skill_evolution(req: SkillEvolutionRequest):
    """
    追踪技能演化生命周期
    基于月度岗位需求量的时序分析
    """
    results = []

    for skill_name in req.skills:
        history_rows = execute_query("""
            SELECT DATE_FORMAT(jp.publish_date, '%%Y-%%m') AS period,
                   COUNT(*) AS cnt
            FROM biz_job_posting jp
            WHERE JSON_CONTAINS(jp.job_labels, JSON_QUOTE(:skill_name))
              AND jp.publish_date >= DATE_SUB(CURDATE(), INTERVAL :months MONTH)
              AND jp.publish_date IS NOT NULL
            GROUP BY period
            ORDER BY period ASC
        """, {"skill_name": skill_name, "months": req.window_months})

        if not history_rows:
            results.append(SkillEvolutionResult(
                skill=skill_name,
                lifecycle="stable",
                lifecycle_zh="稳定",
                confidence=0.0,
                current_demand=0,
                trend_slope=0.0,
                history=[],
                summary=f"技能 {skill_name} 在该时间窗口内无数据",
            ))
            continue

        history_counts = [r["cnt"] for r in history_rows]
        lifecycle, confidence, slope = _classify_lifecycle(history_counts)
        current_demand = history_counts[-1] if history_counts else 0

        # 计算环比增长率
        history_points = []
        for i, row in enumerate(history_rows):
            prev = history_counts[i - 1] if i > 0 else None
            growth_rate = None
            if prev is not None and prev > 0:
                growth_rate = round((row["cnt"] - prev) / prev, 3)
            history_points.append(SkillEvolutionPoint(
                period=row["period"],
                count=row["cnt"],
                growth_rate=growth_rate,
            ))

        # 生成摘要
        lifecycle_zh = LIFECYCLE_MAP.get(lifecycle, lifecycle)
        trend_desc = "快速增长" if slope > 50 else ("缓慢增长" if slope > 5 else ("下降中" if slope < -5 else "基本平稳"))
        summary = f"{skill_name} 技能处于{lifecycle_zh}阶段，近{req.window_months}个月需求{trend_desc}，当前月需求量 {current_demand} 个岗位"

        results.append(SkillEvolutionResult(
            skill=skill_name,
            lifecycle=lifecycle,
            lifecycle_zh=lifecycle_zh,
            confidence=confidence,
            current_demand=current_demand,
            trend_slope=slope,
            history=history_points,
            summary=summary,
        ))

    return SkillEvolutionResponse(
        window_months=req.window_months,
        results=results,
    )
