from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Sequence
import pickle
import re

import numpy as np

try:
    from xgboost import XGBRegressor
except ImportError:  # pragma: no cover - optional at runtime
    XGBRegressor = None

try:
    import lightgbm as lgb
    _LGBM_AVAILABLE = True
except ImportError:  # pragma: no cover
    _LGBM_AVAILABLE = False

try:
    from sklearn.model_selection import KFold
    from sklearn.metrics import mean_absolute_error
    _SKLEARN_AVAILABLE = True
except ImportError:  # pragma: no cover
    _SKLEARN_AVAILABLE = False

from app.config import get_settings


MODEL_FILE_NAME = "salary_xgb.pkl"
TOP_SKILL_LIMIT = 64
EDUCATION_ORDER = {
    "不限": 0,
    "高中": 1,
    "中专": 1,
    "大专": 2,
    "本科": 3,
    "硕士": 4,
    "博士": 5,
}


@dataclass
class SalaryModelBundle:
    model: object
    top_skills: list[str]
    city_vocab: dict[str, int]
    industry_vocab: dict[str, int]
    # 新增字段：交叉验证指标 + 特征重要度 + 分位数模型
    cv_mae_mean: float = 0.0
    cv_mae_std: float = 0.0
    feature_importance: list[dict] = None  # [{"feature": ..., "importance": ...}]
    quantile_model_p25: object = None  # LightGBM P25
    quantile_model_p75: object = None  # LightGBM P75
    feature_names: list[str] = None


def model_path() -> Path:
    settings = get_settings()
    return Path(__file__).resolve().parents[2] / settings.model_dir / MODEL_FILE_NAME


def ensure_model_dir() -> Path:
    path = model_path().parent
    path.mkdir(parents=True, exist_ok=True)
    return path


def normalize_text(value: str | None) -> str:
    return (value or "").strip().lower()


def education_to_ordinal(value: str | None) -> int:
    text = value or ""
    for key, ordinal in EDUCATION_ORDER.items():
        if key in text:
            return ordinal
    return 2


def experience_to_years(value: str | None) -> float:
    text = value or ""
    numbers = [int(num) for num in re.findall(r"\d+", text)]
    if not numbers:
        if "不限" in text or "应届" in text or "在校" in text:
            return 0.0
        return 1.0
    if len(numbers) >= 2:
        return float(sum(numbers[:2]) / 2)
    return float(numbers[0])


def extract_skill_list(value: str | None) -> list[str]:
    if not value:
        return []
    return [part.strip() for part in value.split(",") if part.strip()]


def build_vocabulary(values: Iterable[str], limit: int | None = None) -> dict[str, int]:
    ordered: dict[str, int] = {}
    for value in values:
        key = normalize_text(value)
        if not key or key in ordered:
            continue
        ordered[key] = len(ordered) + 1
        if limit is not None and len(ordered) >= limit:
            break
    return ordered


def build_top_skills(rows: Sequence[dict], limit: int) -> list[str]:
    counts: dict[str, int] = {}
    for row in rows:
        for skill in extract_skill_list(row.get("skill_list")):
            key = normalize_text(skill)
            if not key:
                continue
            counts[key] = counts.get(key, 0) + 1
    ranked = sorted(counts.items(), key=lambda item: (-item[1], item[0]))
    return [skill for skill, _ in ranked[:limit]]


def _build_feature_names(top_skills: list[str]) -> list[str]:
    """构建特征名称列表（用于特征重要度展示）"""
    base = ["city_code", "industry_code", "education_ordinal", "experience_years", "salary_min_raw", "salary_max_raw"]
    return base + [f"skill:{s}" for s in top_skills]


def vectorize_row(
    row: dict,
    top_skills: Sequence[str],
    city_vocab: dict[str, int],
    industry_vocab: dict[str, int],
) -> list[float]:
    city = city_vocab.get(normalize_text(row.get("city")), 0)
    industry = industry_vocab.get(normalize_text(row.get("industry_name")), 0)
    education = education_to_ordinal(row.get("education"))
    experience = experience_to_years(row.get("experience"))
    salary_min = float(row.get("salary_min") or 0)
    salary_max = float(row.get("salary_max") or salary_min)
    skill_set = {normalize_text(skill) for skill in extract_skill_list(row.get("skill_list"))}

    vector = [float(city), float(industry), float(education), float(experience), salary_min, salary_max]
    vector.extend(1.0 if skill in skill_set else 0.0 for skill in top_skills)
    return vector


