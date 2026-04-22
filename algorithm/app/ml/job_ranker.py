from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Sequence
import json
import pickle
import re

import numpy as np

try:
    from xgboost import XGBRegressor
except ImportError:  # pragma: no cover
    XGBRegressor = None

try:
    import lightgbm as lgb
except ImportError:  # pragma: no cover
    lgb = None

try:
    from sklearn.ensemble import HistGradientBoostingRegressor
except ImportError:  # pragma: no cover
    HistGradientBoostingRegressor = None

from app.config import get_settings
from app.db import execute_query
from app.skill_normalizer import normalize_job_labels, normalize_profile_skills


MODEL_FILE_NAME = "job_ranker_xgb.pkl"
FEATURE_NAMES = [
    "family_match",
    "skill_match",
    "core_skill_match",
    "title_match",
    "domain_match",
    "intent_match",
    "seniority_match",
    "recall_strength",
    "favorite_match",
    "location_match",
    "company_match",
    "salary_match",
    "education_match",
    "experience_match",
    "industry_match",
    "freshness",
    "core_skill_hits",
    "skill_hits",
]


@dataclass
class JobRankerBundle:
    model: object
    feature_names: list[str]
    sample_count: int = 0
    source: str = "historical_recommendations"
    model_type: str = "unknown"


def model_path() -> Path:
    settings = get_settings()
    return Path(__file__).resolve().parents[2] / settings.model_dir / MODEL_FILE_NAME


def ensure_model_dir() -> Path:
    path = model_path().parent
    path.mkdir(parents=True, exist_ok=True)
    return path


def _table_exists(table_name: str) -> bool:
    settings = get_settings()
    rows = execute_query(
        """
        SELECT COUNT(*) AS cnt
        FROM information_schema.tables
        WHERE table_schema = :schema_name
          AND table_name = :table_name
        """,
        {"schema_name": settings.mysql_database, "table_name": table_name},
    )
    return bool(rows and int(rows[0].get("cnt") or 0) > 0)


def load_bundle() -> JobRankerBundle | None:
    path = model_path()
    if not path.exists():
        return None
    with path.open("rb") as fp:
        return pickle.load(fp)


def vectorize_feature_map(feature_map: dict, feature_names: Sequence[str] | None = None) -> list[float]:
    names = list(feature_names or FEATURE_NAMES)
    return [float(feature_map.get(name, 0.0) or 0.0) for name in names]


def score_feature_maps(feature_maps: Sequence[dict], fallback_scores: Sequence[float]) -> tuple[list[float], str]:
    bundle = load_bundle()
    if bundle is None:
        return list(fallback_scores), "industrial-heuristic-ranker"

    model_feature_names = list(bundle.feature_names or FEATURE_NAMES)
    try:
        x = np.array([vectorize_feature_map(item, model_feature_names) for item in feature_maps], dtype=float)
        predictions = bundle.model.predict(x)
        scores = [round(float(max(pred, 0.0)), 6) for pred in predictions]
        feature_suffix = "v2" if len(model_feature_names) >= len(FEATURE_NAMES) else "legacy"
        return scores, f"{bundle.model_type}-{feature_suffix}-ranker"
    except Exception:
        return list(fallback_scores), "industrial-heuristic-ranker"


def _build_regressor():
    if XGBRegressor is not None:
        return (
            XGBRegressor(
                n_estimators=240,
                max_depth=5,
                learning_rate=0.05,
                subsample=0.85,
                colsample_bytree=0.85,
                objective="reg:squarederror",
                random_state=42,
                n_jobs=4,
            ),
            "xgboost",
        )
    if lgb is not None:
        return (
            lgb.LGBMRegressor(
                n_estimators=240,
                max_depth=5,
                learning_rate=0.05,
                subsample=0.85,
                colsample_bytree=0.85,
                random_state=42,
                n_jobs=4,
                verbose=-1,
            ),
            "lightgbm",
        )
    if HistGradientBoostingRegressor is not None:
        return (
            HistGradientBoostingRegressor(
                max_depth=5,
                learning_rate=0.05,
                max_iter=240,
                random_state=42,
            ),
            "sklearn-hgbt",
        )
    raise RuntimeError("no supported gradient boosting model is available")


