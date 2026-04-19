"""
简历-岗位双向评分模块
======================
评估用户画像与目标岗位的多维度匹配程度
输出: 总分 + 各维度细分 + 改进建议

POST /algorithm/resume/score
"""
from typing import List, Optional, Dict

from fastapi import APIRouter
from pydantic import BaseModel

from app.db import execute_query

router = APIRouter()


class ResumeScoreRequest(BaseModel):
    # 用户画像
    skills: List[str] = []
    education: Optional[str] = None          # 最高学历
    experience_years: Optional[float] = None  # 工作年限
    target_city: Optional[str] = None
    industry: Optional[str] = None
    # 目标岗位
    target_job_id: Optional[int] = None      # 具体岗位ID
    target_job_type: Optional[str] = None    # 或岗位类型关键词


class DimensionScore(BaseModel):
    skill: float = 0.0
    education: float = 0.0
    experience: float = 0.0
    city: float = 0.0
    industry: float = 0.0


class ImprovementTip(BaseModel):
    dimension: str
    tip: str
    expected_gain: float  # 预期匹配度提升百分比


class ResumeScoreResponse(BaseModel):
    overall_score: float          # 0-100
    grade: str                    # A/B/C/D
    dimension_scores: DimensionScore
    improvement_tips: List[ImprovementTip]
    matched_skills: List[str]
    missing_skills: List[str]
    job_title: Optional[str] = None
    job_company: Optional[str] = None
    summary: str


EDU_LEVELS = {"高中": 1, "中专": 1, "大专": 2, "本科": 3, "硕士": 4, "博士": 5}


def _edu_score(user_edu: str | None, job_edu: str | None) -> float:
    """学历匹配分 0~100"""
    user_level = 3  # 默认本科
    job_level = 2   # 默认大专
    if user_edu:
        for key, val in EDU_LEVELS.items():
            if key in user_edu:
                user_level = val
                break
    if job_edu:
        for key, val in EDU_LEVELS.items():
            if key in job_edu:
                job_level = val
                break
    diff = user_level - job_level
    if diff >= 0:
        return 100.0
    # 每差一级扣 25 分
    return max(0.0, 100.0 + diff * 25)


def _exp_score(user_years: float | None, job_exp_str: str | None) -> float:
    """经验匹配分 0~100"""
    import re
    if user_years is None:
        return 50.0
    text = job_exp_str or ""
    nums = [int(n) for n in re.findall(r"\d+", text)]
    if not nums:
        if "不限" in text or "应届" in text:
            return 90.0
        job_min = 1.0
    else:
        job_min = float(nums[0])

    diff = user_years - job_min
    if diff >= 0:
        return 100.0
    # 每差 1 年扣 15 分
    return max(0.0, 100.0 + diff * 15)


def _skill_score(user_skills: List[str], job_skills: List[str]) -> tuple[float, List[str], List[str]]:
    """技能匹配分 0~100，返回 (score, matched, missing)"""
    if not job_skills:
        return 70.0, [], []
    user_lower = {s.lower() for s in user_skills}
    job_lower_list = [s.lower() for s in job_skills]
    matched = [s for s in job_skills if s.lower() in user_lower]
    missing = [s for s in job_skills if s.lower() not in user_lower]
    score = len(matched) / max(len(job_lower_list), 1) * 100
    return round(score, 1), matched, missing


def _city_score(user_city: str | None, job_city: str | None) -> float:
    """城市匹配分"""
    if not user_city or not job_city:
        return 70.0
    if user_city.lower() in job_city.lower() or job_city.lower() in user_city.lower():
        return 100.0
    return 30.0


def _industry_score(user_industry: str | None, job_industry: str | None) -> float:
    """行业匹配分"""
    if not user_industry or not job_industry:
        return 50.0
    if user_industry.lower() in job_industry.lower():
        return 100.0
    return 30.0


