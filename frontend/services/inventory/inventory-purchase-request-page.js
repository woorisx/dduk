document.addEventListener('DOMContentLoaded', () => {
    const session = window.ddukSession?.requireRole?.(['ADMIN', 'INVENTORY', 'HR'], { redirectToLogin: true });
    if (!session) return;

    if (window.lucide) {
        window.lucide.createIcons();
    }

    const TAX_RATE = 0.1;
    const vendorResult = document.getElementById('vendor-result');
    const itemResult = document.getElementById('item-result');
    const orderBody = document.getElementById('order-items');
    const message = document.getElementById('message');
    const saveButton = document.getElementById('btn-save');
    const requestedByInput = document.getElementById('requested-by-member-id');
    const approvedBySelect = document.getElementById('approved-by-member-id');
    const vendorIdInput = document.getElementById('vendor-id');
    const vendorNameInput = document.getElementById('vendor-name');
    const itemNameInput = document.getElementById('item-name');
    const expectedDateInput = document.getElementById('expected-date');
    const orderNoteInput = document.getElementById('order-note');

    let selectedVendor = null;
    let orderItems = [];
    let vendorSearchTimer = null;
    let itemSearchTimer = null;
    let lastVendorKeyword = '';

    function resolveCurrentMemberId() {
        const loginId = (localStorage.getItem('loginId') || '').toLowerCase();
        const role = (localStorage.getItem('role') || '').toUpperCase();
        if (loginId === 'inventory' || role === 'INVENTORY') return '2';
        if (loginId === 'hr' || role === 'HR') return '3';
        if (loginId === 'admin' || role === 'ADMIN') return '1';
        return localStorage.getItem('memberId') || localStorage.getItem('userId') || '1';
    }

    function text(value) {
        return value == null || value === '' ? '-' : value;
    }

    function money(value) {
        return Number(value || 0).toLocaleString('ko-KR', { maximumFractionDigits: 2 });
    }

    function setMessage(textValue, type = 'info') {
        const color = type === 'error'
            ? 'text-red-600'
            : type === 'ok'
                ? 'text-emerald-600'
                : 'text-gray-500';
        message.className = `text-sm mt-1 font-semibold ${color}`;
        message.textContent = textValue;
    }

    function warnRequired(messageText, target) {
        setMessage(messageText, 'error');
        window.ddukApi?.showToast?.(messageText, 'warning');
        target?.focus?.();
    }

    async function requestList(path, fallbackMessage) {
        try {
            return await window.ddukApi.requestList(path, { method: 'GET' });
        } catch (error) {
            throw new Error(error.message || fallbackMessage);
        }
    }

    function debounceSearch(timer, callback) {
        if (timer) {
            clearTimeout(timer);
        }
        return setTimeout(callback, 250);
    }

    function renderVendors(list) {
        if (!Array.isArray(list) || list.length === 0) {
            vendorResult.innerHTML = '<tr><td colspan="5" class="text-center text-gray-400 font-semibold py-7">조회된 거래처가 없습니다.</td></tr>';
            return;
        }

        vendorResult.innerHTML = list.map((vendor) => {
            const isSelected = selectedVendor && String(selectedVendor.id) === String(vendor.id);
            const rowClass = isSelected ? 'bg-emerald-50' : '';
            const buttonClass = isSelected
                ? 'bg-emerald-600 text-white'
                : 'bg-indigo-50 text-indigo-700';
            const buttonText = isSelected ? '선택됨' : '선택';

            return `
            <tr class="${rowClass}">
                <td class="font-bold">${text(vendor.vendorCode)}</td>
                <td>${text(vendor.name)}</td>
                <td>${text(vendor.representativeName)}</td>
                <td>${text(vendor.contactPhone)}</td>
                <td>
                    <button type="button" class="select-vendor px-3 py-2 text-xs font-bold rounded-lg ${buttonClass}" data-id="${vendor.id}" aria-pressed="${isSelected}">
                        ${buttonText}
                    </button>
                </td>
            </tr>
        `;
        }).join('');

        vendorResult.querySelectorAll('.select-vendor').forEach((button) => {
            button.addEventListener('click', () => {
                selectedVendor = list.find((vendor) => String(vendor.id) === button.dataset.id) || null;
                if (!selectedVendor) {
                    return;
                }
                vendorIdInput.value = selectedVendor.id;
                vendorNameInput.value = selectedVendor.name || vendorNameInput.value;
                lastVendorKeyword = vendorNameInput.value.trim();
                renderVendors(list);
                setMessage('거래처가 선택되었습니다.', 'ok');
            });
        });
    }

    function addItem(item) {
        const existing = orderItems.find((candidate) => candidate.itemId === item.id);
        if (existing) {
            existing.quantity += 1;
        } else {
            orderItems.push({
                itemId: item.id,
                itemCode: item.itemCode,
                name: item.name,
                unit: item.unit,
                quantity: 1,
                unitPrice: Number(item.unitPrice || 0)
            });
        }
        renderOrder();
        setMessage(`품목이 추가되었습니다: ${text(item.name)}`, 'ok');
    }

    function openItemPopup() {
        const params = new URLSearchParams({
            vendorId: selectedVendor ? selectedVendor.id : '',
            vendorName: selectedVendor ? selectedVendor.name : '',
            name: itemNameInput.value.trim(),
            registeredById: requestedByInput.value
        });
        const popup = window.open(
            `item-register-popup.html?${params}`,
            'ddukItemRegister',
            'width=760,height=780,menubar=no,toolbar=no,location=no,status=no'
        );
        if (!popup) {
            setMessage('팝업이 차단되었습니다.', 'error');
        }
    }

    function renderItems(list) {
        if (!Array.isArray(list) || list.length === 0) {
            itemResult.innerHTML = `
                <tr>
                    <td colspan="5" class="text-center py-7">
                        <p class="font-semibold text-gray-400">조회된 품목이 없습니다.</p>
                        <button type="button" id="btn-open-item-register" class="mt-3 px-4 py-2 text-sm font-bold rounded-lg bg-indigo-50 text-indigo-700">
                            품목 등록
                        </button>
                    </td>
                </tr>
            `;
            document.getElementById('btn-open-item-register')?.addEventListener('click', openItemPopup);
            return;
        }

        itemResult.innerHTML = list.map((item) => `
            <tr>
                <td class="font-bold">${text(item.itemCode)}</td>
                <td>${text(item.name)}</td>
                <td>${text(item.unit)}</td>
                <td>${money(item.unitPrice)}</td>
                <td class="whitespace-nowrap">
                    <button type="button" class="add-item whitespace-nowrap px-3 py-2 text-xs font-bold rounded-lg bg-indigo-50 text-indigo-700" data-id="${item.id}">
                        추가
                    </button>
                </td>
            </tr>
        `).join('');

        itemResult.querySelectorAll('.add-item').forEach((button) => {
            button.addEventListener('click', () => {
                const item = list.find((candidate) => String(candidate.id) === button.dataset.id);
                if (item) {
                    addItem(item);
                }
            });
        });
    }

    function renderOrder() {
        if (orderItems.length === 0) {
            orderBody.innerHTML = '<tr><td colspan="7" class="text-center text-gray-400 font-semibold py-8">No order items yet.</td></tr>';
            return;
        }

        orderBody.innerHTML = orderItems.map((item, index) => {
            const supply = item.quantity * item.unitPrice;
            const tax = supply * TAX_RATE;
            const total = supply + tax;
            return `
                <tr>
                    <td>
                        <p class="font-bold">${text(item.name)}</p>
                        <p class="text-xs text-gray-400">${text(item.itemCode)} / ${text(item.unit)}</p>
                    </td>
                    <td><input type="number" min="1" step="1" class="form-input qty" data-index="${index}" value="${item.quantity}"></td>
                    <td><input type="number" min="0" step="0.01" class="form-input price" data-index="${index}" value="${item.unitPrice}"></td>
                    <td>${money(supply)}</td>
                    <td>${money(tax)}</td>
                    <td class="font-bold">${money(total)}</td>
                    <td><button type="button" class="remove px-3 py-2 text-xs font-bold rounded-lg bg-red-50 text-red-600" data-index="${index}">Remove</button></td>
                </tr>
            `;
        }).join('');

        orderBody.querySelectorAll('.qty').forEach((input) => {
            input.addEventListener('change', () => {
                orderItems[Number(input.dataset.index)].quantity = Math.max(1, Number(input.value || 1));
                renderOrder();
            });
        });

        orderBody.querySelectorAll('.price').forEach((input) => {
            input.addEventListener('change', () => {
                orderItems[Number(input.dataset.index)].unitPrice = Math.max(0, Number(input.value || 0));
                renderOrder();
            });
        });

        orderBody.querySelectorAll('.remove').forEach((button) => {
            button.addEventListener('click', () => {
                orderItems.splice(Number(button.dataset.index), 1);
                renderOrder();
            });
        });
    }

    async function searchVendors() {
        const name = vendorNameInput.value.trim();
        if (!name) {
            vendorResult.innerHTML = '<tr><td colspan="5" class="text-center text-gray-400 font-semibold py-7">거래처명을 검색하세요.</td></tr>';
            selectedVendor = null;
            vendorIdInput.value = '';
            lastVendorKeyword = '';
            return;
        }

        if (name !== lastVendorKeyword) {
            selectedVendor = null;
            vendorIdInput.value = '';
            lastVendorKeyword = name;
        }
        vendorResult.innerHTML = '<tr><td colspan="5" class="text-center text-gray-400 font-semibold py-7">거래처 조회 중...</td></tr>';

        try {
            const vendors = await requestList(`/api/v1/inventory/vendors/search?keyword=${encodeURIComponent(name)}`, '거래처를 불러오지 못했습니다.');
            renderVendors(vendors);
        } catch (error) {
            vendorResult.innerHTML = `<tr><td colspan="5" class="text-center text-red-600 font-semibold py-7">거래처 조회 실패: ${text(error.message)}</td></tr>`;
        }
    }

    async function searchItems() {
        const name = itemNameInput.value.trim();
        if (!name) {
            itemResult.innerHTML = '<tr><td colspan="5" class="text-center text-gray-400 font-semibold py-7">품목명을 검색하세요.</td></tr>';
            return;
        }

        itemResult.innerHTML = '<tr><td colspan="5" class="text-center text-gray-400 font-semibold py-7">품목 조회 중...</td></tr>';

        try {
            const items = await requestList(`/api/v1/inventory/items/search?name=${encodeURIComponent(name)}`, '품목을 불러오지 못했습니다.');
            renderItems(items);
        } catch (error) {
            itemResult.innerHTML = `<tr><td colspan="5" class="text-center text-red-600 font-semibold py-7">품목 조회 실패: ${text(error.message)}</td></tr>`;
        }
    }

    async function save() {
        if (!selectedVendor) {
            warnRequired('거래처를 먼저 선택하세요.', vendorNameInput);
            return;
        }
        if (!expectedDateInput.value) {
            warnRequired('희망 납기일을 입력하세요.', expectedDateInput);
            return;
        }
        if (orderItems.length === 0) {
            warnRequired('발주 품목을 1개 이상 추가하세요.', itemNameInput);
            return;
        }

        const payload = {
            vendorId: selectedVendor.id,
            requestedByMemberId: Number(requestedByInput.value),
            approvedByMemberId: Number(approvedBySelect.value),
            expectedDate: expectedDateInput.value,
            note: orderNoteInput.value.trim(),
            items: orderItems.map((item) => ({
                itemId: item.itemId,
                quantity: item.quantity,
                unitPrice: item.unitPrice,
                expectedDate: expectedDateInput.value,
                note: ''
            }))
        };

        const original = saveButton.innerHTML;
        saveButton.disabled = true;
        saveButton.innerHTML = '저장 중...';

        try {
            const response = await window.ddukApi.post('/api/v1/inventory/purchase-orders', payload);
            const saved = window.ddukApi.unwrapData(response);
            setMessage(`구매 요청이 저장되었습니다: ${saved.purchaseOrderNo || '-'}`, 'ok');
            selectedVendor = null;
            vendorIdInput.value = '';
            vendorNameInput.value = '';
            vendorResult.innerHTML = '<tr><td colspan="5" class="text-center text-gray-400 font-semibold py-7">거래처명을 검색하세요.</td></tr>';
            orderItems = [];
            renderOrder();
        } catch (error) {
            const detail = error.message === 'FORBIDDEN'
                ? '현재 계정은 구매 요청 저장 권한이 없습니다. 재고 관리자 또는 관리자 계정으로 로그인하세요.'
                : error.message;
            setMessage(`저장 실패: ${detail}`, 'error');
        } finally {
            saveButton.disabled = false;
            saveButton.innerHTML = original;
            if (window.lucide) {
                window.lucide.createIcons();
            }
        }
    }

    const currentMemberId = resolveCurrentMemberId();
    requestedByInput.value = currentMemberId;
    approvedBySelect.innerHTML = [
        { id: 1, label: 'admin' },
        { id: 2, label: 'inventory' },
        { id: 3, label: 'hr' }
    ]
        .filter((member) => String(member.id) !== String(currentMemberId))
        .map((member) => `<option value="${member.id}">${member.label}</option>`)
        .join('');

    window.ddukPurchaseRequest = { addCreatedItem: addItem };
    window.addEventListener('message', (event) => {
        if (event.origin !== window.location.origin) return;
        if (event.data?.type === 'DDUK_ITEM_CREATED' && event.data.item) {
            addItem(event.data.item);
        }
    });

    // 추천 발주 페이지에서 넘어온 파라미터 처리
    const urlParams = new URLSearchParams(window.location.search);
    const queryItemId = urlParams.get('itemId');
    const queryQty = urlParams.get('qty');
    const queryItemName = urlParams.get('itemName');
    const queryUnit = urlParams.get('unit');

    if (queryItemId && queryItemName) {
        addItem({
            id: Number(queryItemId),
            name: queryItemName,
            unit: queryUnit || 'EA',
            unitPrice: 0
        });
        if (queryQty) {
            const added = orderItems.find((candidate) => candidate.itemId === Number(queryItemId));
            if (added) {
                added.quantity = Math.max(1, Number(queryQty));
                renderOrder();
            }
        }
    }

    document.getElementById('btn-vendor-search')?.addEventListener('click', searchVendors);
    document.getElementById('btn-item-search')?.addEventListener('click', searchItems);
    saveButton?.addEventListener('click', save);
    vendorNameInput?.addEventListener('keydown', (event) => {
        if (event.key === 'Enter') {
            event.preventDefault();
            searchVendors();
        }
    });
    vendorNameInput?.addEventListener('input', () => {
        vendorSearchTimer = debounceSearch(vendorSearchTimer, searchVendors);
    });
    itemNameInput?.addEventListener('keydown', (event) => {
        if (event.key === 'Enter') {
            event.preventDefault();
            searchItems();
        }
    });
    itemNameInput?.addEventListener('input', () => {
        itemSearchTimer = debounceSearch(itemSearchTimer, searchItems);
    });
});