def _experience_to_years(exp_str: str | None) -> float:
    text = exp_str or ""
    if "不限" in text or "应届" in text or "在校" in text:
        return 0.0
    nums = [int(n) for n in re.findall(r"\d+", text)]
    if not nums:
        return 1.0
    return float(sum(nums[:2]) / len(nums[:2]))


def _edu_level(edu: str | None) -> int:
    mapping = {"初中": 1, "高中": 2, "中专": 2, "大专": 3, "本科": 4, "硕士": 5, "博士": 6}
    text = edu or ""
    for key, val in mapping.items():
        if key in text:
            return val
    return 3


def historical_training_rows(limit: int = 20000) -> list[dict]:
    favorite_join = ""
    favorite_field = "0 AS is_favorited"
    if _table_exists("biz_job_favorite"):
        favorite_join = """
        LEFT JOIN biz_job_favorite fav
            ON fav.user_id = rr.user_id
           AND fav.job_id = rr.job_posting_id
        """
        favorite_field = "CASE WHEN fav.id IS NULL THEN 0 ELSE 1 END AS is_favorited"

    return execute_query(
        f"""
        SELECT
            rr.match_score,
            rr.is_viewed,
            {favorite_field},
            rr.rank_no,
            rr.created_at,
            pref.salary_min AS pref_salary_min,
            pref.salary_max AS pref_salary_max,
            pref.company_industry AS pref_industry,
            pref.company_size AS pref_company_size,
            pref.company_finance AS pref_company_finance,
            COALESCE(city.region_name, '') AS pref_city_name,
            jp.salary_min,
            jp.salary_max,
            jp.education_need AS education,
            jp.experience_year AS experience,
            COALESCE(jp.industry_name, jp.job_classification) AS industry_name,
            COALESCE(jp.city, jp.job_city) AS city,
            jp.company_size,
            jp.company_finance,
            jp.publish_date
        FROM biz_recommendation_result rr
        JOIN biz_job_posting jp ON jp.id = rr.job_posting_id
        LEFT JOIN user_report_preference pref ON pref.id = rr.preference_id
        LEFT JOIN dim_region city ON city.region_code = pref.city_code
        {favorite_join}
        ORDER BY rr.created_at DESC
        LIMIT :limit
        """,
        {"limit": limit},
    )


def favorite_training_rows(limit: int = 12000) -> list[dict]:
    if not _table_exists("biz_job_favorite"):
        return []
    return execute_query(
        """
        SELECT
            f.user_id,
            f.job_id,
            f.created_at,
            up.skills AS profile_skills,
            up.profile_summary,
            up.education_level AS pref_education,
            up.expected_salary_min AS pref_salary_min,
            up.expected_salary_max AS pref_salary_max,
            COALESCE(city.region_name, '') AS pref_city_name,
            jp.title,
            COALESCE(jp.description, jp.position_info) AS description,
            COALESCE(jp.industry_name, jp.job_classification) AS industry_name,
            COALESCE(jp.city, jp.job_city) AS city,
            jp.company_size,
            jp.company_finance,
            jp.education_need AS education,
            jp.experience_year AS experience,
            jp.salary_min,
            jp.salary_max,
            jp.publish_date,
            jp.job_labels
        FROM biz_job_favorite f
        JOIN biz_job_posting jp ON jp.id = f.job_id
        LEFT JOIN user_profile up ON up.user_id = f.user_id
        LEFT JOIN dim_region city ON city.region_code = up.target_city_code
        ORDER BY f.created_at DESC
        LIMIT :limit
        """,
        {"limit": limit},
    )


def cold_start_training_rows(limit: int = 6000) -> list[dict]:
    return execute_query(
        """
        SELECT
            jp.id,
            jp.title,
            COALESCE(jp.description, jp.position_info) AS description,
            COALESCE(jp.industry_name, jp.job_classification) AS industry_name,
            COALESCE(jp.city, jp.job_city) AS city,
            jp.company_size,
            jp.company_finance,
            jp.education_need AS education,
            jp.experience_year AS experience,
            jp.salary_min,
            jp.salary_max,
            jp.publish_date,
            jp.job_labels
        FROM biz_job_posting jp
        WHERE jp.salary_min IS NOT NULL
        ORDER BY jp.publish_date DESC
        LIMIT :limit
        """,
        {"limit": limit},
    )


