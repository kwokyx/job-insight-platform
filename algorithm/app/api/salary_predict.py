from typing import List, Optional

import numpy as np
from fastapi import APIRouter
from pydantic import BaseModel

from app.db import execute_query
from app.ml.salary_model import predict_with_model
from app.skill_normalizer import infer_skill_family, normalize_job_labels, normalize_skill_tokens

router = APIRouter()


class SalaryPredictRequest(BaseModel):
    city: Optional[str] = None
    education: Optional[str] = None
    experience: Optional[str] = None
    skills: List[str] = []
    industry: Optional[str] = None


class SalaryFactor(BaseModel):
    factor: str
    impact: float


class FeatureImportanceItem(BaseModel):
    feature: str
    importance: float


class SalaryPredictResponse(BaseModel):
    predicted_min: Optional[float] = None
    predicted_max: Optional[float] = None
    predicted_median: Optional[float] = None
    confidence: float = 0.0
    sample_count: int = 0
    percentile_25: Optional[float] = None
    percentile_75: Optional[float] = None
    main_factors: List[SalaryFactor] = []
    quantile_p25: Optional[float] = None
    quantile_p75: Optional[float] = None
    feature_importance: List[FeatureImportanceItem] = []
    cv_mae_mean: Optional[float] = None
    cv_mae_std: Optional[float] = None
    quantile_model_available: bool = False


def _experience_years(value: Optional[str]) -> float:
    if not value:
        return 1.0
    nums = [int(num) for num in __import__("re").findall(r"\d+", value)]
    if not nums:
        text = value or ""
        if "不限" in text or "应届" in text or "在校" in text:
            return 0.0
        return 1.0
    if len(nums) >= 2:
        return float(sum(nums[:2]) / 2)
    return float(nums[0])


def _education_impact(edu: Optional[str]) -> float:
    impact_map = {"大专": -2.0, "本科": 0.0, "硕士": 3.0, "博士": 6.0}
    return impact_map.get(edu or "", 0.0)


def _confidence_label(value: float) -> str:
    if value >= 0.8:
        return "高"
    if value >= 0.6:
        return "中高"
    if value >= 0.4:
        return "中"
    return "偏低"


def _format_range(min_value: Optional[float], max_value: Optional[float]) -> str:
    if min_value is None and max_value is None:
        return "暂无结果"
    if min_value is None:
        return f"{round(max_value or 0)}K"
    if max_value is None:
        return f"{round(min_value)}K"
    return f"{round(min_value)}K-{round(max_value)}K"


def _rows_by_conditions(req: SalaryPredictRequest):
    conditions = ["salary_min IS NOT NULL", "salary_min > 0"]
    params = {}

    if req.city:
        conditions.append("(job_city LIKE :city OR city LIKE :city)")
        params["city"] = f"%{req.city}%"
    if req.education:
        conditions.append("education_need LIKE :education")
        params["education"] = f"%{req.education}%"
    if req.experience:
        conditions.append("experience_year LIKE :experience")
        params["experience"] = f"%{req.experience}%"
    if req.industry:
        conditions.append("job_classification LIKE :industry")
        params["industry"] = f"%{req.industry}%"

    return execute_query(
        f"""
        SELECT title, job_city, city, education_need, experience_year, job_classification, job_labels, salary_min, salary_max
        FROM biz_job_posting
        WHERE {' AND '.join(conditions)}
        ORDER BY publish_date DESC, id DESC
        LIMIT 4000
        """,
        params,
    )


