document.addEventListener('DOMContentLoaded', () => {
    const session = window.ddukSession?.requireRole?.(['ADMIN', 'INVENTORY', 'HR'], { redirectToLogin: true });
    if (!session) return;

    if (window.lucide) {
        window.lucide.createIcons();
    }

    const body = document.getElementById('orders-body');
    const message = document.getElementById('message');
    const paginationArea = document.getElementById('pagination-area');
    const searchForm = document.getElementById('search-form');
    const keywordInput = document.getElementById('keyword');
    const currentUser = document.getElementById('current-user');
    const pageSize = 10;

    const memberProfiles = {
        1: { loginId: 'admin', label: 'Admin' },
        2: { loginId: 'inventory', label: 'Inventory' },
        3: { loginId: 'hr', label: 'HR' }
    };

    let orders = [];
    let currentPage = 1;
    let totalPages = 1;

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

    function dateText(value) {
        return value ? String(value).slice(0, 10) : '-';
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

    function resolveCurrentMemberId() {
        const loginId = (localStorage.getItem('loginId') || '').toLowerCase();
        const role = (localStorage.getItem('role') || '').toUpperCase();
        if (loginId === 'admin' || role === 'ADMIN') return 1;
        if (loginId === 'inventory' || role === 'INVENTORY') return 2;
        if (loginId === 'hr' || role === 'HR') return 3;
        const stored = localStorage.getItem('memberId') || localStorage.getItem('userId');
        return stored ? Number(stored) : null;
    }

    function memberText(id, name) {
        const profile = memberProfiles[Number(id)];
        if (!profile) {
            return name || `ID ${id || '-'}`;
        }
        return `${profile.label} (${profile.loginId}/${id})`;
    }

    function statusText(status) {
        return {
            DRAFT: 'Draft',
            ORDERED: 'Ordered',
            PENDING: 'Pending',
            REQUESTED: 'Requested',
            APPROVED: 'Approved',
            SENT_TO_VENDOR: 'Sent',
            INBOUND_DELAY: 'Inbound delay',
            RECEIVING: 'Receiving',
            RECEIVED: 'Received',
            COMPLETED: 'Completed',
            CANCELLED: 'Cancelled'
        }[status] || status || '-';
    }

    function itemSummary(order) {
        const items = Array.isArray(order.items) ? order.items : [];
        if (items.length === 0) return 'No items';
        const names = items
            .slice(0, 2)
            .map((item) => item.itemName || item.name || `Item ${item.itemId}`)
            .join(', ');
        return items.length > 2 ? `${names} +${items.length - 2}` : names;
    }

    function canApprove(order) {
        return session.role === 'ADMIN' && ['ORDERED', 'PENDING', 'REQUESTED'].includes(order.status);
    }

    function approveTitle(order) {
        if (order.status === 'APPROVED') return 'Already approved';
        if (!['ORDERED', 'PENDING', 'REQUESTED'].includes(order.status)) return 'Only requested orders can be approved';
        if (session.role !== 'ADMIN') return 'Admin only';
        return 'Approve';
    }

    async function requestList(path) {
        return window.ddukApi.requestList(path, { method: 'GET' });
    }

    async function requestData(path, options) {
        return window.ddukApi.requestData(path, options);
    }

    function render() {
        totalPages = Math.max(1, Math.ceil(orders.length / pageSize));
        if (currentPage > totalPages) currentPage = totalPages;

        const startIdx = (currentPage - 1) * pageSize;
        const pageOrders = orders.slice(startIdx, startIdx + pageSize);

        if (orders.length === 0) {
            body.innerHTML = '<tr><td colspan="7" class="text-center text-gray-400 font-semibold py-8">조회된 발주가 없습니다.</td></tr>';
        } else {
            body.innerHTML = pageOrders.map((order) => `
                <tr>
                    <td>
                        <a class="font-extrabold text-indigo-700 hover:text-indigo-900 hover:underline" href="purchase-order-detail.html?id=${encodeURIComponent(order.purchaseOrderId)}">
                            ${escapeHtml(order.purchaseOrderNo || '-')}
                        </a>
                        <p class="text-xs text-gray-400 mt-1">ID ${escapeHtml(order.purchaseOrderId || '-')}</p>
                    </td>
                    <td>
                        <p class="font-bold text-gray-800">${escapeHtml(order.vendorName || '-')}</p>
                        <p class="text-xs text-gray-500 mt-1">${escapeHtml(itemSummary(order))}</p>
                    </td>
                    <td><span class="member-pill">${escapeHtml(memberText(order.requestedByMemberId, order.requestedByMemberName))}</span></td>
                    <td><span class="member-pill">${escapeHtml(memberText(order.approvedByMemberId, order.approvedByMemberName))}</span></td>
                    <td>
                        <p class="font-bold text-gray-900">${money(order.totalAmount)}</p>
                        <p class="text-xs text-gray-500 mt-1">Requested ${dateText(order.orderDate)} / ETA ${dateText(order.expectedDate)}</p>
                    </td>
                    <td><span class="status-badge status-${escapeHtml(order.status)}">${escapeHtml(statusText(order.status))}</span></td>
                    <td class="action-cell">
                        <button type="button" class="approval-btn" data-id="${escapeHtml(order.purchaseOrderId)}" ${canApprove(order) ? '' : 'disabled'} title="${escapeHtml(approveTitle(order))}">
                            <i data-lucide="check-circle" class="w-4 h-4"></i> Approve
                        </button>
                    </td>
                </tr>
            `).join('');
        }

        renderPagination();
        document.querySelectorAll('.approval-btn[data-id]').forEach((button) => {
            button.addEventListener('click', () => approveOrder(Number(button.dataset.id)));
        });

        if (window.lucide) {
            window.lucide.createIcons();
        }
    }

    function renderPagination() {
        if (totalPages <= 1) {
            paginationArea.innerHTML = '';
            return;
        }

        let html = '<div class="pagination">';

        // 이전 버튼
        html += `<button type="button" class="page-btn nav-btn" data-page="${currentPage - 1}" ${currentPage <= 1 ? 'disabled' : ''}>&laquo; 이전</button>`;

        // 페이지 번호 (최대 5개 표시)
        const maxVisible = 5;
        let startPage = Math.max(1, currentPage - Math.floor(maxVisible / 2));
        let endPage = Math.min(totalPages, startPage + maxVisible - 1);
        if (endPage - startPage + 1 < maxVisible) {
            startPage = Math.max(1, endPage - maxVisible + 1);
        }

        if (startPage > 1) {
            html += `<button type="button" class="page-btn" data-page="1">1</button>`;
            if (startPage > 2) html += `<span style="padding:0 .25rem;color:#9ca3af">…</span>`;
        }

        for (let i = startPage; i <= endPage; i++) {
            html += `<button type="button" class="page-btn ${i === currentPage ? 'active' : ''}" data-page="${i}">${i}</button>`;
        }

        if (endPage < totalPages) {
            if (endPage < totalPages - 1) html += `<span style="padding:0 .25rem;color:#9ca3af">…</span>`;
            html += `<button type="button" class="page-btn" data-page="${totalPages}">${totalPages}</button>`;
        }

        // 다음 버튼
        html += `<button type="button" class="page-btn nav-btn" data-page="${currentPage + 1}" ${currentPage >= totalPages ? 'disabled' : ''}>다음 &raquo;</button>`;

        html += '</div>';
        paginationArea.innerHTML = html;

        // 페이지 버튼 이벤트 바인딩
        paginationArea.querySelectorAll('.page-btn[data-page]').forEach((btn) => {
            btn.addEventListener('click', () => {
                const page = Number(btn.dataset.page);
                if (page >= 1 && page <= totalPages && page !== currentPage) {
                    currentPage = page;
                    const startIdx = (currentPage - 1) * pageSize;
                    setMessage(`총 ${orders.length}건 중 ${startIdx + 1}~${Math.min(startIdx + pageSize, orders.length)}건 표시`, 'ok');
                    render();
                    // 테이블 상단으로 스크롤
                    body.closest('section')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
                }
            });
        });
    }

    async function searchOrders() {
        const keyword = keywordInput.value.trim();

        body.innerHTML = '<tr><td colspan="7" class="text-center text-gray-400 font-semibold py-8">데이터를 불러오는 중입니다...</td></tr>';
        paginationArea.innerHTML = '';
        setMessage('데이터를 불러오는 중입니다...');

        try {
            const url = keyword
                ? `/api/v1/inventory/purchase-orders?keyword=${encodeURIComponent(keyword)}`
                : '/api/v1/inventory/purchase-orders?all=true';
            orders = await requestList(url);
            currentPage = 1;
            const showCount = Math.min(pageSize, orders.length);
            setMessage(`총 ${orders.length}건 중 1~${showCount}건 표시`, 'ok');
            render();
        } catch (error) {
            orders = [];
            body.innerHTML = `<tr><td colspan="7" class="text-center text-red-600 font-semibold py-8">조회 실패: ${escapeHtml(error.message)}</td></tr>`;
            setMessage(`조회 실패: ${error.message}`, 'error');
        }
    }

    async function loadAllOrders() {
        keywordInput.value = '';
        body.innerHTML = '<tr><td colspan="7" class="text-center text-gray-400 font-semibold py-8">데이터를 불러오는 중입니다...</td></tr>';
        paginationArea.innerHTML = '';
        setMessage('데이터를 불러오는 중입니다...');

        try {
            orders = await requestList('/api/v1/inventory/purchase-orders?all=true');
            currentPage = 1;
            const showCount = Math.min(pageSize, orders.length);
            setMessage(`총 ${orders.length}건 중 1~${showCount}건 표시`, 'ok');
            render();
        } catch (error) {
            orders = [];
            body.innerHTML = `<tr><td colspan="7" class="text-center text-red-600 font-semibold py-8">조회 실패: ${escapeHtml(error.message)}</td></tr>`;
            setMessage(`조회 실패: ${error.message}`, 'error');
        }
    }

    async function approveOrder(orderId) {
        const order = orders.find((item) => Number(item.purchaseOrderId) === Number(orderId));
        if (!order) return;
        if (!canApprove(order)) {
            setMessage(approveTitle(order), 'error');
            return;
        }

        setMessage(`Approving ${order.purchaseOrderNo}...`);
        try {
            const updated = await requestData(`/api/v1/inventory/purchase-orders/${orderId}/status`, {
                method: 'PATCH',
                body: { status: 'APPROVED' }
            });
            orders = orders.filter((item) => Number(item.purchaseOrderId) !== Number(orderId));
            setMessage(`${updated.purchaseOrderNo || order.purchaseOrderNo} approved.`, 'ok');
            render();
        } catch (error) {
            setMessage(`Approve failed: ${error.message}`, 'error');
        }
    }

    currentUser.textContent = `Current user: ${memberText(resolveCurrentMemberId(), session.userName)}`;

    searchForm?.addEventListener('submit', (event) => {
        event.preventDefault();
        searchOrders();
    });

    // 페이지 접속 시 자동으로 전체 데이터 로드
    loadAllOrders();
});
