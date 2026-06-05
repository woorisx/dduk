        let rawMovementsData = [];

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
            await loadMovements();

            document.getElementById('warehouse-filter').addEventListener('change', loadMovements);
            document.getElementById('type-filter').addEventListener('change', loadMovements);
            document.getElementById('start-date').addEventListener('change', loadMovements);
            document.getElementById('end-date').addEventListener('change', loadMovements);
            document.getElementById('excel-btn').addEventListener('click', downloadExcel);
            document.getElementById('csv-btn').addEventListener('click', downloadCSV);
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

        async function loadMovements() {
            const warehouseId = document.getElementById('warehouse-filter').value;
            const movementType = document.getElementById('type-filter').value;
            const startDate = document.getElementById('start-date').value;
            const endDate = document.getElementById('end-date').value;
            const list = document.getElementById('movement-list');
            const empty = document.getElementById('empty-state');

            // 로딩 표시
            list.innerHTML = `<tr><td colspan="9" style="text-align:center;padding:2.5rem;color:#6366f1;font-size:0.85rem;font-weight:600">🔄 거래 원장 로딩 중...</td></tr>`;
            empty.classList.add('hidden');

            try {
                const response = await InventoryService.getMovements({ warehouseId, movementType });

                if (response.status === 'success' && response.data.length > 0) {
                    rawMovementsData = response.data;

                    // 기간(Date-Range) 필터링
                    let data = rawMovementsData;
                    if (startDate) {
                        data = data.filter(m => new Date(m.createdAt) >= new Date(startDate + 'T00:00:00'));
                    }
                    if (endDate) {
                        data = data.filter(m => new Date(m.createdAt) <= new Date(endDate + 'T23:59:59'));
                    }

                    if (data.length > 0) {
                        list.innerHTML = data.map(m => `
                            <tr>
                                <td class="text-xs text-gray-500">${new Date(m.createdAt).toLocaleString()}</td>
                                <td><span class="ref_no">${m.referenceNo || '-'}</span></td>
                                <td><span class="type_badge ${getTypeClass(m.movementType)}">${translateType(m.movementType)}</span></td>
                                <td>
                                    <p class="font-bold text-gray-800">${m.item.name}</p>
                                    <p class="text-[10px] text-gray-400 font-semibold tracking-wider uppercase">${m.item.itemCode}</p>
                                </td>
                                <td><span class="text-xs font-semibold text-gray-600 bg-gray-100 px-2 py-1 rounded">${m.warehouse.warehouseName}</span></td>
                                <td class="font-bold ${m.movementType.includes('OUT') ? 'text-red-600' : 'text-green-600'}">
                                    ${m.movementType.includes('OUT') ? '-' : '+'}${m.quantity.toLocaleString()}
                                </td>
                                <td class="text-xs text-gray-500 font-medium">₩${(m.unitCost || 0).toLocaleString()}</td>
                                <td class="text-sm font-extrabold text-gray-800">₩${(m.totalAmount || 0).toLocaleString()}</td>
                                <td>
                                    <div class="flex items-center gap-1.5">
                                        <span class="text-xs px-2 py-0.5 bg-indigo-50 text-indigo-700 font-bold rounded">${translateReason(m.movementReason)}</span>
                                        ${m.referenceType ? `<span class="text-[10px] text-gray-400 font-semibold">(${m.referenceType})</span>` : ''}
                                    </div>
                                </td>
                            </tr>
                        `).join('');
                        list.parentElement.classList.remove('hidden');
                        empty.classList.add('hidden');
                    } else {
                        list.innerHTML = '';
                        list.parentElement.classList.add('hidden');
                        empty.classList.remove('hidden');
                    }
                } else {
                    list.innerHTML = '';
                    list.parentElement.classList.add('hidden');
                    empty.classList.remove('hidden');
                }
                lucide.createIcons();
            } catch (error) {
                const list = document.getElementById('movement-list');
                const empty = document.getElementById('empty-state');
                const isUnauthorized = error && (error.type === 'unauthorized' || error.status === 401);
                const isNetwork = error && error.type === 'network';
                const msg = isNetwork ? '네트워크 오류: 서버에 연결할 수 없습니다.' : (isUnauthorized ? '로그인 후 이용하세요.' : '거래 원장 데이터 로드 실패.');
                list.innerHTML = `<tr><td colspan="9" style="text-align:center;padding:3rem;color:#ef4444;font-size:0.85rem;font-weight:600">🔥 ${msg}</td></tr>`;
                empty.classList.add('hidden');
                if (!isUnauthorized && window.ddukApi && window.ddukApi.showToast) {
                    window.ddukApi.showToast(msg, isNetwork ? 'warning' : 'error');
                }
            }
        }

        function getTypeClass(type) {
            if (!type) return 'bg-gray-100 text-gray-600';
            if (type === 'INBOUND') return 'type_inbound';
            if (type === 'OUTBOUND') return 'type_outbound';
            if (type.startsWith('TRANSFER')) return 'type_transfer';
            if (type.startsWith('ADJUSTMENT')) return 'type_adjustment';
            if (type.startsWith('RETURN')) return 'type_transfer';
            return 'bg-gray-100 text-gray-600';
        }

        function translateType(type) {
            const map = {
                'INBOUND': '입고',
                'OUTBOUND': '출고',
                'TRANSFER_IN': '이동입고',
                'TRANSFER_OUT': '이동출고',
                'ADJUSTMENT_IN': '재고조정+',
                'ADJUSTMENT_OUT': '재고조정-',
                'RETURN_IN': '반품입고',
                'RETURN_OUT': '반품출고'
            };
            return map[type] || (type || '-');
        }

        function translateReason(reason) {
            const map = {
                'PURCHASE_RECEIVED': '구매입고',
                'SALES_SHIPPED': '매출출고',
                'TRANSFER': '창고이동',
                'MANUAL_ADJUST': '수동조정',
                'INITIAL': '초기재고'
            };
            return map[reason] || (reason || '-');
        }

        function getFilteredData() {
            const startDate = document.getElementById('start-date').value;
            const endDate = document.getElementById('end-date').value;
            let data = rawMovementsData;
            if (startDate) {
                data = data.filter(m => new Date(m.createdAt) >= new Date(startDate + 'T00:00:00'));
            }
            if (endDate) {
                data = data.filter(m => new Date(m.createdAt) <= new Date(endDate + 'T23:59:59'));
            }
            return data;
        }

        function downloadExcel() {
            const data = getFilteredData();
            if (data.length === 0) {
                notify('다운로드할 데이터가 없습니다.', 'warning');
                return;
            }

            const formatted = data.map(m => ({
                '일시': new Date(m.createdAt).toLocaleString(),
                '참조번호': m.referenceNo || '-',
                '유형': m.movementType,
                '품목코드': m.item.itemCode,
                '품목명': m.item.name,
                '창고': m.warehouse.warehouseName,
                '변동수량': m.movementType.includes('OUT') ? -m.quantity : m.quantity,
                '단가': m.unitCost,
                '총액': m.totalAmount,
                '사유': translateReason(m.movementReason)
            }));

            const worksheet = XLSX.utils.json_to_sheet(formatted);
            const workbook = XLSX.utils.book_new();
            XLSX.utils.book_append_sheet(workbook, worksheet, "입출고 원장");

            const today = new Date().toISOString().split('T')[0];
            XLSX.writeFile(workbook, `inventory_movements_${today}.xlsx`);
        }

        function downloadCSV() {
            const data = getFilteredData();
            if (data.length === 0) {
                notify('다운로드할 데이터가 없습니다.', 'warning');
                return;
            }

            const headers = ['일시', '참조번호', '유형', '품목코드', '품목명', '창고', '변동수량', '단가', '총액', '사유'];
            const rows = data.map(m => [
                new Date(m.createdAt).toLocaleString(),
                m.referenceNo || '-',
                m.movementType,
                m.item.itemCode,
                `"${m.item.name.replace(/"/g, '""')}"`,
                m.warehouse.warehouseName,
                m.movementType.includes('OUT') ? -m.quantity : m.quantity,
                m.unitCost,
                m.totalAmount,
                translateReason(m.movementReason)
            ]);

            const csvContent = "\uFEFF" + [headers.join(','), ...rows.map(e => e.join(','))].join('\n');
            const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
            const link = document.createElement("a");
            const url = URL.createObjectURL(blob);

            const today = new Date().toISOString().split('T')[0];
            link.setAttribute("href", url);
            link.setAttribute("download", `inventory_movements_${today}.csv`);
            link.style.visibility = 'hidden';
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
        }

