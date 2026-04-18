"""
智能匹配推荐引擎（升级版 v2）
================================
算法升级：
  - 技能匹配：Jaccard → TF-IDF 余弦相似度（高频技能权重降低，稀缺技能更精准）
  - 经验匹配：基于差值线性衰减（exp_diff * 0.15 惩罚）
  - 新鲜度因子：岗位发布越近分数越高（90天线性衰减）
  - 行业匹配：用户行业 vs 岗位行业精确匹配

MatchScore = 0.30×SkillMatch + 0.15×LocationMatch + 0.15×SalaryMatch
           + 0.10×EducationMatch + 0.10×ExperienceMatch
           + 0.10×IndustryMatch + 0.10×Freshness
"""
from datetime import datetime, timezone
from typing import Dict, List, Optional

import numpy as np
from fastapi import APIRouter
from pydantic import BaseModel
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

from app.db import execute_query

router = APIRouter()


# ─── 模型 ──────────────────────────────────────────

class MatchRequest(BaseModel):
    skills: List[str] = []
    preferred_cities: List[str] = []
    education: Optional[str] = None
    experience: Optional[str] = None
    experience_years: Optional[float] = None   # 用户工作年限（数字）
    salary_min: Optional[float] = None
    salary_max: Optional[float] = None
    industry: Optional[str] = None
    limit: int = 20


class MatchDetail(BaseModel):
    skill_match: float = 0.0
    location_match: float = 0.0
    salary_match: float = 0.0
    education_match: float = 0.0
    experience_match: float = 0.0
    industry_match: float = 0.0
    freshness: float = 0.0


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
    algorithm: str = "tfidf-cosine"


# ─── 教育等级映射 ──────────────────────────────────

EDU_LEVELS = {"初中": 1, "高中": 2, "中专": 2, "大专": 3, "本科": 4, "硕士": 5, "博士": 6}


def _edu_level(edu: str) -> int:
    for key, val in EDU_LEVELS.items():
        if key in (edu or ""):
            return val
    return 3  # 默认大专


def _experience_to_years(exp_str: str) -> float:
    """将岗位经验字符串解析为年数（中位值）"""
    import re
    text = exp_str or ""
    if "不限" in text or "应届" in text or "在校" in text:
        return 0.0
    nums = [int(n) for n in re.findall(r"\d+", text)]
    if not nums:
        return 1.0
    return float(sum(nums[:2]) / len(nums[:2]))


def _freshness_score(publish_date_str: str | None) -> float:
    """岗位新鲜度：90 天内线性衰减，超过 90 天为 0"""
    if not publish_date_str:
        return 0.3  # 无日期信息，中性
    try:
        if hasattr(publish_date_str, "date"):  # datetime 对象
            pub = publish_date_str
        else:
            pub = datetime.strptime(str(publish_date_str)[:10], "%Y-%m-%d")
        now = datetime.now()
        days_ago = max(0, (now - pub).days)
        return max(0.0, 1.0 - days_ago / 90.0)
    except Exception:
        return 0.3


# ─── TF-IDF 技能匹配 ──────────────────────────────

def _tfidf_skill_match(user_skills: List[str], candidates_skills: List[List[str]]) -> List[float]:
    """
    使用 TF-IDF 计算用户技能与候选岗位技能的余弦相似度
    高频技能（Java/Python）权重自动降低，稀缺技能（Flink/K8s）匹配度更高
    """
    if not user_skills or not candidates_skills:
        return [0.0] * len(candidates_skills)

    # 构建语料：每个岗位技能列表作为一个"文档"
    corpus = [" ".join(skills) for skills in candidates_skills]
    user_doc = " ".join(user_skills)

    # 过滤掉空文档
    if not any(corpus):
        return [0.0] * len(candidates_skills)

    try:
        vectorizer = TfidfVectorizer(min_df=1, token_pattern=r"[^\s]+")
        all_docs = corpus + [user_doc]
        tfidf_matrix = vectorizer.fit_transform(all_docs)
        user_vec = tfidf_matrix[-1]
        job_vecs = tfidf_matrix[:-1]
        similarities = cosine_similarity(user_vec, job_vecs).flatten()
        return [max(0.0, float(s)) for s in similarities]
    except Exception:
        # fallback to Jaccard if TF-IDF fails
        user_lower = {s.lower() for s in user_skills}
        results = []
        for skills in candidates_skills:
            job_lower = {s.lower() for s in skills}
            if user_lower and job_lower:
                union = user_lower | job_lower
                inter = user_lower & job_lower
                results.append(len(inter) / len(union))
            else:
                results.append(0.0)
        return results


# ─── 核心匹配逻辑 ──────────────────────────────────

