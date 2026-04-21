import argparse
import os
import re
import time
from dataclasses import dataclass
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

import pymysql


ROOT_DIR = Path(__file__).resolve().parents[1]
ENV_FILE = ROOT_DIR / ".env"
BATCH_SIZE = 100
VALID_MIN = Decimal("0.01")
VALID_MAX = Decimal("200")


def load_env_file(path: Path) -> None:
    if not path.exists():
        return
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        os.environ.setdefault(key.strip(), value.strip())


load_env_file(ENV_FILE)


DB_CONFIG = {
    "host": os.getenv("MYSQL_HOST", "localhost"),
    "port": int(os.getenv("MYSQL_PORT", "3307")),
    "user": os.getenv("MYSQL_USERNAME", "career"),
    "password": os.getenv("MYSQL_PASSWORD", "career2026"),
    "database": os.getenv("MYSQL_DATABASE", "career_platform"),
    "charset": "utf8mb4",
    "cursorclass": pymysql.cursors.DictCursor,
    "autocommit": False,
}


NON_MONTHLY_KEYWORDS = ("元/天", "/天", "元/小时", "/小时", "时薪", "日薪", "按天", "按小时")
NEGOTIABLE_KEYWORDS = ("面议", "待定", "保密", "暂无", "null", "none")
RANGE_PATTERN = re.compile(r"(\d+(?:\.\d+)?)")


@dataclass
class NormalizedSalary:
    salary_min: Decimal
    salary_max: Decimal
    source: str


def connect():
    return pymysql.connect(**DB_CONFIG)


def is_retryable_db_error(error: Exception) -> bool:
    if not isinstance(error, pymysql.err.OperationalError):
        return False
    return error.args and error.args[0] in {1205, 1213}


def round_k(value: Decimal) -> Decimal:
    return value.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def is_valid_range(salary_min, salary_max) -> bool:
    if salary_min is None:
        return False
    min_value = Decimal(str(salary_min))
    if min_value < VALID_MIN or min_value > VALID_MAX:
        return False
    if salary_max is None:
        return True
    max_value = Decimal(str(salary_max))
    return min_value <= max_value <= VALID_MAX


def normalize_from_existing(salary_min, salary_max):
    if salary_min is None:
        return None
    min_value = Decimal(str(salary_min))
    max_value = Decimal(str(salary_max)) if salary_max is not None else None

    if is_valid_range(min_value, max_value):
        return None

    if min_value >= 1000:
        fixed_min = round_k(min_value / Decimal("1000"))
        fixed_max = round_k(max_value / Decimal("1000")) if max_value is not None else None
        if is_valid_range(fixed_min, fixed_max):
            return NormalizedSalary(fixed_min, fixed_max, "existing_yuan_to_k")

    if min_value > VALID_MAX and min_value <= Decimal("1000"):
        fixed_min = round_k(min_value / Decimal("10"))
        fixed_max = round_k(max_value / Decimal("10")) if max_value is not None else None
        if is_valid_range(fixed_min, fixed_max):
            return NormalizedSalary(fixed_min, fixed_max, "existing_scale_fix")

    return None


