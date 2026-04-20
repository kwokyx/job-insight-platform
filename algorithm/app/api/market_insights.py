from __future__ import annotations

from typing import Any, Optional

import pandas as pd
from fastapi import APIRouter
from pydantic import BaseModel

from app.db import execute_query

router = APIRouter()


class MarketInsightRequest(BaseModel):
    city: Optional[str] = None
    industry: Optional[str] = None
    months: int = 12


def _sanitize_like(value: Optional[str]) -> Optional[str]:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def _base_conditions(city: Optional[str], industry: Optional[str]) -> tuple[list[str], dict[str, Any]]:
    conditions = ["publish_date IS NOT NULL"]
    params: dict[str, Any] = {}

    if city:
        conditions.append("COALESCE(city, job_city) LIKE :city")
        params["city"] = f"%{city}%"
    if industry:
        conditions.append("COALESCE(industry_name, job_classification) LIKE :industry")
        params["industry"] = f"%{industry}%"

    return conditions, params


def _jobs_frame(city: Optional[str], industry: Optional[str], months: int) -> pd.DataFrame:
    conditions, params = _base_conditions(city, industry)
    conditions.append("publish_date >= DATE_SUB(CURDATE(), INTERVAL :months MONTH)")
    params["months"] = max(3, min(months, 24))

    sql = f"""
        SELECT
            publish_date,
            COALESCE(city, job_city) AS city,
            COALESCE(industry_name, job_classification) AS industry,
            salary_min,
            salary_max,
            company_size,
            company_finance AS finance_stage
        FROM biz_job_posting
        WHERE {' AND '.join(conditions)}
    """
    frame = pd.DataFrame(execute_query(sql, params))
    if frame.empty:
        return frame

    frame["publish_date"] = pd.to_datetime(frame["publish_date"], errors="coerce")
    frame = frame.dropna(subset=["publish_date"]).copy()
    frame["city"] = frame["city"].fillna("未知")
    frame["industry"] = frame["industry"].fillna("未知")
    frame["salary_min"] = pd.to_numeric(frame["salary_min"], errors="coerce")
    frame["salary_max"] = pd.to_numeric(frame["salary_max"], errors="coerce")
    frame["salary_mid"] = frame[["salary_min", "salary_max"]].mean(axis=1)
    frame["period"] = frame["publish_date"].dt.to_period("M").astype(str)
    frame["month_start"] = frame["publish_date"].dt.to_period("M").dt.to_timestamp()
    return frame


def _skills_frame(city: Optional[str], industry: Optional[str], months: int) -> pd.DataFrame:
    conditions, params = _base_conditions(city, industry)
    conditions.append("jp.publish_date >= DATE_SUB(CURDATE(), INTERVAL :months MONTH)")
    params["months"] = max(3, min(months, 24))

    sql = f"""
        SELECT
            jp.publish_date,
            d.label_name AS skill
        FROM biz_job_posting jp
        JOIN job_label_rel r ON jp.id = r.job_posting_id
        JOIN job_label_dict d ON r.label_id = d.id
        WHERE {' AND '.join(condition.replace('publish_date', 'jp.publish_date') for condition in conditions)}
          AND NOT EXISTS (
              SELECT 1 FROM job_welfare_dict w WHERE w.welfare_name = d.label_name
          )
          AND d.label_name IS NOT NULL
          AND d.label_name != ''
    """
    frame = pd.DataFrame(execute_query(sql, params))
    if frame.empty:
        return frame

    frame["publish_date"] = pd.to_datetime(frame["publish_date"], errors="coerce")
    frame = frame.dropna(subset=["publish_date"]).copy()
    frame["skill"] = frame["skill"].fillna("未知技能")
    frame["period"] = frame["publish_date"].dt.to_period("M").astype(str)
    return frame


def _safe_pct_change(current: float, previous: float) -> float:
    if previous <= 0:
        return 0.0 if current <= 0 else 100.0
    return round((current - previous) / previous * 100, 2)


def _confidence_label(score: float) -> str:
    if score >= 0.85:
        return "高"
    if score >= 0.6:
        return "中"
    return "低"


