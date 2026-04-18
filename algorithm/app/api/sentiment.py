"""
市场情绪分析引擎（升级版 v2）
================================
V2 升级：
  - 历史端点：/sentiment/history — 最近 12 个月情绪变化
  - 对比端点：/sentiment/compare — 多城市/行业情绪对比（雷达图数据）
  - 公共 WHERE 子句构建函数（消除重复代码）
"""
from typing import List, Optional
from fastapi import APIRouter
from pydantic import BaseModel

from app.db import execute_query

router = APIRouter()


# ─── 公共工具 ──────────────────────────────────────

def _build_where(city: str | None, industry: str | None) -> tuple[str, dict]:
    """提取重复的条件构建逻辑"""
    conditions = ["1=1"]
    params = {}
    if city:
        conditions.append("job_city LIKE :city")
        params["city"] = f"%{city}%"
    if industry:
        conditions.append("job_classification LIKE :industry")
        params["industry"] = f"%{industry}%"
    return " AND ".join(conditions), params


def _compute_sentiment(city: str | None, industry: str | None, where: str, params: dict) -> dict:
    """核心情绪指数计算（供多个端点复用）"""
    total_row = execute_query(f"SELECT COUNT(*) AS cnt FROM biz_job_posting WHERE {where}", params)
    total_jobs = total_row[0]["cnt"] if total_row else 0

    recent_row = execute_query(f"""
        SELECT COUNT(*) AS cnt FROM biz_job_posting
        WHERE {where} AND publish_date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
    """, params)
    recent_7d = recent_row[0]["cnt"] if recent_row else 0

    sal_row = execute_query(f"""
        SELECT AVG(salary_min) AS avg_sal FROM biz_job_posting
        WHERE {where} AND salary_min > 0
    """, params)
    avg_salary = float(sal_row[0]["avg_sal"]) if sal_row and sal_row[0]["avg_sal"] else 0

    demand_index = min(100, (recent_7d / max(total_jobs, 1)) * 500) if total_jobs > 0 else 50

    global_avg_row = execute_query("SELECT AVG(salary_min) AS avg_sal FROM biz_job_posting WHERE salary_min > 0")
    global_avg = float(global_avg_row[0]["avg_sal"]) if global_avg_row and global_avg_row[0]["avg_sal"] else avg_salary or 1
    salary_index = min(100, max(0, 50 + (avg_salary - global_avg) / max(global_avg, 1) * 100))

    quality_row = execute_query(f"""
        SELECT COUNT(*) AS cnt FROM biz_job_posting
        WHERE {where} AND salary_min IS NOT NULL AND salary_min > 0
    """, params)
    quality_count = quality_row[0]["cnt"] if quality_row else 0
    quality_index = (quality_count / max(total_jobs, 1)) * 100

    freshness_index = min(100, (recent_7d / max(total_jobs, 1)) * 300)

    score = 0.35 * demand_index + 0.25 * salary_index + 0.20 * quality_index + 0.20 * freshness_index
    score = round(min(100, max(0, score)), 1)

    if score >= 80:
        rating = "极热"
    elif score >= 60:
        rating = "偏热"
    elif score >= 40:
        rating = "中性"
    elif score >= 20:
        rating = "偏冷"
    else:
        rating = "极冷"

    return {
        "sentiment_score": score,
        "rating": rating,
        "demand_index": round(demand_index, 1),
        "salary_index": round(salary_index, 1),
        "quality_index": round(quality_index, 1),
        "freshness_index": round(freshness_index, 1),
        "total_jobs": total_jobs,
        "recent_jobs_7d": recent_7d,
        "avg_salary": round(avg_salary, 2) if avg_salary else None,
        "city": city,
        "industry": industry,
    }


# ─── 模型 ──────────────────────────────────────────

class SentimentResponse(BaseModel):
    sentiment_score: float  # 0-100
    rating: str             # 极热/偏热/中性/偏冷/极冷
    demand_index: float
    salary_index: float
    quality_index: float
    freshness_index: float
    total_jobs: int
    recent_jobs_7d: int
    avg_salary: Optional[float] = None
    description: str = ""


class SentimentHistoryPoint(BaseModel):
    period: str       # YYYY-MM
    sentiment_score: float
    demand_index: float
    salary_index: float
    total_jobs: int


class SentimentHistoryResponse(BaseModel):
    city: Optional[str] = None
    industry: Optional[str] = None
    history: List[SentimentHistoryPoint]


class SentimentCompareItem(BaseModel):
    label: str   # 城市名或行业名
    sentiment_score: float
    demand_index: float
    salary_index: float
    quality_index: float
    freshness_index: float
    total_jobs: int


class SentimentCompareResponse(BaseModel):
    dimension: str  # "city" or "industry"
    items: List[SentimentCompareItem]


# ─── 端点 ──────────────────────────────────────────

