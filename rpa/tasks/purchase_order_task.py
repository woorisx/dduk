import json
import os
import re
import sys
import time
from collections import deque
from urllib.parse import urlparse, urlunparse

import requests
from dotenv import load_dotenv

sys.path.append(os.path.join(os.path.dirname(__file__), ".."))
from engine.playwright_engine import PlaywrightEngine

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

dotenv_path = os.path.join(os.path.dirname(__file__), "..", "..", ".env")
load_dotenv(dotenv_path)

AMANTEA_VENDOR_NAME = "아망티"
DEFAULT_CATALOG_URL = "https://www.amantea.co.kr/"
DEFAULT_DISCOVERY_URLS = [
    "https://www.amantea.co.kr/",
    "https://www.amantea.co.kr/product/list.html?cate_no=42",
]
DEFAULT_PRODUCT_URLS = [
    "https://www.amantea.co.kr/product/detail.html?product_no=426",
    "https://www.amantea.co.kr/product/detail.html?product_no=614",
    "https://www.amantea.co.kr/product/detail.html?product_no=1122",
]
DEFAULT_MAX_PRODUCTS = 24
DEFAULT_TIMEOUT_MS = 15000
DEFAULT_DELAY_SECONDS = 0.2
DEFAULT_DISCOVERY_LIMIT = 60


def safe_int(value: str | None, default: int) -> int:
    try:
        return int(str(value).strip())
    except (TypeError, ValueError):
        return default


def safe_float(value: str | None, default: float) -> float:
    try:
        return float(str(value).strip())
    except (TypeError, ValueError):
        return default


def canonicalize_product_url(raw_url: str) -> str | None:
    if not raw_url:
        return None

    parsed = urlparse(raw_url.strip())
    hostname = (parsed.hostname or "").lower()
    path = parsed.path or ""
    if not hostname.endswith("amantea.co.kr"):
        return None

    is_public_product = (
        path.endswith("/product/detail.html")
        or path.endswith("/shop/product/product_view")
        or "/product/list.html" in path
        or bool(re.search(r"/product/.+/\d+/?$", path))
    )
    if not is_public_product:
        return None

    return urlunparse(parsed._replace(fragment=""))


def parse_env_list(name: str, fallback: list[str]) -> list[str]:
    raw_value = os.getenv(name, "").strip()
    if not raw_value:
        return fallback
    return [part.strip() for part in raw_value.split(",") if part.strip()]


def wait_for_page_ready(page, timeout_ms: int):
    try:
        page.wait_for_load_state("networkidle", timeout=min(timeout_ms, 8000))
    except Exception:
        page.wait_for_timeout(1200)


def dismiss_known_popups(page):
    try:
        dismissed = page.evaluate(
            """
            () => {
                const normalize = (value) => (value || "").replace(/\\s+/g, " ").trim();
                const clickByText = (texts) => {
                    const nodes = Array.from(document.querySelectorAll("button, a, label, span, div"));
                    for (const node of nodes) {
                        const text = normalize(node.textContent);
                        if (texts.includes(text)) {
                            node.click();
                            return true;
                        }
                    }
                    return false;
                };

                return clickByText(["닫기", "오늘 하루 보지 않기", "개인정보 수집 및 이용 동의"]);
            }
            """
        )
        if dismissed:
            page.wait_for_timeout(800)
    except Exception:
        pass


def collect_product_links(page, limit: int) -> list[str]:
    discovered = page.evaluate(
        """
        () => Array.from(document.querySelectorAll("a[href]"))
            .map((anchor) => anchor.href)
            .filter(Boolean)
        """
    )

    links = []
    seen = set()
    for raw_url in discovered:
        normalized = canonicalize_product_url(raw_url)
        if not normalized or normalized in seen:
            continue
        seen.add(normalized)
        links.append(normalized)
        if len(links) >= limit:
            break
    return links