def training_rows(limit: int = 12000) -> list[dict]:
    from app.db import execute_query

    return execute_query(
        """
        SELECT
            jp.id,
            jp.job_city AS city,
            jp.job_classification AS industry_name,
            jp.education_need AS education,
            jp.experience_year AS experience,
            jp.salary_min,
            jp.salary_max,
            (COALESCE(jp.salary_min, 0) + COALESCE(NULLIF(jp.salary_max, 0), jp.salary_min)) / 2 AS target_salary,
            GROUP_CONCAT(DISTINCT s.skill_name ORDER BY s.skill_name SEPARATOR ',') AS skill_list
        FROM biz_job_posting jp
        LEFT JOIN biz_job_skill js ON jp.id = js.job_id
        LEFT JOIN biz_skill s ON js.skill_id = s.id
        WHERE jp.salary_min IS NOT NULL
          AND jp.salary_min > 0
        GROUP BY jp.id, jp.job_city, jp.job_classification, jp.education_need, jp.experience_year, jp.salary_min, jp.salary_max
        HAVING target_salary IS NOT NULL
        ORDER BY jp.publish_date DESC
        LIMIT :limit
        """,
        {"limit": limit},
    )


def build_feature_bundle(rows: Sequence[dict]) -> tuple[np.ndarray, np.ndarray, SalaryModelBundle]:
    if XGBRegressor is None:
        raise RuntimeError("xgboost is required for salary model training")

    top_skills = build_top_skills(rows, limit=TOP_SKILL_LIMIT)
    city_vocab = build_vocabulary(row.get("city") for row in rows)
    industry_vocab = build_vocabulary(row.get("industry_name") for row in rows)
    feature_names = _build_feature_names(top_skills)

    features = [vectorize_row(row, top_skills, city_vocab, industry_vocab) for row in rows]
    targets = np.array([float(row["target_salary"]) for row in rows], dtype=float)
    bundle = SalaryModelBundle(
        model=XGBRegressor(
            n_estimators=240,
            max_depth=6,
            learning_rate=0.05,
            subsample=0.9,
            colsample_bytree=0.8,
            objective="reg:squarederror",
            random_state=42,
            n_jobs=4,
        ),
        top_skills=top_skills,
        city_vocab=city_vocab,
        industry_vocab=industry_vocab,
        feature_names=feature_names,
    )
    return np.array(features, dtype=float), targets, bundle


def _cross_validate(x: np.ndarray, y: np.ndarray, model) -> tuple[float, float]:
    """5-Fold 交叉验证，返回 (cv_mae_mean, cv_mae_std)"""
    if not _SKLEARN_AVAILABLE:
        return 0.0, 0.0
    kf = KFold(n_splits=5, shuffle=True, random_state=42)
    cv_scores = []
    for train_idx, val_idx in kf.split(x):
        # 每折使用相同超参数的新模型
        fold_model = XGBRegressor(
            n_estimators=240,
            max_depth=6,
            learning_rate=0.05,
            subsample=0.9,
            colsample_bytree=0.8,
            objective="reg:squarederror",
            random_state=42,
            n_jobs=4,
        )
        fold_model.fit(x[train_idx], y[train_idx])
        pred = fold_model.predict(x[val_idx])
        cv_scores.append(mean_absolute_error(y[val_idx], pred))
    scores = np.array(cv_scores)
    return float(scores.mean()), float(scores.std())


def _train_quantile_models(
    x: np.ndarray, y: np.ndarray
) -> tuple[object | None, object | None]:
    """使用 LightGBM 分位数回归训练 P25/P75 模型"""
    if not _LGBM_AVAILABLE:
        return None, None
    try:
        model_p25 = lgb.LGBMRegressor(
            objective="quantile", alpha=0.25,
            n_estimators=200, max_depth=6,
            learning_rate=0.05, random_state=42, n_jobs=4,
            verbose=-1,
        )
        model_p75 = lgb.LGBMRegressor(
            objective="quantile", alpha=0.75,
            n_estimators=200, max_depth=6,
            learning_rate=0.05, random_state=42, n_jobs=4,
            verbose=-1,
        )
        model_p25.fit(x, y)
        model_p75.fit(x, y)
        return model_p25, model_p75
    except Exception as e:
        import logging
        logging.getLogger(__name__).warning("LightGBM 分位数模型训练失败: %s", e)
        return None, None


