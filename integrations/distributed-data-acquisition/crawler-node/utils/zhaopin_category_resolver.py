"""
智联招聘职类编码解析
"""
from __future__ import annotations

import json
import re
from pathlib import Path
from difflib import SequenceMatcher
from typing import Any, Dict, List, Optional, Tuple

from config import config
from core.logger import log


class ZhaopinCategoryResolver:
    """解析智联职位职类编码，支持本地分类文件和关键词推断。"""

    KEYWORD_ALIASES = {
        "python": ["开发", "软件", "后端", "工程师", "程序员"],
        "java": ["开发", "软件", "后端", "工程师", "程序员"],
        "golang": ["开发", "软件", "后端", "工程师", "程序员"],
        "go": ["开发", "软件", "后端", "工程师", "程序员"],
        "c++": ["开发", "软件", "后端", "工程师", "程序员"],
        "c#": ["开发", "软件", "后端", "工程师", "程序员"],
        ".net": ["开发", "软件", "后端", "工程师", "程序员"],
        "php": ["开发", "软件", "后端", "工程师", "程序员"],
        "javascript": ["开发", "前端", "软件", "工程师"],
        "typescript": ["开发", "前端", "软件", "工程师"],
        "vue": ["开发", "前端", "软件", "工程师"],
        "react": ["开发", "前端", "软件", "工程师"],
        "android": ["开发", "移动", "软件", "工程师"],
        "ios": ["开发", "移动", "软件", "工程师"],
        "测试": ["测试", "qa", "质量"],
        "运维": ["运维", "devops", "系统"],
        "算法": ["算法", "人工智能", "机器学习", "数据"],
        "ai": ["算法", "人工智能", "机器学习", "数据"],
        "数据": ["数据", "分析", "开发", "工程师"],
    }

    def __init__(self):
        self.code_to_name: Dict[str, str] = {}
        self.name_to_codes: Dict[str, List[str]] = {}
        self.group_to_codes: Dict[str, List[str]] = {}
        self.loaded_files: List[str] = []
        self._loaded = False

    def resolve_codes(self, category_hint: Optional[str], keyword: Optional[str]) -> List[str]:
        """根据分类提示或关键词解析候选职类编码。"""
        self._ensure_loaded()

        normalized_hint = self._normalize_text(category_hint)
        direct_code = self._extract_direct_code(category_hint)
        keyword_matches = self._match_codes_by_text(keyword) if keyword else []
        if direct_code:
            if not keyword_matches:
                return [direct_code]

            direct_name = self.code_to_name.get(direct_code, "")
            # 直传编码和关键词明显不匹配时，优先尝试关键词解析结果，保留原编码作为兜底。
            if direct_name and self._score_text_match(direct_name, keyword) < 0.4:
                return self._merge_codes(keyword_matches, [direct_code])
            return self._merge_codes([direct_code], keyword_matches)

        if normalized_hint:
            direct_matches = self.name_to_codes.get(normalized_hint, [])
            if direct_matches:
                return direct_matches

            grouped_matches = self.group_to_codes.get(normalized_hint, [])
            if grouped_matches:
                return grouped_matches

        if keyword_matches:
            return keyword_matches

        if normalized_hint:
            hint_matches = self._match_codes_by_text(normalized_hint)
            if hint_matches:
                return hint_matches

        return []

    def has_local_mappings(self) -> bool:
        """是否已加载本地职类映射文件。"""
        self._ensure_loaded()
        return bool(self.loaded_files)

    def choose_best_code(
        self,
        candidates: List[Any],
        category_hint: Optional[str],
        keyword: Optional[str]
    ) -> Optional[str]:
        """从候选列表中挑选最合适的一个编码。"""
        if not candidates:
            return None

        resolved_codes = set(self.resolve_codes(category_hint, keyword))
        best_code = None
        best_score = -1.0
        for candidate in candidates:
            if isinstance(candidate, dict):
                code = str(candidate.get("code", "")).strip()
                name = str(candidate.get("name", "")).strip()
                count_value = candidate.get("count", candidate.get("num", 0))
            else:
                code, name, count_value = candidate

            if not code or not name:
                continue

            try:
                score = float(count_value or 0)
            except (TypeError, ValueError):
                score = 0.0

            score += self._score_text_match(name, keyword) * 100
            score += self._score_text_match(name, category_hint) * 120
            if code in resolved_codes:
                score += 50
            if score > best_score:
                best_score = score
                best_code = code

        return best_code

    def _ensure_loaded(self) -> None:
        if self._loaded:
            return

        candidate_files = self._discover_candidate_files()

        cluster_codes_path = candidate_files.get("cluster_codes")
        if cluster_codes_path:
            self._load_cluster_codes(cluster_codes_path)

        clusters_by_level_path = candidate_files.get("clusters_by_level")
        if clusters_by_level_path:
            self._load_clusters_by_level(clusters_by_level_path)

        cluster_hierarchy_path = candidate_files.get("cluster_hierarchy")
        if cluster_hierarchy_path:
            self._load_cluster_hierarchy(cluster_hierarchy_path)

        if self.loaded_files:
            log.info(f"已加载智联职类文件: {self.loaded_files}")
        else:
            log.warning("未找到智联职类文件，将仅依赖关键词和接口返回的 jobTypes 自动推断")

        self._loaded = True

    def _discover_candidate_files(self) -> Dict[str, Path]:
        explicit_paths = {
            "cluster_codes": config.ZHAOPIN_CLUSTER_CODES_PATH,
            "clusters_by_level": config.ZHAOPIN_CLUSTERS_BY_LEVEL_PATH,
            "cluster_hierarchy": config.ZHAOPIN_CLUSTER_HIERARCHY_PATH,
        }

        discovered: Dict[str, Path] = {}
        for key, raw_path in explicit_paths.items():
            if raw_path:
                path = Path(raw_path).expanduser()
                if path.exists():
                    discovered[key] = path

        search_roots = [
            Path.cwd(),
            Path.cwd().parent,
            Path.cwd() / "category_label",
            Path.cwd().parent / "category_label",
            Path.cwd() / "data",
            Path.cwd().parent / "data",
            Path.cwd() / "resources",
            Path.cwd().parent / "resources",
            Path(__file__).resolve().parents[2] / "category_label",
        ]
        file_names = {
            "cluster_codes": ["cluster_codes.json", "cluest_code.json"],
            "clusters_by_level": ["clusters_by_level.json", "by_level.json"],
            "cluster_hierarchy": ["cluster_hierarchy.json", "hierarchy.json"],
        }

        for key, names in file_names.items():
            if key in discovered:
                continue

            for root in search_roots:
                for name in names:
                    path = root / name
                    if path.exists():
                        discovered[key] = path
                        break
                if key in discovered:
                    break

        return discovered

    def _load_cluster_codes(self, path: Path) -> None:
        payload = self._load_json(path)
        clusters = payload.get("clusters", {})
        if isinstance(clusters, dict):
            for code, name in clusters.items():
                self._register_code(str(code), str(name))
            self.loaded_files.append(str(path))

    def _load_clusters_by_level(self, path: Path) -> None:
        payload = self._load_json(path)
        if isinstance(payload, dict):
            for level1, level2_data in payload.items():
                self._register_group(level1, [])
                if not isinstance(level2_data, dict):
                    continue

                for level2, items in level2_data.items():
                    codes: List[str] = []
                    if isinstance(items, list):
                        for item in items:
                            if not isinstance(item, dict):
                                continue
                            code = str(item.get("code", "")).strip()
                            name = str(item.get("name", "")).strip()
                            if code and name:
                                self._register_code(code, name)
                                codes.append(code)

                    self._register_group(level2, codes)
                    existing_codes = self.group_to_codes.get(self._normalize_text(level1), [])
                    self.group_to_codes[self._normalize_text(level1)] = self._merge_codes(existing_codes, codes)

            self.loaded_files.append(str(path))

    def _load_cluster_hierarchy(self, path: Path) -> None:
        payload = self._load_json(path)
        hierarchy = payload.get("hierarchy", payload)
        if not isinstance(hierarchy, dict):
            return

        for level1, level2_data in hierarchy.items():
            self._register_group(level1, [])
            if not isinstance(level2_data, dict):
                continue

            for level2, names in level2_data.items():
                normalized_names = []
                if isinstance(names, list):
                    for name in names:
                        normalized_name = self._normalize_text(name)
                        if normalized_name:
                            normalized_names.append(normalized_name)
                mapped_codes: List[str] = []
                for normalized_name in normalized_names:
                    mapped_codes.extend(self.name_to_codes.get(normalized_name, []))

                self._register_group(level2, mapped_codes)
                existing_codes = self.group_to_codes.get(self._normalize_text(level1), [])
                self.group_to_codes[self._normalize_text(level1)] = self._merge_codes(existing_codes, mapped_codes)

        self.loaded_files.append(str(path))

    def _register_code(self, code: str, name: str) -> None:
        normalized_name = self._normalize_text(name)
        if not code or not normalized_name:
            return

        self.code_to_name[code] = name.strip()
        self.name_to_codes.setdefault(normalized_name, [])
        if code not in self.name_to_codes[normalized_name]:
            self.name_to_codes[normalized_name].append(code)

    def _register_group(self, group_name: Any, codes: List[str]) -> None:
        normalized_group = self._normalize_text(group_name)
        if not normalized_group:
            return

        self.group_to_codes.setdefault(normalized_group, [])
        self.group_to_codes[normalized_group] = self._merge_codes(self.group_to_codes[normalized_group], codes)

    def _merge_codes(self, existing: List[str], incoming: List[str]) -> List[str]:
        merged = list(existing)
        for code in incoming:
            if code and code not in merged:
                merged.append(code)
        return merged

    def _load_json(self, path: Path) -> Dict[str, Any]:
        with path.open("r", encoding="utf-8") as file:
            return json.load(file)

    def _extract_direct_code(self, category_hint: Optional[str]) -> Optional[str]:
        if category_hint is None:
            return None

        normalized = str(category_hint).strip()
        if not normalized or normalized in {"0", "-1"}:
            return None

        if normalized.isdigit() and len(normalized) >= 8:
            return normalized

        return None

    def _match_codes_by_text(self, text: str, limit: int = 5) -> List[str]:
        terms = self._expand_terms(text)
        normalized_text = self._normalize_text(text)

        exact_matches = self.name_to_codes.get(normalized_text, [])
        if exact_matches:
            return exact_matches[:limit]

        scored: List[Tuple[float, str]] = []
        for code, name in self.code_to_name.items():
            score = 0.0
            for term in terms:
                score += self._score_text_match(name, term)
            if score > 0:
                scored.append((score, code))

        scored.sort(key=lambda item: item[0], reverse=True)
        return [code for _, code in scored[:limit]]

    def _expand_terms(self, text: str) -> List[str]:
        normalized = self._normalize_text(text)
        if not normalized:
            return []

        terms = {normalized}
        latin_terms = re.findall(r"[a-z0-9+#.]+", normalized)
        chinese_terms = re.findall(r"[\u4e00-\u9fff]{2,}", normalized)
        terms.update(latin_terms)
        terms.update(chinese_terms)

        for latin_term in list(latin_terms):
            for alias in self.KEYWORD_ALIASES.get(latin_term, []):
                terms.add(self._normalize_text(alias))

        return [term for term in terms if term]

    def _score_text_match(self, name: Optional[str], query: Optional[str]) -> float:
        normalized_name = self._normalize_text(name)
        if not normalized_name or not query:
            return 0.0

        best_score = 0.0
        for term in self._expand_terms(query):
            if not term:
                continue
            score = 0.0
            if term == normalized_name:
                score = 1.0
            elif term in normalized_name:
                score = max(score, 0.85 + min(len(term) / max(len(normalized_name), 1), 0.1))
            else:
                score = max(score, SequenceMatcher(None, term, normalized_name).ratio() * 0.7)
            best_score = max(best_score, score)

        return best_score

    def _normalize_text(self, text: Any) -> str:
        if text is None:
            return ""
        return re.sub(r"\s+", "", str(text).strip().lower())