def _infer_family_from_text(text: str) -> str:
    normalized = (text or "").lower()
    if any(token in normalized for token in ["java", "spring", "backend", "后端", "服务端", "golang", "微服务"]):
        return "backend"
    if any(token in normalized for token in ["vue", "react", "frontend", "前端", "javascript", "typescript"]):
        return "frontend"
    if any(token in normalized for token in ["python", "data", "analysis", "analyst", "数据", "sql", "bi"]):
        return "data"
    if any(token in normalized for token in ["test", "qa", "测试", "自动化测试"]):
        return "qa"
    return "general"


def _family_skills(family: str) -> list[str]:
    mapping = {
        "backend": ["java", "spring", "mysql", "redis", "docker"],
        "frontend": ["vue", "react", "javascript", "typescript", "css"],
        "data": ["python", "sql", "pandas", "numpy", "bi"],
        "qa": ["test", "qa", "selenium", "jmeter", "postman"],
    }
    return mapping.get(family, [])


def _parse_job_labels(raw) -> list[str]:
    return [item.lower() for item in normalize_job_labels(raw)]


def _parse_profile_skills(raw) -> list[str]:
    return [item.lower() for item in normalize_profile_skills(raw)]


def _freshness_from_publish_date(value) -> float:
    if value is None:
        return 0.3
    try:
        from datetime import date, datetime

        if isinstance(value, datetime):
            publish_date = value.date()
        elif isinstance(value, date):
            publish_date = value
        else:
            return 0.3
        days_ago = max(0, (date.today() - publish_date).days)
        return max(0.0, 1.0 - days_ago / 90.0)
    except Exception:
        return 0.3


def _build_training_feature(row: dict) -> dict:
    location_match = 1.0 if row.get("pref_city_name") and row.get("pref_city_name") in (row.get("city") or "") else 0.0

    company_match = 0.0
    if row.get("pref_company_size") and row.get("pref_company_size") in (row.get("company_size") or ""):
        company_match += 0.6
    if row.get("pref_company_finance") and row.get("pref_company_finance") in (row.get("company_finance") or ""):
        company_match += 0.4
    company_match = min(company_match, 1.0)

    salary_match = 0.5
    if row.get("pref_salary_min") and row.get("salary_min"):
        job_mid = (float(row.get("salary_min") or 0) + float(row.get("salary_max") or row.get("salary_min") or 0)) / 2
        pref_mid = (float(row.get("pref_salary_min") or 0) + float(row.get("pref_salary_max") or row.get("pref_salary_min") or 0)) / 2
        if pref_mid > 0:
            salary_match = max(0.0, 1.0 - abs(job_mid - pref_mid) / pref_mid)

    industry_match = 0.3
    pref_industry = (row.get("pref_industry") or "").lower()
    industry_name = (row.get("industry_name") or "").lower()
    if pref_industry and industry_name:
        if pref_industry in industry_name:
            industry_match = 1.0
        elif any(token in industry_name for token in pref_industry.split()):
            industry_match = 0.6

    education_match = 1.0 if _edu_level(row.get("education")) <= 4 else 0.7
    exp_years = _experience_to_years(row.get("experience"))
    experience_match = max(0.0, 1.0 - abs(exp_years - 3.0) * 0.15)

    return {
        "family_match": 0.0,
        "skill_match": float(row.get("match_score") or 0) / 100.0,
        "core_skill_match": 0.0,
        "title_match": 0.0,
        "domain_match": 0.0,
        "intent_match": 0.0,
        "seniority_match": experience_match,
        "recall_strength": 0.0,
        "favorite_match": 1.0 if int(row.get("is_favorited") or 0) == 1 else 0.0,
        "location_match": location_match,
        "company_match": company_match,
        "salary_match": salary_match,
        "education_match": education_match,
        "experience_match": experience_match,
        "industry_match": industry_match,
        "freshness": 0.5,
        "core_skill_hits": 0.0,
        "skill_hits": 0.0,
    }


