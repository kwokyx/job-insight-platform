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
from collections import Counter
from typing import Any, Dict, List, Optional, Set, Tuple

import numpy as np
from fastapi import APIRouter
from pydantic import BaseModel, Field
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

from app.db import execute_query
from app.ml.job_ranker import load_bundle as load_ranker_bundle, score_feature_maps, train_and_save as train_job_ranker
from app.skill_normalizer import normalize_job_labels, normalize_skill_tokens

router = APIRouter()


# ─── 模型 ──────────────────────────────────────────

class MatchRequest(BaseModel):
    user_id: Optional[int] = None
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
    ab_group: Optional[str] = None
    strategy_version: Optional[str] = None
    limit: int = 20


class MatchDetail(BaseModel):
    family_match: float = 0.0
    skill_match: float = 0.0
    core_skill_match: float = 0.0
    title_match: float = 0.0
    domain_match: float = 0.0
    intent_match: float = 0.0
    seniority_match: float = 0.0
    recall_strength: float = 0.0
    favorite_match: float = 0.0
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
    confidence: float = 0.0
    fit_label: str = ""
    why_matched: List[str] = Field(default_factory=list)
    risk_flags: List[str] = Field(default_factory=list)
    next_actions: List[str] = Field(default_factory=list)
    advice: dict = Field(default_factory=dict)


class MatchResponse(BaseModel):
    total_candidates: int
    recommendations: List[MatchedJob]
    algorithm: str = "tfidf-cosine"
    profile: dict = Field(default_factory=dict)
    summary: dict = Field(default_factory=dict)
    gap_insights: dict = Field(default_factory=dict)
    recall_diagnostics: dict = Field(default_factory=dict)
    ranking_diagnostics: dict = Field(default_factory=dict)
    experiment: dict = Field(default_factory=dict)


SCORE_WEIGHTS = {
    "family_match": 0.24,
    "core_skill_match": 0.18,
    "skill_match": 0.16,
    "title_match": 0.10,
    "domain_match": 0.08,
    "intent_match": 0.07,
    "seniority_match": 0.04,
    "recall_strength": 0.03,
    "location_match": 0.08,
    "company_match": 0.04,
    "salary_match": 0.03,
    "education_match": 0.02,
    "experience_match": 0.02,
    "industry_match": 0.02,
    "freshness": 0.01,
}


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


def _normalize_skill_tokens(values: List[str]) -> List[str]:
    return [token.lower() for token in normalize_skill_tokens(values)]


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
            "java", "spring", "spring boot", "mysql", "redis", "后端", "服务端", "微服务", "分布式"
        ])
    if normalized & {"vue", "react", "javascript", "typescript", "css", "html"}:
        keywords.extend(["vue", "react", "javascript", "typescript", "前端", "web前端"])
    if normalized & {"python", "pandas", "numpy", "sql", "tableau", "power bi"}:
        keywords.extend(["python", "sql", "数据分析", "数据开发", "bi", "数仓"])
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


def _family_recall_keywords(family: str) -> List[str]:
    mapping = {
        "backend": ["java", "spring", "spring boot", "后端", "服务端", "微服务", "mysql", "redis", "golang"],
        "frontend": ["前端", "vue", "react", "javascript", "typescript", "web前端"],
        "data": ["python", "sql", "数据分析", "数据开发", "bi", "数仓", "算法"],
        "qa": ["测试", "自动化测试", "qa", "test", "selenium", "jmeter"],
        "general": [],
    }
    return mapping.get(family, [])


def _family_negative_keywords(family: str) -> List[str]:
    common = ["美容", "顾问", "钳工", "普工", "导购", "招商", "销售", "客服", "学徒"]
    if family in {"backend", "frontend", "data", "qa"}:
        return common
    return []


def _language_conflict_keywords(user_skills: List[str], family: str) -> List[str]:
    normalized = {skill.strip().lower() for skill in user_skills if skill and skill.strip()}
    if family != "backend":
        return []

    major_languages = {
        "python": ["python", "django", "flask"],
        "php": ["php"],
        "golang": ["golang", "go"],
        "c++": ["c++", "cpp"],
        "c#": ["c#", ".net", "dotnet"],
        "embedded": ["嵌入式", "单片机"],
    }
    conflicts: List[str] = []
    for tech_keywords in major_languages.values():
        if normalized.intersection(tech_keywords):
            continue
        conflicts.extend(tech_keywords)
    return conflicts


def _infer_user_seniority(experience_years: Optional[float], experience_text: Optional[str], target_job_type: Optional[str]) -> int:
    years = experience_years if experience_years is not None else _experience_to_years(experience_text or "")
    text = (target_job_type or "").lower()
    if any(token in text for token in ["lead", "leader", "manager", "总监", "负责人", "架构师", "资深"]):
        years = max(years, 5.0)
    if years >= 8:
        return 4
    if years >= 5:
        return 3
    if years >= 2:
        return 2
    return 1


def _job_seniority_level(title: Optional[str], experience_text: Optional[str]) -> int:
    text = f"{title or ''} {experience_text or ''}".lower()
    if any(token in text for token in ["总监", "leader", "负责人", "manager", "架构师", "principal"]):
        return 4
    if any(token in text for token in ["高级", "资深", "senior", "sr"]):
        return 3
    years = _experience_to_years(experience_text or "")
    if years >= 5:
        return 3
    if years >= 2:
        return 2
    return 1


