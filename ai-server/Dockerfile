FROM python:3.11-slim

WORKDIR /app

# 의존성 설치
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 소스 복사
COPY . .

# 런타임 환경변수 설정 (프로덕션 환경에서는 waitress 구동됨)
ENV FLASK_ENV=production
ENV AI_SERVER_HOST=0.0.0.0
ENV AI_SERVER_PORT=5000

EXPOSE 5000

CMD ["python", "app.py"]
