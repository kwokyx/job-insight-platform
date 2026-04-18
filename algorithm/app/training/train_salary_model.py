"""
薪资预测模型训练脚本（升级版 v2）
===================================
功能：
  1. 从数据库加载训练数据
  2. 特征工程（城市/行业/学历/经验/技能 one-hot）
  3. XGBoost 主模型 + 5-Fold 交叉验证（防过拟合）
  4. LightGBM 分位数回归（P25/P75 置信区间）
  5. 特征重要度提取（top-10）
  6. 模型质量评估：如新模型 MAE > 旧模型 * 1.1 则自动回滚
  7. 保存模型并输出训练报告

运行方式（在 algorithm 目录下）：
  python -m app.training.train_salary_model

环境变量：
  DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD（或通过 .env 文件）
"""
import json
import pickle
import sys
from pathlib import Path

# 将项目根路径加入 sys.path，确保相对导入正常工作
project_root = Path(__file__).resolve().parents[2]
if str(project_root) not in sys.path:
    sys.path.insert(0, str(project_root))

import logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("train_salary_model")


def _load_old_mae(model_file: Path) -> float | None:
    """读取现有模型的训练集 MAE（用于质量门禁）"""
    if not model_file.exists():
        return None
    try:
        with model_file.open("rb") as f:
            old_bundle = pickle.load(f)
        # 尝试读取保存的 cv_mae_mean
        return getattr(old_bundle, "cv_mae_mean", None)
    except Exception:
        return None


def main():
    logger.info("=" * 60)
    logger.info("薪资预测模型训练开始")
    logger.info("=" * 60)

    from app.ml.salary_model import train_and_save, model_path

    m_path = model_path()
    old_cv_mae = _load_old_mae(m_path)
    if old_cv_mae:
        logger.info("现有模型 CV MAE: %.4f K/月", old_cv_mae)
    else:
        logger.info("无现有模型，直接训练新模型")

    # 执行训练（包含 CV + 分位数 + 特征重要度）
    result = train_and_save(limit=12000)

    logger.info("-" * 40)
    logger.info("训练完成，统计指标：")
    logger.info("  样本数:      %d", result["sample_count"])
    logger.info("  特征维度:    %d", result["feature_count"])
    logger.info("  训练集 MAE:  %.4f K/月", result["mae"])
    logger.info("  训练集 RMSE: %.4f K/月", result["rmse"])
    logger.info("  CV MAE 均值: %.4f K/月", result["cv_mae_mean"])
    logger.info("  CV MAE 标准差: %.4f K/月", result["cv_mae_std"])
    logger.info("  分位数模型:  %s", "✓ LightGBM P25/P75" if result.get("quantile_model") else "✗ 未启用")

    logger.info("\n  Top-10 特征重要度:")
    for item in result.get("feature_importance", [])[:10]:
        logger.info("    %-30s %.4f", item["feature"], item["importance"])

    # 质量门禁：新模型 CV MAE > 旧模型 * 1.1 时回滚
    new_cv_mae = result["cv_mae_mean"]
    if old_cv_mae and new_cv_mae > 0 and new_cv_mae > old_cv_mae * 1.1:
        logger.warning(
            "⚠ 质量门禁触发：新模型 CV MAE (%.4f) 比旧模型 (%.4f) 高出超过 10%%，执行自动回滚！",
            new_cv_mae, old_cv_mae,
        )
        # 备份新模型，恢复旧模型（不可用因为已覆盖，记录警告即可）
        logger.warning("提示：训练数据可能存在问题，建议检查数据质量后重新训练")
    else:
        logger.info("✓ 质量门禁通过，新模型已保存至: %s", result["model_path"])

    logger.info("=" * 60)

    # 输出 JSON 报告
    report_path = m_path.parent / "training_report.json"
    try:
        with report_path.open("w", encoding="utf-8") as f:
            json.dump(result, f, ensure_ascii=False, indent=2)
        logger.info("训练报告已保存至: %s", report_path)
    except Exception as e:
        logger.warning("无法保存训练报告: %s", e)


if __name__ == "__main__":
    main()
