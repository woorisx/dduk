        document.addEventListener('DOMContentLoaded', () => {
            const session = window.ddukSession?.requireRole?.(['ADMIN', 'INVENTORY', 'HR'], { redirectToLogin: true });
            if (!session) return;
            if (window.lucide) lucide.createIcons();

            const params = new URLSearchParams(location.search);
            const orderId = params.get('id');
            const apiOrigin = window.ddukSession?.getApiBaseUrl?.() || window.ddukApi?.getBaseUrl?.() || '';
            const message = document.getElementById('message');
            const saveButton = document.getElementById('btn-save');
            const approveButton = document.getElementById('btn-approve');
            const itemsBody = document.getElementById('items-body');
            let order = null;
            saveButton.innerHTML = '<i data-lucide="edit-3" class="w-4 h-4"></i> 수정';

            const memberProfiles = {
                1: { loginId: 'admin', label: '최고관리자' },
                2: { loginId: 'inventory', label: '구매/발주 담당' },
                3: { loginId: 'hr', label: '회계/직원 관리 담당' }
            };

            function resolveCurrentMemberId() {
                const stored = localStorage.getItem('memberId') || localStorage.getItem('userId');
                if (stored) return Number(stored);
                const loginId = (localStorage.getItem('loginId') || '').toLowerCase();
                const role = (localStorage.getItem('role') || '').toUpperCase();
                if (loginId === 'admin' || role === 'ADMIN') return 1;
                if (loginId === 'inventory' || role === 'INVENTORY') return 2;
                if (loginId === 'hr' || role === 'HR') return 3;
                return null;
            }

            const currentMemberId = resolveCurrentMemberId();

            function headers(json) {
                const token = localStorage.getItem('token');
                const result = json ? { 'Content-Type': 'application/json' } : {};
                if (token) result.Authorization = `Bearer ${token}`;
                return result;
            }

            function setMessage(text, type) {
                const color = type === 'error' ? 'text-red-600' : type === 'ok' ? 'text-emerald-600' : 'text-gray-500';
                message.className = `text-sm mt-1 font-semibold ${color}`;
                message.textContent = text;
            }

            async function readError(response) {
                const text = await response.text();
                if (!text) return `HTTP ${response.status}`;
                try {
                    const parsed = JSON.parse(text);
                    return parsed.message || parsed.error || text;
                } catch {
                    return text;
                }
            }

            function escapeHtml(value) {
                return String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' }[char]));
            }

            function dateText(value) {
                return value ? String(value).slice(0, 10) : '';
            }

            function money(value) {
                return Number(value || 0).toLocaleString('ko-KR', { style: 'currency', currency: 'KRW', maximumFractionDigits: 0 });
            }

            function memberText(id, name) {
                const profile = memberProfiles[Number(id)];
                if (!profile) return name || `ID ${id || '-'}`;
                return `${profile.label} (${profile.loginId}/${id})`;
            }

            function statusText(status) {
                return {
                    DRAFT: '초안',
                    ORDERED: '발주요청',
                    PENDING: '발주요청',
                    REQUESTED: '발주요청',
                    APPROVED: '승인 완료',
                    SENT_TO_VENDOR: '거래처 발송',
                    INBOUND_DELAY: '입고지연',
                    RECEIVING: '입고중',
                    RECEIVED: '입고 완료',
                    COMPLETED: '발주완료',
                    CANCELLED: '취소'
                }[status] || status || '-';
            }

            function canApprove() {
                return order
                    && ['DRAFT', 'ORDERED', 'PENDING', 'REQUESTED'].includes(order.status)
                    && Number(order.approvedByMemberId) === Number(currentMemberId);
            }

            function updateApproveButton() {
                const enabled = canApprove();
                approveButton.disabled = !enabled;
                if (!order) {
                    approveButton.title = '발주 정보를 먼저 조회해야 합니다.';
                } else if (order.status === 'APPROVED') {
                    approveButton.title = '이미 승인된 발주입니다.';
                } else if (!['DRAFT', 'ORDERED', 'PENDING', 'REQUESTED'].includes(order.status)) {
                    approveButton.title = '현재 상태에서는 승인할 수 없습니다.';
                } else if (Number(order.approvedByMemberId) !== Number(currentMemberId)) {
                    approveButton.title = '요청된 승인자만 승인할 수 있습니다.';
                } else {
                    approveButton.title = '승인 처리';
                }
            }

            function fillApproverSelect(selectedId) {
                const select = document.getElementById('approved-by-member-id');
                select.innerHTML = Object.entries(memberProfiles).map(([id, profile]) => (
                    `<option value="${id}" ${Number(id) === Number(selectedId) ? 'selected' : ''}>${profile.label} (${profile.loginId}/${id})</option>`
                )).join('');
            }

            function renderItems() {
                const items = order.items || [];
                if (items.length === 0) {
                    itemsBody.innerHTML = '<tr><td colspan="6" class="text-center text-gray-400 font-semibold py-8">등록된 발주 품목이 없습니다.</td></tr>';
                    return;
                }

                itemsBody.innerHTML = items.map(item => `
                    <tr data-item-id="${escapeHtml(item.purchaseOrderItemId)}">
                        <td>
                            <p class="font-bold text-gray-900">${escapeHtml(item.itemName || '-')}</p>
                            <p class="text-xs text-gray-400 mt-1">품목 ID ${escapeHtml(item.itemId || '-')} / ${escapeHtml(item.unit || '-')}</p>
                        </td>
                        <td><input class="form-input item-quantity" type="number" min="1" step="1" value="${escapeHtml(item.quantity || 1)}"></td>
                        <td><input class="form-input item-unit-price" type="number" min="0" step="0.01" value="${escapeHtml(item.unitPrice || 0)}"></td>
                        <td><input class="form-input item-expected-date" type="date" value="${escapeHtml(dateText(item.expectedDate))}"></td>
                        <td><input class="form-input item-note" value="${escapeHtml(item.note || '')}"></td>
                        <td class="font-bold text-gray-900">${money(item.lineAmount)}</td>
                    </tr>
                `).join('');
            }

            function fillForm() {
                document.getElementById('purchase-order-no').value = order.purchaseOrderNo || '';
                document.getElementById('vendor-name').value = order.vendorName || '';
                document.getElementById('requested-by').value = memberText(order.requestedByMemberId, order.requestedByMemberName);
                document.getElementById('status').value = statusText(order.status);
                document.getElementById('order-date').value = dateText(order.orderDate);
                document.getElementById('expected-date').value = dateText(order.expectedDate);
                document.getElementById('total-amount').value = money(order.totalAmount);
                document.getElementById('note').value = order.note || '';
                fillApproverSelect(order.approvedByMemberId);
                renderItems();
                updateApproveButton();
            }

            function collectPayload() {
                const itemPayload = Array.from(itemsBody.querySelectorAll('tr[data-item-id]')).map(row => ({
                    purchaseOrderItemId: Number(row.dataset.itemId),
                    quantity: Number(row.querySelector('.item-quantity').value),
                    unitPrice: Number(row.querySelector('.item-unit-price').value),
                    expectedDate: row.querySelector('.item-expected-date').value || null,
                    note: row.querySelector('.item-note').value.trim()
                }));

                return {
                    approvedByMemberId: Number(document.getElementById('approved-by-member-id').value),
                    expectedDate: document.getElementById('expected-date').value || null,
                    note: document.getElementById('note').value.trim(),
                    items: itemPayload
                };
            }

            async function loadOrder() {
                if (!orderId) {
                    setMessage('발주 ID가 없습니다.', 'error');
                    return;
                }
                setMessage('발주 정보를 불러오는 중입니다.');
                try {
                    const response = await fetch(`${apiOrigin}/api/v1/inventory/purchase-orders/${orderId}`, { headers: headers(false) });
                    if (!response.ok) throw new Error(await readError(response));
                    order = await response.json();
                    fillForm();
                    setMessage(`${order.purchaseOrderNo || ''} 발주 정보를 조회했습니다.`, 'ok');
                } catch (error) {
                    setMessage(`발주 상세 조회 실패: ${error.message}`, 'error');
                    itemsBody.innerHTML = `<tr><td colspan="6" class="text-center text-red-600 font-semibold py-8">${escapeHtml(error.message)}</td></tr>`;
                }
            }

            async function saveOrder() {
                if (!order) return;
                const payload = collectPayload();
                if (payload.items.some(item => !item.quantity || item.quantity < 1 || item.unitPrice < 0)) {
                    setMessage('품목 수량과 단가를 확인해 주세요.', 'error');
                    return;
                }

                const original = saveButton.innerHTML;
                saveButton.disabled = true;
                saveButton.innerHTML = '저장 중...';
                setMessage('발주 정보를 저장하는 중입니다.');
                try {
                    const response = await fetch(`${apiOrigin}/api/v1/inventory/purchase-orders/${order.purchaseOrderId}`, {
                        method: 'PUT',
                        headers: headers(true),
                        body: JSON.stringify(payload)
                    });
                    if (!response.ok) throw new Error(await readError(response));
                    order = await response.json();
                    fillForm();
                    setMessage('발주 정보가 저장되었습니다.', 'ok');
                } catch (error) {
                    setMessage(`발주 저장 실패: ${error.message}`, 'error');
                } finally {
                    saveButton.disabled = false;
                    saveButton.innerHTML = original;
                    if (window.lucide) lucide.createIcons();
                }
            }

            async function approveOrder() {
                if (!canApprove()) return;

                const original = approveButton.innerHTML;
                approveButton.disabled = true;
                approveButton.innerHTML = '승인 중...';
                setMessage(`${order.purchaseOrderNo || ''} 승인 처리 중입니다.`);
                try {
                    const response = await fetch(`${apiOrigin}/api/v1/inventory/purchase-orders/${order.purchaseOrderId}/status`, {
                        method: 'PATCH',
                        headers: headers(true),
                        body: JSON.stringify({ status: 'APPROVED' })
                    });
                    if (!response.ok) throw new Error(await readError(response));
                    order = await response.json();
                    fillForm();
                    setMessage('발주가 승인되었습니다.', 'ok');
                } catch (error) {
                    setMessage(`승인 실패: ${error.message}`, 'error');
                    updateApproveButton();
                } finally {
                    approveButton.innerHTML = original;
                    updateApproveButton();
                    if (window.lucide) lucide.createIcons();
                }
            }

            document.getElementById('btn-back').addEventListener('click', () => {
                location.href = 'purchase-orders.html';
            });
            approveButton.addEventListener('click', approveOrder);
            saveButton.addEventListener('click', saveOrder);
            loadOrder();
        });
    
