import os
import sys
import json
import time
import requests
from dotenv import load_dotenv

sys.path.append(os.path.join(os.path.dirname(__file__), ".."))
from engine.playwright_engine import PlaywrightEngine

dotenv_path = os.path.join(os.path.dirname(__file__), "..", "..", ".env")
load_dotenv(dotenv_path)


def run_hr_reference_task(task_id: str, task_type: str = "HR_MIN_WAGE", action_name: str = "collect_hr_reference"):
    print(f"[RPA Task] Starting HR reference collection. Task ID: {task_id}, Task Type: {task_type}, Action: {action_name}")

    base_url = os.getenv("RPA_BASE_URL", "http://localhost:5050")
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

    try:
        context = engine.get_context()
        page = context.new_page()

        reference_url = f"{base_url}/hr-reference"
        print(f"[RPA Task] Navigating to {reference_url}")
        page.goto(reference_url)
        page.wait_for_selector("#hr-reference-table", timeout=10000)

        row = page.query_selector("#hr-reference-table tbody tr")
        cols = row.query_selector_all("td") if row else []
        if len(cols) < 5:
            raise RuntimeError("HR reference table row is incomplete.")

        reference = {
            "sourceLabel": cols[0].inner_text().strip(),
            "effectiveDate": cols[1].inner_text().strip(),
            "minimumHourlyWage": int(cols[2].inner_text().strip().replace(",", "").replace("원", "")),
            "minimumMonthlySalary": int(cols[3].inner_text().strip().replace(",", "").replace("원", "")),
            "guideline": cols[4].inner_text().strip()
        }

        result_path = os.path.join(output_dir, f"hr_reference_{task_id}.json")
        with open(result_path, "w", encoding="utf-8") as file:
            json.dump(reference, file, ensure_ascii=False, indent=2)

        send_callback(backend_callback_url, callback_token, {
            "taskId": task_id,
            "taskType": task_type,
            "actionName": action_name,
            "status": "success",
            "data": {
                "referenceType": "MIN_WAGE",
                "filePath": f"rpa/outputs/hr_reference_{task_id}.json"
            }
        })
    except Exception as exc:
        screenshot_path = os.path.join(screenshot_dir, f"err_hr_reference_{task_id}.png")
        if page:
            try:
                page.screenshot(path=screenshot_path)
            except Exception:
                screenshot_path = None

        handle_error(
            task_id,
            task_type,
            action_name,
            "RPA_EXECUTION_FAILED",
            str(exc),
            backend_callback_url,
            callback_token,
            screenshot_path
        )
    finally:
        if context:
            try:
                context.close()
            except Exception:
                pass
        try:
            engine.shutdown()
        except Exception:
            pass


def send_callback(callback_url: str, token: str, payload: dict):
    headers = {
        "Content-Type": "application/json",
        "X-RPA-Token": token if token else ""
    }
    try:
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
    task_type = sys.argv[2] if len(sys.argv) > 2 else "HR_MIN_WAGE"
    action_name = sys.argv[3] if len(sys.argv) > 3 else "collect_hr_reference"
    run_hr_reference_task(task_id, task_type, action_name)
