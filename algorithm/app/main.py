"""
职业能力大数据服务平台 — 算法引擎服务
FastAPI Application Entry Point
"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api import salary_predict, skill_graph, matching, trend_forecast, sentiment

app = FastAPI(
    title="Career Platform Algorithm Engine",
    description="薪资预测、技能图谱、智能匹配、趋势预测、情绪分析",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
)

# CORS（仅内部调用，但开发时方便调试）
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# 注册路由
app.include_router(salary_predict.router, prefix="/algorithm/salary", tags=["薪资预测"])
app.include_router(skill_graph.router,    prefix="/algorithm/skills", tags=["技能图谱"])
app.include_router(matching.router,       prefix="/algorithm/match",  tags=["智能匹配"])
app.include_router(trend_forecast.router, prefix="/algorithm/trend",  tags=["趋势预测"])
app.include_router(sentiment.router,      prefix="/algorithm/sentiment", tags=["情绪分析"])


@app.get("/health")
def health_check():
    """增强健康检查 — 检测数据库连通性和模型状态"""
    result = {
        "status": "ok",
        "service": "algorithm-engine",
        "version": "1.0.0",
    }

    # 检查数据库连通性
    try:
        from app.db import execute_query
        db_rows = execute_query("SELECT 1 AS ping")
        result["database"] = "connected"
    except Exception as e:
        result["database"] = f"error: {str(e)}"
        result["status"] = "degraded"

    # 检查模型状态
    try:
        from app.ml.salary_model import load_bundle, model_path
        path = model_path()
        bundle = load_bundle()
        if bundle is not None:
            result["salary_model"] = {
                "status": "loaded",
                "path": str(path),
                "top_skills_count": len(bundle.top_skills),
                "city_vocab_size": len(bundle.city_vocab),
                "industry_vocab_size": len(bundle.industry_vocab),
            }
        else:
            result["salary_model"] = {"status": "not_trained", "path": str(path)}
    except Exception as e:
        result["salary_model"] = {"status": f"error: {str(e)}"}

    return result


@app.get("/")
def root():
    return {
        "service": "Career Platform Algorithm Engine",
        "version": "1.0.0",
        "endpoints": [
            "POST /algorithm/salary/predict",
            "POST /algorithm/salary/train",
            "POST /algorithm/skills/graph",
            "POST /algorithm/skills/gap",
            "POST /algorithm/match",
            "POST /algorithm/trend/forecast",
            "POST /algorithm/sentiment/index",
            "GET  /health",
        ],
    }
