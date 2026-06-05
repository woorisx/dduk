(function () {
    const aiRequestCount = document.getElementById("dashboardAiRequestCount");
    const rpaSuccessRate = document.getElementById("dashboardRpaSuccessRate");
    const activeChatSessions = document.getElementById("dashboardActiveChatSessions");
    const rpaQueueCount = document.getElementById("dashboardRpaQueueCount");

    const totalMemberCount = document.getElementById("dashboardTotalMemberCount");
    const activeMemberCount = document.getElementById("dashboardActiveMemberCount");
    const recentLoginCount = document.getElementById("dashboardRecentLoginCount");
    const recentLoginHint = document.getElementById("dashboardRecentLoginHint");

    const aiEngineStatus = document.getElementById("dashboardAiEngineStatus");
    const rpaNodeStatus = document.getElementById("dashboardRpaNodeStatus");
    const recentErrorCount = document.getElementById("dashboardRecentErrorCount");

    const auditFilterForm = document.getElementById("auditFilterForm");
    const auditTargetFilter = document.getElementById("auditTargetFilter");
    const auditActorFilter = document.getElementById("auditActorFilter");
    const auditActionFilter = document.getElementById("auditActionFilter");
    const auditDateFrom = document.getElementById("auditDateFrom");
    const auditDateTo = document.getElementById("auditDateTo");
    const auditFilterResetButton = document.getElementById("auditFilterResetButton");
    const auditTableBody = document.getElementById("auditTableBody");
    const auditPaginationInfo = document.getElementById("auditPaginationInfo");
    const auditPrevButton = document.getElementById("auditPrevButton");
    const auditNextButton = document.getElementById("auditNextButton");

    const state = {
        page: 0,
        size: 10,
        totalPages: 0,
        totalElements: 0,
        target: "",
        actor: "",
        action: "",
        dateFrom: "",
        dateTo: ""
    };

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
            throw new Error(payload && payload.message ? payload.message : "요청 처리에 실패했습니다.");
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

        return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")} ${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
    }

    function actionLabel(action) {
        return {
            CREATE_MEMBER: "계정 생성",
            UPDATE_MEMBER_ROLE: "권한 변경",
            UPDATE_MEMBER_STATUS: "상태 변경",
            DELETE_MEMBER: "계정 삭제"
        }[action] || action;
    }

    function createFallbackInfraSummary() {
        return {
            aiRequests: 0,
            rpaSuccessRate: "-",
            activeChatSessions: 0,
            rpaQueueCount: 0,
            aiEngineStatus: "집계 대기",
            rpaNodeStatus: "집계 대기",
            recentErrorCount: "0건"
        };
    }

    function renderDashboard(summary) {
        const accountSummary = summary && summary.accountSummary ? summary.accountSummary : {};
        const infraSummary = summary && summary.infraSummary ? summary.infraSummary : createFallbackInfraSummary();

        if (aiRequestCount) {
            aiRequestCount.textContent = String(infraSummary.aiRequests);
        }
        if (rpaSuccessRate) {
            rpaSuccessRate.textContent = String(infraSummary.rpaSuccessRate);
        }
        if (activeChatSessions) {
            activeChatSessions.textContent = String(infraSummary.activeChatSessions);
        }
        if (rpaQueueCount) {
            rpaQueueCount.textContent = String(infraSummary.rpaQueueCount);
        }

        if (totalMemberCount) {
            totalMemberCount.textContent = String(accountSummary.totalMemberCount || 0);
        }
        if (activeMemberCount) {
            activeMemberCount.textContent = String(accountSummary.activeMemberCount || 0);
        }
        if (recentLoginCount) {
            recentLoginCount.textContent = String(accountSummary.recentLoginCount || 0);
        }
        if (recentLoginHint) {
            recentLoginHint.textContent = `최근 ${accountSummary.recentLoginWindowDays || 0}일 로그인 기준`;
        }

        if (aiEngineStatus) {
            aiEngineStatus.textContent = String(infraSummary.aiEngineStatus);
        }
        if (rpaNodeStatus) {
            rpaNodeStatus.textContent = String(infraSummary.rpaNodeStatus);
        }
        if (recentErrorCount) {
            recentErrorCount.textContent = String(infraSummary.recentErrorCount);
        }
    }

    function renderAuditLogs(pageData) {
        const content = pageData && Array.isArray(pageData.content) ? pageData.content : [];

        if (!auditTableBody) {
            return;
        }

        if (content.length === 0) {
            auditTableBody.innerHTML = `
                <tr>
                    <td colspan="6" class="px-4 py-10 text-center text-gray-400">조회 조건에 맞는 감사 로그가 없습니다.</td>
                </tr>
            `;
        } else {
            auditTableBody.innerHTML = content.map(function (log) {
                return `
                    <tr>
                        <td class="px-4 py-4 log-timestamp">${formatDateTime(log.createdAt)}</td>
                        <td class="px-4 py-4"><span class="log-type type-auth">${actionLabel(log.action)}</span></td>
                        <td class="px-4 py-4 text-gray-700">${log.actorName ? `${log.actorName} (${log.actorLoginId})` : "SYSTEM"}</td>
                        <td class="px-4 py-4 text-gray-700">${log.targetLabel || "-"}</td>
                        <td class="px-4 py-4 text-gray-500">${log.details || "-"}</td>
                        <td class="px-4 py-4 text-gray-500 font-mono">${log.ipAddress || "-"}</td>
                    </tr>
                `;
            }).join("");
        }

        state.totalPages = pageData ? pageData.totalPages : 0;
        state.totalElements = pageData ? pageData.totalElements : 0;

        if (auditPaginationInfo) {
            auditPaginationInfo.textContent = `페이지 ${state.page + 1} / ${Math.max(state.totalPages, 1)} · 총 ${state.totalElements}건`;
        }
        if (auditPrevButton) {
            auditPrevButton.disabled = state.page <= 0;
        }
        if (auditNextButton) {
            auditNextButton.disabled = state.page >= Math.max(state.totalPages - 1, 0);
        }
    }

    function buildAuditQuery() {
        const params = new URLSearchParams({
            page: String(state.page),
            size: String(state.size)
        });

        if (state.target) {
            params.set("target", state.target);
        }
        if (state.actor) {
            params.set("actor", state.actor);
        }
        if (state.action) {
            params.set("action", state.action);
        }
        if (state.dateFrom) {
            params.set("dateFrom", state.dateFrom);
        }
        if (state.dateTo) {
            params.set("dateTo", state.dateTo);
        }

        return params.toString();
    }

    async function loadDashboard() {
        const summary = await requestAdminApi("/api/v1/admin/dashboard", {
            method: "GET"
        });
        renderDashboard(summary);
    }

    async function loadAuditLogs() {
        const pageData = await requestAdminApi(`/api/v1/admin/audit-logs?${buildAuditQuery()}`, {
            method: "GET"
        });
        renderAuditLogs(pageData);
    }

    async function refreshPage() {
        try {
            await Promise.all([loadDashboard(), loadAuditLogs()]);
        } catch (error) {
            if (auditTableBody) {
                auditTableBody.innerHTML = `
                    <tr>
                        <td colspan="6" class="px-4 py-10 text-center text-red-400">${error.message}</td>
                    </tr>
                `;
            }
        }
    }

    function readFilterState() {
        state.target = auditTargetFilter ? auditTargetFilter.value.trim() : "";
        state.actor = auditActorFilter ? auditActorFilter.value.trim() : "";
        state.action = auditActionFilter ? auditActionFilter.value : "";
        state.dateFrom = auditDateFrom ? auditDateFrom.value : "";
        state.dateTo = auditDateTo ? auditDateTo.value : "";
    }

    function resetFilters() {
        if (auditTargetFilter) {
            auditTargetFilter.value = "";
        }
        if (auditActorFilter) {
            auditActorFilter.value = "";
        }
        if (auditActionFilter) {
            auditActionFilter.value = "";
        }
        if (auditDateFrom) {
            auditDateFrom.value = "";
        }
        if (auditDateTo) {
            auditDateTo.value = "";
        }

        state.page = 0;
        readFilterState();
    }

    if (auditFilterForm) {
        auditFilterForm.addEventListener("submit", async function (event) {
            event.preventDefault();
            state.page = 0;
            readFilterState();
            await loadAuditLogs();
        });
    }

    if (auditFilterResetButton) {
        auditFilterResetButton.addEventListener("click", async function () {
            resetFilters();
            await loadAuditLogs();
        });
    }

    if (auditPrevButton) {
        auditPrevButton.addEventListener("click", async function () {
            if (state.page <= 0) {
                return;
            }
            state.page -= 1;
            await loadAuditLogs();
        });
    }

    if (auditNextButton) {
        auditNextButton.addEventListener("click", async function () {
            if (state.page >= state.totalPages - 1) {
                return;
            }
            state.page += 1;
            await loadAuditLogs();
        });
    }

    if (ensureAdminSession()) {
        readFilterState();
        refreshPage();
    }
})();
