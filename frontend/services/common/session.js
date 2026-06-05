(function () {
    function normalizeApiBaseUrl(value) {
        if (typeof value !== "string") {
            return null;
        }
        const trimmed = value.trim();
        if (!trimmed) {
            return "";
        }
        return trimmed.replace(/\/+$/, "");
    }

    function getLocalApiBaseUrl() {
        const host = window.location.hostname === "127.0.0.1" ? "127.0.0.1" : "localhost";
        return `http://${host}:8080`;
    }

    const rolePathMap = {
        ADMIN: "dashboard.html",
        HR: "dashboard.html",
        INVENTORY: "dashboard.html"
    };

    function getSession() {
        return {
            token: localStorage.getItem("token"),
            loginId: localStorage.getItem("loginId"),
            role: localStorage.getItem("role"),
            userName: localStorage.getItem("userName")
        };
    }

    function getApiBaseUrl() {
        const explicitBaseUrl = normalizeApiBaseUrl(window.__DDUK_API_BASE_URL__)
            ?? normalizeApiBaseUrl(document.querySelector('meta[name="dduk-api-base-url"]')?.content)
            ?? normalizeApiBaseUrl(window.localStorage?.getItem("dduk.apiBaseUrl"));

        if (explicitBaseUrl !== null) {
            return explicitBaseUrl;
        }

        if (window.location.protocol === "file:") {
            return getLocalApiBaseUrl();
        }

        if (["localhost", "127.0.0.1"].includes(window.location.hostname) && window.location.port && window.location.port !== "8080") {
            return getLocalApiBaseUrl();
        }

        return "";
    }

    function getAuthHeaders(extraHeaders) {
        const session = getSession();
        const headers = {
            Authorization: `Bearer ${session.token}`
        };

        return Object.assign(headers, extraHeaders || {});
    }

    function clearSession() {
        localStorage.removeItem("token");
        localStorage.removeItem("loginId");
        localStorage.removeItem("role");
        localStorage.removeItem("userName");
    }

    function redirectToLogin() {
        window.location.href = "../../index.html";
    }

    function requireRole(allowedRoles) {
        const session = getSession();

        if (!session.token || !session.role) {
            redirectToLogin();
            return null;
        }

        if (allowedRoles.length > 0 && !allowedRoles.includes(session.role)) {
            window.ddukApi?.showToast?.("해당 페이지에 접근할 권한이 없습니다.", "warning");

            const path = window.location.pathname.replace(/\\/g, '/');
            let rootRedirect = 'dashboard.html';
            if (path.includes('/pages/')) {
                const depth = path.split('/pages/')[1].split('/').length;
                rootRedirect = '../'.repeat(depth) + 'dashboard.html';
            }
            window.location.href = rootRedirect;

            return null;
        }

        return session;
    }

    function bindShell(session) {
        const userNameElement = document.querySelector("[data-user-name]");
        const loginIdElement = document.querySelector("[data-login-id]");
        const roleElement = document.querySelector("[data-role]");
        const logoutButton = document.querySelector("[data-action='logout']");

        if (userNameElement) {
            userNameElement.textContent = session.userName || "-";
        }

        if (loginIdElement) {
            loginIdElement.textContent = session.loginId || "-";
        }

        if (roleElement) {
            roleElement.textContent = session.role || "-";
        }

        if (logoutButton) {
            logoutButton.addEventListener("click", function () {
                clearSession();
                redirectToLogin();
            });
        }
    }

    window.ddukSession = {
        bindShell,
        clearSession,
        getApiBaseUrl,
        getAuthHeaders,
        getSession,
        requireRole
    };
})();