@router.post("", response_model=MatchResponse)
def match_jobs(req: MatchRequest):
    """多因子加权匹配（TF-IDF v2）"""

    # 1. 粗筛：从数据库取候选岗位
    conditions = ["jp.salary_min IS NOT NULL"]
    params: Dict = {}

    if req.preferred_cities:
        city_conds = " OR ".join(f"jp.job_city LIKE :city_{i}" for i in range(len(req.preferred_cities)))
        conditions.append(f"({city_conds})")
        for i, c in enumerate(req.preferred_cities):
            params[f"city_{i}"] = f"%{c}%"

    if req.industry:
        conditions.append("jp.job_classification LIKE :industry")
        params["industry"] = f"%{req.industry}%"

    where_clause = " AND ".join(conditions)

    candidate_rows = execute_query(f"""
        SELECT jp.id, jp.title, jp.company_name, jp.job_city AS city, jp.education_need AS education,
               jp.experience_year AS experience, jp.salary_min, jp.salary_max, jp.salary_raw AS salary_text,
               jp.job_classification AS industry_name, jp.publish_date,
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
        return MatchResponse(total_candidates=0, recommendations=[], algorithm="tfidf-cosine")

    # 2. 准备 TF-IDF 数据
    user_skills_lower = [s.lower() for s in req.skills]
    user_edu_level = _edu_level(req.education or "")
    user_exp_years = req.experience_years if req.experience_years is not None else (
        _experience_to_years(req.experience or "")
    )

    # 构建每个候选的技能列表
    candidates_skill_lists = []
    for row in candidate_rows:
        skills = [s.strip().lower() for s in (row["skill_list"] or "").split(",") if s.strip()]
        candidates_skill_lists.append(skills)

    # 批量计算 TF-IDF 技能相似度（向量化，效率远高于逐条 Jaccard）
    tfidf_scores = _tfidf_skill_match(user_skills_lower, candidates_skill_lists)

    # 3. 精排：逐条计算综合匹配分数
    scored_jobs = []
    for i, row in enumerate(candidate_rows):
        job_skills_lower = candidates_skill_lists[i]

        # 技能匹配（TF-IDF 余弦相似度）
        skill_match = tfidf_scores[i]

        # 城市匹配
        location_match = 0.0
        if req.preferred_cities:
            for pc in req.preferred_cities:
                if pc in (row["city"] or ""):
                    location_match = 1.0
                    break

        # 薪资匹配
        salary_match = 0.5
        if req.salary_min and row["salary_min"]:
            job_mid = (float(row["salary_min"]) + float(row.get("salary_max") or row["salary_min"])) / 2
            user_mid = (req.salary_min + (req.salary_max or req.salary_min * 1.5)) / 2
            diff_ratio = abs(job_mid - user_mid) / max(user_mid, 1)
            salary_match = max(0.0, 1.0 - diff_ratio)

        # 学历匹配
        job_edu_level = _edu_level(row["education"] or "")
        edu_diff = user_edu_level - job_edu_level
        education_match = 1.0 if edu_diff >= 0 else max(0.0, 1.0 + edu_diff * 0.3)

        # 经验匹配（新增：基于年限差值线性衰减）
        job_exp_years = _experience_to_years(row["experience"] or "")
        exp_diff = user_exp_years - job_exp_years
        experience_match = max(0.0, 1.0 - abs(exp_diff) * 0.15)

        # 行业匹配（新增）
        industry_match = 0.3  # 默认低分
        if req.industry and row["industry_name"]:
            if req.industry.lower() in (row["industry_name"] or "").lower():
                industry_match = 1.0
            elif any(kw in (row["industry_name"] or "").lower() for kw in req.industry.lower().split()):
                industry_match = 0.6

        # 新鲜度（新增）
        freshness = _freshness_score(row["publish_date"])

        # 综合评分（权重之和 = 1.0）
        total_score = (
            0.30 * skill_match
            + 0.15 * location_match
            + 0.15 * salary_match
            + 0.10 * education_match
            + 0.10 * experience_match
            + 0.10 * industry_match
            + 0.10 * freshness
        )

        matched_skills = [s for s in req.skills if s.lower() in {sk.lower() for sk in job_skills_lower}]
        missing_skills = [s for s in job_skills_lower if s not in user_skills_lower]

        scored_jobs.append(MatchedJob(
            job_id=row["id"],
            title=row["title"],
            company_name=row["company_name"],
            city=row["city"] or "",
            salary_text=row["salary_text"],
            education=row["education"],
            experience=row["experience"],
            skills=[s.strip() for s in job_skills_lower if s.strip()],
            match_score=round(total_score, 3),
            match_details=MatchDetail(
                skill_match=round(skill_match, 3),
                location_match=round(location_match, 3),
                salary_match=round(salary_match, 3),
                education_match=round(education_match, 3),
                experience_match=round(experience_match, 3),
                industry_match=round(industry_match, 3),
                freshness=round(freshness, 3),
            ),
            advice={
                "matched_skills": matched_skills[:5],
                "missing_skills": missing_skills[:5],
            },
        ))

    # 4. 排序取 Top-K
    scored_jobs.sort(key=lambda j: j.match_score, reverse=True)

    return MatchResponse(
        total_candidates=len(candidate_rows),
        recommendations=scored_jobs[:req.limit],
        algorithm="tfidf-cosine",
    )