@router.post("/score", response_model=ResumeScoreResponse)
def score_resume(req: ResumeScoreRequest):
    """简历多维度评分"""

    # 查询目标岗位信息
    job_info: Dict = {}
    job_skills: List[str] = []

    if req.target_job_id:
        job_rows = execute_query("""
            SELECT jp.title, jp.company_name, jp.job_city, jp.education_need,
                   jp.experience_year, jp.job_classification, jp.job_labels
            FROM biz_job_posting jp
            WHERE jp.id = :job_id
        """, {"job_id": req.target_job_id})
        if job_rows:
            job_info = job_rows[0]
            try:
                import json
                job_skills = json.loads(job_info.get("job_labels") or "[]")
                if not isinstance(job_skills, list):
                    job_skills = []
            except:
                job_skills = []
    elif req.target_job_type:
        # 取同类岗位的平均技能需求
        job_rows = execute_query("""
            SELECT job_labels
            FROM biz_job_posting
            WHERE title LIKE :job_type AND job_labels IS NOT NULL
            LIMIT 200
        """, {"job_type": f"%{req.target_job_type}%"})
        
        import json
        from collections import Counter
        skill_counter = Counter()
        for r in job_rows:
            try:
                labels = json.loads(r["job_labels"])
                if isinstance(labels, list):
                    for L in labels:
                        skill_counter[L.strip()] += 1
            except:
                pass
        job_skills = [s for s, c in skill_counter.most_common(15)]

        edu_rows = execute_query("""
            SELECT education_need, COUNT(*) AS cnt
            FROM biz_job_posting
            WHERE title LIKE :job_type AND education_need IS NOT NULL
            GROUP BY education_need
            ORDER BY cnt DESC
            LIMIT 1
        """, {"job_type": f"%{req.target_job_type}%"})
        if edu_rows:
            job_info["education_need"] = edu_rows[0]["education_need"]

        exp_rows = execute_query("""
            SELECT experience_year, COUNT(*) AS cnt
            FROM biz_job_posting
            WHERE title LIKE :job_type AND experience_year IS NOT NULL
            GROUP BY experience_year
            ORDER BY cnt DESC
            LIMIT 1
        """, {"job_type": f"%{req.target_job_type}%"})
        if exp_rows:
            job_info["experience_year"] = exp_rows[0]["experience_year"]

    # 各维度评分
    skill_sc, matched, missing = _skill_score(req.skills, job_skills)
    edu_sc = _edu_score(req.education, job_info.get("education_need"))
    exp_sc = _exp_score(req.experience_years, job_info.get("experience_year"))
    city_sc = _city_score(req.target_city, job_info.get("job_city"))
    ind_sc = _industry_score(req.industry, job_info.get("job_classification"))

    # 加权综合分
    overall = (
        skill_sc * 0.35
        + edu_sc * 0.20
        + exp_sc * 0.20
        + city_sc * 0.15
        + ind_sc * 0.10
    )
    overall = round(overall, 1)

    # 等级
    if overall >= 85:
        grade = "A"
    elif overall >= 70:
        grade = "B"
    elif overall >= 55:
        grade = "C"
    else:
        grade = "D"

    # 改进建议
    tips: List[ImprovementTip] = []
    if skill_sc < 80 and missing:
        top_missing = missing[:3]
        tip_text = f"补充 {', '.join(top_missing)} 可将技能匹配度从 {skill_sc:.0f}% 提升约 {min(20, len(missing) * 5)}%"
        tips.append(ImprovementTip(
            dimension="skill",
            tip=tip_text,
            expected_gain=round(min(20.0, len(missing) * 0.35 * 5), 1),
        ))
    if edu_sc < 100:
        tips.append(ImprovementTip(
            dimension="education",
            tip=f"提升学历至岗位要求（{job_info.get('education_need', '本科')}）可提升约 {(100 - edu_sc) * 0.20:.0f}% 综合匹配度",
            expected_gain=round((100 - edu_sc) * 0.20, 1),
        ))
    if city_sc < 100 and req.target_city:
        tips.append(ImprovementTip(
            dimension="city",
            tip=f"选择 {job_info.get('job_city', '')} 本地岗位可获得满分城市加成",
            expected_gain=round((100 - city_sc) * 0.15, 1),
        ))

    # 摘要
    job_name = job_info.get("title") or req.target_job_type or "目标岗位"
    summary = (
        f"综合匹配得分 {overall}/100（等级 {grade}），"
        f"已匹配 {len(matched)} 项技能，缺失 {len(missing)} 项。"
        f"{'建议优先补充：' + ', '.join(missing[:3]) if missing else '技能覆盖良好！'}"
    )

    return ResumeScoreResponse(
        overall_score=overall,
        grade=grade,
        dimension_scores=DimensionScore(
            skill=round(skill_sc, 1),
            education=round(edu_sc, 1),
            experience=round(exp_sc, 1),
            city=round(city_sc, 1),
            industry=round(ind_sc, 1),
        ),
        improvement_tips=tips,
        matched_skills=matched[:10],
        missing_skills=missing[:10],
        job_title=job_info.get("title"),
        job_company=job_info.get("company_name"),
        summary=summary,
    )
