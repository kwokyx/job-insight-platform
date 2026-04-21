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
from app.ml.job_ranker import load_bundle as load_ranker_bundle, score_feature_maps, train_and_save as train_job_ranker

router = APIRouter()


# ─── 模型 ──────────────────────────────────────────

class MatchRequest(BaseModel):
    skills: List[str] = []
    core_skills: List[str] = []
    preferred_cities: List[str] = []
    excluded_keywords: List[str] = []
    preferred_company_sizes: List[str] = []
    preferred_finance_stages: List[str] = []
    target_job_type: Optional[str] = None
    education: Optional[str] = None
    experience: Optional[str] = None
    experience_years: Optional[float] = None   # 用户工作年限（数字）
    salary_min: Optional[float] = None
    salary_max: Optional[float] = None
    industry: Optional[str] = None
    limit: int = 20


class MatchDetail(BaseModel):
    family_match: float = 0.0
    skill_match: float = 0.0
    core_skill_match: float = 0.0
    title_match: float = 0.0
    location_match: float = 0.0
    company_match: float = 0.0
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
    industry_name: Optional[str] = None
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


@router.post("/train-ranker")
def train_ranker(limit: int = 20000):
    result = train_job_ranker(limit=limit)
    return {"message": "job ranker trained", "data": result}


@router.get("/ranker-status")
def ranker_status():
    bundle = load_ranker_bundle()
    return {
        "trained": bundle is not None,
        "sample_count": 0 if bundle is None else bundle.sample_count,
        "feature_names": [] if bundle is None else bundle.feature_names,
        "model_type": None if bundle is None else bundle.model_type,
        "source": None if bundle is None else bundle.source,
    }


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


def _split_keywords(text: Optional[str]) -> List[str]:
    import re
    if not text:
        return []
    return [
        token.strip().lower()
        for token in re.split(r"[,;/|\s\-()（）]+", text)
        if token and token.strip() and len(token.strip()) > 1
    ]


def _text_match_score(text: Optional[str], keywords: List[str]) -> float:
    if not text or not keywords:
        return 0.0
    normalized = text.lower()
    hits = sum(1 for keyword in keywords if keyword in normalized)
    return hits / max(len(keywords), 1)


def _infer_domain_keywords(skills: List[str]) -> List[str]:
    normalized = {skill.strip().lower() for skill in skills if skill and skill.strip()}
    keywords: List[str] = []
    if normalized & {"java", "spring", "spring boot", "mysql", "redis", "docker", "git", "linux"}:
        keywords.extend([
            "java", "spring", "backend", "后端", "开发", "engineer", "software",
            "程序员", "研发", "mysql", "服务端", "系统"
        ])
    if normalized & {"vue", "react", "javascript", "typescript", "css", "html"}:
        keywords.extend(["frontend", "front-end", "前端", "vue", "react", "web", "javascript"])
    if normalized & {"python", "pandas", "numpy", "sql", "tableau", "power bi"}:
        keywords.extend(["data", "analysis", "analyst", "数据", "python", "sql", "bi"])
    return list(dict.fromkeys(keywords))


def _infer_job_family(skills: List[str], target_job_type: Optional[str]) -> str:
    text = " ".join([*(skills or []), target_job_type or ""]).lower()
    if any(token in text for token in ["java", "spring", "backend", "后端", "服务端", "golang", "微服务"]):
        return "backend"
    if any(token in text for token in ["vue", "react", "frontend", "前端", "javascript", "typescript"]):
        return "frontend"
    if any(token in text for token in ["python", "data", "analysis", "analyst", "数据", "sql", "bi"]):
        return "data"
    if any(token in text for token in ["test", "qa", "测试", "自动化测试"]):
        return "qa"
    return "general"


def _family_positive_keywords(family: str) -> List[str]:
    mapping = {
        "backend": ["后端", "开发", "研发", "工程师", "java", "spring", "backend", "software", "服务端", "系统"],
        "frontend": ["前端", "开发", "工程师", "vue", "react", "frontend", "web", "javascript"],
        "data": ["数据", "分析", "data", "analyst", "python", "sql", "bi", "算法"],
        "qa": ["测试", "qa", "test", "质量", "自动化"],
        "general": [],
    }
    return mapping.get(family, [])


def _family_negative_keywords(family: str) -> List[str]:
    common = ["美容", "顾问", "钳工", "普工", "导购", "招商", "销售", "客服", "学徒"]
    if family in {"backend", "frontend", "data", "qa"}:
        return common
    return []


# ─── 核心匹配逻辑 ──────────────────────────────────

