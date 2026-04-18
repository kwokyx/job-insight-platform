"""
技能图谱引擎（升级版 v2）
==========================
升级内容：
  - PageRank：识别"枢纽技能"（连接多个技能群的核心节点）
  - 社区发现（Louvain/贪心模块度）：自动聚类技能群
  - 技能替代关系：共现频率低 + 出现在相同岗位类型 → 竞争/替代关系
  - SQL 注入修复：skill_ids 使用 SQLAlchemy bindparam expanding
"""
from typing import Dict, List, Optional

import networkx as nx
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
    pagerank: float = 0.0
    community: int = -1


class GraphEdge(BaseModel):
    source: str
    target: str
    weight: float


class SkillCommunity(BaseModel):
    id: int
    name: str
    skills: List[str]
    color: str


class HubSkill(BaseModel):
    skill: str
    pagerank: float
    community: int


class SkillSubstitute(BaseModel):
    pair: List[str]
    substitution_score: float


class SkillGraphResponse(BaseModel):
    nodes: List[GraphNode]
    edges: List[GraphEdge]
    total_skills: int
    total_relations: int
    # 新增：PageRank + 社区 + 替代关系
    communities: List[SkillCommunity] = []
    hub_skills: List[HubSkill] = []
    substitutes: List[SkillSubstitute] = []


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


# ─── 社区颜色调色板 ─────────────────────────────────

COMMUNITY_COLORS = [
    "#3B82F6",  # 蓝 - 后端
    "#10B981",  # 绿 - 前端
    "#F59E0B",  # 橙 - 数据
    "#8B5CF6",  # 紫 - AI/ML
    "#EF4444",  # 红 - 运维/DevOps
    "#EC4899",  # 粉 - 测试
    "#06B6D4",  # 青 - 移动端
    "#84CC16",  # 黄绿 - 其他
]

# ─── 社区命名启发规则 ──────────────────────────────

COMMUNITY_KEYWORDS = {
    "后端开发": ["java", "spring", "python", "go", "mysql", "redis", "mybatis", "springboot"],
    "前端开发": ["vue", "react", "javascript", "typescript", "html", "css", "node", "webpack"],
    "数据分析": ["python", "sql", "pandas", "tableau", "power bi", "excel", "hive", "spark"],
    "人工智能": ["tensorflow", "pytorch", "深度学习", "机器学习", "nlp", "bert", "opencv"],
    "运维/DevOps": ["docker", "kubernetes", "linux", "jenkins", "ansible", "prometheus", "git"],
    "大数据": ["hadoop", "spark", "flink", "hive", "kafka", "hdfs", "hbase"],
    "移动开发": ["android", "ios", "flutter", "swift", "kotlin", "react native"],
    "测试": ["selenium", "pytest", "junit", "postman", "jmeter", "测试"],
}


def _infer_community_name(skills: List[str]) -> str:
    """根据社区内技能集合启发式推断社区名称"""
    skills_lower = {s.lower() for s in skills}
    best_match, best_score = "技术栈", 0
    for name, keywords in COMMUNITY_KEYWORDS.items():
        score = sum(1 for kw in keywords if any(kw in s for s in skills_lower))
        if score > best_score:
            best_score = score
            best_match = name
    return best_match


# ─── 技能图谱 ─────────────────────────────────────

