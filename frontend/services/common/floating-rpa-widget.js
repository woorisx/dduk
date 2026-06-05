(function () {
    const RPA_STATE_STORAGE_KEY = 'dduk_rpa_states';
    const rpaState = {
        taskId: null,
        actionName: null,
        pollingHandle: null,
        status: 'IDLE',
        lastUpdated: '-',
        message: null,
        tone: 'neutral'
    };

    const rpaTranslation = {
        taskTypes: {
            PURCHASE_PRICE: '구매가 수집',
            INVENTORY_SHORTAGE: '체화/임박 재고 분석',
            HR_MIN_WAGE: '급여 기준 조회'
        },
        actions: {
            COLLECT_EXTERNAL_PRICE: '외부 구매가 수집',
            CHECK_LOW_INVENTORY: '체화/임박 재고 분석',
            CHECK_INVENTORY_SHORTAGE: '체화/임박 재고 분석',
            COLLECT_HR_REFERENCE: '급여 기준 수집'
        }
    };

    const terminalStatuses = new Set(['SUCCESS', 'FAILED']);
    const activeStatuses = new Set(['REQUESTED', 'RUNNING']);

    function getRootPath() {
        return window.DDUK_COMMON ? window.DDUK_COMMON.getRootPath() : './';
    }

    function resolveHref(href) {
        return window.DDUK_COMMON ? window.DDUK_COMMON.resolveHref(href) : href;
    }

    function escapeHtml(value) {
        return window.DDUK_COMMON ? window.DDUK_COMMON.escapeHtml(value) : String(value ?? '');
    }

    function formatDateTime(value) {
        return window.DDUK_COMMON ? window.DDUK_COMMON.formatDateTime(value) : value;
    }

    function translateRpaType(type) {
        return rpaTranslation.taskTypes[type] || type || '-';
    }

    function translateRpaAction(action) {
        return rpaTranslation.actions[action] || action || '-';
    }

    function getCurrentRole() {
        if (window.ddukSession && typeof window.ddukSession.getSession === 'function') {
            return window.ddukSession.getSession()?.role || null;
        }
        return null;
    }

    function getApiBaseUrl() {
        return window.DDUK_COMMON ? window.DDUK_COMMON.getApiBaseUrl() : '';
    }

    function getHeaders(extraHeaders) {
        return window.DDUK_COMMON ? window.DDUK_COMMON.getHeaders(extraHeaders) : {
            'Content-Type': 'application/json',
            ...(extraHeaders || {})
        };
    }

    function loadRpaStates() {
        try {
            const raw = window.sessionStorage.getItem(RPA_STATE_STORAGE_KEY);
            if (!raw) {
                return {};
            }
            const parsed = JSON.parse(raw);
            return parsed && typeof parsed === 'object' ? parsed : {};
        } catch (error) {
            window.sessionStorage.removeItem(RPA_STATE_STORAGE_KEY);
            return {};
        }
    }

    function persistRpaStates(states) {
        try {
            window.sessionStorage.setItem(RPA_STATE_STORAGE_KEY, JSON.stringify(states));
        } catch (error) {
            console.warn('RPA 상태 저장에 실패했습니다.', error);
        }
    }

    function saveRpaState(taskType, patch) {
        if (!taskType) {
            return;
        }
        const states = loadRpaStates();
        const previous = states[taskType] && typeof states[taskType] === 'object' ? states[taskType] : {};
        states[taskType] = {
            ...previous,
            ...patch,
            taskType,
            lastUpdated: new Date().toISOString()
        };
        persistRpaStates(states);
    }

    function clearRpaState(taskType) {
        if (!taskType) {
            return;
        }
        const states = loadRpaStates();
        if (!states[taskType]) {
            return;
        }
        delete states[taskType];
        if (Object.keys(states).length) {
            persistRpaStates(states);
        } else {
            window.sessionStorage.removeItem(RPA_STATE_STORAGE_KEY);
        }
    }

    function getStoredRpaState(taskType) {
        const states = loadRpaStates();
        return states[taskType] && typeof states[taskType] === 'object' ? states[taskType] : null;
    }

    function canRenderRpaResult(taskType) {
        const role = getCurrentRole();
        if (!role) {
            return false;
        }
        if (taskType === 'PURCHASE_PRICE' || taskType === 'INVENTORY_SHORTAGE') {
            return role === 'ADMIN' || role === 'INVENTORY';
        }
        return false;
    }

    function toPersistedDetail(taskType, detail) {
        return {
            taskId: detail?.taskId || rpaState.taskId || null,
            actionName: detail?.actionName || rpaState.actionName || null,
            status: detail?.status || rpaState.status || 'IDLE',
            message: rpaState.message || null,
            tone: rpaState.tone || 'neutral',
            errorMessage: detail?.errorMessage || null
        };
    }

    async function requestRpaApi(path, options) {
        const response = await fetch(`${getApiBaseUrl()}${path}`, {
            ...options,
            headers: getHeaders(options && options.headers)
        });

        const text = await response.text();
        let payload = null;

        try {
            payload = text ? JSON.parse(text) : null;
        } catch (error) {
            const parseError = new Error('응답 형식을 해석하지 못했습니다.');
            parseError.statusCode = response.status;
            throw parseError;
        }

        if (!response.ok || !payload || payload.status !== 'success') {
            const requestError = new Error(payload?.message || 'RPA 요청 처리 중 오류가 발생했습니다.');
            requestError.statusCode = response.status;
            throw requestError;
        }

        return payload.data;
    }

    function getRpaContext() {
        const path = window.location.pathname.replace(/\\/g, '/');
        if (path.includes('/purchase-dashboard.html')) {
            return {
                taskType: 'PURCHASE_PRICE',
                title: '구매 대시보드 가격 수집',
                description: '공급처 기준 외부 구매가를 다시 모아 ERP 가격과 비교합니다.',
                actionName: 'COLLECT_EXTERNAL_PRICE'
            };
        }
        if (path.includes('/dashboard.html')) {
            return {
                taskType: 'INVENTORY_SHORTAGE',
                title: '장기 체화 및 임박 재고 분석',
                description: '장기 미출고 제품과 유통기한 임박 원자재를 분석하고 알림을 보냅니다.',
                actionName: 'CHECK_INVENTORY_SHORTAGE'
            };
        }
        return {
            taskType: 'PURCHASE_PRICE',
            title: 'RPA 상태 점검',
            description: '공용 RPA 위젯에서 최근 작업 상태를 확인합니다.',
            actionName: 'COLLECT_EXTERNAL_PRICE'
        };
    }

    function restoreRpaStateForCurrentContext() {
        const ctx = getRpaContext();
        const saved = getStoredRpaState(ctx.taskType);
        if (!saved) {
            return null;
        }
        rpaState.taskId = saved.taskId || null;
        rpaState.actionName = saved.actionName || ctx.actionName;
        rpaState.status = saved.status || 'IDLE';
        rpaState.lastUpdated = saved.lastUpdated || '-';
        rpaState.message = saved.message || null;
        rpaState.tone = saved.tone || 'neutral';
        return saved;
    }

    function renderDynamicRpaWidget() {
        const container = document.getElementById('dduk-floating-rpa-container');
        if (!container) return;

        const path = window.location.pathname.replace(/\\/g, '/');
        if (path.includes('/accounting') || path.includes('/monthly_closing') || path.includes('/voucher_management') || path.includes('/trial_balance') || path.includes('/settlement') || path.includes('/accounts') || path.includes('/wip')) {
            container.innerHTML = `
                <div class="flex flex-col items-center justify-center py-16 text-center" style="font-family: inherit;">
                    <div style="background-color: #f8fafc; border: 1px dashed #cbd5e1; border-radius: 1rem; padding: 2rem; width: 100%;">
                        <i data-lucide="cpu" style="width: 2.5rem; height: 2.5rem; margin: 0 auto 0.75rem auto; color: #94a3b8;"></i>
                        <h4 style="margin: 0 0 0.5rem 0; font-size: 0.875rem; font-weight: 700; color: #1e293b;">RPA 자동 제어</h4>
                        <span style="display: inline-block; background-color: #e2e8f0; color: #475569; border-radius: 9999px; padding: 0.25rem 0.75rem; font-size: 0.75rem; font-weight: 700;">추후 업데이트 예정</span>
                    </div>
                </div>
            `;
            if (window.lucide) window.lucide.createIcons();
            return;
        }

        const ctx = getRpaContext();
        container.innerHTML = `
            <div class="space-y-4" style="font-family: inherit;">
                <div class="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm">
                    <div class="flex items-center justify-between gap-3" style="display: flex; align-items: center; justify-content: space-between; gap: 0.75rem;">
                        <div class="min-w-0" style="flex: 1; min-width: 0;">
                            <div class="flex items-center gap-2" style="display: flex; align-items: center; gap: 0.5rem;">
                                <h3 class="text-sm font-bold text-gray-900" style="margin:0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" title="${ctx.title}">${ctx.title}</h3>
                                <span class="rounded bg-indigo-50 px-1.5 py-0.5 text-[9px] font-bold text-indigo-600" style="white-space: nowrap; display: inline-block;">${translateRpaType(ctx.taskType)}</span>
                            </div>
                            <p class="mt-1 text-[11px] text-gray-400" style="margin: 0.25rem 0 0 0; line-height: 1.3; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;" title="${ctx.description}">${ctx.description}</p>
                        </div>
                        <div style="flex-shrink: 0;">
                            <span id="rpaWidgetStatusBadge" class="rounded-full bg-slate-100 px-2.5 py-0.5 text-[10px] font-bold text-slate-600" style="white-space: nowrap;">대기</span>
                        </div>
                    </div>

                    <div id="rpaWidgetMessage" class="mt-4 rounded-xl border border-slate-100 bg-slate-50/50 px-4 py-3 text-xs text-slate-500 leading-normal">
                        준비됐습니다. 버튼을 누르면 현재 페이지 기준 분석을 실행합니다.
                    </div>

                    <div class="mt-5 flex items-center justify-between gap-3">
                        <span id="rpaWidgetLastUpdated" class="text-[10px] text-slate-400 font-medium">최근 상태 확인: -</span>
                        <div class="flex gap-2" style="display: flex; gap: 0.5rem;">
                            <button id="rpaWidgetRefreshButton" type="button" class="rounded-xl border border-slate-200 hover:bg-slate-50 px-3 py-2 text-xs font-bold text-slate-600 transition-all">새로고침</button>
                            <button id="rpaWidgetTriggerButton" type="button" class="rounded-xl bg-indigo-600 hover:bg-indigo-700 px-4 py-2 text-xs font-bold text-white shadow-sm shadow-indigo-100 transition-all">분석 실행</button>
                        </div>
                    </div>
                </div>

                <div id="rpaWidgetResultContainer"></div>
            </div>
        `;

        const triggerButton = document.getElementById('rpaWidgetTriggerButton');
        triggerButton?.addEventListener('click', triggerRpa);

        const refreshButton = document.getElementById('rpaWidgetRefreshButton');
        refreshButton?.addEventListener('click', refreshRpaState);

        const saved = restoreRpaStateForCurrentContext();
        if (saved && rpaState.status !== 'IDLE') {
            updateSummary(saved);
            if (saved.message) {
                setMessage(saved.message, saved.tone || 'neutral');
            }
            if (saved.taskId && activeStatuses.has(saved.status)) {
                pollTask(saved.taskId, 0);
            } else if (saved.status === 'SUCCESS' && canRenderRpaResult(ctx.taskType)) {
                fetchAndRenderRpaResult(ctx.taskType, true);
            }
            return;
        }

        checkAndRenderPreExistingResult(ctx.taskType);
    }

    function renderStatus(status) {
        const statusBadge = document.getElementById('rpaWidgetStatusBadge');
        const triggerButton = document.getElementById('rpaWidgetTriggerButton');
        if (!statusBadge || !triggerButton) return;

        const palette = {
            IDLE: 'bg-slate-100 text-slate-600',
            REQUESTED: 'bg-amber-100 text-amber-700',
            RUNNING: 'bg-sky-100 text-sky-700',
            SUCCESS: 'bg-emerald-100 text-emerald-700',
            FAILED: 'bg-rose-100 text-rose-700'
        };
        const labels = {
            IDLE: '대기',
            REQUESTED: '요청됨',
            RUNNING: '실행 중',
            SUCCESS: '성공',
            FAILED: '실패'
        };

        statusBadge.className = `rounded-full px-2.5 py-0.5 text-[10px] font-bold ${palette[status] || palette.IDLE}`;
        statusBadge.textContent = labels[status] || status;

        const isProcessing = activeStatuses.has(status);
        triggerButton.disabled = isProcessing;
        triggerButton.classList.toggle('opacity-60', isProcessing);
        triggerButton.classList.toggle('cursor-not-allowed', isProcessing);
        triggerButton.textContent = isProcessing ? '실행 중...' : 'RPA 실행';
    }

    function setMessage(text, tone) {
        const messageElement = document.getElementById('rpaWidgetMessage');
        if (!messageElement) return;

        const tones = {
            neutral: 'border-slate-100 bg-slate-50/50 text-slate-500',
            loading: 'border-sky-100 bg-sky-50/50 text-sky-700',
            success: 'border-emerald-100 bg-emerald-50/50 text-emerald-700',
            error: 'border-rose-100 bg-rose-50/50 text-rose-700'
        };
        messageElement.className = `mt-4 rounded-xl border px-4 py-3 text-xs leading-normal ${tones[tone] || tones.neutral}`;
        messageElement.textContent = text;
        rpaState.message = text || null;
        rpaState.tone = tone || 'neutral';
    }

    function updateSummary(detail) {
        const ctx = getRpaContext();
        const status = detail?.status || rpaState.status;
        rpaState.status = status;
        rpaState.taskId = detail?.taskId || rpaState.taskId || null;
        rpaState.actionName = detail?.actionName || rpaState.actionName || ctx.actionName;

        const actionNameEl = document.getElementById('rpaWidgetActionName');
        const taskIdEl = document.getElementById('rpaWidgetTaskId');
        const lastUpdatedEl = document.getElementById('rpaWidgetLastUpdated');

        if (actionNameEl) {
            actionNameEl.textContent = translateRpaAction(rpaState.actionName);
        }
        if (taskIdEl) {
            taskIdEl.textContent = rpaState.taskId || '-';
            taskIdEl.title = rpaState.taskId || '-';
        }
        if (lastUpdatedEl) {
            lastUpdatedEl.textContent = `최근 상태 확인: ${formatDateTime(new Date().toISOString())}`;
        }

        renderStatus(status);

        if (!detail) {
            return;
        }

        if (status === 'SUCCESS') {
            setMessage('RPA 작업이 완료되었습니다. 최신 결과를 다시 확인해보겠습니다.', 'success');
            saveRpaState(ctx.taskType, toPersistedDetail(ctx.taskType, detail));
            if (canRenderRpaResult(ctx.taskType)) {
                fetchAndRenderRpaResult(ctx.taskType);
            } else {
                setMessage('RPA 작업은 끝났지만 현재 권한으로는 결과 상세를 다시 불러올 수 없습니다.', 'success');
            }
            return;
        }

        if (status === 'FAILED') {
            setMessage(detail.errorMessage || 'RPA 실행 중 오류가 발생해서 작업이 실패했습니다.', 'error');
            saveRpaState(ctx.taskType, toPersistedDetail(ctx.taskType, detail));
            return;
        }

        if (status === 'RUNNING') {
            setMessage('RPA 작업이 아직 진행 중입니다. 실행이 오래 걸린다면 아래 [새로고침] 버튼을 눌러보세요.', 'loading');
            saveRpaState(ctx.taskType, toPersistedDetail(ctx.taskType, detail));
            return;
        }

        if (status === 'REQUESTED') {
            setMessage('RPA 요청이 접수되었습니다. 실행이 오래 걸린다면 아래 [새로고침] 버튼을 눌러보세요.', 'loading');
            saveRpaState(ctx.taskType, toPersistedDetail(ctx.taskType, detail));
            return;
        }

        saveRpaState(ctx.taskType, toPersistedDetail(ctx.taskType, detail));
    }

    function stopPolling() {
        if (rpaState.pollingHandle) {
            window.clearTimeout(rpaState.pollingHandle);
            rpaState.pollingHandle = null;
        }
    }

    async function loadTaskDetail(taskId) {
        const detail = await requestRpaApi(`/api/v1/admin/tasks/${encodeURIComponent(taskId)}`, {
            method: 'GET'
        });
        updateSummary(detail);
        return detail;
    }

    async function pollTask(taskId, attempt) {
        const ctx = getRpaContext();
        try {
            const detail = await loadTaskDetail(taskId);
            if (detail && !terminalStatuses.has(detail.status) && attempt < 30) {
                rpaState.pollingHandle = window.setTimeout(() => {
                    pollTask(taskId, attempt + 1);
                }, 2000);
                return;
            }

            stopPolling();
            if (detail && !terminalStatuses.has(detail.status)) {
                setMessage('아직 최종 결과가 반영되는 중입니다. 실행이 오래 걸린다면 아래 [새로고침] 버튼을 눌러보세요.', 'loading');
                saveRpaState(ctx.taskType, toPersistedDetail(ctx.taskType, detail));
            }
        } catch (error) {
            stopPolling();
            if (error?.statusCode === 404 || error?.statusCode === 403) {
                clearRpaState(ctx.taskType);
                rpaState.taskId = null;
                rpaState.actionName = ctx.actionName;
                rpaState.status = 'IDLE';
                renderStatus('IDLE');
                setMessage(
                    error.statusCode === 403
                        ? '현재 권한으로는 이 작업 상태를 다시 조회할 수 없습니다.'
                        : '이전 작업 정보를 찾지 못해서 저장된 상태를 정리했습니다.',
                    error.statusCode === 403 ? 'error' : 'neutral'
                );
                return;
            }

            renderStatus('FAILED');
            setMessage(error.message || '작업 상태를 확인하지 못했습니다.', 'error');
            saveRpaState(ctx.taskType, toPersistedDetail(ctx.taskType, {
                taskId,
                actionName: rpaState.actionName,
                status: rpaState.status || 'FAILED'
            }));
        }
    }

    async function triggerRpa() {
        const ctx = getRpaContext();
        stopPolling();
        renderStatus('REQUESTED');
        setMessage('RPA 실행 요청을 보내는 중입니다...', 'loading');

        try {
            const data = await requestRpaApi('/api/v1/admin/rpa/trigger', {
                method: 'POST',
                body: JSON.stringify({ taskType: ctx.taskType })
            });

            rpaState.taskId = data.taskId || null;
            rpaState.actionName = data.actionName || ctx.actionName;
            rpaState.status = 'REQUESTED';

            updateSummary({
                taskId: rpaState.taskId,
                actionName: rpaState.actionName,
                status: 'REQUESTED'
            });

            if (rpaState.taskId) {
                pollTask(rpaState.taskId, 0);
            }
        } catch (error) {
            renderStatus('FAILED');
            setMessage(error.message || 'RPA 실행 요청에 실패했습니다.', 'error');
            saveRpaState(ctx.taskType, toPersistedDetail(ctx.taskType, {
                taskId: rpaState.taskId,
                actionName: rpaState.actionName || ctx.actionName,
                status: 'FAILED'
            }));
        }
    }

    async function refreshRpaState() {
        const ctx = getRpaContext();
        setMessage('상태를 새로고침하는 중입니다...', 'loading');

        try {
            const saved = getStoredRpaState(ctx.taskType);
            if (saved && saved.taskId) {
                const detail = await loadTaskDetail(saved.taskId);
                if (detail) {
                    if (detail.status === 'SUCCESS' && canRenderRpaResult(ctx.taskType)) {
                        await fetchAndRenderRpaResult(ctx.taskType);
                    }
                    return;
                }
            }

            await fetchAndRenderRpaResult(ctx.taskType);
            setMessage('최신 상태로 갱신되었습니다.', 'success');

            const lastUpdatedEl = document.getElementById('rpaWidgetLastUpdated');
            if (lastUpdatedEl) {
                lastUpdatedEl.textContent = `최근 상태 확인: ${formatDateTime(new Date().toISOString())}`;
            }
        } catch (error) {
            setMessage(error.message || '상태를 새로고침하지 못했습니다.', 'error');
            console.error('RPA 상태 새로고침 실패:', error);
        }
    }

    async function checkAndRenderPreExistingResult(taskType) {
        fetchAndRenderRpaResult(taskType, true);
    }

    async function fetchAndRenderRpaResult(taskType, silent = false) {
        const resultContainer = document.getElementById('rpaWidgetResultContainer');
        if (!resultContainer) return;

        if (!canRenderRpaResult(taskType)) {
            if (!silent) {
                resultContainer.innerHTML = '';
            }
            return;
        }

        try {
            if (taskType === 'INVENTORY_SHORTAGE') {
                const stats = await requestRpaApi('/api/v1/inventory/dashboard/stats', { method: 'GET' });
                const block = stats.inventoryShortageRpa;
                if (!block || !Array.isArray(block.items) || !block.items.length) {
                    if (!silent) {
                        resultContainer.innerHTML = '';
                    }
                    return;
                }

                const itemsHtml = block.items.slice(0, 3).map((item) => {
                    const daysLabel = item.daysWithoutOutbound ? `최근 출고 ${item.daysWithoutOutbound}일 없음` : '최근 출고 기록 없음';
                    return `
                    <div class="rounded-xl border border-slate-100 bg-white p-3 text-xs shadow-sm" style="margin-bottom: 0.5rem;">
                        <div class="flex items-center justify-between font-bold text-gray-900 mb-1" style="display: flex; justify-content: space-between;">
                            <span>${escapeHtml(item.itemName)}</span>
                            <span class="text-amber-700">${escapeHtml(item.statusLabel || '장기 체화')}</span>
                        </div>
                        <div class="flex justify-between text-[11px] text-gray-500" style="display: flex; justify-content: space-between;">
                            <span>위치: ${escapeHtml(item.warehouseName || '-')}</span>
                            <span>보유재고: ${item.availableStock ?? 0} | ${daysLabel}</span>
                        </div>
                        <div class="mt-2 rounded-lg bg-amber-50/50 p-2 text-[10px] font-medium text-amber-800 leading-normal" style="margin-top: 0.5rem; padding: 0.5rem; border-radius: 0.5rem;">
                            추천 행동: ${escapeHtml(item.recommendedAction || '-')}
                        </div>
                    </div>
                `;
                }).join('');

                resultContainer.innerHTML = `
                    <div class="rounded-2xl border border-slate-100 bg-slate-50/40 p-4 space-y-3" style="margin-top: 1rem; border: 1px solid #f1f5f9; padding: 1rem; border-radius: 1rem; background-color: #f8fafc;">
                        <div class="flex items-center justify-between" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.75rem;">
                            <h4 class="text-xs font-bold text-slate-800" style="margin: 0;">재고 리스크 분석 결과</h4>
                            <span class="text-[10px] font-medium text-slate-400">${block.latestCollectedAt ? `분석일: ${formatDateTime(block.latestCollectedAt)} | ` : ''}총 ${block.totalRiskCount || 0}건</span>
                        </div>
                        <div class="grid gap-2">
                            ${itemsHtml}
                        </div>
                        ${block.totalRiskCount > 3 ? `
                            <div class="text-[10px] text-center text-slate-400 font-semibold pt-1" style="text-align: center; margin-top: 0.5rem;">
                                그 외 ${block.totalRiskCount - 3}건의 리스크 항목이 더 있습니다.
                            </div>
                        ` : ''}
                    </div>
                `;
                return;
            }

            if (taskType === 'PURCHASE_PRICE') {
                const stats = await requestRpaApi('/api/v1/inventory/purchase-dashboard/stats', { method: 'GET' });
                const block = stats.rpaComparison || stats.comparison;
                if (!block) {
                    if (!silent) {
                        resultContainer.innerHTML = '';
                    }
                    return;
                }

                const items = Array.isArray(block.items) ? block.items : [];
                const statusLabelMap = {
                    EMPTY: '대기',
                    MISSING_FILE: '파일 누락',
                    EMPTY_RESULT: '수집 결과 비어 있음',
                    NO_ERP_BASELINE: 'ERP 기준 없음',
                    ERP_BASELINE_ONLY: '매칭 대기',
                    READY: '비교 완료'
                };
                const delta = block.priceDelta == null ? null : Number(block.priceDelta || 0);
                const deltaColor = delta == null ? 'text-slate-500' : delta > 0 ? 'text-rose-600' : delta < 0 ? 'text-emerald-600' : 'text-slate-600';
                const deltaText = delta == null ? '계산 대기' : `${delta > 0 ? '+' : ''}${delta.toLocaleString()}원`;
                const itemsHtml = items.map((item) => {
                    const itemDelta = Number(item.priceDelta || 0);
                    const itemDeltaColor = item.priceDelta == null ? 'text-slate-400' : itemDelta > 0 ? 'text-rose-600 font-semibold' : itemDelta < 0 ? 'text-emerald-600 font-semibold' : 'text-slate-400';
                    const itemDeltaSign = itemDelta > 0 ? '+' : '';
                    const itemDeltaText = item.priceDelta == null ? '-' : itemDelta === 0 ? '동일' : `${itemDeltaSign}${itemDelta.toLocaleString()}원`;

                    return `
                        <tr class="border-b border-slate-100">
                            <td class="py-2 text-[11px] font-medium text-gray-800" style="text-align: left; padding: 6px 0; max-width: 90px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">${escapeHtml(item.productName || item.itemName)}</td>
                            <td class="py-2 text-right text-[11px] text-gray-500" style="text-align: right; padding: 6px 0;">${Number(item.collectedPrice || 0).toLocaleString()}원</td>
                            <td class="py-2 text-right text-[11px] text-gray-400" style="text-align: right; padding: 6px 0;">${Number(item.erpPrice || 0).toLocaleString()}원</td>
                            <td class="py-2 text-right text-[11px] ${itemDeltaColor}" style="text-align: right; padding: 6px 0;">${itemDeltaText}</td>
                        </tr>
                    `;
                }).join('');
                const bodyHtml = items.length ? itemsHtml : `
                    <tr>
                        <td colspan="4" class="py-3">
                            <div class="rounded-xl border border-dashed border-slate-200 bg-slate-50 px-3 py-3 text-[11px] leading-relaxed text-slate-500" style="border: 1px dashed #cbd5e1; border-radius: 0.75rem; padding: 0.75rem; background-color: #f8fafc;">
                                ${escapeHtml(block.message || '수집 결과는 있지만 아직 ERP 품목과 매칭된 비교 항목이 없습니다.')}
                            </div>
                        </td>
                    </tr>
                `;

                resultContainer.innerHTML = `
                    <div class="rounded-2xl border border-slate-100 bg-slate-50/40 p-4 space-y-3" style="margin-top: 1rem; border: 1px solid #f1f5f9; padding: 1rem; border-radius: 1rem; background-color: #f8fafc;">
                        <div class="flex items-center justify-between" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.75rem;">
                            <h4 class="text-xs font-bold text-slate-800" style="margin: 0;">구매가 수집 비교 요약</h4>
                            <span class="text-[10px] font-semibold text-slate-400">${block.latestCollectedAt ? `수집일: ${formatDateTime(block.latestCollectedAt)} | ` : ''}${escapeHtml(statusLabelMap[block.status] || '결과 확인')}</span>
                        </div>
                        <p class="text-[11px] leading-relaxed text-slate-500" style="margin: 0 0 0.75rem 0;">${escapeHtml(block.message || '최신 구매가 수집 결과를 확인했습니다.')}</p>
                        <div class="grid grid-cols-2 gap-2" style="display: grid; grid-template-columns: 1fr 1fr; gap: 0.5rem; margin-bottom: 0.75rem;">
                            <div class="rounded-xl border border-slate-100 bg-white p-2.5 text-center shadow-xs" style="border: 1px solid #f1f5f9; border-radius: 0.75rem; text-align: center; padding: 0.625rem; background-color: #ffffff;">
                                <p class="text-[9px] font-bold text-slate-400 uppercase" style="margin: 0;">수집 평균가</p>
                                <p class="mt-0.5 text-xs font-extrabold text-slate-800" style="margin: 0.25rem 0 0 0;">${block.latestCollectedAveragePrice == null ? '-' : `${Number(block.latestCollectedAveragePrice).toLocaleString()}원`}</p>
                            </div>
                            <div class="rounded-xl border border-slate-100 bg-white p-2.5 text-center shadow-xs" style="border: 1px solid #f1f5f9; border-radius: 0.75rem; text-align: center; padding: 0.625rem; background-color: #ffffff;">
                                <p class="text-[9px] font-bold text-slate-400 uppercase" style="margin: 0;">가격 차이</p>
                                <p class="mt-0.5 text-xs font-extrabold ${deltaColor}" style="margin: 0.25rem 0 0 0;">${deltaText}</p>
                            </div>
                        </div>
                        <div class="grid grid-cols-2 gap-2" style="display: grid; grid-template-columns: 1fr 1fr; gap: 0.5rem; margin-bottom: 0.75rem;">
                            <div class="rounded-xl border border-slate-100 bg-white p-2.5 text-center shadow-xs" style="border: 1px solid #f1f5f9; border-radius: 0.75rem; text-align: center; padding: 0.625rem; background-color: #ffffff;">
                                <p class="text-[9px] font-bold text-slate-400 uppercase" style="margin: 0;">수집 품목</p>
                                <p class="mt-0.5 text-xs font-extrabold text-slate-800" style="margin: 0.25rem 0 0 0;">${Number(block.collectedItemsCount || 0).toLocaleString()}건</p>
                            </div>
                            <div class="rounded-xl border border-slate-100 bg-white p-2.5 text-center shadow-xs" style="border: 1px solid #f1f5f9; border-radius: 0.75rem; text-align: center; padding: 0.625rem; background-color: #ffffff;">
                                <p class="text-[9px] font-bold text-slate-400 uppercase" style="margin: 0;">비교 매칭</p>
                                <p class="mt-0.5 text-xs font-extrabold text-slate-800" style="margin: 0.25rem 0 0 0;">${Number(block.comparedItemsCount || 0).toLocaleString()}건</p>
                            </div>
                        </div>

                        <div class="rounded-xl border border-slate-100 bg-white p-3 shadow-xs" style="border: 1px solid #f1f5f9; border-radius: 0.75rem; padding: 0.75rem; background-color: #ffffff; max-height: 220px; overflow-y: auto;">
                            <table class="w-full" style="width: 100%; border-collapse: collapse;">
                                <thead>
                                    <tr class="border-b text-[10px] font-bold text-slate-400" style="border-bottom: 1px solid #e2e8f0;">
                                        <th class="pb-1.5 text-left" style="text-align: left; padding-bottom: 0.375rem; width: 35%;">품목명</th>
                                        <th class="pb-1.5 text-right" style="text-align: right; padding-bottom: 0.375rem; width: 22%;">수집가</th>
                                        <th class="pb-1.5 text-right" style="text-align: right; padding-bottom: 0.375rem; width: 22%;">ERP가</th>
                                        <th class="pb-1.5 text-right" style="text-align: right; padding-bottom: 0.375rem; width: 21%;">차이</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    ${bodyHtml}
                                </tbody>
                            </table>
                        </div>
                    </div>
                `;
                return;
            }

            resultContainer.innerHTML = `
                <div class="rounded-2xl border border-emerald-100 bg-emerald-50/30 p-4 text-center" style="margin-top: 1rem; border: 1px solid #d1fae5; border-radius: 1rem; background-color: rgba(209, 250, 229, 0.3); text-align: center; padding: 1rem;">
                    <div class="inline-flex rounded-full bg-emerald-100 p-2 text-emerald-600 mb-2" style="margin-bottom: 0.5rem; display: inline-flex; border-radius: 9999px; background-color: #d1fae5; padding: 0.5rem;">
                        <svg style="width:1.25rem; height:1.25rem;" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="3">
                            <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
                        </svg>
                    </div>
                    <h4 class="text-xs font-bold text-emerald-950" style="margin: 0;">RPA 연결 성공</h4>
                    <p class="text-[11px] font-semibold text-emerald-800 mt-1" style="margin: 0.25rem 0 0 0;">현재 작업은 정상적으로 확인되었습니다.</p>
                </div>
            `;
        } catch (error) {
            if (!silent && (error?.statusCode === 403 || error?.statusCode === 404)) {
                setMessage(
                    error.statusCode === 403
                        ? '상태는 복원했지만 현재 권한으로는 결과 상세를 다시 조회할 수 없습니다.'
                        : '저장된 결과를 다시 불러오지 못했습니다.',
                    error.statusCode === 403 ? 'success' : 'error'
                );
                return;
            }
            if (!silent) {
                setMessage(error.message || '결과 요약을 다시 불러오지 못했습니다.', 'error');
            }
            console.error('RPA 결과 요약 렌더 실패:', error);
        }
    }

    // Expose to window for delegation & orchestrator
    window.DDUK_RPA_WIDGET = {
        requestRpaApi,
        renderDynamicRpaWidget,
        triggerRpa,
        pollTask,
        fetchAndRenderRpaResult
    };
})();
