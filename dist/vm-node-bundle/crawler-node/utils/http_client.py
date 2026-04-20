"""
HTTP客户端
"""
import time
import random
from typing import Optional, Dict, Any, List
from fake_useragent import UserAgent
import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry
from tenacity import retry, stop_after_attempt, wait_exponential, retry_if_exception_type

from config import config
from core.logger import log


class HttpClient:
    """HTTP客户端"""

    def __init__(self):
        self.session = requests.Session()
        self.user_agent = UserAgent()
        self.proxies: List[str] = []
        self.proxy_index = 0

        # 配置重试策略
        retry_strategy = Retry(
            total=3,
            backoff_factor=1,
            status_forcelist=[429, 500, 502, 503, 504],
            allowed_methods=["GET", "POST", "PUT", "DELETE"]
        )

        # 配置适配器
        adapter = HTTPAdapter(max_retries=retry_strategy, pool_connections=10, pool_maxsize=20)
        self.session.mount("http://", adapter)
        self.session.mount("https://", adapter)

        # 加载代理
        self._load_proxies()

        log.info("HTTP客户端初始化完成")

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
        except Exception as e:
            log.error(f"加载代理失败: {e}")

    def get_random_user_agent(self) -> str:
        """获取随机User-Agent"""
        return self.user_agent.random

    def get_proxy(self) -> Optional[Dict[str, str]]:
        """获取代理"""
        if not self.proxies:
            return None

        proxy = self.proxies[self.proxy_index]
        self.proxy_index = (self.proxy_index + 1) % len(self.proxies)

        return {
            "http": proxy,
            "https": proxy
        }

    @retry(
        stop=stop_after_attempt(3),
        wait=wait_exponential(multiplier=1, min=2, max=10),
        retry=retry_if_exception_type(requests.RequestException)
    )
    def get(self, url: str, **kwargs) -> requests.Response:
        """发送GET请求"""
        return self._request("GET", url, **kwargs)

    @retry(
        stop=stop_after_attempt(3),
        wait=wait_exponential(multiplier=1, min=2, max=10),
        retry=retry_if_exception_type(requests.RequestException)
    )
    def post(self, url: str, **kwargs) -> requests.Response:
        """发送POST请求"""
        return self._request("POST", url, **kwargs)

    def _request(self, method: str, url: str, **kwargs) -> requests.Response:
        """发送请求"""
        # 准备请求参数
        headers = kwargs.get("headers", {})
        if "User-Agent" not in headers:
            headers["User-Agent"] = self.get_random_user_agent()
        kwargs["headers"] = headers

        # 设置代理
        if config.PROXY_ENABLED:
            proxy = self.get_proxy()
            if proxy:
                kwargs["proxies"] = proxy

        # 设置超时
        if "timeout" not in kwargs:
            kwargs["timeout"] = (10, 30)

        # 添加随机延迟，避免请求过快
        time.sleep(random.uniform(0.5, 2.0))

        try:
            log.debug(f"发送请求: {method} {url}")
            response = self.session.request(method, url, **kwargs)
            log.debug(f"请求完成: {method} {url} - {response.status_code}")

            # 检查响应状态
            if response.status_code >= 400:
                log.warning(f"请求失败: {method} {url} - {response.status_code}")

            return response

        except requests.RequestException as e:
            log.error(f"请求异常: {method} {url} - {e}")
            raise

    def close(self):
        """关闭会话"""
        self.session.close()
        log.info("HTTP客户端已关闭")


# 全局HTTP客户端实例
http_client = HttpClient()