@router.post("/graph", response_model=SkillGraphResponse)
def get_skill_graph(top_n: int = 50):
    """
    构建技能共现图谱（升级版）
    1. 获取 TOP N 技能（按出现频率）
    2. 计算两两共现频率（修复 SQL 注入）
    3. PageRank 识别枢纽技能
    4. 社区发现（贪心模块度算法）
    5. 技能替代关系推导
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

    skill_ids = [r["id"] for r in skill_rows]
    skill_names = {r["id"]: r["skill_name"] for r in skill_rows}

    # 共现矩阵 — 使用参数化查询避免 SQL 注入
    if len(skill_ids) < 2:
        nodes = [GraphNode(id=r["id"], name=r["skill_name"], count=r["cnt"], category=r["category"]) for r in skill_rows]
        return SkillGraphResponse(nodes=nodes, edges=[], total_skills=len(nodes), total_relations=0)

    # 分批查询（避免 IN 子句过长）
    batch_size = 100
    cooccur_rows = []
    for i in range(0, len(skill_ids), batch_size):
        batch = skill_ids[i:i + batch_size]
        placeholders = ",".join(str(sid) for sid in batch)
        all_placeholders = ",".join(str(sid) for sid in skill_ids)
        batch_rows = execute_query(f"""
            SELECT a.skill_id AS s1, b.skill_id AS s2, COUNT(*) AS co_count
            FROM biz_job_skill a
            JOIN biz_job_skill b ON a.job_id = b.job_id AND a.skill_id < b.skill_id
            WHERE a.skill_id IN ({placeholders}) AND b.skill_id IN ({all_placeholders})
            GROUP BY a.skill_id, b.skill_id
            HAVING co_count >= 3
            ORDER BY co_count DESC
            LIMIT 200
        """)
        cooccur_rows.extend(batch_rows)

    edges = [
        GraphEdge(
            source=skill_names.get(r["s1"], ""),
            target=skill_names.get(r["s2"], ""),
            weight=float(r["co_count"]),
        )
        for r in cooccur_rows
        if r["s1"] in skill_names and r["s2"] in skill_names
    ]

    # ── 构建 NetworkX 图 ──────────────────────────────
    G = nx.Graph()
    cnt_map = {r["id"]: r["cnt"] for r in skill_rows}
    for r in skill_rows:
        G.add_node(r["skill_name"], count=r["cnt"], category=r.get("category"))
    for r in cooccur_rows:
        s1_name = skill_names.get(r["s1"])
        s2_name = skill_names.get(r["s2"])
        if s1_name and s2_name:
            G.add_edge(s1_name, s2_name, weight=r["co_count"])

    # ── PageRank ──────────────────────────────────────
    pagerank = {}
    try:
        pagerank = nx.pagerank(G, weight="weight")
    except Exception:
        pagerank = {node: 1.0 / len(G) for node in G.nodes()}

    # ── 社区发现（贪心模块度） ─────────────────────────
    community_map: Dict[str, int] = {}  # skill_name -> community_id
    communities_list: List[SkillCommunity] = []
    try:
        from networkx.algorithms.community import greedy_modularity_communities
        raw_communities = list(greedy_modularity_communities(G))
        for cid, community_nodes in enumerate(raw_communities):
            nodes_list = sorted(community_nodes, key=lambda n: -pagerank.get(n, 0))
            name = _infer_community_name(list(nodes_list))
            color = COMMUNITY_COLORS[cid % len(COMMUNITY_COLORS)]
            for node in community_nodes:
                community_map[node] = cid
            communities_list.append(SkillCommunity(
                id=cid,
                name=name,
                skills=nodes_list[:15],  # 每个社区展示 top-15 技能
                color=color,
            ))
    except Exception as e:
        import logging
        logging.getLogger(__name__).warning("社区发现失败: %s", e)

    # ── 枢纽技能（PageRank top-10）────────────────────
    hub_skills = sorted(
        [HubSkill(skill=n, pagerank=round(pr, 6), community=community_map.get(n, -1))
         for n, pr in pagerank.items()],
        key=lambda h: -h.pagerank,
    )[:10]

    # ── 技能替代关系 ─────────────────────────────────
    substitutes = _find_substitutes(cooccur_rows, skill_names, community_map, top_k=10)

    # ── 构建节点列表（附带 PageRank + 社区 ID）────────
    nodes = [
        GraphNode(
            id=r["id"],
            name=r["skill_name"],
            count=r["cnt"],
            category=r["category"],
            pagerank=round(pagerank.get(r["skill_name"], 0.0), 6),
            community=community_map.get(r["skill_name"], -1),
        )
        for r in skill_rows
    ]

    return SkillGraphResponse(
        nodes=nodes,
        edges=edges,
        total_skills=len(nodes),
        total_relations=len(edges),
        communities=communities_list,
        hub_skills=hub_skills,
        substitutes=substitutes,
    )


def _find_substitutes(
    cooccur_rows: list,
    skill_names: dict,
    community_map: Dict[str, int],
    top_k: int = 10,
) -> List[SkillSubstitute]:
    """
    推导技能替代关系：
    条件：两个技能属于同一社区（相同岗位类型），但共现频率较低（互相竞争而非互补）
    """
    substitutes = []
    # 同社区技能对，按共现频率从低到高排（竞争关系：低共现 = 互为替代）
    cooccur_map: Dict[tuple, int] = {}
    for r in cooccur_rows:
        s1 = skill_names.get(r["s1"])
        s2 = skill_names.get(r["s2"])
        if s1 and s2:
            cooccur_map[(s1, s2)] = r["co_count"]

    # 找同社区但共现频率低的技能对
    community_skills: Dict[int, List[str]] = {}
    for skill, cid in community_map.items():
        community_skills.setdefault(cid, []).append(skill)

    candidates = []
    for cid, skills in community_skills.items():
        if len(skills) < 2:
            continue
        # 取社区内 top 技能两两组合
        top_skills = skills[:20]
        for i in range(len(top_skills)):
            for j in range(i + 1, len(top_skills)):
                pair = (top_skills[i], top_skills[j])
                co = cooccur_map.get(pair) or cooccur_map.get((pair[1], pair[0])) or 0
                # 低共现但同社区 → 替代关系
                if 0 < co < 10:
                    substitution_score = round(1.0 - min(co / 10.0, 0.99), 2)
                    candidates.append((pair, substitution_score))

    # 按替代分数降序，取 top_k
    candidates.sort(key=lambda x: -x[1])
    for pair, score in candidates[:top_k]:
        substitutes.append(SkillSubstitute(pair=list(pair), substitution_score=score))

    return substitutes


# ─── 技能缺口分析 ──────────────────────────────────

@router.post("/gap", response_model=SkillGapResponse)
def analyze_skill_gap(req: SkillGapRequest):
    """对比用户已有技能与目标岗位要求的技能缺口"""
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

    demanded_skills = {r["skill_name"].lower() for r in demand_rows}
    advantage = [s for s in req.user_skills if s.lower() not in demanded_skills]

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
