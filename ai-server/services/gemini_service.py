import logging
import os

import google.generativeai as genai


logger = logging.getLogger(__name__)


class GeminiService:
    DEFAULT_MODEL_CANDIDATES = (
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-1.5-flash",
    )
    SYSTEM_INSTRUCTION = """
You are the DDUK ERP AI assistant.

Rules:
- Reply in natural Korean by default, even when the user writes short or informal Korean.
- Use a concise, practical tone suitable for ERP users and teammates.
- When the question is about HR, inventory, admin, accounting, purchasing, or operations, answer in that business context first.
- Do not guess that the user's text is a foreign language unless it is clearly written in that language.
- If the input looks corrupted, ambiguous, or partially unreadable, say that the text may be broken and ask the user to send it again in Korean.
- Prefer direct answers, short step-by-step guidance, and concrete examples over generic theory.
""".strip()

    def __init__(self):
        api_key = os.getenv("GEMINI_API_KEY")
        if not api_key or api_key == "replace_with_gemini_api_key":
            raise ValueError("GEMINI_API_KEY is not set or invalid in environment variables.")

        genai.configure(api_key=api_key)

        requested_model = (os.getenv("GEMINI_MODEL") or "").strip()
        available_models = self._list_generate_content_models()
        self.model_name = self._select_model_name(requested_model, available_models)
        self.model = genai.GenerativeModel(
            self.model_name,
            system_instruction=self.SYSTEM_INSTRUCTION,
        )
        logger.info("Gemini model selected: %s", self.model_name)

    def _list_generate_content_models(self) -> set[str]:
        try:
            model_names = set()
            for model in genai.list_models():
                supported_methods = set(getattr(model, "supported_generation_methods", []) or [])
                if "generateContent" in supported_methods:
                    model_names.add(model.name.split("/")[-1])
            if model_names:
                logger.info("Discovered Gemini generateContent models: %s", sorted(model_names))
            return model_names
        except Exception as exc:
            logger.warning("Failed to list Gemini models, falling back to local candidates: %s", exc)
            return set()

    def _select_model_name(self, requested_model: str, available_models: set[str]) -> str:
        candidates = []
        if requested_model:
            candidates.append(requested_model)
        candidates.extend(self.DEFAULT_MODEL_CANDIDATES)

        if not available_models:
            return candidates[0]

        for candidate in candidates:
            if candidate in available_models:
                return candidate

        available_list = ", ".join(sorted(available_models))
        if requested_model:
            raise ValueError(
                f"Configured GEMINI_MODEL '{requested_model}' is unavailable. Available models: {available_list}"
            )

        raise ValueError(f"No supported Gemini fallback model was found. Available models: {available_list}")

    def generate_chat_response(self, message: str, history: list = None) -> str:
        """
        history format: [{"role": "user"|"model", "content": "..."}]
        """
        try:
            formatted_history = []
            if history:
                for turn in history:
                    role = turn.get("role")
                    content = turn.get("content")
                    if role in ["user", "model"] and content:
                        formatted_history.append({
                            "role": role,
                            "parts": [content],
                        })

            chat = self.model.start_chat(history=formatted_history)
            response = chat.send_message(message)
            return response.text
        except Exception as exc:
            raise RuntimeError(f"Gemini API invocation failed: {str(exc)}")
