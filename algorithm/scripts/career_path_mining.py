import os
import sys
import json
from collections import defaultdict

# Add parent directory to sys.path
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.db import execute_query, get_db
from sqlalchemy import text

def extract_years(exp_str):
    if not exp_str or "不限" in exp_str or "应届" in exp_str or "在校" in exp_str:
        return 0
    import re
    nums = [int(n) for n in re.findall(r"\d+", exp_str)]
    if not nums:
        return 1
    return sum(nums[:2]) / len(nums[:2])

def run():
    print("Starting Career Path Mining...")
    
    # 1. Fetch job data with skills
    sql = """
        SELECT jp.id, jp.title, COALESCE(jp.industry_name, '') AS category, jp.experience_year, 
               jp.job_labels AS skill_list
        FROM biz_job_posting jp
        WHERE jp.title IS NOT NULL AND jp.experience_year IS NOT NULL
    """
    jobs = execute_query(sql)
    print(f"Fetched {len(jobs)} jobs.")

    # 2. Group by category and clean title
    category_jobs = defaultdict(list)
    for j in jobs:
        cat = j['category'] or "通用"
        # Simplify title to base role (e.g. 高级Java开发工程师 -> Java开发工程师)
        title = j['title']
        for prefix in ["初级", "中级", "高级", "资深", "专家", "总监"]:
            title = title.replace(prefix, "")
        
        years = extract_years(j['experience_year'])
        skill_str = j['skill_list'] or ""
        try:
            skill_parsed = json.loads(skill_str)
            if isinstance(skill_parsed, list):
                skills = set(skill_parsed)
            else:
                skills = set([s.strip() for s in skill_str.split(",") if s.strip()])
        except:
            skills = set([s.strip() for s in skill_str.split(",") if s.strip()])
        
        category_jobs[cat].append({
            "title": title.strip(),
            "years": years,
            "skills": skills
        })

    paths = []
    
    # 3. Discover paths
    for cat, items in category_jobs.items():
        if len(items) < 10:
            continue
            
        # Group by title
        title_groups = defaultdict(list)
        for it in items:
            title_groups[it['title']].append(it)
            
        for base_title, t_items in title_groups.items():
            if len(t_items) < 5:
                continue
                
            # Classify into levels
            junior = [x for x in t_items if x['years'] <= 2]
            mid = [x for x in t_items if 2 < x['years'] <= 5]
            senior = [x for x in t_items if x['years'] > 5]
            
            def get_top_skills(group, n=5):
                skill_counts = defaultdict(int)
                for g in group:
                    for s in g['skills']:
                        skill_counts[s] += 1
                return [s for s, c in sorted(skill_counts.items(), key=lambda x: x[1], reverse=True)[:n]]

            # Junior -> Mid
            if junior and mid:
                paths.append({
                    "from": f"初级{base_title}",
                    "to": f"中高级{base_title}",
                    "type": "晋升",
                    "years": 3.0,
                    "skills": get_top_skills(mid),
                    "freq": len(mid)
                })
                
            # Mid -> Senior
            if mid and senior:
                paths.append({
                    "from": f"中高级{base_title}",
                    "to": f"资深{base_title}/专家",
                    "type": "晋升",
                    "years": 5.5,
                    "skills": get_top_skills(senior),
                    "freq": len(senior)
                })
                
    # 4. Insert into database
    print(f"Discovered {len(paths)} career paths. Inserting into database...")
    with get_db() as session:
        session.execute(text("TRUNCATE TABLE biz_career_path"))
        for p in paths:
            if not p['skills']:
                continue
            session.execute(text("""
                INSERT INTO biz_career_path 
                (job_title_from, job_title_to, transition_type, avg_years, required_skills, frequency)
                VALUES (:from_t, :to_t, :type, :years, :skills, :freq)
            """), {
                "from_t": p['from'],
                "to_t": p['to'],
                "type": p['type'],
                "years": p['years'],
                "skills": json.dumps(p['skills'], ensure_ascii=False),
                "freq": p['freq']
            })
    print("Done!")

if __name__ == "__main__":
    run()
