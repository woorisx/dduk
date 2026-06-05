function resolveApiBaseUrl() {
    if (window.ddukSession && typeof window.ddukSession.getApiBaseUrl === "function") {
        return window.ddukSession.getApiBaseUrl();
    }

    if (typeof window.__DDUK_API_BASE_URL__ === "string" && window.__DDUK_API_BASE_URL__.trim()) {
        return window.__DDUK_API_BASE_URL__.trim().replace(/\/+$/, "");
    }

    if (window.location.protocol === "file:") {
        return window.location.hostname === "127.0.0.1" ? "http://127.0.0.1:8080" : "http://localhost:8080";
    }

    if (["localhost", "127.0.0.1"].includes(window.location.hostname) && window.location.port && window.location.port !== "8080") {
        return window.location.hostname === "127.0.0.1" ? "http://127.0.0.1:8080" : "http://localhost:8080";
    }

    return "";
}

const API_BASE_URL = resolveApiBaseUrl();

const loginForm = document.getElementById("loginForm");
const loginButton = document.getElementById("loginBtn");
const loginButtonText = loginButton ? loginButton.querySelector("span") : null;
const messageElement = document.getElementById("message");
const passwordInput = document.getElementById("password");
const passwordToggle = document.querySelector(".password-toggle");

const roleRedirectMap = {
    ADMIN: "dashboard.html",
    HR: "dashboard.html",
    INVENTORY: "dashboard.html"
};

function setMessage(text, type) {
    if (!messageElement) {
        return;
    }

    messageElement.textContent = text;
    messageElement.className = type ? `message ${type}` : "message";
}

function setLoginButtonText(text) {
    if (!loginButton) {
        return;
    }

    if (loginButtonText) {
        loginButtonText.textContent = text;
        return;
    }

    loginButton.textContent = text;
}

function storeSession(data) {
    localStorage.setItem("token", data.token);
    localStorage.setItem("loginId", data.loginId);
    localStorage.setItem("role", data.role);
    localStorage.setItem("userName", data.name);
}

function loadSavedValues() {
    const saveCompanyCode = document.querySelector('[name="saveCompanyCode"]');
    const savedCompanyCode = localStorage.getItem("savedCompanyCode");
    if (savedCompanyCode) {
        document.getElementById("companyCode").value = savedCompanyCode;
        if (saveCompanyCode) {
            saveCompanyCode.checked = true;
        }
    }

    const rememberId = document.querySelector('[name="rememberId"]');
    const savedLoginId = localStorage.getItem("savedLoginId");
    if (savedLoginId) {
        document.getElementById("loginId").value = savedLoginId;
        if (rememberId) {
            rememberId.checked = true;
        }
    }
}

function initPasswordToggle() {
    if (!passwordToggle || !passwordInput) {
        return;
    }

    passwordToggle.addEventListener("click", () => {
        const isVisible = passwordInput.type === "text";

        passwordInput.type = isVisible ? "password" : "text";
        passwordToggle.setAttribute("aria-pressed", String(!isVisible));
        passwordToggle.setAttribute("aria-label", isVisible ? "비밀번호 보기" : "비밀번호 숨기기");
    });
}

async function parseResponseBody(response) {
    const contentType = response.headers.get("content-type") || "";

    if (!contentType.includes("application/json")) {
        return null;
    }

    try {
        return await response.json();
    } catch (error) {
        console.error("로그인 응답 JSON 파싱 실패:", error);
        return null;
    }
}

if (loginForm && loginButton && passwordInput) {
    loadSavedValues();
    initPasswordToggle();

    loginForm.addEventListener("submit", async (event) => {
        event.preventDefault();

        const loginId = document.getElementById("loginId").value.trim();
        const password = passwordInput.value;
        const companyCodeInput = document.getElementById("companyCode");
        const companyCodeVal = companyCodeInput ? companyCodeInput.value.trim() : "";
        let loginSucceeded = false;

        setMessage("", "");
        loginButton.disabled = true;
        loginButton.classList.add("is-loading");
        setLoginButtonText("로그인 중...");

        try {
            const response = await fetch(`${API_BASE_URL}/api/v1/auth/login`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({ loginId, password })
            });

            const data = await parseResponseBody(response);

            if (!response.ok || !data || data.status !== "success" || !data.data) {
                setMessage(data?.message || "로그인에 실패했습니다.", "error");
                return;
            }

            storeSession(data.data);

            const saveCompanyCodeCheckbox = document.querySelector('[name="saveCompanyCode"]');
            if (saveCompanyCodeCheckbox && saveCompanyCodeCheckbox.checked) {
                localStorage.setItem("savedCompanyCode", companyCodeVal);
            } else {
                localStorage.removeItem("savedCompanyCode");
            }

            const rememberIdCheckbox = document.querySelector('[name="rememberId"]');
            if (rememberIdCheckbox && rememberIdCheckbox.checked) {
                localStorage.setItem("savedLoginId", loginId);
            } else {
                localStorage.removeItem("savedLoginId");
            }

            loginSucceeded = true;
            setMessage("로그인 성공. 대시보드로 이동합니다.", "success");
            setLoginButtonText("접속 중...");

            const redirectPath = roleRedirectMap[data.data.role] || "dashboard.html";
            window.setTimeout(() => {
                window.location.href = redirectPath;
            }, 300);
        } catch (error) {
            console.error("로그인 중 오류 발생:", error);
            setMessage("서버와 통신할 수 없습니다.", "error");
        } finally {
            if (loginSucceeded) {
                loginButton.classList.remove("is-loading");
                return;
            }

            loginButton.disabled = false;
            loginButton.classList.remove("is-loading");
            setLoginButtonText("로그인");
        }
    });
}
