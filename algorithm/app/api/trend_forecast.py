"""
趋势预测引擎（升级版 v2）
==========================
V2 升级：
  - ARIMA 自动阶次选择（AIC 准则），替代简单线性外推
  - 预测置信区间（confidence_lower / confidence_upper）
  - 修复月份进位 Bug（使用 dateutil.relativedelta）
  - 月份递增使用标准日期库，无边界问题
"""
from datetime import datetime
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
    confidence_lower: Optional[float] = None
    confidence_upper: Optional[float] = None


class TrendResponse(BaseModel):
    dimension: str
    dimension_value: str
    metric: str
    history: List[TrendPoint]
    forecast: List[TrendPoint]
    trend_direction: str  # rising / stable / declining
    growth_rate: Optional[float] = None
    # 新增：ARIMA 模型元信息
    model_type: str = "linear"   # "arima" or "linear"
    model_order: Optional[str] = None  # e.g. "(1,1,0)"
    aic: Optional[float] = None


def _try_arima_forecast(values: np.ndarray, periods: int) -> dict | None:
    """
    尝试使用 ARIMA 预测。自动选择最优阶次（AIC 准则）。
    返回 dict 或 None（ARIMA 不可用时）。
    """
    try:
        from statsmodels.tsa.arima.model import ARIMA

        best_aic = float("inf")
        best_order = (1, 1, 0)

        # 在小参数空间内搜索最优 ARIMA 阶次
        for p in range(3):
            for d in range(2):
                for q in range(3):
                    try:
                        m = ARIMA(values, order=(p, d, q))
                        result = m.fit()
                        if result.aic < best_aic:
                            best_aic = result.aic
                            best_order = (p, d, q)
                    except Exception:
                        continue

        # 用最优阶次训练最终模型
        final_model = ARIMA(values, order=best_order)
        final_result = final_model.fit()
        forecast_result = final_result.get_forecast(steps=periods)
        forecast_mean = forecast_result.predicted_mean.tolist()
        conf_int = forecast_result.conf_int()  # DataFrame with lower/upper

        return {
            "mean": forecast_mean,
            "lower": conf_int.iloc[:, 0].tolist(),
            "upper": conf_int.iloc[:, 1].tolist(),
            "order": str(best_order),
            "aic": round(float(best_aic), 2),
        }
    except ImportError:
        return None  # statsmodels 未安装
    except Exception:
        return None  # 数据不足等情况


def _linear_forecast(values: np.ndarray, periods: int) -> dict:
    """线性外推兜底（无 statsmodels 或数据量不足时使用）"""
    x = np.arange(len(values), dtype=float)
    coeffs = np.polyfit(x, values, 1)
    slope = coeffs[0]
    std_err = float(np.std(values - np.polyval(coeffs, x)))

    mean, lower, upper = [], [], []
    for i in range(1, periods + 1):
        pred = max(0.0, float(np.polyval(coeffs, len(values) - 1 + i)))
        mean.append(pred)
        lower.append(max(0.0, pred - 1.96 * std_err))
        upper.append(pred + 1.96 * std_err)

    return {"mean": mean, "lower": lower, "upper": upper, "slope": float(slope)}


def _next_period(last_period: str, i: int) -> str:
    """正确计算下 i 个月的年月字符串，使用标准日期库避免手写进位 Bug"""
    try:
        from dateutil.relativedelta import relativedelta
        base = datetime.strptime(last_period + "-01", "%Y-%m-%d")
        next_date = base + relativedelta(months=i)
        return next_date.strftime("%Y-%m")
    except ImportError:
        # dateutil 不可用时的简单实现（兜底）
        year, month = int(last_period[:4]), int(last_period[5:7])
        total = year * 12 + (month - 1) + i
        return f"{total // 12}-{(total % 12) + 1:02d}"


@router.post("/forecast", response_model=TrendResponse)
def forecast_trend(
    dimension: str = "skill",
    dimension_value: str = "Python",
    metric: str = "demand",
    forecast_periods: int = 3
):
    """
    趋势预测（升级版）
    V2：ARIMA 自动选阶 → 置信区间；fallback 到线性拟合
    """
    if dimension == "skill":
        history_rows = execute_query("""
            SELECT DATE_FORMAT(jp.publish_date, '%%Y-%%m') AS period,
                   COUNT(*) AS value
            FROM biz_job_posting jp
            WHERE JSON_CONTAINS(jp.job_labels, JSON_QUOTE(:skill_name))
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
            model_type="none",
        )

    history = [TrendPoint(period=r["period"], value=float(r["value"])) for r in history_rows]
    values = np.array([h.value for h in history], dtype=float)

    if len(values) >= 2:
        # 优先尝试 ARIMA（数据充足时效果更好）
        arima_result = _try_arima_forecast(values, forecast_periods) if len(values) >= 6 else None

        if arima_result:
            model_type = "arima"
            model_order = arima_result.get("order")
            aic = arima_result.get("aic")
            means = arima_result["mean"]
            lowers = arima_result["lower"]
            uppers = arima_result["upper"]
        else:
            model_type = "linear"
            model_order = None
            aic = None
            linear_result = _linear_forecast(values, forecast_periods)
            means = linear_result["mean"]
            lowers = linear_result["lower"]
            uppers = linear_result["upper"]

        last_period = history[-1].period
        forecast = []
        for i in range(forecast_periods):
            period = _next_period(last_period, i + 1)
            forecast.append(TrendPoint(
                period=period,
                value=round(max(0.0, means[i]), 2),
                is_forecast=True,
                confidence_lower=round(max(0.0, lowers[i]), 2),
                confidence_upper=round(uppers[i], 2),
            ))

        # 趋势方向
        slope = float(np.polyfit(np.arange(len(values)), values, 1)[0])
        mean_val = float(values.mean()) or 1.0
        if slope > mean_val * 0.02:
            direction = "rising"
        elif slope < -mean_val * 0.02:
            direction = "declining"
        else:
            direction = "stable"

        growth_rate = round(slope / max(mean_val, 1), 4)
    else:
        forecast = []
        direction = "stable"
        growth_rate = 0.0
        model_type = "none"
        model_order = None
        aic = None

    return TrendResponse(
        dimension=dimension,
        dimension_value=dimension_value,
        metric=metric,
        history=history,
        forecast=forecast,
        trend_direction=direction,
        growth_rate=growth_rate,
        model_type=model_type,
        model_order=model_order,
        aic=aic,
    )
