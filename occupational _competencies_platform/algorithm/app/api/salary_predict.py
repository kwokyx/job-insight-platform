from typing import List, Optional

import numpy as np
from fastapi import APIRouter
from pydantic import BaseModel

from app.db import execute_query
from app.ml.salary_model import predict_with_model

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


class SalaryPredictResponse(BaseModel):
    predicted_min: Optional[float] = None
    predicted_max: Optional[float] = None
    predicted_median: Optional[float] = None
    confidence: float = 0.0
    sample_count: int = 0
    percentile_25: Optional[float] = None
    percentile_75: Optional[float] = None
    main_factors: List[SalaryFactor] = []


@router.post("/predict", response_model=SalaryPredictResponse)
def predict_salary(req: SalaryPredictRequest):
    conditions = ["salary_min IS NOT NULL", "salary_min > 0"]
    params = {}

    if req.city:
        conditions.append("city LIKE :city")
        params["city"] = f"%{req.city}%"

    if req.education:
        conditions.append("education = :education")
        params["education"] = req.education

    if req.experience:
        conditions.append("experience LIKE :experience")
        params["experience"] = f"%{req.experience}%"

    if req.industry:
        conditions.append("industry_name LIKE :industry")
        params["industry"] = f"%{req.industry}%"

    rows = execute_query(
        f"""
        SELECT salary_min, salary_max
        FROM biz_job_posting
        WHERE {' AND '.join(conditions)}
        LIMIT 5000
        """,
        params,
    )

    model_prediction = predict_with_model(
        city=req.city,
        education=req.education,
        experience=req.experience,
        industry=req.industry,
        skills=req.skills,
    )

    if not rows:
        if model_prediction is None:
            return SalaryPredictResponse(confidence=0.0, sample_count=0)
        return SalaryPredictResponse(
            predicted_min=round(model_prediction * 0.85, 2),
            predicted_max=round(model_prediction * 1.15, 2),
            predicted_median=model_prediction,
            confidence=0.55,
            sample_count=0,
            percentile_25=round(model_prediction * 0.9, 2),
            percentile_75=round(model_prediction * 1.1, 2),
            main_factors=[SalaryFactor(factor="xgboost_model", impact=model_prediction)],
        )

    mins = np.array([float(r["salary_min"]) for r in rows], dtype=float)
    maxs = np.array([float(r["salary_max"]) for r in rows if r["salary_max"]], dtype=float)
    if len(maxs) == 0:
        maxs = mins * 1.3

    salary_pool = (mins + maxs) / 2
    p25 = float(np.percentile(salary_pool, 25))
    p50 = float(np.percentile(salary_pool, 50))
    p75 = float(np.percentile(salary_pool, 75))

    factors: list[SalaryFactor] = []
    if req.city:
        city_avg = float(np.mean(salary_pool))
        all_avg_rows = execute_query("SELECT AVG(salary_min) AS avg_sal FROM biz_job_posting WHERE salary_min > 0")
        all_avg = float(all_avg_rows[0]["avg_sal"]) if all_avg_rows else city_avg
        factors.append(SalaryFactor(factor=f"city={req.city}", impact=round(city_avg - all_avg, 2)))

    if req.education:
        factors.append(SalaryFactor(factor=f"education={req.education}", impact=round(_education_impact(req.education), 2)))

    predicted_median = round(model_prediction, 2) if model_prediction is not None else round(p50, 2)
    predicted_min = round(float(np.percentile(mins, 30)), 2)
    predicted_max = round(float(np.percentile(maxs, 70)), 2)
    if model_prediction is not None:
        predicted_min = round(min(predicted_min, predicted_median), 2)
        predicted_max = round(max(predicted_max, predicted_median), 2)
        factors.append(SalaryFactor(factor="xgboost_model", impact=round(model_prediction - p50, 2)))

    confidence = min(0.95, len(rows) / 200)

    return SalaryPredictResponse(
        predicted_min=predicted_min,
        predicted_max=predicted_max,
        predicted_median=predicted_median,
        confidence=round(confidence, 2),
        sample_count=len(rows),
        percentile_25=round(p25, 2),
        percentile_75=round(p75, 2),
        main_factors=factors,
    )


class TrainResponse(BaseModel):
    model_path: str = ""
    sample_count: int = 0
    feature_count: int = 0
    top_skill_count: int = 0
    mae: float = 0.0
    rmse: float = 0.0
    message: str = ""


@router.post("/train", response_model=TrainResponse)
def train_model(limit: int = 12000):
    """
    触发薪资预测模型训练
    从数据库读取训练数据 → 特征工程 → XGBoost → 保存 .pkl
    """
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
            message="Model trained successfully",
        )
    except Exception as e:
        return TrainResponse(message=f"Training failed: {str(e)}")


def _education_impact(edu: str) -> float:
    impact_map = {"大专": -2.0, "本科": 0.0, "硕士": 3.0, "博士": 6.0}
    return impact_map.get(edu, 0.0)
