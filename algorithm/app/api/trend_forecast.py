"""
趋势预测引擎
=============
V1: 基于月度统计 + 简单线性趋势
V2 (TODO): Prophet / LSTM 时序预测
"""
from typing import List, Optional

import numpy as np
from fastapi import APIRouter
from pydantic import BaseModel

from app.db import execute_query

router = APIRouter()


class TrendPoint(BaseModel):
    period: str
    value: float
    is_forecast: bool = False


class TrendResponse(BaseModel):
    dimension: str
    dimension_value: str
    metric: str
    history: List[TrendPoint]
    forecast: List[TrendPoint]
    trend_direction: str  # rising / stable / declining
    growth_rate: Optional[float] = None


@router.post("/forecast", response_model=TrendResponse)
def forecast_trend(
    dimension: str = "skill",
    dimension_value: str = "Python",
    metric: str = "demand",
    forecast_periods: int = 3
):
    """
    趋势预测
    V1 实现：查询月度历史数据 → 线性拟合 → 外推预测
    """
    if dimension == "skill":
        history_rows = execute_query("""
            SELECT DATE_FORMAT(jp.publish_date, '%%Y-%%m') AS period,
                   COUNT(*) AS value
            FROM biz_job_posting jp
            JOIN biz_job_skill js ON jp.id = js.job_id
            JOIN biz_skill s ON js.skill_id = s.id
            WHERE s.skill_name = :skill_name
              AND jp.publish_date IS NOT NULL
            GROUP BY period
            ORDER BY period
        """, {"skill_name": dimension_value})
    elif dimension == "city":
        history_rows = execute_query("""
            SELECT DATE_FORMAT(publish_date, '%%Y-%%m') AS period,
                   COUNT(*) AS value
            FROM biz_job_posting
            WHERE job_city LIKE :city AND publish_date IS NOT NULL
            GROUP BY period
            ORDER BY period
        """, {"city": f"%{dimension_value}%"})
    elif dimension == "salary":
        history_rows = execute_query("""
            SELECT DATE_FORMAT(publish_date, '%%Y-%%m') AS period,
                   AVG(salary_min) AS value
            FROM biz_job_posting
            WHERE salary_min > 0 AND publish_date IS NOT NULL
            GROUP BY period
            ORDER BY period
        """)
    else:
        history_rows = []

    if not history_rows:
        return TrendResponse(
            dimension=dimension,
            dimension_value=dimension_value,
            metric=metric,
            history=[],
            forecast=[],
            trend_direction="stable",
        )

    history = [TrendPoint(period=r["period"], value=float(r["value"])) for r in history_rows]

    # 线性拟合
    values = np.array([h.value for h in history])
    x = np.arange(len(values), dtype=float)

    if len(values) >= 2:
        coeffs = np.polyfit(x, values, 1)
        slope = coeffs[0]

        # 外推预测
        forecast = []
        last_period = history[-1].period
        for i in range(1, forecast_periods + 1):
            pred_val = max(0, float(np.polyval(coeffs, len(values) - 1 + i)))
            # 简单的月份递增（粗略）
            year, month = int(last_period[:4]), int(last_period[5:7])
            month += i
            while month > 12:
                month -= 12
                year += 1
            forecast.append(TrendPoint(
                period=f"{year}-{month:02d}",
                value=round(pred_val, 2),
                is_forecast=True,
            ))

        # 趋势方向
        if slope > values.mean() * 0.02:
            direction = "rising"
        elif slope < -values.mean() * 0.02:
            direction = "declining"
        else:
            direction = "stable"

        growth_rate = round(float(slope / max(values.mean(), 1)), 4)
    else:
        forecast = []
        direction = "stable"
        growth_rate = 0.0

    return TrendResponse(
        dimension=dimension,
        dimension_value=dimension_value,
        metric=metric,
        history=history,
        forecast=forecast,
        trend_direction=direction,
        growth_rate=growth_rate,
    )