def normalize_salary_text(text: str):
    if not text:
        return None, "empty"

    normalized = (
        text.strip()
        .lower()
        .replace("－", "-")
        .replace("—", "-")
        .replace("–", "-")
        .replace("~", "-")
        .replace("至", "-")
        .replace("·", "")
        .replace(" ", "")
    )

    if not normalized:
        return None, "empty"

    if any(keyword in normalized for keyword in NEGOTIABLE_KEYWORDS):
        return None, "negotiable"

    if any(keyword in normalized for keyword in NON_MONTHLY_KEYWORDS):
        return None, "non_monthly"

    multiplier = Decimal("1")
    if "万" in normalized:
        multiplier = Decimal("10")
    elif "千" in normalized or "k" in normalized:
        multiplier = Decimal("1")
    elif "元" in normalized or "月" in normalized:
        multiplier = Decimal("0.001")

    numbers = [Decimal(match) for match in RANGE_PATTERN.findall(normalized)]
    if not numbers:
        return None, "unparsed"

    # Drop common annual salary counts from "...13薪"
    if "薪" in normalized and len(numbers) >= 3 and numbers[-1] in {Decimal(str(i)) for i in range(12, 17)}:
        numbers = numbers[:-1]
        if not numbers:
            return None, "unparsed"

    if "以下" in normalized or "以内" in normalized:
        salary_max = numbers[0]
        salary_min = salary_max * Decimal("0.8")
    elif "以上" in normalized or "起" in normalized:
        salary_min = numbers[0]
        salary_max = salary_min * Decimal("1.2")
    else:
        salary_min = numbers[0]
        salary_max = numbers[1] if len(numbers) > 1 else numbers[0]

    salary_min = round_k(salary_min * multiplier)
    salary_max = round_k(salary_max * multiplier)

    if salary_min <= 0 or salary_max <= 0:
        return None, "invalid_non_positive"

    if salary_min > salary_max:
        salary_min, salary_max = salary_max, salary_min

    if salary_min > VALID_MAX or salary_max > Decimal("500"):
        return None, "outlier_after_parse"

    if salary_max > VALID_MAX:
        salary_max = min(round_k(salary_max), Decimal("200.00"))

    if not is_valid_range(salary_min, salary_max):
        return None, "invalid_range"

    return NormalizedSalary(salary_min, salary_max, "salary_raw_parse"), "parsed"


def should_update(current_min, current_max, normalized: NormalizedSalary) -> bool:
    current_min_decimal = Decimal(str(current_min)) if current_min is not None else None
    current_max_decimal = Decimal(str(current_max)) if current_max is not None else None
    return current_min_decimal != normalized.salary_min or current_max_decimal != normalized.salary_max


def execute_updates(conn, pending_updates, stats):
    if not pending_updates:
        return

    update_sql = (
        "UPDATE biz_job_posting "
        "SET salary_min = %s, salary_max = %s, updated_at = NOW() "
        "WHERE id = %s"
    )

    with conn.cursor() as cursor:
        for index in range(0, len(pending_updates), BATCH_SIZE):
            batch = pending_updates[index:index + BATCH_SIZE]
            try:
                cursor.executemany(update_sql, batch)
                conn.commit()
            except Exception as batch_error:
                conn.rollback()
                if not is_retryable_db_error(batch_error):
                    raise

                for row in batch:
                    applied = False
                    for attempt in range(3):
                        try:
                            cursor.execute(update_sql, row)
                            conn.commit()
                            applied = True
                            break
                        except Exception as row_error:
                            conn.rollback()
                            if not is_retryable_db_error(row_error):
                                raise
                            time.sleep(0.5 * (attempt + 1))
                    if not applied:
                        stats["failed"] += 1


def execute_sql_bulk_normalization(conn):
    sql = """
        UPDATE biz_job_posting
        SET salary_min = CASE
                WHEN salary_min IS NULL OR salary_min <= 0 THEN NULL
                WHEN salary_min > 200 THEN ROUND(salary_min / 1000, 2)
                ELSE salary_min
            END,
            salary_max = CASE
                WHEN salary_max IS NULL OR salary_max <= 0 THEN NULL
                WHEN salary_max > 200 THEN ROUND(salary_max / 1000, 2)
                ELSE salary_max
            END,
            updated_at = NOW()
        WHERE salary_min > 200 OR salary_max > 500
    """
    with conn.cursor() as cursor:
        cursor.execute(sql)
        affected = cursor.rowcount
    conn.commit()
    return affected