def _seniority_match_score(user_level: int, job_level: int) -> float:
    return max(0.0, 1.0 - abs(user_level - job_level) * 0.35)


def _build_intent_keywords(
    req: MatchRequest,
    family: str,
    title_keywords: List[str],
    domain_keywords: List[str],
    favorite_profile: dict,
) -> List[str]:
    tokens = [
        *(title_keywords or []),
        *(domain_keywords or []),
        *[skill.lower() for skill in req.skills[:6]],
        *[skill.lower() for skill in req.core_skills[:4]],
        *[skill.lower() for skill in favorite_profile.get("skills", [])[:6]],
        *[keyword.lower() for keyword in favorite_profile.get("title_keywords", [])[:6]],
        *_family_recall_keywords(family)[:6],
    ]
    return list(dict.fromkeys([token for token in tokens if token and len(token) > 1]))[:18]


def _intent_match_score(full_text: str, job_skills_lower: List[str], intent_keywords: List[str]) -> float:
    if not intent_keywords:
        return 0.0
    text = full_text.lower()
    job_skill_set = set(job_skills_lower)
    hits = 0.0
    for keyword in intent_keywords:
        if keyword in text:
            hits += 1.0
        elif keyword in job_skill_set:
            hits += 0.8
    return min(1.0, hits / max(len(intent_keywords) * 0.55, 1))


def _recall_strength_score(hit_routes: List[str]) -> float:
    if not hit_routes:
        return 0.0
    core_routes = {"title", "skill", "city", "preference"}
    core_hits = len(set(hit_routes) & core_routes)
    return min(1.0, 0.2 + core_hits * 0.25 + (0.1 if "fresh" in hit_routes else 0.0))


def _job_diversity_bucket(job: MatchedJob) -> str:
    title_tokens = _split_keywords(job.title)[:3]
    main_token = title_tokens[0] if title_tokens else (job.title or "").lower()
    city = (job.city or "").lower()
    return f"{main_token}|{city}"


def _diversify_jobs(jobs: List[MatchedJob], limit: int) -> List[MatchedJob]:
    selected: List[MatchedJob] = []
    bucket_counts: Dict[str, int] = {}
    company_counts: Dict[str, int] = {}
    for job in sorted(jobs, key=lambda item: item.match_score, reverse=True):
        bucket = _job_diversity_bucket(job)
        company_key = (job.company_name or "").strip().lower()
        bucket_penalty = 0.05 * bucket_counts.get(bucket, 0)
        company_penalty = 0.04 * company_counts.get(company_key, 0)
        adjusted_score = round(job.match_score - bucket_penalty - company_penalty, 3)
        if adjusted_score < 0.08:
            continue
        job.match_score = adjusted_score
        selected.append(job)
        bucket_counts[bucket] = bucket_counts.get(bucket, 0) + 1
        company_counts[company_key] = company_counts.get(company_key, 0) + 1
        if len(selected) >= limit * 2:
            break
    selected.sort(key=lambda item: item.match_score, reverse=True)
    return selected[:limit]


def _match_confidence(details: dict, hit_routes: List[str], matched_core_skills: List[str]) -> float:
    confidence = (
        0.26 * details["recall_strength"]
        + 0.22 * max(details["core_skill_match"], details["skill_match"])
        + 0.12 * details["title_match"]
        + 0.10 * details["family_match"]
        + 0.08 * details["intent_match"]
        + 0.08 * details["freshness"]
        + 0.08 * min(1.0, len(set(hit_routes)) / 3.0)
        + 0.06 * (1.0 if matched_core_skills else 0.0)
    )
    return round(min(0.96, max(0.18, confidence)), 3)


def _calibrate_rank_score(score: float, confidence: float, details: dict) -> float:
    calibrated = score
    if details["core_skill_match"] == 0.0 and details["skill_match"] < 0.4:
        calibrated -= 0.12
    elif details["core_skill_match"] < 0.34 and details["skill_match"] < 0.24:
        calibrated -= 0.07

    if details["title_match"] < 0.2 and details["family_match"] < 0.55:
        calibrated -= 0.04
    if details["seniority_match"] < 0.55 or details["experience_match"] < 0.45:
        calibrated -= 0.04
    if confidence < 0.55:
        calibrated -= 0.03

    if details["core_skill_match"] >= 0.5 and details["skill_match"] >= 0.28:
        calibrated += 0.04
    if details["title_match"] >= 0.35 and details["family_match"] >= 0.5:
        calibrated += 0.02
    if confidence >= 0.72:
        calibrated += 0.02

    return round(min(0.96, max(0.05, calibrated)), 3)


def _fit_label(score: float, confidence: float, details: dict) -> str:
    strong_signal = (
        confidence >= 0.68
        and (details["core_skill_match"] >= 0.5 or details["skill_match"] >= 0.42)
        and details["seniority_match"] >= 0.55
    )
    priority_signal = (
        confidence >= 0.52
        and (
            details["core_skill_match"] >= 0.34
            or details["skill_match"] >= 0.22
            or details["title_match"] >= 0.3
        )
    )
    if score >= 0.78 and strong_signal:
        return "强匹配"
    if score >= 0.6 and priority_signal:
        return "优先投递"
    if score >= 0.42 and confidence >= 0.35:
        return "可尝试"
    return "谨慎投递"


