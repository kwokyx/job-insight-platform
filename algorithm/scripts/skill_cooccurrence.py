import os
import sys
from collections import defaultdict

# Add parent directory to sys.path
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.db import execute_query, get_db
from sqlalchemy import text

def run():
    print("Starting Skill Co-occurrence Mining...")
    
    # 1. Fetch job to skill mappings
    sql = """
        SELECT job_posting_id as job_id, label_id as skill_id
        FROM job_label_rel
    """
    rows = execute_query(sql)
    print(f"Fetched {len(rows)} job-skill mappings.")

    job_skills = defaultdict(list)
    for row in rows:
        job_skills[row['job_id']].append(row['skill_id'])
        
    # 2. Count co-occurrences
    co_counts = defaultdict(int)
    skill_freq = defaultdict(int)
    
    for jid, s_ids in job_skills.items():
        s_ids = list(set(s_ids)) # Unique
        for sid in s_ids:
            skill_freq[sid] += 1
            
        for i in range(len(s_ids)):
            for j in range(i + 1, len(s_ids)):
                s1, s2 = sorted([s_ids[i], s_ids[j]])
                co_counts[(s1, s2)] += 1
                
    # 3. Calculate weights (Jaccard or simple co-occurrence probability)
    relations = []
    # Only keep relations where co-occurrence > 5
    for (s1, s2), count in co_counts.items():
        if count < 5:
            continue
        # Weight = co_occurrences / min(freq(s1), freq(s2))
        weight = count / min(skill_freq[s1], skill_freq[s2])
        relations.append({
            "s1": s1,
            "s2": s2,
            "weight": round(weight, 3),
            "type": "CO_OCCURRENCE"
        })
        
    # 4. Insert into database
    print(f"Discovered {len(relations)} significant skill relations. Inserting into database...")
    with get_db() as session:
        session.execute(text("TRUNCATE TABLE biz_skill_relation"))
        
        # Batch insert to speed up
        batch_size = 1000
        for i in range(0, len(relations), batch_size):
            batch = relations[i:i+batch_size]
            for r in batch:
                session.execute(text("""
                    INSERT IGNORE INTO biz_skill_relation 
                    (skill_id_a, skill_id_b, relation_type, weight)
                    VALUES (:s1, :s2, :type, :weight)
                """), {
                    "s1": r['s1'],
                    "s2": r['s2'],
                    "type": r['type'],
                    "weight": r['weight']
                })
    print("Done!")

if __name__ == "__main__":
    run()
