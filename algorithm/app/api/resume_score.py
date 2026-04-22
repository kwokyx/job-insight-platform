import re
from collections import Counter
from typing import Dict, List, Optional

from fastapi import APIRouter
from pydantic import BaseModel

from app.db import execute_query
from app.skill_normalizer import filter_skills_by_family, infer_skill_family, normalize_job_labels, normalize_skill_tokens

router = APIRouter()


class ResumeScoreRequest(BaseModel):
    skills: List[str] = []
    education: Optional[str] = None
    experience_years: Optional[float] = None
    target_city: Optional[str] = None
    industry: Optional[str] = None
    target_job_id: Optional[int] = None
    target_job_type: Optional[str] = None


class ResumeReviewRequest(BaseModel):
    target_job: str
    resume_text: str
    user_skills: List[str] = []
    current_job: Optional[str] = None
    education: Optional[str] = None
    experience_years: Optional[float] = None
    target_city: Optional[str] = None
    industry: Optional[str] = None


class DimensionScore(BaseModel):
    skill: float = 0.0
    education: float = 0.0
    experience: float = 0.0
    city: float = 0.0
    industry: float = 0.0


class ImprovementTip(BaseModel):
    dimension: str
    tip: str
    expected_gain: float


class ResumeScoreResponse(BaseModel):
    overall_score: float
    grade: str
    dimension_scores: DimensionScore
    improvement_tips: List[ImprovementTip]
    matched_skills: List[str]
    missing_skills: List[str]
    job_title: Optional[str] = None
    job_company: Optional[str] = None
    summary: str


EDU_LEVELS = {"高中": 1, "中专": 1, "大专": 2, "本科": 3, "硕士": 4, "博士": 5}
SECTION_PATTERNS = [
    ("summary", ["个人简介", "个人总结", "职业概述", "summary", "profile"]),
    ("experience", ["工作经历", "工作经验", "experience", "employment"]),
    ("project", ["项目经历", "项目经验", "project"]),
    ("education", ["教育经历", "教育背景", "education"]),
    ("skills", ["专业技能", "技能清单", "技能标签", "skills", "tech stack"]),
]
PROJECT_KEYWORDS = ["项目", "负责", "主导", "搭建", "设计", "优化", "实现", "推进", "落地", "协同"]
ARCHITECTURE_KEYWORDS = ["架构", "微服务", "高并发", "分布式", "缓存", "消息队列", "mq", "docker", "k8s", "治理"]
DELIVERY_KEYWORDS = ["上线", "交付", "发布", "迭代", "联调", "部署", "监控", "告警", "灰度"]
LEADERSHIP_KEYWORDS = ["主导", "负责", "推动", "协调", "评审", "owner", "lead"]
RESULT_KEYWORDS = ["提升", "降低", "缩短", "增长", "节省", "稳定", "优化", "下降", "提高"]
METRIC_PATTERN = re.compile(r"\d+(\.\d+)?\s*(%|ms|s|分钟|小时|天|万|千|亿|k|K|qps|wps|人|次)", re.IGNORECASE)


def _infer_target_keywords(target_job: str) -> List[str]:
    family = infer_skill_family([target_job])
    if family == "backend":
        return [target_job, "Java", "Spring Boot", "后端"]
    if family == "frontend":
        return [target_job, "Vue", "React", "前端"]
    if family == "data":
        return [target_job, "Python", "SQL", "数据"]
    if family == "qa":
        return [target_job, "测试", "自动化测试", "QA"]
    return [target_job]


