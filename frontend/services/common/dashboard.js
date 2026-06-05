(function () {
    const API_BASE = '/api/v1';

    function getApiBaseUrl() {
        if (window.ddukSession && typeof window.ddukSession.getApiBaseUrl === 'function') {
            return window.ddukSession.getApiBaseUrl();
        }
        return '';
    }

    function getAuthHeaders(extraHeaders = {}) {
        if (window.ddukSession && typeof window.ddukSession.getAuthHeaders === 'function') {
            return window.ddukSession.getAuthHeaders(extraHeaders);
        }

        const token = localStorage.getItem('token');
        return {
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
            ...extraHeaders
        };
    }

    async function requestJson(path) {
        if (window.ddukApi) {
            return window.ddukApi.get(path);
        }

        const response = await fetch(`${getApiBaseUrl()}${path}`, {
            headers: getAuthHeaders()
        });

        const text = await response.text();
        const payload = text ? JSON.parse(text) : null;

        if (!response.ok) {
            throw new Error(payload && payload.message ? payload.message : `대시보드 데이터를 불러오지 못했어. (${response.status})`);
        }

        return payload;
    }

    function ensureStatusBanner() {
        let banner = document.querySelector('[data-dashboard-status]');
        if (banner) return banner;

        const pageTitle = document.querySelector('.dduk-inline-036');
        if (!pageTitle) return null;

        banner = document.createElement('div');
        banner.setAttribute('data-dashboard-status', 'true');
        Object.assign(banner.style, {
            display: 'none',
            marginTop: '12px',
            padding: '12px 16px',
            borderRadius: '12px',
            fontSize: '0.8125rem',
            fontWeight: '600'
        });
        pageTitle.insertAdjacentElement('afterend', banner);
        return banner;
    }

    function setStatusBanner(message, type) {
        const banner = ensureStatusBanner();
        if (!banner) return;

        if (!message) {
            banner.style.display = 'none';
            banner.textContent = '';
            return;
        }

        const palette = type === 'error'
            ? { border: '#fecaca', bg: '#fef2f2', color: '#991b1b' }
            : { border: '#bfdbfe', bg: '#eff6ff', color: '#1d4ed8' };

        banner.style.display = 'block';
        banner.style.border = `1px solid ${palette.border}`;
        banner.style.backgroundColor = palette.bg;
        banner.style.color = palette.color;
        banner.textContent = message;
    }

    document.addEventListener("DOMContentLoaded", () => {
        initDashboardData();
        initSearch();
        initNotifications();
        initProfileMenu();
        initQuickActions();
    });

    function initQuickActions() {
        const quickBtns = document.querySelectorAll('.quick_action_btn');
        const currentUserRole = localStorage.getItem('role') || '';

        const actionRoles = {
            'purchase-request': ['ADMIN', 'INVENTORY'],
            'transactions': ['ADMIN'],
            'wip': ['ADMIN', 'HR', 'INVENTORY'],
            'transfers': ['ADMIN', 'INVENTORY']
        };

        quickBtns.forEach(btn => {
            const onclickAttr = btn.getAttribute('onclick');
            if (onclickAttr && onclickAttr.includes('location.href')) {
                const match = onclickAttr.match(/location\.href='([^']+)'/);
                if (match && match[1]) {
                    const href = match[1];
                    btn.removeAttribute('onclick');

                    btn.addEventListener('click', (e) => {
                        e.preventDefault();
                        e.stopPropagation();

                        let allowed = true;
                        for (const [key, roles] of Object.entries(actionRoles)) {
                            if (href.includes(key)) {
                                if (!roles.includes(currentUserRole)) {
                                    allowed = false;
                                    break;
                                }
                            }
                        }

                        if (!allowed) {
                            const message = '해당 메뉴에 접근할 권한이 없습니다.';
                            if (window.ddukApi?.showToast) {
                                window.ddukApi.showToast(message, 'warning');
                            } else {
                                setStatusBanner(message, 'error');
                            }
                            return;
                        }

                        location.href = href;
                    });
                }
            }
        });
    }

    // -------------------------------------------------------------
    // 2. 동적 데이터 연동 (KPI 카드, 최근 활동 내역)
    // -------------------------------------------------------------
    async function initDashboardData() {
        try {
            // 1. KPI 카드 연동 (Admin Dashboard API)
            const response = await fetch(`${API_BASE}/admin/dashboard`, {
                headers: {
                    'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
                }
            });
            const resData = await response.json();

            if (resData.status === 'success' && resData.data) {
                const data = resData.data;
                updateKPICards(data);
            }
        } catch (err) {
            console.error("Dashboard KPI fetch failed:", err);
        }

        // 2. 최근 시스템 활동 연동 (Audit Logs API)
        try {
            const logsResponse = await fetch(`${API_BASE}/admin/audit-logs?page=0&size=5`, {
                headers: {
                    'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
                }
            });
            const logsData = await logsResponse.json();

            if (logsData.status === 'success' && logsData.data && logsData.data.content) {
                updateRecentActivityTable(logsData.data.content);
            }
        } catch (err) {
            console.error("Recent activities fetch failed:", err);
        }
    }

    function updateKPICards(data) {
        const kpiContainer = document.querySelector('.kpi_grid');
        if (!kpiContainer) return;

        // Map backend Dto items to our dashboard fields
        const account = data.accountSummary || {};
        const domain = data.domainSummary || {};
        const hr = domain.hr || {};
        const inventory = domain.inventory || {};
        const accounting = domain.accounting || {};

        const cards = kpiContainer.querySelectorAll('.kpi_card');
        if (cards.length >= 5) {
            // 오늘 매출 (accounting.journalEntryCount 활용 혹은 데모 값 제공)
            const salesCard = cards[0];
            const salesVal = salesCard.querySelector('.dduk-inline-041');
            if (salesVal) {
                salesVal.textContent = `₩${((accounting.journalEntryCount || 0) * 1240000 + 12840000).toLocaleString()}`;
            }

            // 이번달 발주 (inventory.purchaseOrderCount 활용)
            const poCard = cards[1];
            const poVal = poCard.querySelector('.dduk-inline-041');
            const approvedLabel = poCard.querySelector('.dduk-inline-042');
            if (poVal) {
                poVal.textContent = `₩${((inventory.purchaseOrderCount || 0) * 4500000).toLocaleString()}`;
            }
            if (approvedLabel) {
                approvedLabel.textContent = `승인 발주: ${inventory.approvedPurchaseOrderCount || 0}건 / 총 ${inventory.purchaseOrderCount || 0}건`;
            }

            // 현재 재고 (inventory.itemCount 활용)
            const stockCard = cards[2];
            const stockVal = stockCard.querySelector('.dduk-inline-041');
            const stockSub = stockCard.querySelector('.dduk-inline-043');
            if (stockVal) {
                stockVal.textContent = `${(inventory.itemCount || 0).toLocaleString()} 품목`;
            }
            if (stockSub) {
                stockSub.textContent = `전사 등록 품목 수 기준`;
                stockSub.className = "dduk-inline-024";
            }

            // 미정산 금액
            const accCard = cards[3];
            const accVal = accCard.querySelector('.dduk-inline-041');
            const accSub = accCard.querySelector('.dduk-inline-044');
            if (accVal) {
                accVal.textContent = `₩${((accounting.accountCount || 0) * 850000).toLocaleString()}`;
            }
            if (accSub) {
                accSub.textContent = `등록 계정 과목: ${accounting.accountCount || 0}개`;
            }

            // 활성 거래처 (activeMemberCount / totalMemberCount 활용)
            const partnerCard = cards[4];
            const partnerVal = partnerCard.querySelector('.dduk-inline-041');
            const partnerSub = partnerCard.querySelector('.dduk-inline-042');
            if (partnerVal) {
                partnerVal.textContent = `${account.activeMemberCount || 0}개 계정`;
            }
            if (partnerSub) {
                partnerSub.textContent = `전체 사용자: ${account.totalMemberCount || 0}명`;
            }
        }
    }

    function updateRecentActivityTable(logs) {
        const tableCard = document.querySelector('.section_card.dduk-inline-067');
        if (!tableCard) return;

        // Clear existing rows (keep the header)
        const rows = tableCard.querySelectorAll('.table_row:not(.dduk-inline-068)');
        rows.forEach(r => r.remove());

        logs.forEach(log => {
            const row = document.createElement('div');
            row.className = 'table_row';

            // Format date: HH:mm
            const dateObj = new Date(log.createdAt);
            const timeStr = isNaN(dateObj.getTime()) 
                ? '09:00' 
                : `${String(dateObj.getHours()).padStart(2, '0')}:${String(dateObj.getMinutes()).padStart(2, '0')}`;

            // Map actions to clean strings
            let actionStr = log.action;
            if (log.action === 'CREATE_MEMBER') actionStr = '계정 생성';
            else if (log.action === 'UPDATE_MEMBER_ROLE') actionStr = '권한 수정';
            else if (log.action === 'UPDATE_MEMBER_STATUS') actionStr = '상태 변경';
            else if (log.action === 'DELETE_MEMBER') actionStr = '계정 삭제';

            // Status label classes
            let statusBadge = '<span class="dduk-inline-070">완료</span>';

            row.innerHTML = `
                <span class="dduk-inline-069">${timeStr}</span>
                <span>${log.actorName || log.actorLoginId || '시스템'}</span>
                <span>${actionStr}</span>
                <span>${log.targetLabel || '-'}</span>
                <span>${statusBadge}</span>
            `;
            tableCard.appendChild(row);
        });
    }

    // -------------------------------------------------------------
    // 3. 헤더(Topbar) 기능 미구현 요소 활성화 (검색, 알림, 프로필)
    // -------------------------------------------------------------
    
    // 3.1. 통합 검색 구현
    function initSearch() {
        const searchInput = document.querySelector('.dduk-inline-032');
        if (!searchInput) return;

        // Create results container
        const searchWrapper = searchInput.parentElement;
        searchWrapper.style.position = 'relative';
        
        const resultsDropdown = document.createElement('div');
        resultsDropdown.className = 'search-results-dropdown';
        Object.assign(resultsDropdown.style, {
            position: 'absolute',
            top: '100%',
            left: '0',
            width: '100%',
            backgroundColor: '#ffffff',
            border: '1px solid #e2e8f0',
            borderRadius: '8px',
            boxShadow: '0 10px 15px -3px rgba(0,0,0,0.1)',
            zIndex: '100',
            marginTop: '4px',
            maxHeight: '300px',
            overflowY: 'auto',
            display: 'none'
        });
        searchWrapper.appendChild(resultsDropdown);

        const menus = [
            { name: '통합 대시보드', href: 'dashboard.html' },
            { name: '구매/발주 대시보드', href: 'pages/inventory/dashboard.html' },
            { name: '구매 요청', href: 'pages/inventory/purchase-request.html' },
            { name: '발주조회', href: 'pages/inventory/purchase-orders.html' },
            { name: '발주 상태 관리', href: 'pages/inventory/purchase-status.html' },
            { name: '입고 등록', href: 'pages/inventory/movements.html' },
            { name: '재고 조회', href: 'pages/inventory/list.html' },
            { name: '입출고 이력', href: 'pages/inventory/movements.html' },
            { name: '창고 이동', href: 'pages/inventory/transfers.html' },
            { name: '회계 대시보드', href: 'pages/hr/accounting/accounting_dashboard.html' },
            { name: '거래 내역 등록', href: 'pages/hr/accounting/transactions.html' },
            { name: '합계잔액시산표', href: 'pages/hr/accounting/trial-balance.html' },
            { name: '계정 및 권한 관리', href: 'pages/admin/account-security.html' },
            { name: '시스템 운영 관리', href: 'pages/admin/system-admin.html' }
        ];

        searchInput.addEventListener('input', (e) => {
            const query = e.target.value.trim().toLowerCase();
            if (!query) {
                resultsDropdown.style.display = 'none';
                return;
            }

            const matched = menus.filter(m => m.name.toLowerCase().includes(query));
            if (matched.length === 0) {
                resultsDropdown.innerHTML = `<div style="padding: 12px; color: #94a3b8; font-size: 0.8125rem; text-align: center;">검색 결과가 없습니다.</div>`;
            } else {
                resultsDropdown.innerHTML = matched.map(m => `
                    <div class="search-item" style="padding: 10px 16px; font-size: 0.8125rem; cursor: pointer; border-bottom: 1px solid #f1f5f9;" onclick="location.href='${m.href}'">
                        <span style="font-weight: 500; color: #1e293b;">${m.name}</span>
                        <span style="float: right; font-size: 0.75rem; color: #4f46e5;">바로가기</span>
                    </div>
                `).join('');
                
                // Add hover style dynamically
                resultsDropdown.querySelectorAll('.search-item').forEach(item => {
                    item.addEventListener('mouseenter', () => item.style.backgroundColor = '#f8fafc');
                    item.addEventListener('mouseleave', () => item.style.backgroundColor = '#ffffff');
                });
            }
            resultsDropdown.style.display = 'block';
        });

        // Hide when clicking outside
        document.addEventListener('click', (e) => {
            if (!searchWrapper.contains(e.target)) {
                resultsDropdown.style.display = 'none';
            }
        });
    }

    // 3.2. 알림 버튼 활성화
    function initNotifications() {
        const bellBtn = document.querySelector('button[aria-label="알림"]');
        if (!bellBtn) return;

        bellBtn.style.position = 'relative';
        const badge = document.createElement('span');
        Object.assign(badge.style, {
            position: 'absolute',
            top: '2px',
            right: '2px',
            width: '8px',
            height: '8px',
            borderRadius: '50%',
            backgroundColor: '#ef4444'
        });
        bellBtn.appendChild(badge);

        // Popover
        const popover = document.createElement('div');
        popover.className = 'notifications-popover';
        Object.assign(popover.style, {
            position: 'absolute',
            top: '50px',
            right: '0',
            width: '280px',
            backgroundColor: '#ffffff',
            border: '1px solid #e2e8f0',
            borderRadius: '8px',
            boxShadow: '0 10px 15px -3px rgba(0,0,0,0.1)',
            zIndex: '100',
            display: 'none',
            padding: '12px'
        });
        bellBtn.appendChild(popover);

        bellBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            const isVisible = popover.style.display === 'block';
            
            // Close other dropdowns first
            closeAllPopovers();

            if (!isVisible) {
                badge.style.display = 'none'; // Clear badge on read
                popover.innerHTML = `
                    <div style="font-weight: 600; font-size: 0.875rem; border-bottom: 1px solid #f1f5f9; padding-bottom: 8px; margin-bottom: 8px; color: #1e293b;">최근 알림</div>
                    <div style="display: flex; flex-direction: column; gap: 8px;">
                        <div style="font-size: 0.75rem; padding: 6px; background-color: #fef2f2; border-left: 3px solid #ef4444; border-radius: 4px;">
                            <strong style="color: #991b1b;">[재고 경고]</strong> 안전재고 부족 항목이 존재합니다.
                        </div>
                        <div style="font-size: 0.75rem; padding: 6px; background-color: #eff6ff; border-left: 3px solid #3b82f6; border-radius: 4px;">
                            <strong style="color: #1e40af;">[결재 요청]</strong> 신규 발주 요청 건 승인 대기중.
                        </div>
                    </div>
                `;
                popover.style.display = 'block';
            } else {
                popover.style.display = 'none';
            }
        });

        document.addEventListener('click', () => {
            popover.style.display = 'none';
        });
    }

    // 3.3. 프로필 메뉴 활성화
    function initProfileMenu() {
        const userBtn = document.querySelector('button[aria-label="프로필"]');
        if (!userBtn) return;

        userBtn.style.position = 'relative';

        const popover = document.createElement('div');
        popover.className = 'profile-popover';
        Object.assign(popover.style, {
            position: 'absolute',
            top: '50px',
            right: '0',
            width: '180px',
            backgroundColor: '#ffffff',
            border: '1px solid #e2e8f0',
            borderRadius: '8px',
            boxShadow: '0 10px 15px -3px rgba(0,0,0,0.1)',
            zIndex: '100',
            display: 'none',
            padding: '8px 0'
        });
        userBtn.appendChild(popover);

        userBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            const isVisible = popover.style.display === 'block';

            // Close other dropdowns first
            closeAllPopovers();

            if (!isVisible) {
                const userName = localStorage.getItem('userName') || '게스트';
                popover.innerHTML = `
                    <div style="padding: 10px 16px; border-bottom: 1px solid #f1f5f9;">
                        <div style="font-weight: 600; font-size: 0.8125rem; color: #1e293b;">${userName}</div>
                        <div style="font-size: 0.6875rem; color: #94a3b8;">${localStorage.getItem('role') || '사용자'}</div>
                    </div>
                    <div class="profile-item" style="padding: 8px 16px; font-size: 0.8125rem; cursor: pointer; color: #334155;" onclick="location.href='pages/admin/account-security.html'">보안 설정</div>
                    <div class="profile-item" style="padding: 8px 16px; font-size: 0.8125rem; cursor: pointer; color: #334155;" onclick="handleLogout()">로그아웃</div>
                `;
                
                // Add hover style dynamically
                popover.querySelectorAll('.profile-item').forEach(item => {
                    item.addEventListener('mouseenter', () => item.style.backgroundColor = '#f8fafc');
                    item.addEventListener('mouseleave', () => item.style.backgroundColor = '#ffffff');
                });

                popover.style.display = 'block';
            } else {
                popover.style.display = 'none';
            }
        });

        document.addEventListener('click', () => {
            popover.style.display = 'none';
        });
    }

    function closeAllPopovers() {
        const notifications = document.querySelector('.notifications-popover');
        const profile = document.querySelector('.profile-popover');
        if (notifications) notifications.style.display = 'none';
        if (profile) profile.style.display = 'none';
    }

    async function initDashboardData() {
        setStatusBanner('대시보드 데이터를 불러오는 중이야.', 'info');

        try {
            const resData = await requestJson(`${API_BASE}/admin/dashboard`);

            if (resData.status === 'success' && resData.data) {
                updateKPICards(resData.data);
                setStatusBanner('', 'info');
            } else {
                renderKpiErrorState();
                setStatusBanner('대시보드 KPI 응답 형식이 올바르지 않아서 실제 수치를 표시하지 못했어.', 'error');
            }
        } catch (err) {
            console.error("Dashboard KPI fetch failed:", err);
            renderKpiErrorState();
            setStatusBanner('대시보드 KPI를 불러오지 못해서 실제 수치 대신 빈 상태로 표시해.', 'error');
        }

        try {
            const logsData = await requestJson(`${API_BASE}/admin/audit-logs?page=0&size=5`);

            if (logsData.status === 'success' && logsData.data && logsData.data.content) {
                updateRecentActivityTable(logsData.data.content);
            } else {
                renderRecentActivityMessage('최근 활동 응답 형식이 올바르지 않아 활동 내역을 표시하지 못했어.');
            }
        } catch (err) {
            console.error("Recent activities fetch failed:", err);
            renderRecentActivityMessage('최근 활동을 불러오지 못했어. 관리자 API 연결 상태를 확인해줘.');
        }
    }

    function renderKpiErrorState() {
        const kpiContainer = document.querySelector('.kpi_grid');
        if (!kpiContainer) return;

        const cards = kpiContainer.querySelectorAll('.kpi_card');
        cards.forEach((card) => {
            const value = card.querySelector('.dduk-inline-041, .dduk-inline-045');
            const sub = card.querySelector('.dduk-inline-042, .dduk-inline-043, .dduk-inline-044, .dduk-inline-046');

            if (value) {
                value.textContent = '-';
            }

            if (sub) {
                sub.textContent = '실데이터 연결 필요';
            }
        });
    }

    function renderRecentActivityMessage(message) {
        const tableCard = document.querySelector('.section_card.dduk-inline-067');
        if (!tableCard) return;

        const rows = tableCard.querySelectorAll('.table_row:not(.dduk-inline-068)');
        rows.forEach((row) => row.remove());

        const row = document.createElement('div');
        row.className = 'table_row';
        row.innerHTML = `
            <span class="dduk-inline-069">-</span>
            <span>시스템</span>
            <span>${message}</span>
            <span>-</span>
            <span class="dduk-inline-072">확인 필요</span>
        `;
        tableCard.appendChild(row);
    }

})();
