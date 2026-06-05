        let currentStatusFilter = '';
        let warehouses = [];
        let addedTransferItems = [];
        let allTransfersData = [];

        let whChartInstance = null;
        let trendChartInstance = null;

        function notify(message, type = 'info') {
            if (window.ddukApi?.showToast) {
                window.ddukApi.showToast(message, type);
                return;
            }
            console[type === 'error' ? 'error' : 'warn'](message);
        }

        document.addEventListener('DOMContentLoaded', async () => {
            lucide.createIcons();
            await loadWarehouses();
            await loadTransfers();

            // Tab Event
            document.querySelectorAll('.tab_btn').forEach(btn => {
                btn.addEventListener('click', async (e) => {
                    document.querySelectorAll('.tab_btn').forEach(b => b.classList.remove('active'));
                    e.target.classList.add('active');
                    currentStatusFilter = e.target.dataset.status;
                    await loadTransfers();
                });
            });

            // Modal Trigger
            document.getElementById('open-request-modal').addEventListener('click', () => {
                document.getElementById('request-modal').classList.remove('hidden');
            });
            document.getElementById('close-request-modal').addEventListener('click', hideRequestModal);
            document.getElementById('cancel-modal').addEventListener('click', hideRequestModal);
            document.getElementById('close-detail-modal').addEventListener('click', () => {
                document.getElementById('detail-modal').classList.add('hidden');
            });
            document.getElementById('close-reason-modal').addEventListener('click', () => {
                document.getElementById('reason-modal').classList.add('hidden');
            });

            document.getElementById('modal-from-warehouse').addEventListener('change', loadItemsInSourceWarehouse);
            document.getElementById('modal-item-select').addEventListener('change', updateAvailableInfo);
            document.getElementById('add-item-row-btn').addEventListener('click', addTransferItemRow);
            document.getElementById('submit-request-btn').addEventListener('click', submitRequest);
        });

        function hideRequestModal() {
            document.getElementById('request-modal').classList.add('hidden');
            document.getElementById('modal-from-warehouse').value = '';
            document.getElementById('modal-to-warehouse').value = '';
            document.getElementById('modal-item-select').innerHTML = '<option value="">출발 창고를 먼저 선택하세요</option>';
            document.getElementById('modal-item-select').disabled = true;
            document.getElementById('modal-transfer-qty').value = '';
            document.getElementById('modal-available-info').textContent = '가용재고: -';
            document.getElementById('modal-item-code-info').textContent = '품목코드: -';
            document.getElementById('modal-remarks').value = '';
            addedTransferItems = [];
            renderAddedItemsList();
        }

        async function loadWarehouses() {
            try {
                const response = await InventoryService.getWarehouses();
                if (response.status === 'success') {
                    warehouses = response.data;
                    const fromWh = document.getElementById('modal-from-warehouse');
                    const toWh = document.getElementById('modal-to-warehouse');

                    const options = warehouses.map(w => `<option value="${w.id}">${w.warehouseName}</option>`).join('');
                    fromWh.innerHTML = '<option value="">출발 창고 선택</option>' + options;
                    toWh.innerHTML = '<option value="">목적지 창고 선택</option>' + options;
                }
            } catch (error) {
                console.error('Failed to load warehouses:', error);
            }
        }

        async function loadItemsInSourceWarehouse() {
            const whId = document.getElementById('modal-from-warehouse').value;
            const itemSelect = document.getElementById('modal-item-select');
            if (!whId) {
                itemSelect.innerHTML = '<option value="">출발 창고를 먼저 선택하세요</option>';
                itemSelect.disabled = true;
                return;
            }

            try {
                const response = await InventoryService.getStocks({ warehouseId: whId });
                if (response.status === 'success') {
                    itemSelect.innerHTML = '<option value="">이동 품목 선택</option>' +
                        response.data.map(i => `<option value="${i.item.id}" data-itemcode="${i.item.itemCode}" data-unit="${i.item.unit}" data-name="${i.item.name}" data-available="${i.currentStock - i.allocatedStock}">${i.item.name} (${i.item.itemCode})</option>`).join('');
                    itemSelect.disabled = false;
                    updateAvailableInfo();
                }
            } catch (error) {
                console.error('Failed to load items in warehouse:', error);
            }
        }

        function updateAvailableInfo() {
            const select = document.getElementById('modal-item-select');
            if (select.disabled || !select.value) {
                document.getElementById('modal-available-info').textContent = '가용재고: -';
                document.getElementById('modal-item-code-info').textContent = '품목코드: -';
                return;
            }
            const selected = select.options[select.selectedIndex];
            const available = selected.dataset.available || '0';
            const itemCode = selected.dataset.itemcode || '-';
            document.getElementById('modal-available-info').textContent = `가용재고: ${parseInt(available).toLocaleString()} ${selected.dataset.unit || ''}`;
            document.getElementById('modal-item-code-info').textContent = `품목코드: ${itemCode}`;
        }

        function addTransferItemRow() {
            const select = document.getElementById('modal-item-select');
            const qtyInput = document.getElementById('modal-transfer-qty');

            if (select.disabled || !select.value) {
                notify('품목을 선택해 주십시오.', 'warning');
                return;
            }

            const qty = parseInt(qtyInput.value);
            if (isNaN(qty) || qty <= 0) {
                notify('유효한 이동 수량을 입력해 주십시오.', 'warning');
                return;
            }

            const selected = select.options[select.selectedIndex];
            const available = parseInt(selected.dataset.available);
            const itemId = parseInt(select.value);
            const name = selected.dataset.name;
            const itemCode = selected.dataset.itemcode;
            const unit = selected.dataset.unit;

            // 중복 검사
            const existingIdx = addedTransferItems.findIndex(i => i.itemId === itemId);
            let targetQty = qty;
            if (existingIdx !== -1) {
                targetQty += addedTransferItems[existingIdx].quantity;
            }

            if (available < targetQty) {
                notify(`해당 품목의 가용 재고가 부족합니다. (추가 수량 포함 가용: ${available}, 요청: ${targetQty})`, 'warning');
                return;
            }

            if (existingIdx !== -1) {
                addedTransferItems[existingIdx].quantity = targetQty;
            } else {
                addedTransferItems.push({ itemId, name, itemCode, unit, quantity: qty });
            }

            qtyInput.value = '';
            renderAddedItemsList();
        }

        function renderAddedItemsList() {
            const tbody = document.getElementById('modal-added-items-list');
            const totalSummary = document.getElementById('modal-total-summary');

            if (addedTransferItems.length === 0) {
                tbody.innerHTML = `<tr><td colspan="3" class="p-4 text-center text-gray-400 text-xs">선택된 품목이 없습니다. 위에서 추가해 주세요.</td></tr>`;
                totalSummary.textContent = '총 0개 품목 | 총 수량: 0';
                return;
            }

            let totalQty = 0;
            tbody.innerHTML = addedTransferItems.map((item, idx) => {
                totalQty += item.quantity;
                return `
                    <tr class="border-b hover:bg-gray-50/50">
                        <td class="p-2">
                            <p class="font-bold text-gray-800">${item.name}</p>
                            <p class="text-[10px] text-gray-400">${item.itemCode}</p>
                        </td>
                        <td class="p-2 text-right font-extrabold text-indigo-600">${item.quantity.toLocaleString()} ${item.unit}</td>
                        <td class="p-2 text-center">
                            <button onclick="removeAddedItemRow(${idx})" class="p-1 hover:bg-red-50 text-red-500 rounded-md transition-colors"><i data-lucide="trash-2" class="w-4 h-4"></i></button>
                        </td>
                    </tr>
                `;
            }).join('');

            totalSummary.textContent = `총 ${addedTransferItems.length}개 품목 | 총 수량: ${totalQty.toLocaleString()}`;
            lucide.createIcons();
        }

        function removeAddedItemRow(idx) {
            addedTransferItems.splice(idx, 1);
            renderAddedItemsList();
        }

        async function loadTransfers() {
            const list = document.getElementById('transfer-list');
            const empty = document.getElementById('empty-state');

            // 1. Skeleton UI 로딩 노출
            list.innerHTML = Array(3).fill(0).map(() => `
                <tr class="animate-pulse">
                    <td class="py-4"><div class="h-4 bg-gray-100 rounded w-16 skeleton"></div></td>
                    <td class="py-4"><div class="h-4 bg-gray-100 rounded w-24 skeleton"></div></td>
                    <td class="py-4"><div class="h-4 bg-gray-100 rounded w-24 skeleton"></div></td>
                    <td class="py-4"><div class="h-4 bg-gray-100 rounded w-12 skeleton"></div></td>
                    <td class="py-4"><div class="h-4 bg-gray-100 rounded w-16 skeleton"></div></td>
                    <td class="py-4"><div class="h-4 bg-gray-100 rounded w-16 skeleton"></div></td>
                    <td class="py-4"><div class="h-4 bg-gray-100 rounded w-28 skeleton"></div></td>
                    <td class="py-4"><div class="h-4 bg-gray-100 rounded w-16 skeleton"></div></td>
                    <td class="py-4 text-center"><div class="h-8 bg-gray-100 rounded w-20 mx-auto skeleton"></div></td>
                </tr>
            `).join('');
            empty.classList.add('hidden');

            try {
                const params = {};
                if (currentStatusFilter) params.status = currentStatusFilter;

                const response = await InventoryService.getTransfers(params);
                if (response.status === 'success') {
                    allTransfersData = response.data;

                    // KPI 카드 & 차트 업데이트는 전체 조회가 끝난 시점에 한 번만
                    if (!currentStatusFilter) {
                        updateKPICards(response.data);
                        updateCharts(response.data);
                    }

                    if (response.data.length > 0) {
                        list.innerHTML = response.data.map(t => {
                            const isEmergency = t.remarks && t.remarks.includes('긴급');
                            return `
                                <tr class="${isEmergency ? 'border-l-4 border-l-red-500 bg-red-50/20' : ''}">
                                    <td>
                                        <button class="font-bold text-indigo-600 hover:underline flex items-center gap-1" onclick="showDetail(${t.id})">
                                            ${isEmergency ? '🚨 ' : ''}${t.transferNo}
                                        </button>
                                    </td>
                                    <td><span class="font-medium text-gray-700">${t.sourceWarehouseName}</span></td>
                                    <td><span class="font-medium text-gray-700">${t.targetWarehouseName}</span></td>
                                    <td class="font-semibold text-center">${t.items.length} 건</td>
                                    <td class="text-xs text-gray-500">${t.requestedByName}</td>
                                    <td class="text-xs text-gray-500">${t.approvedByName || '-'}</td>
                                    <td class="text-xs text-gray-400">${new Date(t.createdAt).toLocaleString()}</td>
                                    <td>
                                        <span class="status_badge status_${getStatusClass(t)}">
                                            ${getStatusText(t)}
                                        </span>
                                    </td>
                                    <td class="text-center flex justify-center gap-1 py-3">
                                        ${getActionButtons(t)}
                                    </td>
                                </tr>
                            `;
                        }).join('');
                        list.parentElement.classList.remove('hidden');
                        empty.classList.add('hidden');
                    } else {
                        list.innerHTML = '';
                        list.parentElement.classList.add('hidden');
                        empty.classList.remove('hidden');
                    }
                }
                lucide.createIcons();
            } catch (error) {
                const isUnauthorized = error && (error.type === 'unauthorized' || error.status === 401);
                if (!isUnauthorized && window.ddukApi && window.ddukApi.showToast) {
                    window.ddukApi.showToast('창고 이동 목록 로드 실패.', 'error');
                }
                list.innerHTML = `<tr><td colspan="9" style="text-align:center;padding:2rem;color:#ef4444;font-size:0.85rem">🔥 데이터 로드 실패</td></tr>`;
            }
        }

        function getStatusClass(t) {
            if (t.status === 'CANCELLED' && t.remarks && t.remarks.includes('[반려]')) {
                return 'REJECTED';
            }
            return t.status;
        }

        function getStatusText(t) {
            if (t.status === 'PENDING') return '승인대기';
            if (t.status === 'APPROVED') return '이동중';
            if (t.status === 'COMPLETED') return '이동완료';
            if (t.status === 'CANCELLED') {
                if (t.remarks && t.remarks.includes('[반려]')) return '반려됨';
                return '취소됨';
            }
            return t.status;
        }

        function getActionButtons(t) {
            // PENDING 상태: 승인 / 반려 / 취소 단추 활성화
            if (t.status === 'PENDING') {
                return `
                    <button onclick="approveTransfer(${t.id})" class="px-2 py-1 bg-blue-600 text-white rounded-lg text-[10px] font-bold hover:bg-blue-700 transition-colors">승인</button>
                    <button onclick="openReasonModal(${t.id}, '반려')" class="px-2 py-1 bg-red-100 text-red-600 rounded-lg text-[10px] font-bold hover:bg-red-200 transition-colors">반려</button>
                    <button onclick="openReasonModal(${t.id}, '취소')" class="px-2 py-1 bg-gray-100 text-gray-500 rounded-lg text-[10px] font-bold hover:bg-gray-200 transition-colors">취소</button>
                `;
            }
            // APPROVED 상태: 완료 / 취소 단추 활성화
            if (t.status === 'APPROVED') {
                return `
                    <button onclick="completeTransfer(${t.id})" class="px-2 py-1 bg-green-600 text-white rounded-lg text-[10px] font-bold hover:bg-green-700 transition-colors">수령완료</button>
                    <button onclick="openReasonModal(${t.id}, '취소')" class="px-2 py-1 bg-gray-100 text-gray-500 rounded-lg text-[10px] font-bold hover:bg-gray-200 transition-colors">취소</button>
                `;
            }
            // 그 외(COMPLETED, CANCELLED): 읽기 전용으로 조치 단추 비활성화
            return '<span class="text-xs text-gray-400 font-bold">-</span>';
        }

        function updateKPICards(data) {
            const todayStr = new Date().toISOString().split('T')[0];
            const todayCount = data.filter(t => t.createdAt.startsWith(todayStr)).length;
            const pendingCount = data.filter(t => t.status === 'PENDING').length;

            const completedCount = data.filter(t => t.status === 'COMPLETED').length;
            const completionRate = data.length > 0 ? Math.round((completedCount / data.length) * 100) : 0;

            const oneWeekAgo = new Date();
            oneWeekAgo.setDate(oneWeekAgo.getDate() - 7);
            const weeklyCount = data.filter(t => new Date(t.createdAt) >= oneWeekAgo).length;

            document.getElementById('kpi-today-count').textContent = todayCount;
            document.getElementById('kpi-pending-count').textContent = pendingCount;
            document.getElementById('kpi-completion-rate').textContent = completionRate + '%';
            document.getElementById('kpi-weekly-count').textContent = weeklyCount;
        }

        function updateCharts(data) {
            // Chart 안전 파괴 후 null 초기화
            if (whChartInstance) { whChartInstance.destroy(); whChartInstance = null; }
            if (trendChartInstance) { trendChartInstance.destroy(); trendChartInstance = null; }

            const whCanvas = document.getElementById('transfer-wh-chart');
            const trendCanvas = document.getElementById('transfer-trend-chart');
            if (!whCanvas || !trendCanvas) return;

            // 1. 창고별 이동 물량 통계 (Source 창고 기준 집계)
            const whStats = {};
            data.forEach(t => {
                whStats[t.sourceWarehouseName] = (whStats[t.sourceWarehouseName] || 0) + t.items.length;
            });
            const whLabels = Object.keys(whStats);
            const whValues = Object.values(whStats);

            const ctxWh = whCanvas.getContext('2d');
            whChartInstance = new Chart(ctxWh, {
                type: 'bar',
                data: {
                    labels: whLabels.length > 0 ? whLabels : ['데이터 없음'],
                    datasets: [{
                        label: '이동 품목 건수',
                        data: whValues.length > 0 ? whValues : [0],
                        backgroundColor: 'rgba(79, 70, 229, 0.75)',
                        borderColor: 'rgb(79, 70, 229)',
                        borderWidth: 1,
                        borderRadius: 6
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    scales: { y: { beginAtZero: true, grid: { drawBorder: false } } }
                }
            });

            // 2. 최근 7일 추이 집계
            const dateStats = {};
            for (let i = 6; i >= 0; i--) {
                const d = new Date();
                d.setDate(d.getDate() - i);
                const dStr = d.toISOString().split('T')[0];
                dateStats[dStr] = 0;
            }
            data.forEach(t => {
                const dStr = t.createdAt.split('T')[0];
                if (dateStats[dStr] !== undefined) {
                    dateStats[dStr]++;
                }
            });
            const dateLabels = Object.keys(dateStats).map(d => d.substring(5)); // MM-DD
            const dateValues = Object.values(dateStats);

            const ctxTrend = trendCanvas.getContext('2d');
            trendChartInstance = new Chart(ctxTrend, {
                type: 'line',
                data: {
                    labels: dateLabels,
                    datasets: [{
                        label: '창고 이동 등록 수',
                        data: dateValues,
                        borderColor: 'rgb(16, 185, 129)',
                        backgroundColor: 'rgba(10, 185, 129, 0.1)',
                        tension: 0.3,
                        fill: true,
                        pointRadius: 4
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    scales: { y: { beginAtZero: true, grid: { drawBorder: false } } }
                }
            });
        }

        async function submitRequest() {
            const fromWh = document.getElementById('modal-from-warehouse').value;
            const toWh = document.getElementById('modal-to-warehouse').value;
            const remarks = document.getElementById('modal-remarks').value;

            if (!fromWh || !toWh) {
                notify('출발 및 도착 창고를 반드시 선택하십시오.', 'warning');
                return;
            }

            if (fromWh === toWh) {
                notify('출발지와 도착지 창고는 같을 수 없습니다.', 'warning');
                return;
            }

            if (addedTransferItems.length === 0) {
                notify('이동할 품목을 최소 하나 이상 추가하십시오.', 'warning');
                return;
            }

            try {
                const response = await InventoryService.requestTransfer({
                    sourceWarehouseId: fromWh,
                    targetWarehouseId: toWh,
                    remarks: remarks,
                    items: addedTransferItems.map(i => ({ itemId: i.itemId, quantity: i.quantity }))
                });

                if (response.status === 'success') {
                    notify('창고 이동 요청서가 성공적으로 등록되었습니다.', 'success');
                    hideRequestModal();
                    await loadTransfers();
                } else {
                    notify('등록 실패: ' + response.message, 'error');
                }
            } catch (error) {
                notify('서버 통신 오류가 발생했습니다.', 'error');
            }
        }

        async function approveTransfer(id) {
            if (!confirm('이 창고 이동 요청을 최종 승인(이동중 처리)하시겠습니까?')) return;
            try {
                const res = await InventoryService.approveTransfer(id);
                if (res.status === 'success') {
                    notify('이동 승인이 완료되었습니다.', 'success');
                    await loadTransfers();
                } else {
                    notify('실패: ' + res.message, 'error');
                }
            } catch (error) {
                notify('오류가 발생했습니다.', 'error');
            }
        }

        async function completeTransfer(id) {
            if (!confirm('이동 물품이 도착지에 무사 수령되었습니까?\n확인 클릭 시 수불원장 적재와 회계 자동전표가 기안됩니다.')) return;
            try {
                const res = await InventoryService.completeTransfer(id);
                if (res.status === 'success') {
                    notify('수령 확인 및 창고 이동 완료가 정상 등록되었습니다.', 'success');
                    await loadTransfers();
                } else {
                    notify('완료 처리 실패: ' + res.message, 'error');
                }
            } catch (error) {
                notify('오류가 발생했습니다.', 'error');
            }
        }

        // 반려 / 취소 모달 팝업 가동
        let activeReasonTransferId = null;
        let activeReasonType = '';

        function openReasonModal(id, type) {
            activeReasonTransferId = id;
            activeReasonType = type;

            const title = document.getElementById('reason-modal-title');
            const desc = document.getElementById('reason-modal-desc');
            const textarea = document.getElementById('reason-modal-text');

            textarea.value = '';
            if (type === '반려') {
                title.textContent = '결재 반려 사유 입력';
                title.className = 'text-sm font-bold text-red-600 flex items-center gap-1.5';
                desc.textContent = '승인권자가 해당 요청을 반려하는 명확한 사유를 기입해 주십시오.';
            } else {
                title.textContent = '이동 요청 취소 사유 입력';
                title.className = 'text-sm font-bold text-gray-700 flex items-center gap-1.5';
                desc.textContent = '요청 부서에서 본 요청을 철회/취소하는 사유를 입력하십시오.';
            }

            document.getElementById('reason-modal').classList.remove('hidden');

            // 기존 이벤트 바인딩 해제 후 재바인딩
            const submitBtn = document.getElementById('submit-reason-modal');
            submitBtn.onclick = submitReasonAction;
        }

        async function submitReasonAction() {
            const textarea = document.getElementById('reason-modal-text');
            const reason = textarea.value.trim();

            if (!reason) {
                notify('사유를 입력해 주십시오.', 'warning');
                return;
            }

            try {
                // ddukApi 를 통해 JWT 자동 포함 처리
                const res = await InventoryService.cancelTransfer(activeReasonTransferId, {
                    reason: reason,
                    type: activeReasonType
                });

                if (res.status === 'success') {
                    if (window.ddukApi && window.ddukApi.showToast) {
                        window.ddukApi.showToast(`창고 이동이 정상적으로 ${activeReasonType} 처리되었습니다.`, 'success');
                    }
                    document.getElementById('reason-modal').classList.add('hidden');
                    await loadTransfers();
                } else {
                    notify('오류: ' + res.message, 'error');
                }
            } catch (error) {
                notify('통신 중 예외가 발생했습니다.', 'error');
            }
        }

        async function showDetail(id) {
            try {
                const res = await InventoryService.getTransferDetail(id);
                if (res.status === 'success') {
                    const t = res.data;
                    const content = document.getElementById('detail-content');

                    // remarks 에서 반려/취소 사유 파싱
                    let reasonDisplay = '';
                    if (t.remarks && (t.remarks.includes('[반려]') || t.remarks.includes('[취소]'))) {
                        reasonDisplay = `
                            <div class="bg-red-50 p-3 rounded-xl border border-red-100 mt-2">
                                <span class="px-2 py-0.5 bg-red-600 text-white rounded text-[10px] font-bold">사유</span>
                                <p class="text-xs font-bold text-red-700 mt-1.5">${t.remarks}</p>
                            </div>
                        `;
                    }

                    // 감사 타임라인 동적 생성
                    const timelineHTML = buildTimelineHTML(t);

                    content.innerHTML = `
                        <div class="grid grid-cols-2 gap-4 text-sm bg-gray-50 p-4 rounded-xl">
                            <div><p class="text-gray-400 text-xs">이동 번호</p><p class="font-bold text-gray-800">${t.transferNo}</p></div>
                            <div><p class="text-gray-400 text-xs">상태</p><p class="font-bold status_badge status_${getStatusClass(t)} mt-1">${getStatusText(t)}</p></div>
                            <div><p class="text-gray-400 text-xs">출발 창고</p><p class="font-semibold text-gray-700">${t.sourceWarehouseName}</p></div>
                            <div><p class="text-gray-400 text-xs">목적지 창고</p><p class="font-semibold text-gray-700">${t.targetWarehouseName}</p></div>
                        </div>

                        <div>
                            <h4 class="font-bold text-gray-800 mb-2 border-b pb-1 text-xs"><i data-lucide="list" class="w-4 h-4 inline"></i> 이동 품목 상세</h4>
                            <div class="space-y-1.5 max-h-36 overflow-y-auto pr-1">
                                ${t.items.map(item => `
                                    <div class="flex justify-between items-center p-2.5 bg-white border rounded-xl text-xs">
                                        <div>
                                            <p class="font-bold text-gray-800">${item.itemName}</p>
                                            <p class="text-[10px] text-gray-400">${item.itemCode}</p>
                                        </div>
                                        <p class="font-semibold text-indigo-600">${item.quantity.toLocaleString()} ${item.unit}</p>
                                    </div>
                                `).join('')}
                            </div>
                        </div>

                        <div>
                            <h4 class="font-bold text-gray-800 text-xs mb-1">비고 및 코멘트</h4>
                            <p class="text-xs bg-gray-50 p-3 rounded-xl text-gray-600 border border-dashed">${(!t.remarks || t.remarks.includes('[반려]') || t.remarks.includes('[취소]')) ? '추가 비고 없음' : t.remarks}</p>
                            ${reasonDisplay}
                        </div>

                        <!-- Audit Log Timeline Section -->
                        <div class="border-t pt-4">
                            <h4 class="font-bold text-gray-800 text-xs mb-3 flex items-center gap-1.5"><i data-lucide="history" class="w-4 h-4 text-indigo-500"></i> 이동 이력 Audit Timeline</h4>
                            <div class="px-1">
                                ${timelineHTML}
                            </div>
                        </div>
                    `;
                    document.getElementById('detail-modal').classList.remove('hidden');
                    lucide.createIcons();
                }
            } catch (error) {
                notify('상세 정보를 불러올 수 없습니다.', 'error');
            }
        }

        function buildTimelineHTML(t) {
            const steps = [];

            // 1단계: 요청 등록
            steps.push({
                title: '이동 요청 등록',
                desc: `출발지: ${t.sourceWarehouseName} ➔ 가용재고 ${t.items.length}개 품목 잠금`,
                user: t.requestedByName || '시스템',
                time: new Date(t.createdAt).toLocaleString(),
                class: 'timeline-active'
            });

            // 2단계: 승인 완료
            if (t.approvedAt) {
                steps.push({
                    title: '요청 결재 승인',
                    desc: '이동중 상태로 전환 및 물류 이동 준비',
                    user: t.approvedByName || '승인자',
                    time: new Date(t.approvedAt).toLocaleString(),
                    class: 'timeline-active'
                });
            }

            // 3단계: 취소/반려 또는 최종 완료
            if (t.status === 'COMPLETED') {
                steps.push({
                    title: '도착지 수령 및 최종 완료',
                    desc: '물품 수령 검수 확인 ➔ 실재고 차감 및 가산 원장 반영 완료',
                    user: t.approvedByName || '도착지 담당자',
                    time: t.completedAt ? new Date(t.completedAt).toLocaleString() : '완료 날인 없음',
                    class: 'timeline-success'
                });
            } else if (t.status === 'CANCELLED') {
                const isReject = t.remarks && t.remarks.includes('[반려]');
                steps.push({
                    title: isReject ? '결재 반려' : '이동 요청 취소',
                    desc: isReject ? '요청 사유 부적합 또는 재고 초과로 반려' : '요청 부서 단순 변심에 의한 철회',
                    user: isReject ? (t.approvedByName || '승인자') : (t.requestedByName || '요청자'),
                    time: new Date(t.createdAt).toLocaleString(), // 업데이트 일자 대용
                    class: 'timeline-error'
                });
            }

            return steps.map(s => `
                <div class="timeline-item ${s.class}">
                    <div class="timeline-dot"></div>
                    <div class="flex flex-col gap-0.5">
                        <span class="text-xs font-bold text-gray-800">${s.title}</span>
                        <span class="text-[10px] text-gray-400">${s.desc}</span>
                        <span class="text-[10px] text-indigo-500 font-bold">${s.user} | ${s.time}</span>
                    </div>
                </div>
            `).join('');
        }

