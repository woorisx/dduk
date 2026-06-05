(function () {
    const state = {
        page: 0,
        size: 10
    };

    const noticeTableBody = document.getElementById("noticeTableBody");
    const noticePagination = document.getElementById("noticePagination");
    const noticeFilterForm = document.getElementById("noticeFilterForm");
    const noticeMessage = document.getElementById("noticeMessage");

    const formModal = document.getElementById("noticeModal");
    const noticeForm = document.getElementById("noticeForm");
    const formModalTitle = document.getElementById("noticeModalTitle");
    const createNoticeButton = document.getElementById("createNoticeBtn");
    const closeNoticeModalButton = document.getElementById("closeNoticeModal");
    const cancelNoticeModalButton = document.getElementById("cancelNoticeModal");
    const saveNoticeButton = document.getElementById("saveNoticeBtn");

    const detailModal = document.getElementById("noticeDetailModal");
    const detailTitle = document.getElementById("detailNoticeTitle");
    const detailType = document.getElementById("detailNoticeType");
    const detailAuthor = document.getElementById("detailNoticeAuthor");
    const detailPeriod = document.getElementById("detailNoticePeriod");
    const detailViews = document.getElementById("detailNoticeViews");
    const detailContent = document.getElementById("detailNoticeContent");
    const closeDetailModalButton = document.getElementById("closeNoticeDetail");
    const closeDetailModalFooterButton = document.getElementById("closeNoticeDetailFooter");

    const kpiTotal = document.getElementById("kpiTotal");
    const kpiUrgent = document.getElementById("kpiUrgent");
    const kpiTopView = document.getElementById("kpiTopView");

    function ensureAdminSession() {
        if (!window.ddukSession || typeof window.ddukSession.requireRole !== "function") {
            return true;
        }
        return Boolean(window.ddukSession.requireRole(["ADMIN"]));
    }

    function setNoticeMessage(text, type) {
        if (!noticeMessage) {
            return;
        }

        if (!text) {
            noticeMessage.className = "hidden";
            noticeMessage.textContent = "";
            return;
        }

        const palette = type === "error"
            ? "border-red-200 bg-red-50 text-red-700"
            : "border-emerald-200 bg-emerald-50 text-emerald-700";
        noticeMessage.className = `rounded-2xl border px-4 py-3 text-sm font-medium ${palette}`;
        noticeMessage.textContent = text;
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
            throw new Error(payload && payload.message ? payload.message : "공지사항 요청 처리에 실패했습니다.");
        }

        return payload.data;
    }

    function getTypeLabel(type) {
        return {
            NORMAL: "일반",
            URGENT: "긴급",
            MAINTENANCE: "점검"
        }[type] || type;
    }

    function getTypeBadge(type) {
        const palette = {
            NORMAL: "bg-blue-50 text-blue-700",
            URGENT: "bg-red-50 text-red-700",
            MAINTENANCE: "bg-gray-100 text-gray-700"
        }[type] || "bg-gray-100 text-gray-700";

        return `<span class="${palette} rounded-lg px-2.5 py-1 text-[10px] font-bold uppercase tracking-wider">${getTypeLabel(type)}</span>`;
    }

    function formatDate(value) {
        if (!value) {
            return "-";
        }
        return value.replace(/-/g, ".");
    }

    function formatPeriod(startDate, endDate) {
        if (!startDate && !endDate) {
            return "상시게시";
        }
        return `${startDate || "-"} ~ ${endDate || "-"}`;
    }

    function resetForm() {
        noticeForm.reset();
        document.getElementById("noticeId").value = "";
    }

    function openFormModal(isEdit) {
        formModalTitle.textContent = isEdit ? "공지사항 수정" : "새 공지 작성";
        formModal.classList.remove("hidden");
    }

    function closeFormModal() {
        formModal.classList.add("hidden");
        resetForm();
    }

    function openDetailModal(notice) {
        detailTitle.textContent = notice.title;
        detailType.innerHTML = getTypeBadge(notice.type);
        detailAuthor.textContent = notice.authorId || "-";
        detailPeriod.textContent = formatPeriod(notice.startDate, notice.endDate);
        detailViews.textContent = String(notice.viewCount);
        detailContent.textContent = notice.content;
        detailModal.classList.remove("hidden");
        if (window.lucide) {
            window.lucide.createIcons();
        }
    }

    function closeDetailModal() {
        detailModal.classList.add("hidden");
    }

    function renderLoading() {
        noticeTableBody.innerHTML = `
            <tr>
                <td colspan="6" class="px-5 py-12 text-center text-gray-400 font-medium">데이터를 불러오는 중입니다...</td>
            </tr>
        `;
    }

    function renderTable(notices) {
        if (!notices || notices.length === 0) {
            noticeTableBody.innerHTML = `
                <tr>
                    <td colspan="6" class="px-5 py-12 text-center text-gray-400 font-medium">등록된 공지사항이 없습니다.</td>
                </tr>
            `;
            return;
        }

        noticeTableBody.innerHTML = notices.map(function (notice) {
            return `
                <tr class="transition-colors duration-150 hover:bg-gray-50/50">
                    <td class="px-5 py-4">${getTypeBadge(notice.type)}</td>
                    <td class="px-5 py-4 font-bold text-gray-900">
                        <button type="button" class="notice-view-btn hover:text-blue-600" data-notice-id="${notice.id}">
                            ${notice.title}
                        </button>
                    </td>
                    <td class="px-5 py-4 font-mono text-xs text-gray-500">${formatDate((notice.createdAt || "").split("T")[0] || "")}</td>
                    <td class="px-5 py-4 font-mono text-xs text-gray-500">${formatPeriod(notice.startDate, notice.endDate)}</td>
                    <td class="px-5 py-4 text-center font-bold text-gray-700">${notice.viewCount}</td>
                    <td class="space-x-2 px-5 py-4 text-center">
                        <button type="button" class="notice-edit-btn text-blue-600 hover:text-blue-800" data-notice-id="${notice.id}">
                            <i data-lucide="edit" class="inline h-4 w-4"></i>
                        </button>
                        <button type="button" class="notice-delete-btn text-red-600 hover:text-red-800" data-notice-id="${notice.id}">
                            <i data-lucide="trash-2" class="inline h-4 w-4"></i>
                        </button>
                    </td>
                </tr>
            `;
        }).join("");

        noticeTableBody.querySelectorAll(".notice-view-btn").forEach(function (button) {
            button.addEventListener("click", function () {
                viewNotice(button.dataset.noticeId);
            });
        });

        noticeTableBody.querySelectorAll(".notice-edit-btn").forEach(function (button) {
            button.addEventListener("click", function () {
                editNotice(button.dataset.noticeId);
            });
        });

        noticeTableBody.querySelectorAll(".notice-delete-btn").forEach(function (button) {
            button.addEventListener("click", function () {
                deleteNotice(button.dataset.noticeId);
            });
        });

        if (window.lucide) {
            window.lucide.createIcons();
        }
    }

    function renderPagination(currentPage, totalPages) {
        if (totalPages <= 1) {
            noticePagination.innerHTML = "";
            return;
        }

        let html = "";
        html += `<button class="rounded-xl border border-gray-200 px-3 py-1.5 text-gray-600 transition-all duration-200 hover:bg-gray-50 disabled:opacity-40" ${currentPage === 0 ? "disabled" : ""} data-page="${currentPage - 1}"><i data-lucide="chevron-left" class="h-4 w-4"></i></button>`;

        for (let i = 0; i < totalPages; i += 1) {
            if (i === currentPage) {
                html += `<button class="rounded-xl bg-gray-900 px-3 py-1.5 text-xs font-bold text-white shadow-sm">${i + 1}</button>`;
            } else {
                html += `<button class="rounded-xl border border-gray-200 px-3 py-1.5 text-xs font-bold text-gray-600 transition-all duration-200 hover:bg-gray-50" data-page="${i}">${i + 1}</button>`;
            }
        }

        html += `<button class="rounded-xl border border-gray-200 px-3 py-1.5 text-gray-600 transition-all duration-200 hover:bg-gray-50 disabled:opacity-40" ${currentPage === totalPages - 1 ? "disabled" : ""} data-page="${currentPage + 1}"><i data-lucide="chevron-right" class="h-4 w-4"></i></button>`;
        noticePagination.innerHTML = html;

        noticePagination.querySelectorAll("[data-page]").forEach(function (button) {
            button.addEventListener("click", function () {
                loadNotices(Number(button.dataset.page));
            });
        });

        if (window.lucide) {
            window.lucide.createIcons();
        }
    }

    function updateSummary(pageData) {
        kpiTotal.textContent = String(pageData.totalElements || 0);
        const urgentCount = (pageData.content || []).filter(function (notice) {
            return notice.type === "URGENT";
        }).length;
        kpiUrgent.textContent = String(urgentCount);

        if (pageData.content && pageData.content.length > 0) {
            const topNotice = [...pageData.content].sort(function (left, right) {
                return right.viewCount - left.viewCount;
            })[0];
            kpiTopView.textContent = topNotice.title;
        } else {
            kpiTopView.textContent = "-";
        }
    }

    async function loadNotices(page) {
        renderLoading();
        setNoticeMessage("", "");

        const type = document.getElementById("noticeTypeFilter").value;
        const keyword = document.getElementById("noticeKeyword").value.trim();
        const params = new URLSearchParams({
            page: String(page),
            size: String(state.size)
        });

        if (type) {
            params.set("type", type);
        }
        if (keyword) {
            params.set("keyword", keyword);
        }

        try {
            const pageData = await requestAdminApi(`/api/v1/admin/notices?${params.toString()}`, {
                method: "GET"
            });
            state.page = pageData.number;
            renderTable(pageData.content);
            renderPagination(pageData.number, pageData.totalPages);
            updateSummary(pageData);
        } catch (error) {
            console.error("[Notice Admin] Failed to load notices", error);
            noticeTableBody.innerHTML = `
                <tr>
                    <td colspan="6" class="px-5 py-12 text-center text-red-400 font-medium">데이터를 불러오는 중 오류가 발생했습니다.</td>
                </tr>
            `;
            setNoticeMessage(error.message, "error");
        }
    }

    async function editNotice(id) {
        try {
            const data = await requestAdminApi(`/api/v1/admin/notices/${id}`, {
                method: "GET"
            });
            document.getElementById("noticeId").value = data.id;
            document.getElementById("noticeType").value = data.type;
            document.getElementById("noticeTitle").value = data.title;
            document.getElementById("noticeContent").value = data.content;
            document.getElementById("noticeStartDate").value = data.startDate || "";
            document.getElementById("noticeEndDate").value = data.endDate || "";
            openFormModal(true);
        } catch (error) {
            setNoticeMessage(error.message, "error");
        }
    }

    async function viewNotice(id) {
        try {
            const data = await requestAdminApi(`/api/v1/admin/notices/${id}?increaseViewCount=true`, {
                method: "GET"
            });
            openDetailModal(data);
            await loadNotices(state.page);
        } catch (error) {
            setNoticeMessage(error.message, "error");
        }
    }

    async function deleteNotice(id) {
        if (!window.confirm("정말로 이 공지사항을 삭제하시겠습니까?")) {
            return;
        }

        try {
            await requestAdminApi(`/api/v1/admin/notices/${id}`, {
                method: "DELETE"
            });
            setNoticeMessage("공지사항을 삭제했습니다.", "success");
            await loadNotices(0);
        } catch (error) {
            setNoticeMessage(error.message, "error");
        }
    }

    function bindEvents() {
        noticeFilterForm.addEventListener("submit", function (event) {
            event.preventDefault();
            loadNotices(0);
        });

        createNoticeButton.addEventListener("click", function () {
            resetForm();
            openFormModal(false);
        });

        closeNoticeModalButton.addEventListener("click", closeFormModal);
        cancelNoticeModalButton.addEventListener("click", closeFormModal);
        closeDetailModalButton.addEventListener("click", closeDetailModal);
        closeDetailModalFooterButton.addEventListener("click", closeDetailModal);

        noticeForm.addEventListener("submit", async function (event) {
            event.preventDefault();

            const id = document.getElementById("noticeId").value;
            const payload = {
                type: document.getElementById("noticeType").value,
                title: document.getElementById("noticeTitle").value,
                content: document.getElementById("noticeContent").value,
                startDate: document.getElementById("noticeStartDate").value || null,
                endDate: document.getElementById("noticeEndDate").value || null
            };

            saveNoticeButton.disabled = true;

            try {
                if (id) {
                    await requestAdminApi(`/api/v1/admin/notices/${id}`, {
                        method: "PUT",
                        body: JSON.stringify(payload)
                    });
                    setNoticeMessage("공지사항을 수정했습니다.", "success");
                } else {
                    await requestAdminApi("/api/v1/admin/notices", {
                        method: "POST",
                        body: JSON.stringify(payload)
                    });
                    setNoticeMessage("공지사항을 등록했습니다.", "success");
                }

                closeFormModal();
                await loadNotices(state.page);
            } catch (error) {
                setNoticeMessage(error.message, "error");
            } finally {
                saveNoticeButton.disabled = false;
            }
        });
    }

    if (ensureAdminSession()) {
        bindEvents();
        loadNotices(0);
    }
})();