@router.post("/index", response_model=SentimentResponse)
def market_sentiment(city: Optional[str] = None, industry: Optional[str] = None):
    """计算市场情绪综合指数（当前快照）"""
    where, params = _build_where(city, industry)
    data = _compute_sentiment(city, industry, where, params)

    desc = f"{'城市' + city if city else '全国'}{'·' + industry if industry else ''}就业市场情绪{data['rating']}，"
    desc += f"共 {data['total_jobs']} 个岗位，近7天新增 {data['recent_jobs_7d']} 个，平均薪资 {data.get('avg_salary') or 0:.1f}K/月"

    return SentimentResponse(**data, description=desc)


@router.post("/history", response_model=SentimentHistoryResponse)
def sentiment_history(
    city: Optional[str] = None,
    industry: Optional[str] = None,
    months: int = 12,
):
    """
    返回最近 N 个月的情绪指数历史变化
    每月计算一次快照（基于当月发布的岗位）
    """
    where_extra = ""
    params: dict = {}
    if city:
        where_extra += " AND job_city LIKE :city"
        params["city"] = f"%{city}%"
    if industry:
        where_extra += " AND job_classification LIKE :industry"
        params["industry"] = f"%{industry}%"
    params["months"] = months

    monthly_rows = execute_query(f"""
        SELECT
            DATE_FORMAT(publish_date, '%%Y-%%m') AS period,
            COUNT(*) AS total_jobs,
            COUNT(CASE WHEN publish_date >= DATE_SUB(LAST_DAY(publish_date), INTERVAL 6 DAY) THEN 1 END) AS recent_count,
            AVG(CASE WHEN salary_min > 0 THEN salary_min END) AS avg_salary
        FROM biz_job_posting
        WHERE publish_date >= DATE_SUB(CURDATE(), INTERVAL :months MONTH)
          {where_extra}
        GROUP BY period
        ORDER BY period DESC
        LIMIT :months
    """, params)

    if not monthly_rows:
        return SentimentHistoryResponse(city=city, industry=industry, history=[])

    # 全局平均薪资（用于 salary_index 基准）
    global_avg_row = execute_query("SELECT AVG(salary_min) AS avg_sal FROM biz_job_posting WHERE salary_min > 0")
    global_avg = float(global_avg_row[0]["avg_sal"]) if global_avg_row and global_avg_row[0]["avg_sal"] else 5000

    history = []
    for row in reversed(monthly_rows):  # 时间升序
        total = row["total_jobs"] or 0
        recent = row["recent_count"] or 0
        avg_sal = float(row["avg_salary"] or 0)

        demand_index = min(100, (recent / max(total, 1)) * 500)
        salary_index = min(100, max(0, 50 + (avg_sal - global_avg) / max(global_avg, 1) * 100))
        score = round(0.6 * demand_index + 0.4 * salary_index, 1)

        history.append(SentimentHistoryPoint(
            period=row["period"],
            sentiment_score=score,
            demand_index=round(demand_index, 1),
            salary_index=round(salary_index, 1),
            total_jobs=total,
        ))

    return SentimentHistoryResponse(city=city, industry=industry, history=history)


@router.post("/compare", response_model=SentimentCompareResponse)
def sentiment_compare(
    dimension: str = "city",
    values: Optional[List[str]] = None,
    top_n: int = 8,
):
    """
    多维度情绪指数对比（用于雷达图）
    dimension: "city" | "industry"
    values: 指定对比的城市/行业列表（为空则取 top N）
    """
    if dimension == "city":
        col = "job_city"
    else:
        col = "job_classification"

    # 获取 top N 维度值（如未指定）
    if not values:
        top_rows = execute_query(f"""
            SELECT {col} AS dim_val, COUNT(*) AS cnt
            FROM biz_job_posting
            WHERE {col} IS NOT NULL AND {col} != ''
            GROUP BY {col}
            ORDER BY cnt DESC
            LIMIT :top_n
        """, {"top_n": top_n})
        values = [r["dim_val"] for r in top_rows if r["dim_val"]]

    if not values:
        return SentimentCompareResponse(dimension=dimension, items=[])

    items = []
    for val in values:
        where, params = _build_where(
            city=val if dimension == "city" else None,
            industry=val if dimension == "industry" else None,
        )
        data = _compute_sentiment(
            city=val if dimension == "city" else None,
            industry=val if dimension == "industry" else None,
            where=where,
            params=params,
        )
        if data["total_jobs"] == 0:
            continue
        items.append(SentimentCompareItem(
            label=val,
            sentiment_score=data["sentiment_score"],
            demand_index=data["demand_index"],
            salary_index=data["salary_index"],
            quality_index=data["quality_index"],
            freshness_index=data["freshness_index"],
            total_jobs=data["total_jobs"],
        ))

    return SentimentCompareResponse(dimension=dimension, items=items)
