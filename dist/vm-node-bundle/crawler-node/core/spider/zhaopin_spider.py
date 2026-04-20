"""
智联招聘爬虫
"""
import json
import re
import time
import random
import hashlib
from typing import Optional, List, Dict, Any, Tuple
from datetime import datetime

from config import config
from core.logger import log
from utils.http_client import HttpClient
from utils.zhaopin_category_resolver import ZhaopinCategoryResolver
from utils.zhaopin_auth import ZhaopinAuthManager


class ZhaopinSpider:
    """智联招聘爬虫"""

    def __init__(self, http_client: HttpClient):
        self.http_client = http_client
        self.base_url = config.ZHAOPIN_API_URL
        self.auth_manager = ZhaopinAuthManager()
        self.token_mme = ""
        self.token_c1k = ""
        self.cookie = ""
        self.page_request_id = ""
        self.request_id = ""
        self.client_id = ""
        self.category_resolver = ZhaopinCategoryResolver()
        self._sync_auth_context(force_refresh=True)

    def crawl(self, keyword: Optional[str] = None, city: Optional[str] = None,
              category_code: Optional[str] = None, page: int = 1,
              search_keyword: Optional[str] = None) -> List[Dict[str, Any]]:
        """
        爬取职位数据

        Args:
            keyword: 搜索关键词
            city: 城市
            category_code: 智联职位职类编码
            page: 页码

        Returns:
            职位数据列表
        """
        try:
            # 构建请求参数
            params, category_candidates = self._build_params(keyword, city, category_code, page)
            self._sync_auth_context()
            self._log_auth_health()

            # 发送请求
            response, data = self._request_positions(params)
            if response is None or data is None:
                return []

            # 提取职位数据
            positions = self._extract_positions(data)
            job_data_list = []

            if not positions:
                self._log_response_diagnostics(data, params)
                fallback_code = self._resolve_job_type_fallback(
                    response_data=data,
                    keyword=keyword,
                    category_code=category_code,
                    current_job_type=params.get("jobType"),
                    category_candidates=category_candidates
                )
                if fallback_code:
                    retry_params = dict(params)
                    retry_params["jobType"] = fallback_code
                    log.info(f"自动改用推断职类编码重试: jobType={fallback_code}")
                    response, data = self._request_positions(retry_params)
                    if response is None or data is None:
                        return []
                    positions = self._extract_positions(data)
                    params = retry_params
                    if not positions:
                        self._log_response_diagnostics(data, retry_params)

            for position in positions:
                job_data = self._parse_position(position, search_keyword=search_keyword or keyword)
                if job_data:
                    job_data_list.append(self._normalize_job_data(job_data))

            log.info(f"爬取完成: keyword={keyword}, city={city}, page={page}, 获取职位{len(job_data_list)}条")

            # 随机延迟，避免请求过快
            time.sleep(random.uniform(1.0, 3.0))

            return job_data_list

        except Exception as e:
            log.error(f"爬取数据失败: {e}")
            return []

    def _extract_positions(self, response_data: Dict[str, Any]) -> List[Dict[str, Any]]:
        """兼容不同版本接口的职位列表结构。"""
        data = response_data.get("data", {})

        if isinstance(data, dict):
            positions = data.get("results")
            if isinstance(positions, list):
                return positions

            positions = data.get("list")
            if isinstance(positions, list):
                return positions

        log.warning(f"未识别的职位列表结构，data keys={list(data.keys()) if isinstance(data, dict) else type(data)}")
        return []

    def _log_response_diagnostics(self, response_data: Dict[str, Any], params: Dict[str, Any]) -> None:
        """在没有结果时输出精简诊断信息，便于排查接口字段变化。"""
        try:
            data = response_data.get("data", {})
            data_keys = list(data.keys())[:20] if isinstance(data, dict) else []
            sample = {}

            if isinstance(data, dict):
                for key in ("list", "results", "positions", "jobList"):
                    value = data.get(key)
                    if isinstance(value, list) and value:
                        first_item = value[0]
                        sample[key] = list(first_item.keys())[:20] if isinstance(first_item, dict) else type(first_item).__name__

            log.warning(
                "职位结果为空，诊断信息: "
                f"request_params={params}, "
                f"response_code={response_data.get('code')}, "
                f"message={response_data.get('message')}, "
                f"data_keys={data_keys}, "
                f"sample_item_keys={sample}"
            )
        except Exception as exc:
            log.warning(f"输出响应诊断信息失败: {exc}")

    def _request_positions(
        self,
        params: Dict[str, Any],
        allow_auth_refresh: bool = True,
    ) -> Tuple[Optional[Any], Optional[Dict[str, Any]]]:
        """请求职位搜索接口，优先使用稳定的兼容协议。"""
        self._sync_auth_context(force_refresh=not allow_auth_refresh)
        compat_response, compat_data = self._request_positions_compat(params)
        if compat_response is not None and compat_data is not None:
            self._log_verification_hint(compat_data, "兼容请求")
            refresh_reason = self.auth_manager.should_refresh(
                response=compat_response,
                response_data=compat_data,
                empty_result=not bool(self._extract_positions(compat_data)),
            )
            if refresh_reason and allow_auth_refresh:
                refreshed = self.auth_manager.refresh_auth(refresh_reason)
                if refreshed.is_usable():
                    self._sync_auth_context(force_refresh=True)
                    return self._request_positions(params, allow_auth_refresh=False)
            if self._extract_positions(compat_data):
                log.info("兼容请求命中职位数据，已作为当前主采集链路")
                return compat_response, compat_data
            self._log_response_diagnostics(compat_data, self._build_compat_body(params))
        elif allow_auth_refresh:
            refresh_reason = self.auth_manager.should_refresh(
                response=compat_response,
                response_data=compat_data,
                empty_result=True,
            )
            if refresh_reason:
                refreshed = self.auth_manager.refresh_auth(refresh_reason)
                if refreshed.is_usable():
                    self._sync_auth_context(force_refresh=True)
                    return self._request_positions(params, allow_auth_refresh=False)

        response, data, headers = self._request_positions_primary(params)
        if response is None or data is None:
            return None, None

        self._log_request_summary(response, data, params, headers)
        self._log_verification_hint(data, "主请求")
        refresh_reason = self.auth_manager.should_refresh(
            response=response,
            response_data=data,
            empty_result=not bool(self._extract_positions(data)),
        )
        if refresh_reason and allow_auth_refresh:
            refreshed = self.auth_manager.refresh_auth(refresh_reason)
            if refreshed.is_usable():
                self._sync_auth_context(force_refresh=True)
                return self._request_positions(params, allow_auth_refresh=False)
        return response, data

    def _request_positions_primary(
        self, params: Dict[str, Any]
    ) -> Tuple[Optional[Any], Optional[Dict[str, Any]], Dict[str, str]]:
        """当前项目原有请求协议。"""
        headers = self._build_headers()
        response = self.http_client.post(
            self.base_url,
            json=params,
            headers=headers
        )

        if response.status_code != 200:
            log.error(f"API请求失败: status_code={response.status_code}")
            return response, None, headers

        try:
            data = response.json()
        except Exception:
            data = {}
        if data.get("code") != 200:
            log.error(f"API返回错误: {data.get('message')}")
            return response, data, headers

        return response, data, headers

    def _request_positions_compat(self, params: Dict[str, Any]) -> Tuple[Optional[Any], Optional[Dict[str, Any]]]:
        """兼容用户提供的 search/positions 请求协议。"""
        compat_url = self._build_compat_url()
        compat_headers = self._build_compat_headers()
        compat_body = self._build_compat_body(params)
        compat_cookies = self._build_compat_cookies()

        log.info(
            "开始尝试兼容请求: "
            f"url={compat_url}, body={json.dumps(compat_body, ensure_ascii=False)}"
        )

        response = self.http_client.post(
            compat_url,
            json=compat_body,
            headers=compat_headers,
            cookies=compat_cookies if compat_cookies else None,
        )

        if response.status_code != 200:
            log.warning(f"兼容请求失败: status_code={response.status_code}")
            return response, None

        try:
            data = response.json()
        except Exception:
            data = {}
        if data.get("code") != 200:
            log.warning(f"兼容请求返回错误: {data.get('message')}")
            return response, data

        self._log_request_summary(response, data, compat_body, compat_headers, request_url=compat_url)
        return response, data

    def _build_compat_url(self) -> str:
        token_mme = (self.token_mme or "").strip()
        token_c1k = (self.token_c1k or "").strip()
        if token_mme and token_mme != "MmEwMD=" and token_c1k and token_c1k != "c1K5tw0w6_=":
            return f"{self.base_url}?MmEwMD={token_mme}&c1K5tw0w6_={token_c1k}"
        return self.base_url

    def _build_compat_cookies(self) -> Dict[str, str]:
        cookie_str = (self.cookie or "").strip()
        cookies: Dict[str, str] = {}
        if not cookie_str:
            return cookies

        for part in cookie_str.split(";"):
            chunk = part.strip()
            if "=" not in chunk:
                continue
            key, value = chunk.split("=", 1)
            key = key.strip()
            value = value.strip()
            if key:
                cookies[key] = value

        return cookies

    def _build_compat_headers(self) -> Dict[str, str]:
        headers = {
            "accept": "application/json, text/plain, */*",
            "accept-language": "zh-CN,zh;q=0.9,en;q=0.8",
            "content-type": "application/json;charset=UTF-8",
            "origin": config.ZHAOPIN_ORIGIN,
            "referer": config.ZHAOPIN_REFERER,
            "user-agent": self.http_client.get_random_user_agent(),
            "x-zp-business-system": "1",
            "x-zp-page-code": "4019",
            "x-zp-platform": "13",
        }

        if config.ZHAOPIN_PAGE_REQUEST_ID:
            headers["x-zp-page-request-id"] = self.page_request_id or config.ZHAOPIN_PAGE_REQUEST_ID
        elif self.page_request_id:
            headers["x-zp-page-request-id"] = self.page_request_id
        if config.ZHAOPIN_REQUEST_ID:
            headers["x-zp-request-id"] = self.request_id or config.ZHAOPIN_REQUEST_ID
        elif self.request_id:
            headers["x-zp-request-id"] = self.request_id
        if config.ZHAOPIN_CLIENT_ID:
            headers["x-zp-client-id"] = self.client_id or config.ZHAOPIN_CLIENT_ID
        elif self.client_id:
            headers["x-zp-client-id"] = self.client_id

        return headers

    def _build_compat_body(self, params: Dict[str, Any]) -> Dict[str, Any]:
        page = int(
            params.get("jumpTo")
            or (params.get("lastUrlQuery") or {}).get("p")
            or 1
        )
        body = {
            "S_SOU_FULL_INDEX": params.get("kw", ""),
            "S_SOU_WORK_CITY": params.get("cityId", 0),
            "order": 0,
            "pageSize": int(params.get("pageSize", 20) or 20),
            "pageIndex": page,
            "anonymous": 0,
            "sortType": "DEFAULT",
            "platform": 13,
            "version": "0.0.0",
        }

        if params.get("jobType"):
            body["S_SOU_JD_JOB_LEVEL3"] = params["jobType"]

        return body

    def _log_verification_hint(self, response_data: Dict[str, Any], request_name: str) -> None:
        data = response_data.get("data", {})
        if isinstance(data, dict) and data.get("isVerification") == 1:
            log.warning(
                f"{request_name} 命中智联验证态: isVerification=1。"
                "当前 token/cookie 很可能已失效，需重新从浏览器登录态同步。"
            )

    def _build_params(self, keyword: Optional[str], city: Optional[str],
                     category_code: Optional[str], page: int) -> Tuple[Dict[str, Any], List[str]]:
        """构建请求参数"""
        params = {
            "pageSize": 20,
            "cityId": self._get_city_id(city) if city else 0,
            "workExperience": -1,
            "education": -1,
            "companyType": -1,
            "employmentType": -1,
            "jobWelfareTag": -1,
            "kw": keyword if keyword else "",
            "kt": 3 if keyword else 0,
            "lastUrlQuery": {"p": page, "pageSize": 20},
            "jumpTo": page
        }

        category_candidates = self._resolve_category_candidates(category_code, keyword)
        should_send_job_type = self._should_send_job_type(category_code, category_candidates)
        if should_send_job_type and category_candidates:
            # 这里需要传职位职类，而不是行业编码。
            params["jobType"] = category_candidates[0]

        return params, category_candidates

    def _resolve_category_candidates(self, category_code: Optional[str], keyword: Optional[str]) -> List[str]:
        """解析职位职类候选编码。"""
        candidates = self.category_resolver.resolve_codes(category_code, keyword)
        if candidates:
            if category_code and str(category_code).strip() != candidates[0]:
                log.info(f"已自动解析职类编码: input={category_code}, resolved={candidates[:3]}")
            return candidates

        if category_code:
            normalized = str(category_code).strip()
            if normalized.isdigit() and len(normalized) < 8:
                log.warning(
                    f"category_code={normalized} 看起来不像智联职位职类编码，"
                    "未能从本地分类文件解析，将尝试结合接口 jobTypes 自动推断"
                )

        return []

    def _should_send_job_type(self, category_code: Optional[str], category_candidates: List[str]) -> bool:
        """测试模式：首次请求统一不传 jobType，先验证基础搜索是否能返回职位。"""
        if category_code or category_candidates:
            log.info("最小改动测试已启用：首次请求不传 jobType")
        return False

    def _resolve_job_type_fallback(
        self,
        response_data: Dict[str, Any],
        keyword: Optional[str],
        category_code: Optional[str],
        current_job_type: Optional[str],
        category_candidates: List[str]
    ) -> Optional[str]:
        """在首次没有结果时，结合接口返回的 jobTypes 自动挑选更合适的职类编码。"""
        data = response_data.get("data", {})
        if not isinstance(data, dict):
            return None

        raw_job_types = data.get("jobTypes", [])
        candidates = self._flatten_job_type_candidates(raw_job_types)
        if not candidates:
            return None

        best_code = self.category_resolver.choose_best_code(candidates, category_code, keyword)
        if not best_code:
            return None

        if best_code == current_job_type:
            return None

        if category_candidates and best_code == category_candidates[0]:
            return None

        return best_code

    def _flatten_job_type_candidates(self, job_types: Any) -> List[Tuple[str, str, int]]:
        """拍平接口返回的 jobTypes 候选。"""
        results: List[Tuple[str, str, int]] = []

        def visit(node: Any) -> None:
            if isinstance(node, list):
                for item in node:
                    visit(item)
                return

            if not isinstance(node, dict):
                return

            code = (
                str(node.get("code", "")).strip()
                or str(node.get("value", "")).strip()
                or str(node.get("jobTypeId", "")).strip()
            )
            name = (
                str(node.get("name", "")).strip()
                or str(node.get("label", "")).strip()
                or str(node.get("jobTypeName", "")).strip()
            )
            count_value = node.get("count", node.get("num", 0))
            try:
                count = int(count_value or 0)
            except (TypeError, ValueError):
                count = 0

            if code and name:
                results.append((code, name, count))

            for key in ("children", "subJobTypes", "items", "options"):
                if key in node:
                    visit(node.get(key))

        visit(job_types)

        deduped: List[Tuple[str, str, int]] = []
        seen = set()
        for item in results:
            if item[0] in seen:
                continue
            seen.add(item[0])
            deduped.append(item)

        return deduped

    def _build_headers(self) -> Dict[str, str]:
        """构建请求头"""
        headers = {
            "User-Agent": self.http_client.get_random_user_agent(),
            "Content-Type": "application/json",
            "Accept": "application/json, text/plain, */*",
            "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
            "Authorization": f"Bearer {self.token_mme}",
            "Cookie": self.cookie,
            "Referer": config.ZHAOPIN_REFERER,
            "Origin": config.ZHAOPIN_ORIGIN,
            "Sec-Fetch-Dest": "empty",
            "Sec-Fetch-Mode": "cors",
            "Sec-Fetch-Site": "same-site",
        }

        # 添加Token
        if self.token_c1k:
            headers["C1K"] = self.token_c1k
        if self.page_request_id or config.ZHAOPIN_PAGE_REQUEST_ID:
            headers["x-zp-page-request-id"] = self.page_request_id or config.ZHAOPIN_PAGE_REQUEST_ID
        if self.request_id or config.ZHAOPIN_REQUEST_ID:
            headers["x-zp-request-id"] = self.request_id or config.ZHAOPIN_REQUEST_ID
        if self.client_id or config.ZHAOPIN_CLIENT_ID:
            headers["x-zp-client-id"] = self.client_id or config.ZHAOPIN_CLIENT_ID

        return headers

    def _log_auth_health(self) -> None:
        """输出鉴权配置健康度，帮助定位 200 但空列表的问题。"""
        auth_signals = {
            "token_mme_default": self.token_mme == "MmEwMD=",
            "token_c1k_default": self.token_c1k == "c1K5tw0w6_=",
            "cookie_present": bool(self.cookie.strip()),
            "page_request_id_present": bool((self.page_request_id or config.ZHAOPIN_PAGE_REQUEST_ID).strip()),
            "request_id_present": bool((self.request_id or config.ZHAOPIN_REQUEST_ID).strip()),
            "client_id_present": bool((self.client_id or config.ZHAOPIN_CLIENT_ID).strip()),
        }
        if auth_signals["token_mme_default"] or auth_signals["token_c1k_default"] or not auth_signals["cookie_present"]:
            log.warning(f"智联鉴权配置可能不足，当前状态: {auth_signals}")
        else:
            log.info(f"智联鉴权配置检查通过: {auth_signals}")

    def _sync_auth_context(self, force_refresh: bool = False) -> None:
        snapshot = self.auth_manager.get_snapshot(force_refresh=force_refresh)
        self.token_mme = snapshot.token_mme or config.ZHAOPIN_TOKEN_MME
        self.token_c1k = snapshot.token_c1k or config.ZHAOPIN_TOKEN_C1K
        self.cookie = snapshot.cookie or config.ZHAOPIN_COOKIE
        self.page_request_id = snapshot.page_request_id or config.ZHAOPIN_PAGE_REQUEST_ID
        self.request_id = snapshot.request_id or config.ZHAOPIN_REQUEST_ID
        self.client_id = snapshot.client_id or config.ZHAOPIN_CLIENT_ID

    def _log_request_summary(
        self,
        response: Any,
        response_data: Dict[str, Any],
        params: Dict[str, Any],
        headers: Dict[str, str],
        request_url: Optional[str] = None,
    ) -> None:
        """记录请求/响应摘要，便于和浏览器抓包对照。"""
        data = response_data.get("data", {})
        positions = self._extract_positions(response_data)
        job_types = data.get("jobTypes", []) if isinstance(data, dict) else []
        flattened_job_types = self._flatten_job_type_candidates(job_types)[:5]
        request_id = (
            response.headers.get("x-zp-page-request-id")
            or response.headers.get("x-request-id")
            or response.headers.get("traceId")
            or ""
        )
        header_snapshot = {
            "Authorization": self._mask_value(headers.get("Authorization", "")),
            "C1K": self._mask_value(headers.get("C1K", "")),
            "Cookie": self._mask_value(headers.get("Cookie", "")),
            "Referer": headers.get("Referer", ""),
            "Origin": headers.get("Origin", ""),
            "x-zp-page-request-id": headers.get("x-zp-page-request-id", ""),
            "x-zp-request-id": headers.get("x-zp-request-id", ""),
            "x-zp-client-id": headers.get("x-zp-client-id", ""),
        }
        response_summary = {
            "code": response_data.get("code"),
            "message": response_data.get("message"),
            "position_count": len(positions),
            "data_count_hint": data.get("count") if isinstance(data, dict) else None,
            "request_id": request_id,
            "job_type_candidates": flattened_job_types,
        }
        log.info(
            "智联请求摘要: "
            f"url={request_url or self.base_url}, "
            f"params={json.dumps(params, ensure_ascii=False)}, "
            f"headers={json.dumps(header_snapshot, ensure_ascii=False)}, "
            f"response={json.dumps(response_summary, ensure_ascii=False)}"
        )

    def _mask_value(self, value: str) -> str:
        raw = str(value or "").strip()
        if not raw:
            return ""
        if len(raw) <= 8:
            return "*" * len(raw)
        return f"{raw[:4]}***{raw[-4:]}"

    def _get_city_id(self, city_name: str) -> int:
        """获取城市ID（简化版本）"""
        city_map = {
            "北京": 530,
            "上海": 538,
            "广州": 763,
            "深圳": 765,
            "杭州": 653,
            "南京": 635,
            "武汉": 736,
            "成都": 801,
            "重庆": 551,
            "天津": 531,
        }
        return city_map.get(city_name, 0)

    def _parse_position(self, position: Dict[str, Any], search_keyword: Optional[str] = None) -> Optional[Dict[str, Any]]:
        """解析职位数据"""
        try:
            if position.get("jobDetailData"):
                return self._parse_universal_position(position, search_keyword=search_keyword)

            if self._is_flat_position(position):
                return self._parse_flat_position(position, search_keyword=search_keyword)

            # 提取基础信息
            base_info = position.get("job", {}) or position.get("position", {})
            company_info = position.get("company", {})

            # 职位标题
            title = base_info.get("name", "").strip()
            if not title:
                return None

            # 薪资解析
            salary_raw = base_info.get("salary", "")
            salary_min, salary_max = self._parse_salary(salary_raw)

            # 工作城市
            city_display = base_info.get("city", {}).get("display", "")
            if not city_display:
                city_display = base_info.get("cityName", "")
            district = (
                base_info.get("district", {}).get("name", "")
                or base_info.get("districtName", "")
                or ""
            )
            street_name = base_info.get("streetName", "") or ""

            # 经验要求
            experience = base_info.get("workingExp", {}).get("name", "")
            if not experience:
                experience = base_info.get("experienceName", "")

            # 学历要求
            education = base_info.get("eduLevel", {}).get("name", "")
            if not education:
                education = base_info.get("degreeName", "")

            # 公司信息
            company_name = company_info.get("name", "").strip()
            company_size = company_info.get("size", {}).get("name", "")
            company_finance = company_info.get("type", {}).get("name", "")

            # 职位福利
            welfare_list = position.get("welfare", []) or base_info.get("welfare", [])
            welfare = ",".join(welfare_list) if welfare_list else ""

            # 职位标签
            tags = position.get("tags", []) or base_info.get("jobLabels", [])
            job_labels = ",".join(tags) if tags else ""
            job_skill_tags = ",".join(tags) if tags else ""
            job_keywords = self._build_keywords(tags, search_keyword)

            # 职位描述
            description = base_info.get("desc", "") or base_info.get("description", "")
            # 清理HTML标签
            description = self._clean_html(description)
            requirements = ""

            # 职位分类
            job_classification = base_info.get("jobType", {}).get("name", "")

            # 公司Logo
            company_logo = company_info.get("logo", "")
            address = company_info.get("address", "") or base_info.get("address", "")
            company_type = company_info.get("property", {}).get("name", "") or ""
            industry_name = company_info.get("industry", {}).get("name", "") or ""
            industry_code = str(company_info.get("industry", {}).get("code", "") or "")
            company_url = company_info.get("url", "") or ""

            # 发布时间
            publish_date_str = base_info.get("publishDate", "") or base_info.get("updateDate", "")
            publish_date = self._parse_date(publish_date_str)

            # 构建职位数据
            job_data = {
                "url": base_info.get("positionURL", "") or f"https://www.zhaopin.com/job/{base_info.get('number', '')}.html",
                "url_obj_id": base_info.get("number", "") or str(position.get("id", "")),
                "title": title,
                "salary_min": salary_min,
                "salary_max": salary_max,
                "salary_raw": salary_raw,
                "job_city": city_display,
                "district": district,
                "street_name": street_name,
                "experience_year": experience,
                "education_need": education,
                "publish_date": publish_date,
                "job_welfare": welfare,
                "job_labels": job_labels,
                "job_skill_tags": job_skill_tags,
                "job_keywords": job_keywords,
                "requirements": requirements,
                "position_info": description,
                "job_classification": job_classification,
                "address": address,
                "company_name": company_name,
                "company_size": company_size,
                "company_type": company_type,
                "company_finance": company_finance,
                "industry_name": industry_name,
                "industry_code": industry_code,
                "company_url": company_url,
                "company_logo": company_logo,
                "crawl_time": datetime.now(),
            }

            return job_data

        except Exception as e:
            log.error(f"解析职位数据失败: {e}, position={position.get('number', 'unknown')}")
            return None

    def _parse_universal_position(self, position: Dict[str, Any], search_keyword: Optional[str] = None) -> Optional[Dict[str, Any]]:
        """解析用户提供的 search/positions 通用结构。"""
        detail = position.get("jobDetailData", {}) or {}
        pos = detail.get("position", {}) or {}
        base = pos.get("base", {}) or {}
        desc = pos.get("desc", {}) or {}
        job_type = pos.get("jobType", {}) or {}
        company = detail.get("company", {}) or {}

        position_number = str(base.get("positionNumber", "")).strip()
        title = str(base.get("positionName", "") or position.get("jobName", "")).strip()
        if not title:
            return None

        salary_raw = str(base.get("salary", "") or self._get_salary_from_card(position)).strip()
        salary_min, salary_max = self._parse_salary(salary_raw)

        city_name, district, address = self._extract_city_info_from_card(position)
        welfare_tags = desc.get("welfareTags", []) or []
        label_tags = desc.get("labels", []) or []
        job_labels = ",".join(str(tag).strip() for tag in label_tags if str(tag).strip())
        job_keywords = self._build_keywords(search_keyword, label_tags)
        welfare = ",".join(str(tag).strip() for tag in welfare_tags if str(tag).strip())

        description = self._clean_html(str(desc.get("description", "") or ""))
        publish_date = self._parse_date(
            str(position.get("firstPublishTime", "") or "").split(" ")[0]
        )

        return {
            "url": f"https://jobs.zhaopin.com/{position_number}.htm" if position_number else "",
            "url_obj_id": position_number or str(position.get("number", "") or position.get("jobId", "")),
            "title": title,
            "salary_min": salary_min,
            "salary_max": salary_max,
            "salary_raw": salary_raw,
            "job_city": city_name,
            "district": district,
            "street_name": "",
            "experience_year": str(base.get("positionWorkingExp", "")).strip(),
            "education_need": str(base.get("education", "") or position.get("education", "")).strip(),
            "publish_date": publish_date,
            "job_welfare": welfare,
            "job_labels": job_labels,
            "job_skill_tags": job_labels,
            "job_keywords": job_keywords,
            "requirements": "",
            "position_info": description,
            "job_classification": (
                position.get("subJobTypeLevelName", "")
                or position.get("jobTypeLevelName", "")
                or job_type.get("subJobTypeLevelName", "")
                or position.get("industryName", "")
            ),
            "address": address,
            "company_name": str(company.get("shortName", "") or company.get("name", "") or position.get("companyName", "")).strip(),
            "company_size": str(company.get("size", "") or position.get("companySize", "")).strip(),
            "company_type": "",
            "company_finance": str(company.get("financeStage", "") or position.get("financeStage", "")).strip(),
            "industry_name": str(position.get("industryName", "")).strip(),
            "industry_code": str(position.get("industryCode", "") or "").strip(),
            "company_url": "",
            "company_logo": "",
            "crawl_time": datetime.now(),
        }

    def _is_flat_position(self, position: Dict[str, Any]) -> bool:
        """判断是否为 search/positions 的扁平职位结构。"""
        return bool(position.get("name")) and (
            "companyName" in position or
            "workCity" in position or
            "salary60" in position
        )

    def _parse_flat_position(self, position: Dict[str, Any], search_keyword: Optional[str] = None) -> Optional[Dict[str, Any]]:
        """解析 search/positions 接口返回的扁平职位数据。"""
        title = str(position.get("name", "")).strip()
        if not title:
            return None

        salary_raw = (
            position.get("salary60")
            or position.get("salary")
            or position.get("salaryDesc")
            or ""
        )
        salary_min, salary_max = self._parse_salary(salary_raw)

        skill_tags = position.get("jobSkillTags", []) or []
        job_labels = ",".join(
            tag.get("name", "").strip()
            for tag in skill_tags
            if isinstance(tag, dict) and tag.get("name")
        )
        skill_tag_names = [
            tag.get("name", "").strip()
            for tag in skill_tags
            if isinstance(tag, dict) and tag.get("name")
        ]
        job_skill_tags = ",".join(skill_tag_names)
        job_keywords = self._build_keywords(skill_tag_names, search_keyword, job_labels.split(",") if job_labels else [])

        welfare_tags = (
            position.get("jobKnowledgeWelfareFeatures", [])
            or position.get("welfare", [])
            or []
        )
        welfare = ",".join(str(tag).strip() for tag in welfare_tags if str(tag).strip())

        description = (
            position.get("jobSummary")
            or position.get("positionDetail")
            or position.get("description")
            or ""
        )
        description = self._clean_html(description)
        requirements = self._extract_requirements(position)

        publish_date = self._parse_date(
            position.get("firstPublishTime")
            or position.get("publishTime")
            or position.get("updateDate")
            or ""
        )
        district = (
            position.get("district")
            or position.get("districtName")
            or position.get("jobDistrict")
            or ""
        )
        street_name = position.get("streetName") or position.get("street") or ""
        company_type = position.get("companyType") or position.get("companyProperty") or ""
        industry_name = position.get("industryName") or position.get("companyIndustry") or ""
        industry_code = str(position.get("industryCode") or "")
        company_url = position.get("companyUrl") or ""
        address = position.get("address") or position.get("companyAddress") or ""

        job_data = {
            "url": position.get("positionURL", "") or position.get("jobUrl", ""),
            "url_obj_id": position.get("number", "") or str(position.get("jobId", "") or position.get("positionId", "")),
            "title": title,
            "salary_min": salary_min,
            "salary_max": salary_max,
            "salary_raw": salary_raw,
            "job_city": position.get("workCity", "") or position.get("cityName", ""),
            "district": str(district).strip(),
            "street_name": str(street_name).strip(),
            "experience_year": position.get("workingExp", "") or position.get("jobExperience", ""),
            "education_need": position.get("education", "") or position.get("jobDegree", ""),
            "publish_date": publish_date,
            "job_welfare": welfare,
            "job_labels": job_labels,
            "job_skill_tags": job_skill_tags,
            "job_keywords": job_keywords,
            "requirements": requirements,
            "position_info": description,
            "job_classification": position.get("jobType", "") or position.get("jobTypeName", ""),
            "address": str(address).strip(),
            "company_name": position.get("companyName", "").strip(),
            "company_size": position.get("companySize", "") or position.get("companyScaleTypeTags", ""),
            "company_type": str(company_type).strip(),
            "company_finance": position.get("companyTypeName", "") or position.get("financeStage", ""),
            "industry_name": str(industry_name).strip(),
            "industry_code": industry_code.strip(),
            "company_url": str(company_url).strip(),
            "company_logo": position.get("companyLogo", "") or position.get("logoUrl", ""),
            "crawl_time": datetime.now(),
        }

        if not job_data["url"] and job_data["url_obj_id"]:
            job_data["url"] = f"https://www.zhaopin.com/job/{job_data['url_obj_id']}.html"

        return job_data

    def _normalize_job_data(self, job_data: Dict[str, Any]) -> Dict[str, Any]:
        """统一输出分布式采集字段，保证消息体结构稳定。"""
        normalized = dict(job_data)
        crawl_time = normalized.get("crawl_time")
        if not isinstance(crawl_time, datetime):
            crawl_time = datetime.now()
        normalized["crawl_time"] = crawl_time
        normalized["crawl_update_time"] = crawl_time

        url = str(normalized.get("url", "") or "").strip()
        raw_identifier = str(normalized.get("url_obj_id", "") or "").strip()
        if url:
            normalized["url_obj_id"] = self._stable_job_id(url)
        elif raw_identifier:
            normalized["url_obj_id"] = self._stable_job_id(raw_identifier)
        else:
            normalized["url_obj_id"] = self._stable_job_id(
                json.dumps(normalized, ensure_ascii=False, default=str)
            )

        normalized["url"] = url
        normalized["title"] = str(normalized.get("title", "") or "").strip()
        normalized["salary_min"] = int(normalized.get("salary_min") or 0)
        normalized["salary_max"] = int(normalized.get("salary_max") or 0)
        normalized["salary_raw"] = str(normalized.get("salary_raw", "") or "").strip()
        normalized["job_city"] = str(normalized.get("job_city", "") or "").strip()
        normalized["experience_year"] = str(normalized.get("experience_year", "") or "").strip()
        normalized["education_need"] = str(normalized.get("education_need", "") or "").strip()
        normalized["job_welfare"] = str(normalized.get("job_welfare", "") or "").strip()
        normalized["job_labels"] = str(normalized.get("job_labels", "") or "").strip()
        normalized["position_info"] = str(normalized.get("position_info", "") or "").strip()
        normalized["job_classification"] = str(normalized.get("job_classification", "") or "").strip()
        normalized["company_name"] = str(normalized.get("company_name", "") or "").strip()
        normalized["company_size"] = str(normalized.get("company_size", "") or "").strip()
        normalized["company_finance"] = str(normalized.get("company_finance", "") or "").strip()
        normalized["company_logo"] = str(normalized.get("company_logo", "") or "").strip()
        normalized.setdefault("task_id", "")
        return normalized

    def _stable_job_id(self, raw_value: str) -> str:
        return hashlib.md5(str(raw_value).encode("utf-8")).hexdigest()

    def _parse_salary(self, salary_str: str) -> tuple[int, int]:
        """解析薪资格式"""
        if not salary_str or "面议" in salary_str:
            return 0, 0

        try:
            text = salary_str.strip()
            text = re.sub(r"[·/]\d+薪?", "", text)

            if "万" in text:
                cleaned = text.replace("万", "").replace("元", "").strip()
                parts = cleaned.split("-")
                if len(parts) >= 2:
                    low = re.sub(r"[^\d.]", "", parts[0])
                    high = re.sub(r"[^\d.]", "", parts[1])
                    if low and high:
                        return int(float(low) * 10000), int(float(high) * 10000)

            if "k" in text.lower():
                cleaned = re.sub(r"[kK]", "", text)
                parts = cleaned.split("-")
                if len(parts) >= 2:
                    low = re.sub(r"[^\d.]", "", parts[0])
                    high = re.sub(r"[^\d.]", "", parts[1])
                    if low and high:
                        return int(float(low) * 1000), int(float(high) * 1000)

            cleaned = text.replace("元", "").strip()
            parts = cleaned.split("-")
            if len(parts) >= 2:
                low = re.sub(r"[^\d.]", "", parts[0])
                high = re.sub(r"[^\d.]", "", parts[1])
                if low and high:
                    return int(float(low)), int(float(high))

            numbers = re.findall(r"(\d+(?:\.\d+)?)", text)
            if len(numbers) == 1:
                salary = int(float(numbers[0]) * 1000)
                return salary, salary
            return 0, 0
        except Exception:
            return 0, 0

    def _build_keywords(self, *sources: Any) -> str:
        keywords = []
        seen = set()
        for source in sources:
            if source is None:
                continue
            if isinstance(source, str):
                parts = [item.strip() for item in re.split(r"[,，/\s]+", source) if item.strip()]
            elif isinstance(source, list):
                parts = [str(item).strip() for item in source if str(item).strip()]
            else:
                parts = [str(source).strip()] if str(source).strip() else []
            for part in parts:
                lowered = part.lower()
                if lowered not in seen:
                    seen.add(lowered)
                    keywords.append(part)
        return ",".join(keywords)

    def _extract_requirements(self, position: Dict[str, Any]) -> str:
        raw = position.get("jobRequirements") or position.get("requirements") or ""
        if isinstance(raw, list):
            return ",".join(str(item).strip() for item in raw if str(item).strip())
        return self._clean_html(str(raw))

    def _get_salary_from_card(self, position: Dict[str, Any]) -> str:
        card = self._load_card_custom_json(position)
        return str(card.get("salary60", "") or "").strip()

    def _extract_city_info_from_card(self, position: Dict[str, Any]) -> Tuple[str, str, str]:
        card = self._load_card_custom_json(position)
        address_text = str(card.get("address", "") or "").strip()
        parts = [part.strip() for part in address_text.split() if part.strip()]
        city_name = parts[0] if parts else str(position.get("cityId", "") or "").strip()
        district = parts[1] if len(parts) > 1 else ""
        return city_name, district, address_text

    def _load_card_custom_json(self, position: Dict[str, Any]) -> Dict[str, Any]:
        raw = position.get("cardCustomJson", "{}") or "{}"
        try:
            payload = json.loads(raw)
            return payload if isinstance(payload, dict) else {}
        except Exception:
            return {}

    def _clean_html(self, html_text: str) -> str:
        """清理HTML标签"""
        if not html_text:
            return ""

        # 移除HTML标签
        clean_text = re.sub(r'<[^>]+>', ' ', html_text)
        # 合并多个空格
        clean_text = re.sub(r'\s+', ' ', clean_text).strip()
        return clean_text

    def _parse_date(self, date_str: str) -> Optional[datetime]:
        """解析日期字符串"""
        if not date_str:
            return None

        try:
            # 尝试多种日期格式
            formats = [
                "%Y-%m-%d %H:%M:%S",
                "%Y-%m-%d",
                "%Y/%m/%d %H:%M:%S",
                "%Y/%m/%d",
            ]

            for fmt in formats:
                try:
                    return datetime.strptime(date_str, fmt)
                except ValueError:
                    continue

            # 如果都失败，返回当前时间
            return datetime.now()
        except Exception:
            return datetime.now()
