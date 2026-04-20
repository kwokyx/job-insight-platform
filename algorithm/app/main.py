from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api import (
    market_insights,
    matching,
    resume_parse,
    resume_score,
    salary_predict,
    sentiment,
    skill_evolution,
    skill_graph,
    trend_forecast,
)

app = FastAPI(
    title="Career Platform Algorithm Engine",
    description="Algorithm services for salary prediction, skill graph, matching, sentiment, trend forecast and deep market insights.",
    version="2.1.0",
    docs_url="/docs",
    redoc_url="/redoc",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(salary_predict.router, prefix="/algorithm/salary", tags=["salary"])
app.include_router(skill_graph.router, prefix="/algorithm/skills", tags=["skills"])
app.include_router(skill_evolution.router, prefix="/algorithm/skills", tags=["skills"])
app.include_router(matching.router, prefix="/algorithm/match", tags=["matching"])
app.include_router(trend_forecast.router, prefix="/algorithm/trend", tags=["trend"])
app.include_router(sentiment.router, prefix="/algorithm/sentiment", tags=["sentiment"])
app.include_router(resume_score.router, prefix="/algorithm/resume", tags=["resume"])
app.include_router(resume_parse.router, prefix="/algorithm/resume", tags=["resume"])
app.include_router(market_insights.router, prefix="/algorithm/insights", tags=["insights"])


@app.get("/health")
def health_check():
    result = {
        "status": "ok",
        "service": "algorithm-engine",
        "version": "2.1.0",
    }

    try:
        from app.db import execute_query

        execute_query("SELECT 1 AS ping")
        result["database"] = "connected"
    except Exception as exc:
        result["database"] = f"error: {exc}"
        result["status"] = "degraded"

    try:
        from app.ml.salary_model import load_bundle, model_path

        bundle = load_bundle()
        result["salary_model"] = {
            "status": "loaded" if bundle is not None else "not_trained",
            "path": str(model_path()),
        }
        if bundle is not None:
            result["salary_model"]["top_skills_count"] = len(bundle.top_skills)
            result["salary_model"]["city_vocab_size"] = len(bundle.city_vocab)
            result["salary_model"]["industry_vocab_size"] = len(bundle.industry_vocab)
            result["salary_model"]["cv_mae_mean"] = getattr(bundle, "cv_mae_mean", None)
            result["salary_model"]["cv_mae_std"] = getattr(bundle, "cv_mae_std", None)
            result["salary_model"]["quantile_model"] = getattr(bundle, "quantile_model_p25", None) is not None
    except Exception as exc:
        result["salary_model"] = {"status": f"error: {exc}"}

    return result


@app.get("/")
def root():
    return {
        "service": "Career Platform Algorithm Engine",
        "version": "2.1.0",
        "endpoints": [
            "POST /algorithm/salary/predict",
            "POST /algorithm/salary/train",
            "POST /algorithm/skills/graph",
            "POST /algorithm/skills/evolution",
            "POST /algorithm/match",
            "POST /algorithm/trend/forecast",
            "POST /algorithm/sentiment/index",
            "POST /algorithm/sentiment/history",
            "POST /algorithm/sentiment/compare",
            "POST /algorithm/insights/deep",
            "POST /algorithm/resume/score",
            "POST /algorithm/resume/parse",
            "GET /health",
        ],
    }