def _skill_stats(rows: List[dict], user_skills: List[str]) -> dict:
    normalized_user_skills = normalize_skill_tokens(user_skills)
    if not rows or not normalized_user_skills:
        return {
            "matched_skills": [],
            "missing_skills": [],
            "skill_coverage": 0.0,
            "skill_premium": 0.0,
        }

    family = infer_skill_family(normalized_user_skills)
    pool_skill_counter = {}
    matched_rows = []
    for row in rows:
        row_skills = normalize_job_labels(row.get("job_labels"))
        if family:
            row_skills = normalize_skill_tokens(row_skills)
        row_skill_set = {skill.lower() for skill in row_skills}
        for skill in row_skills:
            pool_skill_counter[skill] = pool_skill_counter.get(skill, 0) + 1
        overlap = [skill for skill in normalized_user_skills if skill.lower() in row_skill_set]
        if overlap:
            matched_rows.append(((float(row.get("salary_min") or 0) + float(row.get("salary_max") or row.get("salary_min") or 0)) / 2, len(overlap)))

    high_freq = [skill for skill, _ in sorted(pool_skill_counter.items(), key=lambda item: (-item[1], item[0]))[:12]]
    matched_skills = [skill for skill in high_freq if skill.lower() in {item.lower() for item in normalized_user_skills}]
    missing_skills = [skill for skill in high_freq if skill.lower() not in {item.lower() for item in normalized_user_skills}]

    skill_premium = 0.0
    if matched_rows:
        weighted = np.array([row[0] for row in matched_rows], dtype=float)
        skill_premium = float(np.mean(weighted))

    return {
        "matched_skills": matched_skills[:8],
        "missing_skills": missing_skills[:8],
        "skill_coverage": round(len(matched_skills) / max(len(high_freq), 1) * 100, 1) if high_freq else 0.0,
        "skill_premium": skill_premium,
    }


def _build_summary(req: SalaryPredictRequest, predicted_median: float, confidence: float, matched_skills: List[str], sample_count: int) -> str:
    parts = []
    if req.city:
        parts.append(f"{req.city} 市场")
    if req.industry:
        parts.append(f"{req.industry} 行业")
    scene = "、".join(parts) if parts else "当前目标市场"
    skill_hint = f"，技能命中 {', '.join(matched_skills[:3])}" if matched_skills else ""
    return f"{scene} 下的预测中位薪资约为 {round(predicted_median)}K，样本量 {sample_count}，置信度 {_confidence_label(confidence)}{skill_hint}。"


def _build_factor_cards(req: SalaryPredictRequest, predicted_median: float, pool_p50: float, skill_stats: dict, rows: List[dict]) -> List[dict]:
    cards: List[dict] = []
    if req.city:
        cards.append({
            "label": "城市基线",
            "detail": f"{req.city} 对应样本池中位数约 {round(pool_p50)}K，决定了区间底座。"
        })
    if req.education:
        cards.append({
            "label": "学历影响",
            "detail": f"{req.education} 对应的经验市场溢价约 {_education_impact(req.education):+.1f}K，影响更多体现在上沿。"
        })
    exp_years = _experience_years(req.experience)
    cards.append({
        "label": "经验阶段",
        "detail": f"当前按约 {exp_years:g} 年经验估计，经验越接近岗位主流区间，预测越稳定。"
    })
    if skill_stats["matched_skills"]:
        cards.append({
            "label": "技能溢价",
            "detail": f"样本池中已命中 {', '.join(skill_stats['matched_skills'][:4])}，对中位预测有直接支撑。"
        })
    elif req.skills:
        cards.append({
            "label": "技能匹配",
            "detail": "当前输入技能与样本池高频要求重合偏少，薪资结果会更多依赖城市和经验信号。"
        })
    if predicted_median > pool_p50 + 2:
        cards.append({
            "label": "模型抬升",
            "detail": "模型判断你的画像高于基础样本池中位，说明技能或组合条件带来额外上浮空间。"
        })
    return cards[:4]


def _build_benchmarks(pool_p25: float, pool_p50: float, pool_p75: float, predicted_min: float, predicted_max: float, sample_count: int) -> List[dict]:
    return [
        {"label": "市场 25 分位", "value": f"{round(pool_p25)}K"},
        {"label": "市场中位", "value": f"{round(pool_p50)}K"},
        {"label": "市场 75 分位", "value": f"{round(pool_p75)}K"},
        {"label": "当前预测区间", "value": _format_range(predicted_min, predicted_max)},
        {"label": "有效样本", "value": str(sample_count)},
    ]


