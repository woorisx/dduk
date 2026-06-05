FROM mcr.microsoft.com/playwright/python:v1.40.0-jammy

WORKDIR /app

# 의존성 복사 및 설치
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Playwright용 Chromium 브라우저 명시적 설치
RUN playwright install chromium

# 소스 전체 복사
COPY . .

# 런타임 환경변수 설정
ENV RPA_SERVER_HOST=0.0.0.0
ENV RPA_SERVER_PORT=5050

EXPOSE 5050

CMD ["python", "app.py"]
