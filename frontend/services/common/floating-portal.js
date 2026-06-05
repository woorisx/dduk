(function () {
    let chatbotHistory = [];

    const money = {
        format: function(value) {
            return Number(value || 0).toLocaleString('ko-KR');
        }
    };

    function getRootPath() {
        return window.DDUK_COMMON ? window.DDUK_COMMON.getRootPath() : './';
    }

    function resolveHref(href) {
        return window.DDUK_COMMON ? window.DDUK_COMMON.resolveHref(href) : href;
    }

    function getApiBaseUrl() {
        return window.DDUK_COMMON ? window.DDUK_COMMON.getApiBaseUrl() : '';
    }

    function getHeaders(extraHeaders) {
        return window.DDUK_COMMON ? window.DDUK_COMMON.getHeaders(extraHeaders) : {
            'Content-Type': 'application/json',
            ...(extraHeaders || {})
        };
    }

    // 패널을 트리거 아래/옆에 동적으로 정렬하는 헬퍼 함수
    function alignPanelToTrigger() {
        const panel = document.getElementById('dduk-floating-chatbot-panel');
        const trigger = document.getElementById('dduk-floating-chatbot-trigger');
        if (!panel || !trigger) return;

        const triggerRect = trigger.getBoundingClientRect();
        const panelWidth = panel.offsetWidth || 450;
        const panelHeight = panel.offsetHeight || 600;

        let targetTop = triggerRect.bottom + 10;
        let targetLeft = triggerRect.right - panelWidth;

        // 화면 하단을 벗어나면 트리거 위쪽으로 배치
        if (targetTop + panelHeight > window.innerHeight) {
            targetTop = triggerRect.top - panelHeight - 10;
        }
        if (targetTop < 10) {
            targetTop = 10;
        }

        // 화면 왼쪽을 벗어나면 트리거 좌측 끝선에 맞춤
        if (targetLeft < 10) {
            targetLeft = Math.max(10, triggerRect.left);
        }
        if (targetLeft + panelWidth > window.innerWidth - 10) {
            targetLeft = window.innerWidth - panelWidth - 10;
        }

        panel.style.right = 'auto';
        panel.style.bottom = 'auto';
        panel.style.left = targetLeft + 'px';
        panel.style.top = targetTop + 'px';
    }

    function toggleFloatingChatbot(forceOpen) {
        const panel = document.getElementById('dduk-floating-chatbot-panel');
        if (!panel) return;

        if (forceOpen === true || (forceOpen === undefined && !panel.classList.contains('active'))) {
            alignPanelToTrigger();
        }

        if (forceOpen === true) {
            panel.classList.add('active');
        } else if (forceOpen === false) {
            panel.classList.remove('active');
        } else {
            panel.classList.toggle('active');
        }

        if (panel.classList.contains('active')) {
            const activeTab = panel.querySelector('.dduk-portal-tab.active');
            if (activeTab && activeTab.getAttribute('data-tab') === 'chatbot') {
                const input = document.getElementById('dduk-floating-chatbot-input');
                if (input) input.focus();
            }
        }
    }

    function openFloatingChatbotWithQuery(query) {
        toggleFloatingChatbot(true);
        switchPortalTab('chatbot');
        const input = document.getElementById('dduk-floating-chatbot-input');
        if (input) {
            input.value = query;
        }
        sendFloatingChatMessage(query);
    }

    function switchPortalTab(tabName) {
        document.querySelectorAll('.dduk-portal-tab').forEach(btn => {
            if (btn.getAttribute('data-tab') === tabName) {
                btn.classList.add('active');
            } else {
                btn.classList.remove('active');
            }
        });
        
        document.querySelectorAll('.dduk-portal-content').forEach(content => {
            if (content.id === `dduk-portal-content-${tabName}`) {
                content.classList.add('active');
            } else {
                content.classList.remove('active');
            }
        });
        
        if (tabName === 'chatbot') {
            const input = document.getElementById('dduk-floating-chatbot-input');
            if (input) input.focus();
        } else if (tabName === 'rpa') {
            if (window.DDUK_RPA_WIDGET && typeof window.DDUK_RPA_WIDGET.renderDynamicRpaWidget === 'function') {
                window.DDUK_RPA_WIDGET.renderDynamicRpaWidget();
            }
        } else if (tabName === 'anomaly') {
            renderAnomalyList();
        } else if (tabName === 'prediction') {
            renderPredictionList();
        }
        
        if (window.lucide) lucide.createIcons();
    }

    async function sendFloatingChatMessage(message) {
        if (!message) return;
        
        const messagesContainer = document.getElementById('dduk-floating-chatbot-messages');
        const input = document.getElementById('dduk-floating-chatbot-input');
        if (input) input.value = '';
        
        appendFloatingMessage('user', message);
        
        const loadingDiv = document.createElement("div");
        loadingDiv.className = "dduk-chat-bubble bot loading";
        loadingDiv.innerHTML = `
            <svg class="animate-spin" style="width: 1rem; height: 1rem; color: #4f46e5;" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            <span>분석 중...</span>
        `;
        messagesContainer.appendChild(loadingDiv);
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
        
        try {
            const response = await fetch(`${getApiBaseUrl()}/api/v1/ai/chat`, {
                method: "POST",
                headers: getHeaders({
                    "Content-Type": "application/json"
                }),
                body: JSON.stringify({
                    message: message,
                    history: chatbotHistory
                })
            });
            
            const text = await response.text();
            let payload;
            try {
                payload = text ? JSON.parse(text) : null;
            } catch (e) {
                throw new Error("응답 형식 오류가 발생했습니다.");
            }
            
            loadingDiv.remove();
            
            if (!response.ok || !payload) {
                const errMsg = payload && payload.message ? payload.message : `실패 (HTTP ${response.status})`;
                throw new Error(errMsg);
            }
            
            if (payload.status === "success" && payload.data && payload.data.response) {
                const reply = payload.data.response;
                appendFloatingMessage("bot", reply);
                chatbotHistory.push({ role: "user", content: message });
                chatbotHistory.push({ role: "model", content: reply });
            } else {
                throw new Error(payload.message || "답변을 받아오지 못했습니다.");
            }
        } catch (error) {
            loadingDiv.remove();
            appendFloatingMessage("bot", `오류: ${error.message}`);
        }
    }

    function appendFloatingMessage(role, text) {
        const container = document.getElementById('dduk-floating-chatbot-messages');
        if (!container) return;
        
        const messageDiv = document.createElement("div");
        messageDiv.className = `dduk-chat-bubble ${role}`;
        
        if (text.startsWith('오류:')) {
            messageDiv.style.backgroundColor = '#fef2f2';
            messageDiv.style.color = '#991b1b';
            messageDiv.style.borderColor = '#fee2e2';
        }
        
        messageDiv.textContent = text;
        container.appendChild(messageDiv);
        container.scrollTop = container.scrollHeight;
    }

    async function renderAnomalyList() {
        const container = document.getElementById('dduk-floating-anomaly-list');
        if (!container) return;

        container.innerHTML = '<div class="py-12 text-center text-xs text-slate-400">이상 징후를 감지하는 중입니다...</div>';
        
        const detailUrl = resolveHref('pages/admin/anomaly-detection.html');
        
        try {
            // OPEN 상태의 이상 징후 이력 조회
            if (!window.DDUK_RPA_WIDGET || typeof window.DDUK_RPA_WIDGET.requestRpaApi !== 'function') {
                throw new Error('RPA 모듈이 로드되지 않았습니다.');
            }
            const data = await window.DDUK_RPA_WIDGET.requestRpaApi('/api/v1/admin/anomaly-logs?status=OPEN&size=5', { method: "GET" });
            const list = data && data.content ? data.content : [];

            if (list.length === 0) {
                container.innerHTML = `
                    <div class="flex flex-col items-center justify-center py-12 text-center text-slate-400">
                        <i data-lucide="check-circle" class="w-8 h-8 text-emerald-500 mb-2" style="margin: 0 auto 0.5rem auto;"></i>
                        <p class="text-xs font-bold text-slate-600">감지된 이상 징후 없음</p>
                        <p class="text-[10px] text-slate-400 mt-1">시스템이 안정적인 상태입니다.</p>
                    </div>
                `;
                if (window.lucide) lucide.createIcons();
                return;
            }

            let html = list.map(item => {
                const timeStr = item.lastDetectedAt ? (window.DDUK_COMMON ? window.DDUK_COMMON.formatDateTime(item.lastDetectedAt) : item.lastDetectedAt) : '-';
                const severityClass = item.severity === 'DANGER' || item.severity === 'danger' ? 'danger' : 'warning';
                const severityLabel = item.severity === 'DANGER' || item.severity === 'danger' ? '위험' : '경고';
                const aiQuery = `${item.title} (${item.summary || ''})에 대해 분석해 주고, 해당 건의 발생 원인과 ERP 시스템 상의 권장 후속 조치를 자세히 설명해 줘.`;

                return `
                    <div class="dduk-anomaly-card" style="font-family: inherit;">
                        <div class="dduk-anomaly-card-header" style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 0.5rem;">
                            <span class="dduk-anomaly-badge ${severityClass}">${severityLabel}</span>
                            <span class="dduk-anomaly-time" style="font-size: 0.7rem; color: #94a3b8;">${timeStr}</span>
                        </div>
                        <div class="dduk-anomaly-title" style="font-size: 0.825rem; font-weight: 700; color: #1e293b; line-height: 1.3;">${item.title}</div>
                        <div class="dduk-anomaly-desc" style="font-size: 0.75rem; color: #64748b; margin-top: 0.25rem; line-height: 1.4;">${item.summary || '-'}</div>
                        <div class="dduk-anomaly-action" style="display: flex; gap: 0.5rem; justify-content: flex-end; margin-top: 0.625rem;">
                            <a href="${detailUrl}" class="dduk-prediction-link" style="background-color: #f1f5f9; color: #475569; border-radius: 0.5rem; font-size: 0.75rem; text-decoration: none; padding: 0.3rem 0.75rem; display: inline-flex; align-items: center; font-weight: 700;">
                                이동 ↗
                            </a>
                            <button class="dduk-anomaly-btn" onclick="window.openFloatingChatbotWithQuery('${aiQuery}')">AI 분석</button>
                        </div>
                    </div>
                `;
            }).join('');
            
            html += `
                <div style="margin-top: 1.25rem; text-align: center; padding-bottom: 0.5rem;">
                    <a href="${detailUrl}" style="color: #4f46e5; text-decoration: none; font-size: 0.785rem; font-weight: 700; display: inline-flex; align-items: center; gap: 0.25rem;">
                        <span>이상 감지 상세 모니터링 페이지로 이동</span>
                        <i data-lucide="arrow-right" style="width: 0.85rem; height: 0.85rem;"></i>
                    </a>
                </div>
            `;
            
            container.innerHTML = html;
            if (window.lucide) lucide.createIcons();
        } catch (err) {
            container.innerHTML = `
                <div class="py-8 text-center text-rose-500 text-xs">
                    이상 감지 목록을 불러오지 못했습니다: ${err.message || err}
                </div>
            `;
        }
    }

    async function renderPredictionList() {
        const container = document.getElementById('dduk-floating-prediction-list');
        if (!container) return;

        container.innerHTML = '<div class="py-12 text-center text-xs text-slate-400">재고 및 재무 데이터를 분석하는 중입니다...</div>';
        
        try {
            if (!window.DDUK_RPA_WIDGET || typeof window.DDUK_RPA_WIDGET.requestRpaApi !== 'function') {
                throw new Error('RPA 모듈이 로드되지 않았습니다.');
            }
            // 1. 발주 추천 품목 목록 조회
            const data = await window.DDUK_RPA_WIDGET.requestRpaApi('/api/v1/inventory/purchase-recommendations', { method: "GET" });
            const items = data && data.items ? data.items : [];

            // 2. 이상 징후 이력 조회 (회계 정산 리스크 체크용)
            const anomalyData = await window.DDUK_RPA_WIDGET.requestRpaApi('/api/v1/admin/anomaly-logs?size=10', { method: "GET" });
            const anomalies = anomalyData && anomalyData.content ? anomalyData.content : [];

            // 2-1) 다음 달 예상 발주 금액 집계 (recommendedOrderQty * averageCost)
            const totalRecAmount = items.reduce((sum, item) => {
                const qty = item.recommendedOrderQty || 0;
                const cost = Number(item.averageCost || 0);
                return sum + (qty * cost);
            }, 0);

            // 2-2) 재고 소진 위험 품목 (daysUntilStockout이 가장 작고 0보다 큰 품목 중 시급한 품목 선정)
            const urgentStockoutItem = [...items]
                .filter(item => item.daysUntilStockout > 0)
                .sort((a, b) => a.daysUntilStockout - b.daysUntilStockout)[0];

            let stockoutText = "현재 집계상 재고 소진 리스크가 없습니다.";
            let stockoutStat = "안정 상태";
            let stockoutColor = "green";
            if (urgentStockoutItem) {
                stockoutText = `평균 출고 기준 ${urgentStockoutItem.itemName} 품목의 재고가 ${urgentStockoutItem.daysUntilStockout}일 내 소진될 것으로 예측됩니다.`;
                stockoutStat = `${urgentStockoutItem.daysUntilStockout}일 내 소진 우려`;
                stockoutColor = urgentStockoutItem.daysUntilStockout <= 7 ? "purple" : "blue";
            }

            // 2-3) 회계 정산 예측 (이상 징후 항목 중 severity가 danger/DANGER 이면서 OPEN 상태인 항목이 있으면 주의, 없으면 정상)
            const hasDangerAnomaly = anomalies.some(a => (a.severity === 'DANGER' || a.severity === 'danger') && a.status === 'OPEN');
            const accountingStat = hasDangerAnomaly ? "정산 주의 필요" : "정상 정산 예상";
            const accountingDesc = hasDangerAnomaly 
                ? "비정상 전표 패턴과 공급 부족 불일치 이슈가 감지돼 월 마감 전 추가 확인이 필요합니다."
                : "최근 거래 흐름 대비 특이점이 낮아 월 마감은 비교적 안정적일 것으로 예상됩니다.";
            const accountingColor = hasDangerAnomaly ? "purple" : "blue";

            const predictionItems = [
                {
                    title: '다음 달 예상 발주 금액',
                    stat: `₩${money.format(totalRecAmount)}`,
                    desc: '현재 추천 발주 필요 품목들의 권장 수량과 평균 단가를 기준으로 계산한 금액입니다.',
                    color: 'blue',
                    linkText: '발주 추천 페이지 이동',
                    linkUrl: 'pages/inventory/reorder.html'
                },
                {
                    title: '이상 재고 탐지 리스트',
                    stat: stockoutStat,
                    desc: stockoutText,
                    color: stockoutColor,
                    linkText: '재고 조회 이동',
                    linkUrl: 'pages/inventory/list.html'
                },
                {
                    title: '월 마감 회계 정산 예측',
                    stat: accountingStat,
                    desc: accountingDesc,
                    color: accountingColor,
                    linkText: '월 마감 페이지 이동',
                    linkUrl: 'pages/hr/accounting/monthly_closing.html'
                }
            ];

            container.innerHTML = predictionItems.map(item => `
                <div class="dduk-prediction-card">
                    <div class="dduk-prediction-header" style="display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.5rem;">
                        <div class="dduk-prediction-indicator ${item.color}" style="width: 0.5rem; height: 0.5rem; border-radius: 9999px;"></div>
                        <div class="dduk-prediction-title" style="font-size: 0.825rem; font-weight: 700; color: #1e293b; line-height: 1.3;">${item.title}</div>
                    </div>
                    <div class="dduk-prediction-stat" style="font-size: 1.15rem; font-weight: 800; color: #4f46e5; margin: 0.25rem 0;">${item.stat}</div>
                    <div class="dduk-prediction-desc" style="font-size: 0.75rem; color: #64748b; line-height: 1.4;">${item.desc}</div>
                    <div class="dduk-prediction-action" style="display: flex; justify-content: flex-end; margin-top: 0.625rem;">
                        <a href="${resolveHref(item.linkUrl)}" class="dduk-prediction-link" style="text-decoration: none; display: inline-flex; align-items: center; gap: 0.25rem;">
                            <span>${item.linkText}</span>
                            <i data-lucide="external-link" style="width: 0.75rem; height: 0.75rem;"></i>
                        </a>
                    </div>
                </div>
            `).join('');
            
            if (window.lucide) lucide.createIcons();
        } catch (err) {
            container.innerHTML = `
                <div class="py-8 text-center text-rose-500 text-xs">
                    예측 분석 데이터를 처리하지 못했습니다: ${err.message || err}
                </div>
            `;
        }
    }

    function initAICopilotPortal() {
        if (document.getElementById('dduk-floating-chatbot-container')) return;

        // 사이드바 "AI 챗봇"과 "예측 분석" 메뉴 클릭 시 플로팅 위젯 강제 오픈 및 탭 이동 연동
        document.querySelectorAll('.menu_item').forEach(btn => {
            const labelEl = btn.querySelector('span');
            if (labelEl) {
                const label = labelEl.textContent.trim();
                if (label === 'AI 챗봇') {
                    btn.addEventListener('click', (e) => {
                        e.preventDefault();
                        toggleFloatingChatbot(true);
                        switchPortalTab('chatbot');
                    });
                } else if (label === '예측 분석') {
                    btn.addEventListener('click', (e) => {
                        e.preventDefault();
                        toggleFloatingChatbot(true);
                        switchPortalTab('prediction');
                    });
                } else if (label === '이상 탐지') {
                    btn.addEventListener('click', (e) => {
                        e.preventDefault();
                        toggleFloatingChatbot(true);
                        switchPortalTab('anomaly');
                    });
                }
            }
        });
        
        const css = `
            .dduk-chat-trigger {
                position: fixed;
                top: 5.5rem;
                right: 1.5rem;
                width: 3.5rem;
                height: 3.5rem;
                background-color: #4f46e5;
                color: #ffffff;
                border-radius: 9999px;
                display: flex;
                align-items: center;
                justify-content: center;
                box-shadow: 0 10px 25px -5px rgba(79, 70, 229, 0.4), 0 8px 10px -6px rgba(79, 70, 229, 0.4);
                cursor: move;
                transition: transform 0.2s cubic-bezier(0.4, 0, 0.2, 1);
                z-index: 9999;
                user-select: none;
                touch-action: none;
            }
            .dduk-chat-trigger:hover {
                transform: scale(1.08);
                background-color: #4338ca;
            }
            .dduk-chat-trigger:active {
                transform: scale(0.95);
            }
            .dduk-chat-panel {
                position: fixed;
                top: 9.5rem;
                right: 1.5rem;
                width: 450px;
                height: 600px;
                min-width: 320px;
                min-height: 400px;
                max-width: 95vw;
                max-height: 80vh;
                background-color: #ffffff;
                border-radius: 1.25rem;
                border: 1px solid #f1f5f9;
                box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1);
                display: flex;
                flex-direction: column;
                overflow: hidden;
                resize: none !important;
                transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1), opacity 0.3s ease;
                transform: translateY(20px) scale(0.9);
                opacity: 0;
                pointer-events: none;
                z-index: 9999;
            }
            .dduk-chat-panel::-webkit-resizer {
                display: none !important;
            }
            .dduk-chat-panel.active {
                transform: translateY(0) scale(1);
                opacity: 1;
                pointer-events: auto;
            }
            .dduk-chat-header {
                padding: 1rem 1.25rem;
                background: linear-gradient(135deg, #4f46e5 0%, #3730a3 100%);
                color: #ffffff;
                display: flex;
                align-items: center;
                justify-content: space-between;
            }
            .dduk-chat-header-info {
                display: flex;
                align-items: center;
                gap: 0.75rem;
            }
            .dduk-chat-header-dot {
                width: 0.5rem;
                height: 0.5rem;
                background-color: #34d399;
                border-radius: 9999px;
                box-shadow: 0 0 8px #34d399;
                animation: dduk-pulse 2s infinite;
            }
            @keyframes dduk-pulse {
                0% { opacity: 0.4; }
                50% { opacity: 1; }
                100% { opacity: 0.4; }
            }
            .dduk-chat-header-title {
                font-size: 0.875rem;
                font-weight: 700;
            }
            .dduk-chat-header-close {
                background: none;
                border: none;
                color: rgba(255, 255, 255, 0.8);
                cursor: pointer;
                padding: 0.25rem;
                display: flex;
                align-items: center;
                justify-content: center;
                border-radius: 0.375rem;
                transition: all 0.2s;
            }
            .dduk-chat-header-close:hover {
                color: #ffffff;
                background-color: rgba(255, 255, 255, 0.1);
            }
            
            /* Portal Tab Styling */
            .dduk-portal-tabs {
                display: flex;
                background-color: #f1f5f9;
                border-bottom: 1px solid #e2e8f0;
                padding: 0.25rem;
                gap: 0.25rem;
            }
            .dduk-portal-tab {
                flex: 1;
                background: none;
                border: none;
                padding: 0.5rem 0;
                font-size: 0.8rem;
                font-weight: 700;
                color: #64748b;
                cursor: pointer;
                border-radius: 0.5rem;
                transition: all 0.2s;
                text-align: center;
            }
            .dduk-portal-tab.active {
                background-color: #ffffff;
                color: #4f46e5;
                box-shadow: 0 1px 3px rgba(0,0,0,0.05);
            }
            .dduk-portal-content {
                display: none;
                flex-direction: column;
                flex: 1;
                overflow: hidden;
            }
            .dduk-portal-content.active {
                display: flex;
            }
            .dduk-portal-scroll-area {
                flex: 1;
                padding: 1rem;
                overflow-y: auto;
                background-color: #f8fafc;
            }
            
            /* Anomaly Card Styling */
            .dduk-anomaly-card {
                background: #ffffff;
                border: 1px solid #e2e8f0;
                border-radius: 0.75rem;
                padding: 0.875rem;
                margin-bottom: 0.75rem;
                box-shadow: 0 1px 2px rgba(0,0,0,0.02);
            }
            .dduk-anomaly-card-header {
                display: flex;
                align-items: center;
                justify-content: space-between;
                margin-bottom: 0.375rem;
            }
            .dduk-anomaly-badge {
                font-size: 0.7rem;
                font-weight: 800;
                padding: 0.15rem 0.4rem;
                border-radius: 9999px;
            }
            .dduk-anomaly-badge.danger {
                background-color: #fee2e2;
                color: #ef4444;
            }
            .dduk-anomaly-badge.warning {
                background-color: #fef3c7;
                color: #d97706;
            }
            .dduk-anomaly-time {
                font-size: 0.7rem;
                color: #94a3b8;
                font-weight: 500;
            }
            .dduk-anomaly-title {
                font-size: 0.825rem;
                font-weight: 700;
                color: #1e293b;
                line-height: 1.3;
            }
            .dduk-anomaly-desc {
                font-size: 0.75rem;
                color: #64748b;
                margin-top: 0.25rem;
                line-height: 1.4;
            }
            .dduk-anomaly-action {
                display: flex;
                justify-content: flex-end;
            }
            .dduk-anomaly-btn {
                background-color: #f1f5f9;
                color: #4f46e5;
                border: none;
                border-radius: 0.5rem;
                padding: 0.3rem 0.75rem;
                font-size: 0.75rem;
                font-weight: 700;
                cursor: pointer;
                transition: all 0.2s;
            }
            .dduk-anomaly-btn:hover {
                background-color: #e2e8f0;
            }
            
            /* Prediction Card Styling */
            .dduk-prediction-card {
                background: #ffffff;
                border: 1px solid #e2e8f0;
                border-radius: 0.75rem;
                padding: 0.875rem;
                margin-bottom: 0.75rem;
                box-shadow: 0 1px 2px rgba(0,0,0,0.02);
            }
            .dduk-prediction-header {
                display: flex;
                align-items: center;
                gap: 0.5rem;
                margin-bottom: 0.5rem;
            }
            .dduk-prediction-indicator {
                width: 0.5rem;
                height: 0.5rem;
                border-radius: 9999px;
            }
            .dduk-prediction-indicator.blue { background-color: #3b82f6; }
            .dduk-prediction-indicator.green { background-color: #10b981; }
            .dduk-prediction-indicator.purple { background-color: #8b5cf6; }
            
            .dduk-prediction-title {
                font-size: 0.825rem;
                font-weight: 700;
                color: #1e293b;
                line-height: 1.3;
            }
            .dduk-prediction-stat {
                font-size: 1.15rem;
                font-weight: 800;
                color: #4f46e5;
                margin: 0.25rem 0;
            }
            .dduk-prediction-desc {
                font-size: 0.75rem;
                color: #64748b;
                line-height: 1.4;
            }
            .dduk-prediction-action {
                display: flex;
                justify-content: flex-end;
                margin-top: 0.625rem;
            }
            .dduk-prediction-link {
                background-color: #e0e7ff;
                color: #4f46e5;
                text-decoration: none;
                border-radius: 0.5rem;
                padding: 0.3rem 0.75rem;
                font-size: 0.75rem;
                font-weight: 700;
                cursor: pointer;
                transition: all 0.2s;
                display: inline-flex;
                align-items: center;
                gap: 0.25rem;
            }
            .dduk-prediction-link:hover {
                background-color: #c7d2fe;
            }
            
            .dduk-chat-messages {
                flex: 1;
                padding: 1.25rem;
                overflow-y: auto;
                background-color: #f8fafc;
                display: flex;
                flex-direction: column;
                gap: 1rem;
                scrollbar-width: thin;
            }
            .dduk-chat-messages::-webkit-scrollbar {
                width: 6px;
            }
            .dduk-chat-messages::-webkit-scrollbar-thumb {
                background-color: #cbd5e1;
                border-radius: 3px;
            }
            .dduk-chat-input-area {
                padding: 0.75rem 1rem;
                border-top: 1px solid #f1f5f9;
                background-color: #ffffff;
                display: flex;
                align-items: center;
                gap: 0.5rem;
            }
            .dduk-chat-input {
                flex: 1;
                border: 1px solid #e2e8f0;
                border-radius: 0.75rem;
                padding: 0.625rem 0.875rem;
                font-size: 0.875rem;
                outline: none;
                transition: border-color 0.2s;
                background-color: #ffffff;
                color: #1e293b;
            }
            .dduk-chat-input:focus {
                border-color: #4f46e5;
            }
            .dduk-chat-send-btn {
                background-color: #4f46e5;
                color: #ffffff;
                border: none;
                border-radius: 0.75rem;
                width: 2.25rem;
                height: 2.25rem;
                display: flex;
                align-items: center;
                justify-content: center;
                cursor: pointer;
                transition: background-color 0.2s;
            }
            .dduk-chat-send-btn:hover {
                background-color: #4338ca;
            }
            .dduk-chat-bubble {
                max-width: 85%;
                padding: 0.625rem 0.875rem;
                font-size: 0.875rem;
                line-height: 1.4;
                word-break: break-all;
                white-space: pre-wrap;
            }
            .dduk-chat-bubble.user {
                align-self: flex-end;
                background-color: #4f46e5;
                color: #ffffff;
                border-radius: 1rem 1rem 0 1rem;
                box-shadow: 0 2px 4px rgba(79, 70, 229, 0.15);
            }
            .dduk-chat-bubble.bot {
                align-self: flex-start;
                background-color: #ffffff;
                color: #1e293b;
                border-radius: 1rem 1rem 1rem 0;
                border: 1px solid #e2e8f0;
                box-shadow: 0 2px 4px rgba(0, 0, 0, 0.02);
            }
            .dduk-chat-bubble.loading {
                display: flex;
                align-items: center;
                gap: 0.5rem;
                color: #64748b;
                background-color: #f1f5f9;
            }

            .dduk-portal-resizer-left {
                width: 14px;
                height: 14px;
                position: absolute;
                left: 4px;
                bottom: 4px;
                cursor: sw-resize;
                z-index: 10000;
                background: linear-gradient(225deg, transparent 30%, #94a3b8 30%, #94a3b8 50%, transparent 50%, transparent 70%, #94a3b8 70%);
                border-radius: 0 0 0 1.25rem;
            }
        `;
        const style = document.createElement('style');
        style.textContent = css;
        document.head.appendChild(style);
        
        const container = document.createElement('div');
        container.id = 'dduk-floating-chatbot-container';
        container.innerHTML = `
            <div class="dduk-chat-trigger" id="dduk-floating-chatbot-trigger" title="AI 업무지원 포털">
                <i data-lucide="bot" style="width: 1.5rem; height: 1.5rem;"></i>
            </div>
            <div class="dduk-chat-panel" id="dduk-floating-chatbot-panel">
                <div class="dduk-chat-header">
                    <div class="dduk-chat-header-info">
                        <div class="dduk-chat-header-dot"></div>
                        <span class="dduk-chat-header-title">DDUK AI Copilot</span>
                    </div>
                    <button class="dduk-chat-header-close" id="dduk-floating-chatbot-close" aria-label="닫기">
                        <i data-lucide="x" style="width: 1.1rem; height: 1.1rem;"></i>
                    </button>
                </div>
                
                <div class="dduk-portal-tabs">
                    <button class="dduk-portal-tab active" data-tab="chatbot">AI 챗봇</button>
                    <button class="dduk-portal-tab" data-tab="anomaly">이상 감지</button>
                    <button class="dduk-portal-tab" data-tab="prediction">예측 분석</button>
                    <button class="dduk-portal-tab" data-tab="rpa">RPA 제어</button>
                </div>
                
                <!-- AI 챗봇 컨텐츠 -->
                <div class="dduk-portal-content active" id="dduk-portal-content-chatbot">
                    <div class="dduk-chat-messages" id="dduk-floating-chatbot-messages">
                        <div class="dduk-chat-bubble bot">안녕하세요! 뚝 ERP AI 어시스턴트입니다. 무엇이든 물어보세요!</div>
                    </div>
                    <div class="dduk-chat-input-area">
                        <input type="text" class="dduk-chat-input" id="dduk-floating-chatbot-input" placeholder="메시지를 입력하세요..." autocomplete="off">
                        <button class="dduk-chat-send-btn" id="dduk-floating-chatbot-send" aria-label="보내기">
                            <i data-lucide="send" style="width: 1rem; height: 1rem;"></i>
                        </button>
                    </div>
                </div>
                
                <!-- 이상 감지 컨텐츠 -->
                <div class="dduk-portal-content" id="dduk-portal-content-anomaly">
                    <div class="dduk-portal-scroll-area">
                        <div class="dduk-anomaly-list" id="dduk-floating-anomaly-list"></div>
                    </div>
                </div>
                
                <!-- 예측 분석 컨텐츠 -->
                <div class="dduk-portal-content" id="dduk-portal-content-prediction">
                    <div class="dduk-portal-scroll-area">
                        <div class="dduk-prediction-list" id="dduk-floating-prediction-list"></div>
                    </div>
                </div>

                <!-- RPA 제어 컨텐츠 -->
                <div class="dduk-portal-content" id="dduk-portal-content-rpa">
                    <div class="dduk-portal-scroll-area" id="dduk-floating-rpa-scroll-area">
                        <div id="dduk-floating-rpa-container"></div>
                    </div>
                </div>
                
                <!-- 좌측 하단 크기 조절 핸들 -->
                <div class="dduk-portal-resizer-left" id="dduk-portal-resizer-left"></div>
            </div>
        `;
        document.body.appendChild(container);

        // 드래그 가능한 챗 트리거 구현 (드래그와 클릭의 명확한 구분)
        const triggerEl = document.getElementById('dduk-floating-chatbot-trigger');
        let triggerDragging = false;
        let tStartX, tStartY, tInitLeft, tInitTop;

        const onTriggerMove = (e) => {
            const clientX = e.type.startsWith('touch') ? e.touches[0].clientX : e.clientX;
            const clientY = e.type.startsWith('touch') ? e.touches[0].clientY : e.clientY;
            const dx = clientX - tStartX;
            const dy = clientY - tStartY;

            if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                triggerDragging = true;
                if (e.cancelable) e.preventDefault();
                triggerEl.style.right = 'auto';
                triggerEl.style.left = (tInitLeft + dx) + 'px';
                triggerEl.style.top = (tInitTop + dy) + 'px';

                alignPanelToTrigger();
            }
        };

        const onTriggerUp = (e) => {
            if (e.type.startsWith('touch')) {
                document.removeEventListener('touchmove', onTriggerMove);
                document.removeEventListener('touchend', onTriggerUp);
            } else {
                document.removeEventListener('mousemove', onTriggerMove);
                document.removeEventListener('mouseup', onTriggerUp);
            }

            if (!triggerDragging) {
                toggleFloatingChatbot();
            }
        };

        const onTriggerDown = (e) => {
            triggerDragging = false;
            const clientX = e.type.startsWith('touch') ? e.touches[0].clientX : e.clientX;
            const clientY = e.type.startsWith('touch') ? e.touches[0].clientY : e.clientY;
            tStartX = clientX;
            tStartY = clientY;

            const rect = triggerEl.getBoundingClientRect();
            tInitLeft = rect.left;
            tInitTop = rect.top;

            if (e.type.startsWith('touch')) {
                document.addEventListener('touchmove', onTriggerMove, { passive: false });
                document.addEventListener('touchend', onTriggerUp);
            } else {
                document.addEventListener('mousemove', onTriggerMove);
                document.addEventListener('mouseup', onTriggerUp);
            }
        };

        triggerEl.addEventListener('mousedown', onTriggerDown);
        triggerEl.addEventListener('touchstart', onTriggerDown, { passive: true });

        document.getElementById('dduk-floating-chatbot-close').addEventListener('click', () => toggleFloatingChatbot(false));
        
        document.querySelectorAll('.dduk-portal-tab').forEach(tabBtn => {
            tabBtn.addEventListener('click', () => {
                const tabName = tabBtn.getAttribute('data-tab');
                switchPortalTab(tabName);
            });
        });
        
        const sendBtn = document.getElementById('dduk-floating-chatbot-send');
        const inputField = document.getElementById('dduk-floating-chatbot-input');
        
        sendBtn.addEventListener('click', () => {
            const query = inputField.value.trim();
            sendFloatingChatMessage(query);
        });
        
        inputField.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                const query = inputField.value.trim();
                sendFloatingChatMessage(query);
            }
        });
        
        // Custom 드래그 리사이즈 바인딩 (좌측 구석 핸들을 타겟팅)
        const resizerLeft = document.getElementById('dduk-portal-resizer-left');
        const panel = document.getElementById('dduk-floating-chatbot-panel');
        
        if (resizerLeft && panel) {
            let startWidth, startHeight, startX, startY, startRight;
            
            resizerLeft.addEventListener('mousedown', (e) => {
                e.preventDefault();
                const rect = panel.getBoundingClientRect();
                startWidth = parseInt(document.defaultView.getComputedStyle(panel).width, 10);
                startHeight = parseInt(document.defaultView.getComputedStyle(panel).height, 10);
                startX = e.clientX;
                startY = e.clientY;
                startRight = Math.max(0, window.innerWidth - rect.right);
                
                document.documentElement.addEventListener('mousemove', doResize);
                document.documentElement.addEventListener('mouseup', stopResize);
            });
            
            function doResize(e) {
                e.preventDefault();
                const newWidth = Math.max(320, Math.min(window.innerWidth * 0.95, startWidth - (e.clientX - startX)));
                const newHeight = Math.max(400, Math.min(window.innerHeight * 0.8, startHeight + (e.clientY - startY)));
                
                panel.style.left = 'auto';
                panel.style.right = startRight + 'px';
                panel.style.width = newWidth + 'px';
                panel.style.height = newHeight + 'px';
            }
            
            function stopResize() {
                document.documentElement.removeEventListener('mousemove', doResize);
                document.documentElement.removeEventListener('mouseup', stopResize);
            }
        }
        
        renderAnomalyList();
        renderPredictionList();
        
        if (window.lucide) lucide.createIcons();
    }

    // Expose to window for delegation & orchestrator
    window.DDUK_FLOATING_PORTAL = {
        initAICopilotPortal,
        toggleFloatingChatbot,
        openFloatingChatbotWithQuery,
        switchPortalTab,
        renderAnomalyList,
        renderPredictionList
    };
})();