@router.post("/predict")
def predict_salary(req: SalaryPredictRequest):
    rows = _rows_by_conditions(req)
    model_result = predict_with_model(
        city=req.city,
        education=req.education,
        experience=req.experience,
        industry=req.industry,
        skills=req.skills,
    ) or {}

    model_prediction = model_result.get("predicted_median")
    model_p25 = model_result.get("p25")
    model_p75 = model_result.get("p75")
    feature_importance = [
        FeatureImportanceItem(feature=item["feature"], importance=item["importance"])
        for item in (model_result.get("feature_importance") or [])
    ]
    cv_mae_mean = model_result.get("cv_mae_mean")
    cv_mae_std = model_result.get("cv_mae_std")

    if not rows:
        if model_prediction is None:
            return {
                "predicted_min": None,
                "predicted_max": None,
                "predicted_median": None,
                "confidence": 0.0,
                "sample_count": 0,
                "percentile_25": None,
                "percentile_75": None,
                "main_factors": [],
                "quantile_p25": model_p25,
                "quantile_p75": model_p75,
                "feature_importance": feature_importance,
                "cv_mae_mean": cv_mae_mean,
                "cv_mae_std": cv_mae_std,
                "quantile_model_available": model_p25 is not None,
                "range": "暂无结果",
                "median": "--",
                "summary": "当前样本不足，无法形成稳定薪资预测。",
                "factors": [{"label": "样本不足", "detail": "城市、经验或行业条件过窄，暂时没有足够样本。"}],
                "benchmarks": [{"label": "有效样本", "value": "0"}],
                "matchedSkills": [],
                "missingSkills": normalize_skill_tokens(req.skills)[:6],
                "salaryScorecard": [],
            }

        predicted_median = round(model_prediction, 2)
        predicted_min = round(model_p25 or model_prediction * 0.88, 2)
        predicted_max = round(model_p75 or model_prediction * 1.12, 2)
        confidence = 0.45
        return {
            "predicted_min": predicted_min,
            "predicted_max": predicted_max,
            "predicted_median": predicted_median,
            "confidence": confidence,
            "sample_count": 0,
            "percentile_25": predicted_min,
            "percentile_75": predicted_max,
            "main_factors": [SalaryFactor(factor="model_only", impact=predicted_median)],
            "quantile_p25": model_p25,
            "quantile_p75": model_p75,
            "feature_importance": feature_importance,
            "cv_mae_mean": cv_mae_mean,
            "cv_mae_std": cv_mae_std,
            "quantile_model_available": model_p25 is not None,
            "range": _format_range(predicted_min, predicted_max),
            "median": f"{round(predicted_median)}K",
            "summary": f"当前主要基于模型画像估计，中位薪资约 {round(predicted_median)}K，但缺少同条件市场样本支撑。",
            "factors": [{"label": "模型估计", "detail": "当前结果更多来自训练模型，而不是同条件市场样本池。"}],
            "benchmarks": [{"label": "预测区间", "value": _format_range(predicted_min, predicted_max)}],
            "matchedSkills": normalize_skill_tokens(req.skills)[:6],
            "missingSkills": [],
            "salaryScorecard": [
                {"label": "样本稳定性", "score": 35},
                {"label": "模型置信", "score": 45},
            ],
        }

    mins = np.array([float(r["salary_min"]) for r in rows], dtype=float)
    maxs = np.array([float(r["salary_max"] or r["salary_min"]) for r in rows], dtype=float)
    salary_pool = (mins + maxs) / 2
    pool_p25 = float(np.percentile(salary_pool, 25))
    pool_p50 = float(np.percentile(salary_pool, 50))
    pool_p75 = float(np.percentile(salary_pool, 75))

    skill_stats = _skill_stats(rows, req.skills)
    exp_years = _experience_years(req.experience)
    education_bonus = _education_impact(req.education)
    skill_bonus = 0.0
    if skill_stats["skill_premium"] > 0:
        skill_bonus = (skill_stats["skill_premium"] - pool_p50) * 0.18

    base_prediction = model_prediction if model_prediction is not None else pool_p50
    blended_prediction = (base_prediction * 0.62) + (pool_p50 * 0.28) + (skill_bonus + education_bonus + exp_years * 0.35)
    predicted_median = round(float(np.clip(blended_prediction, pool_p25 * 0.85, pool_p75 * 1.25)), 2)

    spread = max(2.0, (pool_p75 - pool_p25) * 0.45)
    predicted_min = round(max(pool_p25 * 0.85, predicted_median - spread), 2)
    predicted_max = round(min(pool_p75 * 1.2, predicted_median + spread), 2)
    if model_p25 is not None:
        predicted_min = round((predicted_min + float(model_p25)) / 2, 2)
    if model_p75 is not None:
        predicted_max = round((predicted_max + float(model_p75)) / 2, 2)

    sample_score = min(1.0, len(rows) / 180)
    skill_score = min(1.0, (skill_stats["skill_coverage"] or 0) / 60)
    confidence = round(min(0.93, 0.28 + sample_score * 0.42 + skill_score * 0.18 + (0.1 if model_prediction is not None else 0.0)), 2)

    factor_cards = _build_factor_cards(req, predicted_median, pool_p50, skill_stats, rows)
    benchmarks = _build_benchmarks(pool_p25, pool_p50, pool_p75, predicted_min, predicted_max, len(rows))

    main_factors = [
        SalaryFactor(factor="market_median", impact=round(pool_p50, 2)),
        SalaryFactor(factor="education_bonus", impact=round(education_bonus, 2)),
        SalaryFactor(factor="skill_bonus", impact=round(skill_bonus, 2)),
    ]
    if model_prediction is not None:
        main_factors.append(SalaryFactor(factor="model_adjustment", impact=round(model_prediction - pool_p50, 2)))

    salary_scorecard = [
        {"label": "样本稳定性", "score": round(min(100, len(rows) / 2.2), 1)},
        {"label": "技能贴合", "score": round(skill_stats["skill_coverage"], 1)},
        {"label": "经验合理性", "score": round(min(100, 60 + exp_years * 8), 1)},
        {"label": "区间可信度", "score": round(confidence * 100, 1)},
    ]

    return {
        "predicted_min": predicted_min,
        "predicted_max": predicted_max,
        "predicted_median": predicted_median,
        "confidence": confidence,
        "sample_count": len(rows),
        "percentile_25": round(pool_p25, 2),
        "percentile_75": round(pool_p75, 2),
        "main_factors": main_factors,
        "quantile_p25": model_p25,
        "quantile_p75": model_p75,
        "feature_importance": feature_importance,
        "cv_mae_mean": cv_mae_mean,
        "cv_mae_std": cv_mae_std,
        "quantile_model_available": model_p25 is not None,
        "range": _format_range(predicted_min, predicted_max),
        "median": f"{round(predicted_median)}K",
        "confidenceLabel": _confidence_label(confidence),
        "summary": _build_summary(req, predicted_median, confidence, skill_stats["matched_skills"], len(rows)),
        "factors": factor_cards,
        "benchmarks": benchmarks,
        "matchedSkills": skill_stats["matched_skills"],
        "missingSkills": skill_stats["missing_skills"],
        "salaryScorecard": salary_scorecard,
        "marketSnapshot": {
            "city": req.city,
            "industry": req.industry,
            "experienceYears": exp_years,
            "poolMedian": round(pool_p50, 2),
            "poolP25": round(pool_p25, 2),
            "poolP75": round(pool_p75, 2),
        },
    }


class TrainResponse(BaseModel):
    model_path: str = ""
    sample_count: int = 0
    feature_count: int = 0
    top_skill_count: int = 0
    mae: float = 0.0
    rmse: float = 0.0
    cv_mae_mean: float = 0.0
    cv_mae_std: float = 0.0
    feature_importance: list = []
    quantile_model: bool = False
    message: str = ""


@router.post("/train", response_model=TrainResponse)
def train_model(limit: int = 12000):
    try:
        from app.ml.salary_model import train_and_save

        result = train_and_save(limit=limit)
        return TrainResponse(
            model_path=result.get("model_path", ""),
            sample_count=result.get("sample_count", 0),
            feature_count=result.get("feature_count", 0),
            top_skill_count=result.get("top_skill_count", 0),
            mae=result.get("mae", 0.0),
            rmse=result.get("rmse", 0.0),
            cv_mae_mean=result.get("cv_mae_mean", 0.0),
            cv_mae_std=result.get("cv_mae_std", 0.0),
            feature_importance=result.get("feature_importance", []),
            quantile_model=result.get("quantile_model", False),
            message="Model trained successfully with CV and quantile regression",
        )
    except Exception as exc:
        return TrainResponse(message=f"Training failed: {exc}")
