import logging

from flask import Blueprint, jsonify, request

from services.ocr_gemini_service import OcrGeminiService


logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

ocr_bp = Blueprint("ocr", __name__)

try:
    ocr_service = OcrGeminiService()
except Exception as exc:
    logger.error("Failed to initialize OcrGeminiService: %s", exc)
    ocr_service = None


@ocr_bp.route("/api/v1/ai/ocr/documents", methods=["POST"])
def parse_document():
    if not ocr_service:
        return jsonify({
            "status": "error",
            "message": "OCR AI 서비스 초기화에 실패했습니다. 환경변수 설정을 확인해 주세요.",
            "code": "OCR_INITIALIZATION_FAILED"
        }), 503

    file = request.files.get("file")
    document_type = (request.form.get("documentType") or "RECEIPT").strip().upper()

    if file is None or not file.filename:
        return jsonify({
            "status": "error",
            "message": "OCR 처리할 파일이 없습니다.",
            "code": "INVALID_REQUEST_PARAMETER"
        }), 400

    mime_type = file.mimetype or "application/octet-stream"
    if mime_type not in {"image/png", "image/jpeg", "image/webp"}:
        return jsonify({
            "status": "error",
            "message": "지원하지 않는 OCR 파일 형식입니다.",
            "code": "UNSUPPORTED_FILE_TYPE"
        }), 400

    try:
        payload = ocr_service.extract_document(file.read(), mime_type, document_type)
        return jsonify({
            "status": "success",
            "data": payload,
            "message": "OCR 분석이 완료되었습니다."
        }), 200
    except ValueError as exc:
        logger.error("OCR configuration/value error: %s", exc)
        return jsonify({
            "status": "error",
            "message": str(exc),
            "code": "OCR_CONFIGURATION_ERROR"
        }), 400
    except RuntimeError as exc:
        logger.error("OCR runtime error: %s", exc)
        return jsonify({
            "status": "error",
            "message": "OCR 분석 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
            "code": "OCR_SERVICE_ERROR"
        }), 503
    except Exception as exc:
        logger.error("Unexpected OCR error: %s", exc)
        return jsonify({
            "status": "error",
            "message": "서버 내부 오류가 발생했습니다.",
            "code": "INTERNAL_SERVER_ERROR"
        }), 500
