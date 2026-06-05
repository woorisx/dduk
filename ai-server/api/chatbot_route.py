from flask import Blueprint, request, jsonify
from services.gemini_service import GeminiService
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

chatbot_bp = Blueprint("chatbot", __name__)

# 싱글톤 형태로 서비스 기동
try:
    gemini_service = GeminiService()
except Exception as e:
    logger.error(f"Failed to initialize GeminiService: {str(e)}")
    gemini_service = None

@chatbot_bp.route("/api/v1/ai/chat", methods=["POST"])
def chat():
    """
    백엔드 프록시를 통해 전달받은 질문과 대화 이력을 처리하는 엔드포인트
    """
    if not gemini_service:
        return jsonify({
            "status": "error",
            "message": "AI 서비스 초기화에 실패하여 구동할 수 없습니다. 환경변수 설정을 확인해 주세요.",
            "code": "AI_INITIALIZATION_FAILED"
        }), 503

    try:
        data = request.get_json() or {}
        message = data.get("message")
        history = data.get("history", [])

        if not message:
            return jsonify({
                "status": "error",
                "message": "질문 내용(message)이 비어 있습니다.",
                "code": "INVALID_REQUEST_PARAMETER"
            }), 400

        # Gemini API 호출 및 결과 생성
        response_text = gemini_service.generate_chat_response(message, history)

        return jsonify({
            "status": "success",
            "data": {
                "response": response_text
            },
            "message": "요청이 완료되었습니다."
        }), 200

    except ValueError as ve:
        logger.error(f"Value error in chat endpoint: {str(ve)}")
        return jsonify({
            "status": "error",
            "message": str(ve),
            "code": "AI_CONFIGURATION_ERROR"
        }), 400
    except RuntimeError as re:
        logger.error(f"Runtime error in Gemini API call: {str(re)}")
        return jsonify({
            "status": "error",
            "message": "AI 서비스 응답 도중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
            "code": "AI_SERVICE_ERROR"
        }), 503
    except Exception as e:
        logger.error(f"Unexpected error in chat endpoint: {str(e)}")
        return jsonify({
            "status": "error",
            "message": "서버 내부 오류가 발생했습니다.",
            "code": "INTERNAL_SERVER_ERROR"
        }), 500