def _target_market_profile(target_job: str, city: Optional[str] = None) -> dict:
    keywords = [keyword for keyword in _infer_target_keywords(target_job) if keyword][:4]
    conditions = ["job_labels IS NOT NULL"]
    params: Dict[str, object] = {}
    title_clauses = []
    for index, keyword in enumerate(keywords):
        key = f"target_job_{index}"
        params[key] = f"%{keyword}%"
        title_clauses.append(f"title LIKE :{key}")
    if title_clauses:
        conditions.append("(" + " OR ".join(title_clauses) + ")")
    if city:
        conditions.append("(COALESCE(city, job_city) LIKE :city OR job_city LIKE :city)")
        params["city"] = f"%{city}%"

    rows = execute_query(
        f"""
        SELECT title, company_name, job_city, education_need, experience_year, job_classification, job_labels
        FROM biz_job_posting
        WHERE {' AND '.join(conditions)}
        ORDER BY publish_date DESC, id DESC
        LIMIT 400
        """,
        params,
    )
    if len(rows) < 20 and city:
        broader = _target_market_profile(target_job, None)
        if broader.get("skills"):
            return broader

    counter: Counter = Counter()
    job_info = rows[0] if rows else {}
    family = infer_skill_family([target_job])
    for row in rows:
        for token in filter_skills_by_family(normalize_job_labels(row.get("job_labels")), family):
            counter[token] += 1
    return {
        "job_info": job_info,
        "skills": [skill for skill, _ in counter.most_common(15)],
        "sample_count": len(rows),
    }


def _edu_score(user_edu: Optional[str], job_edu: Optional[str]) -> float:
    user_level = 3
    job_level = 2
    for key, value in EDU_LEVELS.items():
        if user_edu and key in user_edu:
            user_level = value
        if job_edu and key in job_edu:
            job_level = value
    diff = user_level - job_level
    return 100.0 if diff >= 0 else max(0.0, 100.0 + diff * 25)


def _exp_score(user_years: Optional[float], job_exp: Optional[str]) -> float:
    if user_years is None:
        return 50.0
    nums = [int(num) for num in re.findall(r"\d+", job_exp or "")]
    if not nums:
        text = job_exp or ""
        return 90.0 if ("不限" in text or "应届" in text) else 60.0
    diff = user_years - nums[0]
    return 100.0 if diff >= 0 else max(0.0, 100.0 + diff * 15)


def _skill_score(user_skills: List[str], job_skills: List[str]) -> tuple[float, List[str], List[str]]:
    user_set = {skill.lower() for skill in normalize_skill_tokens(user_skills)}
    normalized_job_skills = normalize_skill_tokens(job_skills)
    matched = [skill for skill in normalized_job_skills if skill.lower() in user_set]
    missing = [skill for skill in normalized_job_skills if skill.lower() not in user_set]
    score = round(len(matched) / max(len(normalized_job_skills), 1) * 100, 1) if normalized_job_skills else 70.0
    return score, matched, missing


def _city_score(user_city: Optional[str], job_city: Optional[str]) -> float:
    if not user_city or not job_city:
        return 70.0
    normalized_user = user_city.lower()
    normalized_job = job_city.lower()
    return 100.0 if normalized_user in normalized_job or normalized_job in normalized_user else 30.0


def _industry_score(user_industry: Optional[str], job_industry: Optional[str]) -> float:
    if not user_industry or not job_industry:
        return 50.0
    normalized_user = user_industry.lower()
    normalized_job = job_industry.lower()
    return 100.0 if normalized_user in normalized_job or normalized_job in normalized_user else 30.0


def _extract_resume_skills(resume_text: str, user_skills: List[str], target_skills: List[str]) -> List[str]:
    resume_lower = resume_text.lower()
    candidates = normalize_skill_tokens([*user_skills, *target_skills])
    extracted = [skill for skill in candidates if skill.lower() in resume_lower]
    return list(dict.fromkeys([*normalize_skill_tokens(user_skills), *extracted]))


def _split_lines(text: str) -> List[str]:
    return [line.strip(" -*•\t") for line in re.split(r"[\r\n]+", text or "") if line.strip()]


def _extract_sections(text: str) -> Dict[str, str]:
    lines = _split_lines(text)
    sections: Dict[str, List[str]] = {}
    current = "general"
    sections[current] = []
    for line in lines:
        lowered = line.lower()
        matched_key = None
        for key, patterns in SECTION_PATTERNS:
            if any(pattern.lower() in lowered for pattern in patterns):
                matched_key = key
                break
        if matched_key:
            current = matched_key
            sections.setdefault(current, [])
            continue
        sections.setdefault(current, []).append(line)
    return {key: "\n".join(value).strip() for key, value in sections.items() if value}