def main():
    parser = argparse.ArgumentParser(description="Clean historical salary data in biz_job_posting.")
    parser.add_argument("--dry-run", action="store_true", help="Only analyze and print summary, do not update DB.")
    parser.add_argument("--limit", type=int, default=0, help="Only scan the latest N rows for verification.")
    parser.add_argument("--sample", type=int, default=20, help="Number of sample updates to print.")
    parser.add_argument(
        "--bulk-sql-only",
        action="store_true",
        help="Use direct SQL normalization for obvious yuan-to-K historical data and skip row parser.",
    )
    args = parser.parse_args()

    stats = {
        "scanned": 0,
        "parsed_from_raw": 0,
        "fixed_from_existing": 0,
        "updated": 0,
        "skipped_valid": 0,
        "skipped_empty": 0,
        "ambiguous": 0,
        "non_monthly": 0,
        "negotiable": 0,
        "failed": 0,
    }
    reason_counter = {}
    samples = []

    conn = connect()
    try:
        with conn.cursor() as cursor:
            sql = (
                "SELECT id, title, salary_raw, salary_min, salary_max "
                "FROM biz_job_posting ORDER BY id DESC"
            )
            if args.limit and args.limit > 0:
                sql += " LIMIT %s"
                cursor.execute(sql, (args.limit,))
            else:
                cursor.execute(sql)
            rows = cursor.fetchall()

        pending_updates = []
        for row in rows:
            stats["scanned"] += 1
            salary_raw = row.get("salary_raw")
            salary_min = row.get("salary_min")
            salary_max = row.get("salary_max")

            if is_valid_range(salary_min, salary_max):
                stats["skipped_valid"] += 1
                continue

            normalized = None
            reason = "unknown"

            if salary_raw:
                normalized, reason = normalize_salary_text(str(salary_raw))
                if normalized is not None:
                    stats["parsed_from_raw"] += 1
            else:
                reason = "empty"

            if normalized is None:
                fallback = normalize_from_existing(salary_min, salary_max)
                if fallback is not None:
                    normalized = fallback
                    stats["fixed_from_existing"] += 1

            if normalized is None:
                if reason == "empty":
                    stats["skipped_empty"] += 1
                elif reason == "non_monthly":
                    stats["non_monthly"] += 1
                elif reason == "negotiable":
                    stats["negotiable"] += 1
                else:
                    stats["ambiguous"] += 1
                reason_counter[reason] = reason_counter.get(reason, 0) + 1
                continue

            if not should_update(salary_min, salary_max, normalized):
                stats["skipped_valid"] += 1
                continue

            pending_updates.append(
                (
                    normalized.salary_min,
                    normalized.salary_max,
                    row["id"],
                )
            )
            stats["updated"] += 1

            if len(samples) < max(0, args.sample):
                samples.append(
                    {
                        "id": row["id"],
                        "title": row.get("title"),
                        "salary_raw": salary_raw,
                        "old_min": salary_min,
                        "old_max": salary_max,
                        "new_min": str(normalized.salary_min),
                        "new_max": str(normalized.salary_max),
                        "source": normalized.source,
                    }
                )

        if not args.dry_run and args.bulk_sql_only:
            bulk_affected = execute_sql_bulk_normalization(conn)
            stats["updated"] = bulk_affected
        elif not args.dry_run and pending_updates:
            execute_updates(conn, pending_updates, stats)

        print("=" * 72)
        print("Historical Salary Cleaning Summary")
        print("=" * 72)
        print(f"DB: {DB_CONFIG['host']}:{DB_CONFIG['port']}/{DB_CONFIG['database']}")
        print(f"Mode: {'DRY-RUN' if args.dry_run else 'APPLY'}")
        for key, value in stats.items():
            print(f"{key}: {value}")
        if not args.dry_run:
            print(f"applied_successfully: {stats['updated'] - stats['failed']}")

        if reason_counter:
            print("\nUnresolved reasons:")
            for key, value in sorted(reason_counter.items(), key=lambda item: item[1], reverse=True):
                print(f"  - {key}: {value}")

        if samples:
            print("\nSample updates:")
            for sample in samples:
                print(
                    f"  - id={sample['id']} title={sample['title']} raw={sample['salary_raw']} "
                    f"old=({sample['old_min']}, {sample['old_max']}) "
                    f"new=({sample['new_min']}, {sample['new_max']}) source={sample['source']}"
                )
    finally:
        conn.close()


if __name__ == "__main__":
    main()