def _build_favorite_training_feature(row: dict) -> dict | None:
    profile_skills = _parse_profile_skills(row.get("profile_skills"))
    profile_text = " ".join(
        [
            " ".join(profile_skills),
            str(row.get("profile_summary") or ""),
        ]
    ).strip()
    job_text = " ".join(
        [
            str(row.get("title") or ""),
            str(row.get("description") or ""),
            str(row.get("industry_name") or ""),
        ]
    ).lower()
    job_labels = _parse_job_labels(row.get("job_labels"))
    job_skill_set = set(job_labels)

    family = _infer_family_from_text(profile_text)
    if family == "general":
        family = _infer_family_from_text(job_text)
    family_skills = _family_skills(family)

    if profile_skills:
        skill_hits = sum(1 for skill in profile_skills if skill in job_text or skill in job_skill_set)
        skill_match = min(1.0, skill_hits / max(len(profile_skills), 1))
        core_hits = sum(1 for skill in profile_skills[:3] if skill in job_text or skill in job_skill_set)
        core_skill_match = min(1.0, core_hits / max(min(len(profile_skills), 3), 1))
    else:
        skill_hits = sum(1 for skill in family_skills if skill in job_text or skill in job_skill_set)
        skill_match = min(1.0, max(0.35, skill_hits / max(len(family_skills), 1))) if family_skills else 0.35
        core_hits = min(skill_hits, 3)
        core_skill_match = min(1.0, max(0.35, core_hits / 3.0))

    if skill_match <= 0 and family == "general":
        return None

    family_match = 1.0 if family != "general" and any(skill in job_text or skill in job_skill_set for skill in family_skills) else (0.5 if family != "general" else 0.0)
    title_match = family_match if family != "general" else skill_match
    domain_match = max(skill_match, family_match)
    location_match = 1.0 if row.get("pref_city_name") and row.get("pref_city_name") in (row.get("city") or "") else 0.0

    salary_match = 0.5
    if row.get("pref_salary_min") and row.get("salary_min"):
        job_mid = (float(row.get("salary_min") or 0) + float(row.get("salary_max") or row.get("salary_min") or 0)) / 2
        pref_mid = (float(row.get("pref_salary_min") or 0) + float(row.get("pref_salary_max") or row.get("pref_salary_min") or 0)) / 2
        if pref_mid > 0:
            salary_match = max(0.0, 1.0 - abs(job_mid - pref_mid) / pref_mid)

    education_match = 1.0
    pref_edu = row.get("pref_education")
    if pref_edu:
        edu_diff = _edu_level(pref_edu) - _edu_level(row.get("education"))
        education_match = 1.0 if edu_diff >= 0 else max(0.0, 1.0 + edu_diff * 0.3)

    experience_match = max(0.0, 1.0 - abs(_experience_to_years(row.get("experience")) - 3.0) * 0.15)
    industry_match = 0.8 if row.get("industry_name") else 0.4
    freshness = _freshness_from_publish_date(row.get("publish_date"))
    company_match = 1.0 if row.get("company_size") or row.get("company_finance") else 0.3

    return {
        "family_match": family_match,
        "skill_match": skill_match,
        "core_skill_match": core_skill_match,
        "title_match": title_match,
        "domain_match": domain_match,
        "intent_match": max(skill_match, title_match, family_match),
        "seniority_match": experience_match,
        "recall_strength": 0.75,
        "favorite_match": 1.0,
        "location_match": location_match,
        "company_match": company_match,
        "salary_match": salary_match,
        "education_match": education_match,
        "experience_match": experience_match,
        "industry_match": industry_match,
        "freshness": freshness,
        "core_skill_hits": float(core_hits),
        "skill_hits": float(skill_hits),
    }