def _contains_any(text: str, keywords: List[str]) -> bool:
    lowered = text.lower()
    return any(keyword.lower() in lowered for keyword in keywords)


def _keyword_density_score(text: str, target_skills: List[str]) -> float:
    if not target_skills:
        return 60.0
    lowered = text.lower()
    hits = sum(1 for skill in target_skills if skill.lower() in lowered)
    return round(min(100.0, hits / max(len(target_skills), 1) * 100), 1)


def _quant_score(text: str) -> float:
    metric_hits = len(METRIC_PATTERN.findall(text))
    result_hits = sum(1 for keyword in RESULT_KEYWORDS if keyword in text)
    if metric_hits >= 3:
        return 90.0
    if metric_hits >= 1 and result_hits >= 1:
        return 78.0
    if metric_hits >= 1:
        return 66.0
    if result_hits >= 2:
        return 52.0
    return 35.0


def _section_score(sections: Dict[str, str], key: str, fallback_text: str) -> float:
    text = sections.get(key, "").strip()
    if not text:
        return 35.0 if key in {"summary", "project"} else 45.0
    lines = _split_lines(text)
    richness = min(100.0, 45 + len(lines) * 8)
    if key == "project":
        richness += 10 if _contains_any(text, PROJECT_KEYWORDS) else 0
        richness += 10 if METRIC_PATTERN.search(text) else 0
    if key == "summary":
        richness += 8 if _contains_any(text, ["年经验", "后端", "前端", "数据", "测试"]) else 0
    if key == "skills":
        richness += 8 if len(normalize_skill_tokens(lines)) >= 5 else 0
    return round(min(100.0, richness), 1)


def _build_strengths(matched_skills: List[str], scorecard: dict, req: ResumeReviewRequest, sections: Dict[str, str]) -> List[str]:
    strengths: List[str] = []
    if matched_skills:
        strengths.append("已命中目标岗位的高频技能关键词：" + "、".join(matched_skills[:4]))
    if scorecard["projectEvidence"] >= 70:
        strengths.append("项目经历里已经出现职责、技术动作和实现背景，基础证据链是成立的。")
    if scorecard["businessImpact"] >= 70:
        strengths.append("简历中已有量化结果，对筛选系统和面试官都更有说服力。")
    if sections.get("summary"):
        strengths.append("简历包含个人概述，可以承接目标岗位和经验定位。")
    if req.experience_years and req.experience_years >= 2:
        strengths.append(f"已有约 {req.experience_years:g} 年经验，具备支撑中级岗位投递的履历基础。")
    return strengths[:4]


def _build_issues(missing_skills: List[str], scorecard: dict, req: ResumeReviewRequest, sections: Dict[str, str]) -> List[str]:
    issues: List[str] = []
    if missing_skills:
        issues.append("缺少目标岗位高频关键词：" + "、".join(missing_skills[:4]))
    if scorecard["projectEvidence"] < 60:
        issues.append("项目描述偏弱，缺少背景、职责、难点、方案和结果的完整链路。")
    if scorecard["businessImpact"] < 60:
        issues.append("量化结果不足，无法证明性能、效率或业务价值。")
    if scorecard["structureReadability"] < 60:
        issues.append("简历结构不够清晰，重点信息没有前置。")
    if not sections.get("summary"):
        issues.append("缺少个人摘要，目标岗位、经验层级和技术方向没有先讲清楚。")
    if req.target_city and scorecard["locationFit"] < 60:
        issues.append("目标城市和简历画像的匹配证据不足。")
    return issues[:4]


