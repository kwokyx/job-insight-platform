from typing import List, Optional

import numpy as np
from fastapi import APIRouter
from pydantic import BaseModel

from app.db import execute_query
from app.ml.salary_model import predict_with_model, predict_median_salary

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
    # 新增：LightGBM 分位数置信区间
    quantile_p25: Optional[float] = None
    quantile_p75: Optional[float] = None
    # 新增：XGBoost 特征重要度（top-10）
    feature_importance: List[FeatureImportanceItem] = []
    # 新增：交叉验证指标
    cv_mae_mean: Optional[float] = None
    cv_mae_std: Optional[float] = None
    quantile_model_available: bool = False


@router.post("/predict", response_model=SalaryPredictResponse)
def predict_salary(req: SalaryPredictRequest):
    conditions = ["salary_min IS NOT NULL", "salary_min > 0"]
    params = {}

    if req.city:
        conditions.append("job_city LIKE :city")
        params["city"] = f"%{req.city}%"

    if req.education:
        conditions.append("education_need = :education")
        params["education"] = req.education

    if req.experience:
        conditions.append("experience_year LIKE :experience")
        params["experience"] = f"%{req.experience}%"

    if req.industry:
        conditions.append("job_classification LIKE :industry")
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

    # 调用升级后的模型（返回 dict 含 p25/p75/feature_importance）
    model_result = predict_with_model(
        city=req.city,
        education=req.education,
        experience=req.experience,
        industry=req.industry,
        skills=req.skills,
    )
    model_prediction = model_result.get("predicted_median") if model_result else None
    model_p25 = model_result.get("p25") if model_result else None
    model_p75 = model_result.get("p75") if model_result else None
    feat_importance = [
        FeatureImportanceItem(feature=fi["feature"], importance=fi["importance"])
        for fi in (model_result.get("feature_importance") or [])
    ] if model_result else []
    cv_mae_mean = model_result.get("cv_mae_mean") if model_result else None
    cv_mae_std = model_result.get("cv_mae_std") if model_result else None

    if not rows:
        if model_prediction is None:
            return SalaryPredictResponse(confidence=0.0, sample_count=0)
        return SalaryPredictResponse(
            predicted_min=round(model_p25 or model_prediction * 0.85, 2),
            predicted_max=round(model_p75 or model_prediction * 1.15, 2),
            predicted_median=model_prediction,
            confidence=0.55,
            sample_count=0,
            percentile_25=round(model_p25 or model_prediction * 0.9, 2),
            percentile_75=round(model_p75 or model_prediction * 1.1, 2),
            main_factors=[SalaryFactor(factor="xgboost_model", impact=model_prediction)],
            quantile_p25=model_p25,
            quantile_p75=model_p75,
            feature_importance=feat_importance,
            cv_mae_mean=cv_mae_mean,
            cv_mae_std=cv_mae_std,
            quantile_model_available=model_p25 is not None,
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
        quantile_p25=model_p25,
        quantile_p75=model_p75,
        feature_importance=feat_importance,
        cv_mae_mean=cv_mae_mean,
        cv_mae_std=cv_mae_std,
        quantile_model_available=model_p25 is not None,
    )


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
    """
    触发薪资预测模型训练（升级版）
    XGBoost 全量训练 + 5-Fold CV + 特征重要度 + LightGBM 分位数回归
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
            cv_mae_mean=result.get("cv_mae_mean", 0.0),
            cv_mae_std=result.get("cv_mae_std", 0.0),
            feature_importance=result.get("feature_importance", []),
            quantile_model=result.get("quantile_model", False),
            message="Model trained successfully with CV and quantile regression",
        )
    except Exception as e:
        return TrainResponse(message=f"Training failed: {str(e)}")


def _education_impact(edu: str) -> float:
    impact_map = {"大专": -2.0, "本科": 0.0, "硕士": 3.0, "博士": 6.0}
    return impact_map.get(edu, 0.0)
