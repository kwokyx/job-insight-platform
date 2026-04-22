from collections import Counter
from typing import Dict, List, Optional

import networkx as nx
from fastapi import APIRouter
from pydantic import BaseModel

from app.db import execute_query
from app.skill_normalizer import filter_skills_by_family, infer_skill_family, normalize_job_labels, normalize_skill_tokens, skill_family_score

router = APIRouter()


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
    communities: List[SkillCommunity] = []
    hub_skills: List[HubSkill] = []
    substitutes: List[SkillSubstitute] = []


class GapSkill(BaseModel):
    skill: str
    urgency: str
    demand_ratio: float
    related_jobs: int
    trend: str = "stable"
    difficulty: str = "medium"
    priority_score: float = 0.0


class SkillGapRequest(BaseModel):
    user_skills: List[str]
    target_job_type: Optional[str] = None
    city: Optional[str] = None


class SkillGapResponse(BaseModel):
    mastered: List[str]
    gap: List[GapSkill]
    advantage: List[str]
    learning_path: List[dict]
    diagnosis: dict = {}
    market_required_skills: List[str] = []


class SkillRadarItem(BaseModel):
    skill: str
    current_score: float
    target_score: float
    gap_score: float
    demand_ratio: float
    trend: str
    priority: str


class SkillRadarResponse(BaseModel):
    target_job_type: Optional[str] = None
    city: Optional[str] = None
    radar: List[SkillRadarItem]
    summary: str


COMMUNITY_COLORS = ["#2563EB", "#059669", "#D97706", "#7C3AED", "#DC2626", "#0891B2", "#65A30D"]
COMMUNITY_KEYWORDS = {
    "后端工程": ["java", "spring", "spring boot", "mysql", "redis", "docker", "kafka"],
    "前端工程": ["vue", "react", "javascript", "typescript", "css", "html", "node.js"],
    "数据方向": ["python", "sql", "spark", "flink", "hadoop", "pandas", "bi"],
    "测试质量": ["selenium", "jmeter", "postman", "自动化测试", "接口测试"],
    "平台运维": ["linux", "docker", "kubernetes", "jenkins", "nginx"],
}

def _infer_role_family(target_job_type: Optional[str], user_skills: Optional[List[str]] = None) -> str:
    return infer_skill_family([target_job_type or "", *(user_skills or [])])


def _infer_query_keywords(target_job_type: Optional[str], user_skills: Optional[List[str]] = None) -> List[str]:
    family = _infer_role_family(target_job_type, user_skills)
    if family == "backend":
        return [target_job_type or "", "Java", "Spring Boot", "后端"]
    if family == "frontend":
        return [target_job_type or "", "Vue", "React", "前端"]
    if family == "data":
        return [target_job_type or "", "Python", "SQL", "数据"]
    if family == "qa":
        return [target_job_type or "", "测试", "自动化测试", "QA"]
    return [target_job_type or ""]


def _load_job_skill_tokens(limit: int = 5000) -> List[List[str]]:
    rows = execute_query(
        """
        SELECT job_labels
        FROM biz_job_posting
        WHERE job_labels IS NOT NULL
        ORDER BY publish_date DESC, id DESC
        LIMIT :limit
        """,
        {"limit": limit},
    )
    token_rows: List[List[str]] = []
    for row in rows:
        tokens = normalize_job_labels(row["job_labels"])
        if tokens:
            token_rows.append(tokens)
    return token_rows


def _infer_community_name(skills: List[str]) -> str:
    skills_lower = {value.lower() for value in skills}
    best_name = "通用技能栈"
    best_score = 0
    for name, keywords in COMMUNITY_KEYWORDS.items():
        score = sum(1 for keyword in keywords if any(keyword in skill for skill in skills_lower))
        if score > best_score:
            best_name = name
            best_score = score
    return best_name


def _query_market_rows(target_job_type: Optional[str], city: Optional[str], limit: int, user_skills: Optional[List[str]]) -> List[dict]:
    conditions = ["job_labels IS NOT NULL"]
    params: Dict[str, object] = {"limit": limit}
    query_keywords = [keyword for keyword in _infer_query_keywords(target_job_type, user_skills) if keyword][:4]
    if query_keywords:
        clauses = []
        for index, keyword in enumerate(query_keywords):
            key = f"job_type_{index}"
            params[key] = f"%{keyword}%"
            clauses.append(f"title LIKE :{key}")
        conditions.append("(" + " OR ".join(clauses) + ")")
    if city:
        conditions.append("(COALESCE(city, job_city) LIKE :city OR job_city LIKE :city)")
        params["city"] = f"%{city}%"

    return execute_query(
        f"""
        SELECT id, title, job_labels
        FROM biz_job_posting
        WHERE {' AND '.join(conditions)}
        ORDER BY publish_date DESC, id DESC
        LIMIT :limit
        """,
        params,
    )