def _build_section_advice(sections: Dict[str, str], missing_skills: List[str]) -> List[dict]:
    advice: List[dict] = []
    if not sections.get("summary"):
        advice.append({
            "section": "个人摘要",
            "score": 35,
            "issue": "缺少摘要，简历开头没有告诉招聘方你要投什么岗位、处于什么层级。",
            "advice": "增加 3 到 4 行摘要，写清目标方向、经验年限、核心技术栈和代表性场景。",
        })
    else:
        advice.append({
            "section": "个人摘要",
            "score": _section_score(sections, "summary", ""),
            "issue": "摘要可继续收紧成岗位导向表达。",
            "advice": "把目标岗位和最强技术栈前置，避免空泛自我评价。",
        })

    if not sections.get("project"):
        advice.append({
            "section": "项目经历",
            "score": 30,
            "issue": "没有独立项目经历板块，最影响岗位说服力。",
            "advice": "补 2 到 3 个项目，按背景、动作、技术方案、量化结果来写。",
        })
    else:
        advice.append({
            "section": "项目经历",
            "score": _section_score(sections, "project", ""),
            "issue": "项目经历需要更强的结果和复杂度信号。",
            "advice": "每个项目至少补 1 条指标结果，并明确自己的职责边界和技术决策。",
        })

    if missing_skills:
        advice.append({
            "section": "技能清单",
            "score": _section_score(sections, "skills", ""),
            "issue": "技能区未充分承接目标岗位高频关键词。",
            "advice": "把这些词补到技能区或项目描述中：" + "、".join(missing_skills[:5]),
        })
    return advice[:4]


def _build_priority_fixes(scorecard: dict, missing_skills: List[str], sections: Dict[str, str]) -> List[dict]:
    fixes: List[dict] = []
    if scorecard["businessImpact"] < 60:
        fixes.append({
            "title": "补量化结果",
            "priority": "P0",
            "reason": "没有量化结果时，简历很难证明价值产出。",
            "expectedGain": 12,
        })
    if scorecard["projectEvidence"] < 60:
        fixes.append({
            "title": "重写项目经历",
            "priority": "P0",
            "reason": "项目证据不足会直接影响面试官判断你的真实深度。",
            "expectedGain": 14,
        })
    if missing_skills:
        fixes.append({
            "title": "补齐岗位关键词",
            "priority": "P1",
            "reason": "ATS 和首轮筛选会优先看目标岗位关键词是否出现。",
            "expectedGain": min(10, 3 + len(missing_skills)),
        })
    if not sections.get("summary"):
        fixes.append({
            "title": "补个人摘要",
            "priority": "P1",
            "reason": "没有摘要时，简历开头无法快速建立岗位定位。",
            "expectedGain": 8,
        })
    return fixes[:4]


def _build_evidence_gaps(text: str, sections: Dict[str, str]) -> List[dict]:
    gaps: List[dict] = []
    if not METRIC_PATTERN.search(text):
        gaps.append({"title": "结果指标缺失", "detail": "缺少性能、效率、规模或业务结果数字。"})
    if not _contains_any(text, ARCHITECTURE_KEYWORDS):
        gaps.append({"title": "架构深度不足", "detail": "缺少缓存、微服务、分布式、消息队列等复杂场景证据。"})
    if not _contains_any(text, DELIVERY_KEYWORDS):
        gaps.append({"title": "交付闭环不足", "detail": "没有体现上线、部署、联调、监控、排障等落地能力。"})
    if not sections.get("education"):
        gaps.append({"title": "教育信息缺失", "detail": "学历板块缺失时，校招/社招筛选都可能受影响。"})
    return gaps[:4]


def _build_rewrite_plan(req: ResumeReviewRequest, missing_skills: List[str]) -> List[dict]:
    return [
        {
            "step": "先重写摘要",
            "detail": f"首段直接对齐 {req.target_job}，用经验年限、核心技术栈、代表场景做定位。",
        },
        {
            "step": "再重写项目",
            "detail": "每个项目采用 背景 -> 关键动作 -> 指标结果 的三段式表达。",
        },
        {
            "step": "补关键词",
            "detail": ("把缺失技能补到技能区和项目里：" + "、".join(missing_skills[:5])) if missing_skills else "围绕目标岗位继续强化关键词覆盖。",
        },
        {
            "step": "最后收结构",
            "detail": "把最强项目和最相关技能放在前半屏，减少无关描述。",
        },
    ]