def build_spec(option_groups: list[list[str]], option_summary_text: str) -> str:
    chunks = []
    for group in option_groups[:2]:
        values = []
        for value in group[:4]:
            normalized = re.sub(r"\s+", " ", value).strip(" -")
            if not normalized:
                continue
            normalized = re.sub(r"\s*\(\+\d[\d,]*원\)", "", normalized).strip()
            normalized = re.sub(r"\s*-\s*일시품절$", "", normalized).strip()
            if normalized and normalized not in values:
                values.append(normalized)
        if values:
            chunks.append(", ".join(values))

    if chunks:
        return " / ".join(chunks)

    fallback = re.sub(r"\s+", " ", option_summary_text or "").strip()
    return fallback[:140] if fallback else "공개 옵션 정보 없음"


def extract_product_code(product_url: str, index: int) -> str:
    parsed = urlparse(product_url)
    query = parsed.query or ""
    for key in ("product_no", "product_cd", "item_code"):
        match = re.search(rf"(?:^|[?&]){key}=([^&]+)", query)
        if match:
            return match.group(1)

    path_match = re.search(r"/(\d+)/?$", parsed.path)
    if path_match:
        return path_match.group(1)

    return f"public-{index:03d}"


def extract_shipping_fee_number(raw_text: str) -> int | None:
    if not raw_text:
        return None
    match = re.search(r"([0-9,]+)\s*원", raw_text)
    if not match:
        return None
    return int(match.group(1).replace(",", ""))


