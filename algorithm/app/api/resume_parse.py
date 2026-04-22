from __future__ import annotations

import json
import os
import re
import tempfile
from typing import Any

import pdfplumber
import pytesseract
import requests
from docx import Document
from fastapi import APIRouter, File, HTTPException, UploadFile
from PIL import Image

router = APIRouter()

AI_API_URL = os.getenv("AI_API_URL", "https://api.siliconflow.cn/v1/chat/completions")
AI_API_KEY = os.getenv("AI_API_KEY", "")
AI_MODEL = os.getenv("AI_MODEL", "deepseek-ai/DeepSeek-V3")

MAX_TEXT_LENGTH = 12000
LIST_SPLIT_RE = re.compile(r"[,，、/；;|\n]+")
DOUBLE_SPACE_RE = re.compile(r"\s{2,}")

SKILL_DICT = [
    "Java", "Python", "JavaScript", "TypeScript", "C++", "C#", "Go", "Rust", "PHP", "Ruby",
    "Spring Boot", "Spring Cloud", "Spring", "Django", "Flask", "FastAPI",
    "Vue", "React", "Angular", "Node.js", "Next.js",
    "MySQL", "PostgreSQL", "Oracle", "SQL Server", "MongoDB", "Redis", "Elasticsearch",
    "Docker", "Kubernetes", "K8s", "Linux", "Nginx", "Git", "Jenkins", "CI/CD",
    "Kafka", "RabbitMQ", "Flink", "Spark", "Hadoop",
    "REST", "GraphQL", "gRPC", "微服务", "分布式", "高并发",
    "机器学习", "深度学习", "NLP", "自然语言处理", "计算机视觉", "AI",
    "产品策划", "产品设计", "用户研究", "数据分析", "运营", "新媒体运营",
    "品牌策划", "市场营销", "销售管理", "商务拓展", "财务分析", "审计",
    "招聘", "薪酬绩效", "组织发展", "培训", "英语", "日语"
]

FIELD_LABELS = {
    "name": ["姓名", "名字", "name"],
    "phone": ["手机", "手机号", "电话", "联系方式", "mobile", "phone"],
    "email": ["邮箱", "邮件", "email", "e-mail"],
    "target_job_type": ["目标岗位", "目标职位", "意向岗位", "求职岗位", "应聘岗位", "target job"],
    "current_job": ["当前岗位", "当前职位", "现岗位", "现职位", "current job"],
    "target_city": ["目标城市", "意向城市", "期望城市", "目标地区", "target city"],
    "industry": ["目标行业", "意向行业", "行业方向", "所属行业", "industry"],
    "education": ["学历", "最高学历", "教育背景", "education"],
    "experience_years": ["工作年限", "经验年限", "从业年限", "experience years"],
    "resume_text": ["经历摘要", "自我评价", "个人总结", "个人简介", "profile summary"],
}

SKILL_LABELS = ["核心技能", "专业技能", "技能", "技能标签", "专业能力", "工具 / 证书", "工具/证书"]
AWARD_LABELS = ["荣誉奖项", "获奖奖项", "奖项荣誉", "竞赛奖项", "荣誉经历"]
CERTIFICATE_LABELS = ["资格证书", "证书", "职业证书"]


def extract_text_from_file(file_path: str, filename: str) -> str:
    ext = filename.lower().rsplit(".", 1)[-1]
    text = ""

    if ext == "pdf":
        with pdfplumber.open(file_path) as pdf:
            for page in pdf.pages:
                page_text = page.extract_text()
                if page_text:
                    text += page_text + "\n"
    elif ext in {"jpg", "jpeg", "png", "webp"}:
        img = Image.open(file_path)
        text = pytesseract.image_to_string(img, lang="chi_sim+eng")
    elif ext in {"txt", "md", "json", "csv"}:
        with open(file_path, "r", encoding="utf-8", errors="ignore") as file:
            text = file.read()
    elif ext == "docx":
        doc = Document(file_path)
        text = "\n".join(p.text for p in doc.paragraphs if p.text and p.text.strip())
    else:
        raise ValueError(f"Unsupported file format: {ext}")

    return normalize_text(text)


def normalize_text(text: str) -> str:
    cleaned = (text or "").replace("\ufeff", "").replace("\r\n", "\n").strip()
    return cleaned[:MAX_TEXT_LENGTH]


def first_text(*values: Any) -> str | None:
    for value in values:
        if isinstance(value, str) and value.strip():
            return value.strip()
    return None


def unique_list(items: list[str], limit: int | None = None) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for item in items:
        value = item.strip()
        if not value:
            continue
        key = value.casefold()
        if key in seen:
            continue
        seen.add(key)
        result.append(value)
        if limit and len(result) >= limit:
            break
    return result


