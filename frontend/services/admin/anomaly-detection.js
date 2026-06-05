(function () {
    const state = {
        page: 0,
        size: 12,
        status: "",
        active: "",
        severity: "",
        keyword: "",
        totalPages: 1,
        totalElements: 0
    };

    const messageBox = document.getElementById("anomalyMessage");
    const tableBody = document.getElementById("anomalyTableBody");
    const pageInfo = document.getElementById("anomalyPageInfo");
    const prevPageBtn = document.getElementById("prevPageBtn");
    const nextPageBtn = document.getElementById("nextPageBtn");

    function ensureAdminSession() {
        if (!window.ddukSession || typeof window.ddukSession.requireRole !== "function") {
            return true;
        }
        return Boolean(window.ddukSession.requireRole(["ADMIN"]));
    }

    async function requestAdminApi(path, options) {
        const response = await fetch(`${window.ddukSession.getApiBaseUrl()}${path}`, {
            ...options,
            headers: window.ddukSession.getAuthHeaders({
                "Content-Type": "application/json",
                ...(options && options.headers ? options.headers : {})
            })
        });

        const text = await response.text();
        const payload = text ? JSON.parse(text) : null;
        if (!response.ok || !payload || payload.status !== "success") {
            throw new Error(payload && payload.message ? payload.message : "관리자 요청 처리에 실패했어.");
        }
        return payload.data;
    }

    function setMessage(message, type) {
        if (!message) {
            messageBox.className = "hidden rounded-2xl border px-4 py-3 text-sm font-semibold";
            messageBox.textContent = "";
            return;
        }

        const theme = {
            success: "border-emerald-200 bg-emerald-50 text-emerald-700",
            warning: "border-amber-200 bg-amber-50 text-amber-700",
            error: "border-rose-200 bg-rose-50 text-rose-700",
            info: "border-slate-200 bg-slate-50 text-slate-700"
        }[type || "info"];

        messageBox.className = `rounded-2xl border px-4 py-3 text-sm font-semibold ${theme}`;
        messageBox.textContent = message;
    }

    function formatDateTime(value) {
        if (!value) return "-";
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? value : date.toLocaleString("ko-KR");
    }

    function escapeHtml(value) {
        return String(value == null ? "" : value).replace(/[&<>"']/g, function (char) {
            return {
                "&": "&amp;",
                "<": "&lt;",
                ">": "&gt;",
                "\"": "&quot;",
                "'": "&#039;"
            }[char];
        });
    }

    function severityClass(value) {
        return {
            CRITICAL: "bg-rose-100 text-rose-700",
            HIGH: "bg-indigo-100 text-indigo-700",
            MEDIUM: "bg-amber-100 text-amber-700"
        }[value] || "bg-slate-100 text-slate-700";
    }

    function statusClass(value) {
        return {
            OPEN: "bg-amber-100 text-amber-700",
            CONFIRMED: "bg-emerald-100 text-emerald-700",
            FALSE_POSITIVE: "bg-slate-200 text-slate-700",
            IGNORED: "bg-slate-100 text-slate-500"
        }[value] || "bg-slate-100 text-slate-700";
    }

    function readFilters() {
        state.status = document.getElementById("filterStatus").value;
        state.active = document.getElementById("filterActive").value;
        state.severity = document.getElementById("filterSeverity").value;
        state.keyword = document.getElementById("filterKeyword").value.trim();
    }

    function resetFilters() {
        document.getElementById("filterStatus").value = "";
        document.getElementById("filterActive").value = "";
        document.getElementById("filterSeverity").value = "";
        document.getElementById("filterKeyword").value = "";
        state.page = 0;
        readFilters();
    }

    function buildListQuery() {
        const params = new URLSearchParams({
            page: String(state.page),
            size: String(state.size),
            sort: "lastDetectedAt,desc"
        });
        if (state.status) params.set("status", state.status);
        if (state.active) params.set("active", state.active);
        if (state.severity) params.set("severity", state.severity);
        if (state.keyword) params.set("keyword", state.keyword);
        return params.toString();
    }

    function renderSummary(summary) {
        document.getElementById("summaryActiveCount").textContent = String(summary.activeCount || 0);
        document.getElementById("summaryOpenCount").textContent = String(summary.openCount || 0);
        document.getElementById("summaryConfirmedCount").textContent = String(summary.confirmedCount || 0);
        document.getElementById("summaryCriticalCount").textContent = String(summary.criticalCount || 0);
        document.getElementById("summaryHighCount").textContent = String(summary.highCount || 0);
    }

    function renderTable(pageData) {
        const items = pageData && Array.isArray(pageData.content) ? pageData.content : [];
        state.totalPages = pageData && pageData.totalPages ? pageData.totalPages : 1;
        state.totalElements = pageData && pageData.totalElements ? pageData.totalElements : 0;

        if (!items.length) {
            tableBody.innerHTML = '<tr><td colspan="7" class="px-5 py-10 text-center text-slate-400">조건에 맞는 이상 탐지 항목이 아직 없어.</td></tr>';
        } else {
            tableBody.innerHTML = items.map(function (item) {
                const activeBadge = item.active
                    ? '<span class="rounded-full bg-rose-50 px-2 py-1 text-[11px] font-black text-rose-600">ACTIVE</span>'
                    : '<span class="rounded-full bg-slate-100 px-2 py-1 text-[11px] font-black text-slate-500">INACTIVE</span>';
                return `
                    <tr class="align-top">
                        <td class="px-5 py-4"><span class="rounded-full px-2.5 py-1 text-xs font-black ${severityClass(item.severity)}">${escapeHtml(item.severity)}</span></td>
                        <td class="px-5 py-4">
                            <div class="font-black text-slate-900">${escapeHtml(item.title)}</div>
                            <div class="mt-1 font-mono text-xs text-slate-500">${escapeHtml(item.ruleCode)}</div>
                        </td>
                        <td class="px-5 py-4">
                            <div class="font-bold text-slate-800">${escapeHtml(item.sourceLabel)}</div>
                            <div class="mt-1 text-xs text-slate-500">${escapeHtml(item.sourceType)} #${escapeHtml(item.sourceId || "-")}</div>
                        </td>
                        <td class="px-5 py-4">
                            <div class="text-sm font-medium text-slate-700">${escapeHtml(item.summary)}</div>
                            <div class="mt-2">${activeBadge}</div>
                        </td>
                        <td class="px-5 py-4">
                            <span class="rounded-full px-2.5 py-1 text-xs font-black ${statusClass(item.status)}">${escapeHtml(item.status)}</span>
                            <div class="mt-2 text-xs text-slate-500">${escapeHtml(item.reviewedBy || "-")}</div>
                        </td>
                        <td class="px-5 py-4 text-sm text-slate-600">
                            <div>${escapeHtml(formatDateTime(item.lastDetectedAt))}</div>
                            <div class="mt-1 text-xs text-slate-400">최초 ${escapeHtml(formatDateTime(item.firstDetectedAt))}</div>
                        </td>
                        <td class="px-5 py-4">
                            <div class="flex justify-end gap-2">
                                <button type="button" data-action="CONFIRMED" data-id="${item.id}" class="rounded-lg border border-emerald-200 px-2.5 py-1 text-xs font-black text-emerald-700 hover:bg-emerald-50">확인</button>
                                <button type="button" data-action="FALSE_POSITIVE" data-id="${item.id}" class="rounded-lg border border-slate-200 px-2.5 py-1 text-xs font-black text-slate-700 hover:bg-slate-50">오탐</button>
                                <button type="button" data-action="IGNORED" data-id="${item.id}" class="rounded-lg border border-amber-200 px-2.5 py-1 text-xs font-black text-amber-700 hover:bg-amber-50">무시</button>
                            </div>
                        </td>
                    </tr>
                `;
            }).join("");
        }

        pageInfo.textContent = `페이지 ${state.page + 1} / ${Math.max(state.totalPages, 1)} · 총 ${state.totalElements}건`;
        prevPageBtn.disabled = state.page <= 0;
        nextPageBtn.disabled = state.page >= Math.max(state.totalPages - 1, 0);

        tableBody.querySelectorAll("button[data-action]").forEach(function (button) {
            button.addEventListener("click", async function () {
                const status = button.getAttribute("data-action");
                const anomalyId = Number(button.getAttribute("data-id"));
                const note = window.prompt(`상태를 ${status}로 바꿀 메모가 있으면 적어줘.`, "") || "";
                await updateStatus(anomalyId, status, note);
            });
        });
    }

    async function loadSummary() {
        renderSummary(await requestAdminApi("/api/v1/admin/anomaly-logs/summary", { method: "GET" }));
    }

    async function loadLogs() {
        renderTable(await requestAdminApi(`/api/v1/admin/anomaly-logs?${buildListQuery()}`, { method: "GET" }));
    }

    async function refreshPage() {
        try {
            await Promise.all([loadSummary(), loadLogs()]);
        } catch (error) {
            setMessage(error.message, "error");
            tableBody.innerHTML = `<tr><td colspan="7" class="px-5 py-10 text-center text-rose-400">${escapeHtml(error.message)}</td></tr>`;
        }
    }

    async function runDetection() {
        setMessage("규칙 기반 이상 탐지를 다시 실행하는 중이야...", "info");
        try {
            const result = await requestAdminApi("/api/v1/admin/anomaly-logs/refresh", { method: "POST" });
            renderSummary(result.summary || {});
            await loadLogs();
            setMessage(`탐지를 다시 돌렸어. 현재 ${result.detectedCount || 0}건 감지됐고 ${result.deactivatedCount || 0}건은 비활성으로 정리했어.`, "success");
        } catch (error) {
            setMessage(error.message, "error");
        }
    }

    async function updateStatus(anomalyId, status, reviewNote) {
        try {
            const session = window.ddukSession.getSession();
            await requestAdminApi(`/api/v1/admin/anomaly-logs/${anomalyId}/status`, {
                method: "PATCH",
                body: JSON.stringify({
                    status: status,
                    reviewNote: reviewNote,
                    reviewedBy: session.userName || session.loginId || "ADMIN"
                })
            });
            setMessage(`이상 탐지 상태를 ${status}로 업데이트했어.`, "success");
            await refreshPage();
        } catch (error) {
            setMessage(error.message, "error");
        }
    }

    document.getElementById("anomalyFilterForm").addEventListener("submit", async function (event) {
        event.preventDefault();
        state.page = 0;
        readFilters();
        await loadLogs();
    });

    document.getElementById("resetFiltersBtn").addEventListener("click", async function () {
        resetFilters();
        await refreshPage();
    });

    document.getElementById("refreshSummaryBtn").addEventListener("click", refreshPage);
    document.getElementById("runDetectionBtn").addEventListener("click", runDetection);

    prevPageBtn.addEventListener("click", async function () {
        if (state.page <= 0) return;
        state.page -= 1;
        await loadLogs();
    });

    nextPageBtn.addEventListener("click", async function () {
        if (state.page >= state.totalPages - 1) return;
        state.page += 1;
        await loadLogs();
    });

    const toggleBtn = document.getElementById("btnToggleAnomalyAccordion");
    const accordionContent = document.getElementById("anomalyAccordionContent");

    if (toggleBtn && accordionContent) {
        // system-admin.html에서의 아코디언 모드
        toggleBtn.addEventListener("click", async function (e) {
            e.preventDefault();
            const isHidden = accordionContent.classList.contains("hidden");
            if (isHidden) {
                accordionContent.classList.remove("hidden");
                toggleBtn.textContent = "경고 목록 닫기";
                toggleBtn.classList.remove("bg-slate-900", "hover:bg-slate-700");
                toggleBtn.classList.add("bg-rose-600", "hover:bg-rose-500");
                
                // 열리는 시점에 데이터 로드
                if (ensureAdminSession()) {
                    readFilters();
                    await refreshPage();
                }
            } else {
                accordionContent.classList.add("hidden");
                toggleBtn.textContent = "경고 목록 열기";
                toggleBtn.classList.remove("bg-rose-600", "hover:bg-rose-500");
                toggleBtn.classList.add("bg-slate-900", "hover:bg-slate-700");
            }
        });
    } else {
        // 기존 단독 anomaly-detection.html 페이지 모드
        if (ensureAdminSession()) {
            readFilters();
            refreshPage();
        }
    }
})();