def parse_product_detail(page, product_url: str, index: int, timeout_ms: int) -> tuple[dict, list[str]]:
    page.goto(product_url, wait_until="domcontentloaded", timeout=timeout_ms)
    wait_for_page_ready(page, timeout_ms)
    dismiss_known_popups(page)

    payload = page.evaluate(
        """
        () => {
            const normalize = (value) => (value || "").replace(/\\s+/g, " ").trim();
            const visibleText = (selector) => {
                const node = typeof selector === "string" ? document.querySelector(selector) : selector;
                if (!node) return "";
                const style = window.getComputedStyle(node);
                const visible = !!(node.offsetWidth || node.offsetHeight || node.getClientRects().length);
                if (!visible || style.visibility === "hidden" || style.display === "none") return "";
                return normalize(node.textContent);
            };

            const bodyTextRaw = document.body ? document.body.innerText : "";
            const bodyText = normalize(bodyTextRaw);
            const bodyLines = bodyTextRaw
                .split("\\n")
                .map((line) => normalize(line))
                .filter(Boolean);

            const firstPriceIndex = bodyLines.findIndex((line) => /[0-9,]+원/.test(line));
            const bodyTitleCandidate = firstPriceIndex <= 0
                ? ""
                : bodyLines
                    .slice(Math.max(0, firstPriceIndex - 6), firstPriceIndex)
                    .reverse()
                    .find((line) =>
                        line.length >= 8
                        && !/^리뷰\\s*\\d+개$/.test(line)
                        && !["닫기", "리뷰이동", "뒤로 가기", "공유하기 레이어 열기"].includes(line)
                    ) || "";

            const priceLines = bodyLines.filter((line) => /[0-9,]+원/.test(line));
            const optionGroups = Array.from(document.querySelectorAll("select"))
                .map((select) => Array.from(select.options)
                    .map((option) => normalize(option.textContent))
                    .filter((text) =>
                        text
                        && !text.includes("옵션을 선택")
                        && !/^[-\\s]+$/.test(text)
                    )
                )
                .filter((group) => group.length > 0);

            const categoryNodes = Array.from(document.querySelectorAll("a, li, span"))
                .map((node) => normalize(node.textContent))
                .filter((text) =>
                    text
                    && !["HOME", "닫기", "로그인", "회원가입"].includes(text)
                    && text.length <= 30
                );

            const detailPairs = {};
            const detailSectionLines = bodyLines.slice(
                Math.max(0, bodyLines.findIndex((line) => line.includes("상품 고시 정보"))),
                Math.min(bodyLines.length, bodyLines.findIndex((line) => line.includes("배송/반품/교환안내")) + 1 || bodyLines.length)
            );
            for (let i = 0; i < detailSectionLines.length - 1; i += 1) {
                const key = detailSectionLines[i];
                const value = detailSectionLines[i + 1];
                if (key && value && key.length <= 20 && value.length <= 80) {
                    detailPairs[key] = value;
                }
            }

            const titleCandidates = [
                bodyTitleCandidate,
                visibleText(".headingArea h2"),
                visibleText(".prdName"),
                visibleText('meta[property="og:title"]'),
                normalize(document.title)
            ].filter(Boolean);

            const title = titleCandidates
                .map((candidate) => candidate.replace(/^아망티\\s*[|ㅣ]\\s*/, "").trim())
                .find((candidate) => candidate && !candidate.includes("개인정보 수집 및 이용"))
                || "";

            const reviewMatch = bodyText.match(/리뷰\\s*(\\d+)개/);
            const optionSummaryMatch = bodyText.match(/상품 구성\\s+(.+?)\\s+사이즈 및 구성/);
            const shippingSectionMatch = bodyText.match(/배송비\\s+(.+?)(?:상품 구성|적립금|총 상품 금액)/);
            const labeledPriceMatch = bodyText.match(/판매가\\s*([0-9,]+)원/);
            const fallbackPrices = Array.from(bodyText.matchAll(/([0-9,]+)원/g)).map((match) => match[1]);

            return {
                title,
                priceText: labeledPriceMatch ? labeledPriceMatch[1] : (fallbackPrices[0] || ""),
                optionGroups,
                optionSummaryText: optionSummaryMatch ? optionSummaryMatch[1] : "",
                shippingFeeText: shippingSectionMatch ? shippingSectionMatch[1] : "",
                reviewCountText: reviewMatch ? reviewMatch[1] : "",
                categoryTrail: categoryNodes.slice(0, 8).join(" > "),
                manufacturer: detailPairs["제조자"] || "",
                countryOfOrigin: detailPairs["제조국"] || "",
                soldOut: /SOLD OUT|일시품절|품절/.test(bodyText),
                priceLines: priceLines.slice(0, 6)
            };
        }
        """
    )

    product_name = str(payload.get("title") or "").strip()
    if not product_name:
        raise RuntimeError(f"상품명을 찾지 못했어: {product_url}")

    unit_price_text = str(payload.get("priceText") or "").replace(",", "").strip()
    if not unit_price_text.isdigit():
        raise RuntimeError(f"판매가를 찾지 못했어: {product_url}")

    related_links = collect_product_links(page, DEFAULT_DISCOVERY_LIMIT)

    order = {
        "orderNo": f"AMANTEA-{extract_product_code(product_url, index)}",
        "productCode": extract_product_code(product_url, index),
        "productName": product_name,
        "quantity": 1,
        "unitPrice": int(unit_price_text),
        "spec": build_spec(payload.get("optionGroups") or [], str(payload.get("optionSummaryText") or "")),
        "vendorName": AMANTEA_VENDOR_NAME,
        "productUrl": product_url,
        "soldOut": bool(payload.get("soldOut")),
        "stockStatus": "SOLD_OUT" if payload.get("soldOut") else "AVAILABLE_OR_UNKNOWN",
        "shippingFee": extract_shipping_fee_number(str(payload.get("shippingFeeText") or "")),
        "category": str(payload.get("categoryTrail") or "").strip() or None,
        "reviewCount": safe_int(payload.get("reviewCountText"), 0),
        "manufacturer": str(payload.get("manufacturer") or "").strip() or None,
        "countryOfOrigin": str(payload.get("countryOfOrigin") or "").strip() or None,
        "priceCandidates": payload.get("priceLines") or [],
    }
    return order, related_links