def split_items(value: str) -> list[str]:
    if not value:
        return []
    parts = LIST_SPLIT_RE.split(value)
    if len(parts) == 1 and "  " in value:
        parts = DOUBLE_SPACE_RE.split(value)
    return unique_list(parts)


def find_labeled_value(text: str, labels: list[str]) -> str | None:
    for label in labels:
        pattern = re.compile(
            rf"(?im)^\s*[-*•]?\s*{re.escape(label)}\s*[:：]\s*(.+?)\s*$"
        )
        match = pattern.search(text)
        if match:
            return match.group(1).strip()
    return None


def find_multiline_block(text: str, labels: list[str]) -> str | None:
    lines = text.split("\n")
    for index, raw_line in enumerate(lines):
        line = raw_line.strip()
        for label in labels:
            if not re.match(rf"^[-*•]?\s*{re.escape(label)}\s*[:：].*$", line, re.IGNORECASE):
                continue

            inline = re.sub(rf"^[-*•]?\s*{re.escape(label)}\s*[:：]\s*", "", line, flags=re.IGNORECASE).strip()
            chunks = [inline] if inline else []
            for next_index in range(index + 1, len(lines)):
                next_line = lines[next_index].strip()
                if not next_line:
                    if chunks:
                        break
                    continue
                if re.match(r"^#{1,6}\s+.*$", next_line):
                    break
                if re.match(r"^[-*•]?\s*[\u4e00-\u9fa5A-Za-z0-9 /()（）-]+\s*[:：].*$", next_line):
                    break
                chunks.append(next_line)
            if chunks:
                return "\n".join(chunks).strip()
    return None


def parse_labeled_list(text: str, labels: list[str]) -> list[str]:
    values: list[str] = []
    inline = find_labeled_value(text, labels)
    if inline:
        values.extend(split_items(inline))

    block = find_multiline_block(text, labels)
    if block:
        for line in block.split("\n"):
            cleaned = re.sub(r"^[-*•\s]+", "", line).strip()
            values.extend(split_items(cleaned) if len(cleaned) < 120 else [cleaned])

    return unique_list(values)


def infer_education(text: str) -> str | None:
    education_levels = ["博士", "硕士", "研究生", "本科", "大专", "专科", "中专", "MBA", "EMBA"]
    for edu in education_levels:
        if edu in text:
            return edu
    return None


def infer_experience_years(text: str) -> float | None:
    labeled = find_labeled_value(text, FIELD_LABELS["experience_years"])
    if labeled:
        match = re.search(r"(\d+(?:\.\d+)?)", labeled)
        if match:
            return float(match.group(1))

    patterns = [
        r"(\d+(?:\.\d+)?)\s*年(?:工作|从业|经验)",
        r"(?:工作|从业|经验).*?(\d+(?:\.\d+)?)\s*年",
        r"(\d+)\s*个月(?:工作|从业|经验)",
    ]
    for pattern in patterns:
        match = re.search(pattern, text, re.IGNORECASE)
        if not match:
            continue
        if "个月" in match.group(0):
            return round(float(match.group(1)) / 12, 1)
        return float(match.group(1))

    return None


def extract_contact_fields(text: str, result: dict[str, Any]) -> None:
    if "phone" not in result:
        phone_match = re.search(r"(1[3-9]\d{9})", text)
        if phone_match:
            result["phone"] = phone_match.group(1)

    if "email" not in result:
        email_match = re.search(r"[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}", text)
        if email_match:
            result["email"] = email_match.group(0)

    if "name" not in result:
        for line in text.split("\n")[:8]:
            trimmed = line.strip()
            if re.fullmatch(r"[\u4e00-\u9fa5]{2,4}", trimmed):
                result["name"] = trimmed
                break


def extract_skill_keywords(text: str) -> list[str]:
    text_lower = text.lower()
    return unique_list([skill for skill in SKILL_DICT if skill.lower() in text_lower], limit=24)


def contains_any(text: str, tokens: list[str]) -> bool:
    return any(token in text for token in tokens)


def infer_award_level(text: str) -> str:
    if contains_any(text, ["省级", "省赛", "自治区", "直辖市"]):
        return "省级"
    if contains_any(text, ["市级", "行业", "区域"]):
        return "市级/行业级"
    if contains_any(text, ["市级", "行业", "区域"]):
        return "市级/行业级"
    if contains_any(text, ["校级", "院级", "院系"]):
        return "校级"
    if contains_any(text, ["国家级", "全国", "国赛", "国家"]):
        return "国家级"
    return "未识别级别"


def infer_award_rank(text: str) -> str:
    if contains_any(text, ["特等奖"]):
        return "特等奖"
    if contains_any(text, ["一等奖", "金奖", "冠军"]):
        return "一等奖/金奖"
    if contains_any(text, ["二等奖", "银奖", "亚军"]):
        return "二等奖/银奖"
    if contains_any(text, ["三等奖", "铜奖", "季军"]):
        return "三等奖/铜奖"
    if contains_any(text, ["优秀奖", "优胜奖", "入围", "入选"]):
        return "优秀奖/入围"
    return "未识别奖项等级"


