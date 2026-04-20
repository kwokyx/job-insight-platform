"""
HTTP客户端
"""
import random
import time
from typing import Dict, List, Optional

from fake_useragent import UserAgent
import requests
from requests import Response
from requests.adapters import HTTPAdapter
from requests.exceptions import RequestException
from urllib3.util.retry import Retry

from config import config
from core.logger import log


class HttpClient:
    """带连接池和瞬时故障恢复能力的 HTTP 客户端。"""

    _MAX_ATTEMPTS = 4
    _DEFAULT_TIMEOUT = (15, 45)
    _TRANSIENT_ERROR_KEYWORDS = (
        "transport indicated eof",
        "connection aborted",
        "remote end closed connection",
        "remote disconnected",
        "connection reset by peer",
        "read timed out",
        "temporarily unavailable",
        "connection refused",
        "protocolerror",
        "chunkedencodingerror",
        "sslwantreaderror",
    )

    def __init__(self):
        self.user_agent = UserAgent()
        self.proxies: List[str] = []
        self.proxy_index = 0
        self.session = self._build_session()

        self._load_proxies()
        log.info("HTTP客户端初始化完成")

    def _build_retry_strategy(self) -> Retry:
        return Retry(
            total=4,
            connect=4,
            read=4,
            status=3,
            other=4,
            backoff_factor=1.5,
            backoff_max=12,
            status_forcelist=[408, 425, 429, 500, 502, 503, 504],
            allowed_methods=frozenset(["GET", "POST", "PUT", "DELETE"]),
            respect_retry_after_header=True,
            raise_on_status=False,
        )

    def _build_session(self) -> requests.Session:
        session = requests.Session()
        adapter = HTTPAdapter(
            max_retries=self._build_retry_strategy(),
            pool_connections=20,
            pool_maxsize=50,
            pool_block=True,
        )
        session.mount("http://", adapter)
        session.mount("https://", adapter)
        session.headers.update(
            {
                "Accept-Encoding": "gzip, deflate, br",
                "Connection": "keep-alive",
            }
        )
        return session

    def _reset_session(self) -> None:
        try:
            self.session.close()
        except Exception:
            pass
        self.session = self._build_session()
        log.warning("检测到瞬时网络异常，已重建 HTTP 会话连接池")

    def _load_proxies(self):
        """加载代理列表"""
        if not config.PROXY_ENABLED:
            return

        try:
            if config.PROXY_POOL_URL:
                response = requests.get(config.PROXY_POOL_URL, timeout=10)
                if response.status_code == 200:
                    self.proxies = response.json().get("proxies", [])
                    log.info(f"加载代理 {len(self.proxies)} 个")
        except Exception as exc:
            log.error(f"加载代理失败: {e}")

    def get_random_user_agent(self) -> str:
        """获取随机 User-Agent。"""
        try:
            return self.user_agent.random
        except Exception as exc:
            log.warning(f"生成随机 User-Agent 失败，使用兜底值: {exc}")
            return (
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/123.0.0.0 Safari/537.36"
            )

    def get_proxy(self) -> Optional[Dict[str, str]]:
        """获取代理"""
        if not self.proxies:
            return None

        proxy = self.proxies[self.proxy_index]
        self.proxy_index = (self.proxy_index + 1) % len(self.proxies)
        return {"http": proxy, "https": proxy}

    def get(self, url: str, **kwargs) -> Response:
        """发送 GET 请求"""
        return self._request_with_retries("GET", url, **kwargs)

    def post(self, url: str, **kwargs) -> Response:
        """发送 POST 请求"""
        return self._request_with_retries("POST", url, **kwargs)

    def _request_with_retries(self, method: str, url: str, **kwargs) -> Response:
        last_error: Optional[BaseException] = None

        for attempt in range(1, self._MAX_ATTEMPTS + 1):
            try:
                return self._request_once(method, url, **kwargs)
            except Exception as exc:
                last_error = exc
                retryable = self._is_retryable_exception(exc)
                if not retryable or attempt >= self._MAX_ATTEMPTS:
                    log.error(
                        "HTTP请求最终失败: %s %s, attempt=%s/%s, error=%s",
                        method,
                        url,
                        attempt,
                        self._MAX_ATTEMPTS,
                        exc,
                    )
                    raise

                backoff = min(12, (2 ** (attempt - 1)) + random.uniform(0.5, 1.5))
                log.warning(
                    "HTTP请求遇到瞬时异常，准备重试: %s %s, attempt=%s/%s, backoff=%.2fs, error=%s",
                    method,
                    url,
                    attempt,
                    self._MAX_ATTEMPTS,
                    backoff,
                    exc,
                )
                self._reset_session()
                time.sleep(backoff)

        if last_error:
            raise last_error
        raise RuntimeError(f"HTTP请求未执行: {method} {url}")

    def _request_once(self, method: str, url: str, **kwargs) -> Response:
        headers = dict(kwargs.get("headers", {}) or {})
        if "User-Agent" not in headers:
            headers["User-Agent"] = self.get_random_user_agent()
        kwargs["headers"] = headers

        if config.PROXY_ENABLED:
            proxy = self.get_proxy()
            if proxy:
                kwargs["proxies"] = proxy

        if "timeout" not in kwargs:
            kwargs["timeout"] = self._DEFAULT_TIMEOUT

        # 添加随机延迟，避免请求过快
        time.sleep(random.uniform(0.4, 1.1))

        try:
            log.debug(f"发送请求: {method} {url}")
            response = self.session.request(method, url, **kwargs)
            log.debug(f"请求完成: {method} {url} - {response.status_code}")

            if response.status_code >= 400:
                log.warning(f"请求失败: {method} {url} - {response.status_code}")

            return response
        except RequestException as exc:
            log.error(f"请求异常: {method} {url} - {exc}")
            raise

    def _is_retryable_exception(self, exc: BaseException) -> bool:
        if isinstance(exc, (RequestException, TimeoutError, ConnectionError, EOFError)):
            return True

        message = str(exc).lower()
        return any(keyword in message for keyword in self._TRANSIENT_ERROR_KEYWORDS)

    def close(self):
        """关闭会话"""
        self.session.close()
        log.info("HTTP客户端已关闭")


# 全局HTTP客户端实例
http_client = HttpClient()
