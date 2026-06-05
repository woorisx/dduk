(function () {
    const RECENT_MENU_KEY = 'dduk_dashboard_recent_menu';
    const RECENT_MENU_TTL = 3 * 24 * 60 * 60 * 1000;
    const EXPANDED_MENU_KEY = 'dduk_sidebar_expanded_menus';
    const SIDEBAR_SCROLL_KEY = 'dduk_sidebar_scroll_top';

    const MENU_GROUPS = [
        {
            id: 'purchase',
            label: '구매/발주',
            items: [
                { label: '구매/발주 대시보드', icon: 'bar-chart-3', href: 'pages/inventory/purchase-dashboard.html', roles: ['ADMIN', 'INVENTORY'] },
                { label: '구매 요청', icon: 'file-plus', href: 'pages/inventory/purchase-request.html', roles: ['ADMIN', 'INVENTORY'] },
                { label: '발주조회', icon: 'clipboard-list', href: 'pages/inventory/purchase-orders.html', roles: ['ADMIN', 'INVENTORY'] },
                { label: '발주 상태 관리', icon: 'trending-up', href: 'pages/inventory/purchase-status.html', roles: ['ADMIN', 'INVENTORY'] },
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
                { label: '회계 대시보드', icon: 'bar-chart-3', href: 'pages/hr/accounting/accounting_dashboard.html', roles: ['ADMIN', 'HR'] },
                { label: '계정과목 관리', icon: 'folder-tree', href: 'pages/hr/accounting/accounts.html', roles: ['ADMIN', 'HR'] },
                { label: '전표 관리', icon: 'receipt', href: 'pages/hr/accounting/voucher_management.html', roles: ['ADMIN', 'HR'] },
                { label: '합계잔액시산표', icon: 'trending-up', href: 'pages/hr/accounting/trial_balance.html', roles: ['ADMIN', 'HR'] },
                { label: '재무제표', icon: 'file-text', href: 'pages/hr/accounting/reports.html', roles: ['ADMIN', 'HR'] },
                { label: '회계 분석 리포트', icon: 'file-bar-chart', href: 'pages/hr/accounting/accounting_reports.html', roles: ['ADMIN', 'HR'] },
                { label: '월 마감', icon: 'calendar-check', href: 'pages/hr/accounting/monthly_closing.html', roles: ['ADMIN', 'HR'] },
                { label: '급여 계산/대장', icon: 'wallet', href: 'pages/hr/accounting/payroll_management.html', match: 'pages/hr/accounting/payroll_management.html', roles: ['ADMIN', 'HR'] },
                { label: '세금계산서', icon: 'file-check', href: 'pages/hr/accounting/tax_invoice.html', roles: ['ADMIN', 'HR'] },
                { label: '비용 처리', icon: 'credit-card', href: 'pages/hr/accounting/expenses.html', roles: ['ADMIN', 'HR'] }
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
                { label: '이상 탐지', icon: 'alert-triangle', href: '#', roles: ['ADMIN'] },
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
                { label: '조직 및 부서 관리', icon: 'network', href: 'pages/admin/org-admin.html', roles: ['ADMIN'], disabled: true },
                { label: 'AI 챗봇 테스트', icon: 'bot', href: 'pages/admin/chatbot-test.html', roles: ['ADMIN'] },
                { label: 'AI/RPA 작업 이력', icon: 'history', href: 'pages/admin/task-history.html', roles: ['ADMIN'] }
            ]
        }
    ];

    function getRootPath() {
        return window.DDUK_COMMON ? window.DDUK_COMMON.getRootPath() : './';
    }

    function resolveHref(href) {
        return window.DDUK_COMMON ? window.DDUK_COMMON.resolveHref(href) : href;
    }

    function isCurrentPage(item) {
        const path = window.location.pathname.replace(/\\/g, '/');
        if (item.match && path.includes('/' + item.match)) return true;
        if (!item.href || item.href === '#') return false;

        const cleanHref = item.href.replace(/^\//, '');
        return path.endsWith('/' + cleanHref)
            || path.includes('/' + cleanHref)
            || path.endsWith('/frontend/' + cleanHref)
            || (path.includes(cleanHref) && path.endsWith(cleanHref.split('/').pop()));
    }

    function renderMenuItem(item, extraClass) {
        const currentClass = isCurrentPage(item) ? ' active' : '';
        const rolesAttr = item.roles ? ` data-roles="${item.roles.join(',')}"` : '';
        if (item.disabled) {
            return `
                <span class="menu_item menu_item_disabled${extraClass ? ` ${extraClass}` : ''}" title="${item.label}" aria-disabled="true">
                    <i class="dduk-inline-012" data-lucide="${item.icon}"></i>
                    <span class="menu_label">${item.label}</span>
                    <span class="menu_badge_wip">준비중</span>
                </span>
            `;
        }

        return `
            <a class="menu_item${extraClass ? ` ${extraClass}` : ''}${currentClass}" href="${resolveHref(item.href)}"${rolesAttr} data-label="${item.label}" data-href-raw="${item.href}" title="${item.label}">
                <i class="dduk-inline-012" data-lucide="${item.icon}"></i>
                <span class="menu_label">${item.label}</span>
            </a>
        `;
    }

    function renderGroups() {
        return MENU_GROUPS.map((group) => `
            <div class="menu_group_title dduk-inline-014" onclick="toggleMenu('menu_${group.id}')">
                <span>${group.label}</span> <i class="dduk-inline-015" data-lucide="chevron-down" id="icon_${group.id}"></i>
            </div>
            <div class="submenu collapsed dduk-inline-016" id="menu_${group.id}">
                ${group.items.map((item) => renderMenuItem(item)).join('')}
            </div>
        `).join('');
    }

    function renderSidebar() {
        const root = getRootPath();
        const userName = localStorage.getItem('userName') || '게스트';
        const rawRole = localStorage.getItem('role') || '';
        const roleMap = {
            ADMIN: '시스템 관리자',
            HR: '인사 관리자',
            INVENTORY: '재고 관리자'
        };
        const displayRole = roleMap[rawRole] || '사용자';

        return `
            <aside class="sidebar" id="sidebar">
                <div class="dduk-inline-001">
                    <div class="dduk-inline-002">
                        <a class="sidebar_logo_link" href="${root}dashboard.html" aria-label="대시보드로 이동">
                            <h1 data-template-id="logo-text" class="canva-text dduk-inline-003">
                                <img src="${root}assets/logo.png" alt="LOGO">
                            </h1>
                        </a>
                        <button class="sidebar_icon_btn" onclick="toggleSidebar()" aria-label="사이드바 접기">
                            <i class="dduk-inline-004" data-lucide="panel-left-close"></i>
                        </button>
                    </div>
                    <div class="dduk-inline-005">
                        <span data-template-id="workspace-name" class="canva-text dduk-inline-006">(주)아망티</span>
                        <i class="dduk-inline-007" data-lucide="chevron-down"></i>
                    </div>
                    <div class="dduk-inline-008">
                        <a class="sidebar_icon_btn" aria-label="AI" href="#" id="quickBtnAI"><i class="dduk-inline-009" data-lucide="bot"></i></a>
                        <a class="sidebar_icon_btn" aria-label="OCR" href="${resolveHref('pages/ocr/ocr-box.html')}"><i class="dduk-inline-009" data-lucide="scan"></i></a>
                        <a class="sidebar_icon_btn sidebar_icon_btn_disabled" aria-label="승인" href="#" onclick="return false;"><i class="dduk-inline-009" data-lucide="check-circle"></i></a>
                        <a class="sidebar_icon_btn sidebar_icon_btn_disabled" aria-label="설정" href="#" onclick="return false;"><i class="dduk-inline-009" data-lucide="settings"></i></a>
                    </div>
                    <div class="recent_group dduk-inline-010">
                        <button class="recent_header dduk-inline-011" type="button" onclick="toggleRecentMenu()" aria-expanded="true">
                            <span>최근 사용</span><i class="dduk-inline-015" data-lucide="chevron-up" id="icon_recent"></i>
                        </button>
                        <div class="recent_items" id="recent_menu_items"></div>
                    </div>
                </div>
                <nav class="dduk-inline-013">
                    ${renderMenuItem({ label: '대시보드', icon: 'layout-dashboard', href: 'dashboard.html', roles: ['ADMIN', 'HR', 'INVENTORY'] })}
                    ${renderGroups()}
                </nav>
                <div class="dduk-inline-019">
                    <div class="dduk-inline-020">
                        <div class="dduk-inline-021"><i class="dduk-inline-022" data-lucide="user"></i></div>
                        <div class="sidebar_user_text">
                            <p class="dduk-inline-023">${userName}</p>
                            <p class="dduk-inline-024">${displayRole}</p>
                        </div>
                    </div>
                    <div class="dduk-inline-025">
                        <span class="status_dot dduk-inline-026"></span>
                        <span class="dduk-inline-024 sidebar_status_text">서버 정상 · 99.9% uptime</span>
                    </div>
                    <div style="margin-top: 1rem; border-top: 1px solid rgba(0,0,0,0.05); padding-top: 0.75rem;">
                        <button onclick="handleLogout()" style="display: flex; align-items: center; gap: 0.5rem; font-size: 0.875rem; color: #6b7280; width: 100%; padding: 0.25rem 0; background: none; border: none; cursor: pointer;">
                            <i data-lucide="log-out" style="width: 1rem; height: 1rem;"></i>
                            <span>로그아웃</span>
                        </button>
                    </div>
                </div>
            </aside>
        `;
    }

    function updateMenuIcon(id, isOpen) {
        const icon = document.getElementById('icon_' + id.replace('menu_', ''));
        if (!icon) return;
        icon.setAttribute('data-lucide', isOpen ? 'chevron-up' : 'chevron-down');
        if (window.lucide) window.lucide.createIcons();
    }

    function readExpandedMenus() {
        try {
            const parsed = JSON.parse(localStorage.getItem(EXPANDED_MENU_KEY) || '[]');
            if (!Array.isArray(parsed)) return [];
            return parsed.filter((id) => typeof id === 'string' && id.startsWith('menu_'));
        } catch (error) {
            localStorage.removeItem(EXPANDED_MENU_KEY);
            return [];
        }
    }

    function writeExpandedMenus(menuIds) {
        const uniqueIds = Array.from(new Set(menuIds.filter((id) => typeof id === 'string' && id.startsWith('menu_'))));
        localStorage.setItem(EXPANDED_MENU_KEY, JSON.stringify(uniqueIds));
    }

    function syncExpandedMenusFromDom() {
        const expandedMenus = Array.from(document.querySelectorAll('.submenu'))
            .filter((submenu) => !submenu.classList.contains('collapsed'))
            .map((submenu) => submenu.id);
        writeExpandedMenus(expandedMenus);
    }

    function readSidebarScrollTop() {
        const rawValue = sessionStorage.getItem(SIDEBAR_SCROLL_KEY);
        if (!rawValue) return 0;

        const parsed = Number(rawValue);
        return Number.isFinite(parsed) && parsed >= 0 ? parsed : 0;
    }

    function writeSidebarScrollTop(value) {
        sessionStorage.setItem(SIDEBAR_SCROLL_KEY, String(Math.max(0, Math.floor(value || 0))));
    }

    function restoreSidebarScrollTop(sidebar) {
        if (!sidebar) return;
        window.requestAnimationFrame(() => {
            sidebar.scrollTop = readSidebarScrollTop();
        });
    }

    function bindSidebarScrollPersistence(sidebar) {
        if (!sidebar) return;

        const persistScroll = () => writeSidebarScrollTop(sidebar.scrollTop);
        sidebar.addEventListener('scroll', persistScroll, { passive: true });
        window.addEventListener('beforeunload', persistScroll);
    }

    function toggleMenu(id) {
        if (document.body.classList.contains('sidebar-collapsed')) return;

        const el = document.getElementById(id);
        if (!el) return;

        const shouldOpen = el.classList.contains('collapsed');
        el.classList.toggle('collapsed', !shouldOpen);
        updateMenuIcon(id, shouldOpen);
        syncExpandedMenusFromDom();
    }

    function toggleRecentMenu() {
        const items = document.getElementById('recent_menu_items');
        const icon = document.getElementById('icon_recent');
        const header = document.querySelector('.recent_header');
        if (!items || !icon) return;

        const isCollapsed = items.classList.toggle('collapsed');
        icon.setAttribute('data-lucide', isCollapsed ? 'chevron-down' : 'chevron-up');
        if (header) header.setAttribute('aria-expanded', String(!isCollapsed));
        if (window.lucide) window.lucide.createIcons();
    }

    function toggleSidebar() {
        const sidebar = document.getElementById('sidebar');
        if (!sidebar) return;

        if (window.innerWidth <= 1024) {
            sidebar.classList.toggle('open');
            return;
        }

        const willCollapse = !document.body.classList.contains('sidebar-collapsed');
        document.body.classList.toggle('sidebar-collapsed', willCollapse);
        localStorage.setItem('dduk_sidebar_collapsed', String(willCollapse));

        if (willCollapse) {
            document.querySelectorAll('.submenu').forEach((submenu) => {
                submenu.classList.add('collapsed');
                updateMenuIcon(submenu.id, false);
            });
        } else {
            restoreExpandedMenus();
            openCurrentMenuGroup();
            syncExpandedMenusFromDom();
        }

        const icon = sidebar.querySelector('[data-lucide="panel-left-close"], [data-lucide="panel-left-open"]');
        if (icon) {
            icon.setAttribute('data-lucide', willCollapse ? 'panel-left-open' : 'panel-left-close');
        }
        if (window.lucide) window.lucide.createIcons();
    }

    function getMenuLabel(menuItem) {
        const label = menuItem.querySelector('.menu_label');
        return label ? label.textContent.trim() : '';
    }

    function readRecentMenus() {
        const now = Date.now();
        try {
            return JSON.parse(localStorage.getItem(RECENT_MENU_KEY) || '[]')
                .filter((item) => item.expiresAt > now)
                .sort((a, b) => b.usedAt - a.usedAt)
                .slice(0, 5);
        } catch (error) {
            localStorage.removeItem(RECENT_MENU_KEY);
            return [];
        }
    }

    function writeRecentMenus(items) {
        localStorage.setItem(RECENT_MENU_KEY, JSON.stringify(items));
    }

    function addRecentMenu(menuItem) {
        if (menuItem.closest('.recent_group')) return;

        const label = getMenuLabel(menuItem);
        if (!label) return;

        const hrefRaw = menuItem.getAttribute('data-href-raw') || '';
        const icon = menuItem.querySelector('[data-lucide]');
        const iconName = icon ? icon.getAttribute('data-lucide') : 'circle';
        const now = Date.now();
        const items = readRecentMenus().filter((item) => item.label !== label);
        items.unshift({
            label,
            iconName,
            hrefRaw,
            usedAt: now,
            expiresAt: now + RECENT_MENU_TTL
        });
        writeRecentMenus(items.slice(0, 5));
        renderRecentMenus();
    }

    function findHrefByLabel(label) {
        if (label === '대시보드') return 'dashboard.html';
        for (const group of MENU_GROUPS) {
            for (const item of group.items) {
                if (item.label === label) return item.href;
            }
        }
        return '#';
    }

    function renderRecentMenus() {
        const container = document.getElementById('recent_menu_items');
        if (!container) return;

        const items = readRecentMenus();
        if (items.length === 0) {
            container.innerHTML = '<div class="recent_empty">최근 사용한 메뉴가 없습니다.</div>';
            return;
        }

        container.innerHTML = items.map((item) => {
            const href = item.hrefRaw || findHrefByLabel(item.label);
            return `
                <a class="menu_item recent_menu_item" href="${resolveHref(href)}" data-label="${item.label}" data-href-raw="${href}" title="${item.label}">
                    <i class="dduk-inline-012" data-lucide="${item.iconName}"></i>
                    <span class="menu_label">${item.label}</span>
                </a>
            `;
        }).join('');
        if (window.lucide) window.lucide.createIcons();
    }

    function checkMobile() {
        const btn = document.getElementById('mobile_menu_btn');
        if (btn) btn.style.display = 'flex';

        if (window.innerWidth <= 1024) {
            document.body.classList.remove('sidebar-collapsed');
        }
    }

    function openCurrentMenuGroup() {
        const current = document.querySelector('.menu_item.active');
        if (!current) return;

        const expandedMenus = new Set(readExpandedMenus());
        let parent = current.parentElement;
        while (parent) {
            if (parent.classList.contains('submenu')) {
                parent.classList.remove('collapsed');
                updateMenuIcon(parent.id, true);
                expandedMenus.add(parent.id);
            }
            parent = parent.parentElement;
        }
        writeExpandedMenus(Array.from(expandedMenus));
    }

    function restoreExpandedMenus() {
        const expandedMenus = readExpandedMenus();
        document.querySelectorAll('.submenu').forEach((submenu) => {
            const isExpanded = expandedMenus.includes(submenu.id);
            submenu.classList.toggle('collapsed', !isExpanded);
            updateMenuIcon(submenu.id, isExpanded);
        });
    }

    function initSidebar() {
        const host = document.querySelector('[data-dduk-sidebar]');
        if (!host) return;

        const isCollapsed = localStorage.getItem('dduk_sidebar_collapsed') === 'true';
        if (isCollapsed && window.innerWidth > 1024) {
            document.body.classList.add('sidebar-collapsed');
        } else {
            document.body.classList.remove('sidebar-collapsed');
        }

        host.outerHTML = renderSidebar();

        const sidebar = document.getElementById('sidebar');
        if (sidebar && isCollapsed && window.innerWidth > 1024) {
            const icon = sidebar.querySelector('[data-lucide="panel-left-close"], [data-lucide="panel-left-open"]');
            if (icon) {
                icon.setAttribute('data-lucide', 'panel-left-open');
            }
        }

        restoreSidebarScrollTop(sidebar);
        bindSidebarScrollPersistence(sidebar);

        restoreExpandedMenus();

        const currentUserRole = localStorage.getItem('role') || '';

        document.querySelectorAll('.menu_item').forEach((menuItem) => {
            menuItem.addEventListener('click', (e) => {
                const rolesData = menuItem.getAttribute('data-roles');
                if (rolesData) {
                    const allowedRoles = rolesData.split(',');
                    if (!allowedRoles.includes(currentUserRole)) {
                        e.preventDefault();
                        e.stopPropagation();
                        window.ddukApi?.showToast?.('해당 메뉴에 접근할 권한이 없습니다.', 'warning');
                        return;
                    }
                }
                addRecentMenu(menuItem);
            });
        });

        renderRecentMenus();

        if (!document.body.classList.contains('sidebar-collapsed')) {
            openCurrentMenuGroup();
        }

        syncExpandedMenusFromDom();

        checkMobile();
        window.addEventListener('resize', checkMobile);
        if (window.lucide) window.lucide.createIcons();

        const quickAI = document.getElementById('quickBtnAI');
        if (quickAI) {
            quickAI.addEventListener('click', (e) => {
                e.preventDefault();
                if (typeof window.toggleFloatingChatbot === 'function') {
                    window.toggleFloatingChatbot(true);
                }
                if (typeof window.switchPortalTab === 'function') {
                    window.switchPortalTab('chatbot');
                }
            });
        }
    }

    window.DDUK_SIDEBAR_MENU = {
        initSidebar,
        toggleMenu,
        toggleRecentMenu,
        toggleSidebar,
        renderRecentMenus
    };
})();
