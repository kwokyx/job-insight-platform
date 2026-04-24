from collections import Counter, defaultdict
from typing import Dict, List, Optional
import re

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
    type: Optional[str] = None
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

DISPLAY_COMMUNITY_KEYWORDS = {
    "后端工程": ["java", "spring", "spring boot", "mysql", "redis", "docker", "kafka"],
    "前端工程": ["vue", "react", "javascript", "typescript", "css", "html", "node.js"],
    "数据方向": ["python", "sql", "spark", "flink", "hadoop", "pandas", "bi"],
    "测试质量": ["selenium", "jmeter", "postman", "自动化测试", "接口测试"],
    "平台运维": ["linux", "docker", "kubernetes", "jenkins", "nginx"],
}

GRAPH_FAMILY_KEYWORDS = {
    "软件开发": ["java", "python", "c++", "c#", "javascript", "typescript", "vue", "react", "spring", "mysql", "redis", "sql", "oracle", "postgresql", "mongodb"],
    "数据智能": ["人工智能", "机器学习", "深度学习", "大数据", "数据分析", "数据挖掘", "python", "sql", "spark", "flink", "hadoop", "hive", "bi"],
    "云平台运维": ["docker", "kubernetes", "linux", "jenkins", "nginx", "devops", "云计算", "网络安全", "信息安全", "运维"],
    "制造自动化": ["工业自动化", "自动化", "机械", "电气", "机电", "plc", "数控", "模具", "工艺", "设备", "制造"],
    "工程建设": ["工程施工", "工程管理", "土木", "建筑", "造价", "测绘", "暖通", "给排水", "结构设计", "施工"],
    "医疗健康": ["护理", "临床", "药学", "检验", "影像", "康复", "口腔", "器械", "医疗", "生物"],
    "财务风控": ["财务", "审计", "税务", "会计", "风控", "法务", "内控", "证券", "投融资"],
    "供应链物流": ["物流", "仓储", "采购", "供应链", "报关", "运输", "货运", "计划", "质检"],
}

GRAPH_EXCLUDE_KEYWORDS = [
    "销售", "客户", "咨询服务", "企业服务", "互联网", "银行", "保险", "电子商务", "零售", "批发",
    "房地产", "门店", "福利", "带薪", "全白班", "全职", "节日", "客服", "电话", "面销", "陌拜",
    "渠道", "抖音", "新媒体", "企业客户", "个人客户", "快消", "租赁服务", "会骑电动车",
    "it服务", "o2o", "2b", "2c", "4s店", "spa", "dom", "spd", "cpa", "cta",
    "b端", "c端", "驾驶证",
]

GRAPH_MIN_COUNT = 3

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


def _graph_skill_score(skill: str) -> int:
    if not skill:
        return 0
    lowered = skill.lower()
    if any(keyword.lower() in lowered or keyword in skill for keyword in GRAPH_EXCLUDE_KEYWORDS):
        return 0
    if re.fullmatch(r"[A-Z0-9]{2,6}", skill) and skill.lower() not in {"java", "python", "sql", "c++", "c#"}:
        return 0
    if skill.endswith("服务") or skill.endswith("客户") or skill.endswith("门店") or skill.endswith("4S店"):
        return 0

    family_hits = 0
    for keywords in GRAPH_FAMILY_KEYWORDS.values():
        if any(keyword.lower() in lowered or keyword in skill for keyword in keywords):
            family_hits += 1

    score = family_hits * 3
    if re.search(r"[A-Za-z+#.]", skill):
        score += 2
    if "/" in skill and family_hits > 0:
        score += 1
    return score


def _infer_graph_family(skill: str) -> str:
    lowered = skill.lower()
    best_family = "通用专业"
    best_score = 0
    for family, keywords in GRAPH_FAMILY_KEYWORDS.items():
        score = sum(1 for keyword in keywords if keyword.lower() in lowered or keyword in skill)
        if score > best_score:
            best_family = family
            best_score = score
    return best_family


def _select_balanced_top_skills(skill_counter: Counter, top_n: int) -> List[str]:
    family_buckets: Dict[str, List[tuple[str, int, int]]] = defaultdict(list)
    for skill, count in skill_counter.items():
        if count < GRAPH_MIN_COUNT:
            continue
        score = _graph_skill_score(skill)
        if score <= 0:
            continue
        family_buckets[_infer_graph_family(skill)].append((skill, count, score))

    if not family_buckets:
        return []

    for family in family_buckets:
        family_buckets[family].sort(key=lambda item: (-item[2], -item[1], item[0].lower()))

    family_order = sorted(
        family_buckets.keys(),
        key=lambda family: (-len(family_buckets[family]), family)
    )

    selected: List[str] = []
    while len(selected) < top_n:
        progressed = False
        for family in family_order:
            bucket = family_buckets[family]
            if not bucket:
                continue
            skill, _, _ = bucket.pop(0)
            selected.append(skill)
            progressed = True
            if len(selected) >= top_n:
                break
        if not progressed:
            break
    return selected


def _is_graph_skill_candidate(skill: str) -> bool:
    if not skill:
        return False
    if re.search(r"[A-Za-z+#.]", skill):
        return True
    return _graph_skill_score(skill) > 0


