        document.addEventListener('DOMContentLoaded', async () => {
            lucide.createIcons();
            await loadWarehouses();
            await loadStocks();
            
            document.getElementById('warehouse-filter').addEventListener('change', loadStocks);
            document.getElementById('low-stock-only').addEventListener('change', loadStocks);
            document.getElementById('search-input').addEventListener('input', debounce(loadStocks, 300));
        });

        async function loadWarehouses() {
            try {
                const response = await InventoryService.getWarehouses();
                if (response.status === 'success') {
                    const filter = document.getElementById('warehouse-filter');
                    const options = response.data.map(w => `<option value="${w.id}">${w.warehouseName}</option>`).join('');
                    filter.innerHTML = '<option value="">모든 창고</option>' + options;
                }
            } catch (error) {
                console.error('Failed to load warehouses:', error);
            }
        }

        async function loadStocks() {
            const warehouseId = document.getElementById('warehouse-filter').value;
            const lowStockOnly = document.getElementById('low-stock-only').checked;
            const search = document.getElementById('search-input').value.toLowerCase();
            const list = document.getElementById('stock-list');
            
            // 로딩 상태
            list.innerHTML = `<tr><td colspan="10" style="text-align:center;padding:2rem;color:#6366f1;font-size:0.85rem;font-weight:600">🔄 재고 데이터 로딩 중...</td></tr>`;

            try {
                const response = await InventoryService.getStocks({ warehouseId, lowStockOnly });
                const list = document.getElementById('stock-list');
                
                if (response.status === 'success') {
                    let data = response.data;
                    if (search) {
                        data = data.filter(i => 
                            i.item.name.toLowerCase().includes(search) || 
                            i.item.itemCode.toLowerCase().includes(search)
                        );
                    }

                    // 안전재고 하회 품목 최상단 우선 정렬
                    data.sort((a, b) => {
                        const aLow = a.currentStock <= a.safetyStock ? 1 : 0;
                        const bLow = b.currentStock <= b.safetyStock ? 1 : 0;
                        return bLow - aLow;
                    });

                    list.innerHTML = data.map(i => {
                        const available = i.currentStock - i.allocatedStock;
                        const isLow = i.currentStock <= i.safetyStock;
                        const parsed = parseSpec(i.item.spec);

                        return `
                            <tr class="${isLow ? 'low_stock' : ''} hover:bg-gray-50 transition-colors">
                                <td>
                                    <p class="font-bold text-gray-800">${i.item.name}</p>
                                    <p class="text-[10px] text-gray-400 font-semibold tracking-wider uppercase">${i.item.itemCode} | ${i.item.unit}</p>
                                </td>
                                <td><span class="text-xs font-semibold text-gray-600 bg-gray-100 px-2 py-1 rounded">${i.warehouse.warehouseName}</span></td>
                                <td>
                                    <p class="font-bold text-gray-800">${i.currentStock.toLocaleString()}</p>
                                    <p class="text-xs text-indigo-600 font-semibold">가용: ${available.toLocaleString()}</p>
                                </td>
                                <td class="text-xs font-semibold text-gray-500">${i.safetyStock.toLocaleString()}</td>
                                <td class="text-xs text-gray-600 font-medium">${parsed.spec}</td>
                                <td class="text-xs text-gray-600 font-medium">${parsed.vendor}</td>
                                <td class="text-xs"><span class="font-mono text-indigo-600 bg-indigo-50 px-1.5 py-0.5 rounded font-bold">${parsed.location}</span></td>
                                <td class="text-xs font-mono text-gray-500 font-semibold">${parsed.lot}</td>
                                <td>
                                    <p class="text-xs text-gray-400">₩${(i.averageCost || 0).toLocaleString()}</p>
                                    <p class="text-xs text-gray-800 font-bold">₩${(i.inventoryValue || 0).toLocaleString()}</p>
                                </td>
                                <td>
                                    <span class="status_badge ${getStatusClass(i.currentStock, i.safetyStock)}">
                                        ${getStatusText(i.currentStock, i.safetyStock)}
                                    </span>
                                </td>
                            </tr>
                        `;
                    }).join('');

                    if (data.length === 0) {
                        list.innerHTML = `<tr><td colspan="10" style="text-align:center;padding:3rem;color:#9ca3af;font-size:0.85rem">📦 조건에 맞는 재고가 없습니다.</td></tr>`;
                    }
                } else {
                    list.innerHTML = `<tr><td colspan="10" style="text-align:center;padding:3rem;color:#9ca3af;font-size:0.85rem">데이터를 원하는 형식으로 가져옴 실패</td></tr>`;
                }

            } catch (error) {
                const list = document.getElementById('stock-list');
                const isUnauthorized = error && (error.type === 'unauthorized' || error.status === 401);
                const isNetwork = error && error.type === 'network';
                const msg = isNetwork ? '네트워크 오류: 서버에 연결할 수 없습니다.' : (isUnauthorized ? '로그인 후 이용하세요.' : ('오류: ' + (error.message || '알 수 없는 오류')));
                list.innerHTML = `<tr><td colspan="10" style="text-align:center;padding:3rem;color:#ef4444;font-size:0.85rem;font-weight:600">🔥 ${msg}</td></tr>`;
                if (!isUnauthorized && window.ddukApi && window.ddukApi.showToast) {
                    window.ddukApi.showToast(msg, isNetwork ? 'warning' : 'error');
                }
            }
        }

        function parseSpec(specStr) {
            const fallback = { spec: specStr || '-', vendor: '-', location: '-', lot: '-' };
            if (!specStr) return fallback;
            
            // 정규식 매칭: "20kg/포대 [공급처: (주)뚝딱물산, 위치: WH-RAW-A1, LOT: LOT-202605-A01]"
            const specRegex = /^(.*?)\s*\[공급처:\s*(.*?),\s*위치:\s*(.*?),\s*LOT:\s*(.*?)\]$/;
            const match = specStr.match(specRegex);
            if (match) {
                return {
                    spec: match[1].trim() || '-',
                    vendor: match[2].trim() || '-',
                    location: match[3].trim() || '-',
                    lot: match[4].trim() || '-'
                };
            }
            return fallback;
        }

        function getStatusClass(current, safety) {
            if (current <= 0) return 'status_danger';
            if (current <= safety) return 'status_warning';
            return 'status_normal';
        }

        function getStatusText(current, safety) {
            if (current <= 0) return '품절';
            if (current <= safety) return '부족';
            return '정상';
        }

        function debounce(func, wait) {
            let timeout;
            return function(...args) {
                clearTimeout(timeout);
                timeout = setTimeout(() => func.apply(this, args), wait);
            };
        }
    