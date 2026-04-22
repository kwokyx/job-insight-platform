import json
import re
from typing import Iterable, List


SKILL_STOPWORDS = {
    "本科",
    "大专",
    "硕士",
    "博士",
    "学历",
    "经验",
    "不限",
    "应届",
    "校招",
    "社招",
    "双休",
    "五险一金",
    "年终奖",
    "带薪年假",
    "餐补",
    "房补",
    "交通补助",
    "通讯补助",
    "六险一金",
    "法定节假日",
    "大牛带队",
    "有餐补",
    "弹性工作",
    "岗位职责",
    "任职要求",
    "岗位要求",
    "职位描述",
    "岗位方向",
    "后端开发",
    "前端开发",
    "全栈开发",
    "服务端开发",
    "软件开发",
    "开发工程师",
    "后台开发",
    "互联网",
    "计算机软件",
}

SKILL_STOP_PATTERNS = [
    "福利",
    "补助",
    "补贴",
    "假期",
    "双休",
    "班车",
    "团建",
    "保险",
    "体检",
    "奖金",
    "节日",
    "晋升",
    "经验",
    "学历",
    "专业",
    "优先",
    "熟悉办公",
    "沟通能力",
    "责任心",
    "抗压",
    "加班",
]

SKILL_ALIASES = {
    "java": "Java",
    "spring": "Spring",
    "springboot": "Spring Boot",
    "spring boot": "Spring Boot",
    "springcloud": "Spring Cloud",
    "spring cloud": "Spring Cloud",
    "mysql": "MySQL",
    "redis": "Redis",
    "oracle": "Oracle",
    "sql server": "SQL Server",
    "sqlserver": "SQL Server",
    "postgres": "PostgreSQL",
    "postgresql": "PostgreSQL",
    "mongodb": "MongoDB",
    "elasticsearch": "Elasticsearch",
    "es": "Elasticsearch",
    "kafka": "Kafka",
    "rocketmq": "RocketMQ",
    "rabbitmq": "RabbitMQ",
    "sql": "SQL",
    "mybatis": "MyBatis",
    "mybatis plus": "MyBatis Plus",
    "mybatisplus": "MyBatis Plus",
    "maven": "Maven",
    "gradle": "Gradle",
    "docker": "Docker",
    "k8s": "Kubernetes",
    "kubernetes": "Kubernetes",
    "jenkins": "Jenkins",
    "linux": "Linux",
    "git": "Git",
    "nginx": "Nginx",
    "python": "Python",
    "pandas": "Pandas",
    "numpy": "NumPy",
    "flink": "Flink",
    "spark": "Spark",
    "hadoop": "Hadoop",
    "hive": "Hive",
    "java script": "JavaScript",
    "javascript": "JavaScript",
    "js": "JavaScript",
    "type script": "TypeScript",
    "typescript": "TypeScript",
    "ts": "TypeScript",
    "vue.js": "Vue",
    "vue": "Vue",
    "react.js": "React",
    "react": "React",
    "next.js": "Next.js",
    "nextjs": "Next.js",
    "node.js": "Node.js",
    "nodejs": "Node.js",
    "html": "HTML",
    "css": "CSS",
    "sass": "Sass",
    "less": "Less",
    "uniapp": "uni-app",
    "微信小程序": "微信小程序",
    "golang": "Go",
    "go": "Go",
    "cpp": "C++",
    "c++": "C++",
    "c#": "C#",
    "dotnet": ".NET",
    ".net": ".NET",
    "测试开发": "测试开发",
    "自动化测试": "自动化测试",
    "selenium": "Selenium",
    "jmeter": "JMeter",
    "postman": "Postman",
    "接口测试": "接口测试",
    "微服务": "微服务",
    "分布式": "分布式",
    "高并发": "高并发",
    "数据结构": "数据结构",
    "设计模式": "设计模式",
}

ROLE_DIRECTION_HINTS = {
    "backend",
    "frontend",
    "fullstack",
    "后端",
    "前端",
    "全栈",
    "服务端",
    "客户端",
    "开发",
    "工程师",
}