def _extract_feature_importance(model, feature_names: list[str], top_k: int = 10) -> list[dict]:
    """提取 XGBoost 特征重要度，返回 top-k 列表"""
    try:
        importances = model.feature_importances_
        if feature_names and len(importances) == len(feature_names):
            pairs = sorted(
                zip(feature_names, importances.tolist()),
                key=lambda t: -t[1]
            )
            total = sum(imp for _, imp in pairs) or 1.0
            return [
                {"feature": name, "importance": round(imp / total, 4)}
                for name, imp in pairs[:top_k]
            ]
    except Exception:
        pass
    return []


def train_and_save(limit: int = 12000) -> dict:
    if XGBRegressor is None:
        raise RuntimeError("xgboost is not installed")

    rows = training_rows(limit=limit)
    if len(rows) < 100:
        raise ValueError("not enough training rows")

    x, y, bundle = build_feature_bundle(rows)

    # 1. 5-Fold 交叉验证（防过拟合，提供泛化误差估计）
    cv_mae_mean, cv_mae_std = _cross_validate(x, y, bundle.model)

    # 2. 全量训练最终模型
    bundle.model.fit(x, y)

    # 训练集指标
    predictions = bundle.model.predict(x)
    mae = float(np.mean(np.abs(predictions - y)))
    rmse = float(np.sqrt(np.mean(np.square(predictions - y))))

    # 3. 特征重要度
    feature_importance = _extract_feature_importance(bundle.model, bundle.feature_names)

    # 4. LightGBM 分位数回归模型（P25/P75 置信区间）
    model_p25, model_p75 = _train_quantile_models(x, y)

    # 更新 bundle
    bundle.cv_mae_mean = cv_mae_mean
    bundle.cv_mae_std = cv_mae_std
    bundle.feature_importance = feature_importance
    bundle.quantile_model_p25 = model_p25
    bundle.quantile_model_p75 = model_p75

    ensure_model_dir()
    with model_path().open("wb") as fp:
        pickle.dump(bundle, fp)

    return {
        "model_path": str(model_path()),
        "sample_count": len(rows),
        "feature_count": int(x.shape[1]),
        "top_skill_count": len(bundle.top_skills),
        "mae": round(mae, 4),
        "rmse": round(rmse, 4),
        "cv_mae_mean": round(cv_mae_mean, 4),
        "cv_mae_std": round(cv_mae_std, 4),
        "feature_importance": feature_importance,
        "quantile_model": model_p25 is not None,
    }


def load_bundle() -> SalaryModelBundle | None:
    path = model_path()
    if not path.exists():
        return None
    with path.open("rb") as fp:
        return pickle.load(fp)


def predict_with_model(
    city: str | None,
    education: str | None,
    experience: str | None,
    industry: str | None,
    skills: Sequence[str] | None,
) -> dict | None:
    """
    返回预测结果 dict，包含：
      predicted_median, p25, p75（如有分位数模型），feature_importance
    旧版调用方可取 predicted_median 字段作为 float 使用。
    """
    bundle = load_bundle()
    if bundle is None:
        return None

    row = {
        "city": city,
        "education": education,
        "experience": experience,
        "industry_name": industry,
        "salary_min": 0,
        "salary_max": 0,
        "skill_list": ",".join(skills or []),
    }
    x = np.array([
        vectorize_row(row, bundle.top_skills, bundle.city_vocab, bundle.industry_vocab)
    ], dtype=float)

    median = round(float(max(bundle.model.predict(x)[0], 0.0)), 2)
    result: dict = {"predicted_median": median}

    # 分位数区间
    p25, p75 = None, None
    if bundle.quantile_model_p25 is not None:
        try:
            p25 = round(float(max(bundle.quantile_model_p25.predict(x)[0], 0.0)), 2)
        except Exception:
            pass
    if bundle.quantile_model_p75 is not None:
        try:
            p75 = round(float(max(bundle.quantile_model_p75.predict(x)[0], 0.0)), 2)
        except Exception:
            pass

    result["p25"] = p25 if p25 is not None else round(median * 0.88, 2)
    result["p75"] = p75 if p75 is not None else round(median * 1.12, 2)
    result["feature_importance"] = getattr(bundle, "feature_importance", []) or []
    result["cv_mae_mean"] = getattr(bundle, "cv_mae_mean", 0.0)
    result["cv_mae_std"] = getattr(bundle, "cv_mae_std", 0.0)

    return result


def predict_median_salary(
    city: str | None,
    education: str | None,
    experience: str | None,
    industry: str | None,
    skills: Sequence[str] | None,
) -> float | None:
    """向后兼容接口：只返回预测中位数（float）"""
    result = predict_with_model(city, education, experience, industry, skills)
    if result is None:
        return None
    return result.get("predicted_median")