def infer_award_authority(text: str) -> str:
    if contains_any(text, ["教育部", "人社部", "工信部", "团中央", "国家级"]):
        return "高权威主办"
    if contains_any(text, ["省教育厅", "省人社厅", "省级"]):
        return "省级主管部门"
    if contains_any(text, ["行业协会", "学会", "联盟", "行业"]):
        return "行业组织"
    if contains_any(text, ["学校", "学院", "大学", "校级"]):
        return "校内组织"
    return "主办方待识别"


def score_award(text: str) -> int:
    score = 45

    if contains_any(text, ["国家级", "全国", "国赛", "国家"]):
        score += 32
    elif contains_any(text, ["省级", "省赛", "自治区", "直辖市"]):
        score += 24
    elif contains_any(text, ["市级", "行业", "区域"]):
        score += 16
    elif contains_any(text, ["校级", "院级", "院系"]):
        score += 8

    if contains_any(text, ["特等奖"]):
        score += 14
    elif contains_any(text, ["一等奖", "金奖", "冠军"]):
        score += 10
    elif contains_any(text, ["二等奖", "银奖", "亚军"]):
        score += 7
    elif contains_any(text, ["三等奖", "铜奖", "季军"]):
        score += 4
    elif contains_any(text, ["优秀奖", "优胜奖", "入围", "入选"]):
        score += 2

    return min(score, 99)


def explain_award_score(level: str, rank: str, authority: str, score: int) -> str:
    return f"按“赛事级别 + 奖项等级 + 主办方权威度”做规则评分，当前判断为 {level} / {rank} / {authority}，综合分 {score}。"


def build_award_insights(items: list[str]) -> list[dict[str, Any]]:
    awards: list[dict[str, Any]] = []
    for item in items:
        level = infer_award_level(item)
        rank = infer_award_rank(item)
        authority = infer_award_authority(item)
        score = score_award(item)
        awards.append({
            "name": item,
            "level": level,
            "rank": rank,
            "authority": authority,
            "score": score,
            "explanation": explain_award_score(level, rank, authority, score),
        })
    awards.sort(key=lambda row: row["score"], reverse=True)
    return awards


def extract_structured_resume(text: str) -> dict[str, Any]:
    normalized = normalize_text(text)
    if not normalized:
        return {}

    result: dict[str, Any] = {"text": normalized}

    for field, labels in FIELD_LABELS.items():
        value = find_labeled_value(normalized, labels)
        if value:
            result[field] = value

    summary = find_multiline_block(normalized, FIELD_LABELS["resume_text"])
    if summary:
        result["resume_text"] = summary
    elif "resume_text" not in result:
        result["resume_text"] = normalized[:1800]

    extract_contact_fields(normalized, result)

    education = first_text(result.get("education"), infer_education(normalized))
    if education:
        result["education"] = education

    experience_years = infer_experience_years(normalized)
    if experience_years is not None:
        result["experience_years"] = experience_years
        result["experience"] = f"{experience_years:g}年"

    skills = unique_list(
        parse_labeled_list(normalized, SKILL_LABELS) + extract_skill_keywords(normalized),
        limit=24,
    )
    if skills:
        result["skills"] = skills

    awards = build_award_insights(parse_labeled_list(normalized, AWARD_LABELS))
    if awards:
        result["awards"] = awards

    certificates = parse_labeled_list(normalized, CERTIFICATE_LABELS)
    if certificates:
        result["certificates"] = certificates

    result["targetJob"] = result.get("target_job_type")
    result["currentJob"] = result.get("current_job")
    result["targetCity"] = result.get("target_city")
    result["resumeText"] = result.get("resume_text")
    result["experienceYears"] = result.get("experience_years")

    recognized_fields = [
        field
        for field in [
            "name", "phone", "email", "target_job_type", "current_job", "target_city",
            "industry", "education", "experience_years", "skills", "awards", "certificates"
        ]
        if result.get(field)
    ]
    result["parse_meta"] = {
        "parser_mode": "rule_based_local",
        "recognized_count": len(recognized_fields),
        "recognized_fields": recognized_fields,
        "text_length": len(normalized),
        "award_scoring": "rule_based_level_rank_authority",
    }
    return result


def extract_json_object(content: str) -> dict[str, Any]:
    cleaned = content.strip()
    if cleaned.startswith("```json"):
        cleaned = cleaned[7:]
    if cleaned.startswith("```"):
        cleaned = cleaned[3:]
    if cleaned.endswith("```"):
        cleaned = cleaned[:-3]
    return json.loads(cleaned.strip())


