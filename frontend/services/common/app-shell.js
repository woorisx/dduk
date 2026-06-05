(function () {
    const GLOBAL_NAV_ITEMS = [
        {
            label: "대시보드",
            items: [
                { href: "dashboard.html", label: "통합 대시보드", roles: ["ADMIN", "HR", "INVENTORY"] }
            ]
        },
        {
            label: "구매/발주",
            items: [
                { href: "pages/inventory/purchase-dashboard.html", label: "구매/발주 대시보드", roles: ["ADMIN", "INVENTORY"] },
                { href: "pages/inventory/purchase-request.html", label: "구매 요청", roles: ["ADMIN", "INVENTORY"] },
                { href: "pages/inventory/purchase-orders.html", label: "발주조회", roles: ["ADMIN", "INVENTORY"] },
                { href: "pages/inventory/purchase-status.html", label: "발주 상태 관리", roles: ["ADMIN", "INVENTORY"] },
                { href: "pages/inventory/receiving.html", label: "입고 등록", roles: ["ADMIN", "INVENTORY"] },
                { href: "pages/inventory/vendors.html", label: "거래처 관리", roles: ["ADMIN", "INVENTORY"] }
            ]
        },
        {
            label: "재고관리",
            items: [
                { href: "pages/inventory/dashboard.html", label: "재고관리 대시보드", roles: ["ADMIN", "INVENTORY"] },
                { href: "pages/inventory/list.html", label: "재고 조회", roles: ["ADMIN", "INVENTORY"] },
                { href: "pages/inventory/movements.html", label: "입출고 이력", roles: ["ADMIN", "INVENTORY"] },
                { href: "pages/inventory/transfers.html", label: "창고 이동", roles: ["ADMIN", "INVENTORY"] },
                { href: "pages/inventory/reorder.html", label: "자동 발주 추천", roles: ["ADMIN", "INVENTORY"] }
            ]
        },
        {
            label: "회계관리",
            items: [
                { href: "pages/hr/accounting/accounting_dashboard.html", label: "회계 대시보드", roles: ["ADMIN", "HR"] },
                { href: "pages/hr/accounting/transactions.html", label: "거래내역 등록", roles: ["ADMIN", "HR"] },
                { href: "pages/hr/accounting/accounts.html", label: "계정과목 관리", roles: ["ADMIN", "HR"] },
                { href: "pages/hr/accounting/voucher_management.html", label: "전표 관리", roles: ["ADMIN", "HR"] },
                { href: "pages/hr/accounting/trial_balance.html", label: "합계잔액시산표", roles: ["ADMIN", "HR"] },
                { href: "pages/hr/accounting/reports.html", label: "재무제표", roles: ["ADMIN", "HR"] },
                { href: "pages/hr/accounting/accounting_reports.html", label: "회계 분석 리포트", roles: ["ADMIN", "HR"] },
                { href: "pages/hr/accounting/monthly_closing.html", label: "월 마감", roles: ["ADMIN", "HR"] },
                { href: "pages/hr/accounting/payroll_management.html", label: "급여 계산/대장", roles: ["ADMIN", "HR"] },
                { href: "pages/hr/accounting/tax_invoice.html", label: "세금계산서", roles: ["ADMIN", "HR"] },
                { href: "pages/hr/accounting/expenses.html", label: "비용 처리", roles: ["ADMIN", "HR"] },
            ]
        },
        {
            label: "문서/증빙",
            items: [
                { href: "pages/ocr/upload.html", label: "증빙 업로드", roles: ["ADMIN", "HR", "INVENTORY"] },
                { href: "pages/ocr/ocr-box.html", label: "OCR 문서함", roles: ["ADMIN", "HR", "INVENTORY"] },
                { href: "#", label: "계약 문서", roles: ["ADMIN"] }
            ]
        },
        {
            label: "AI 업무지원",
            items: [
                { href: "#", label: "AI 챗봇", roles: ["ADMIN", "HR", "INVENTORY"], action: "chatbot" },
                { href: "pages/admin/anomaly-detection.html", label: "이상 탐지", roles: ["ADMIN"] },
                { href: "#", label: "예측 분석", roles: ["ADMIN", "HR", "INVENTORY"], action: "prediction" }
            ]
        },
        {
            label: "관리자",
            items: [
                { href: "pages/admin/account-security.html", label: "계정 및 권한 관리", roles: ["ADMIN"] },
                { href: "pages/admin/org-admin.html", label: "조직 및 부서 관리", roles: ["ADMIN"] },
                { href: "pages/admin/system-admin.html", label: "시스템 운영 관리", roles: ["ADMIN"] },
                { href: "pages/admin/notice-admin.html", label: "공지사항 관리", roles: ["ADMIN"] },
                { href: "pages/admin/chatbot-test.html", label: "AI 챗봇 테스트", roles: ["ADMIN"] },
                { href: "pages/admin/task-history.html", label: "AI/RPA 작업 이력", roles: ["ADMIN"] }
            ]
        }
    ];

    function getRelativeRoot() {
        const path = window.location.pathname.replace(/\\/g, '/');
        if (path.includes('/pages/')) {
            const depth = path.split('/pages/')[1].split('/').length;
            return '../'.repeat(depth);
        }
        return './';
    }

    function renderNav() {
        const session = window.ddukSession && typeof window.ddukSession.getSession === 'function'
            ? window.ddukSession.getSession()
            : { role: "USER" };
        const userRole = session.role || "USER";
        const root = getRelativeRoot();

        return GLOBAL_NAV_ITEMS.map((group) => {
            const allowedItems = group.items.filter((item) => item.roles.includes(userRole));
            if (allowedItems.length === 0) return "";

            const itemHtml = allowedItems.map((item) => {
                const href = item.href === "#" ? "#" : root + item.href;
                const isCurrent = window.location.pathname.replace(/\\/g, '/').endsWith(item.href)
                    ? ' aria-current="page"'
                    : "";
                const actionAttr = item.action ? ` data-ai-action="${item.action}"` : "";

                return `
                    <a href="${href}"${isCurrent}${actionAttr}>
                        <span class="nav-label">${item.label}</span>
                    </a>
                `;
            }).join("");

            return `
                <div class="nav-group">
                    <div class="nav-header" onclick="toggleNavGroup(this)">
                        ${group.label}
                    </div>
                    <div class="nav-items">
                        ${itemHtml}
                    </div>
                </div>
            `;
        }).join("");
    }

    function bindNavActions() {
        document.querySelectorAll("[data-ai-action]").forEach((anchor) => {
            anchor.addEventListener("click", (event) => {
                event.preventDefault();
                const action = anchor.getAttribute("data-ai-action");
                if (typeof window.toggleFloatingChatbot === "function") {
                    window.toggleFloatingChatbot(true);
                }
                if (typeof window.switchPortalTab === "function") {
                    window.switchPortalTab(action === "prediction" ? "prediction" : "chatbot");
                }
            });
        });
    }

    window.toggleNavGroup = function (header) {
        const currentGroup = header.parentElement;
        const allGroups = document.querySelectorAll('.nav-group');
        const isExpanded = currentGroup.classList.contains('expanded');

        allGroups.forEach((group) => group.classList.remove('expanded'));

        if (!isExpanded) {
            currentGroup.classList.add('expanded');
        }
    };

    function renderCards(items) {
        return items.map((item) => {
            if (item.value !== undefined) {
                return `
                    <article class="panel stat-card">
                        <span class="label">${item.title}</span>
                        <span class="value">${item.value}</span>
                        ${item.trend ? `<span class="trend ${item.trend.startsWith('+') ? 'up' : 'down'}">${item.trend}</span>` : ''}
                    </article>
                `;
            }
            return `
                <article class="panel">
                    <h3>${item.title}</h3>
                    <p>${item.description}</p>
                </article>
            `;
        }).join("");
    }

    function renderList(items) {
        return items.map((item) => `<li>${item}</li>`).join("");
    }

    function hydratePage(config) {
        const navElement = document.querySelector("[data-nav]");
        const titleElement = document.querySelector("[data-page-title]");
        const descriptionElement = document.querySelector("[data-page-description]");
        const cardsElement = document.querySelector("[data-summary-cards]");
        const todoElement = document.querySelector("[data-todo-list]");
        const tableElement = document.querySelector("[data-table-list]");
        const apiElement = document.querySelector("[data-api-list]");

        if (navElement) {
            navElement.innerHTML = renderNav();
            bindNavActions();
        }

        if (titleElement && config.title !== undefined) {
            titleElement.textContent = config.title || "";
        }

        if (descriptionElement && config.description !== undefined) {
            descriptionElement.textContent = config.description || "";
        }

        if (cardsElement) {
            cardsElement.innerHTML = renderCards(config.cards || []);
        }

        if (todoElement) {
            todoElement.innerHTML = renderList(config.todoItems || []);
        }

        if (tableElement) {
            tableElement.innerHTML = renderList(config.tables || []);
        }

        if (apiElement) {
            apiElement.innerHTML = renderList(config.apis || []);
        }
    }

    window.ddukAppShell = {
        hydratePage
    };
})();
