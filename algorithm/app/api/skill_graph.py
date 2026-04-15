"""
技能图谱引擎
=============
从职位-技能关联数据中构建技能共现图谱
提供技能聚类、关联度分析、缺口分析
"""
from typing import Dict, List, Optional

import numpy as np
from fastapi import APIRouter
from pydantic import BaseModel

from app.db import execute_query

router = APIRouter()


# ─── 模型 ──────────────────────────────────────────

class GraphNode(BaseModel):
    id: int
    name: str
    count: int
    category: Optional[str] = None


class GraphEdge(BaseModel):
    source: str
    target: str
    weight: float


class SkillGraphResponse(BaseModel):
    nodes: List[GraphNode]
    edges: List[GraphEdge]
    total_skills: int
    total_relations: int


class GapSkill(BaseModel):
    skill: str
    urgency: str  # high / medium / low
    demand_ratio: float
    related_jobs: int


class SkillGapRequest(BaseModel):
    user_skills: List[str]
    target_job_type: Optional[str] = None
    city: Optional[str] = None


class SkillGapResponse(BaseModel):
    mastered: List[str]
    gap: List[GapSkill]
    advantage: List[str]
    learning_path: List[dict]


# ─── 技能图谱 ─────────────────────────────────────

@router.post("/graph", response_model=SkillGraphResponse)
def get_skill_graph(top_n: int = 50):
    """
    构建技能共现图谱
    1. 获取 TOP N 技能（按出现频率）
    2. 计算两两共现频率
    3. 返回节点 + 边
    """
    # TOP N 技能
    skill_rows = execute_query("""
        SELECT s.id, s.skill_name, s.category, COUNT(js.id) AS cnt
        FROM biz_skill s
        JOIN biz_job_skill js ON s.id = js.skill_id
        GROUP BY s.id, s.skill_name, s.category
        ORDER BY cnt DESC
        LIMIT :limit
    """, {"limit": top_n})

    if not skill_rows:
        return SkillGraphResponse(nodes=[], edges=[], total_skills=0, total_relations=0)

    nodes = [
        GraphNode(id=r["id"], name=r["skill_name"], count=r["cnt"], category=r["category"])
        for r in skill_rows
    ]
    skill_ids = [r["id"] for r in skill_rows]
    skill_names = {r["id"]: r["skill_name"] for r in skill_rows}

    # 共现矩阵：两个技能出现在同一职位中的次数
    if len(skill_ids) < 2:
        return SkillGraphResponse(nodes=nodes, edges=[], total_skills=len(nodes), total_relations=0)

    placeholders = ",".join(str(sid) for sid in skill_ids)
    cooccur_rows = execute_query(f"""
        SELECT a.skill_id AS s1, b.skill_id AS s2, COUNT(*) AS co_count
        FROM biz_job_skill a
        JOIN biz_job_skill b ON a.job_id = b.job_id AND a.skill_id < b.skill_id
        WHERE a.skill_id IN ({placeholders}) AND b.skill_id IN ({placeholders})
        GROUP BY a.skill_id, b.skill_id
        HAVING co_count >= 3
        ORDER BY co_count DESC
        LIMIT 200
    """)

    edges = [
        GraphEdge(
            source=skill_names.get(r["s1"], ""),
            target=skill_names.get(r["s2"], ""),
            weight=float(r["co_count"]),
        )
        for r in cooccur_rows
        if r["s1"] in skill_names and r["s2"] in skill_names
    ]

    return SkillGraphResponse(
        nodes=nodes,
        edges=edges,
        total_skills=len(nodes),
        total_relations=len(edges),
    )


# ─── 技能缺口分析 ──────────────────────────────────

@router.post("/gap", response_model=SkillGapResponse)
def analyze_skill_gap(req: SkillGapRequest):
    """
    对比用户已有技能与目标岗位要求的技能缺口
    """
    # 查询目标岗位类型的技能需求
    conditions = ["1=1"]
    params: Dict = {}

    if req.target_job_type:
        conditions.append("jp.title LIKE :job_type")
        params["job_type"] = f"%{req.target_job_type}%"

    if req.city:
        conditions.append("jp.job_city LIKE :city")
        params["city"] = f"%{req.city}%"

    where_clause = " AND ".join(conditions)

    demand_rows = execute_query(f"""
        SELECT s.skill_name, COUNT(*) AS demand_count
        FROM biz_job_skill js
        JOIN biz_skill s ON js.skill_id = s.id
        JOIN biz_job_posting jp ON js.job_id = jp.id
        WHERE {where_clause}
        GROUP BY s.skill_name
        ORDER BY demand_count DESC
        LIMIT 30
    """, params)

    if not demand_rows:
        return SkillGapResponse(mastered=req.user_skills, gap=[], advantage=[], learning_path=[])

    # 统计总岗位数
    total_jobs_rows = execute_query(f"""
        SELECT COUNT(DISTINCT jp.id) AS total
        FROM biz_job_posting jp
        WHERE {where_clause}
    """, params)
    total_jobs = total_jobs_rows[0]["total"] if total_jobs_rows else 1

    user_skills_lower = {s.lower() for s in req.user_skills}

    mastered = []
    gap = []
    advantage = []

    for row in demand_rows:
        skill_name = row["skill_name"]
        demand_ratio = row["demand_count"] / max(total_jobs, 1)

        if skill_name.lower() in user_skills_lower:
            mastered.append(skill_name)
        else:
            urgency = "high" if demand_ratio > 0.5 else ("medium" if demand_ratio > 0.2 else "low")
            gap.append(GapSkill(
                skill=skill_name,
                urgency=urgency,
                demand_ratio=round(demand_ratio, 3),
                related_jobs=row["demand_count"],
            ))

    # 用户拥有但目标岗位不太需要的 = 优势/差异化技能
    demanded_skills = {r["skill_name"].lower() for r in demand_rows}
    advantage = [s for s in req.user_skills if s.lower() not in demanded_skills]

    # 学习路径（按紧急度排序）
    learning_path = [
        {"step": i + 1, "skill": g.skill, "urgency": g.urgency, "demand_ratio": g.demand_ratio}
        for i, g in enumerate(gap[:10])
    ]

    return SkillGapResponse(
        mastered=mastered,
        gap=gap,
        advantage=advantage,
        learning_path=learning_path,
    )