def _risk_label(hhi: float, top_share: float) -> str:
    if hhi >= 0.22 or top_share >= 0.35:
        return "高集中"
    if hhi >= 0.12 or top_share >= 0.22:
        return "中集中"
    return "分散"


def _to_records(frame: pd.DataFrame, columns: list[str], limit: int = 5) -> list[dict[str, Any]]:
    if frame.empty:
        return []
    records = frame.loc[:, columns].head(limit).to_dict(orient="records")
    normalized: list[dict[str, Any]] = []
    for item in records:
        row = {}
        for key, value in item.items():
            if pd.isna(value):
                row[key] = None
            elif isinstance(value, (pd.Timestamp, pd.Period)):
                row[key] = str(value)
            elif isinstance(value, (float, int)):
                row[key] = round(float(value), 2)
            else:
                row[key] = value
        normalized.append(row)
    return normalized


def _build_response(city: Optional[str], industry: Optional[str], months: int) -> dict[str, Any]:
    jobs = _jobs_frame(city, industry, months)
    skills = _skills_frame(city, industry, months)

    if jobs.empty:
        return {
            "source": "pandas-engine",
            "filters": {"city": city, "industry": industry, "months": months},
            "sample": {
                "totalJobs": 0,
                "recentJobs30d": 0,
                "activeMonths": 0,
                "confidenceScore": 0.0,
                "confidenceLabel": "低",
            },
            "marketPulse": {},
            "cityConcentration": {},
            "industryMomentum": {},
            "skillsInsight": {},
            "structuralInsights": [],
            "recommendations": ["当前筛选条件下样本不足，建议放宽城市或行业范围后再做治理判断。"],
        }

    total_jobs = int(len(jobs))
    recent_jobs_30d = int((jobs["publish_date"] >= (pd.Timestamp.today().normalize() - pd.Timedelta(days=30))).sum())
    active_months = int(jobs["period"].nunique())
    confidence_score = min(0.98, (min(total_jobs, 5000) / 5000) * 0.7 + (min(active_months, 12) / 12) * 0.3)

    monthly = (
        jobs.groupby("period", as_index=False)
        .agg(
            jobCount=("period", "size"),
            avgSalaryMin=("salary_min", "mean"),
            avgSalaryMax=("salary_max", "mean"),
            avgSalaryMid=("salary_mid", "mean"),
        )
        .sort_values("period")
    )
    monthly["jobCount"] = monthly["jobCount"].fillna(0)
    monthly["avgSalaryMid"] = monthly["avgSalaryMid"].fillna(0)

    recent_window = monthly.tail(min(3, len(monthly)))
    previous_window = monthly.iloc[max(0, len(monthly) - 6):max(0, len(monthly) - 3)]
    recent_demand = float(recent_window["jobCount"].mean()) if not recent_window.empty else 0.0
    previous_demand = float(previous_window["jobCount"].mean()) if not previous_window.empty else 0.0
    recent_salary = float(recent_window["avgSalaryMid"].mean()) if not recent_window.empty else 0.0
    previous_salary = float(previous_window["avgSalaryMid"].mean()) if not previous_window.empty else 0.0

    demand_momentum_pct = _safe_pct_change(recent_demand, previous_demand)
    salary_momentum_pct = _safe_pct_change(recent_salary, previous_salary)
    salary_volatility = round(
        (monthly["avgSalaryMid"].std(ddof=0) / monthly["avgSalaryMid"].mean() * 100)
        if monthly["avgSalaryMid"].mean() > 0 else 0.0,
        2,
    )

    city_dist = (
        jobs.groupby("city", as_index=False)
        .agg(jobCount=("city", "size"), avgSalaryMid=("salary_mid", "mean"))
        .sort_values(["jobCount", "avgSalaryMid"], ascending=[False, False])
    )
    city_dist["share"] = city_dist["jobCount"] / max(total_jobs, 1)
    city_hhi = float((city_dist["share"] ** 2).sum()) if not city_dist.empty else 0.0
    top_city = city_dist.iloc[0] if not city_dist.empty else None

    industry_monthly = (
        jobs.groupby(["industry", "period"], as_index=False)
        .agg(jobCount=("industry", "size"), avgSalaryMid=("salary_mid", "mean"))
    )
    periods = sorted(monthly["period"].unique().tolist())
    recent_periods = periods[-min(3, len(periods)):]
    previous_periods = periods[-min(6, len(periods)):-min(3, len(periods))] if len(periods) > 3 else []
    industry_summary = (
        industry_monthly.assign(
            recentCount=lambda df: df["jobCount"].where(df["period"].isin(recent_periods), 0),
            previousCount=lambda df: df["jobCount"].where(df["period"].isin(previous_periods), 0),
        )
        .groupby("industry", as_index=False)
        .agg(
            currentCount=("recentCount", "sum"),
            previousCount=("previousCount", "sum"),
            avgSalaryMid=("avgSalaryMid", "mean"),
        )
    )
    industry_summary["growthPct"] = industry_summary.apply(
        lambda row: _safe_pct_change(float(row["currentCount"]), float(row["previousCount"])),
        axis=1,
    )
    industry_summary = industry_summary.sort_values(["growthPct", "currentCount"], ascending=[False, False])
    declining_industry = industry_summary.sort_values(["growthPct", "currentCount"], ascending=[True, False])

    skill_counts = pd.DataFrame(columns=["skill", "count", "share", "growthPct"])
    if not skills.empty:
        skill_recent = skills[skills["period"].isin(recent_periods)]
        skill_previous = skills[skills["period"].isin(previous_periods)] if previous_periods else skills.iloc[0:0]

        recent_group = skill_recent.groupby("skill").size().rename("recentCount")
        previous_group = skill_previous.groupby("skill").size().rename("previousCount")
        skill_counts = (
            pd.concat([recent_group, previous_group], axis=1)
            .fillna(0)
            .reset_index()
            .rename(columns={"index": "skill"})
        )
        skill_counts["count"] = skill_counts["recentCount"]
        total_recent_skill = max(skill_counts["count"].sum(), 1)
        skill_counts["share"] = skill_counts["count"] / total_recent_skill
        skill_counts["growthPct"] = skill_counts.apply(
            lambda row: _safe_pct_change(float(row["recentCount"]), float(row["previousCount"])),
            axis=1,
        )
        skill_counts = skill_counts.sort_values(["count", "growthPct"], ascending=[False, False])

    top_skill_share = float(skill_counts["share"].head(5).sum()) if not skill_counts.empty else 0.0
    diversification_index = round(1 - float((skill_counts["share"] ** 2).sum()), 4) if not skill_counts.empty else 0.0

    structural_insights = [
        {
            "title": "需求动量",
            "value": demand_momentum_pct,
            "unit": "%",
            "direction": "up" if demand_momentum_pct >= 0 else "down",
            "summary": f"最近窗口岗位需求较上一窗口{'上升' if demand_momentum_pct >= 0 else '回落'} {abs(demand_momentum_pct):.2f}%。",
        },
        {
            "title": "薪资动量",
            "value": salary_momentum_pct,
            "unit": "%",
            "direction": "up" if salary_momentum_pct >= 0 else "down",
            "summary": f"平均薪资中位带较上一窗口{'提升' if salary_momentum_pct >= 0 else '下降'} {abs(salary_momentum_pct):.2f}%。",
        },
        {
            "title": "城市集中度",
            "value": round(city_hhi, 4),
            "unit": "HHI",
            "direction": "neutral",
            "summary": f"头部城市占比 {round(float(top_city['share']) * 100, 2) if top_city is not None else 0:.2f}%，空间分布属于{_risk_label(city_hhi, float(top_city['share']) if top_city is not None else 0.0)}。",
        },
        {
            "title": "技能多样性",
            "value": diversification_index,
            "unit": "指数",
            "direction": "neutral",
            "summary": f"前五技能合计占比 {round(top_skill_share * 100, 2):.2f}%，技能结构{'偏集中' if top_skill_share >= 0.55 else '相对均衡'}。",
        },
    ]

    recommendations: list[str] = []
    if demand_momentum_pct > 12:
        recommendations.append("岗位需求处于扩张区间，建议在专业侧优先扩容与头部岗位族对应的核心课程与实训学时。")
    if demand_momentum_pct < -8:
        recommendations.append("岗位需求出现回落，建议降低低转化课程比重，转向岗位迁移能力和复合技能训练。")
    if top_city is not None and float(top_city["share"]) >= 0.3:
        recommendations.append(f"岗位需求明显集中在{top_city['city']}，学校侧应同步配置区域化就业合作与异地实习资源。")
    if salary_volatility >= 12:
        recommendations.append("薪资波动较大，说明市场分层明显，课程设计应增加分层培养路径与证书型能力模块。")
    if top_skill_share >= 0.55:
        recommendations.append("技能需求集中度偏高，适合围绕高频技能构建“核心能力点+进阶专题”两层课程结构。")
    if skill_counts.empty or len(recommendations) < 3:
        recommendations.append("当前数据可支撑方向判断，但仍建议结合院校自身毕业去向数据做二次校验。")

    return {
        "source": "pandas-engine",
        "filters": {"city": city, "industry": industry, "months": months},
        "sample": {
            "totalJobs": total_jobs,
            "recentJobs30d": recent_jobs_30d,
            "activeMonths": active_months,
            "confidenceScore": round(confidence_score, 4),
            "confidenceLabel": _confidence_label(confidence_score),
        },
        "marketPulse": {
            "medianSalaryMin": round(float(jobs["salary_min"].median()) if jobs["salary_min"].notna().any() else 0.0, 2),
            "medianSalaryMax": round(float(jobs["salary_max"].median()) if jobs["salary_max"].notna().any() else 0.0, 2),
            "salaryBandwidth": round(
                float(jobs["salary_max"].median() - jobs["salary_min"].median())
                if jobs["salary_min"].notna().any() and jobs["salary_max"].notna().any() else 0.0,
                2,
            ),
            "salaryVolatility": salary_volatility,
            "demandMomentumPct": demand_momentum_pct,
            "salaryMomentumPct": salary_momentum_pct,
            "monthlyTrend": _to_records(monthly, ["period", "jobCount", "avgSalaryMin", "avgSalaryMax", "avgSalaryMid"], 12),
        },
        "cityConcentration": {
            "topCity": None if top_city is None else top_city["city"],
            "topCityShare": round(float(top_city["share"]) * 100, 2) if top_city is not None else 0.0,
            "hhi": round(city_hhi, 4),
            "riskLevel": _risk_label(city_hhi, float(top_city["share"]) if top_city is not None else 0.0),
            "leadingCities": _to_records(city_dist.assign(sharePct=city_dist["share"] * 100), ["city", "jobCount", "sharePct", "avgSalaryMid"]),
        },
        "industryMomentum": {
            "topGrowingIndustries": _to_records(industry_summary, ["industry", "currentCount", "previousCount", "growthPct", "avgSalaryMid"]),
            "decliningIndustries": _to_records(declining_industry, ["industry", "currentCount", "previousCount", "growthPct", "avgSalaryMid"]),
        },
        "skillsInsight": {
            "topSkillShare": round(top_skill_share * 100, 2),
            "diversificationIndex": diversification_index,
            "hotSkills": _to_records(skill_counts, ["skill", "count", "share", "growthPct"]),
            "emergingSkills": _to_records(skill_counts.sort_values(["growthPct", "count"], ascending=[False, False]), ["skill", "count", "share", "growthPct"]),
            "saturatedSkills": _to_records(skill_counts.sort_values(["growthPct", "count"], ascending=[True, False]), ["skill", "count", "share", "growthPct"]),
        },
        "structuralInsights": structural_insights,
        "recommendations": recommendations[:5],
    }


@router.post("/deep")
def deep_market_insights(payload: MarketInsightRequest):
    city = _sanitize_like(payload.city)
    industry = _sanitize_like(payload.industry)
    months = max(3, min(payload.months, 24))
    return _build_response(city, industry, months)
