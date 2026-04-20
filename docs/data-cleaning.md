# 数据清洗说明

## 历史工资清洗

用于修正 `biz_job_posting` 中历史工资单位混乱问题，当前平台统一按 `K/月` 口径存储。

脚本位置：

`scripts/clean_historical_salaries.py`

推荐执行方式：

```powershell
D:\Python\python.exe scripts\clean_historical_salaries.py --dry-run --bulk-sql-only
D:\Python\python.exe scripts\clean_historical_salaries.py --bulk-sql-only
```

说明：

- `--bulk-sql-only`：直接把明显是“元/月误写入 K/月字段”的历史数据批量除以 `1000`，适合当前主数据。
- `--dry-run`：只看扫描和样本，不写库。
- 不带 `--bulk-sql-only` 时，脚本会尝试按 `salary_raw` 做逐条解析，适合后续扩展更细的规则。

数据库连接默认读取仓库根目录 `.env`：

- `MYSQL_HOST`
- `MYSQL_PORT`
- `MYSQL_DATABASE`
- `MYSQL_USERNAME`
- `MYSQL_PASSWORD`

## 同步链路防回脏

为避免采集数据再次把 `元/月` 直接写回业务表，以下同步 SQL 已补充工资归一化：

- `config/sync_crawl_to_business.sql`
- `config/sync_crawl_to_business_incremental.sql`

全量或增量同步后，业务表工资会先按阈值归一化，再进入后续分析口径。