def _load_job_skill_tokens(limit: int = 5000) -> List[List[str]]:
    rows = execute_query(
        """
        SELECT latest.id AS job_id, d.label_name AS skill
        FROM (
            SELECT id
            FROM biz_job_posting
            ORDER BY publish_date DESC, id DESC
            LIMIT :limit
        ) latest
        JOIN job_label_rel r ON latest.id = r.job_posting_id
        JOIN job_label_dict d ON r.label_id = d.id
        WHERE d.label_name IS NOT NULL
          AND d.label_name != ''
          AND NOT EXISTS (
              SELECT 1 FROM job_welfare_dict w WHERE w.welfare_name = d.label_name
          )
          AND NOT EXISTS (
              SELECT 1 FROM biz_job_posting jp2 WHERE jp2.education_need = d.label_name
          )
          AND NOT EXISTS (
              SELECT 1 FROM biz_job_posting jp3 WHERE jp3.experience_year = d.label_name
          )
        ORDER BY latest.id DESC, d.label_name ASC
        """,
        {"limit": limit},
    )
    grouped: dict[int, list[str]] = defaultdict(list)
    for row in rows:
        skill = str(row.get("skill") or "").strip()
        if not skill:
            continue
        grouped[int(row["job_id"])].append(skill)

    token_rows: List[List[str]] = []
    for raw_skills in grouped.values():
        tokens = [
            token
            for token in normalize_skill_tokens(raw_skills)
            if _is_graph_skill_candidate(token)
        ]
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


def _infer_community_name(skills: List[str]) -> str:
    skills_lower = {value.lower() for value in skills}
    best_name = "通用技能栈"
    best_score = 0
    for name, keywords in DISPLAY_COMMUNITY_KEYWORDS.items():
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

    candidate_skills = _select_balanced_top_skills(skill_counter, max(top_n * 2, top_n))
    if not candidate_skills:
        return SkillGraphResponse(nodes=[], edges=[], total_skills=0, total_relations=0)

    skill_graph = nx.Graph()
    for skill in candidate_skills:
        skill_graph.add_node(skill, count=skill_counter[skill])
    for (left, right), count in co_counter.most_common(240):
        if left in skill_graph and right in skill_graph and count >= 2:
            skill_graph.add_edge(left, right, weight=float(count))

    isolated_nodes = [
        node for node in list(skill_graph.nodes)
        if skill_graph.degree(node) == 0 and skill_counter.get(node, 0) < 8
    ]
    if isolated_nodes:
        skill_graph.remove_nodes_from(isolated_nodes)

    if skill_graph.number_of_nodes() == 0:
        return SkillGraphResponse(nodes=[], edges=[], total_skills=0, total_relations=0)

    ranked_skills = sorted(
        list(skill_graph.nodes),
        key=lambda item: (-skill_counter[item], item.lower())
    )[:top_n]
    ranked_set = set(ranked_skills)
    skill_graph = skill_graph.subgraph(ranked_set).copy()

    pagerank = nx.pagerank(skill_graph, weight="weight") if skill_graph.number_of_nodes() else {}
    family_groups: Dict[str, List[str]] = defaultdict(list)
    for skill in ranked_skills:
        if skill in skill_graph:
            family_groups[_infer_graph_family(skill)].append(skill)

    communities: List[SkillCommunity] = []
    community_map: Dict[str, int] = {}
    for community_id, (family, skills_in_family) in enumerate(
        sorted(family_groups.items(), key=lambda item: (-len(item[1]), item[0]))
    ):
        ordered_skills = sorted(skills_in_family, key=lambda item: (-skill_counter[item], item.lower()))
        communities.append(
            SkillCommunity(
                id=community_id,
                name=family,
                skills=ordered_skills[:15],
                color=COMMUNITY_COLORS[community_id % len(COMMUNITY_COLORS)],
            )
        )
        for skill in ordered_skills:
            community_map[skill] = community_id

    nodes: List[GraphNode] = []
    domain_ids: Dict[str, int] = {}
    next_id = 0
    for community in communities:
        domain_ids[community.name] = next_id
        nodes.append(
            GraphNode(
                id=next_id,
                name=community.name,
                count=sum(skill_counter.get(skill, 0) for skill in community.skills),
                type="domain",
                category=community.name,
                pagerank=0.0,
                community=community.id,
            )
        )
        next_id += 1

    for skill in ranked_skills:
        if skill not in skill_graph:
            continue
        family = _infer_graph_family(skill)
        nodes.append(
            GraphNode(
                id=next_id,
                name=skill,
                count=skill_counter[skill],
                type="skill",
                category=family,
                pagerank=round(pagerank.get(skill, 0.0), 6),
                community=community_map.get(skill, -1),
            )
        )
        next_id += 1

    edges: List[GraphEdge] = []
    for family, skills_in_family in family_groups.items():
        for skill in skills_in_family:
            edges.append(
                GraphEdge(
                    source=family,
                    target=skill,
                    weight=float(max(1, min(skill_counter.get(skill, 0), 12))),
                )
            )
    for left, right, data in skill_graph.edges(data=True):
        if _infer_graph_family(left) != _infer_graph_family(right) and float(data.get("weight", 0.0)) < 4:
            continue
        edges.append(
            GraphEdge(source=left, target=right, weight=float(data.get("weight", 1.0)))
        )

    hub_skills = [
        HubSkill(skill=skill, pagerank=round(score, 6), community=community_map.get(skill, -1))
        for skill, score in sorted(pagerank.items(), key=lambda item: -item[1])[:10]
    ]
    substitutes: List[SkillSubstitute] = []
    for community in communities[:4]:
        member_list = community.skills[:6]
        for index, left in enumerate(member_list):
            for right in member_list[index + 1:]:
                weight = skill_graph.get_edge_data(left, right, default={}).get("weight", 0.0)
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