def _score_breakdown(details: dict) -> List[dict]:
    items = []
    for key, weight in SCORE_WEIGHTS.items():
        contribution = round(details.get(key, 0.0) * weight, 4)
        if contribution <= 0:
            continue
        items.append({
            "dimension": key,
            "value": round(details.get(key, 0.0), 3),
            "weight": weight,
            "contribution": contribution,
        })
    items.sort(key=lambda item: item["contribution"], reverse=True)
    return items[:6]


def _build_match_reasons(
    row: dict,
    matched_core_skills: List[str],
    matched_skills: List[str],
    details: dict,
    hit_routes: List[str],
) -> List[str]:
    reasons: List[str] = []
    if matched_core_skills:
        reasons.append("命中了你的核心技能：" + "、".join(matched_core_skills[:3]))
    elif matched_skills:
        reasons.append("与现有技能有直接重合：" + "、".join(matched_skills[:4]))
    if details["title_match"] >= 0.45 or details["family_match"] >= 0.45:
        reasons.append(f"岗位标题与目标方向接近：{row.get('title') or '目标岗位'}")
    if details["location_match"] >= 1.0 and row.get("city"):
        reasons.append(f"地点满足偏好：{row['city']}")
    if details["salary_match"] >= 0.75 and row.get("salary_text"):
        reasons.append(f"薪资区间与你的预期更接近：{row['salary_text']}")
    if "preference" in hit_routes:
        reasons.append("命中了你的公司规模或融资偏好。")
    if details["freshness"] >= 0.75:
        reasons.append("岗位发布时间较近，投递窗口更友好。")
    return reasons[:4]


def _build_risk_flags(
    missing_skills: List[str],
    details: dict,
    req: MatchRequest,
    score: float,
    confidence: float,
) -> List[str]:
    risks: List[str] = []
    if req.core_skills and details["core_skill_match"] < 0.34:
        risks.append("核心技能重合偏低，面试通过率会受影响。")
    if score >= 0.7 and confidence < 0.58:
        risks.append("当前分数更多来自召回和向量相似，不是核心技能的强支撑。")
    if missing_skills:
        risks.append("岗位仍缺少这些高频技能：" + "、".join(missing_skills[:4]))
    if req.preferred_cities and details["location_match"] == 0.0:
        risks.append("城市不在当前优先范围内。")
    if req.industry and details["industry_match"] < 0.6:
        risks.append("行业贴合度一般，适合作为扩圈岗位而非主投岗位。")
    if details["experience_match"] < 0.45 or details["seniority_match"] < 0.45:
        risks.append("岗位层级与你当前年限存在偏差。")
    return risks[:4]


def _build_next_actions(
    matched_core_skills: List[str],
    missing_skills: List[str],
    details: dict,
    req: MatchRequest,
) -> List[str]:
    actions: List[str] = []
    if matched_core_skills:
        actions.append("投递时把这些技能前置到简历摘要：" + "、".join(matched_core_skills[:3]))
    if missing_skills:
        actions.append("优先补一个能证明 " + "、".join(missing_skills[:2]) + " 的项目或案例。")
    if details["title_match"] < 0.35 and req.target_job_type:
        actions.append("简历标题和求职摘要再向目标岗位命名靠拢，减少方向噪声。")
    if details["experience_match"] < 0.5:
        actions.append("准备解释你的层级跨度，重点讲复杂度、范围和结果。")
    if details["salary_match"] < 0.45 and (req.salary_min or req.salary_max):
        actions.append("把这类岗位作为扩圈岗位，不建议拿它校准核心薪资预期。")
    if not actions:
        actions.append("直接进入投递，并按岗位要求微调关键词顺序和项目证据。")
    return actions[:4]


def _response_profile(req: MatchRequest, family: str, favorite_profile: dict) -> dict:
    return {
        "targetJobType": req.target_job_type,
        "jobFamily": family,
        "skillCount": len(req.skills),
        "coreSkillCount": len(req.core_skills or req.skills[:3]),
        "preferredCities": req.preferred_cities,
        "industry": req.industry,
        "experienceYears": req.experience_years if req.experience_years is not None else _experience_to_years(req.experience or ""),
        "hasFavoriteSignal": bool(favorite_profile.get("job_ids")),
    }


def _response_summary(jobs: List[MatchedJob], total_candidates: int) -> dict:
    if not jobs:
        return {
            "headline": "当前没有形成稳定的推荐岗位池，建议先补齐画像和技能再重新召回。",
            "topMatchScore": 0.0,
            "averageMatchScore": 0.0,
            "readyCount": 0,
            "stretchCount": 0,
            "topCities": [],
            "topIndustries": [],
        }

    city_counter = Counter(job.city for job in jobs if job.city)
    industry_counter = Counter(job.industry_name for job in jobs if job.industry_name)
    strong_count = sum(1 for job in jobs if job.fit_label == "强匹配")
    ready_count = sum(1 for job in jobs if job.fit_label in {"强匹配", "优先投递"})
    stretch_count = sum(1 for job in jobs if job.fit_label == "可尝试")
    avg_score = round(sum(job.match_score for job in jobs) / max(len(jobs), 1), 3)
    top_score = round(max(job.match_score for job in jobs), 3)
    if strong_count >= 2:
        headline = "已经形成高质量主投岗位池，建议先吃透强匹配岗位，再处理扩圈机会。"
    elif ready_count >= 3:
        headline = "已经形成可投递岗位池，适合按优先级分批投递，同时继续补强核心短板。"
    else:
        headline = "当前岗位池可作扩圈参考，但主投岗位还不够稳，建议继续补强后再集中投递。"
    return {
        "headline": headline,
        "topMatchScore": top_score,
        "averageMatchScore": avg_score,
        "strongCount": strong_count,
        "readyCount": ready_count,
        "stretchCount": stretch_count,
        "coverageRate": round(len(jobs) / max(total_candidates, 1), 3),
        "topCities": [item[0] for item in city_counter.most_common(3)],
        "topIndustries": [item[0] for item in industry_counter.most_common(3)],
    }