@router.post("", response_model=MatchResponse)
def match_jobs(req: MatchRequest):
    """多路召回 + 工业化启发式精排"""

    conditions = ["jp.salary_min IS NOT NULL"]
    params: Dict = {}
    family = _infer_job_family(req.skills, req.target_job_type)

    if req.preferred_cities:
        city_conds = " OR ".join(f"jp.job_city LIKE :city_{i}" for i in range(len(req.preferred_cities)))
        conditions.append(f"({city_conds})")
        for i, city in enumerate(req.preferred_cities):
            params[f"city_{i}"] = f"%{city}%"

    if req.industry:
        conditions.append("jp.job_classification LIKE :industry")
        params["industry"] = f"%{req.industry}%"

    if req.preferred_company_sizes:
        size_conds = " OR ".join(f"jp.company_size LIKE :company_size_{i}" for i in range(len(req.preferred_company_sizes)))
        conditions.append(f"({size_conds})")
        for i, value in enumerate(req.preferred_company_sizes):
            params[f"company_size_{i}"] = f"%{value}%"

    if req.preferred_finance_stages:
        finance_conds = " OR ".join(f"jp.company_finance LIKE :finance_{i}" for i in range(len(req.preferred_finance_stages)))
        conditions.append(f"({finance_conds})")
        for i, value in enumerate(req.preferred_finance_stages):
            params[f"finance_{i}"] = f"%{value}%"

    title_keywords = _split_keywords(req.target_job_type) or _family_positive_keywords(family)[:4]
    if title_keywords:
        title_conds = " OR ".join(
            f"(jp.title LIKE :title_{i} OR COALESCE(jp.description, jp.position_info) LIKE :title_{i})"
            for i in range(len(title_keywords))
        )
        conditions.append(f"({title_conds})")
        for i, keyword in enumerate(title_keywords):
            params[f"title_{i}"] = f"%{keyword}%"

    where_clause = " AND ".join(conditions)
    candidate_rows = execute_query(f"""
        SELECT jp.id, jp.title, jp.company_name, COALESCE(jp.city, jp.job_city) AS city,
               jp.education_need AS education, jp.experience_year AS experience,
               jp.salary_min, jp.salary_max, jp.salary_raw AS salary_text,
               COALESCE(jp.industry_name, jp.job_classification) AS industry_name,
               jp.publish_date, COALESCE(jp.description, jp.position_info) AS job_summary,
               jp.company_size, jp.company_finance, jp.job_labels
        FROM biz_job_posting jp
        WHERE {where_clause}
        ORDER BY jp.publish_date DESC
        LIMIT 500
    """, params)

    if not candidate_rows:
        return MatchResponse(total_candidates=0, recommendations=[], algorithm="industrial-heuristic-ranker")

    user_skills_lower = [s.lower() for s in req.skills]
    core_skills_lower = [s.lower() for s in (req.core_skills or req.skills[:3])]
    domain_keywords = _infer_domain_keywords(req.skills)
    family_positive = _family_positive_keywords(family)
    family_negative = list(dict.fromkeys([*(req.excluded_keywords or []), *_family_negative_keywords(family)]))
    user_edu_level = _edu_level(req.education or "")
    user_exp_years = req.experience_years if req.experience_years is not None else _experience_to_years(req.experience or "")

    import json
    candidates_skill_lists = []
    for row in candidate_rows:
        try:
            skills = json.loads(row.get("job_labels") or "[]")
            if not isinstance(skills, list):
                skills = []
        except Exception:
            skills = []
        skills = [s.strip().lower() for s in skills if isinstance(s, str) and s.strip()]
        candidates_skill_lists.append(skills)

    tfidf_scores = _tfidf_skill_match(user_skills_lower, candidates_skill_lists)
    scored_jobs = []
    feature_maps: List[dict] = []
    fallback_scores: List[float] = []
    staged_rows: List[dict] = []
    for i, row in enumerate(candidate_rows):
        job_skills_lower = candidates_skill_lists[i]
        full_text = f"{row['title'] or ''} {row.get('job_summary') or ''} {row.get('industry_name') or ''} {' '.join(job_skills_lower)}"

        family_match = _text_match_score(full_text, family_positive)
        skill_match = tfidf_scores[i]
        core_skill_hits = len([skill for skill in core_skills_lower if skill in set(job_skills_lower)])
        core_skill_match = core_skill_hits / max(len(core_skills_lower), 1) if core_skills_lower else 0.0
        title_match = _text_match_score(full_text, title_keywords)
        domain_match = _text_match_score(full_text, domain_keywords)

        location_match = 0.0
        if req.preferred_cities:
            for city in req.preferred_cities:
                if city in (row["city"] or ""):
                    location_match = 1.0
                    break

        company_match = 0.0
        if req.preferred_company_sizes and any(size in (row.get("company_size") or "") for size in req.preferred_company_sizes):
            company_match += 0.6
        if req.preferred_finance_stages and any(stage in (row.get("company_finance") or "") for stage in req.preferred_finance_stages):
            company_match += 0.4
        company_match = min(company_match, 1.0)

        salary_match = 0.5
        if req.salary_min and row["salary_min"]:
            job_mid = (float(row["salary_min"]) + float(row.get("salary_max") or row["salary_min"])) / 2
            user_mid = (req.salary_min + (req.salary_max or req.salary_min * 1.5)) / 2
            diff_ratio = abs(job_mid - user_mid) / max(user_mid, 1)
            salary_match = max(0.0, 1.0 - diff_ratio)

        job_edu_level = _edu_level(row["education"] or "")
        edu_diff = user_edu_level - job_edu_level
        education_match = 1.0 if edu_diff >= 0 else max(0.0, 1.0 + edu_diff * 0.3)

        job_exp_years = _experience_to_years(row["experience"] or "")
        exp_diff = user_exp_years - job_exp_years
        experience_match = max(0.0, 1.0 - abs(exp_diff) * 0.15)

        industry_match = 0.3
        if req.industry and row["industry_name"]:
            if req.industry.lower() in (row["industry_name"] or "").lower():
                industry_match = 1.0
            elif any(kw in (row["industry_name"] or "").lower() for kw in req.industry.lower().split()):
                industry_match = 0.6

        freshness = _freshness_score(row["publish_date"])

        if family_negative and any(keyword.lower() in full_text.lower() for keyword in family_negative if keyword):
            continue
        if core_skills_lower and core_skill_match == 0.0:
            continue
        if title_keywords and title_match < 0.2 and skill_match < 0.1 and family_match < 0.2:
            continue
        if user_skills_lower and domain_keywords and domain_match == 0.0 and skill_match < 0.12 and family_match < 0.2:
            continue

        fallback_score = (
            0.24 * family_match
            + 0.24 * core_skill_match
            + 0.18 * skill_match
            + 0.14 * title_match
            + 0.08 * location_match
            + 0.04 * company_match
            + 0.03 * salary_match
            + 0.02 * education_match
            + 0.02 * experience_match
            + 0.02 * industry_match
            + 0.01 * freshness
        )

        matched_skills = [s for s in req.skills if s.lower() in {sk.lower() for sk in job_skills_lower}]
        matched_core_skills = [s for s in (req.core_skills or req.skills[:3]) if s.lower() in {sk.lower() for sk in job_skills_lower}]
        missing_skills = [s for s in job_skills_lower if s not in user_skills_lower]
        feature_maps.append({
            "family_match": family_match,
            "skill_match": skill_match,
            "core_skill_match": core_skill_match,
            "title_match": title_match,
            "domain_match": domain_match,
            "location_match": location_match,
            "company_match": company_match,
            "salary_match": salary_match,
            "education_match": education_match,
            "experience_match": experience_match,
            "industry_match": industry_match,
            "freshness": freshness,
            "core_skill_hits": float(core_skill_hits),
            "skill_hits": float(len(matched_skills)),
        })
        fallback_scores.append(fallback_score)
        staged_rows.append({
            "row": row,
            "job_skills_lower": job_skills_lower,
            "matched_skills": matched_skills,
            "matched_core_skills": matched_core_skills,
            "missing_skills": missing_skills,
            "details": {
                "family_match": family_match,
                "skill_match": skill_match,
                "core_skill_match": core_skill_match,
                "title_match": title_match,
                "location_match": location_match,
                "company_match": company_match,
                "salary_match": salary_match,
                "education_match": education_match,
                "experience_match": experience_match,
                "industry_match": industry_match,
                "freshness": freshness,
            },
        })

    ranked_scores, algorithm_name = score_feature_maps(feature_maps, fallback_scores)
    for staged, score in zip(staged_rows, ranked_scores):
        row = staged["row"]
        job_skills_lower = staged["job_skills_lower"]
        details = staged["details"]
        scored_jobs.append(MatchedJob(
            job_id=row["id"],
            title=row["title"],
            company_name=row["company_name"],
            city=row["city"] or "",
            industry_name=row.get("industry_name"),
            salary_text=row["salary_text"],
            education=row["education"],
            experience=row["experience"],
            skills=[s.strip() for s in job_skills_lower if s.strip()],
            match_score=round(score, 3),
            match_details=MatchDetail(
                family_match=round(details["family_match"], 3),
                skill_match=round(details["skill_match"], 3),
                core_skill_match=round(details["core_skill_match"], 3),
                title_match=round(details["title_match"], 3),
                location_match=round(details["location_match"], 3),
                company_match=round(details["company_match"], 3),
                salary_match=round(details["salary_match"], 3),
                education_match=round(details["education_match"], 3),
                experience_match=round(details["experience_match"], 3),
                industry_match=round(details["industry_match"], 3),
                freshness=round(details["freshness"], 3),
            ),
            advice={
                "job_family": family,
                "matched_core_skills": staged["matched_core_skills"][:5],
                "matched_skills": staged["matched_skills"][:5],
                "missing_skills": staged["missing_skills"][:5],
            },
        ))

    scored_jobs.sort(key=lambda j: j.match_score, reverse=True)
    return MatchResponse(
        total_candidates=len(candidate_rows),
        recommendations=scored_jobs[:req.limit],
        algorithm=algorithm_name,
    )
