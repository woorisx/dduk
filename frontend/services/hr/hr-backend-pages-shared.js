export const LAST_PAYROLL_KEY = 'dduk_last_backend_payroll';

let currentSession = null;

export function money(value) {
    const number = Number(value || 0);
    return new Intl.NumberFormat('ko-KR', {
        style: 'currency',
        currency: 'KRW',
        maximumFractionDigits: 0
    }).format(number);
}

export function byId(id) {
    return document.getElementById(id);
}

export function setText(id, value) {
    const element = byId(id);
    if (element) {
        element.textContent = value;
    }
}

export function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, (char) => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
    }[char]));
}

export function setStatus(id, message, type = '') {
    const element = byId(id);
    if (!element) {
        return;
    }
    element.textContent = message;
    element.className = `hr_status ${type}`.trim();
}

export function showJson(id, data) {
    const element = byId(id);
    if (element) {
        element.textContent = JSON.stringify(data, null, 2);
    }
}

export function unwrapData(response) {
    return response?.data ?? response;
}

export function getCurrentSession() {
    return currentSession;
}

export function getCurrentLoginId(options = {}) {
    const allowSystem = options.allowSystem === true;
    const loginId = currentSession?.loginId || window.ddukSession?.getSession?.().loginId;

    if (loginId) {
        return loginId;
    }

    if (allowSystem) {
        return 'SYSTEM';
    }

    throw new Error('로그인 정보가 없어 요청자를 확인할 수 없어.');
}

export async function requestTaskHistory(path) {
    if (window.ddukApi?.requestData) {
        return window.ddukApi.requestData(path, {
            method: 'GET',
            cache: 'no-store'
        });
    }

    if (window.ddukApi) {
        return window.ddukApi.get(path);
    }

    const baseUrl = window.ddukSession?.getApiBaseUrl?.() || '';
    const headers = window.ddukSession?.getAuthHeaders?.() || {};
    const response = await fetch(`${baseUrl}${path}`, {
        headers,
        cache: 'no-store'
    });

    const text = await response.text();
    const payload = text ? JSON.parse(text) : null;
    if (!response.ok) {
        throw new Error(payload?.message || `작업 이력을 불러오지 못했어. (${response.status})`);
    }
    return payload;
}

export function initShell() {
    const session = window.ddukSession?.requireRole?.(['ADMIN', 'HR', 'FINANCE']);
    if (!session) {
        return null;
    }

    window.ddukSession?.bindShell?.(session);
    window.ddukAppShell?.hydratePage?.({});
    currentSession = session;
    return session;
}

export async function safeRun(statusId, task) {
    try {
        setStatus(statusId, '백엔드 API를 호출하는 중입니다.');
        await task();
        setStatus(statusId, '완료했어.', 'ok');
    } catch (error) {
        setStatus(statusId, error.message || '요청 처리 중 오류가 발생했어.', 'error');
    }
}

export function showToast(message, type = 'info') {
    const container = byId('toast_container');
    if (!container) {
        return;
    }

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.textContent = message;
    container.appendChild(toast);

    setTimeout(() => toast.remove(), 3000);
}

export function wireRefresh(buttonId, handler) {
    const button = byId(buttonId);
    if (button) {
        button.addEventListener('click', handler);
    }
}
