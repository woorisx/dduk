document.addEventListener('DOMContentLoaded', () => {
    const session = window.ddukSession?.requireRole?.(['ADMIN', 'INVENTORY', 'HR'], { redirectToLogin: true });
    if (!session) return;

    if (window.lucide) {
        window.lucide.createIcons();
    }

    const form = document.getElementById('vendor-form');
    const saveButton = document.getElementById('btn-save');
    const message = document.getElementById('message');
    const addressInput = document.getElementById('address');
    const zonecodeInput = document.getElementById('zonecode');
    const detailAddressInput = document.getElementById('detailAddress');
    const addressSearchButton = document.getElementById('btn-search-address');

    let kakaoPostcodeLoadPromise = null;

    function setMessage(text, type = 'info') {
        const color = type === 'error'
            ? 'text-red-600'
            : type === 'ok'
                ? 'text-emerald-600'
                : 'text-gray-500';
        message.className = `mt-5 min-h-6 text-sm font-semibold ${color}`;
        message.textContent = text;
    }

    async function requestData(path, options) {
        return window.ddukApi.requestData(path, options);
    }

    function loadKakaoPostcode() {
        if (window.daum?.Postcode) {
            return Promise.resolve();
        }
        if (kakaoPostcodeLoadPromise) {
            return kakaoPostcodeLoadPromise;
        }

        kakaoPostcodeLoadPromise = new Promise((resolve, reject) => {
            const script = document.createElement('script');
            const timeoutId = window.setTimeout(() => reject(new Error('postcode api timeout')), 10000);
            script.src = 'https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js';
            script.dataset.kakaoPostcode = 'true';
            script.onload = () => {
                window.clearTimeout(timeoutId);
                resolve();
            };
            script.onerror = () => {
                window.clearTimeout(timeoutId);
                reject(new Error('postcode api load failed'));
            };
            document.head.appendChild(script);
        }).then(() => {
            if (!window.daum?.Postcode) {
                throw new Error('postcode api unavailable');
            }
        }).catch((error) => {
            kakaoPostcodeLoadPromise = null;
            throw error;
        });

        return kakaoPostcodeLoadPromise;
    }

    async function openAddressSearch() {
        try {
            await loadKakaoPostcode();
        } catch (error) {
            setMessage('Failed to load postcode service.', 'error');
            return;
        }

        const postcode = new window.daum.Postcode({
            oncomplete(data) {
                const baseAddress = data.userSelectedType === 'R' ? data.roadAddress : data.jibunAddress;
                const extraAddress = data.userSelectedType === 'R'
                    ? [data.bname, data.buildingName].filter(Boolean).join(', ')
                    : '';

                zonecodeInput.value = data.zonecode || '';
                addressInput.value = extraAddress ? `${baseAddress} (${extraAddress})` : baseAddress;
                detailAddressInput.focus();
            }
        });

        postcode.open({
            popupTitle: 'Address search',
            popupKey: 'dduk-vendor-address-search',
            autoClose: true
        });
    }

    addressSearchButton?.addEventListener('click', openAddressSearch);

    saveButton?.addEventListener('click', async () => {
        if (!form.checkValidity()) {
            form.reportValidity();
            return;
        }

        const payload = Object.fromEntries(new FormData(form).entries());
        const detailAddress = payload.detailAddress ? payload.detailAddress.trim() : '';
        payload.address = detailAddress ? `${payload.address} ${detailAddress}` : payload.address;
        delete payload.zonecode;
        delete payload.detailAddress;

        const original = saveButton.innerHTML;
        saveButton.disabled = true;
        saveButton.innerHTML = '<i data-lucide="loader-2" class="w-4 h-4 animate-spin"></i> Saving...';
        if (window.lucide) {
            window.lucide.createIcons();
        }

        try {
            const vendor = await requestData('/api/v1/inventory/vendors', {
                method: 'POST',
                body: payload
            });
            setMessage('Vendor registered.', 'ok');
            window.location.href = `vendors.html?vendorId=${encodeURIComponent(vendor.id)}`;
        } catch (error) {
            setMessage(`Registration failed: ${error.message}`, 'error');
            saveButton.disabled = false;
            saveButton.innerHTML = original;
            if (window.lucide) {
                window.lucide.createIcons();
            }
        }
    });
});
