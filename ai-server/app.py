import os
import logging
from flask import Flask
from flask_cors import CORS
from dotenv import load_dotenv

# 루트 .env 로드
dotenv_path = os.path.join(os.path.dirname(__file__), "..", ".env")
load_dotenv(dotenv_path)

# 라우터 Blueprint 등록을 위해 지연 임포트(dotenv 로드 후 호출되어야 함)
from api.chatbot_route import chatbot_bp
from api.ocr_route import ocr_bp

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def create_app():
    app = Flask(__name__)

    # CORS 설정 최소화 정책: 백엔드 내부 프록시만 허용
    # application.yml에 명시된 백엔드 오리진(기본 http://localhost:8080)만 허용
    backend_url = os.getenv("BACKEND_API_BASE_URL", "http://localhost:8080")
    # 원격 백엔드 포털 주소 파싱하여 도메인 오리진만 기입
    allowed_origin = "http://localhost:8080"
    if backend_url:
        # http://localhost:8080/api/v1 -> http://localhost:8080 추출
        parts = backend_url.split("/api")
        if len(parts) > 0:
            allowed_origin = parts[0]

    CORS(app, resources={r"/api/v1/*": {"origins": [allowed_origin]}})
    logger.info(f"CORS restricted to origin: {allowed_origin}")

    # Blueprints
    app.register_blueprint(chatbot_bp)
    app.register_blueprint(ocr_bp)

    @app.route("/health", methods=["GET"])
    def health():
        return {"status": "UP"}, 200

    return app

app = create_app()

if __name__ == "__main__":
    port = int(os.getenv("AI_SERVER_PORT", 5000))
    host = os.getenv("AI_SERVER_HOST", "127.0.0.1")
    
    # 개발 환경(development)에만 Flask debug 서버 구동, 프로덕션(production)에서는 waitress 사용
    env = os.getenv("FLASK_ENV", "development")
    
    if env == "development":
        logger.info(f"Starting Flask development server on {host}:{port} with debug mode...")
        app.run(host=host, port=port, debug=True)
    else:
        from waitress import serve
        logger.info(f"Starting production WSGI server (Waitress) on {host}:{port}...")
        serve(app, host=host, port=port, threads=8)
