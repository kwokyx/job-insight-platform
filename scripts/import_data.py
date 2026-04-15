"""
数据导入脚本 — 将 JSON 数据导入 MySQL career_platform 数据库
用法: python scripts/import_data.py
"""
import json
import os
import sys
import pymysql
from datetime import datetime

# 数据库连接配置
DB_CONFIG = {
    'host': os.getenv('MYSQL_HOST', 'localhost'),
    'port': int(os.getenv('MYSQL_PORT', '3307')),
    'user': os.getenv('MYSQL_USERNAME', 'root'),
    'password': os.getenv('MYSQL_PASSWORD', 'career2026'),
    'database': os.getenv('MYSQL_DATABASE', 'career_platform'),
    'charset': 'utf8mb4',
    'cursorclass': pymysql.cursors.DictCursor
}

DATA_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'data', 'processed')


def connect():
    return pymysql.connect(**DB_CONFIG)


def import_jobs(conn):
    """导入职位数据 + 技能数据"""
    # 读取所有 JSON 数据文件
    files = ['51job_100k_jobs.json', 'real_jobs_merged.json', 'live_market_jobs.json', 'official_live_jobs.json']
    all_jobs = []
    seen_ids = set()

    for f in files:
        path = os.path.join(DATA_DIR, f)
        if not os.path.exists(path):
            print(f"  [跳过] {f} 不存在")
            continue
        with open(path, 'r', encoding='utf-8') as fh:
            data = json.load(fh)
            if isinstance(data, list):
                for job in data:
                    jid = job.get('job_id', '')
                    if jid and jid not in seen_ids:
                        seen_ids.add(jid)
                        all_jobs.append(job)
            print(f"  [读取] {f}: {len(data) if isinstance(data, list) else '非列表'} 条")

    print(f"\n去重后共 {len(all_jobs)} 条职位待导入")

    cursor = conn.cursor()

    # 清空已有数据
    cursor.execute("DELETE FROM biz_job_skill")
    cursor.execute("DELETE FROM biz_skill")
    cursor.execute("DELETE FROM biz_job_posting")
    cursor.execute("DELETE FROM biz_company")
    conn.commit()
    print("已清空旧数据")

    # 收集公司和技能
    company_map = {}  # company_name -> company row id
    skill_map = {}    # skill_name -> skill_id
    job_count = 0
    skill_link_count = 0

    for i, job in enumerate(all_jobs):
        title = (job.get('title') or '').strip()
        if not title:
            continue

        company_name = (job.get('company_name') or job.get('company') or '').strip()
        city = (job.get('job_city') or job.get('city') or job.get('region') or '').strip()
        classification = (job.get('job_classification') or job.get('industry_name') or job.get('industry') or '').strip()
        education = (job.get('education_need') or job.get('education') or '').strip()
        experience = (job.get('experience_year') or job.get('experience') or '').strip()
        salary_min = job.get('salary_min')
        salary_max = job.get('salary_max')
        job_welfare = (job.get('job_welfare') or '').strip()
        salary_text = (job.get('salary_raw') or job.get('salary_text') or '').strip()
        position_info = (job.get('position_info') or job.get('description') or '').strip()
        source_url = (job.get('url') or job.get('source_url') or '').strip()
        publish_date = job.get('publish_date')
        crawl_time = job.get('crawl_time')
        job_labels = job.get('job_labels') or job.get('skills') or []
        crawl_update_time = job.get('crawl_update_time')
        job_id_source = (job.get('url_obj_id') or job.get('job_id') or '').strip()
        company_size = (job.get('company_size') or '').strip()
        company_finance = (job.get('company_finance') or '').strip()

        # 处理薪资
        try:
            salary_min = float(salary_min) if salary_min else None
            salary_max = float(salary_max) if salary_max else None
        except (ValueError, TypeError):
            salary_min = None
            salary_max = None

        # 处理日期
        try:
            if publish_date:
                publish_date = str(publish_date)[:10]
            else:
                publish_date = None
        except:
            publish_date = None

        try:
            if crawl_time:
                crawl_time = str(crawl_time)[:19].replace('T', ' ').replace('Z', '')
            else:
                crawl_time = None
        except:
            crawl_time = None

        # 插入公司
        if company_name and company_name not in company_map:
            cursor.execute(
                "INSERT INTO biz_company (company_name, industry, company_size, company_finance) VALUES (%s, %s, %s, %s)",
                (
                    company_name[:255],
                    classification[:100] if classification else None,
                    company_size[:50] if company_size else None,
                    company_finance[:50] if company_finance else None
                )
            )
            company_map[company_name] = cursor.lastrowid

        # 插入职位
        benefits_val = None
        if job_welfare:
            benefits_val = json.dumps([job_welfare], ensure_ascii=False)
        elif isinstance(job_labels, list) and job_labels:
            benefits_val = json.dumps(job_labels, ensure_ascii=False)

        cursor.execute("""
            INSERT INTO biz_job_posting 
            (job_id_source, title, company_id, company_name, city, industry_name,
             education, experience, salary_min, salary_max, salary_text,
             job_benefits, description, source_site, source_url, publish_date, crawl_time)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """, (
            job_id_source[:100] if job_id_source else None,
            title[:255],
            company_map.get(company_name) if company_name else None,
            company_name[:255] if company_name else None,
            city[:100] if city else None,
            classification[:100] if classification else None,
            education[:50] if education else None,
            experience[:50] if experience else None,
            salary_min, salary_max,
            salary_text[:100] if salary_text else None,
            benefits_val,
            position_info[:10000] if position_info else None,
            'import',
            source_url[:500] if source_url else None,
            publish_date,
            crawl_time or datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        ))
        job_db_id = cursor.lastrowid
        job_count += 1

        # 插入技能
        skills = job.get('skills', [])
        if isinstance(skills, list):
            for sk in skills:
                sk_name = (sk if isinstance(sk, str) else '').strip()
                if not sk_name or len(sk_name) > 50 or len(sk_name) < 1:
                    continue
                if sk_name not in skill_map:
                    cursor.execute(
                        "INSERT IGNORE INTO biz_skill (skill_name, category) VALUES (%s, %s)",
                        (sk_name[:50], categorize_skill(sk_name))
                    )
                    if cursor.lastrowid:
                        skill_map[sk_name] = cursor.lastrowid
                    else:
                        cursor.execute("SELECT id FROM biz_skill WHERE skill_name = %s", (sk_name,))
                        row = cursor.fetchone()
                        if row:
                            skill_map[sk_name] = row['id']
                
                if sk_name in skill_map:
                    try:
                        cursor.execute(
                            "INSERT INTO biz_job_skill (job_id, skill_id) VALUES (%s, %s)",
                            (job_db_id, skill_map[sk_name])
                        )
                        skill_link_count += 1
                    except pymysql.IntegrityError:
                        pass

        if (i + 1) % 500 == 0:
            conn.commit()
            print(f"  进度: {i+1}/{len(all_jobs)}")

    conn.commit()
    print(f"\n导入完成: {job_count} 个职位, {len(company_map)} 家公司, {len(skill_map)} 个技能, {skill_link_count} 条技能关联")


