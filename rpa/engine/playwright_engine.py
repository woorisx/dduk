import os
import threading
from playwright.sync_api import sync_playwright

class PlaywrightEngine:
    _instance = None
    _lock = threading.Lock()

    def __new__(cls, *args, **kwargs):
        """
        Thread-safe 싱글톤 패턴으로 Playwright 엔진 인스턴스를 하나만 유지합니다.
        """
        with cls._lock:
            if cls._instance is None:
                cls._instance = super(PlaywrightEngine, cls).__new__(cls)
                cls._instance._initialized = False
            return cls._instance

    def __init__(self):
        if self._initialized:
            return
        
        # .env 설정값 로드
        self.browser_type = os.getenv("RPA_BROWSER", "chromium")
        self.headless = os.getenv("RPA_HEADLESS", "true").lower() == "true"
        self.timeout = int(os.getenv("PLAYWRIGHT_TIMEOUT_MS", 30000))
        
        self.playwright = None
        self.browser = None
        self._initialized = True

    def start(self):
        """
        Playwright 드라이버 및 브라우저를 기동합니다.
        """
        if not self.playwright:
            self.playwright = sync_playwright().start()
            
            # 브라우저 타입 선택 (chromium, firefox, webkit)
            if self.browser_type == "firefox":
                launch_fn = self.playwright.firefox.launch
            elif self.browser_type == "webkit":
                launch_fn = self.playwright.webkit.launch
            else:
                launch_fn = self.playwright.chromium.launch

            self.browser = launch_fn(headless=self.headless)
            print(f"[RPA Engine] Browser ({self.browser_type}) started. Headless: {self.headless}")

    def get_context(self):
        """
        각 태스크를 격리해서 실행할 수 있는 독립된 브라우저 컨텍스트를 생성합니다.
        """
        self.start()
        # 매번 완전히 격리된 컨텍스트를 생성하여 세션 유출 방지
        context = self.browser.new_context()
        context.set_default_timeout(self.timeout)
        return context

    def shutdown(self):
        """
        자원을 안전하게 닫고 종료합니다.
        """
        if self.browser:
            self.browser.close()
            self.browser = None
        if self.playwright:
            self.playwright.stop()
            self.playwright = None
        print("[RPA Engine] Browser shut down successfully.")