def request_llm_enrichment(text: str) -> dict[str, Any]:
    if not AI_API_KEY:
        return {}

    prompt = f"""
你是一名严谨的中文简历结构化分析助手。请从下面简历文本中提取字段，并只返回合法 JSON。

要求：
1. 仅输出 JSON，不要输出解释、markdown 或代码块。
2. 缺失字段填 null 或空数组，不要编造。
3. skills 必须是数组。
4. awards 必须是对象数组，每项包含 name、level、rank。
5. certificates 必须是字符串数组。

JSON 字段：
{{
  "target_job_type": "目标岗位",
  "current_job": "当前岗位",
  "target_city": "目标城市",
  "industry": "目标行业",
  "education": "学历",
  "experience_years": 0,
  "skills": ["技能1", "技能2"],
  "awards": [{{"name": "奖项名称", "level": "国家级/省级/市级/校级/未知", "rank": "特等奖/一等奖/二等奖/三等奖/优秀奖/未知"}}],
  "certificates": ["证书1", "证书2"]
}}

简历文本：
{text[:5000]}
"""

    headers = {
        "Authorization": f"Bearer {AI_API_KEY}",
        "Content-Type": "application/json",
    }
    payload = {
        "model": AI_MODEL,
        "messages": [{"role": "user", "content": prompt}],
        "temperature": 0.1,
    }

    response = requests.post(AI_API_URL, headers=headers, json=payload, timeout=30)
    response.raise_for_status()
    content = response.json()["choices"][0]["message"]["content"]
    return extract_json_object(content)


def merge_llm_enrichment(base: dict[str, Any], llm_data: dict[str, Any]) -> dict[str, Any]:
    if not llm_data:
        return base

    merged = dict(base)
    for key in ["target_job_type", "current_job", "target_city", "industry", "education"]:
        merged[key] = first_text(base.get(key), llm_data.get(key)) or first_text(llm_data.get(key), base.get(key))

    if merged.get("experience_years") is None and llm_data.get("experience_years") is not None:
        try:
            merged["experience_years"] = float(llm_data["experience_years"])
            merged["experience"] = f"{float(llm_data['experience_years']):g}年"
        except (TypeError, ValueError):
            pass

    llm_skills = unique_list([item for item in llm_data.get("skills", []) if isinstance(item, str)], limit=24)
    merged["skills"] = unique_list(base.get("skills", []) + llm_skills, limit=24)

    if not merged.get("awards") and isinstance(llm_data.get("awards"), list):
        award_names = [item.get("name", "").strip() for item in llm_data["awards"] if isinstance(item, dict)]
        merged["awards"] = build_award_insights(unique_list(award_names))

    if not merged.get("certificates") and isinstance(llm_data.get("certificates"), list):
        merged["certificates"] = unique_list([item for item in llm_data["certificates"] if isinstance(item, str)])

    merged["targetJob"] = merged.get("target_job_type")
    merged["currentJob"] = merged.get("current_job")
    merged["targetCity"] = merged.get("target_city")
    merged["resumeText"] = merged.get("resume_text")
    merged["experienceYears"] = merged.get("experience_years")

    parse_meta = dict(merged.get("parse_meta", {}))
    parse_meta["parser_mode"] = "rule_based_plus_llm" if AI_API_KEY else "rule_based_local"
    merged["parse_meta"] = parse_meta
    return merged


def parse_resume(text: str) -> dict[str, Any]:
    base = extract_structured_resume(text)
    if not base:
        return {}

    if not AI_API_KEY:
        return base

    try:
        llm_data = request_llm_enrichment(text)
        return merge_llm_enrichment(base, llm_data)
    except Exception as exc:
        parse_meta = dict(base.get("parse_meta", {}))
        parse_meta["llm_enrichment_error"] = str(exc)
        base["parse_meta"] = parse_meta
        return base


@router.post("/parse")
async def parse_resume_upload(file: UploadFile = File(...)):
    if not file.filename:
        raise HTTPException(status_code=400, detail="No file uploaded")

    tmp_path = ""
    try:
        suffix = "." + file.filename.rsplit(".", 1)[-1] if "." in file.filename else ""
        fd, tmp_path = tempfile.mkstemp(suffix=suffix)
        with os.fdopen(fd, "wb") as temp_file:
            temp_file.write(await file.read())

        text = extract_text_from_file(tmp_path, file.filename)
        if len(text.strip()) < 10:
            raise HTTPException(status_code=400, detail="未从文件中识别到足够的有效文本，请检查文件是否为可复制文本或清晰扫描件。")

        parsed_data = parse_resume(text)
        parsed_data["raw_text_length"] = len(text)
        return parsed_data
    except HTTPException:
        raise
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    finally:
        if tmp_path and os.path.exists(tmp_path):
            os.remove(tmp_path)