def _readiness_label(score: float) -> str:
    if score >= 78:
        return "可直接投递"
    if score >= 60:
        return "优化后可投递"
    return "建议重点重写"


def _section_coverage(sections: Dict[str, str]) -> dict:
    expected = ["summary", "skills", "experience", "project", "education"]
    present = [key for key in expected if sections.get(key)]
    missing = [key for key in expected if key not in present]
    return {
        "present": present,
        "missing": missing,
        "coverageRate": round(len(present) / len(expected), 3),
    }


def _ats_signals(
    keyword_score: float,
    structure_score: float,
    resume_text: str,
    matched_skills: List[str],
    missing_skills: List[str],
) -> dict:
    return {
        "keywordCoverage": keyword_score,
        "matchedKeywordCount": len(matched_skills),
        "missingKeywordCount": len(missing_skills),
        "structureReadable": structure_score >= 65,
        "hasQuantifiedEvidence": bool(METRIC_PATTERN.search(resume_text)),
        "riskLevel": "low" if keyword_score >= 65 and structure_score >= 70 else ("medium" if keyword_score >= 40 else "high"),
    }


def _competitive_signals(
    impact_score: float,
    architecture_score: float,
    delivery_score: float,
    sections: Dict[str, str],
) -> dict:
    return {
        "hasProjectModule": bool(sections.get("project")),
        "hasExperienceModule": bool(sections.get("experience")),
        "businessImpactReady": impact_score >= 65,
        "architectureReady": architecture_score >= 60,
        "deliveryReady": delivery_score >= 60,
    }


def _review_confidence(sample_count: int, sections: Dict[str, str], req: ResumeReviewRequest) -> float:
    confidence = (
        min(1.0, sample_count / 120.0) * 0.45
        + min(1.0, len([key for key in sections if sections.get(key)]) / 5.0) * 0.35
        + (0.12 if req.target_city else 0.0)
        + (0.08 if req.industry else 0.0)
    )
    return round(min(0.94, max(0.28, confidence)), 3)


@router.post("/score", response_model=ResumeScoreResponse)
def score_resume(req: ResumeScoreRequest):
    job_info: Dict = {}
    job_skills: List[str] = []
    if req.target_job_id:
        rows = execute_query(
            """
            SELECT title, company_name, job_city, education_need, experience_year, job_classification, job_labels
            FROM biz_job_posting
            WHERE id = :job_id
            """,
            {"job_id": req.target_job_id},
        )
        if rows:
            job_info = rows[0]
            job_skills = normalize_job_labels(job_info.get("job_labels"))
    elif req.target_job_type:
        market = _target_market_profile(req.target_job_type, req.target_city)
        job_info = market["job_info"]
        job_skills = market["skills"]

    skill_sc, matched, missing = _skill_score(req.skills, job_skills)
    edu_sc = _edu_score(req.education, job_info.get("education_need"))
    exp_sc = _exp_score(req.experience_years, job_info.get("experience_year"))
    city_sc = _city_score(req.target_city, job_info.get("job_city"))
    industry_sc = _industry_score(req.industry, job_info.get("job_classification"))
    overall = round(skill_sc * 0.35 + edu_sc * 0.2 + exp_sc * 0.2 + city_sc * 0.15 + industry_sc * 0.1, 1)
    grade = "A" if overall >= 85 else ("B" if overall >= 70 else ("C" if overall >= 55 else "D"))

    tips: List[ImprovementTip] = []
    if missing:
        tips.append(ImprovementTip(dimension="skill", tip=f"优先补齐 {', '.join(missing[:3])}，这是目标岗位最常见的技能缺口。", expected_gain=round(min(20.0, len(missing) * 3.5), 1)))
    if edu_sc < 100:
        tips.append(ImprovementTip(dimension="education", tip=f"目标岗位学历通常偏向 {job_info.get('education_need') or '本科'}，这一项仍有提升空间。", expected_gain=round((100 - edu_sc) * 0.2, 1)))
    if city_sc < 100 and req.target_city:
        tips.append(ImprovementTip(dimension="city", tip="如果接受目标城市本地岗位，这一项可以明显提分。", expected_gain=round((100 - city_sc) * 0.15, 1)))

    return ResumeScoreResponse(
        overall_score=overall,
        grade=grade,
        dimension_scores=DimensionScore(skill=skill_sc, education=edu_sc, experience=exp_sc, city=city_sc, industry=industry_sc),
        improvement_tips=tips,
        matched_skills=matched[:10],
        missing_skills=missing[:10],
        job_title=job_info.get("title"),
        job_company=job_info.get("company_name"),
        summary=f"综合匹配得分 {overall}/100，已命中 {len(matched)} 项技能，仍缺少 {len(missing)} 项目标岗位高频技能。",
    )


