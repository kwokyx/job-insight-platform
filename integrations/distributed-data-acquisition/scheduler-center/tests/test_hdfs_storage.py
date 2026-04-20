import sys
from pathlib import Path
from datetime import datetime

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.services.hdfs_storage import JobRecordProjector, WebHdfsClient


def test_job_record_projector_keeps_exact_21_fields():
    projector = JobRecordProjector()
    record = projector.project(
        {
            "url": "https://example.com/job/1",
            "url_obj_id": "job-1",
            "title": "Python工程师",
            "salary_min": "15",
            "salary_max": 25,
            "salary_raw": "15k-25k",
            "job_city": "成都",
            "experience_year": "3-5年",
            "education_need": "本科",
            "publish_date": "2026-04-19T10:20:30",
            "job_welfare": "五险一金",
            "job_labels": "Python,FastAPI",
            "position_info": "负责后端开发",
            "job_classification": "后端开发",
            "company_name": "示例公司",
            "company_size": "100-499人",
            "company_finance": "B轮",
            "crawl_time": datetime(2026, 4, 19, 11, 22, 33),
            "crawl_update_time": "2026-04-19 11:30:00",
            "company_logo": "https://example.com/logo.png",
            "task_id": "task_001",
            "industry_name": "互联网",
            "shard_id": "shard_001",
        }
    )

    assert record is not None
    assert list(record.keys()) == JobRecordProjector.FIELDS
    assert len(record) == 21
    assert record["salary_min"] == 15
    assert record["salary_max"] == 25
    assert record["publish_date"] == "2026-04-19 10:20:30"
    assert record["crawl_time"] == "2026-04-19 11:22:33"


def test_job_record_projector_uses_crawl_time_for_partition_date():
    projector = JobRecordProjector()
    record = projector.project(
        {
            "url_obj_id": "job-2",
            "title": "数据分析师",
            "job_city": "北京",
            "crawl_time": "2026-04-20 08:00:00",
        }
    )

    assert record is not None
    assert projector.partition_date(record) == "2026-04-20"


def test_webhdfs_redirect_location_can_be_rewritten():
    client = WebHdfsClient()
    client.redirect_host_map = {
        "hadoop001": "192.168.152.151",
        "hadoop002": "192.168.152.152",
    }

    rewritten = client._rewrite_redirect_location(
        "http://hadoop002:9864/webhdfs/v1/data/test.jsonl?op=CREATE"
    )

    assert rewritten == "http://192.168.152.152:9864/webhdfs/v1/data/test.jsonl?op=CREATE"
    client.close()
