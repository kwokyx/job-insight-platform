"""
智能匹配推荐引擎
=================
基于多因子加权的岗位推荐
MatchScore = 0.30×SkillMatch + 0.15×LocationMatch + 0.15×SalaryMatch
           + 0.10×EducationMatch + 0.10×ExperienceMatch + 0.10×IndustryMatch
           + 0.10×Freshness
"""
from typing import Dict, List, Optional

from fastapi import APIRouter
from pydantic import BaseModel

from app.db import execute_query

router = APIRouter()


# ─── 模型 ──────────────────────────────────────────

class MatchRequest(BaseModel):
    skills: List[str] = []
    preferred_cities: List[str] = []
    education: Optional[str] = None
    experience: Optional[str] = None
    salary_min: Optional[float] = None
    salary_max: Optional[float] = None
    industry: Optional[str] = None
    limit: int = 20


class MatchDetail(BaseModel):
    skill_match: float = 0.0
    location_match: float = 0.0
    salary_match: float = 0.0
    education_match: float = 0.0


class MatchedJob(BaseModel):
    job_id: int
    title: str
    company_name: str
    city: str
    salary_text: Optional[str] = None
    education: Optional[str] = None
    experience: Optional[str] = None
    skills: List[str] = []
    match_score: float
    match_details: MatchDetail
    advice: dict = {}


class MatchResponse(BaseModel):
    total_candidates: int
    recommendations: List[MatchedJob]


# ─── 教育等级映射 ──────────────────────────────────

EDU_LEVELS = {"初中": 1, "高中": 2, "中专": 2, "大专": 3, "本科": 4, "硕士": 5, "博士": 6}
CITY_ALIASES = {
    "beijing": "北京",
    "shanghai": "上海",
    "guangzhou": "广州",
    "shenzhen": "深圳",
    "hangzhou": "杭州",
    "nanjing": "南京",
    "chengdu": "成都",
    "wuhan": "武汉",
    "xian": "西安",
    "suzhou": "苏州",
}
EDU_ALIASES = {
    "bachelor": "本科",
    "master": "硕士",
    "phd": "博士",
    "doctor": "博士",
    "associate": "大专",
    "college": "大专",
}


def _edu_level(edu: str) -> int:
    edu = EDU_ALIASES.get((edu or "").strip().lower(), edu or "")
    for key, val in EDU_LEVELS.items():
        if key in (edu or ""):
            return val
    return 3  # 默认大专


def _normalize_cities(cities: List[str]) -> List[str]:
    normalized = []
    for city in cities or []:
        value = (city or "").strip()
        if not value:
            continue
        normalized.append(CITY_ALIASES.get(value.lower(), value))
    return normalized


# ─── 核心匹配逻辑 ──────────────────────────────────

@router.post("", response_model=MatchResponse)
def match_jobs(req: MatchRequest):
    """多因子加权匹配"""

    # 1. 粗筛：从数据库取候选岗位（按城市/行业/薪资预过滤）
    conditions = ["jp.salary_min IS NOT NULL"]
    params: Dict = {}
    preferred_cities = _normalize_cities(req.preferred_cities)

    if preferred_cities:
        city_conds = " OR ".join(f"jp.city LIKE :city_{i}" for i in range(len(preferred_cities)))
        conditions.append(f"({city_conds})")
        for i, c in enumerate(preferred_cities):
            params[f"city_{i}"] = f"%{c}%"

    if req.industry:
        conditions.append("jp.industry_name LIKE :industry")
        params["industry"] = f"%{req.industry}%"

    where_clause = " AND ".join(conditions)

    candidate_rows = execute_query(f"""
        SELECT jp.id, jp.title, jp.company_name, jp.city, jp.education,
               jp.experience, jp.salary_min, jp.salary_max, jp.salary_text,
               jp.industry_name, jp.publish_date,
               GROUP_CONCAT(s.skill_name) AS skill_list
        FROM biz_job_posting jp
        LEFT JOIN biz_job_skill js ON jp.id = js.job_id
        LEFT JOIN biz_skill s ON js.skill_id = s.id
        WHERE {where_clause}
        GROUP BY jp.id
        ORDER BY jp.publish_date DESC
        LIMIT 500
    """, params)

    if not candidate_rows:
        return MatchResponse(total_candidates=0, recommendations=[])

    # 2. 精排：逐条计算匹配分数
    user_skills_lower = {s.lower() for s in req.skills}
    user_edu_level = _edu_level(req.education or "")
    scored_jobs = []

    for row in candidate_rows:
        job_skills = (row["skill_list"] or "").split(",")
        job_skills_lower = {s.strip().lower() for s in job_skills if s.strip()}

        # 技能匹配 (Jaccard)
        if user_skills_lower and job_skills_lower:
            intersection = user_skills_lower & job_skills_lower
            union = user_skills_lower | job_skills_lower
            skill_match = len(intersection) / len(union) if union else 0.0
        else:
            skill_match = 0.0

        # 城市匹配
        location_match = 0.0
        if preferred_cities:
            for pc in preferred_cities:
                if pc in (row["city"] or ""):
                    location_match = 1.0
                    break

        # 薪资匹配
        salary_match = 0.5  # 默认中性
        if req.salary_min and row["salary_min"]:
            job_mid = (float(row["salary_min"]) + float(row.get("salary_max") or row["salary_min"])) / 2
            user_mid = (req.salary_min + (req.salary_max or req.salary_min * 1.5)) / 2
            diff_ratio = abs(job_mid - user_mid) / max(user_mid, 1)
            salary_match = max(0, 1.0 - diff_ratio)

        # 学历匹配
        job_edu_level = _edu_level(row["education"] or "")
        edu_diff = user_edu_level - job_edu_level
        education_match = 1.0 if edu_diff >= 0 else max(0, 1.0 + edu_diff * 0.3)

        # 综合评分
        total_score = (
            0.30 * skill_match
            + 0.15 * location_match
            + 0.15 * salary_match
            + 0.10 * education_match
            + 0.30 * 0.5  # 其他因子暂用默认
        )

        matched_skills = [s for s in req.skills if s.lower() in job_skills_lower]
        missing_skills = [s for s in job_skills if s.strip() and s.strip().lower() not in user_skills_lower]

        scored_jobs.append(MatchedJob(
            job_id=row["id"],
            title=row["title"],
            company_name=row["company_name"],
            city=row["city"] or "",
            salary_text=row["salary_text"],
            education=row["education"],
            experience=row["experience"],
            skills=[s.strip() for s in job_skills if s.strip()],
            match_score=round(total_score, 3),
            match_details=MatchDetail(
                skill_match=round(skill_match, 3),
                location_match=round(location_match, 3),
                salary_match=round(salary_match, 3),
                education_match=round(education_match, 3),
            ),
            advice={
                "matched_skills": matched_skills[:5],
                "missing_skills": missing_skills[:5],
            },
        ))

    # 3. 排序取 Top-K
    scored_jobs.sort(key=lambda j: j.match_score, reverse=True)

    return MatchResponse(
        total_candidates=len(candidate_rows),
        recommendations=scored_jobs[:req.limit],
    )