def discover_initial_product_urls(context, catalog_url: str, max_products: int, timeout_ms: int) -> list[str]:
    seeded = []
    seen = set()
    for raw_url in parse_env_list("RPA_AMANTEA_PRODUCT_URLS", DEFAULT_PRODUCT_URLS):
        normalized = canonicalize_product_url(raw_url)
        if normalized and normalized not in seen:
            seen.add(normalized)
            seeded.append(normalized)

    for discovery_url in parse_env_list("RPA_AMANTEA_DISCOVERY_URLS", DEFAULT_DISCOVERY_URLS):
        if len(seeded) >= max_products:
            break
        page = context.new_page()
        try:
            page.goto(discovery_url or catalog_url, wait_until="domcontentloaded", timeout=timeout_ms)
            wait_for_page_ready(page, timeout_ms)
            dismiss_known_popups(page)
            for product_url in collect_product_links(page, DEFAULT_DISCOVERY_LIMIT):
                if product_url in seen:
                    continue
                seen.add(product_url)
                seeded.append(product_url)
                if len(seeded) >= max_products:
                    break
        except Exception as discovery_exception:
            print(f"[RPA Task] Discovery page skipped {discovery_url}: {str(discovery_exception)}")
        finally:
            page.close()

    return seeded[:max_products]


def run_purchase_order_task(task_id: str, task_type: str = "PURCHASE_PRICE", action_name: str = "collect_purchase_orders"):
    print(f"[RPA Task] Starting public Amantea collection. Task ID: {task_id}, Task Type: {task_type}, Action: {action_name}")

    catalog_url = os.getenv("RPA_AMANTEA_CATALOG_URL", DEFAULT_CATALOG_URL).strip() or DEFAULT_CATALOG_URL
    max_products = max(1, safe_int(os.getenv("RPA_AMANTEA_MAX_PRODUCTS"), DEFAULT_MAX_PRODUCTS))
    timeout_ms = max(5000, safe_int(os.getenv("RPA_PUBLIC_PAGE_TIMEOUT_MS"), DEFAULT_TIMEOUT_MS))
    per_item_delay = max(0.0, safe_float(os.getenv("RPA_PUBLIC_PAGE_DELAY_SECONDS"), DEFAULT_DELAY_SECONDS))
    callback_token = os.getenv("RPA_CALLBACK_TOKEN")

    backend_callback_url = os.getenv("BACKEND_CALLBACK_URL")
    if not backend_callback_url:
        backend_base_url = os.getenv("BACKEND_API_BASE_URL", "http://localhost:8080/api/v1")
        backend_callback_url = f"{backend_base_url}/callbacks/rpa"

    output_dir = os.path.join(os.path.dirname(__file__), "..", "outputs")
    os.makedirs(output_dir, exist_ok=True)
    screenshot_dir = os.path.join(output_dir, "screenshots")
    os.makedirs(screenshot_dir, exist_ok=True)

    engine = PlaywrightEngine()
    context = None
    page = None

    max_retries = 3
    last_exception = None
    last_screenshot_path = None

    for attempt in range(1, max_retries + 1):
        context = None
        page = None
        print(f"[RPA Task] Executing public crawl attempt {attempt} of {max_retries} for task: {task_id}")

        try:
            context = engine.get_context()
            page = context.new_page()

            initial_urls = discover_initial_product_urls(context, catalog_url, max_products, timeout_ms)
            if not initial_urls:
                raise RuntimeError("아망티(amantea.co.kr) 공개 페이지에서 상품 링크를 찾지 못했어.")

            queue = deque(initial_urls)
            queued = set(initial_urls)
            processed = set()
            orders = []
            detail_index = 1

            print(f"[RPA Task] Starting crawl with {len(initial_urls)} initial product links.")

            while queue and len(orders) < max_products:
                product_url = queue.popleft()
                if product_url in processed:
                    continue
                processed.add(product_url)

                detail_page = context.new_page()
                try:
                    order, related_links = parse_product_detail(detail_page, product_url, detail_index, timeout_ms)
                    orders.append(order)
                    detail_index += 1
                    print(f"[RPA Task] Collected {order['productCode']} / {order['productName']}")

                    for related_url in related_links:
                        if related_url in queued or related_url in processed:
                            continue
                        queued.add(related_url)
                        queue.append(related_url)
                        if len(queued) >= max_products * 3:
                            break
                except Exception as detail_exception:
                    print(f"[RPA Task] Failed to parse {product_url}: {str(detail_exception)}")
                finally:
                    detail_page.close()

                if per_item_delay > 0:
                    time.sleep(per_item_delay)

            if not orders:
                raise RuntimeError("아망티 공개 상품 페이지는 열렸지만 발주 비교에 필요한 공개 데이터를 추출하지 못했어.")

            result_path = os.path.join(output_dir, f"orders_{task_id}.json")
            with open(result_path, "w", encoding="utf-8") as file:
                json.dump(orders, file, ensure_ascii=False, indent=2)

            print(f"[RPA Task] Public data collection complete. Saved {len(orders)} rows to: {result_path}")

            send_callback(backend_callback_url, callback_token, {
                "taskId": task_id,
                "taskType": task_type,
                "actionName": action_name,
                "status": "success",
                "data": {
                    "ordersCount": len(orders),
                    "filePath": f"rpa/outputs/orders_{task_id}.json"
                }
            })

            if context:
                context.close()
            return

        except Exception as exc:
            last_exception = exc
            print(f"[RPA Task] Attempt {attempt} failed: {str(exc)}")

            screenshot_path = os.path.join(screenshot_dir, f"err_{task_id}_attempt_{attempt}.png")
            last_screenshot_path = screenshot_path
            if page:
                try:
                    page.screenshot(path=screenshot_path)
                    print(f"[RPA Task] Error screenshot saved: {screenshot_path}")
                except Exception as screenshot_exception:
                    print(f"[RPA Task] Failed to take screenshot: {str(screenshot_exception)}")
                    last_screenshot_path = None

            if context:
                try:
                    context.close()
                except Exception:
                    pass
            try:
                engine.shutdown()
            except Exception:
                pass

            if attempt < max_retries:
                time.sleep(2)

    error_msg = str(last_exception) if last_exception else "아망티 공개 크롤링 시도 횟수를 초과했어."
    handle_error(
        task_id,
        task_type,
        action_name,
        "RPA_EXECUTION_FAILED",
        error_msg,
        backend_callback_url,
        callback_token,
        last_screenshot_path
    )


