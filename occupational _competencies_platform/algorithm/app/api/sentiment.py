"""
市场情绪分析引擎
=================
复合情绪指数 = α×DemandIndex + β×SalaryIndex + γ×QualityIndex + δ×FreshnessIndex
"""
from typing import Optional
from fastapi import APIRouter
from pydantic import BaseModel

from app.db import execute_query

router = APIRouter()


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


@router.post("/index", response_model=SentimentResponse)
def market_sentiment(city: Optional[str] = None, industry: Optional[str] = None):
    """计算市场情绪综合指数"""

    conditions = ["1=1"]
    params = {}
    if city:
        conditions.append("city LIKE :city")
        params["city"] = f"%{city}%"
    if industry:
        conditions.append("industry_name LIKE :industry")
        params["industry"] = f"%{industry}%"

    where = " AND ".join(conditions)

    # 总岗位数
    total_row = execute_query(f"SELECT COUNT(*) AS cnt FROM biz_job_posting WHERE {where}", params)
    total_jobs = total_row[0]["cnt"] if total_row else 0

    # 7天内新增
    recent_row = execute_query(f"""
        SELECT COUNT(*) AS cnt FROM biz_job_posting
        WHERE {where} AND publish_date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
    """, params)
    recent_7d = recent_row[0]["cnt"] if recent_row else 0

    # 平均薪资
    sal_row = execute_query(f"""
        SELECT AVG(salary_min) AS avg_sal FROM biz_job_posting
        WHERE {where} AND salary_min > 0
    """, params)
    avg_salary = float(sal_row[0]["avg_sal"]) if sal_row and sal_row[0]["avg_sal"] else 0

    # ── 各分项指数（0-100）──
    # 需求指数：7天新增占总量比例
    demand_index = min(100, (recent_7d / max(total_jobs, 1)) * 500) if total_jobs > 0 else 50

    # 薪资指数：对比全局平均
    global_avg_row = execute_query("SELECT AVG(salary_min) AS avg_sal FROM biz_job_posting WHERE salary_min > 0")
    global_avg = float(global_avg_row[0]["avg_sal"]) if global_avg_row and global_avg_row[0]["avg_sal"] else avg_salary
    salary_index = min(100, max(0, 50 + (avg_salary - global_avg) / max(global_avg, 1) * 100))

    # 质量指数：有薪资信息的岗位占比
    quality_row = execute_query(f"""
        SELECT COUNT(*) AS cnt FROM biz_job_posting
        WHERE {where} AND salary_min IS NOT NULL AND salary_min > 0
    """, params)
    quality_count = quality_row[0]["cnt"] if quality_row else 0
    quality_index = (quality_count / max(total_jobs, 1)) * 100

    # 新鲜度：7天岗位占比
    freshness_index = min(100, (recent_7d / max(total_jobs, 1)) * 300)

    # 综合情绪分数
    score = 0.35 * demand_index + 0.25 * salary_index + 0.20 * quality_index + 0.20 * freshness_index
    score = round(min(100, max(0, score)), 1)

    # 评级
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

    desc = f"{'城市' + city if city else '全国'}{'·' + industry if industry else ''}就业市场情绪{rating}，"
    desc += f"共 {total_jobs} 个岗位，近7天新增 {recent_7d} 个，平均薪资 {avg_salary:.1f}K/月"

    return SentimentResponse(
        sentiment_score=score,
        rating=rating,
        demand_index=round(demand_index, 1),
        salary_index=round(salary_index, 1),
        quality_index=round(quality_index, 1),
        freshness_index=round(freshness_index, 1),
        total_jobs=total_jobs,
        recent_jobs_7d=recent_7d,
        avg_salary=round(avg_salary, 2) if avg_salary else None,
        description=desc,
    )
