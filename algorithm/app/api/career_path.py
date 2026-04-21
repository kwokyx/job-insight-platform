import json
from collections import Counter
from typing import List, Optional

from fastapi import APIRouter
from pydantic import BaseModel

from app.db import execute_query
from app.skill_normalizer import filter_skills_by_family, infer_skill_family, normalize_job_labels, normalize_skill_tokens

router = APIRouter()


class CareerPathRequest(BaseModel):
    current_job: Optional[str] = None
    target_job: str
    current_skills: List[str] = []
    city: Optional[str] = None


class CareerPathStep(BaseModel):
    from_role: str
    to_role: str
    transition_type: str
    avg_years: float
    required_skills: List[str]
    gap_skills: List[str]
    market_heat: int


class CareerPathResponse(BaseModel):
    current_job: Optional[str] = None
    target_job: str
    city: Optional[str] = None
    direction: str
    steps: List[CareerPathStep]
    milestones: List[dict]
    recommended_projects: List[dict]
    timeline_summary: str
    strategy_summary: dict
    market_signals: List[str]


def _fallback_role_keywords(direction: str) -> List[str]:
    mapping = {
        "backend": ["Java", "Spring Boot", "后端"],
        "frontend": ["前端", "Vue", "React"],
        "data": ["Python", "SQL", "数据"],
        "qa": ["测试", "QA", "自动化测试"],
        "general": [],
    }
    return mapping.get(direction, [])


def _infer_direction(current_job: Optional[str], target_job: str, current_skills: List[str]) -> str:
    return infer_skill_family([current_job or "", target_job or "", *(current_skills or [])])


def _query_market_rows(keywords: List[str], city: Optional[str], limit: int = 500) -> List[dict]:
    conditions = ["job_labels IS NOT NULL"]
    params = {"limit": limit}
    keyword_clauses = []
    for index, keyword in enumerate([keyword for keyword in keywords if keyword][:4]):
        key = f"job_keyword_{index}"
        params[key] = f"%{keyword}%"
        keyword_clauses.append(f"title LIKE :{key}")
    if keyword_clauses:
        conditions.append("(" + " OR ".join(keyword_clauses) + ")")
    if city:
        conditions.append("(COALESCE(city, job_city) LIKE :city OR job_city LIKE :city)")
        params["city"] = f"%{city}%"

    return execute_query(
        f"""
        SELECT title, job_labels
        FROM biz_job_posting
        WHERE {' AND '.join(conditions)}
        ORDER BY publish_date DESC, id DESC
        LIMIT :limit
        """,
        params,
    )


def _market_skills(job_keyword: str, city: Optional[str], limit: int = 12) -> List[str]:
    direction = _infer_direction(None, job_keyword, [])
    keywords = [job_keyword, *_fallback_role_keywords(direction)]
    rows = _query_market_rows(keywords, city, limit=500)
    if len(rows) < 20:
        broader_rows = _query_market_rows(_fallback_role_keywords(direction), None, limit=500)
        if broader_rows:
            rows = broader_rows
    counter: Counter = Counter()
    for row in rows:
        for token in filter_skills_by_family(normalize_job_labels(row["job_labels"]), direction):
            counter[token] += 1
    return [skill for skill, _ in counter.most_common(limit)]


def _market_heat(job_keyword: str, city: Optional[str]) -> int:
    direction = _infer_direction(None, job_keyword, [])
    keywords = [job_keyword, *_fallback_role_keywords(direction)]
    conditions = []
    params = {}
    keyword_clauses = []
    for index, keyword in enumerate([keyword for keyword in keywords if keyword][:4]):
        key = f"job_keyword_{index}"
        params[key] = f"%{keyword}%"
        keyword_clauses.append(f"title LIKE :{key}")
    if keyword_clauses:
        conditions.append("(" + " OR ".join(keyword_clauses) + ")")
    if city:
        conditions.append("(COALESCE(city, job_city) LIKE :city OR job_city LIKE :city)")
        params["city"] = f"%{city}%"
    where_clause = " AND ".join(conditions) if conditions else "1=1"
    rows = execute_query(f"SELECT COUNT(*) AS total FROM biz_job_posting WHERE {where_clause}", params)
    total = int(rows[0]["total"]) if rows else 0
    if total == 0 and city:
        return _market_heat(job_keyword, None)
    return total


def _career_path_rows(target_job: str) -> List[dict]:
    return execute_query(
        """
        SELECT
            job_title_from,
            job_title_to,
            transition_type,
            avg_years,
            required_skills,
            frequency
        FROM biz_career_path
        WHERE job_title_to LIKE :target_job OR job_title_from LIKE :target_job
        ORDER BY frequency DESC, avg_years ASC
        LIMIT 8
        """,
        {"target_job": f"%{target_job}%"},
    )


