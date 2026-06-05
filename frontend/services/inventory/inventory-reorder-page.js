    let allRecommendations = [];
    let currentStatusFilter = '';
    let currentUrgencyFilter = '';

    document.addEventListener('DOMContentLoaded', async () => {
        lucide.createIcons();
        bindFilters();
        document.getElementById('refresh-btn').addEventListener('click', async () => {
            await loadRecommendations();
        });
        await loadRecommendations();
    });

    function bindFilters() {
        document.querySelectorAll('.filter-chip').forEach((button) => {
            button.addEventListener('click', () => {
                const filterType = button.dataset.filterType;
                const filterValue = button.dataset.filterValue || '';

                const isToggleOff = filterType === 'urgency' && currentUrgencyFilter === filterValue;

                document.querySelectorAll(`.filter-chip[data-filter-type="${filterType}"]`)
                    .forEach((chip) => chip.classList.remove('active'));

                if (filterType === 'status') {
                    button.classList.add('active');
                    currentStatusFilter = filterValue;
                } else if (filterType === 'urgency') {
                    currentUrgencyFilter = isToggleOff ? '' : filterValue;
                    if (!isToggleOff) {
                        button.classList.add('active');
                    }
                }

                renderRecommendations();
            });
        });
    }

    async function loadRecommendations() {
        renderSkeleton();
        try {
            let recommendations = [];
            try {
                const response = await InventoryService.getPurchaseRecommendations();
                if (response.status === 'success' && response.data && Array.isArray(response.data.items)) {
                    recommendations = response.data.items;
                }
            } catch (error) {
                const legacyResponse = await InventoryService.getReorderRecommendations();
                if (legacyResponse.status === 'success' && Array.isArray(legacyResponse.data)) {
                    recommendations = mapLegacyToRecommendations(legacyResponse.data);
                } else {
                    throw error;
                }
            }

            allRecommendations = recommendations;
            updateSummary(recommendations);
            renderRecommendations();
            document.getElementById('last-updated').textContent = `마지막 갱신 ${new Date().toLocaleString('ko-KR')}`;
        } catch (error) {
            renderError(error);
        }
    }

    function renderSkeleton() {
        const list = document.getElementById('recommendation-list');
        document.getElementById('empty-state').classList.add('hidden');
        list.innerHTML = Array.from({ length: 4 }).map(() => `
            <tr>
                <td colspan="6" class="px-4 py-4">
                    <div class="skeleton h-16 w-full"></div>
                </td>
            </tr>
        `).join('');
    }

    function updateSummary(items) {
        setText('cnt-ready', items.filter((item) => item.recommendationStatus === 'READY').length);
        setText('cnt-review', items.filter((item) => item.recommendationStatus === 'REVIEW').length);
        setText('cnt-disabled', items.filter((item) => item.recommendationStatus === 'DISABLED').length);
        setText('cnt-critical', items.filter((item) => item.urgency === 'CRITICAL').length);
    }

    function renderRecommendations() {
        const list = document.getElementById('recommendation-list');
        const empty = document.getElementById('empty-state');
        const filtered = allRecommendations.filter((item) => {
            const matchesStatus = currentStatusFilter ? item.recommendationStatus === currentStatusFilter : true;
            const matchesUrgency = currentUrgencyFilter ? item.urgency === currentUrgencyFilter : true;
            return matchesStatus && matchesUrgency;
        });

        if (filtered.length === 0) {
            list.innerHTML = '';
            empty.classList.remove('hidden');
            lucide.createIcons();
            return;
        }

        empty.classList.add('hidden');
        list.innerHTML = filtered.map((item) => {
            const urgencyLabel = urgencyText(item.urgency);
            const statusLabel = statusText(item.recommendationStatus);
            const actionButton = item.orderable
                ? `<button class="action-btn action-btn-ready" onclick="handleOrderRequest(${item.itemId}, '${escapeHtml(item.itemName)}', ${item.recommendedOrderQty}, '${escapeHtml(item.unit)}')">발주 요청</button>`
                : `<button class="action-btn action-btn-muted" disabled>${item.recommendationStatus === 'REVIEW' ? '검토 필요' : '비활성'}</button>`;

            return `
                <tr>
                    <td>
                        <p class="text-sm font-extrabold text-gray-900">${escapeHtml(item.itemName)}</p>
                        <p class="mt-1 text-xs font-semibold uppercase tracking-[0.16em] text-gray-400">${escapeHtml(item.itemCode)} · ${escapeHtml(item.itemCategory)} · ${escapeHtml(item.warehouseName)}</p>
                    </td>
                    <td>
                        <div class="flex flex-wrap gap-2">
                            <span class="status-badge status-${item.recommendationStatus}">${statusLabel}</span>
                            <span class="urgency-badge urgency-${item.urgency}">${urgencyLabel}</span>
                        </div>
                        <p class="mt-2 text-sm font-semibold text-gray-700">${escapeHtml(item.statusReason || '-')}</p>
                        <p class="mt-1 text-xs text-gray-500">${escapeHtml(item.reviewMessage || item.evidenceSummary || '-')}</p>
                    </td>
                    <td>
                        <p class="metric"><strong>현재</strong> ${number(item.currentStock)} ${escapeHtml(item.unit)}</p>
                        <p class="metric mt-1"><strong>안전</strong> ${number(item.safetyStock)} ${escapeHtml(item.unit)}</p>
                        <p class="metric mt-1"><strong>추천</strong> ${number(item.recommendedOrderQty)} ${escapeHtml(item.unit)}</p>
                        <p class="metric mt-1"><strong>소진 예상</strong> ${stockoutText(item.daysUntilStockout)}</p>
                    </td>
                    <td>
                        <p class="metric"><strong>최근 30일 출고</strong> ${number(item.outboundQuantityLast30Days)}</p>
                        <p class="metric mt-1"><strong>출고일 수</strong> ${number(item.outboundHistoryDays)}일</p>
                        <p class="metric mt-1"><strong>리드타임</strong> ${number(item.leadTimeDays)}일</p>
                        <p class="metric mt-1"><strong>발주 근거</strong> ${number(item.recentPurchaseOrderCount)}건</p>
                    </td>
                    <td>
                        <p class="text-sm font-semibold text-gray-800">${escapeHtml(item.defaultVendorName || '-')}</p>
                        <p class="mt-2 text-xs text-gray-500">${escapeHtml(item.leadTimeReliable ? '납기 근거 확보' : '납기 근거 없음')}</p>
                    </td>
                    <td>${actionButton}</td>
                </tr>
            `;
        }).join('');

        lucide.createIcons();
    }

    function renderError(error) {
        const list = document.getElementById('recommendation-list');
        const isUnauthorized = error && (error.type === 'unauthorized' || error.status === 401);
        const isNetwork = error && error.type === 'network';
        const message = isNetwork
            ? '서버 연결에 실패했어.'
            : isUnauthorized
                ? '로그인이 필요해.'
                : `추천 발주 조회에 실패했어. ${error.message || ''}`.trim();

        list.innerHTML = `<tr><td colspan="6" class="px-6 py-10 text-center text-sm font-bold text-red-500">${escapeHtml(message)}</td></tr>`;

        if (!isUnauthorized && window.ddukApi && window.ddukApi.showToast) {
            window.ddukApi.showToast(message, isNetwork ? 'warning' : 'error');
        }
    }

    function mapLegacyToRecommendations(inventories) {
        return inventories.map((inventory) => {
            const currentStock = inventory.currentStock || 0;
            const safetyStock = inventory.safetyStock || 0;
            const shortage = Math.max(safetyStock - currentStock, 1);
            return {
                inventoryId: inventory.id,
                itemId: inventory.item?.id,
                itemCode: inventory.item?.itemCode || '-',
                itemName: inventory.item?.name || '-',
                itemCategory: inventory.item?.category || '-',
                unit: inventory.item?.unit || 'EA',
                warehouseId: inventory.warehouse?.id,
                warehouseName: inventory.warehouse?.warehouseName || '-',
                currentStock,
                safetyStock,
                allocatedStock: inventory.allocatedStock || 0,
                availableStock: currentStock - (inventory.allocatedStock || 0),
                averageCost: inventory.averageCost || 0,
                avgMonthlyUsage: 0,
                recommendedOrderQty: shortage,
                urgency: calcUrgency(currentStock, safetyStock),
                daysUntilStockout: currentStock <= 0 ? 0 : 999,
                defaultVendorName: '-',
                leadTimeDays: 7,
                recommendationStatus: 'DISABLED',
                orderable: false,
                statusReason: '신규 추천 API를 사용할 수 없어 통계 추천을 비활성화했습니다.',
                reviewMessage: '기존 안전재고 기준 fallback 화면입니다.',
                outboundQuantityLast30Days: 0,
                outboundHistoryDays: 0,
                recentPurchaseOrderCount: 0,
                leadTimeReliable: false,
                evidenceSummary: 'fallback'
            };
        });
    }

    function handleOrderRequest(itemId, itemName, quantity, unit) {
        const confirmed = window.confirm(`[${itemName}] ${number(quantity)} ${unit} 발주 요청 화면으로 이동할까?`);
        if (confirmed) {
            window.location.href = `purchase-request.html?itemId=${itemId}&qty=${quantity}&itemName=${encodeURIComponent(itemName)}&unit=${encodeURIComponent(unit)}`;
        }
    }

    function setText(id, value) {
        document.getElementById(id).textContent = value;
    }

    function number(value) {
        return Number(value || 0).toLocaleString('ko-KR');
    }

    function statusText(status) {
        return {
            READY: '추천 가능',
            REVIEW: '수동 검토',
            DISABLED: '비활성'
        }[status] || status || '-';
    }

    function urgencyText(urgency) {
        return {
            CRITICAL: '긴급',
            HIGH: '높음',
            MEDIUM: '주의',
            LOW: '여유'
        }[urgency] || urgency || '-';
    }

    function stockoutText(daysUntilStockout) {
        if (daysUntilStockout === 0) return '즉시 부족';
        if (daysUntilStockout >= 999) return '예측 불가';
        return `${number(daysUntilStockout)}일`;
    }

    function calcUrgency(currentStock, safetyStock) {
        if (currentStock <= 0) return 'CRITICAL';
        if (currentStock <= safetyStock / 2) return 'HIGH';
        if (currentStock <= safetyStock) return 'MEDIUM';
        return 'LOW';
    }

    function escapeHtml(value) {
        if (value === null || value === undefined) return '-';
        return String(value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }
