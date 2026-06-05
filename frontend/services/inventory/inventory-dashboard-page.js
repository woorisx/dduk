(function () {
    let assetChartInstance = null;
    let trendChartInstance = null;
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

    function rpaChipMeta(status) {
        switch (status) {
            case 'READY':
                return { label: '결과 확인 가능', className: 'rpa-chip ready' };
            case 'ANALYSIS_PARTIAL':
                return { label: '부분 분석 완료', className: 'rpa-chip ready' };
            case 'EMPTY_RESULT':
                return { label: '리스크 없음', className: 'rpa-chip ready' };
            case 'MISSING_FILE':
                return { label: '결과 누락', className: 'rpa-chip missing' };
            default:
                return { label: rpaTriggerPending ? '실행 요청 중' : '대기', className: 'rpa-chip waiting' };
        }
    }

    async function loadDashboard() {
        const pageMessage = el('page-message');
        try {
            const response = await InventoryService.getDashboardStats();
            if (!response || response.status !== 'success') {
                throw new Error(response?.message || '재고 대시보드를 불러오지 못했어.');
            }

            const stats = response.data || {};
            setText('base-time', `기준 ${new Date().toLocaleString('ko-KR')}`);
            setText('total-qty', number(stats.totalQuantity));
            setText('total-value', money(stats.totalValue));
            setText('low-stock-count', number(stats.lowStockCount));
            setText('pending-transfer-count', number(stats.pendingTransferCount));
            setText('outbound-volume', number(stats.outboundVolume30Days));

            renderDistribution(stats.warehouseDistribution || []);
            renderRecentMovements(stats.recentMovements || []);
            renderDashboardCharts(stats);
            renderShortageRpa(stats.inventoryShortageRpa || {});
            if (session?.role && ['ADMIN', 'INVENTORY', 'HR'].includes(session.role)) {
                loadRpaHistory();
            }

            window.ddukApi?.showToast?.('최신 데이터로 대시보드가 갱신되었습니다.', 'success');
        } catch (error) {
            if (pageMessage) {
                pageMessage.textContent = error.message || '재고 대시보드를 불러오지 못했어.';
                pageMessage.className = 'mt-1 text-sm font-semibold text-rose-600';
            }
            ['total-qty', 'total-value', 'low-stock-count', 'pending-transfer-count', 'outbound-volume'].forEach((id) => {
                setText(id, '-');
            });
        } finally {
            if (window.lucide) {
                window.lucide.createIcons();
            }
        }
    }

    function renderDistribution(items) {
        const target = el('distribution-list');
        if (!target) {
            return;
        }
        if (!items.length) {
            target.innerHTML = '<div class="dist-card"><div class="text-sm font-bold text-slate-400">표시할 창고 재고가 없어.</div></div>';
            return;
        }

        target.innerHTML = items.map((item) => `
            <div class="dist-card">
                <div class="flex items-center gap-3">
                    <div class="h-10 w-2.5 rounded-full bg-indigo-500"></div>
                    <div>
                        <p class="text-sm font-black text-slate-800">${escapeHtml(item.warehouseName)}</p>
                        <p class="text-xs font-semibold text-slate-400">자산 ${money(item.totalValue)}</p>
                    </div>
                </div>
                <div class="text-right">
                    <p class="text-base font-black text-indigo-600">${number(item.totalStock)}</p>
                    <p class="text-[10px] font-black uppercase text-slate-400">수량</p>
                </div>
            </div>
        `).join('');
    }

    function renderRecentMovements(items) {
        const target = el('recent-movements');
        if (!target) {
            return;
        }
        if (!items.length) {
            target.innerHTML = '<p class="py-4 text-center text-xs font-bold text-slate-400">최근 재고 변경 이력이 없어.</p>';
            return;
        }

        target.innerHTML = items.slice(0, 5).map((item) => `
            <div class="movement-card">
                <div>
                    <p class="text-sm font-black text-slate-800">${escapeHtml(item.itemName)}</p>
                    <p class="text-[11px] font-semibold text-slate-400">${escapeHtml(item.warehouseName)} | ${escapeHtml(item.referenceNo || '-')}</p>
                </div>
                <div class="text-right">
                    <p class="text-sm font-black ${String(item.movementType || '').includes('OUT') ? 'text-rose-600' : 'text-emerald-600'}">
                        ${String(item.movementType || '').includes('OUT') ? '-' : '+'}${number(item.quantity)}
                    </p>
                    <p class="text-[10px] font-black uppercase text-slate-400">${escapeHtml(translateType(item.movementType))}</p>
                </div>
            </div>
        `).join('');
    }

    function renderShortageRpa(block) {
        if (!hasRpaPanel()) {
            return;
        }
        const chip = el('rpa-status-chip');
        const body = el('rpa-items-body');
        if (!chip || !body) {
            return;
        }

        const chipMeta = rpaChipMeta(block.status);
        chip.className = chipMeta.className;
        chip.textContent = chipMeta.label;

        setText('rpa-message', block.message || '분석 결과가 아직 없어.');
        setText('rpa-vendor-name', block.vendorName || '내부 분석기');
        setText('rpa-collected-at', formatDateTime(block.latestCollectedAt));
        setText('rpa-matched-count', number(block.agingCount || 0));
        setText('rpa-erp-count', number(block.expiryCount || 0));
        setText('rpa-alert-count', number(block.totalRiskCount || 0));
        setText('rpa-match-count-card', number(block.totalRiskCount || 0));

        const items = Array.isArray(block.items) ? block.items : [];
        if (!items.length) {
            body.innerHTML = '<tr><td colspan="5"><div class="warn-empty">분석된 리스크 재고 항목이 없어.</div></td></tr>';
            return;
        }

        body.innerHTML = items.map((item) => `
            <tr>
                <td class="font-black text-slate-900">${escapeHtml(item.itemName || '-')}</td>
                <td class="font-bold text-slate-600">${escapeHtml(item.warehouseName || '-')}</td>
                <td class="font-bold text-slate-700">${item.availableStock == null ? '-' : number(item.availableStock)}</td>
                <td class="font-bold text-amber-700">${escapeHtml(item.statusLabel || '장기 체화')}</td>
                <td class="font-bold text-slate-700">${escapeHtml(item.recommendedAction || '-')}</td>
            </tr>
        `).join('');
    }

    function renderRpaHistory(items) {
        const target = el('rpa-history-list');
        if (!target) {
            return;
        }
        if (!Array.isArray(items) || !items.length) {
            target.innerHTML = '<div class="warn-empty">아직 최근 실행 이력이 없어.</div>';
            return;
        }

        target.innerHTML = items.map((item) => `
            <div class="flex items-start justify-between gap-3 rounded-xl border border-amber-100 bg-white/80 px-4 py-3">
                <div class="min-w-0">
                    <div class="flex flex-wrap items-center gap-2">
                        <span class="text-xs font-black text-slate-900">${escapeHtml(item.status || '-')}</span>
                        <span class="text-[11px] font-bold text-slate-500">${escapeHtml(item.taskId || '-')}</span>
                    </div>
                    <p class="mt-1 text-xs font-semibold text-slate-600">${escapeHtml(formatDateTime(item.requestedAt))}</p>
                    <p class="mt-1 text-xs font-semibold text-rose-600">${escapeHtml(item.errorMessage || '')}</p>
                </div>
                <button type="button" onclick="triggerInventoryShortageRpa()" class="rounded-lg border border-amber-200 bg-white px-3 py-1.5 text-xs font-black text-amber-900 hover:bg-amber-50">재실행</button>
            </div>
        `).join('');
    }

    async function loadRpaHistory() {
        const target = el('rpa-history-list');
        if (!target) {
            return;
        }
        if (!session || !['ADMIN', 'INVENTORY', 'HR'].includes(session.role)) {
            target.innerHTML = '<div class="warn-empty">최근 RPA 실행 이력은 관리자만 볼 수 있어.</div>';
            return;
        }

        try {
            const response = await window.ddukApi.get('/api/v1/admin/tasks?taskType=RPA&actionName=check_inventory_shortage&size=5&sort=requestedAt,desc');
            const page = response?.data;
            renderRpaHistory(Array.isArray(page?.content) ? page.content : []);
        } catch (error) {
            target.innerHTML = `<div class="warn-empty">${escapeHtml(error.message || '최근 실행 이력을 불러오지 못했어.')}</div>`;
        }
    }

    async function triggerInventoryShortageRpa() {
        if (!session || !['ADMIN', 'INVENTORY', 'HR'].includes(session.role)) {
            window.ddukApi?.showToast?.('RPA 실행은 관리자만 가능해.', 'warning');
            return;
        }

        const button = el('rpa-trigger-button');
        const pageMessage = el('page-message');
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
            const response = await window.ddukApi.post('/api/v1/admin/rpa/trigger', { taskType: 'INVENTORY_SHORTAGE' });
            if (pageMessage) {
                pageMessage.textContent = `장기 체화 및 임박 재고 분석을 요청했어. Task ID: ${response.data?.taskId || '-'}`;
                pageMessage.className = 'mt-1 text-sm font-semibold text-amber-700';
            }
            renderShortageRpa({
                status: 'WAITING',
                vendorName: '내부 분석기',
                message: '분석 실행을 요청했어. 완료되면 재고 리스크 분석 결과가 갱신돼.',
                items: []
            });
            loadRpaHistory();
            window.setTimeout(loadDashboard, 2000);
        } catch (error) {
            if (pageMessage) {
                pageMessage.textContent = error.message || '재고 리스크 분석 실행 요청이 실패했어.';
                pageMessage.className = 'mt-1 text-sm font-semibold text-rose-600';
            }
        } finally {
            rpaTriggerPending = false;
            button.disabled = !['ADMIN', 'INVENTORY', 'HR'].includes(session.role);
            button.innerHTML = '<i data-lucide="radar" class="w-4 h-4"></i> 분석 실행';
            if (window.lucide) {
                window.lucide.createIcons();
            }
        }
    }

    function renderDashboardCharts(stats) {
        if (assetChartInstance) {
            assetChartInstance.destroy();
            assetChartInstance = null;
        }
        if (trendChartInstance) {
            trendChartInstance.destroy();
            trendChartInstance = null;
        }

        const assetCanvas = el('asset-ratio-chart');
        const trendCanvas = el('outbound-trend-chart');
        if (!assetCanvas || !trendCanvas || !window.Chart) {
            return;
        }

        const distribution = Array.isArray(stats.warehouseDistribution) ? stats.warehouseDistribution : [];
        const whNames = distribution.map((item) => item.warehouseName);
        const whValues = distribution.map((item) => Number(item.totalValue || 0));

        assetChartInstance = new Chart(assetCanvas.getContext('2d'), {
            type: 'doughnut',
            data: {
                labels: whNames.length ? whNames : ['창고 없음'],
                datasets: [{
                    data: whValues.length ? whValues : [0],
                    backgroundColor: [
                        'rgba(79, 70, 229, 0.82)',
                        'rgba(16, 185, 129, 0.82)',
                        'rgba(245, 158, 11, 0.82)',
                        'rgba(239, 68, 68, 0.82)',
                        'rgba(107, 114, 128, 0.82)'
                    ],
                    borderColor: '#ffffff',
                    borderWidth: 2
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { position: 'bottom', labels: { boxWidth: 12, font: { size: 10 } } }
                }
            }
        });

        const dateStats = {};
        for (let index = 29; index >= 0; index -= 1) {
            const date = new Date();
            date.setDate(date.getDate() - index);
            const key = date.toISOString().split('T')[0];
            dateStats[key] = { inbound: 0, outbound: 0 };
        }

        (stats.recentMovements || []).forEach((item) => {
            const key = String(item.createdAt || '').split('T')[0];
            if (!dateStats[key]) {
                return;
            }
            if (String(item.movementType || '').includes('IN')) {
                dateStats[key].inbound += Number(item.quantity || 0);
            } else {
                dateStats[key].outbound += Number(item.quantity || 0);
            }
        });

        trendChartInstance = new Chart(trendCanvas.getContext('2d'), {
            type: 'line',
            data: {
                labels: Object.keys(dateStats).map((key) => key.slice(5)),
                datasets: [
                    {
                        label: '입고',
                        data: Object.values(dateStats).map((value) => value.inbound),
                        borderColor: 'rgb(16, 185, 129)',
                        backgroundColor: 'rgba(16, 185, 129, 0.06)',
                        tension: 0.3,
                        fill: true,
                        pointRadius: 2
                    },
                    {
                        label: '출고',
                        data: Object.values(dateStats).map((value) => value.outbound),
                        borderColor: 'rgb(239, 68, 68)',
                        backgroundColor: 'rgba(239, 68, 68, 0.06)',
                        tension: 0.3,
                        fill: true,
                        pointRadius: 2
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                scales: { y: { beginAtZero: true } },
                plugins: { legend: { position: 'top', labels: { boxWidth: 12 } } }
            }
        });
    }

    async function loadPendingTransfers() {
        const list = el('pending-transfer-list');
        const empty = el('pending-empty');
        if (!list || !empty) {
            return;
        }

        list.innerHTML = '<tr><td colspan="4" class="py-6 text-center text-xs font-bold text-slate-400">로딩 중...</td></tr>';

        try {
            const response = await InventoryService.getTransfers({ status: 'PENDING' });
            const items = Array.isArray(response.data) ? response.data : [];
            if (!items.length) {
                list.innerHTML = '';
                empty.classList.remove('hidden');
                return;
            }

            empty.classList.add('hidden');
            list.innerHTML = items.slice(0, 5).map((item) => `
                <tr class="border-b border-slate-100">
                    <td class="py-3 text-sm font-black text-indigo-600">${escapeHtml(item.transferNo)}</td>
                    <td class="py-3 text-sm text-slate-600">${escapeHtml(item.sourceWarehouseName)} → ${escapeHtml(item.targetWarehouseName)}</td>
                    <td class="py-3 text-sm text-slate-500">${escapeHtml(item.requestedByName)}</td>
                    <td class="py-3 text-center">
                        <button type="button" onclick="approveTransfer(${item.id})" class="rounded-md bg-amber-500 px-2 py-1 text-[11px] font-black text-white hover:bg-amber-600">승인</button>
                    </td>
                </tr>
            `).join('');
        } catch (error) {
            list.innerHTML = '';
            empty.classList.remove('hidden');
        }
    }

    async function approveTransfer(id) {
        if (!window.confirm('해당 창고 이동 요청을 승인하시겠습니까?')) {
            return;
        }
        try {
            const response = await InventoryService.approveTransfer(id);
            if (response.status === 'success') {
                window.ddukApi?.showToast?.('이동 요청을 승인했어.', 'success');
                await loadDashboard();
                await loadPendingTransfers();
            }
        } catch (error) {
            window.ddukApi?.showToast?.(error.message || '이동 승인에 실패했어.', 'error');
        }
    }

    function translateType(type) {
        return {
            INBOUND: '입고',
            OUTBOUND: '출고',
            TRANSFER_IN: '이동 입고',
            TRANSFER_OUT: '이동 출고',
            ADJUSTMENT_IN: '조정 입고',
            ADJUSTMENT_OUT: '조정 출고'
        }[type] || type || '-';
    }

    document.addEventListener('DOMContentLoaded', async () => {
        session = window.ddukSession?.requireRole?.(['ADMIN', 'INVENTORY', 'HR']);
        if (!session) {
            return;
        }

        const triggerButton = el('rpa-trigger-button');
        const refreshButton = el('rpa-history-refresh');
        const historyList = el('rpa-history-list');

        if (triggerButton) {
            triggerButton.addEventListener('click', triggerInventoryShortageRpa);
        }
        if (refreshButton) {
            refreshButton.addEventListener('click', loadRpaHistory);
        }
        if (triggerButton && !['ADMIN', 'INVENTORY', 'HR'].includes(session.role)) {
            triggerButton.disabled = true;
            triggerButton.title = 'RPA 실행은 허용된 사용자만 가능해.';
            if (historyList) {
                historyList.innerHTML = '<div class="warn-empty">최근 RPA 실행 이력을 볼 수 없어.</div>';
            }
        }

        if (window.lucide) {
            window.lucide.createIcons();
        }
        await loadDashboard();
        await loadPendingTransfers();
        window.setInterval(loadDashboard, 10000);
    });

    window.approveTransfer = approveTransfer;
    window.triggerInventoryShortageRpa = triggerInventoryShortageRpa;
})();