def _gap_insights(jobs: List[MatchedJob]) -> dict:
    missing_counter: Counter = Counter()
    matched_counter: Counter = Counter()
    for job in jobs:
        advice = job.advice or {}
        missing_counter.update(advice.get("missing_skills") or [])
        matched_counter.update(advice.get("matched_skills") or [])

    return {
        "topMissingSkills": [skill for skill, _ in missing_counter.most_common(6)],
        "topMatchedSkills": [skill for skill, _ in matched_counter.most_common(6)],
        "coreGapCount": sum(1 for _, count in missing_counter.most_common(6) if count >= 2),
    }


def _load_favorite_profile(user_id: Optional[int]) -> dict:
    if not user_id:
        return {"skills": [], "title_keywords": [], "job_ids": []}
    rows = execute_query(
        """
        SELECT
            f.job_id,
            jp.title,
            jp.job_labels,
            COALESCE(jp.description, jp.position_info) AS job_summary
        FROM biz_job_favorite f
        JOIN biz_job_posting jp ON jp.id = f.job_id
        WHERE f.user_id = :user_id
        ORDER BY f.created_at DESC, f.id DESC
        LIMIT 10
        """,
        {"user_id": user_id},
    )
    if not rows:
        return {"skills": [], "title_keywords": [], "job_ids": []}

    import json
    skill_tokens: List[str] = []
    title_tokens: List[str] = []
    for row in rows:
        try:
            labels = json.loads(row.get("job_labels") or "[]")
            if not isinstance(labels, list):
                labels = []
        except Exception:
            labels = []
        raw_skills = [value for value in labels if isinstance(value, str) and value.strip()]
        skill_tokens.extend([token.lower() for token in normalize_skill_tokens(raw_skills)])
        title_tokens.extend(_split_keywords(row.get("title") or ""))
        title_tokens.extend(_split_keywords(row.get("job_summary") or "")[:8])

    stopwords = {
        "工程师", "开发", "后端", "前端", "系统", "软件", "岗位", "负责", "以及", "相关", "年以上",
        "java开发", "python开发", "任职", "要求", "经验", "能力", "熟悉", "掌握", "进行", "参与"
    }
    skill_rank = list(dict.fromkeys([token for token in skill_tokens if len(token) > 1 and token not in stopwords]))
    title_rank = list(dict.fromkeys([token for token in title_tokens if len(token) > 1 and token not in stopwords]))
    return {
        "skills": skill_rank[:8],
        "title_keywords": title_rank[:8],
        "job_ids": [int(row["job_id"]) for row in rows if row.get("job_id") is not None],
    }


def _base_job_select_sql(where_clause: str, limit: int) -> str:
    return f"""
        SELECT jp.id, jp.title, jp.company_name, COALESCE(jp.city, jp.job_city) AS city,
               jp.education_need AS education, jp.experience_year AS experience,
               jp.salary_min, jp.salary_max, jp.salary_raw AS salary_text,
               COALESCE(jp.industry_name, jp.job_classification) AS industry_name,
               jp.publish_date, COALESCE(jp.description, jp.position_info) AS job_summary,
               jp.company_size, jp.company_finance, jp.job_labels
        FROM biz_job_posting jp
        WHERE {where_clause}
        ORDER BY jp.publish_date DESC, jp.id DESC
        LIMIT {limit}
    """


def _like_any_clauses(fields: List[str], values: List[str], params: Dict, prefix: str) -> str:
    clauses: List[str] = []
    for index, value in enumerate(values):
        param_name = f"{prefix}_{index}"
        params[param_name] = f"%{value}%"
        field_clause = " OR ".join(f"{field} LIKE :{param_name}" for field in fields)
        clauses.append(f"({field_clause})")
    return " OR ".join(clauses)


def _collect_preference_filters(req: MatchRequest, params: Dict, prefix: str) -> List[str]:
    filters: List[str] = []
    if req.industry:
        param_name = f"{prefix}_industry"
        params[param_name] = f"%{req.industry}%"
        filters.append(f"COALESCE(jp.industry_name, jp.job_classification) LIKE :{param_name}")

    if req.preferred_company_sizes:
        size_clause = _like_any_clauses(["jp.company_size"], req.preferred_company_sizes, params, f"{prefix}_size")
        if size_clause:
            filters.append(f"({size_clause})")

    if req.preferred_finance_stages:
        finance_clause = _like_any_clauses(["jp.company_finance"], req.preferred_finance_stages, params, f"{prefix}_finance")
        if finance_clause:
            filters.append(f"({finance_clause})")

    return filters


