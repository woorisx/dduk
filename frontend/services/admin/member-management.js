(function () {
    const memberForm = document.getElementById("memberCreateForm");
    const memberMessage = document.getElementById("memberMessage");
    const memberTableBody = document.getElementById("memberTableBody");
    const memberFilterForm = document.getElementById("memberFilterForm");
    const memberKeywordInput = document.getElementById("memberKeyword");
    const memberRoleFilter = document.getElementById("memberRoleFilter");
    const memberActiveFilter = document.getElementById("memberActiveFilter");
    const memberFilterResetButton = document.getElementById("memberFilterResetButton");
    const memberPaginationInfo = document.getElementById("memberPaginationInfo");
    const memberPrevButton = document.getElementById("memberPrevButton");
    const memberNextButton = document.getElementById("memberNextButton");

    const memberTotalCount = document.getElementById("memberTotalCount");
    const memberActiveCount = document.getElementById("memberActiveCount");
    const memberRecentLoginCount = document.getElementById("memberRecentLoginCount");
    const memberRecentLoginWindow = document.getElementById("memberRecentLoginWindow");
    const memberLoadedCount = document.getElementById("memberLoadedCount");
    const memberLoadedHint = document.getElementById("memberLoadedHint");
    const memberAuditTableBody = document.getElementById("memberAuditTableBody");
    const memberAuditHint = document.getElementById("memberAuditHint");

    const state = {
        page: 0,
        size: 10,
        totalPages: 0,
        totalElements: 0,
        keyword: "",
        role: "",
        active: "",
        auditPageSize: 5
    };

    function ensureAdminSession() {
        if (!window.ddukSession || typeof window.ddukSession.requireRole !== "function") {
            return true;
        }
        return Boolean(window.ddukSession.requireRole(["ADMIN"]));
    }

    function setMemberMessage(text, type) {
        if (!memberMessage) {
            return;
        }

        memberMessage.textContent = text;
        memberMessage.className = `message ${type || ""}`.trim();
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

    function createRoleOptions(currentRole) {
        return ["ADMIN", "HR", "INVENTORY"].map(function (role) {
            const selected = role === currentRole ? " selected" : "";
            return `<option value="${role}"${selected}>${role}</option>`;
        }).join("");
    }

    function createRoleBadge(role) {
        const cssClass = {
            ADMIN: "bg-blue-50 text-blue-700 border-blue-100",
            HR: "bg-purple-50 text-purple-700 border-purple-100",
            INVENTORY: "bg-amber-50 text-amber-700 border-amber-100"
        }[role] || "bg-gray-50 text-gray-700 border-gray-100";

        return `<span class="inline-flex items-center px-2.5 py-0.5 rounded-lg text-[10px] font-bold border ${cssClass}">${role}</span>`;
    }

    function auditActionLabel(action) {
        return {
            CREATE_MEMBER: "계정 생성",
            UPDATE_MEMBER_ROLE: "권한 변경",
            UPDATE_MEMBER_STATUS: "상태 변경",
            DELETE_MEMBER: "계정 삭제"
        }[action] || action;
    }

    function createStatusBadge(active) {
        const bgClass = active ? "bg-emerald-50 text-emerald-700" : "bg-rose-50 text-rose-700";
        const dotClass = active ? "bg-emerald-500" : "bg-rose-500";
        return `
            <span class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-bold ${bgClass}">
                <span class="w-1.5 h-1.5 rounded-full ${dotClass}"></span>
                ${active ? "활성" : "비활성"}
            </span>
        `;
    }

    function renderMembers(pageData) {
        const members = pageData && Array.isArray(pageData.content) ? pageData.content : [];

        if (members.length === 0) {
            memberTableBody.innerHTML = `
                <tr>
                    <td colspan="8" class="px-5 py-12 text-center text-gray-400 font-medium">조건에 맞는 관리자 계정이 없습니다.</td>
                </tr>
            `;
        } else {
            memberTableBody.innerHTML = members.map(function (member) {
                const toggleLabel = member.active ? "비활성화" : "활성화";

                return `
                    <tr class="hover:bg-gray-50/50 transition-colors duration-150">
                        <td class="px-5 py-4 text-gray-400 font-mono text-[11px]">${member.id}</td>
                        <td class="px-5 py-4 font-bold text-gray-900">${member.name}</td>
                        <td class="px-5 py-4 text-gray-600 font-mono">${member.loginId}</td>
                        <td class="px-5 py-4">
                            <select class="matrix-select border border-gray-200 rounded-xl px-2.5 py-1 text-xs font-semibold focus:outline-none bg-white text-gray-700 cursor-pointer" data-role-select="${member.id}">
                                ${createRoleOptions(member.role)}
                            </select>
                        </td>
                        <td class="px-5 py-4 text-center">${createStatusBadge(member.active)}</td>
                        <td class="px-5 py-4 text-gray-500 font-medium">${formatDateTime(member.lastLoginAt)}</td>
                        <td class="px-5 py-4 text-gray-500 font-medium">${formatDateTime(member.createdAt)}</td>
                        <td class="px-5 py-4">
                            <div class="flex items-center justify-center gap-2">
                                <button type="button" class="rounded-xl border border-gray-200 hover:bg-gray-50 px-2.5 py-1.5 text-xs font-bold text-gray-600 transition-colors duration-150" data-action="update-role" data-member-id="${member.id}">권한 저장</button>
                                <button type="button" class="rounded-xl border border-gray-200 hover:bg-gray-50 px-2.5 py-1.5 text-xs font-bold text-gray-600 transition-colors duration-150" data-action="toggle-status" data-member-id="${member.id}" data-active="${member.active}">${toggleLabel}</button>
                                <button type="button" class="rounded-xl border border-rose-100 hover:bg-rose-50 px-2.5 py-1.5 text-xs font-bold text-rose-600 transition-colors duration-150" data-action="delete-member" data-member-id="${member.id}" data-member-name="${member.name}" data-member-login-id="${member.loginId}">삭제</button>
                            </div>
                        </td>
                    </tr>
                `;
            }).join("");
        }

        state.totalPages = pageData ? pageData.totalPages : 0;
        state.totalElements = pageData ? pageData.totalElements : 0;
        memberPaginationInfo.textContent = `페이지 ${state.page + 1} / ${Math.max(state.totalPages, 1)} · 총 ${state.totalElements}건`;
        memberPrevButton.disabled = state.page <= 0;
        memberNextButton.disabled = state.page >= Math.max(state.totalPages - 1, 0);

        memberLoadedCount.textContent = String(members.length);
        memberLoadedHint.textContent = state.totalElements > 0
            ? `총 ${state.totalElements}건 중 현재 페이지 표시`
            : "현재 화면에 표시할 계정이 없습니다.";
    }

    function renderSummary(summary) {
        if (!summary || !summary.accountSummary) {
            return;
        }

        const accountSummary = summary.accountSummary;
        memberTotalCount.textContent = String(accountSummary.totalMemberCount);
        memberActiveCount.textContent = String(accountSummary.activeMemberCount);
        memberRecentLoginCount.textContent = String(accountSummary.recentLoginCount);
        memberRecentLoginWindow.textContent = `최근 ${accountSummary.recentLoginWindowDays}일 로그인 계정`;
    }

    function renderRecentAuditLogs(pageData) {
        if (!memberAuditTableBody) {
            return;
        }

        const logs = pageData && Array.isArray(pageData.content) ? pageData.content : [];

        if (logs.length === 0) {
            memberAuditTableBody.innerHTML = `
                <tr>
                    <td colspan="5" class="px-4 py-10 text-center text-gray-400">표시할 최근 감사 로그가 없습니다.</td>
                </tr>
            `;
            if (memberAuditHint) {
                memberAuditHint.textContent = "아직 기록된 관리자 계정 변경 이력이 없습니다.";
            }
            return;
        }

        memberAuditTableBody.innerHTML = logs.map(function (log) {
            const actor = log.actorName ? `${log.actorName} (${log.actorLoginId})` : "SYSTEM";
            return `
                <tr class="hover:bg-gray-50/50 transition-colors duration-150">
                    <td class="px-5 py-4 text-gray-400 font-mono text-[11px]">${formatDateTime(log.createdAt)}</td>
                    <td class="px-5 py-4"><span class="inline-flex items-center px-2.5 py-0.5 rounded-lg text-[10px] font-bold border border-indigo-100 bg-indigo-50 text-indigo-700">${auditActionLabel(log.action)}</span></td>
                    <td class="px-5 py-4 text-gray-700 font-medium">${actor}</td>
                    <td class="px-5 py-4 text-gray-700 font-medium">${log.targetLabel || "-"}</td>
                    <td class="px-5 py-4 text-gray-500 font-medium">${log.details || "-"}</td>
                </tr>
            `;
        }).join("");

        if (memberAuditHint) {
            memberAuditHint.textContent = `최근 ${logs.length}건의 계정 변경 로그를 표시 중입니다.`;
        }
    }

    async function loadSummary() {
        const summary = await requestAdminApi("/api/v1/admin/dashboard", { method: "GET" });
        renderSummary(summary);
    }

    async function loadRecentAuditLogs() {
        const logsPage = await requestAdminApi(`/api/v1/admin/audit-logs?page=0&size=${state.auditPageSize}`, {
            method: "GET"
        });
        renderRecentAuditLogs(logsPage);
    }

    function buildMembersQuery() {
        const params = new URLSearchParams({
            page: String(state.page),
            size: String(state.size)
        });

        if (state.keyword) {
            params.set("keyword", state.keyword);
        }
        if (state.role) {
            params.set("role", state.role);
        }
        if (state.active !== "") {
            params.set("active", state.active);
        }

        return params.toString();
    }

    async function loadMembers() {
        const membersPage = await requestAdminApi(`/api/v1/admin/members?${buildMembersQuery()}`, {
            method: "GET"
        });
        renderMembers(membersPage);
    }

    async function refreshPage() {
        try {
            await Promise.all([loadSummary(), loadMembers(), loadRecentAuditLogs()]);
        } catch (error) {
            setMemberMessage(error.message, "error");
            memberTableBody.innerHTML = `
                <tr>
                    <td colspan="8" class="px-4 py-10 text-center text-red-400">관리자 계정 데이터를 불러오지 못했습니다.</td>
                </tr>
            `;
            if (memberAuditTableBody) {
                memberAuditTableBody.innerHTML = `
                    <tr>
                        <td colspan="5" class="px-4 py-10 text-center text-red-400">최근 감사 로그를 불러오지 못했습니다.</td>
                    </tr>
                `;
            }
        }
    }

    function readFilterState() {
        state.keyword = memberKeywordInput.value.trim();
        state.role = memberRoleFilter.value;
        state.active = memberActiveFilter.value;
    }

    function resetFilters() {
        memberKeywordInput.value = "";
        memberRoleFilter.value = "";
        memberActiveFilter.value = "";
        state.page = 0;
        readFilterState();
    }

    if (memberForm) {
        memberForm.addEventListener("submit", async function (event) {
            event.preventDefault();

            const formData = new FormData(memberForm);
            const payload = {
                loginId: formData.get("loginId"),
                password: formData.get("password"),
                name: formData.get("name"),
                role: formData.get("role")
            };

            try {
                await requestAdminApi("/api/v1/admin/members", {
                    method: "POST",
                    body: JSON.stringify(payload)
                });

                memberForm.reset();
                setMemberMessage("관리자 계정을 생성했습니다.", "success");
                state.page = 0;
                await refreshPage();
            } catch (error) {
                setMemberMessage(error.message, "error");
            }
        });
    }

    if (memberFilterForm) {
        memberFilterForm.addEventListener("submit", async function (event) {
            event.preventDefault();
            state.page = 0;
            readFilterState();
            await refreshPage();
        });
    }

    if (memberFilterResetButton) {
        memberFilterResetButton.addEventListener("click", async function () {
            resetFilters();
            await refreshPage();
        });
    }

    if (memberPrevButton) {
        memberPrevButton.addEventListener("click", async function () {
            if (state.page <= 0) {
                return;
            }
            state.page -= 1;
            await loadMembers().catch(function (error) {
                setMemberMessage(error.message, "error");
            });
        });
    }

    if (memberNextButton) {
        memberNextButton.addEventListener("click", async function () {
            if (state.page >= state.totalPages - 1) {
                return;
            }
            state.page += 1;
            await loadMembers().catch(function (error) {
                setMemberMessage(error.message, "error");
            });
        });
    }

    if (memberTableBody) {
        memberTableBody.addEventListener("click", async function (event) {
            const button = event.target.closest("button");
            if (!button) {
                return;
            }

            const memberId = button.dataset.memberId;

            try {
                if (button.dataset.action === "update-role") {
                    const roleSelect = document.querySelector(`[data-role-select="${memberId}"]`);

                    await requestAdminApi(`/api/v1/admin/members/${memberId}/role`, {
                        method: "PATCH",
                        body: JSON.stringify({ role: roleSelect.value })
                    });

                    setMemberMessage("관리자 권한을 변경했습니다.", "success");
                }

                if (button.dataset.action === "toggle-status") {
                    const nextActive = button.dataset.active !== "true";

                    await requestAdminApi(`/api/v1/admin/members/${memberId}/status`, {
                        method: "PATCH",
                        body: JSON.stringify({ active: nextActive })
                    });

                    setMemberMessage("관리자 계정 상태를 변경했습니다.", "success");
                }

                if (button.dataset.action === "delete-member") {
                    const memberLabel = `${button.dataset.memberName} (${button.dataset.memberLoginId})`;
                    const confirmed = window.confirm(`${memberLabel} 계정을 삭제할까?\n삭제 후에는 복구할 수 없고, 발주 요청 이력이 있으면 삭제되지 않아.`);

                    if (!confirmed) {
                        return;
                    }

                    await requestAdminApi(`/api/v1/admin/members/${memberId}`, {
                        method: "DELETE"
                    });

                    setMemberMessage("관리자 계정을 삭제했습니다.", "success");
                    state.page = 0;
                }

                await refreshPage();
            } catch (error) {
                setMemberMessage(error.message, "error");
            }
        });
    }

    if (ensureAdminSession()) {
        readFilterState();
        refreshPage();
    }
})();
