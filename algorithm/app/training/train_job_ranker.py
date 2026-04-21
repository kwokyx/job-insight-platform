"""
职位排序器训练脚本

运行方式（在 algorithm 目录下）：
  py -3 -m app.training.train_job_ranker
"""
import json
import sys
from pathlib import Path

project_root = Path(__file__).resolve().parents[2]
if str(project_root) not in sys.path:
    sys.path.insert(0, str(project_root))

import logging

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("train_job_ranker")


def main():
    logger.info("=" * 60)
    logger.info("职位排序器训练开始")
    logger.info("=" * 60)

    from app.ml.job_ranker import train_and_save, model_path

    result = train_and_save(limit=20000)

    logger.info("-" * 40)
    logger.info("训练完成，统计指标：")
    logger.info("  样本数:        %d", result["sample_count"])
    logger.info("  特征维度:      %d", result["feature_count"])
    logger.info("  目标均值:      %.4f", result["target_mean"])
    logger.info("  模型路径:      %s", result["model_path"])
    logger.info("=" * 60)

    report_path = model_path().parent / "job_ranker_training_report.json"
    try:
        with report_path.open("w", encoding="utf-8") as f:
            json.dump(result, f, ensure_ascii=False, indent=2)
        logger.info("训练报告已保存至: %s", report_path)
    except Exception as exc:
        logger.warning("保存训练报告失败: %s", exc)


if __name__ == "__main__":
    main()