def _cold_start_training_samples(limit: int = 6000) -> list[tuple[dict, float]]:
    rows = cold_start_training_rows(limit=limit)
    samples: list[tuple[dict, float]] = []
    for row in rows:
        full_text = " ".join(
            [
                str(row.get("title") or ""),
                str(row.get("description") or ""),
                str(row.get("industry_name") or ""),
            ]
        ).lower()
        family = _infer_family_from_text(full_text)
        if family == "general":
            continue

        family_skills = _family_skills(family)
        job_labels = _parse_job_labels(row.get("job_labels"))
        skill_hits = sum(1 for skill in family_skills if skill in full_text or skill in job_labels)
        skill_ratio = skill_hits / max(len(family_skills), 1)
        freshness = _freshness_from_publish_date(row.get("publish_date"))
        industry_match = 0.8 if row.get("industry_name") else 0.4
        education_match = 1.0 if _edu_level(row.get("education")) <= 4 else 0.7
        exp_years = _experience_to_years(row.get("experience"))
        experience_match = max(0.0, 1.0 - abs(exp_years - 3.0) * 0.15)

        positive = {
            "family_match": 1.0,
            "skill_match": max(0.45, skill_ratio),
            "core_skill_match": max(0.4, skill_ratio),
            "title_match": 1.0,
            "domain_match": max(0.5, skill_ratio),
            "intent_match": max(0.55, skill_ratio),
            "seniority_match": experience_match,
            "recall_strength": 0.8,
            "favorite_match": 0.0,
            "location_match": 1.0,
            "company_match": 1.0 if row.get("company_size") or row.get("company_finance") else 0.4,
            "salary_match": 1.0 if row.get("salary_min") else 0.5,
            "education_match": education_match,
            "experience_match": experience_match,
            "industry_match": industry_match,
            "freshness": freshness,
            "core_skill_hits": float(min(skill_hits, 3)),
            "skill_hits": float(skill_hits),
        }
        negative = {
            "family_match": 0.0,
            "skill_match": min(0.08, skill_ratio * 0.2),
            "core_skill_match": 0.0,
            "title_match": 0.0,
            "domain_match": 0.0,
            "intent_match": 0.0,
            "seniority_match": max(0.0, experience_match * 0.5),
            "recall_strength": 0.1,
            "favorite_match": 0.0,
            "location_match": 0.0,
            "company_match": 0.1,
            "salary_match": 0.25,
            "education_match": education_match,
            "experience_match": max(0.0, experience_match * 0.4),
            "industry_match": 0.2,
            "freshness": freshness,
            "core_skill_hits": 0.0,
            "skill_hits": 0.0,
        }
        samples.append((positive, 0.92))
        samples.append((negative, 0.06))
    return samples


def train_and_save(limit: int = 20000) -> dict:
    rows = historical_training_rows(limit=limit)
    favorite_rows = favorite_training_rows(limit=min(max(limit, 4000), 12000))
    samples: list[tuple[dict, float]] = []
    source = "historical_behavior"
    for row in rows:
        target = (
            (float(row.get("match_score") or 0) / 100.0)
            + (0.20 if int(row.get("is_viewed") or 0) == 1 else 0.0)
            + (0.55 if int(row.get("is_favorited") or 0) == 1 else 0.0)
        )
        samples.append((
            _build_training_feature(row),
            min(1.5, target),
        ))

    favorite_sample_count = 0
    for row in favorite_rows:
        feature_map = _build_favorite_training_feature(row)
        if feature_map is None:
            continue
        samples.append((feature_map, 1.2))
        favorite_sample_count += 1

    if len(samples) < 200:
        cold_start_samples = _cold_start_training_samples(limit=min(max(limit, 2000), 8000))
        samples.extend(cold_start_samples)
        source = "mixed_behavior_cold_start" if (rows or favorite_sample_count) else "cold_start_bootstrap"

    if len(samples) < 200:
        raise ValueError("not enough ranker training samples")

    x = np.array([vectorize_feature_map(feature_map) for feature_map, _ in samples], dtype=float)
    y = np.array([target for _, target in samples], dtype=float)

    model, model_type = _build_regressor()
    model.fit(x, y)

    bundle = JobRankerBundle(
        model=model,
        feature_names=list(FEATURE_NAMES),
        sample_count=len(samples),
        source=source,
        model_type=model_type,
    )
    ensure_model_dir()
    with model_path().open("wb") as fp:
        pickle.dump(bundle, fp)

    return {
        "model_path": str(model_path()),
        "sample_count": len(samples),
        "historical_sample_count": len(rows),
        "favorite_sample_count": favorite_sample_count,
        "feature_count": len(FEATURE_NAMES),
        "model_type": model_type,
        "source": source,
        "target_mean": round(float(np.mean(y)), 4),
    }