def _fetch_market_skill_stats(
    target_job_type: Optional[str],
    city: Optional[str],
    limit: int = 6000,
    user_skills: Optional[List[str]] = None,
) -> tuple[list[dict], int]:
    rows = _query_market_rows(target_job_type, city, limit, user_skills)
    if len(rows) < 30:
        broad_rows = _query_market_rows(None if rows else target_job_type, None, limit, user_skills)
        if broad_rows:
            rows = broad_rows
    if not rows:
        return [], 0

    total_count = len(rows)
    split_index = max(1, total_count // 2)
    all_counter: Counter = Counter()
    recent_counter: Counter = Counter()
    previous_counter: Counter = Counter()

    for index, row in enumerate(rows):
        tokens = filter_skills_by_family(normalize_job_labels(row["job_labels"]), _infer_role_family(target_job_type, user_skills))
        for token in tokens:
            all_counter[token] += 1
            if index < split_index:
                recent_counter[token] += 1
            else:
                previous_counter[token] += 1

    stats: List[dict] = []
    family = _infer_role_family(target_job_type, user_skills)
    scored_skills = sorted(
        all_counter.items(),
        key=lambda item: (-skill_family_score(item[0], family), -item[1], item[0]),
    )
    for skill, count in scored_skills[:40]:
        prev = previous_counter.get(skill, 0)
        recent = recent_counter.get(skill, 0)
        if prev == 0 and recent > 0:
            trend = "rising"
        elif recent >= prev * 1.2:
            trend = "rising"
        elif prev > 0 and recent <= prev * 0.8:
            trend = "cooling"
        else:
            trend = "stable"

        demand_ratio = round(count / max(total_count, 1), 3)
        difficulty = "high" if demand_ratio >= 0.35 else ("medium" if demand_ratio >= 0.15 else "low")
        stats.append(
            {
                "skill": skill,
                "count": count,
                "demand_ratio": demand_ratio,
                "trend": trend,
                "difficulty": difficulty,
            }
        )
    return stats, total_count


def _priority_label(score: float) -> str:
    if score >= 75:
        return "P0"
    if score >= 50:
        return "P1"
    return "P2"


@router.post("/graph", response_model=SkillGraphResponse)
def get_skill_graph(top_n: int = 50):
    token_rows = _load_job_skill_tokens(limit=5000)
    skill_counter: Counter = Counter()
    co_counter: Counter = Counter()
    for tokens in token_rows:
        uniq = sorted(set(tokens))
        for token in uniq:
            skill_counter[token] += 1
        for index, left in enumerate(uniq):
            for right in uniq[index + 1:]:
                co_counter[(left, right)] += 1

    top_skills = [skill for skill, _ in skill_counter.most_common(top_n)]
    if not top_skills:
        return SkillGraphResponse(nodes=[], edges=[], total_skills=0, total_relations=0)

    graph = nx.Graph()
    for skill in top_skills:
        graph.add_node(skill, count=skill_counter[skill])
    for (left, right), count in co_counter.most_common(240):
        if left in graph and right in graph and count >= 2:
            graph.add_edge(left, right, weight=float(count))

    pagerank = nx.pagerank(graph, weight="weight") if graph.number_of_nodes() else {}
    community_map: Dict[str, int] = {}
    communities: List[SkillCommunity] = []
    try:
        from networkx.algorithms.community import greedy_modularity_communities

        raw = list(greedy_modularity_communities(graph))
        for community_id, members in enumerate(raw):
            member_list = sorted(members, key=lambda item: -pagerank.get(item, 0.0))
            for member in members:
                community_map[member] = community_id
            communities.append(
                SkillCommunity(
                    id=community_id,
                    name=_infer_community_name(member_list),
                    skills=member_list[:15],
                    color=COMMUNITY_COLORS[community_id % len(COMMUNITY_COLORS)],
                )
            )
    except Exception:
        pass

    nodes = [
        GraphNode(
            id=index,
            name=skill,
            count=skill_counter[skill],
            category=_infer_community_name([skill]),
            pagerank=round(pagerank.get(skill, 0.0), 6),
            community=community_map.get(skill, -1),
        )
        for index, skill in enumerate(top_skills)
    ]
    edges = [
        GraphEdge(source=left, target=right, weight=float(data.get("weight", 1.0)))
        for left, right, data in graph.edges(data=True)
    ]
    hub_skills = [
        HubSkill(skill=skill, pagerank=round(score, 6), community=community_map.get(skill, -1))
        for skill, score in sorted(pagerank.items(), key=lambda item: -item[1])[:10]
    ]
    substitutes: List[SkillSubstitute] = []
    for community in communities[:4]:
        member_list = community.skills[:6]
        for index, left in enumerate(member_list):
            for right in member_list[index + 1:]:
                weight = graph.get_edge_data(left, right, default={}).get("weight", 0.0)
                if 0 < weight <= 6:
                    substitutes.append(
                        SkillSubstitute(
                            pair=[left, right],
                            substitution_score=round(max(0.1, 1.0 - weight / 10.0), 2),
                        )
                    )
    return SkillGraphResponse(
        nodes=nodes,
        edges=edges,
        total_skills=len(nodes),
        total_relations=len(edges),
        communities=communities,
        hub_skills=hub_skills,
        substitutes=substitutes[:10],
    )


@router.post("/gap", response_model=SkillGapResponse)
def analyze_skill_gap(req: SkillGapRequest):
    demand_list, total_jobs = _fetch_market_skill_stats(req.target_job_type, req.city, user_skills=req.user_skills)
    normalized_user_skills = normalize_skill_tokens(req.user_skills)
    if not demand_list:
        return SkillGapResponse(mastered=normalized_user_skills, gap=[], advantage=[], learning_path=[])

    user_skills_lower = {value.lower() for value in normalized_user_skills}
    mastered: List[str] = []
    gap: List[GapSkill] = []

    for row in demand_list:
        skill_name = row["skill"]
        priority_score = round(
            row["demand_ratio"] * 100
            + (15 if row["trend"] == "rising" else 0)
            + (10 if row["difficulty"] == "high" else 0),
            1,
        )
        if skill_name.lower() in user_skills_lower:
            mastered.append(skill_name)
            continue
        gap.append(
            GapSkill(
                skill=skill_name,
                urgency="high" if priority_score >= 60 else ("medium" if priority_score >= 30 else "low"),
                demand_ratio=row["demand_ratio"],
                related_jobs=row["count"],
                trend=row["trend"],
                difficulty=row["difficulty"],
                priority_score=priority_score,
            )
        )

    gap.sort(key=lambda item: item.priority_score, reverse=True)
    demanded_skills = {row["skill"].lower() for row in demand_list}
    advantage = [skill for skill in normalized_user_skills if skill.lower() not in demanded_skills][:8]
    learning_path = [
        {
            "step": index + 1,
            "skill": item.skill,
            "urgency": item.urgency,
            "trend": item.trend,
            "difficulty": item.difficulty,
            "milestone": f"完成 1 个包含 {item.skill} 的项目案例，并把结果写入简历或作品集。",
        }
        for index, item in enumerate(gap[:10])
    ]

    return SkillGapResponse(
        mastered=mastered,
        gap=gap,
        advantage=advantage,
        learning_path=learning_path,
        diagnosis={
            "target_job_type": req.target_job_type,
            "city": req.city,
            "market_job_count": total_jobs,
            "match_ratio": round(len(mastered) / max(len(demand_list), 1), 3),
            "readiness": "ready_to_apply" if len(mastered) >= 6 else ("need_upskill" if len(mastered) >= 3 else "rebuild_core"),
            "rising_skills": [row["skill"] for row in demand_list if row["trend"] == "rising"][:6],
            "hard_skills": [row["skill"] for row in demand_list if row["difficulty"] == "high"][:6],
        },
        market_required_skills=[row["skill"] for row in demand_list[:12]],
    )


@router.post("/radar", response_model=SkillRadarResponse)
def skill_radar(req: SkillGapRequest):
    demand_list, _ = _fetch_market_skill_stats(req.target_job_type, req.city, user_skills=req.user_skills)
    user_skills = {skill.lower() for skill in normalize_skill_tokens(req.user_skills)}
    radar: List[SkillRadarItem] = []

    for row in demand_list[:6]:
        current_score = 100.0 if row["skill"].lower() in user_skills else 25.0
        target_score = min(100.0, 40.0 + row["demand_ratio"] * 100)
        gap_score = max(0.0, round(target_score - current_score, 1))
        radar.append(
            SkillRadarItem(
                skill=row["skill"],
                current_score=round(current_score, 1),
                target_score=round(target_score, 1),
                gap_score=gap_score,
                demand_ratio=row["demand_ratio"],
                trend=row["trend"],
                priority=_priority_label(gap_score + row["demand_ratio"] * 100),
            )
        )

    summary = (
        "当前技能结构已经接近目标岗位核心要求。"
        if radar and all(item.gap_score < 30 for item in radar)
        else "当前短板主要集中在高频核心技能，建议优先补齐 P0/P1 项。"
    )
    return SkillRadarResponse(
        target_job_type=req.target_job_type,
        city=req.city,
        radar=radar,
        summary=summary,
    )
