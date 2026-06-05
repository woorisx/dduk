document.addEventListener('DOMContentLoaded', () => {
    const session = window.ddukSession?.requireRole?.(['ADMIN', 'INVENTORY', 'HR'], { redirectToLogin: true });
    if (!session) return;

    if (window.lucide) {
        window.lucide.createIcons();
    }

    const ordersBody = document.getElementById('orders-body');
    const itemsBody = document.getElementById('items-body');
    const message = document.getElementById('message');
    const selectedOrderText = document.getElementById('selected-order');
    const receiveButton = document.getElementById('btn-receive');
    const cancelButton = document.getElementById('btn-cancel');
    const keywordInput = document.getElementById('keyword');

    let orders = [];
    let selectedOrder = null;
    let currentPage = 1;
    const pageSize = 10;

    function escapeHtml(value) {
        return String(value ?? '').replace(/[&<>"']/g, (char) => ({
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#039;'
        }[char]));
    }

    function money(value) {
        return Number(value || 0).toLocaleString('ko-KR', {
            style: 'currency',
            currency: 'KRW',
            maximumFractionDigits: 0
        });
    }

    function setMessage(text, type = 'info') {
        const color = type === 'error'
            ? 'text-red-600'
            : type === 'ok'
                ? 'text-emerald-600'
                : 'text-gray-500';
        message.className = `text-sm mt-1 font-semibold ${color}`;
        message.textContent = text;
    }

    function memberText(id, name) {
        if (Number(id) === 1) return `최고관리자 (${name || 'admin'}/1)`;
        if (Number(id) === 2) return `구매/발주 담당 (${name || 'inventory'}/2)`;
        if (Number(id) === 3) return `회계/직원 관리 담당 (${name || 'hr'}/3)`;
        return name || `ID ${id || '-'}`;
    }

    function statusText(status) {
        return {
            DRAFT: '발주작성',
            ORDERED: '발주요청',
            PENDING: '보류',
            REQUESTED: '요청됨',
            APPROVED: '승인됨',
            SENT_TO_VENDOR: '거래처 발송',
            INBOUND_DELAY: '입고지연',
            RECEIVING: '입고중',
            RECEIVED: '입고완료',
            COMPLETED: '완료됨',
            CANCELLED: '취소됨'
        }[status] || status || '-';
    }

    function canSelectOrder(order) {
        return order.status === 'APPROVED' || order.status === 'SENT_TO_VENDOR';
    }

    function clearSelection() {
        selectedOrder = null;
        renderItems();
    }

    async function requestList(path) {
        return window.ddukApi.requestList(path, { method: 'GET' });
    }

    async function requestData(path, options) {
        return window.ddukApi.requestData(path, options);
    }

    function renderOrders() {
        const totalPages = Math.max(1, Math.ceil(orders.length / pageSize));
        if (currentPage > totalPages) currentPage = totalPages;

        if (orders.length === 0) {
            ordersBody.innerHTML = '<tr><td colspan="5" class="text-center text-gray-400 font-semibold py-8">조회된 입고 전 발주서가 없습니다.</td></tr>';
            renderPagination(totalPages);
            return;
        }

        const startIdx = (currentPage - 1) * pageSize;
        const pageOrders = orders.slice(startIdx, startIdx + pageSize);

        ordersBody.innerHTML = pageOrders.map((order) => `
            <tr>
                <td>
                    <a href="purchase-order-detail.html?id=${encodeURIComponent(order.purchaseOrderId)}" target="_blank" class="font-extrabold text-indigo-700 hover:text-indigo-900 hover:underline">
                        ${escapeHtml(order.purchaseOrderNo)}
                    </a>
                    <span class="status-badge mt-2 block w-max">${escapeHtml(statusText(order.status))}</span>
                </td>
                <td>
                    <p class="font-bold text-gray-800">${escapeHtml(order.vendorName || '-')}</p>
                    <p class="text-xs text-gray-500 mt-1">납기예정 ${escapeHtml((order.expectedDate || '').slice(0, 10) || '-')}</p>
                </td>
                <td>
                    <p class="text-xs text-gray-500">요청 ${escapeHtml(memberText(order.requestedByMemberId, order.requestedByMemberName))}</p>
                    <p class="text-xs text-gray-500 mt-1">승인 ${escapeHtml(memberText(order.approvedByMemberId, order.approvedByMemberName))}</p>
                </td>
                <td class="font-bold text-gray-900">${money(order.totalAmount)}</td>
                <td class="action-cell">
                    <button type="button" class="select-order px-3 py-2 text-xs font-bold rounded-lg bg-indigo-50 text-indigo-700 hover:bg-indigo-100 disabled:hover:bg-gray-100" data-id="${escapeHtml(order.purchaseOrderId)}" ${canSelectOrder(order) ? '' : 'disabled'} title="${canSelectOrder(order) ? '선택하여 입고 진행' : '승인 또는 거래처 발송 상태의 발주만 선택 가능합니다.'}">
                        선택
                    </button>
                </td>
            </tr>
        `).join('');

        ordersBody.querySelectorAll('.select-order:not(:disabled)').forEach((button) => {
            button.addEventListener('click', () => selectOrder(Number(button.dataset.id)));
        });

        renderPagination(totalPages);
    }

    function renderPagination(totalPages) {
        const paginationArea = document.getElementById('orders-pagination');
        if (!paginationArea) return;

        if (totalPages <= 1) {
            paginationArea.innerHTML = '';
            return;
        }

        let html = '';
        html += `<button type="button" class="rv-btn nav" data-pg="${currentPage - 1}" ${currentPage <= 1 ? 'disabled' : ''}>&laquo; 이전</button>`;

        const maxVisible = 5;
        let startPage = Math.max(1, currentPage - Math.floor(maxVisible / 2));
        let endPage = Math.min(totalPages, startPage + maxVisible - 1);
        if (endPage - startPage + 1 < maxVisible) startPage = Math.max(1, endPage - maxVisible + 1);

        if (startPage > 1) {
            html += `<button type="button" class="rv-btn" data-pg="1">1</button>`;
            if (startPage > 2) html += `<span style="padding:0 .25rem;color:#9ca3af">…</span>`;
        }
        for (let i = startPage; i <= endPage; i++) {
            html += `<button type="button" class="rv-btn ${i === currentPage ? 'active' : ''}" data-pg="${i}">${i}</button>`;
        }
        if (endPage < totalPages) {
            if (endPage < totalPages - 1) html += `<span style="padding:0 .25rem;color:#9ca3af">…</span>`;
            html += `<button type="button" class="rv-btn" data-pg="${totalPages}">${totalPages}</button>`;
        }
        html += `<button type="button" class="rv-btn nav" data-pg="${currentPage + 1}" ${currentPage >= totalPages ? 'disabled' : ''}>다음 &raquo;</button>`;

        paginationArea.innerHTML = html;
        paginationArea.querySelectorAll('[data-pg]').forEach((btn) => {
            btn.addEventListener('click', () => {
                const p = Number(btn.dataset.pg);
                if (p >= 1 && p <= totalPages && p !== currentPage) {
                    currentPage = p;
                    const s = (currentPage - 1) * pageSize;
                    setMessage(`총 ${orders.length}건 중 ${s + 1}~${Math.min(s + pageSize, orders.length)}건 표시`, 'ok');
                    renderOrders();
                    ordersBody.closest('section')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
                }
            });
        });
    }

    function renderItems() {
        if (!selectedOrder) {
            selectedOrderText.textContent = '선택된 발주서가 없습니다.';
            itemsBody.innerHTML = '<tr><td colspan="4" class="text-center text-gray-400 font-semibold py-8">발주서를 먼저 선택하세요.</td></tr>';
            receiveButton.disabled = true;
            cancelButton.disabled = true;
            return;
        }

        selectedOrderText.textContent = `${selectedOrder.purchaseOrderNo} / ${selectedOrder.vendorName}`;
        receiveButton.disabled = false;
        cancelButton.disabled = false;
        itemsBody.innerHTML = (selectedOrder.items || []).map((item) => `
            <tr>
                <td>
                    <p class="font-bold text-gray-900">${escapeHtml(item.itemName || '-')}</p>
                    <p class="text-xs text-gray-400 mt-1">품목 ID ${escapeHtml(item.itemId)} / ${escapeHtml(item.unit || '-')}</p>
                </td>
                <td class="font-bold text-emerald-700">${Number(item.quantity || 0).toLocaleString()}</td>
                <td>${money(item.unitPrice)}</td>
                <td class="font-bold text-gray-900">${money(item.lineAmount)}</td>
            </tr>
        `).join('');
    }

    function selectOrder(orderId) {
        selectedOrder = orders.find((order) => Number(order.purchaseOrderId) === Number(orderId)) || null;
        renderItems();
    }

    async function loadOrders(keyword) {
        clearSelection();
        ordersBody.innerHTML = '<tr><td colspan="5" class="text-center text-gray-400 font-semibold py-8">데이터를 불러오는 중입니다...</td></tr>';
        setMessage('입고 전 발주 데이터를 불러오는 중입니다...');
        const paginationArea = document.getElementById('orders-pagination');
        if (paginationArea) paginationArea.innerHTML = '';

        try {
            const query = keyword ? `?keyword=${encodeURIComponent(keyword)}` : '';
            orders = await requestList(`/api/v1/inventory/purchase-orders/receivable${query}`);
            currentPage = 1;
            const showCount = Math.min(pageSize, orders.length);
            setMessage(`총 ${orders.length}건 중 ${orders.length > 0 ? 1 : 0}~${showCount}건 표시`, 'ok');
            renderOrders();
        } catch (error) {
            orders = [];
            ordersBody.innerHTML = `<tr><td colspan="5" class="text-center text-red-600 font-semibold py-8">조회 실패: ${escapeHtml(error.message)}</td></tr>`;
            setMessage(`조회 실패: ${error.message}`, 'error');
        }
    }

    async function receiveOrder() {
        if (!selectedOrder) return;
        if (!window.confirm(`${selectedOrder.purchaseOrderNo} 발주 건을 입고 처리하시겠습니까?`)) return;

        const originalReceive = receiveButton.innerHTML;
        receiveButton.disabled = true;
        cancelButton.disabled = true;
        receiveButton.innerHTML = '입고 등록 중...';
        setMessage(`${selectedOrder.purchaseOrderNo} 입고 처리 중...`);

        try {
            const received = await requestData(`/api/v1/inventory/purchase-orders/${selectedOrder.purchaseOrderId}/receive`, {
                method: 'POST'
            });
            orders = orders.filter((order) => Number(order.purchaseOrderId) !== Number(received.purchaseOrderId));
            clearSelection();
            renderOrders();
            setMessage(`${received.purchaseOrderNo} 입고 등록이 완료되었습니다.`, 'ok');
        } catch (error) {
            setMessage(`입고 실패: ${error.message}`, 'error');
            receiveButton.disabled = false;
            cancelButton.disabled = false;
        } finally {
            receiveButton.innerHTML = originalReceive;
            if (window.lucide) {
                window.lucide.createIcons();
            }
        }
    }

    async function cancelOrder() {
        if (!selectedOrder) return;
        if (!window.confirm(`입고 전에 ${selectedOrder.purchaseOrderNo} 발주 건을 취소하시겠습니까?`)) return;

        const originalCancel = cancelButton.innerHTML;
        receiveButton.disabled = true;
        cancelButton.disabled = true;
        cancelButton.innerHTML = '취소 처리 중...';
        setMessage(`${selectedOrder.purchaseOrderNo} 취소 처리 중...`);

        try {
            const cancelled = await requestData(`/api/v1/inventory/purchase-orders/${selectedOrder.purchaseOrderId}/cancel`, {
                method: 'POST'
            });
            orders = orders.filter((order) => Number(order.purchaseOrderId) !== Number(cancelled.purchaseOrderId));
            clearSelection();
            renderOrders();
            setMessage(`${cancelled.purchaseOrderNo} 발주 건이 취소되었습니다.`, 'ok');
        } catch (error) {
            setMessage(`취소 실패: ${error.message}`, 'error');
            receiveButton.disabled = false;
            cancelButton.disabled = false;
        } finally {
            cancelButton.innerHTML = originalCancel;
            if (window.lucide) {
                window.lucide.createIcons();
            }
        }
    }

    let searchTimer = null;
    function debounceSearch() {
        clearTimeout(searchTimer);
        searchTimer = setTimeout(() => {
            const keyword = keywordInput.value.trim();
            loadOrders(keyword);
        }, 300);
    }

    keywordInput?.addEventListener('input', debounceSearch);

    document.getElementById('search-form')?.addEventListener('submit', (event) => {
        event.preventDefault();
        clearTimeout(searchTimer);
        const keyword = keywordInput.value.trim();
        loadOrders(keyword);
    });

    document.getElementById('btn-load-all')?.addEventListener('click', () => {
        keywordInput.value = '';
        loadOrders('');
    });

    receiveButton?.addEventListener('click', receiveOrder);
    cancelButton?.addEventListener('click', cancelOrder);

    // 페이지 접속 시 자동 전체 조회 실행
    loadOrders('');
});
