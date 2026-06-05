(function () {
    // ===================================================
    // DDUK ERP 공용 API 클라이언트 (apiClient.js)
    // - JWT 자동 포함
    // - base URL 자동 연결
    // - 401 자동 감지 + Toast UX
    // - 모든 에러 상태 분기 처리
    // ===================================================

    function resolveApiBaseUrl() {
        if (window.ddukSession && typeof window.ddukSession.getApiBaseUrl === 'function') {
            return window.ddukSession.getApiBaseUrl();
        }
        if (typeof window.__DDUK_API_BASE_URL__ === 'string' && window.__DDUK_API_BASE_URL__.trim()) {
            return window.__DDUK_API_BASE_URL__.trim().replace(/\/+$/, '');
        }
        if (window.location.protocol === 'file:') {
            return window.location.hostname === '127.0.0.1' ? 'http://127.0.0.1:8080' : 'http://localhost:8080';
        }
        if (['localhost', '127.0.0.1'].includes(window.location.hostname) && window.location.port && window.location.port !== '8080') {
            return window.location.hostname === '127.0.0.1' ? 'http://127.0.0.1:8080' : 'http://localhost:8080';
        }
        return '';
    }

    const API_BASE_URL = resolveApiBaseUrl();

    // ── Toast 컨테이너 (DOM에 없으면 자동 생성) ──────────────────
    function ensureToastContainer() {
        let container = document.getElementById('dduk-toast-container');
        if (!container) {
            container = document.createElement('div');
            container.id = 'dduk-toast-container';
            container.style.cssText = `
                position: fixed; top: 1.25rem; right: 1.25rem; z-index: 99999;
                display: flex; flex-direction: column; gap: 0.5rem;
                pointer-events: none;
            `;
            document.body.appendChild(container);
        }
        return container;
    }

    /**
     * Toast 알림 표시
     * @param {string} message  표시할 메시지
     * @param {'info'|'success'|'warning'|'error'} type  종류
     * @param {number} durationMs  자동 닫힘 시간(ms), 0이면 수동 닫기
     */
    function showToast(message, type = 'info', durationMs = 5000) {
        const container = ensureToastContainer();

        const colorMap = {
            info:    { bg: '#eff6ff', border: '#bfdbfe', text: '#1e40af', icon: 'ℹ️' },
            success: { bg: '#f0fdf4', border: '#bbf7d0', text: '#166534', icon: '✅' },
            warning: { bg: '#fffbeb', border: '#fde68a', text: '#92400e', icon: '⚠️' },
            error:   { bg: '#fef2f2', border: '#fecaca', text: '#991b1b', icon: '❌' }
        };
        const c = colorMap[type] || colorMap.info;

        const toast = document.createElement('div');
        toast.style.cssText = `
            background: ${c.bg}; border: 1px solid ${c.border}; color: ${c.text};
            padding: 0.875rem 1.25rem; border-radius: 0.75rem;
            box-shadow: 0 8px 30px rgba(0,0,0,0.12);
            font-size: 0.85rem; font-weight: 600;
            display: flex; align-items: center; gap: 0.625rem;
            max-width: 22rem; pointer-events: all;
            animation: ddukToastIn 0.25s ease;
            font-family: 'Noto Sans KR', 'Outfit', sans-serif;
        `;
        toast.innerHTML = `<span style="font-size:1rem">${c.icon}</span><span>${message}</span>`;

        // 애니메이션 키프레임 삽입 (한 번만)
        if (!document.getElementById('dduk-toast-style')) {
            const style = document.createElement('style');
            style.id = 'dduk-toast-style';
            style.textContent = `
                @keyframes ddukToastIn { from { opacity:0; transform:translateX(1rem); } to { opacity:1; transform:none; } }
                @keyframes ddukToastOut { from { opacity:1; } to { opacity:0; transform:translateX(1rem); } }
            `;
            document.head.appendChild(style);
        }

        container.appendChild(toast);

        function dismiss() {
            toast.style.animation = 'ddukToastOut 0.2s ease forwards';
            setTimeout(() => toast.remove(), 220);
        }

        if (durationMs > 0) {
            setTimeout(dismiss, durationMs);
        }
        toast.addEventListener('click', dismiss);

        return { dismiss };
    }

    // ── 세션 만료 처리 ────────────────────────────────────────────
    let _401Handled = false;

    function handle401() {
        if (_401Handled) return;
        _401Handled = true;

        localStorage.clear();
        sessionStorage.clear();

        showToast('로그인 세션이 만료되었습니다. 잠시 후 로그인 페이지로 이동합니다.', 'error', 0);

        setTimeout(() => {
            const path = window.location.pathname.replace(/\\/g, '/');
            let loginUrl = 'index.html';
            if (path.includes('/pages/')) {
                const depth = path.split('/pages/')[1].split('/').length;
                loginUrl = '../'.repeat(depth) + 'index.html';
            }
            window.location.href = loginUrl;
        }, 2200);
    }

    // ── UI 상태 헬퍼 (loading / empty / error) ───────────────────
    /**
     * 컨테이너 요소에 상태 오버레이를 표시합니다.
     * @param {HTMLElement} container    표시할 대상 요소
     * @param {'loading'|'empty'|'error'|'unauthorized'|'network'} state  상태
     * @param {string} [message]  추가 메시지 (선택)
     */
    function setUiState(container, state, message) {
        const stateConfig = {
            loading: {
                icon: '<svg style="width:2.5rem;height:2.5rem;animation:ddukSpin 1s linear infinite" viewBox="0 0 24 24" fill="none" stroke="#6366f1" stroke-width="2"><path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"/></svg>',
                title: '데이터를 불러오는 중...',
                sub: '잠시만 기다려 주세요.',
                color: '#6366f1'
            },
            empty: {
                icon: '📭',
                title: '데이터가 없습니다',
                sub: message || '필터 조건을 변경하거나 데이터를 등록해 주세요.',
                color: '#9ca3af'
            },
            error: {
                icon: '🔥',
                title: '서버 오류가 발생했습니다',
                sub: message || '서버 측 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.',
                color: '#ef4444'
            },
            unauthorized: {
                icon: '🔒',
                title: '로그인이 필요합니다',
                sub: '세션이 만료되었거나 권한이 없습니다. 다시 로그인해 주세요.',
                color: '#f59e0b'
            },
            network: {
                icon: '📡',
                title: '네트워크 오류',
                sub: message || '서버에 연결할 수 없습니다. 네트워크 상태를 확인해 주세요.',
                color: '#6b7280'
            }
        };

        const cfg = stateConfig[state] || stateConfig.error;

        // 스핀 키프레임
        if (!document.getElementById('dduk-spin-style')) {
            const style = document.createElement('style');
            style.id = 'dduk-spin-style';
            style.textContent = '@keyframes ddukSpin { to { transform: rotate(360deg); } }';
            document.head.appendChild(style);
        }

        container.innerHTML = `
            <div style="display:flex;flex-direction:column;align-items:center;justify-content:center;padding:4rem 2rem;gap:1rem;text-align:center;">
                <div style="font-size:2.5rem;line-height:1">${cfg.icon}</div>
                <p style="font-size:0.95rem;font-weight:700;color:${cfg.color};margin:0">${cfg.title}</p>
                <p style="font-size:0.8rem;color:#9ca3af;margin:0;max-width:22rem">${cfg.sub}</p>
            </div>
        `;
    }

    // ── 핵심 fetch 래퍼 ──────────────────────────────────────────
    function buildUrl(url) {
        if (url.startsWith('/api/') && API_BASE_URL) {
            return `${API_BASE_URL}${url}`;
        }
        if (!url.startsWith('http') && !url.startsWith('/api/') && API_BASE_URL) {
            return `${API_BASE_URL}/api/v1/${url}`;
        }
        return url;
    }

    function getAuthHeaders(extraHeaders = {}) {
        const token = localStorage.getItem('token');
        const headers = { ...extraHeaders };
        if (token) {
            headers.Authorization = `Bearer ${token}`;
        }
        return headers;
    }

    function normalizeOptions(options = {}) {
        const headers = getAuthHeaders(options.headers || {});
        const config = {
            ...options,
            headers
        };

        if (
            config.body &&
            typeof config.body === 'object' &&
            !(config.body instanceof FormData) &&
            !(config.body instanceof Blob) &&
            !headers['Content-Type']
        ) {
            headers['Content-Type'] = 'application/json';
        }

        if (
            config.body &&
            headers['Content-Type'] === 'application/json' &&
            typeof config.body !== 'string' &&
            !(config.body instanceof FormData) &&
            !(config.body instanceof Blob)
        ) {
            config.body = JSON.stringify(config.body);
        }

        return config;
    }

    function unwrapPayload(payload) {
        return payload && typeof payload === 'object' && 'data' in payload ? payload.data : payload;
    }

    async function request(url, options = {}) {
        const config = normalizeOptions(options);
        const fullUrl = buildUrl(url);

        let response;
        try {
            response = await fetch(fullUrl, config);
        } catch (networkErr) {
            const err = new Error('NETWORK_ERROR: ' + (networkErr.message || '서버에 연결할 수 없습니다.'));
            err.type = 'network';
            throw err;
        }

        if (response.status === 401) {
            handle401();
            const err = new Error('UNAUTHORIZED');
            err.type = 'unauthorized';
            err.status = 401;
            throw err;
        }

        if (response.status === 403) {
            showToast('해당 작업을 수행할 권한이 없습니다.', 'error');
            const err = new Error('FORBIDDEN');
            err.type = 'forbidden';
            err.status = 403;
            throw err;
        }

        // 바이너리 스트림 예외
        const contentType = response.headers.get('content-type');
        if (contentType && (contentType.includes('octet-stream') || contentType.includes('csv'))) {
            return response;
        }

        let payload;
        try {
            payload = await response.json();
        } catch (parseErr) {
            const err = new Error('JSON_PARSE_ERROR');
            err.type = 'server';
            err.status = response.status;
            throw err;
        }

        if (!response.ok || payload.status === 'error') {
            const err = new Error(payload.message || 'API 요청 수행 중 오류가 발생했습니다.');
            err.type = 'server';
            err.status = response.status;
            throw err;
        }

        return payload;
    }

    // ── public API ────────────────────────────────────────────────
    async function requestData(url, options = {}) {
        const payload = await request(url, options);
        return unwrapPayload(payload);
    }

    async function requestList(url, options = {}) {
        const payload = await requestData(url, options);
        return Array.isArray(payload) ? payload : [];
    }

    window.ddukApi = {
        get: (url, options) => request(url, { ...options, method: 'GET' }),
        post: (url, body, options) => request(url, { ...options, method: 'POST', body }),
        put: (url, body, options) => request(url, { ...options, method: 'PUT', body }),
        patch: (url, body, options) => request(url, { ...options, method: 'PATCH', body }),
        delete: (url, options) => request(url, { ...options, method: 'DELETE' }),
        request,
        requestData,
        requestList,
        buildUrl,
        getAuthHeaders,
        unwrapData: unwrapPayload,
        getBaseUrl: () => API_BASE_URL,
        showToast,
        setUiState
    };
})();
