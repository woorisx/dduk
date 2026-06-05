import json
import logging
import os
import re

import google.generativeai as genai


logger = logging.getLogger(__name__)


class OcrGeminiService:
    DEFAULT_MODEL_CANDIDATES = (
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-1.5-flash",
    )

    OCR_PROMPT = """
You are an OCR extraction service for DDUK ERP.

Read the attached business document image and return only one JSON object.
Do not wrap the response in markdown fences.

Required JSON shape:
{
  "documentType": "RECEIPT",
  "vendorName": "string or null",
  "transactionDate": "YYYY-MM-DD or null",
  "totalAmount": number or null,
  "currency": "KRW",
  "items": [
    {
      "name": "string",
      "quantity": number or null,
      "unitPrice": number or null,
      "amount": number or null
    }
  ],
  "confidence": number,
  "notes": ["string"]
}

Rules:
- Keep date in ISO YYYY-MM-DD when clearly identifiable.
- totalAmount must be the final paid amount when visible.
- If a field is not readable, return null.
- Keep notes as a short array describing ambiguities.
- Use Korean document understanding, but JSON keys must stay English.
""".strip()

    def __init__(self):
        api_key = os.getenv("GEMINI_API_KEY")
        if not api_key or api_key == "replace_with_gemini_api_key":
            raise ValueError("GEMINI_API_KEY is not set or invalid in environment variables.")

        genai.configure(api_key=api_key)

        requested_model = (os.getenv("GEMINI_MODEL") or "").strip()
        available_models = self._list_generate_content_models()
        self.model_name = self._select_model_name(requested_model, available_models)
        self.model = genai.GenerativeModel(self.model_name)
        logger.info("OCR Gemini model selected: %s", self.model_name)

    def _list_generate_content_models(self) -> set[str]:
        try:
            model_names = set()
            for model in genai.list_models():
                supported_methods = set(getattr(model, "supported_generation_methods", []) or [])
                if "generateContent" in supported_methods:
                    model_names.add(model.name.split("/")[-1])
            return model_names
        except Exception as exc:
            logger.warning("Failed to list Gemini models for OCR service: %s", exc)
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

    def extract_document(self, image_bytes: bytes, mime_type: str, document_type: str) -> dict:
        prompt = self.OCR_PROMPT.replace('"documentType": "RECEIPT"', f'"documentType": "{document_type}"')

        try:
            response = self.model.generate_content([
                prompt,
                {"mime_type": mime_type, "data": image_bytes}
            ])
            parsed = self._parse_json_response(response.text)
            parsed["documentType"] = document_type
            parsed.setdefault("currency", "KRW")
            parsed.setdefault("items", [])
            parsed.setdefault("notes", [])
            parsed.setdefault("confidence", 0.0)
            return parsed
        except Exception as exc:
            raise RuntimeError(f"Gemini OCR invocation failed: {str(exc)}")

    def _parse_json_response(self, text: str) -> dict:
        if not text:
            raise ValueError("Gemini OCR returned an empty response.")

        cleaned = text.strip()
        cleaned = re.sub(r"^```json\s*", "", cleaned)
        cleaned = re.sub(r"^```\s*", "", cleaned)
        cleaned = re.sub(r"\s*```$", "", cleaned)

        start = cleaned.find("{")
        end = cleaned.rfind("}")
        if start >= 0 and end >= start:
            cleaned = cleaned[start:end + 1]

        parsed = json.loads(cleaned)
        if not isinstance(parsed, dict):
            raise ValueError("Gemini OCR returned a non-object JSON payload.")
        return parsed