def categorize_skill(skill_name):
    """简单技能分类"""
    sn = skill_name.lower()
    
    lang_kw = ['python', 'java', 'javascript', 'c++', 'c#', 'go', 'rust', 'php', 'ruby', 'swift', 'kotlin', 'typescript', 'r语言', 'scala', 'matlab', 'perl', 'lua', 'shell', 'sql', 'html', 'css']
    for kw in lang_kw:
        if kw in sn:
            return '编程语言'
    
    fw_kw = ['spring', 'django', 'flask', 'react', 'vue', 'angular', 'node', 'express', 'mybatis', 'hibernate', 'laravel', '.net', 'qt', 'electron', 'flutter', 'unity']
    for kw in fw_kw:
        if kw in sn:
            return '框架'
    
    ai_kw = ['tensorflow', 'pytorch', 'keras', '机器学习', '深度学习', 'nlp', '自然语言', '计算机视觉', 'cv', 'ai', '人工智能', '算法', 'opencv']
    for kw in ai_kw:
        if kw in sn:
            return 'AI/ML'
    
    data_kw = ['mysql', 'redis', 'mongodb', 'postgresql', 'oracle', 'elasticsearch', 'kafka', 'hadoop', 'spark', 'hive', 'flink', '数据库', '大数据', '数据分析', 'tableau', 'power bi']
    for kw in data_kw:
        if kw in sn:
            return '数据/数据库'
    
    devops_kw = ['docker', 'kubernetes', 'k8s', 'jenkins', 'ci/cd', 'git', 'linux', 'aws', 'azure', '云', 'devops', 'nginx', '运维', '微服务']
    for kw in devops_kw:
        if kw in sn:
            return 'DevOps/云'
    
    return '其他'


def create_admin_user(conn):
    """创建管理员用户（如果不存在）"""
    cursor = conn.cursor()
    cursor.execute("SELECT id FROM sys_user WHERE username = 'admin'")
    if cursor.fetchone():
        print("管理员账号已存在")
        return
    
    # BCrypt 加密的 admin123
    import hashlib
    bcrypt_hash = '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36fVIK8RJUK3sR4j.T9YhHa'
    
    cursor.execute("""
        INSERT INTO sys_user (username, nickname, password_hash, role_type, status) 
        VALUES ('admin', '系统管理员', %s, 1, 1)
    """, (bcrypt_hash,))
    conn.commit()
    print("已创建管理员账号: admin / admin123")


if __name__ == '__main__':
    print("="*60)
    print("职业能力大数据平台 — 数据导入工具")
    print("="*60)
    print(f"数据库: {DB_CONFIG['host']}:{DB_CONFIG['port']}/{DB_CONFIG['database']}")
    print(f"数据目录: {DATA_DIR}")
    print()

    conn = connect()
    try:
        create_admin_user(conn)
        import_jobs(conn)
    except Exception as e:
        print(f"\n[错误] {e}")
        import traceback
        traceback.print_exc()
    finally:
        conn.close()

    print("\n" + "="*60)
    print("导入完毕！")