def _recall_route_quotas(req: MatchRequest) -> Dict[str, int]:
    base = {
        "title": 220,
        "skill": 280,
        "city": 180,
        "preference": 180,
        "fresh": 180,
        "collaborative": 200,
        "graph": 160,
        "vector": 180,
        "hot": 120,
        "longtail": 110,
    }
    group = (req.ab_group or "control").lower()
    if group == "exp_recall_heavy":
        base["collaborative"] += 80
        base["graph"] += 60
        base["vector"] += 60
        base["hot"] += 40
    elif group == "exp_fresh_first":
        base["fresh"] += 100
        base["hot"] += 80
        base["longtail"] = max(80, base["longtail"] - 20)
    return base


def _expand_graph_keywords(base_skills: List[str], family: str) -> List[str]:
    if not base_skills:
        return []
    rows = execute_query(
        """
        SELECT job_labels
        FROM biz_job_posting
        WHERE job_labels IS NOT NULL
          AND salary_min IS NOT NULL
        ORDER BY publish_date DESC, id DESC
        LIMIT 1200
        """
    )
    seed_set = {skill.lower() for skill in normalize_skill_tokens(base_skills)}
    if not seed_set:
        return []
    counter: Counter = Counter()
    for row in rows:
        labels = normalize_job_labels(row.get("job_labels"))
        normalized = [token.lower() for token in normalize_skill_tokens(labels)]
        if not normalized:
            continue
        if not seed_set.intersection(normalized):
            continue
        for token in normalized:
            if token in seed_set:
                continue
            counter[token] += 1
    family_guard = set(_family_recall_keywords(family))
    expanded = [token for token, _ in counter.most_common(14)]
    if family_guard:
        expanded = [token for token in expanded if token in family_guard or len(token) > 2]
    return expanded[:10]


def _ab_experiment_meta(req: MatchRequest) -> dict:
    group = (req.ab_group or "control").lower()
    strategy_version = req.strategy_version or "student_recommend_v3"
    return {
        "group": group,
        "strategyVersion": strategy_version,
    }


def _multi_recall_candidate_rows(
    req: MatchRequest,
    family: str,
    title_keywords: List[str],
    domain_keywords: List[str],
) -> Tuple[List[dict], Dict[int, List[str]], Dict[str, Any]]:
    route_hits: Dict[int, List[str]] = {}
    dedup_rows: Dict[int, dict] = {}
    skill_keywords = list(dict.fromkeys([*(req.core_skills or []), *(req.skills or [])]))[:8]
    family_keywords = list(dict.fromkeys([*title_keywords, *_family_recall_keywords(family), *domain_keywords]))
    quotas = _recall_route_quotas(req)
    favorite_profile = _load_favorite_profile(req.user_id)
    collaborative_keywords = list(dict.fromkeys([
        *favorite_profile.get("skills", [])[:8],
        *favorite_profile.get("title_keywords", [])[:8],
    ]))
    graph_keywords = _expand_graph_keywords(skill_keywords, family)

    route_specs: List[Tuple[str, List[str], Dict, int]] = []

    if family_keywords:
        params: Dict = {}
        title_clause = _like_any_clauses(
            ["jp.title", "COALESCE(jp.description, jp.position_info)"],
            family_keywords[:6],
            params,
            "title_route",
        )
        route_specs.append(("title", ["jp.salary_min IS NOT NULL", f"({title_clause})"], params, quotas["title"]))

    if skill_keywords:
        params = {}
        skill_clause = _like_any_clauses(
            ["jp.job_labels", "COALESCE(jp.description, jp.position_info)", "jp.title"],
            skill_keywords,
            params,
            "skill_route",
        )
        route_specs.append(("skill", ["jp.salary_min IS NOT NULL", f"({skill_clause})"], params, quotas["skill"]))

    if req.preferred_cities:
        params = {}
        city_clause = _like_any_clauses(["COALESCE(jp.city, jp.job_city)"], req.preferred_cities, params, "city_route")
        filters = ["jp.salary_min IS NOT NULL", f"({city_clause})"]
        if family_keywords:
            family_clause = _like_any_clauses(
                ["jp.title", "COALESCE(jp.description, jp.position_info)"],
                family_keywords[:4],
                params,
                "city_family",
            )
            filters.append(f"({family_clause})")
        route_specs.append(("city", filters, params, quotas["city"]))

    preference_params: Dict = {}
    preference_filters = _collect_preference_filters(req, preference_params, "pref_route")
    if preference_filters:
        filters = ["jp.salary_min IS NOT NULL", *preference_filters]
        if family_keywords:
            family_clause = _like_any_clauses(
                ["jp.title", "COALESCE(jp.description, jp.position_info)", "jp.job_labels"],
                family_keywords[:4],
                preference_params,
                "pref_family",
            )
            filters.append(f"({family_clause})")
        route_specs.append(("preference", filters, preference_params, quotas["preference"]))

    fresh_params: Dict = {}
    fresh_filters = ["jp.salary_min IS NOT NULL"]
    if family_keywords:
        fresh_clause = _like_any_clauses(
            ["jp.title", "COALESCE(jp.description, jp.position_info)", "jp.job_labels"],
            family_keywords[:5],
            fresh_params,
            "fresh_route",
        )
        fresh_filters.append(f"({fresh_clause})")
    route_specs.append(("fresh", fresh_filters, fresh_params, quotas["fresh"]))

    if collaborative_keywords:
        collab_params: Dict = {}
        collab_clause = _like_any_clauses(
            ["jp.title", "jp.job_labels", "COALESCE(jp.description, jp.position_info)"],
            collaborative_keywords[:8],
            collab_params,
            "collab_route",
        )
        route_specs.append(("collaborative", ["jp.salary_min IS NOT NULL", f"({collab_clause})"], collab_params, quotas["collaborative"]))

    if graph_keywords:
        graph_params: Dict = {}
        graph_clause = _like_any_clauses(
            ["jp.job_labels", "COALESCE(jp.description, jp.position_info)"],
            graph_keywords[:8],
            graph_params,
            "graph_route",
        )
        route_specs.append(("graph", ["jp.salary_min IS NOT NULL", f"({graph_clause})"], graph_params, quotas["graph"]))

    if family_keywords or skill_keywords:
        vector_params: Dict = {}
        vector_tokens = list(dict.fromkeys([*family_keywords[:6], *skill_keywords[:6], *graph_keywords[:4]]))[:10]
        if vector_tokens:
            vector_clause = _like_any_clauses(
                ["jp.title", "jp.job_labels", "COALESCE(jp.description, jp.position_info)"],
                vector_tokens,
                vector_params,
                "vector_route",
            )
            route_specs.append(("vector", ["jp.salary_min IS NOT NULL", f"({vector_clause})"], vector_params, quotas["vector"]))

    hot_params: Dict = {}
    hot_filters = ["jp.salary_min IS NOT NULL"]
    if req.preferred_cities:
        hot_city_clause = _like_any_clauses(["COALESCE(jp.city, jp.job_city)"], req.preferred_cities[:3], hot_params, "hot_city")
        hot_filters.append(f"({hot_city_clause})")
    route_specs.append(("hot", hot_filters, hot_params, quotas["hot"]))

    longtail_params: Dict = {}
    longtail_filters = ["jp.salary_min IS NOT NULL", "jp.publish_date <= DATE_SUB(CURRENT_DATE, INTERVAL 25 DAY)"]
    if family_keywords:
        longtail_clause = _like_any_clauses(
            ["jp.title", "jp.job_labels", "COALESCE(jp.description, jp.position_info)"],
            family_keywords[:5],
            longtail_params,
            "longtail_route",
        )
        longtail_filters.append(f"({longtail_clause})")
    route_specs.append(("longtail", longtail_filters, longtail_params, quotas["longtail"]))

    route_raw_counts: Dict[str, int] = {}

    for route_name, filters, params, limit in route_specs:
        where_clause = " AND ".join(filters)
        rows = execute_query(_base_job_select_sql(where_clause, limit), params)
        route_raw_counts[route_name] = len(rows)
        for row in rows:
            job_id = row["id"]
            dedup_rows.setdefault(job_id, row)
            route_hits.setdefault(job_id, [])
            if route_name not in route_hits[job_id]:
                route_hits[job_id].append(route_name)

    route_unique_hits: Dict[str, int] = {}
    for routes in route_hits.values():
        for route_name in routes:
            route_unique_hits[route_name] = route_unique_hits.get(route_name, 0) + 1

    diagnostics = {
        "routeRawCounts": route_raw_counts,
        "routeUniqueHits": route_unique_hits,
        "routeCount": len(route_specs),
        "graphKeywords": graph_keywords[:8],
        "collaborativeKeywords": collaborative_keywords[:8],
    }
    return list(dedup_rows.values()), route_hits, diagnostics


