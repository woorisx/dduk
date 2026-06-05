(function () {
    let session = null;
    let rpaTriggerPending = false;

    function el(id) {
        return document.getElementById(id);
    }

    function hasRpaPanel() {
        return Boolean(el('rpa-status-chip'));
    }

    function number(value) {
        return Number(value || 0).toLocaleString('ko-KR');
    }

    function money(value) {
        if (value === null || value === undefined || value === '') {
            return '-';
        }
        return Number(value || 0).toLocaleString('ko-KR', {
            style: 'currency',
            currency: 'KRW',
            maximumFractionDigits: 0
        });
    }

    function percent(value) {
        return `${Number(value || 0)}%`;
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

    function setText(id, value) {
        const target = el(id);
        if (target) {
            target.textContent = value;
        }
    }

    function formatDateTime(value) {
        if (!value) {
            return '-';
        }
        const parsed = new Date(value);
        return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString('ko-KR');
    }

    function signedMoney(value) {
        if (value === null || value === undefined || value === '') {
            return '-';
        }
        const numeric = Number(value || 0);
        return `${numeric > 0 ? '+' : ''}${money(numeric)}`;
    }

    function statusText(status) {
        return {
            DRAFT: '초안',
            ORDERED: '발주 요청',
            PENDING: '발주 요청',
            REQUESTED: '발주 요청',
            APPROVED: '승인 완료',
            SENT_TO_VENDOR: '거래처 발송',
            INBOUND_DELAY: '입고 지연',
            RECEIVING: '입고 중',
            RECEIVED: '입고 완료',
            COMPLETED: '발주 완료',
            CANCELLED: '취소'
        }[status] || status || '-';
    }

    function statusVisual(status) {
        return {
            DRAFT: { color: '#64748b', bg: '#f1f5f9' },
            ORDERED: { color: '#4f46e5', bg: '#eef2ff' },
            PENDING: { color: '#4f46e5', bg: '#eef2ff' },
            REQUESTED: { color: '#4f46e5', bg: '#eef2ff' },
            APPROVED: { color: '#059669', bg: '#ecfdf5' },
            SENT_TO_VENDOR: { color: '#0d9488', bg: '#f0fdfa' },
            INBOUND_DELAY: { color: '#e11d48', bg: '#fff1f2' },
            RECEIVING: { color: '#ea580c', bg: '#fff7ed' },
            RECEIVED: { color: '#0284c7', bg: '#eff6ff' },
            COMPLETED: { color: '#16a34a', bg: '#f0fdf4' },
            CANCELLED: { color: '#b91c1c', bg: '#fef2f2' }
        }[status] || { color: '#475569', bg: '#f1f5f9' };
    }

    function rpaChipMeta(status) {
        switch (status) {
            case 'READY':
                return { label: '비교 가능', className: 'rpa-chip ready' };
            case 'ERP_BASELINE_ONLY':
                return { label: '부분 비교', className: 'rpa-chip waiting' };
            case 'NO_ERP_BASELINE':
            case 'MISSING_FILE':
                return { label: '기준 부족', className: 'rpa-chip missing' };
            case 'EMPTY_RESULT':
                return { label: '결과 비어 있음', className: 'rpa-chip missing' };
            default:
                return { label: rpaTriggerPending ? '실행 요청 중' : '대기', className: 'rpa-chip waiting' };
        }
    }
    function matchTypeText(value) {
        return {
            EXACT: '정확 매칭',
            LOOSE: '유사 매칭',
            COLLECTED_ONLY: '수집 데이터만 있음'
        }[value] || '비교';
    }

    function clampPercent(value) {
        return Math.max(0, Math.min(100, Number(value || 0)));
    }

    function renderProgress(stats) {
        const target = el('progress-list');
        if (!target) {
            return;
        }
        const items = [
            ['발주 완료율', stats.orderCompletionRate || 0, '#16a34a', `${number(stats.completedOrderCount)}건 완료`],
            ['입고 완료율', stats.receivingCompletionRate || 0, '#0284c7', `${number(stats.receivedCount)}건 완료`],
            ['입고 지연율', stats.delayRate || 0, '#e11d48', `${number(stats.delayedReceivingCount)}건 지연`]
        ];

        target.innerHTML = items.map(([label, value, color, note]) => {
            const rate = clampPercent(value);
            return `
                <div class="progress-gauge-row">
                    <div class="progress-gauge" style="--gauge-color:${color};--gauge-stop:${rate}%">
                        <strong>${rate}%</strong>
                    </div>
                    <div>
                        <div class="progress-title">${label}</div>
                        <div class="progress-note">${note}</div>
                    </div>
                </div>
            `;
        }).join('');
    }

    function buildDisplayMonths(rows) {
        const realRows = Array.isArray(rows) ? rows : [];
        const rowMap = new Map(realRows.map((row) => [row.month, { ...row, demo: false }]));
        const today = new Date();
        const currentCount = Math.max(...realRows.map((row) => Number(row.count || 0)), 6);
        const currentAmount = Math.max(...realRows.map((row) => Number(row.amount || 0)), 12000000);

        return Array.from({ length: 6 }, (_, index) => {
            const date = new Date(today.getFullYear(), today.getMonth() - 5 + index, 1);
            const month = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
            if (rowMap.has(month)) {
                return rowMap.get(month);
            }
            const wave = [0.58, 0.72, 0.65, 0.84, 0.76, 0.92][index];
            return {
                month,
                count: Math.max(1, Math.round(currentCount * wave)),
                amount: Math.round(currentAmount * wave),
                demo: true
            };
        });
    }

    function renderMonthlyChart(rows) {
        const chart = el('monthly-chart');
        if (!chart) {
            return;
        }
        const displayRows = buildDisplayMonths(rows);
        if (!displayRows.length) {
            chart.innerHTML = '<div class="self-center w-full text-center text-sm font-bold text-slate-400">표시할 월별 데이터가 없어.</div>';
            return;
        }

        const max = Math.max(...displayRows.map((row) => Number(row.count || 0)), 1);
        chart.innerHTML = displayRows.map((row) => {
            const height = Math.max(8, Number(row.count || 0) / max * 210);
            return `
                <div class="flex-1 h-full flex flex-col justify-end items-center gap-2 min-w-[4rem]">
                    <div class="text-xs font-bold text-slate-500">${number(row.count)}건</div>
                    <div class="chart-bar w-full bg-indigo-500" style="height:${height}px"></div>
                    <div class="text-xs font-extrabold text-slate-600">${escapeHtml(row.month)}</div>
                    <div class="text-[11px] font-bold text-slate-400">${money(row.amount)}${row.demo ? ' · 예시' : ''}</div>
                </div>
            `;
        }).join('');
    }

    function renderStatusList(statusCounts, total) {
        const target = el('status-list');
        if (!target) {
            return;
        }
        const entries = Object.entries(statusCounts || {});
        if (!entries.length) {
            target.innerHTML = '<p class="text-sm font-bold text-slate-400">표시할 상태 데이터가 없어.</p>';
            return;
        }

        const stack = entries.map(([status, count]) => {
            const rate = total ? Math.round(count * 1000 / total) / 10 : 0;
            const visual = statusVisual(status);
            return `<div class="status-stack-segment" title="${escapeHtml(statusText(status))} ${rate}%" style="width:${Math.max(rate, 1)}%;background:${visual.color}"></div>`;
        }).join('');

        const legend = entries.map(([status, count]) => {
            const rate = total ? Math.round(count * 1000 / total) / 10 : 0;
            const visual = statusVisual(status);
            return `
                <div class="status-legend-row">
                    <span class="status-dot-chip" style="background:${visual.color};--status-bg:${visual.bg}"></span>
                    <div>
                        <div class="status-label-text">${escapeHtml(statusText(status))}</div>
                        <div class="status-mini-track"><div class="status-mini-fill" style="width:${Math.min(100, rate)}%;background:${visual.color}"></div></div>
                    </div>
                    <div class="status-count-text">${number(count)}건 · ${rate}%</div>
                </div>
            `;
        }).join('');

        target.innerHTML = `<div class="status-stack">${stack}</div><div class="status-legend">${legend}</div>`;
    }

    function renderRecentOrders(rows) {
        const target = el('recent-orders');
        if (!target) {
            return;
        }
        if (!Array.isArray(rows) || !rows.length) {
            target.innerHTML = '<tr><td colspan="5" class="p-6 text-center text-sm font-bold text-slate-400">최근 발주가 아직 없어.</td></tr>';
            return;
        }

        target.innerHTML = rows.map((order) => `
            <tr class="border-t border-slate-100">
                <td class="p-3 font-extrabold text-indigo-700">${escapeHtml(order.purchaseOrderNo)}</td>
                <td class="p-3 font-bold text-slate-700">${escapeHtml(order.vendorName)}</td>
                <td class="p-3"><span class="status-pill">${escapeHtml(statusText(order.status))}</span></td>
                <td class="p-3 text-sm text-slate-500">${escapeHtml(order.expectedDate || '-')}</td>
                <td class="p-3 text-right font-extrabold text-slate-900">${money(order.totalAmount)}</td>
            </tr>
        `).join('');
    }

    function renderRpaComparison(block) {
        if (!hasRpaPanel()) {
            return;
        }
        const chip = el('rpa-status-chip');
        const body = el('rpa-items-body');
        if (!chip || !body) {
            return;
        }

        const comparison = block || {};
        const chipMeta = rpaChipMeta(comparison.status);
        chip.className = chipMeta.className;
        chip.textContent = chipMeta.label;

        setText('rpa-message', comparison.message || '아직 비교 결과가 없어.');
        setText('rpa-vendor-name', comparison.vendorName || '-');
        setText('rpa-collected-at', formatDateTime(comparison.latestCollectedAt));
        setText('rpa-compared-count', number(comparison.comparedItemsCount || comparison.collectedItemsCount || 0));
        setText('rpa-collected-price', money(comparison.latestCollectedAveragePrice));
        setText('rpa-erp-price', money(comparison.erpBaselineAveragePrice));
        setText('rpa-delta-price', signedMoney(comparison.priceDelta));
        setText('rpa-collected-note', `${number(comparison.collectedItemsCount || 0)}건 수집 기준`);
        setText('rpa-erp-note', comparison.erpBaselineAveragePrice == null ? 'ERP 기준 가격 없음' : 'ERP 평균 단가');
        setText('rpa-delta-note', comparison.priceDelta == null ? '차이 계산 대기' : '수집 평균 - ERP 평균');

        const items = Array.isArray(comparison.items) ? comparison.items : [];
        if (!items.length) {
            body.innerHTML = `
                <tr>
                    <td colspan="5">
                        <div class="rpa-empty">비교 가능한 RPA 가격 데이터가 아직 없어.</div>
                    </td>
                </tr>
            `;
            return;
        }

        body.innerHTML = items.map((item) => `
            <tr>
                <td class="font-bold text-slate-900">${escapeHtml(item.productName || '-')}</td>
                <td class="font-black text-slate-900">${money(item.collectedPrice)}</td>
                <td class="font-bold text-slate-700">${money(item.erpPrice)}</td>
                <td class="font-black ${Number(item.priceDelta || 0) > 0 ? 'text-rose-600' : 'text-emerald-700'}">${signedMoney(item.priceDelta)}</td>
                <td class="font-bold text-slate-600">${escapeHtml(matchTypeText(item.matchType))}</td>
            </tr>
        `).join('');
    }

    function renderRpaHistory(items) {
        const target = el('rpa-history-list');
        if (!target) {
            return;
        }
        if (!Array.isArray(items) || !items.length) {
            target.innerHTML = '<div class="rpa-empty">아직 최근 실행 이력이 없어.</div>';
            return;
        }

        target.innerHTML = items.map((item) => `
            <div class="rounded-xl border border-slate-100 bg-white px-3 py-3">
                <div class="flex items-start justify-between gap-3">
                    <div class="min-w-0">
                        <div class="font-mono text-xs font-black text-slate-700">${escapeHtml(item.taskId || '-')}</div>
                        <div class="mt-1 text-xs font-bold text-slate-500">${escapeHtml(item.status || '-')} · ${escapeHtml(formatDateTime(item.requestedAt))}</div>
                        <div class="mt-1 text-xs font-semibold text-rose-600">${escapeHtml(item.errorMessage || '')}</div>
                    </div>
                    <button type="button" class="rounded-lg border border-slate-200 px-2.5 py-1 text-[11px] font-black text-slate-700 hover:bg-slate-50" onclick="triggerPurchasePriceRpa()">재실행</button>
                </div>
            </div>
        `).join('');
    }

    async function loadRpaHistory() {
        const target = el('rpa-history-list');
        if (!target) {
            return;
        }
        if (!session || !['ADMIN', 'INVENTORY', 'HR'].includes(session.role)) {
            target.innerHTML = '<div class="rpa-empty">최근 RPA 실행 이력은 관리자만 볼 수 있어.</div>';
            return;
        }

        try {
            const payload = await window.ddukApi.get('/api/v1/admin/tasks?taskType=RPA&actionName=collect_purchase_orders&size=5&sort=requestedAt,desc');
            renderRpaHistory(payload.data?.content || []);
        } catch (error) {
            target.innerHTML = `<div class="rpa-empty">최근 실행 이력을 불러오지 못했어. ${escapeHtml(error.message)}</div>`;
        }
    }

    async function triggerPurchasePriceRpa() {
        const message = el('message');
        if (!session || !['ADMIN', 'INVENTORY', 'HR'].includes(session.role)) {
            if (message) {
                message.textContent = 'RPA 실행은 허용된 사용자만 가능해.';
                message.className = 'text-sm text-slate-600 mt-1 font-semibold';
            }
            return;
        }

        const button = el('rpa-trigger-button');
        if (!button) {
            return;
        }

        rpaTriggerPending = true;
        button.disabled = true;
        button.innerHTML = '<i data-lucide="loader-circle" class="w-4 h-4"></i> 실행 요청 중';
        if (window.lucide) {
            window.lucide.createIcons();
        }

        try {
            const payload = await window.ddukApi.post('/api/v1/admin/rpa/trigger', { taskType: 'PURCHASE_PRICE' });
            if (message) {
                message.textContent = `구매 가격 수집을 요청했어. Task ID: ${payload.data?.taskId || '-'}`;
                message.className = 'text-sm text-slate-600 mt-1 font-semibold';
            }
            renderRpaComparison({
                status: 'WAITING',
                vendorName: '알림',
                message: 'RPA 실행을 요청했어. 완료되면 자동으로 비교 카드가 갱신돼.',
                items: []
            });
            window.setTimeout(loadDashboard, 2000);
        } catch (error) {
            if (message) {
                message.textContent = `RPA 실행 요청 실패: ${error.message}`;
                message.className = 'text-sm text-red-600 mt-1 font-semibold';
            }
        } finally {
            rpaTriggerPending = false;
            button.disabled = false;
            button.innerHTML = '<i data-lucide="bot" class="w-4 h-4"></i> 가격 수집 실행';
            if (window.lucide) {
                window.lucide.createIcons();
            }
        }
    }

    async function loadDashboard() {
        const message = el('message');
        try {
            const payload = await window.ddukApi.get(`/api/v1/inventory/purchase-dashboard/stats?_=${Date.now()}`);
            const stats = payload.data || {};

            setText('order-count', number(stats.orderCount));
            setText('completed-order-count', number(stats.completedOrderCount));
            setText('receiving-count', number(stats.receivingCount));
            setText('delay-count', number(stats.delayedReceivingCount));
            setText('received-count', number(stats.receivedCount));
            setText('order-rate', `완료율 ${percent(stats.orderCompletionRate)}`);
            setText('delay-rate', `지연율 ${percent(stats.delayRate)}`);
            setText('received-rate', `완료율 ${percent(stats.receivingCompletionRate)}`);
            setText('base-date', `기준일 ${stats.baseDate || '-'}`);

            renderProgress(stats);
            renderMonthlyChart(stats.monthlyOrders);
            renderStatusList(stats.statusCounts, Number(stats.orderCount || 0));
            renderRecentOrders(stats.recentOrders);
            renderRpaComparison(stats.rpaComparison);
            if (session && ['ADMIN', 'INVENTORY', 'HR'].includes(session.role)) {
                loadRpaHistory();
            }

            window.ddukApi?.showToast?.('최신 데이터로 대시보드가 갱신되었습니다.', 'success');
        } catch (error) {
            if (message) {
                message.textContent = `대시보드 조회 실패: ${error.message}`;
                message.className = 'text-sm text-red-600 mt-1 font-semibold';
            }
        } finally {
            if (window.lucide) {
                window.lucide.createIcons();
            }
        }
    }

    document.addEventListener('DOMContentLoaded', () => {
        session = window.ddukSession?.requireRole?.(['ADMIN', 'INVENTORY', 'HR'], { redirectToLogin: true });
        if (!session) {
            return;
        }

        const triggerButton = el('rpa-trigger-button');
        const refreshButton = el('rpa-history-refresh');
        const historyList = el('rpa-history-list');

        if (triggerButton) {
            triggerButton.addEventListener('click', triggerPurchasePriceRpa);
        }
        if (refreshButton) {
            refreshButton.addEventListener('click', loadRpaHistory);
        }
        if (triggerButton && !['ADMIN', 'INVENTORY', 'HR'].includes(session.role)) {
            triggerButton.disabled = true;
            triggerButton.title = 'RPA 실행은 허용된 사용자만 가능해.';
            if (historyList) {
                historyList.innerHTML = '<div class="rpa-empty">최근 RPA 실행 이력은 관리자만 볼 수 있어.</div>';
            }
        }

        if (window.lucide) {
            window.lucide.createIcons();
        }
        loadDashboard();
        window.setInterval(loadDashboard, 10000);
        document.addEventListener('visibilitychange', () => {
            if (!document.hidden) {
                loadDashboard();
            }
        });
    });

    window.triggerPurchasePriceRpa = triggerPurchasePriceRpa;
})();

