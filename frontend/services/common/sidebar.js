(function () {
    const RECENT_MENU_KEY = 'dduk_dashboard_recent_menu';
    const RECENT_MENU_TTL = 3 * 24 * 60 * 60 * 1000;

    const MENU_GROUPS = [
        {
            id: 'purchase',
            label: '구매/발주',
            items: [
                { label: '구매/발주 대시보드', icon: 'bar-chart-3', href: 'pages/inventory/purchase-dashboard.html', roles: ['ADMIN', 'INVENTORY'] },
                { label: '구매 요청', icon: 'file-plus', href: 'pages/inventory/purchase-request.html', roles: ['ADMIN', 'INVENTORY'] },
                { label: '발주 관리', icon: 'clipboard-list', href: 'pages/inventory/purchase-orders.html', roles: ['ADMIN', 'INVENTORY'] },
                { label: '발주 현황', icon: 'trending-up', href: 'pages/inventory/purchase-status.html', roles: ['ADMIN', 'INVENTORY'] },
                { label: '입고 등록', icon: 'package-check', href: 'pages/inventory/receiving.html', roles: ['ADMIN', 'INVENTORY'] },
                { label: '거래처 관리', icon: 'building', href: 'pages/inventory/vendors.html', roles: ['ADMIN', 'INVENTORY'] }
            ]
        },
        {
            id: 'inventory',
            label: '재고관리',
            items: [
                { label: '재고관리 대시보드', icon: 'bar-chart-3', href: 'pages/inventory/dashboard.html', roles: ['ADMIN', 'INVENTORY'] },
                { label: '재고 조회', icon: 'search', href: 'pages/inventory/list.html', roles: ['ADMIN', 'INVENTORY'] },
                { label: '입출고 이력', icon: 'history', href: 'pages/inventory/movements.html', roles: ['ADMIN', 'INVENTORY'] },
                { label: '창고 이동', icon: 'truck', href: 'pages/inventory/transfers.html', roles: ['ADMIN', 'INVENTORY'] },
                { label: '자동 발주 추천', icon: 'zap', href: 'pages/inventory/reorder.html', roles: ['ADMIN', 'INVENTORY'] }
            ]
        },
        {
            id: 'accounting',
            label: '회계관리',
            items: [
                { label: '회계 대시보드',    icon: 'bar-chart-3',    href: 'pages/hr/accounting/accounting_dashboard.html', roles: ['ADMIN', 'HR'] },
                { label: '계정과목 관리',    icon: 'folder-tree',    href: 'pages/hr/accounting/accounts.html', roles: ['ADMIN', 'HR'] },
                { label: '전표 관리',        icon: 'receipt',        href: 'pages/hr/accounting/voucher_management.html', roles: ['ADMIN', 'HR'] },
                { label: '합계잔액시산표',   icon: 'trending-up',    href: 'pages/hr/accounting/trial_balance.html', roles: ['ADMIN', 'HR'] },
                { label: '재무제표',         icon: 'file-text',      href: 'pages/hr/accounting/reports.html', roles: ['ADMIN', 'HR'] },
                { label: '회계 분석 리포트', icon: 'file-bar-chart', href: 'pages/hr/accounting/accounting_reports.html', roles: ['ADMIN', 'HR'] },
                { label: '월 마감',          icon: 'calendar-check', href: 'pages/hr/accounting/monthly_closing.html', roles: ['ADMIN', 'HR'] },
                { label: '급여 관리',          icon: 'wallet',         href: 'pages/hr/accounting/payroll_management.html', match: 'pages/hr/accounting/payroll_management.html', roles: ['ADMIN', 'HR'] },
                { label: '세금계산서',       icon: 'file-check',     href: 'pages/hr/accounting/wip.html', disabled: true, roles: ['ADMIN', 'HR'] },
                { label: '비용 처리',        icon: 'credit-card',    href: 'pages/hr/accounting/wip.html', disabled: true, roles: ['ADMIN', 'HR'] }
            ]
        },
        {
            id: 'docs',
            label: '문서/증빙',
            items: [
                { label: '증빙 업로드', icon: 'upload', href: 'pages/ocr/upload.html', roles: ['ADMIN', 'HR', 'INVENTORY'] },
                { label: 'OCR 문서함', icon: 'scan', href: 'pages/ocr/ocr-box.html', roles: ['ADMIN', 'HR', 'INVENTORY'] },
                { label: '계약 문서', icon: 'file-signature', href: '#', roles: ['ADMIN'] }
            ]
        },
        {
            id: 'ai',
            label: 'AI 업무지원',
            items: [
                { label: 'AI 챗봇', icon: 'bot', href: '#', roles: ['ADMIN', 'HR', 'INVENTORY'] },
                { label: '이상 탐지', icon: 'alert-triangle', href: 'pages/admin/anomaly-detection.html', roles: ['ADMIN'] },
                { label: '예측 분석', icon: 'brain', href: '#', roles: ['ADMIN', 'HR', 'INVENTORY'] }
            ]
        },
        {
            id: 'admin',
            label: '관리자',
            items: [
                { label: '계정 및 권한 관리', icon: 'shield-check', href: 'pages/admin/account-security.html', roles: ['ADMIN'] },
                { label: '시스템 운영 관리', icon: 'settings-2', href: 'pages/admin/system-admin.html', roles: ['ADMIN'] },
                { label: '공지사항 관리', icon: 'megaphone', href: 'pages/admin/notice-admin.html', roles: ['ADMIN'] },
                { label: '조직 및 부서 관리', icon: 'network', href: 'pages/admin/org-admin.html', roles: ['ADMIN'] },
                { label: 'AI 챗봇 테스트', icon: 'bot', href: 'pages/admin/chatbot-test.html', roles: ['ADMIN'] },
                { label: 'AI/RPA 작업 이력', icon: 'history', href: 'pages/admin/task-history.html', roles: ['ADMIN'] }
            ]
        }
    ];

    // 1. 공용 유틸리티 함수 보관 및 노출
    function getRootPath() {
        const path = window.location.pathname.replace(/\\/g, '/');
        const frontendIndex = path.lastIndexOf('/frontend/');

        if (frontendIndex === -1) {
            const pagesIndex = path.lastIndexOf('/pages/');
            if (pagesIndex === -1) return './';
            const depth = path.slice(pagesIndex + '/pages/'.length).split('/').length;
            return '../'.repeat(depth);
        }

        const relativePath = path.slice(frontendIndex + '/frontend/'.length);
        const depth = Math.max(0, relativePath.split('/').length - 1);
        return depth === 0 ? './' : '../'.repeat(depth);
    }

    function resolveHref(href) {
        if (!href || href === '#' || /^(https?:|mailto:|tel:)/.test(href)) {
            return href || '#';
        }
        return getRootPath() + href;
    }

    function escapeHtml(value) {
        return String(value ?? '').replace(/[&<>"']/g, (char) => ({
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#039;'
        }[char]));
    }

    function formatDateTime(value) {
        if (!value) return '-';
        try {
            const date = new Date(value);
            if (isNaN(date.getTime())) return value;
            const y = date.getFullYear();
            const m = String(date.getMonth() + 1).padStart(2, '0');
            const d = String(date.getDate()).padStart(2, '0');
            const hh = String(date.getHours()).padStart(2, '0');
            const mm = String(date.getMinutes()).padStart(2, '0');
            return `${y}-${m}-${d} ${hh}:${mm}`;
        } catch (e) {
            return value;
        }
    }

    function getApiBaseUrl() {
        return window.ddukSession && typeof window.ddukSession.getApiBaseUrl === 'function'
            ? window.ddukSession.getApiBaseUrl()
            : '';
    }

    function getHeaders(extraHeaders) {
        if (window.ddukSession && typeof window.ddukSession.getAuthHeaders === 'function') {
            return window.ddukSession.getAuthHeaders({
                'Content-Type': 'application/json',
                ...(extraHeaders || {})
            });
        }
        return {
            'Content-Type': 'application/json',
            ...(extraHeaders || {})
        };
    }

    window.DDUK_COMMON = {
        getRootPath,
        resolveHref,
        escapeHtml,
        formatDateTime,
        getApiBaseUrl,
        getHeaders
    };

    // 2. 기존 전역 계약(브릿지 함수) 유지
    window.toggleMenu = function (id) {
        if (window.DDUK_SIDEBAR_MENU && typeof window.DDUK_SIDEBAR_MENU.toggleMenu === 'function') {
            window.DDUK_SIDEBAR_MENU.toggleMenu(id);
        }
    };

    window.toggleRecentMenu = function () {
        if (window.DDUK_SIDEBAR_MENU && typeof window.DDUK_SIDEBAR_MENU.toggleRecentMenu === 'function') {
            window.DDUK_SIDEBAR_MENU.toggleRecentMenu();
        }
    };

    window.toggleSidebar = function () {
        if (window.DDUK_SIDEBAR_MENU && typeof window.DDUK_SIDEBAR_MENU.toggleSidebar === 'function') {
            window.DDUK_SIDEBAR_MENU.toggleSidebar();
        }
    };

    window.toggleFloatingChatbot = function (forceOpen) {
        if (window.DDUK_FLOATING_PORTAL && typeof window.DDUK_FLOATING_PORTAL.toggleFloatingChatbot === 'function') {
            window.DDUK_FLOATING_PORTAL.toggleFloatingChatbot(forceOpen);
        }
    };

    window.openFloatingChatbotWithQuery = function (query) {
        if (window.DDUK_FLOATING_PORTAL && typeof window.DDUK_FLOATING_PORTAL.openFloatingChatbotWithQuery === 'function') {
            window.DDUK_FLOATING_PORTAL.openFloatingChatbotWithQuery(query);
        }
    };

    window.switchPortalTab = function (tabName) {
        if (window.DDUK_FLOATING_PORTAL && typeof window.DDUK_FLOATING_PORTAL.switchPortalTab === 'function') {
            window.DDUK_FLOATING_PORTAL.switchPortalTab(tabName);
        }
    };

    window.renderDynamicRpaWidget = function () {
        if (window.DDUK_RPA_WIDGET && typeof window.DDUK_RPA_WIDGET.renderDynamicRpaWidget === 'function') {
            window.DDUK_RPA_WIDGET.renderDynamicRpaWidget();
        }
    };

    window.handleLogout = function () {
        localStorage.clear();
        sessionStorage.clear();
        const root = getRootPath();
        window.location.href = root + 'index.html';
    };

    // 3. 동적 스크립트 로더 및 초기화
    function loadScript(src) {
        return new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = src;
            script.onload = resolve;
            script.onerror = reject;
            document.head.appendChild(script);
        });
    }

    const root = getRootPath();
    const commonPath = root + 'services/common/';
    const cacheBuster = '20260604-sidebar-v3';
    const withVersion = (file) => `${commonPath}${file}?v=${cacheBuster}`;

    const scripts = [];
    if (!window.ddukSession) {
        scripts.push(withVersion('session.js'));
    }
    scripts.push(withVersion('sidebar-menu.js'));
    scripts.push(withVersion('floating-rpa-widget.js'));
    scripts.push(withVersion('floating-portal.js'));

    function loadAllScripts() {
        return scripts.reduce((promise, src) => {
            return promise.then(() => loadScript(src));
        }, Promise.resolve());
    }

    function init() {
        loadAllScripts()
            .then(() => {
                if (window.DDUK_SIDEBAR_MENU && typeof window.DDUK_SIDEBAR_MENU.initSidebar === 'function') {
                    window.DDUK_SIDEBAR_MENU.initSidebar();
                }
                if (window.DDUK_FLOATING_PORTAL && typeof window.DDUK_FLOATING_PORTAL.initAICopilotPortal === 'function') {
                    window.DDUK_FLOATING_PORTAL.initAICopilotPortal();
                }
            })
            .catch((err) => {
                console.error('사이드바 서비스 스크립트 로드 중 오류 발생:', err);
            });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