def _heuristic_bridge_roles(direction: str, current_job: Optional[str], target_job: str) -> List[str]:
    base = [current_job.strip()] if current_job and current_job.strip() else []
    bridge_map = {
        "backend": ["初级后端工程师", "后端开发工程师", target_job],
        "frontend": ["前端开发工程师", "高级前端工程师", target_job],
        "data": ["数据分析师", "数据开发工程师", target_job],
        "qa": ["测试工程师", "自动化测试工程师", target_job],
        "general": ["通用技术岗位", target_job],
    }
    chain = base + bridge_map.get(direction, ["通用技术岗位", target_job])
    deduped: List[str] = []
    for role in chain:
        if role and role not in deduped:
            deduped.append(role)
    if deduped[-1] != target_job:
        deduped.append(target_job)
    return deduped


@router.post("/path", response_model=CareerPathResponse)
def career_path(req: CareerPathRequest):
    current_skills = normalize_skill_tokens(req.current_skills)
    current_skill_set = {skill.lower() for skill in current_skills}
    direction = _infer_direction(req.current_job, req.target_job, current_skills)
    raw_paths = _career_path_rows(req.target_job)
    steps: List[CareerPathStep] = []

    for row in raw_paths:
        try:
            required_skills_raw = json.loads(row.get("required_skills") or "[]")
            if not isinstance(required_skills_raw, list):
                required_skills_raw = []
        except Exception:
            required_skills_raw = []
        normalized_skills = filter_skills_by_family(
            normalize_skill_tokens([value for value in required_skills_raw if isinstance(value, str)]),
            direction,
        )
        if not normalized_skills:
            normalized_skills = _market_skills(row["job_title_to"], req.city, limit=6)
        gap_skills = [skill for skill in normalized_skills if skill.lower() not in current_skill_set][:6]
        steps.append(
            CareerPathStep(
                from_role=row["job_title_from"],
                to_role=row["job_title_to"],
                transition_type=row.get("transition_type") or "TRANSITION",
                avg_years=float(row.get("avg_years") or 1.5),
                required_skills=normalized_skills[:6],
                gap_skills=gap_skills,
                market_heat=int(row.get("frequency") or 0),
            )
        )

    if not steps:
        roles = _heuristic_bridge_roles(direction, req.current_job, req.target_job)
        for index in range(len(roles) - 1):
            to_role = roles[index + 1]
            required_skills = _market_skills(to_role, req.city, limit=6)
            gap_skills = [skill for skill in required_skills if skill.lower() not in current_skill_set][:6]
            steps.append(
                CareerPathStep(
                    from_role=roles[index],
                    to_role=to_role,
                    transition_type="PROMOTION" if direction != "general" and index == len(roles) - 2 else "TRANSITION",
                    avg_years=1.5 if index == len(roles) - 2 else 1.0,
                    required_skills=required_skills,
                    gap_skills=gap_skills,
                    market_heat=_market_heat(to_role, req.city),
                )
            )

    milestones = []
    for index, step in enumerate(steps):
        milestones.append(
            {
                "stage": f"阶段 {index + 1}",
                "goal": f"为 {step.to_role} 准备可验证的能力证据",
                "focus": step.gap_skills[:3] or step.required_skills[:3],
                "deliverable": "1 个项目案例 + 1 版定向简历 + 1 组面试表达素材",
            }
        )

    recommended_projects = []
    target_skills = _market_skills(req.target_job, req.city, limit=5)
    recommended_projects.append(
        {
            "name": f"{req.target_job} 定向作品项目",
            "goal": "用完整项目证明你具备目标岗位最常见的技术栈与交付能力",
            "stack": target_skills,
        }
    )
    if direction == "backend":
        recommended_projects.append(
            {
                "name": "高并发业务系统项目",
                "goal": "补齐服务设计、数据库建模、接口治理与性能优化证据",
                "stack": ["Java", "Spring Boot", "MySQL", "Redis"],
            }
        )
    elif direction == "data":
        recommended_projects.append(
            {
                "name": "业务指标分析看板项目",
                "goal": "补齐数据抽取、分析建模、指标解释与汇报表达能力",
                "stack": ["Python", "SQL", "BI"],
            }
        )

    total_months_low = max(3, int(sum(step.avg_years for step in steps) * 6))
    total_months_high = max(total_months_low + 2, int(sum(step.avg_years for step in steps) * 9))
    return CareerPathResponse(
        current_job=req.current_job,
        target_job=req.target_job,
        city=req.city,
        direction=direction,
        steps=steps,
        milestones=milestones,
        recommended_projects=recommended_projects,
        timeline_summary=f"预计需要经过 {len(steps)} 个阶段，整体转型周期大约 {total_months_low} 到 {total_months_high} 个月。",
        strategy_summary={
            "path_source": "career_path_table" if raw_paths else "heuristic_market_path",
            "highest_gap_skills": list(dict.fromkeys([skill for step in steps for skill in step.gap_skills]))[:6],
            "execution_strategy": "优先补齐每阶段前三个缺口技能，再把补齐结果沉淀为项目证据后进入下一阶段。",
        },
        market_signals=target_skills,
    )
