(function () {
    const mount = document.querySelector("[data-rpa-widget]");
    if (!mount) {
        return;
    }

    const taskType = String(mount.dataset.taskType || "PURCHASE_PRICE").trim().toUpperCase();
    const historyTaskType = String(mount.dataset.historyTaskType || "RPA").trim().toUpperCase();
    const title = mount.dataset.title || "RPA 실행";
    const description = mount.dataset.description || "관리자 화면에서 공용 RPA 흐름을 테스트한다.";
    const actionNameHint = mount.dataset.actionName || "";

    const terminalStatuses = new Set(["SUCCESS", "FAILED"]);
    const activeStatuses = new Set(["REQUESTED", "RUNNING"]);
    const state = {
        taskId: null,
        actionName: null,
        pollingHandle: null
    };

    mount.innerHTML = `
        <section class="bg-white rounded-2xl border border-gray-100 p-6 shadow-sm">
            <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                <div>
                    <p class="text-xs font-semibold uppercase tracking-[0.2em] text-sky-500">Stage 6 RPA UX</p>
                    <h3 class="mt-2 text-xl font-bold text-gray-900">${escapeHtml(title)}</h3>
                    <p class="mt-2 text-sm text-gray-500">${escapeHtml(description)}</p>
                </div>
                <div class="flex flex-wrap items-center gap-3">
                    <span id="rpaWidgetStatusBadge" class="rounded-full bg-slate-100 px-3 py-1 text-xs font-bold text-slate-600">대기</span>
                    <button id="rpaWidgetTriggerButton" type="button" class="rounded-xl bg-slate-900 px-4 py-2.5 text-sm font-bold text-white transition hover:bg-slate-800">실행</button>
                </div>
            </div>
            <div class="mt-5 grid gap-3 md:grid-cols-3">
                <div class="rounded-xl border border-gray-100 bg-gray-50 px-4 py-3">
                    <p class="text-[11px] font-bold uppercase tracking-wider text-gray-400">Task Type</p>
                    <p class="mt-2 text-sm font-semibold text-gray-800">${escapeHtml(taskType)}</p>
                </div>
                <div class="rounded-xl border border-gray-100 bg-gray-50 px-4 py-3">
                    <p class="text-[11px] font-bold uppercase tracking-wider text-gray-400">Action</p>
                    <p id="rpaWidgetActionName" class="mt-2 text-sm font-semibold text-gray-800">-</p>
                </div>
                <div class="rounded-xl border border-gray-100 bg-gray-50 px-4 py-3">
                    <p class="text-[11px] font-bold uppercase tracking-wider text-gray-400">Task ID</p>
                    <p id="rpaWidgetTaskId" class="mt-2 text-sm font-semibold text-gray-800">-</p>
                </div>
            </div>
            <div id="rpaWidgetMessage" class="mt-4 rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
                준비됨. 버튼을 누르면 관리자 RPA trigger API를 호출한다.
            </div>
            <div class="mt-4 flex flex-wrap items-center gap-3 text-xs font-semibold text-slate-500">
                <span id="rpaWidgetLastUpdated">최근 상태 확인: -</span>
                <button id="rpaWidgetRefreshButton" type="button" class="rounded-lg border border-slate-200 px-3 py-1.5 text-slate-600 hover:bg-slate-50">이력 새로고침</button>
            </div>
            <div class="mt-5 rounded-2xl border border-slate-100 bg-slate-50/60 p-4">
                <div class="flex items-center justify-between gap-3">
                    <h4 class="text-sm font-bold text-slate-800">최근 실행 이력</h4>
                    <span class="text-xs font-semibold text-slate-400">같은 taskType/action 기준 최근 5건</span>
                </div>
                <div id="rpaWidgetHistory" class="mt-3 space-y-2">
                    <div class="rounded-xl border border-slate-100 bg-white px-3 py-3 text-sm text-slate-400">최근 실행 이력을 불러오는 중이야.</div>
                </div>
            </div>
        </section>
    `;

    const triggerButton = document.getElementById("rpaWidgetTriggerButton");
    const refreshButton = document.getElementById("rpaWidgetRefreshButton");
    const statusBadge = document.getElementById("rpaWidgetStatusBadge");
    const actionNameElement = document.getElementById("rpaWidgetActionName");
    const taskIdElement = document.getElementById("rpaWidgetTaskId");
    const messageElement = document.getElementById("rpaWidgetMessage");
    const historyElement = document.getElementById("rpaWidgetHistory");
    const lastUpdatedElement = document.getElementById("rpaWidgetLastUpdated");

    function escapeHtml(value) {
        return String(value ?? "").replace(/[&<>"']/g, function (char) {
            return { "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#039;" }[char];
        });
    }

    function getApiBaseUrl() {
        return window.ddukSession && typeof window.ddukSession.getApiBaseUrl === "function"
            ? window.ddukSession.getApiBaseUrl()
            : "";
    }

    function getHeaders(extraHeaders) {
        if (window.ddukSession && typeof window.ddukSession.getAuthHeaders === "function") {
            return window.ddukSession.getAuthHeaders({
                "Content-Type": "application/json",
                ...(extraHeaders || {})
            });
        }
        return {
            "Content-Type": "application/json",
            ...(extraHeaders || {})
        };
    }

    async function requestApi(path, options) {
        const response = await fetch(`${getApiBaseUrl()}${path}`, {
            ...options,
            headers: getHeaders(options && options.headers)
        });

        const text = await response.text();
        const payload = text ? JSON.parse(text) : null;

        if (!response.ok || !payload || payload.status !== "success") {
            throw new Error(payload && payload.message ? payload.message : "RPA 요청 처리 중 오류가 발생했어.");
        }

        return payload.data;
    }

    function formatDateTime(value) {
        if (!value) {
            return "-";
        }
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return value;
        }
        return date.toLocaleString("ko-KR");
    }

    function renderStatus(status) {
        const palette = {
            IDLE: "bg-slate-100 text-slate-600",
            REQUESTED: "bg-amber-100 text-amber-700",
            RUNNING: "bg-sky-100 text-sky-700",
            SUCCESS: "bg-emerald-100 text-emerald-700",
            FAILED: "bg-rose-100 text-rose-700"
        };
        const labels = {
            IDLE: "대기",
            REQUESTED: "요청됨",
            RUNNING: "실행 중",
            SUCCESS: "성공",
            FAILED: "실패"
        };

        statusBadge.className = `rounded-full px-3 py-1 text-xs font-bold ${palette[status] || palette.IDLE}`;
        statusBadge.textContent = labels[status] || status;
        triggerButton.disabled = activeStatuses.has(status);
        triggerButton.classList.toggle("opacity-60", triggerButton.disabled);
        triggerButton.classList.toggle("cursor-not-allowed", triggerButton.disabled);
        triggerButton.textContent = activeStatuses.has(status) ? "실행 중" : "실행";
    }

    function setMessage(text, tone) {
        const tones = {
            neutral: "border-slate-200 bg-slate-50 text-slate-600",
            loading: "border-sky-200 bg-sky-50 text-sky-700",
            success: "border-emerald-200 bg-emerald-50 text-emerald-700",
            error: "border-rose-200 bg-rose-50 text-rose-700"
        };
        messageElement.className = `mt-4 rounded-xl border px-4 py-3 text-sm ${tones[tone] || tones.neutral}`;
        messageElement.textContent = text;
    }

    function stopPolling() {
        if (state.pollingHandle) {
            window.clearTimeout(state.pollingHandle);
            state.pollingHandle = null;
        }
    }

    async function loadRecentHistory() {
        try {
            const effectiveActionName = state.actionName || actionNameHint;
            const query = new URLSearchParams({ taskType: historyTaskType, size: "5", sort: "requestedAt,desc" });
            if (effectiveActionName) {
                query.set("actionName", effectiveActionName);
            }
            const page = await requestApi(`/api/v1/admin/tasks?${query.toString()}`, { method: "GET" });
            const items = Array.isArray(page.content) ? page.content : [];

            if (!items.length) {
                historyElement.innerHTML = '<div class="rounded-xl border border-slate-100 bg-white px-3 py-3 text-sm text-slate-400">아직 같은 범위의 실행 이력이 없어.</div>';
                return;
            }

            historyElement.innerHTML = items.map(function (item) {
                const statusTone = item.status === "SUCCESS"
                    ? "bg-emerald-50 text-emerald-700 border-emerald-100"
                    : item.status === "FAILED"
                        ? "bg-rose-50 text-rose-700 border-rose-100"
                        : "bg-sky-50 text-sky-700 border-sky-100";
                return `
                    <div class="rounded-xl border bg-white px-3 py-3 ${statusTone}">
                        <div class="flex items-start justify-between gap-3">
                            <div class="min-w-0">
                                <div class="font-mono text-xs font-bold">${escapeHtml(item.taskId)}</div>
                                <div class="mt-1 text-xs">${escapeHtml(item.status)} · ${escapeHtml(formatDateTime(item.requestedAt))}</div>
                                <div class="mt-1 text-xs text-slate-500">${escapeHtml(item.errorMessage || "실패 메시지 없음")}</div>
                            </div>
                            <button type="button" class="rounded-lg border border-current px-2.5 py-1 text-[11px] font-bold hover:bg-white/70" data-retry-task="${escapeHtml(item.taskId)}">재실행</button>
                        </div>
                    </div>
                `;
            }).join("");

            historyElement.querySelectorAll("[data-retry-task]").forEach(function (button) {
                button.addEventListener("click", triggerRpa);
            });
        } catch (error) {
            historyElement.innerHTML = `<div class="rounded-xl border border-rose-100 bg-white px-3 py-3 text-sm text-rose-600">${escapeHtml(error.message || "최근 실행 이력을 불러오지 못했어.")}</div>`;
        }
    }

    function updateSummary(detail) {
        const status = detail && detail.status ? detail.status : "IDLE";
        actionNameElement.textContent = detail && detail.actionName ? detail.actionName : state.actionName || actionNameHint || "-";
        taskIdElement.textContent = detail && detail.taskId ? detail.taskId : state.taskId || "-";
        lastUpdatedElement.textContent = `최근 상태 확인: ${formatDateTime(new Date().toISOString())}`;
        renderStatus(status);

        if (!detail) {
            return;
        }

        if (detail.status === "SUCCESS") {
            setMessage("최근 실행이 성공으로 끝났어. 아래 최근 실행 이력에서도 바로 다시 실행할 수 있어.", "success");
        } else if (detail.status === "FAILED") {
            setMessage(detail.errorMessage || "최근 실행이 실패했어. 실패 메시지를 확인하고 바로 재실행할 수 있어.", "error");
        } else if (detail.status === "RUNNING") {
            setMessage("RPA 작업이 실행 중이야. callback이 아직 안 왔으면 잠시 후 자동 갱신돼.", "loading");
        } else if (detail.status === "REQUESTED") {
            setMessage("RPA 요청은 접수됐고 실행 대기 또는 callback 대기 상태야.", "loading");
        }
    }

    async function loadTaskDetail(taskId) {
        const detail = await requestApi(`/api/v1/admin/tasks/${encodeURIComponent(taskId)}`, {
            method: "GET"
        });
        updateSummary(detail);
        await loadRecentHistory();
        return detail;
    }

    async function pollTask(taskId, attempt) {
        try {
            const detail = await loadTaskDetail(taskId);
            if (detail && !terminalStatuses.has(detail.status) && attempt < 30) {
                state.pollingHandle = window.setTimeout(function () {
                    pollTask(taskId, attempt + 1);
                }, 2000);
                return;
            }

            stopPolling();
            if (detail && !terminalStatuses.has(detail.status)) {
                setMessage("최종 callback이 아직 안 왔어. 작업 이력에 남아 있으니 잠시 후 다시 보면 돼.", "loading");
            }
        } catch (error) {
            stopPolling();
            renderStatus("FAILED");
            setMessage(error.message || "작업 상태를 확인하지 못했어.", "error");
        }
    }

    async function triggerRpa() {
        stopPolling();
        renderStatus("REQUESTED");
        setMessage("RPA trigger 요청을 보내는 중이야...", "loading");

        try {
            const data = await requestApi("/api/v1/admin/rpa/trigger", {
                method: "POST",
                body: JSON.stringify({ taskType: taskType })
            });

            state.taskId = data.taskId || null;
            state.actionName = data.actionName || null;
            actionNameElement.textContent = state.actionName || "-";
            taskIdElement.textContent = state.taskId || "-";
            renderStatus("REQUESTED");
            setMessage("RPA trigger가 접수됐어. 실행과 callback 상태를 자동으로 추적할게.", "loading");
            await loadRecentHistory();

            if (state.taskId) {
                pollTask(state.taskId, 0);
            }
        } catch (error) {
            renderStatus("FAILED");
            setMessage(error.message || "RPA trigger 호출에 실패했어.", "error");
        }
    }

    triggerButton.addEventListener("click", triggerRpa);
    refreshButton.addEventListener("click", loadRecentHistory);
    renderStatus("IDLE");
    loadRecentHistory();
})();
