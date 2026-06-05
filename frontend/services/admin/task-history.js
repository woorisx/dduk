(function () {
    const state = {
        page: 0,
        size: 20,
        totalPages: 0,
        totalElements: 0
    };

    const taskTableBody = document.getElementById("taskTableBody");
    const refreshButton = document.getElementById("refreshTasksBtn");
    const pageInfo = document.getElementById("pageInfo");
    const prevPageButton = document.getElementById("prevPageBtn");
    const nextPageButton = document.getElementById("nextPageBtn");
    const feedbackMessage = document.getElementById("taskHistoryMessage");

    function ensureAdminSession() {
        if (!window.ddukSession || typeof window.ddukSession.requireRole !== "function") {
            return true;
        }
        return Boolean(window.ddukSession.requireRole(["ADMIN"]));
    }

    function setFeedbackMessage(text, type) {
        if (!feedbackMessage) {
            return;
        }

        if (!text) {
            feedbackMessage.className = "hidden";
            feedbackMessage.textContent = "";
            return;
        }

        const palette = type === "error"
            ? "border-red-200 bg-red-50 text-red-700"
            : "border-emerald-200 bg-emerald-50 text-emerald-700";

        feedbackMessage.className = `rounded-2xl border px-4 py-3 text-sm font-medium ${palette}`;
        feedbackMessage.textContent = text;
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
            throw new Error(payload && payload.message ? payload.message : "작업 이력을 불러오지 못했습니다.");
        }

        return payload.data;
    }

    function renderLoading() {
        taskTableBody.innerHTML = `
            <tr>
                <td colspan="6" class="px-6 py-10 text-center text-gray-400">
                    <i data-lucide="loader-2" class="mx-auto mb-2 h-6 w-6 animate-spin"></i>
                    작업 이력을 불러오는 중입니다.
                </td>
            </tr>
        `;
        if (window.lucide) {
            window.lucide.createIcons();
        }
    }

    function formatDateTime(value) {
        if (!value) {
            return "-";
        }

        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return value;
        }

        return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")} ${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}:${String(date.getSeconds()).padStart(2, "0")}`;
    }

    function getStatusBadge(status) {
        const styles = {
            SUCCESS: {
                colorClass: "bg-emerald-50 text-emerald-700 ring-emerald-600/20",
                icon: "check-circle-2"
            },
            FAILED: {
                colorClass: "bg-red-50 text-red-700 ring-red-600/10",
                icon: "x-circle"
            },
            RUNNING: {
                colorClass: "bg-blue-50 text-blue-700 ring-blue-600/20",
                icon: "loader-2"
            },
            REQUESTED: {
                colorClass: "bg-gray-50 text-gray-600 ring-gray-500/10",
                icon: "clock"
            }
        };

        const style = styles[status] || {
            colorClass: "bg-gray-50 text-gray-600 ring-gray-500/10",
            icon: "help-circle"
        };
        const spinClass = status === "RUNNING" ? " animate-spin" : "";

        return `
            <span class="inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-xs font-medium ring-1 ring-inset ${style.colorClass}">
                <i data-lucide="${style.icon}" class="h-3.5 w-3.5${spinClass}"></i>
                ${status}
            </span>
        `;
    }

    function renderTable(tasks) {
        if (!tasks || tasks.length === 0) {
            taskTableBody.innerHTML = `
                <tr>
                    <td colspan="6" class="px-6 py-10 text-center text-gray-400">저장된 작업 이력이 없습니다.</td>
                </tr>
            `;
            return;
        }

        taskTableBody.innerHTML = tasks.map(function (task) {
            const typeIcon = task.taskType === "AI" ? "bot" : "cpu";
            const typePalette = task.taskType === "AI"
                ? "bg-indigo-50 text-indigo-600"
                : "bg-orange-50 text-orange-600";

            return `
                <tr class="group transition-colors hover:bg-gray-50/50">
                    <td class="px-6 py-4">
                        <div class="flex items-center gap-3">
                            <div class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${typePalette}">
                                <i data-lucide="${typeIcon}" class="h-4 w-4"></i>
                            </div>
                            <div>
                                <div class="font-semibold text-gray-900">${task.taskType}</div>
                                <div class="mt-0.5 font-mono text-xs text-gray-400">${task.taskId}</div>
                            </div>
                        </div>
                    </td>
                    <td class="px-6 py-4">
                        <span class="rounded-md border border-gray-200 bg-gray-50 px-2 py-1 font-mono text-sm text-gray-700">${task.actionName || "-"}</span>
                    </td>
                    <td class="px-6 py-4 text-sm text-gray-900">${formatDateTime(task.requestedAt)}</td>
                    <td class="px-6 py-4 text-sm text-gray-500">${formatDateTime(task.completedAt)}</td>
                    <td class="px-6 py-4">${getStatusBadge(task.status)}</td>
                    <td class="px-6 py-4 text-right">
                        <button type="button" class="view-detail-btn rounded-lg border border-indigo-100 bg-indigo-50 px-3 py-1.5 text-xs font-semibold text-indigo-600 transition-colors hover:bg-indigo-100 group-hover:opacity-100 sm:opacity-70" data-task-id="${task.taskId}">
                            상세 보기
                        </button>
                    </td>
                </tr>
            `;
        }).join("");

        taskTableBody.querySelectorAll(".view-detail-btn").forEach(function (button) {
            button.addEventListener("click", function () {
                openDetailModal(button.dataset.taskId);
            });
        });

        if (window.lucide) {
            window.lucide.createIcons();
        }
    }

    function updatePagination(pageData) {
        state.page = pageData.number;
        state.totalPages = pageData.totalPages;
        state.totalElements = pageData.totalElements;

        pageInfo.textContent = `페이지 ${pageData.number + 1} / ${Math.max(pageData.totalPages, 1)} · 총 ${pageData.totalElements}건`;
        prevPageButton.disabled = pageData.first;
        nextPageButton.disabled = pageData.last;
    }

    async function loadTaskHistory(page) {
        renderLoading();
        setFeedbackMessage("", "");

        try {
            const pageData = await requestAdminApi(`/api/v1/admin/tasks?page=${page}&size=${state.size}&sort=createdAt,desc`, {
                method: "GET"
            });
            renderTable(pageData.content);
            updatePagination(pageData);
        } catch (error) {
            console.error("[Task History] Failed to load list", error);
            taskTableBody.innerHTML = `
                <tr>
                    <td colspan="6" class="px-6 py-10 text-center text-red-500 font-semibold">작업 이력을 불러오지 못했습니다.</td>
                </tr>
            `;
            setFeedbackMessage(error.message, "error");
        }
    }

    function formatPayload(payload) {
        if (!payload) {
            return "No data";
        }

        try {
            return JSON.stringify(JSON.parse(payload), null, 2);
        } catch (error) {
            return payload;
        }
    }

    function showModal() {
        const modal = document.getElementById("detailModal");
        const backdrop = document.getElementById("modalBackdrop");
        const panel = document.getElementById("modalPanel");

        modal.classList.remove("hidden");
        setTimeout(function () {
            backdrop.classList.remove("opacity-0");
            backdrop.classList.add("opacity-100");
            panel.classList.remove("opacity-0", "translate-y-4", "sm:translate-y-0", "sm:scale-95");
            panel.classList.add("opacity-100", "translate-y-0", "sm:scale-100");
        }, 10);
    }

    function hideModal() {
        const modal = document.getElementById("detailModal");
        const backdrop = document.getElementById("modalBackdrop");
        const panel = document.getElementById("modalPanel");

        backdrop.classList.remove("opacity-100");
        backdrop.classList.add("opacity-0");
        panel.classList.remove("opacity-100", "translate-y-0", "sm:scale-100");
        panel.classList.add("opacity-0", "translate-y-4", "sm:translate-y-0", "sm:scale-95");

        setTimeout(function () {
            modal.classList.add("hidden");
        }, 300);
    }

    async function openDetailModal(taskId) {
        try {
            const data = await requestAdminApi(`/api/v1/admin/tasks/${taskId}`, {
                method: "GET"
            });

            document.getElementById("modalTaskId").textContent = data.taskId;
            document.getElementById("modalTaskType").textContent = data.taskType;
            document.getElementById("modalActionName").textContent = data.actionName;
            document.getElementById("modalStatusBadge").innerHTML = getStatusBadge(data.status);
            document.getElementById("modalRequestPayload").textContent = formatPayload(data.requestPayload);
            document.getElementById("modalResponsePayload").textContent = formatPayload(data.responsePayload);

            const errorSection = document.getElementById("modalErrorSection");
            if (data.errorMessage) {
                errorSection.classList.remove("hidden");
                document.getElementById("modalErrorMessage").textContent = data.errorMessage;
            } else {
                errorSection.classList.add("hidden");
                document.getElementById("modalErrorMessage").textContent = "";
            }

            if (window.lucide) {
                window.lucide.createIcons();
            }
            showModal();
        } catch (error) {
            setFeedbackMessage(error.message, "error");
        }
    }

    function setupModalEvents() {
        const closeButtons = [
            document.getElementById("closeModalBtn"),
            document.getElementById("closeModalBottomBtn"),
            document.getElementById("modalBackdrop")
        ];

        closeButtons.forEach(function (button) {
            if (button) {
                button.addEventListener("click", hideModal);
            }
        });
    }

    function initTaskHistory() {
        if (refreshButton) {
            refreshButton.addEventListener("click", function () {
                state.page = 0;
                loadTaskHistory(state.page);
            });
        }

        if (prevPageButton) {
            prevPageButton.addEventListener("click", function () {
                if (state.page <= 0) {
                    return;
                }
                loadTaskHistory(state.page - 1);
            });
        }

        if (nextPageButton) {
            nextPageButton.addEventListener("click", function () {
                if (state.page >= state.totalPages - 1) {
                    return;
                }
                loadTaskHistory(state.page + 1);
            });
        }

        setupModalEvents();
        loadTaskHistory(state.page);
    }

    if (ensureAdminSession()) {
        initTaskHistory();
    }
})();