SKILL_FAMILY_KEYWORDS = {
    "backend": {
        "Java",
        "Spring",
        "Spring Boot",
        "Spring Cloud",
        "MySQL",
        "Redis",
        "MyBatis",
        "MyBatis Plus",
        "SQL",
        "Oracle",
        "PostgreSQL",
        "MongoDB",
        "Elasticsearch",
        "Kafka",
        "RocketMQ",
        "RabbitMQ",
        "Docker",
        "Kubernetes",
        "Linux",
        "Git",
        "Nginx",
        "Maven",
        "Gradle",
        "微服务",
        "分布式",
        "高并发",
        "设计模式",
    },
    "frontend": {
        "JavaScript",
        "TypeScript",
        "Vue",
        "React",
        "Next.js",
        "Node.js",
        "HTML",
        "CSS",
        "Sass",
        "Less",
        "uni-app",
        "微信小程序",
    },
    "data": {
        "Python",
        "SQL",
        "Pandas",
        "NumPy",
        "Spark",
        "Flink",
        "Hadoop",
        "Hive",
        "Elasticsearch",
    },
    "qa": {
        "自动化测试",
        "测试开发",
        "Selenium",
        "JMeter",
        "Postman",
        "接口测试",
        "Python",
        "Java",
    },
}


def _normalize_whitespace(value: str) -> str:
    return re.sub(r"\s+", " ", value.strip())


def _clean_token(token: str) -> str:
    value = _normalize_whitespace(token)
    if not value:
        return ""
    lower = value.lower()
    if lower in SKILL_ALIASES:
        return SKILL_ALIASES[lower]
    if value in SKILL_ALIASES:
        return SKILL_ALIASES[value]
    return value


def _is_noise_token(token: str) -> bool:
    lower = token.lower()
    if len(token) <= 1:
        return True
    if token in SKILL_STOPWORDS or lower in SKILL_STOPWORDS:
        return True
    if lower in ROLE_DIRECTION_HINTS:
        return True
    if re.fullmatch(r"\d+\+?年", token):
        return True
    if re.fullmatch(r"\d+\s*-\s*\d+\+?年", token):
        return True
    if re.fullmatch(r"\d+\+?", token):
        return True
    if re.search(r"(届|年龄|专业|学历|经验|优先|相关专业)$", token):
        return True
    return any(pattern in token for pattern in SKILL_STOP_PATTERNS)


def normalize_skill_tokens(values: Iterable[str]) -> List[str]:
    tokens: List[str] = []
    for value in values:
        if not value or not isinstance(value, str):
            continue
        normalized_value = _normalize_whitespace(value)
        if not normalized_value:
            continue
        candidates = re.split(r"[,;/|、\n\r\t()（）]+", normalized_value)
        for candidate in candidates:
            token = _normalize_whitespace(candidate)
            if not token:
                continue
            normalized = _clean_token(token)
            if any(separator in normalized for separator in [",", ";", "|", "/"]):
                continue
            if not normalized or _is_noise_token(normalized):
                continue
            tokens.append(normalized)
    return list(dict.fromkeys(tokens))


def normalize_profile_skills(raw) -> List[str]:
    if raw is None:
        return []
    if isinstance(raw, list):
        return normalize_skill_tokens([str(item) for item in raw])

    text = str(raw).strip()
    if not text:
        return []
    try:
        values = json.loads(text)
        if isinstance(values, list):
            return normalize_skill_tokens([str(item) for item in values])
    except Exception:
        pass
    return normalize_skill_tokens([text])


def normalize_job_labels(raw) -> List[str]:
    if raw is None:
        return []
    if isinstance(raw, list):
        return normalize_skill_tokens([str(item) for item in raw])
    try:
        values = json.loads(raw or "[]")
        if isinstance(values, list):
            return normalize_skill_tokens([str(item) for item in values])
    except Exception:
        pass
    return normalize_skill_tokens([str(raw or "")])


def infer_skill_family(values: Iterable[str]) -> str:
    text = " ".join(str(value or "") for value in values).lower()
    if any(token in text for token in ["java", "spring", "mysql", "redis", "后端", "backend", "微服务"]):
        return "backend"
    if any(token in text for token in ["vue", "react", "javascript", "typescript", "前端", "frontend"]):
        return "frontend"
    if any(token in text for token in ["python", "sql", "数据", "data", "分析", "spark", "flink"]):
        return "data"
    if any(token in text for token in ["qa", "test", "测试", "自动化", "selenium", "jmeter"]):
        return "qa"
    return "general"


def skill_family_score(skill: str, family: str) -> int:
    if family == "general":
        return 1
    canonical = _clean_token(skill)
    family_skills = SKILL_FAMILY_KEYWORDS.get(family, set())
    if canonical in family_skills:
        return 3
    lowered = canonical.lower()
    lowered_parts = set(re.split(r"[\s.+#-]+", lowered))
    if any(lowered == item.lower() for item in family_skills):
        return 3
    if any(item.lower() in lowered_parts for item in family_skills):
        return 2
    return 0


def filter_skills_by_family(skills: Iterable[str], family: str) -> List[str]:
    normalized = normalize_skill_tokens(skills)
    if family == "general":
        return normalized
    return [skill for skill in normalized if skill_family_score(skill, family) > 0]