def send_callback(callback_url: str, token: str, payload: dict):
    headers = {
        "Content-Type": "application/json",
        "X-RPA-Token": token if token else ""
    }
    try:
        print(f"[RPA Task] Sending callback to {callback_url}")
        response = requests.post(callback_url, json=payload, headers=headers, timeout=5)
        print(f"[RPA Task] Callback response: HTTP {response.status_code}")
    except Exception as exc:
        print(f"[RPA Task] Failed to send callback to backend: {str(exc)}")


def handle_error(task_id: str, task_type: str, action_name: str, code: str, message: str, callback_url: str, token: str, screenshot_path: str = None):
    payload = {
        "taskId": task_id,
        "taskType": task_type,
        "actionName": action_name,
        "status": "failed",
        "error": {
            "code": code,
            "message": message
        }
    }
    if screenshot_path:
        filename = os.path.basename(screenshot_path)
        payload["error"]["screenshotPath"] = f"rpa/outputs/screenshots/{filename}"

    send_callback(callback_url, token, payload)


if __name__ == "__main__":
    task_id = sys.argv[1] if len(sys.argv) > 1 else f"task_{int(time.time())}"
    task_type = sys.argv[2] if len(sys.argv) > 2 else "PURCHASE_PRICE"
    action_name = sys.argv[3] if len(sys.argv) > 3 else "collect_purchase_orders"
    run_purchase_order_task(task_id, task_type, action_name)