# ─── 核心匹配逻辑 ──────────────────────────────────

@router.post("", response_model=MatchResponse)
def match_jobs(req: MatchRequest):
    """多路召回 + 工业化启发式精排"""
    family = _infer_job_family(req.skills, req.target_job_type)
    title_keywords = _split_keywords(req.target_job_type) or _family_recall_keywords(family)[:4]
    domain_keywords = _infer_domain_keywords(req.skills)
    favorite_profile = _load_favorite_profile(req.user_id)
    intent_keywords = _build_intent_keywords(req, family, title_keywords, domain_keywords, favorite_profile)
    experiment_meta = _ab_experiment_meta(req)
    candidate_rows, route_hits, recall_diagnostics = _multi_recall_candidate_rows(req, family, title_keywords, domain_keywords)

    if not candidate_rows:
        return MatchResponse(
            total_candidates=0,
            recommendations=[],
            algorithm="multi-recall-industrial-heuristic-ranker",
            profile=_response_profile(req, family, favorite_profile),
            summary=_response_summary([], 0),
            gap_insights=_gap_insights([]),
            recall_diagnostics=recall_diagnostics,
            ranking_diagnostics={"stage": "empty"},
            experiment=experiment_meta,
        )

    user_skills_lower = [s.lower() for s in req.skills]
    core_skills_lower = [s.lower() for s in (req.core_skills or req.skills[:3])]
    family_positive = _family_positive_keywords(family)
    family_negative = list(dict.fromkeys([*(req.excluded_keywords or []), *_family_negative_keywords(family)]))
    conflict_keywords = _language_conflict_keywords(req.skills, family)
    favorite_skills = [skill.lower() for skill in favorite_profile.get("skills", [])]
    favorite_title_keywords = [keyword.lower() for keyword in favorite_profile.get("title_keywords", [])]
    favorite_job_ids = set(favorite_profile.get("job_ids", []))
    user_edu_level = _edu_level(req.education or "")
    user_exp_years = req.experience_years if req.experience_years is not None else _experience_to_years(req.experience or "")
    user_seniority = _infer_user_seniority(req.experience_years, req.experience, req.target_job_type)

    import json
    candidates_skill_lists = []
    for row in candidate_rows:
        try:
            skills = json.loads(row.get("job_labels") or "[]")
            if not isinstance(skills, list):
                skills = []
        except Exception:
            skills = []
        raw_skills = [s for s in skills if isinstance(s, str) and s.strip()]
        skills = [token.lower() for token in normalize_skill_tokens(raw_skills)]
        candidates_skill_lists.append(skills)

    tfidf_scores = _tfidf_skill_match(user_skills_lower, candidates_skill_lists)
    scored_jobs = []
    feature_maps: List[dict] = []
    fallback_scores: List[float] = []
    staged_rows: List[dict] = []
    for i, row in enumerate(candidate_rows):
        job_skills_lower = candidates_skill_lists[i]
        full_text = f"{row['title'] or ''} {row.get('job_summary') or ''} {row.get('industry_name') or ''} {' '.join(job_skills_lower)}"
        hit_routes = route_hits.get(row["id"], [])

        family_match = _text_match_score(full_text, family_positive)
        skill_match = tfidf_scores[i]
        core_skill_hits = len([skill for skill in core_skills_lower if skill in set(job_skills_lower)])
        core_skill_match = core_skill_hits / max(len(core_skills_lower), 1) if core_skills_lower else 0.0
        title_match = _text_match_score(full_text, title_keywords)
        domain_match = _text_match_score(full_text, domain_keywords)
        intent_match = _intent_match_score(full_text, job_skills_lower, intent_keywords)
        favorite_skill_match = 0.0
        if favorite_skills:
            favorite_skill_hits = len([skill for skill in favorite_skills if skill in set(job_skills_lower)])
            favorite_skill_match = favorite_skill_hits / max(len(favorite_skills), 1)
        favorite_title_match = _text_match_score(full_text, favorite_title_keywords)
        favorite_match = min(1.0, 0.6 * favorite_skill_match + 0.4 * favorite_title_match) if (favorite_skills or favorite_title_keywords) else 0.0

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
        seniority_match = _seniority_match_score(user_seniority, _job_seniority_level(row["title"], row["experience"]))

        industry_match = 0.3
        if req.industry and row["industry_name"]:
            if req.industry.lower() in (row["industry_name"] or "").lower():
                industry_match = 1.0
            elif any(kw in (row["industry_name"] or "").lower() for kw in req.industry.lower().split()):
                industry_match = 0.6

        freshness = _freshness_score(row["publish_date"])

        if family_negative and any(keyword.lower() in full_text.lower() for keyword in family_negative if keyword):
            continue
        if row["id"] in favorite_job_ids:
            continue
        if conflict_keywords and any(keyword in (row["title"] or "").lower() for keyword in conflict_keywords) and core_skill_match == 0.0:
            continue
        if core_skills_lower and core_skill_match == 0.0 and title_match < 0.25 and family_match < 0.35 and domain_match < 0.2:
            continue
        if core_skills_lower and core_skill_match < 0.34 and skill_match < 0.08 and domain_match < 0.2:
            continue
        if title_keywords and title_match < 0.2 and skill_match < 0.1 and family_match < 0.2:
            continue
        if user_skills_lower and domain_keywords and domain_match == 0.0 and skill_match < 0.12 and family_match < 0.2:
            continue

        overlap_routes = set(hit_routes) & {"title", "skill", "city", "preference", "collaborative", "graph", "vector", "hot", "longtail"}
        recall_strength = _recall_strength_score(hit_routes)
        route_bonus = min(0.08, max(0, len(overlap_routes) - 1) * 0.02)
        bucket_bonus = 0.0
        if core_skill_match >= 0.5 and family_match >= 0.3:
            bucket_bonus += 0.05
        elif skill_match >= 0.22 and title_match >= 0.2:
            bucket_bonus += 0.03
        elif "fresh" in hit_routes and freshness >= 0.8 and family_match >= 0.2:
            bucket_bonus += 0.01
        favorite_bonus = 0.0
        if favorite_skills or favorite_title_keywords:
            favorite_bonus = min(0.12, 0.08 * favorite_skill_match + 0.06 * favorite_title_match)

        fallback_score = (
            0.24 * family_match
            + 0.18 * core_skill_match
            + 0.16 * skill_match
            + 0.10 * title_match
            + 0.08 * domain_match
            + 0.07 * intent_match
            + 0.04 * seniority_match
            + 0.03 * recall_strength
            + 0.08 * location_match
            + 0.04 * company_match
            + 0.03 * salary_match
            + 0.02 * education_match
            + 0.02 * experience_match
            + 0.02 * industry_match
            + 0.01 * freshness
            + route_bonus
            + bucket_bonus
            + favorite_bonus
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
            "intent_match": intent_match,
            "seniority_match": seniority_match,
            "recall_strength": recall_strength,
            "favorite_match": favorite_match,
            "location_match": location_match,
            "company_match": company_match,
            "salary_match": salary_match,
            "education_match": education_match,
            "experience_match": experience_match,
            "industry_match": industry_match,
            "freshness": freshness,
            "core_skill_hits": float(core_skill_hits),
            "skill_hits": float(len(matched_skills)),
            "route_overlap": float(len(overlap_routes)),
            "route_bonus": route_bonus,
            "bucket_bonus": bucket_bonus,
            "favorite_bonus": favorite_bonus,
        })
        fallback_scores.append(fallback_score)
        staged_rows.append({
            "row": row,
            "job_skills_lower": job_skills_lower,
            "matched_skills": matched_skills,
            "matched_core_skills": matched_core_skills,
            "missing_skills": missing_skills,
            "recall_routes": hit_routes,
            "favorite_overlap_skills": [skill for skill in favorite_profile.get("skills", []) if skill.lower() in set(job_skills_lower)][:5],
            "details": {
                "family_match": family_match,
                "skill_match": skill_match,
                "core_skill_match": core_skill_match,
                "title_match": title_match,
                "domain_match": domain_match,
                "intent_match": intent_match,
                "seniority_match": seniority_match,
                "recall_strength": recall_strength,
                "favorite_match": favorite_match,
                "location_match": location_match,
                "company_match": company_match,
                "salary_match": salary_match,
                "education_match": education_match,
                "experience_match": experience_match,
                "industry_match": industry_match,
                "freshness": freshness,
            },
            "coarse_score": fallback_score + min(0.08, len(set(hit_routes)) * 0.016),
        })

    if not feature_maps:
        return MatchResponse(
            total_candidates=len(candidate_rows),
            recommendations=[],
            algorithm="multi-recall-industrial-heuristic-ranker",
            profile=_response_profile(req, family, favorite_profile),
            summary=_response_summary([], len(candidate_rows)),
            gap_insights=_gap_insights([]),
            recall_diagnostics=recall_diagnostics,
            ranking_diagnostics={"stage": "filtered_empty", "rawCandidates": len(candidate_rows)},
            experiment=experiment_meta,
        )

    coarse_ranked_indices = sorted(
        range(len(staged_rows)),
        key=lambda idx: staged_rows[idx]["coarse_score"],
        reverse=True,
    )
    coarse_limit = min(len(coarse_ranked_indices), max(req.limit * 8, 120))
    selected_indices = coarse_ranked_indices[:coarse_limit]
    feature_maps = [feature_maps[idx] for idx in selected_indices]
    fallback_scores = [fallback_scores[idx] for idx in selected_indices]
    staged_rows = [staged_rows[idx] for idx in selected_indices]

    ranked_scores, algorithm_name = score_feature_maps(feature_maps, fallback_scores)
    for staged, score in zip(staged_rows, ranked_scores):
        row = staged["row"]
        job_skills_lower = staged["job_skills_lower"]
        details = staged["details"]
        confidence = _match_confidence(details, staged["recall_routes"], staged["matched_core_skills"])
        calibrated_score = _calibrate_rank_score(score, confidence, details)
        fit_label = _fit_label(calibrated_score, confidence, details)
        why_matched = _build_match_reasons(
            row,
            staged["matched_core_skills"],
            staged["matched_skills"],
            details,
            staged["recall_routes"],
        )
        risk_flags = _build_risk_flags(staged["missing_skills"], details, req, calibrated_score, confidence)
        next_actions = _build_next_actions(staged["matched_core_skills"], staged["missing_skills"], details, req)
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
            match_score=calibrated_score,
            confidence=confidence,
            fit_label=fit_label,
            why_matched=why_matched,
            risk_flags=risk_flags,
            next_actions=next_actions,
            match_details=MatchDetail(
                family_match=round(details["family_match"], 3),
                skill_match=round(details["skill_match"], 3),
                core_skill_match=round(details["core_skill_match"], 3),
                title_match=round(details["title_match"], 3),
                domain_match=round(details["domain_match"], 3),
                intent_match=round(details["intent_match"], 3),
                seniority_match=round(details["seniority_match"], 3),
                recall_strength=round(details["recall_strength"], 3),
                favorite_match=round(details["favorite_match"], 3),
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
                "recall_routes": staged["recall_routes"],
                "favorite_overlap_skills": staged["favorite_overlap_skills"],
                "matched_core_skills": staged["matched_core_skills"][:5],
                "matched_skills": staged["matched_skills"][:5],
                "missing_skills": staged["missing_skills"][:5],
                "intent_keywords": intent_keywords[:8],
                "raw_score": round(score, 3),
                "calibrated_score": calibrated_score,
                "confidence": confidence,
                "fit_label": fit_label,
                "score_breakdown": _score_breakdown(details),
                "why_matched": why_matched,
                "risk_flags": risk_flags,
                "next_actions": next_actions,
            },
        ))

    scored_jobs.sort(key=lambda j: j.match_score, reverse=True)
    min_score = 0.12 if family in {"backend", "frontend", "data", "qa"} else 0.08
    filtered_jobs = [job for job in scored_jobs if job.match_score >= min_score]
    reranked_jobs = _diversify_jobs(filtered_jobs, req.limit)
    ranking_diagnostics = {
        "rawCandidates": len(candidate_rows),
        "afterFilter": len(feature_maps),
        "coarseLimit": coarse_limit,
        "afterCoarse": len(staged_rows),
        "afterFinalThreshold": len(filtered_jobs),
        "finalCount": len(reranked_jobs),
        "strategy": "coarse-fallback->ranker->diversity-rerank",
    }
    return MatchResponse(
        total_candidates=len(candidate_rows),
        recommendations=reranked_jobs,
        algorithm=f"multi-recall-{algorithm_name}",
        profile=_response_profile(req, family, favorite_profile),
        summary=_response_summary(reranked_jobs, len(candidate_rows)),
        gap_insights=_gap_insights(reranked_jobs),
        recall_diagnostics=recall_diagnostics,
        ranking_diagnostics=ranking_diagnostics,
        experiment=experiment_meta,
    )