@router.post("/review")
def review_resume(req: ResumeReviewRequest):
    market = _target_market_profile(req.target_job, req.target_city)
    target_skills = market["skills"]
    job_info = market["job_info"]
    sample_count = market.get("sample_count", 0)
    resume_text = (req.resume_text or "").strip()
    resume_lower = resume_text.lower()
    sections = _extract_sections(resume_text)

    extracted_skills = _extract_resume_skills(resume_text, req.user_skills, target_skills)
    extracted_skill_set = {skill.lower() for skill in extracted_skills}
    matched_skills = [skill for skill in target_skills if skill.lower() in extracted_skill_set][:10]
    missing_skills = [skill for skill in target_skills if skill.lower() not in extracted_skill_set][:10]

    keyword_score = round(len(matched_skills) / max(len(target_skills), 1) * 100, 1) if target_skills else 60.0
    project_score = round((_section_score(sections, "project", resume_text) * 0.65) + (15 if _contains_any(resume_text, PROJECT_KEYWORDS) else 0) + (10 if sections.get("experience") else 0), 1)
    impact_score = _quant_score(resume_text)
    structure_score = round(
        min(
            100.0,
            35
            + (15 if sections.get("summary") else 0)
            + (15 if sections.get("project") else 0)
            + (12 if sections.get("skills") else 0)
            + (10 if sections.get("experience") else 0)
            + (8 if len(_split_lines(resume_text)) >= 8 else 0)
        ),
        1,
    )
    architecture_score = round(min(100.0, _keyword_density_score(resume_text, filter_skills_by_family(target_skills, infer_skill_family([req.target_job]))[:8]) * 0.5 + (35 if _contains_any(resume_text, ARCHITECTURE_KEYWORDS) else 0)), 1)
    delivery_score = round(min(100.0, 45 + (25 if _contains_any(resume_text, DELIVERY_KEYWORDS) else 0) + (10 if _contains_any(resume_text, LEADERSHIP_KEYWORDS) else 0) + (10 if sections.get("experience") else 0)), 1)
    experience_score = _exp_score(req.experience_years, job_info.get("experience_year"))
    education_score = _edu_score(req.education, job_info.get("education_need"))
    city_score = _city_score(req.target_city, job_info.get("job_city"))

    overall_score = round(
        keyword_score * 0.24
        + project_score * 0.16
        + impact_score * 0.14
        + structure_score * 0.12
        + architecture_score * 0.11
        + delivery_score * 0.09
        + experience_score * 0.08
        + education_score * 0.03
        + city_score * 0.03,
        1,
    )

    scorecard = {
        "keywordCoverage": keyword_score,
        "projectEvidence": project_score,
        "businessImpact": impact_score,
        "structureReadability": structure_score,
        "architectureEvidence": architecture_score,
        "deliveryEvidence": delivery_score,
        "experienceFit": experience_score,
        "educationFit": education_score,
        "locationFit": city_score,
    }
    section_coverage = _section_coverage(sections)
    ats_signals = _ats_signals(keyword_score, structure_score, resume_text, matched_skills, missing_skills)
    competitive_signals = _competitive_signals(impact_score, architecture_score, delivery_score, sections)
    review_confidence = _review_confidence(sample_count, sections, req)

    strengths = _build_strengths(matched_skills, scorecard, req, sections)
    issues = _build_issues(missing_skills, scorecard, req, sections)
    section_advice = _build_section_advice(sections, missing_skills)
    priority_fixes = _build_priority_fixes(scorecard, missing_skills, sections)
    evidence_gaps = _build_evidence_gaps(resume_text, sections)
    rewrite_plan = _build_rewrite_plan(req, missing_skills)

    suggestions: List[str] = []
    if impact_score < 60:
        suggestions.append("补充量化结果，例如延迟下降、吞吐提升、故障率下降或交付效率提升。")
    if project_score < 60:
        suggestions.append("至少写清一个完整项目：背景、职责、技术栈、关键难点和最终结果。")
    if architecture_score < 60 and infer_skill_family([req.target_job]) == "backend":
        suggestions.append("补上系统设计证据，例如缓存、分库分表、微服务、消息队列或性能优化。")
    if missing_skills:
        suggestions.append("把目标岗位高频关键词前置到技能清单或项目描述中：" + "、".join(missing_skills[:5]))
    if structure_score < 60:
        suggestions.append("按 摘要 -> 技能 -> 工作经历 -> 项目经历 -> 教育经历 的顺序重排简历结构。")

    rewrite_hints = [
        "用 动作 + 方法 + 结果 替代简单名词堆砌。",
        "每个项目至少保留 2 到 3 条结果性描述，而不是只列技术名词。",
        "把最相关技术栈前置到摘要和第一段项目经历。",
        "如果目标是中高级岗位，要明确复杂度、规模和稳定性责任。",
    ]

    return {
        "targetJob": req.target_job,
        "overallScore": overall_score,
        "score": overall_score,
        "scorecard": scorecard,
        "matchedSkills": matched_skills,
        "missingSkills": missing_skills,
        "keywords": list(dict.fromkeys([*matched_skills[:4], *missing_skills[:4]])),
        "strengths": strengths,
        "issues": issues,
        "actions": suggestions[:4],
        "suggestions": suggestions,
        "rewriteHints": rewrite_hints,
        "sectionAdvice": section_advice,
        "priorityFixes": priority_fixes,
        "evidenceGaps": evidence_gaps,
        "rewritePlan": rewrite_plan,
        "sectionCoverage": section_coverage,
        "atsSignals": ats_signals,
        "competitiveSignals": competitive_signals,
        "confidence": review_confidence,
        "marketSignals": {
            "sampleCount": sample_count,
            "targetSkills": target_skills[:10],
            "jobTitle": job_info.get("title"),
            "jobCompany": job_info.get("company_name"),
            "jobCity": job_info.get("job_city"),
        },
        "benchmark": {
            "sampleCount": sample_count,
            "marketSkillCount": len(target_skills),
            "marketReadiness": _readiness_label(overall_score),
        },
        "extractedProfile": {
            "currentJob": req.current_job,
            "education": req.education,
            "experienceYears": req.experience_years,
            "targetCity": req.target_city,
            "industry": req.industry,
            "skills": extracted_skills[:12],
            "sections": list(sections.keys()),
        },
        "diagnosis": {
            "readinessLevel": _readiness_label(overall_score),
            "atsRisk": "low" if keyword_score >= 65 else ("medium" if keyword_score >= 40 else "high"),
            "coreIssue": "关键词覆盖不足" if keyword_score < 50 else ("项目证据偏弱" if project_score < 60 else ("量化成果不足" if impact_score < 60 else "结构表达仍可优化")),
        },
        "summary": f"当前简历对 {req.target_job} 的综合贴合度为 {overall_score}/100，主要短板集中在关键词覆盖、项目证据和结果量化三类信号。",
    }
