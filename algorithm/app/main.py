"""
职业能力大数据服务平台 — 算法引擎服务（升级版 v2）
FastAPI Application Entry Point

新增模块：
  - skill_evolution: 技能演化追踪（新兴/增长/稳定/衰退）
  - resume_score:    简历-岗位双向评分

升级模块：
  - salary_predict:  XGBoost CV + LightGBM 分位数回归 + 特征重要度
  - matching:        TF-IDF 余弦相似度 + 完整因子加权
  - skill_graph:     PageRank + 社区发现 + 替代关系
  - trend_forecast:  ARIMA 自动选阶 + 置信区间
  - sentiment:       历史端点 + 多维对比
"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api import salary_predict, skill_graph, matching, trend_forecast, sentiment
from app.api import skill_evolution, resume_score

app = FastAPI(
    title="Career Platform Algorithm Engine",
    description="薪资预测、技能图谱、智能匹配、趋势预测、情绪分析、技能演化、简历评分",
    version="2.0.0",
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
app.include_router(salary_predict.router,  prefix="/algorithm/salary",    tags=["薪资预测"])
app.include_router(skill_graph.router,     prefix="/algorithm/skills",    tags=["技能图谱"])
app.include_router(matching.router,        prefix="/algorithm/match",     tags=["智能匹配"])
app.include_router(trend_forecast.router,  prefix="/algorithm/trend",     tags=["趋势预测"])
app.include_router(sentiment.router,       prefix="/algorithm/sentiment", tags=["情绪分析"])
app.include_router(skill_evolution.router, prefix="/algorithm/skills",    tags=["技能演化"])
app.include_router(resume_score.router,    prefix="/algorithm/resume",    tags=["简历评分"])


@app.get("/health")
def health_check():
    """增强健康检查 — 检测数据库连通性和模型状态"""
    result = {
        "status": "ok",
        "service": "algorithm-engine",
        "version": "2.0.0",
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
                "cv_mae_mean": getattr(bundle, "cv_mae_mean", None),
                "cv_mae_std": getattr(bundle, "cv_mae_std", None),
                "quantile_model": getattr(bundle, "quantile_model_p25", None) is not None,
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
        "version": "2.0.0",
        "endpoints": [
            # 薪资预测（升级版）
            "POST /algorithm/salary/predict  — XGBoost + LightGBM 分位数 + 特征重要度",
            "POST /algorithm/salary/train    — 训练（5-Fold CV + 分位数模型）",
            # 技能图谱（升级版）
            "POST /algorithm/skills/graph    — 共现图谱 + PageRank + 社区发现 + 替代关系",
            "POST /algorithm/skills/gap      — 技能缺口分析",
            # 技能演化（新增）
            "POST /algorithm/skills/evolution — 技能生命周期追踪",
            # 智能匹配（升级版）
            "POST /algorithm/match           — TF-IDF 余弦相似度 + 完整7因子加权",
            # 趋势预测（升级版）
            "POST /algorithm/trend/forecast  — ARIMA 自动选阶 + 置信区间",
            # 情绪分析（升级版）
            "POST /algorithm/sentiment/index   — 当前市场情绪快照",
            "POST /algorithm/sentiment/history — 最近12个月情绪变化",
            "POST /algorithm/sentiment/compare — 多城市/行业情绪对比",
            # 简历评分（新增）
            "POST /algorithm/resume/score    — 简历-岗位多维度评分",
            # 系统
            "GET  /health",
        ],
    }
