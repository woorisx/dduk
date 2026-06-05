(function () {
    const shared = window.OcrDocumentsShared;
    if (!shared) {
        throw new Error('OcrDocumentsShared is required before ocr-document-box-page.js');
    }

    const {
        getApiBaseUrl,
        getAuthHeaders,
        requestApi,
        requestJsonList,
        formatDateTime,
        formatCurrency,
        formatCurrencyWithoutSymbol,
        setMessage,
        translateDocumentType,
        translateProcessingStatus,
        translateReviewStatus,
        translateLinkStatus,
        translateLinkedDomainType,
        badgeClass,
        parseApiResponse,
        safeParseJson,
        buildPurchaseItemDraft,
        buildExpenseDraft,
        buildVoucherDraft,
        renderSearchResults,
        clampAmount,
        buildReviewDraft
    } = shared;

function initDocumentBoxPage() {
        const tableBody = document.getElementById("ocrTableBody");
        if (!tableBody) {
            return;
        }

        if (!window.ddukSession.requireRole(["ADMIN", "HR", "INVENTORY"])) {
            return;
        }

        const message = document.getElementById("ocrBoxMessage");
        const refreshButton = document.getElementById("refreshOcrDocumentsBtn");
        const keywordInput = document.getElementById("ocrKeyword");
        const statusFilter = document.getElementById("ocrStatusFilter");
        const reviewFilter = document.getElementById("ocrReviewFilter");
        const linkFilter = document.getElementById("ocrLinkFilter");
        const typeFilter = document.getElementById("ocrTypeFilter");
        const previewImage = document.getElementById("ocrPreviewImage");
        const previewFallback = document.getElementById("ocrPreviewFallback");
        const previewHint = document.getElementById("ocrPreviewHint");
        const rawResult = document.getElementById("ocrRawResult");
        const detailSummary = document.getElementById("ocrDetailSummary");
        const retryButton = document.getElementById("retryOcrBtn");
        const unlinkButton = document.getElementById("unlinkOcrBtn");
        const deleteButton = document.getElementById("deleteOcrBtn");
        const reviewedResultInput = document.getElementById("ocrReviewedResultInput");
        const reviewVendorName = document.getElementById("reviewVendorName");
        const reviewTransactionDate = document.getElementById("reviewTransactionDate");
        const reviewTotalAmount = document.getElementById("reviewTotalAmount");
        const reviewCurrency = document.getElementById("reviewCurrency");
        const reviewNotes = document.getElementById("reviewNotes");
        const reviewStatusBadge = document.getElementById("ocrReviewStatusBadge");
        const approveButton = document.getElementById("approveOcrBtn");
        const rejectButton = document.getElementById("rejectOcrBtn");
        const purchaseVendorId = document.getElementById("purchaseVendorId");
        const purchaseExpectedDate = document.getElementById("purchaseExpectedDate");
        const purchaseNote = document.getElementById("purchaseNote");
        const purchaseItemsJson = document.getElementById("purchaseItemsJson");
        const linkPurchaseOrderButton = document.getElementById("linkPurchaseOrderBtn");
        const vendorSearchKeyword = document.getElementById("vendorSearchKeyword");
        const searchVendorButton = document.getElementById("searchVendorBtn");
        const vendorSearchResults = document.getElementById("vendorSearchResults");
        const itemSearchKeyword = document.getElementById("itemSearchKeyword");
        const searchItemButton = document.getElementById("searchItemBtn");
        const itemSearchResults = document.getElementById("itemSearchResults");
        const expenseEmployeeId = document.getElementById("expenseEmployeeId");
        const expenseDate = document.getElementById("expenseDate");
        const expenseCategory = document.getElementById("expenseCategory");
        const expenseAmount = document.getElementById("expenseAmount");
        const expenseDescription = document.getElementById("expenseDescription");
        const expenseStatus = document.getElementById("expenseStatus");
        const linkExpenseButton = document.getElementById("linkExpenseBtn");
        const voucherDate = document.getElementById("voucherDate");
        const voucherType = document.getElementById("voucherType");
        const voucherVatType = document.getElementById("voucherVatType");
        const voucherVendorId = document.getElementById("voucherVendorId");
        const voucherVendorName = document.getElementById("voucherVendorName");
        const voucherDescription = document.getElementById("voucherDescription");
        const voucherSupplyAmount = document.getElementById("voucherSupplyAmount");
        const voucherVatAmount = document.getElementById("voucherVatAmount");
        const voucherFeeAmount = document.getElementById("voucherFeeAmount");
        const voucherBusinessAccountId = document.getElementById("voucherBusinessAccountId");
        const voucherSettlementAccountId = document.getElementById("voucherSettlementAccountId");
        const voucherBusinessAccountKeyword = document.getElementById("voucherBusinessAccountKeyword");
        const voucherSettlementAccountKeyword = document.getElementById("voucherSettlementAccountKeyword");
        const searchBusinessAccountBtn = document.getElementById("searchBusinessAccountBtn");
        const searchSettlementAccountBtn = document.getElementById("searchSettlementAccountBtn");
        const businessAccountResults = document.getElementById("businessAccountResults");
        const settlementAccountResults = document.getElementById("settlementAccountResults");
        const linkVoucherButton = document.getElementById("linkVoucherBtn");
        let selectedDocument = null;

        async function loadDocuments() {
            tableBody.innerHTML = `
                <tr>
                    <td colspan="9" class="px-6 py-10 text-center text-gray-400">OCR 문서를 불러오는 중이야.</td>
                </tr>
            `;

            try {
                const params = new URLSearchParams({
                    page: "0",
                    size: "20",
                    sort: "createdAt,desc"
                });

                if (keywordInput.value.trim()) {
                    params.set("keyword", keywordInput.value.trim());
                }
                if (statusFilter.value) {
                    params.set("processingStatus", statusFilter.value);
                }
                if (reviewFilter.value) {
                    params.set("reviewStatus", reviewFilter.value);
                }
                if (linkFilter.value) {
                    params.set("linkStatus", linkFilter.value);
                }
                if (typeFilter.value) {
                    params.set("documentType", typeFilter.value);
                }

                const pageData = await requestApi(`/api/v1/admin/ocr-documents?${params.toString()}`, "OCR 문서 목록을 불러오지 못했어.", {
                    headers: { "Content-Type": "application/json" }
                });
                renderDocuments(pageData.content || []);
            } catch (error) {
                setMessage(message, error.message, "error");
                tableBody.innerHTML = `
                    <tr>
                        <td colspan="9" class="px-6 py-10 text-center text-red-500">OCR 문서 목록을 불러오지 못했어.</td>
                    </tr>
                `;
            }
        }

        function renderDocuments(documents) {
            if (!documents.length) {
                tableBody.innerHTML = `
                    <tr>
                        <td colspan="9" class="px-6 py-10 text-center text-gray-400">조건에 맞는 OCR 문서가 없어.</td>
                    </tr>
                `;
                return;
            }

            tableBody.innerHTML = documents.map(function (document) {
                const linkLabel = document.linkedDomainType
                    ? `${translateLinkStatus(document.linkStatus)} / ${translateLinkedDomainType(document.linkedDomainType)}${document.linkedDomainId ? ` #${document.linkedDomainId}` : ""}`
                    : translateLinkStatus(document.linkStatus);

                const isSelected = selectedDocument && selectedDocument.id === document.id;
                const rowClass = isSelected 
                    ? "border-b border-indigo-100 bg-indigo-50/40 hover:bg-indigo-50/60 text-center font-medium" 
                    : "border-b border-gray-100 hover:bg-gray-50/60 text-center";

                return `
                    <tr class="${rowClass}" data-row-id="${document.id}">
                        <td class="px-6 py-4 text-sm font-semibold text-gray-900 text-center">${document.id}</td>
                        <td class="px-6 py-4 text-sm text-gray-700 text-center">${document.originalFilename}</td>
                        <td class="px-6 py-4 text-sm text-gray-600 text-center">${translateDocumentType(document.documentType)}</td>
                        <td class="px-6 py-4 text-sm font-medium text-gray-800 text-center">${translateProcessingStatus(document.processingStatus)}</td>
                        <td class="px-6 py-4 text-sm text-center"><span class="rounded-full px-2.5 py-1 text-xs font-semibold ${badgeClass(document.reviewStatus)}">${translateReviewStatus(document.reviewStatus)}</span></td>
                        <td class="px-6 py-4 text-sm text-center"><span class="rounded-full px-2.5 py-1 text-xs font-semibold ${badgeClass(document.linkStatus)}">${linkLabel}</span></td>
                        <td class="px-6 py-4 text-sm text-gray-600 text-center">${document.extractedVendor || "-"}</td>
                        <td class="px-6 py-4 text-sm text-gray-600 text-center">${formatCurrency(document.extractedAmount)}</td>
                        <td class="px-6 py-4 text-center">
                            <button type="button" class="view-ocr-detail rounded-lg border border-indigo-100 bg-indigo-50 px-3 py-1.5 text-xs font-semibold text-indigo-600 hover:bg-indigo-100" data-id="${document.id}">
                                상세 보기
                            </button>
                        </td>
                    </tr>
                `;
            }).join("");

            tableBody.querySelectorAll(".view-ocr-detail").forEach(function (button) {
                button.addEventListener("click", function () {
                    openDocumentDetail(button.dataset.id);
                });
            });
        }

        async function openDocumentDetail(id) {
            try {
                const detail = await requestApi(`/api/v1/admin/ocr-documents/${id}`, "OCR 문서 상세를 불러오지 못했어.", {
                    headers: { "Content-Type": "application/json" }
                });
                selectedDocument = detail;

                // 선택한 행에 강조 스타일을 적용한다.
                tableBody.querySelectorAll("tr[data-row-id]").forEach(function (row) {
                    if (Number(row.dataset.rowId) === Number(id)) {
                        row.className = "border-b border-indigo-100 bg-indigo-50/40 hover:bg-indigo-50/60 text-center font-medium";
                    } else {
                        row.className = "border-b border-gray-100 hover:bg-gray-50/60 text-center";
                    }
                });

                const linkLabel = detail.linkedDomainType
                    ? `${translateLinkStatus(detail.linkStatus)} / ${translateLinkedDomainType(detail.linkedDomainType)}${detail.linkedDomainId ? ` #${detail.linkedDomainId}` : ""}`
                    : translateLinkStatus(detail.linkStatus);

                detailSummary.innerHTML = `
                    <div class="grid grid-cols-1 gap-3 md:grid-cols-2">
                        <div><span class="font-semibold text-gray-500">문서 ID</span><div class="mt-1 text-sm text-gray-900">${detail.id}</div></div>
                        <div><span class="font-semibold text-gray-500">처리 상태</span><div class="mt-1 text-sm text-gray-900">${translateProcessingStatus(detail.processingStatus)}</div></div>
                        <div><span class="font-semibold text-gray-500">문서 유형</span><div class="mt-1 text-sm text-gray-900">${translateDocumentType(detail.documentType)}</div></div>
                        <div><span class="font-semibold text-gray-500">생성 일시</span><div class="mt-1 text-sm text-gray-900">${formatDateTime(detail.createdAt)}</div></div>
                        <div><span class="font-semibold text-gray-500">검토 상태</span><div class="mt-1 text-sm text-gray-900">${translateReviewStatus(detail.reviewStatus)}</div></div>
                        <div><span class="font-semibold text-gray-500">연결 상태</span><div class="mt-1 text-sm text-gray-900">${linkLabel}</div></div>
                        <div><span class="font-semibold text-gray-500">거래처</span><div class="mt-1 text-sm text-gray-900">${detail.extractedVendor || "-"}</div></div>
                        <div><span class="font-semibold text-gray-500">금액</span><div class="mt-1 text-sm text-gray-900">${formatCurrency(detail.extractedAmount)}</div></div>
                        <div><span class="font-semibold text-gray-500">연결 일시</span><div class="mt-1 text-sm text-gray-900">${formatDateTime(detail.linkedAt)}</div></div>
                        <div><span class="font-semibold text-gray-500">연결 담당자</span><div class="mt-1 text-sm text-gray-900">${detail.linkedByMemberId || "-"}</div></div>
                    </div>
                `;

                rawResult.textContent = detail.rawOcrResult || detail.errorMessage || "표시할 OCR 원본 결과가 없어.";
                reviewedResultInput.value = detail.reviewedResult || detail.rawOcrResult || "";
                reviewStatusBadge.className = `rounded-full px-3 py-1 text-xs font-semibold ${badgeClass(detail.reviewStatus)}`;
                reviewStatusBadge.textContent = translateReviewStatus(detail.reviewStatus) || "상태 없음";

                prefillReviewForm(detail);
                prefillPurchaseLinkForm(detail);
                prefillExpenseLinkForm(detail);
                prefillVoucherLinkForm(detail);
                syncReviewControls(detail);
                syncRetryControls(detail);
                syncUnlinkControls(detail);
                syncDeleteControls(detail);
                syncPurchaseLinkControls(detail);
                syncExpenseLinkControls(detail);
                syncVoucherLinkControls(detail);
                await loadPreview(detail.fileEndpoint, detail.mimeType);
            } catch (error) {
                setMessage(message, error.message, "error");
            }
        }

        function prefillReviewForm(detail) {
            const draft = buildReviewDraft(detail);
            reviewVendorName.value = draft.vendorName || "";
            reviewTransactionDate.value = draft.transactionDate || "";
            reviewTotalAmount.value = draft.totalAmount === null || draft.totalAmount === undefined ? "" : draft.totalAmount;
            reviewCurrency.value = draft.currency || "KRW";
            reviewNotes.value = draft.notes || "";
            syncReviewJsonPreview(detail);
        }

        function buildReviewPayload(detail) {
            const baseDraft = buildReviewDraft(detail);
            const amountValue = reviewTotalAmount.value.trim();
            const currencyValue = reviewCurrency.value.trim().toUpperCase();
            const notesValue = reviewNotes.value.trim();

            return {
                documentType: detail.documentType,
                vendorName: reviewVendorName.value.trim() || null,
                transactionDate: reviewTransactionDate.value || null,
                totalAmount: amountValue === "" ? null : Number(amountValue),
                currency: currencyValue || "KRW",
                items: Array.isArray(baseDraft.items) ? baseDraft.items : [],
                confidence: baseDraft.confidence ?? null,
                notes: notesValue || null
            };
        }

        function renderExcelTable(detail) {
            const container = document.getElementById("ocrExcelTableContainer");
            if (!container) return;

            const payload = buildReviewPayload(detail);
            const items = Array.isArray(payload.items) ? payload.items : [];

            let html = `
                <table class="min-w-full border-collapse border border-slate-200 text-xs bg-white">
                    <thead>
                        <tr class="bg-slate-100 text-slate-600 font-semibold border-b border-slate-200">
                            <th class="border border-slate-200 px-3 py-2.5 w-10 text-center bg-slate-50/80"></th>
                            <th class="border border-slate-200 px-3 py-2.5 text-left text-slate-700">품목명</th>
                            <th class="border border-slate-200 px-3 py-2.5 w-20 text-center text-slate-700">수량</th>
                            <th class="border border-slate-200 px-3 py-2.5 w-28 text-right text-slate-700">단가</th>
                            <th class="border border-slate-200 px-3 py-2.5 w-28 text-right text-slate-700">금액</th>
                        </tr>
                    </thead>
                    <tbody>
            `;

            if (items.length === 0) {
                html += `
                    <tr>
                        <td class="border border-slate-200 px-3 py-8 text-center text-slate-400 font-medium" colspan="5">
                            추출된 품목 데이터가 없어.
                        </td>
                    </tr>
                `;
            } else {
                items.forEach((item, idx) => {
                    const name = item.name || item.itemName || "-";
                    const qty = item.quantity ?? 1;
                    const price = item.unitPrice ?? 0;
                    const amt = item.amount ?? (qty * price);

                    html += `
                        <tr class="hover:bg-slate-50/50 transition">
                            <td class="border border-slate-200 px-3 py-2 text-center bg-slate-50/40 font-semibold text-slate-500">${idx + 1}</td>
                            <td class="border border-slate-200 px-3 py-2 text-slate-800 font-medium">${name}</td>
                            <td class="border border-slate-200 px-3 py-2 text-center text-slate-600 font-medium">${qty}</td>
                            <td class="border border-slate-200 px-3 py-2 text-right text-slate-600 font-mono">${formatCurrencyWithoutSymbol(price)}</td>
                            <td class="border border-slate-200 px-3 py-2 text-right text-slate-800 font-bold font-mono">${formatCurrencyWithoutSymbol(amt)}</td>
                        </tr>
                    `;
                });
            }

            html += `
                    </tbody>
                </table>
            `;

            // 요약 정보를 표 형태로 함께 보여준다.
            html += `
                <div class="mt-4 border-t border-slate-200 pt-4 px-1 pb-1">
                    <span class="mb-2.5 block text-xs font-bold text-slate-600 flex items-center gap-1.5">
                      <span class="inline-block w-1.5 h-3 bg-indigo-500 rounded-sm"></span>
                      검토 입력 요약
                    </span>
                    <div class="overflow-x-auto rounded-xl border border-slate-200 max-w-md bg-white">
                        <table class="min-w-full border-collapse text-xs">
                            <tbody>
                                <tr class="border-b border-slate-100">
                                    <td class="px-4 py-2 w-32 bg-slate-50/60 font-semibold text-slate-500 text-center border-r border-slate-100">거래처명</td>
                                    <td class="px-4 py-2 text-slate-800 font-medium">${payload.vendorName || "-"}</td>
                                </tr>
                                <tr class="border-b border-slate-100">
                                    <td class="px-4 py-2 bg-slate-50/60 font-semibold text-slate-500 text-center border-r border-slate-100">거래일자</td>
                                    <td class="px-4 py-2 text-slate-800 font-medium">${payload.transactionDate || "-"}</td>
                                </tr>
                                <tr class="border-b border-slate-100">
                                    <td class="px-4 py-2 bg-slate-50/60 font-semibold text-slate-500 text-center border-r border-slate-100">총 금액</td>
                                    <td class="px-4 py-2 text-slate-800 font-bold font-mono">${formatCurrency(payload.totalAmount)}</td>
                                </tr>
                                <tr>
                                    <td class="px-4 py-2 bg-slate-50/60 font-semibold text-slate-500 text-center border-r border-slate-100">통화</td>
                                    <td class="px-4 py-2 text-slate-800 font-medium">${payload.currency || "KRW"}</td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </div>
            `;

            container.innerHTML = html;
        }

        function syncReviewJsonPreview(detail) {
            if (!detail) {
                if (reviewedResultInput) reviewedResultInput.value = "";
                const container = document.getElementById("ocrExcelTableContainer");
                if (container) container.innerHTML = "<div class=\"text-xs text-slate-400 p-4 text-center\">문서를 선택하면 검토 요약이 여기에 표시돼.</div>";
                return;
            }
            const payload = buildReviewPayload(detail);
            if (reviewedResultInput) {
                reviewedResultInput.value = JSON.stringify(payload, null, 2);
            }
            renderExcelTable(detail);
        }

        function renderPurchaseItemsTable() {
            const container = document.getElementById("purchaseItemsTableContainer");
            if (!container) return;

            let items = [];
            try {
                items = JSON.parse(purchaseItemsJson.value || "[]");
                if (!Array.isArray(items)) items = [];
            } catch (e) {
                items = [];
            }

            let html = `
                <table class="min-w-full border-collapse border border-slate-200 text-xs bg-white">
                    <thead>
                        <tr class="bg-slate-100 text-slate-600 font-semibold border-b border-slate-200">
                            <th class="border border-slate-200 px-2 py-2.5 w-10 text-center bg-slate-50/80"></th>
                            <th class="border border-slate-200 px-3 py-2.5 text-left text-slate-700">품목명</th>
                            <th class="border border-slate-200 px-3 py-2.5 w-24 text-center text-slate-700">수량</th>
                            <th class="border border-slate-200 px-3 py-2.5 w-28 text-right text-slate-700">단가</th>
                            <th class="border border-slate-200 px-3 py-2.5 w-28 text-right text-slate-700">금액</th>
                            <th class="border border-slate-200 px-3 py-2.5 w-32 text-center text-slate-700">납기일</th>
                            <th class="border border-slate-200 px-3 py-2.5 text-left text-slate-700">메모</th>
                            <th class="border border-slate-200 px-2 py-2.5 w-12 text-center text-slate-700">삭제</th>
                        </tr>
                    </thead>
                    <tbody>
            `;

            if (items.length === 0) {
                html += `
                    <tr>
                        <td class="border border-slate-200 px-3 py-8 text-center text-slate-400 font-medium" colspan="8">
                            아직 발주 항목이 없어. 품목 검색으로 항목을 추가해 줘.
                        </td>
                    </tr>
                `;
            } else {
                items.forEach((item, idx) => {
                    const name = item.itemName || "-";
                    const qty = item.quantity ?? 1;
                    const price = item.unitPrice ?? 0;
                    const amt = qty * price;
                    const expectedDate = item.expectedDate || "";
                    const note = item.note || "";

                    html += `
                        <tr class="hover:bg-slate-50/30">
                            <td class="border border-slate-200 px-2 py-1 text-center bg-slate-50/40 font-semibold text-slate-500">${idx + 1}</td>
                            <td class="border border-slate-200 px-3 py-1 font-medium text-slate-800">${name}</td>
                            <td class="border border-slate-200 px-2 py-1 text-center">
                                <input type="number" min="1" class="purchase-item-qty w-full rounded border border-slate-200 px-2 py-1 text-center font-medium focus:border-indigo-500 focus:outline-none" data-idx="${idx}" value="${qty}">
                            </td>
                            <td class="border border-slate-200 px-2 py-1 text-right">
                                <input type="number" min="0" class="purchase-item-price w-full rounded border border-slate-200 px-2 py-1 text-right font-mono focus:border-indigo-500 focus:outline-none" data-idx="${idx}" value="${price}">
                            </td>
                            <td class="border border-slate-200 px-3 py-1 text-right text-slate-800 font-bold font-mono bg-slate-50/20">${formatCurrencyWithoutSymbol(amt)}</td>
                            <td class="border border-slate-200 px-2 py-1 text-center">
                                <input type="date" class="purchase-item-date w-full rounded border border-slate-200 px-2 py-1 text-center focus:border-indigo-500 focus:outline-none" data-idx="${idx}" value="${expectedDate}">
                            </td>
                            <td class="border border-slate-200 px-2 py-1">
                                <input type="text" class="purchase-item-note w-full rounded border border-slate-200 px-2 py-1 focus:border-indigo-500 focus:outline-none" data-idx="${idx}" value="${note}">
                            </td>
                            <td class="border border-slate-200 px-2 py-1 text-center">
                                <button type="button" class="delete-purchase-item-row text-rose-500 hover:text-rose-700 transition" data-idx="${idx}">
                                    <i data-lucide="trash-2" class="h-4 w-4 mx-auto"></i>
                                </button>
                            </td>
                        </tr>
                    `;
                });
            }

            html += `
                    </tbody>
                </table>
            `;

            container.innerHTML = html;

            if (window.lucide) {
                window.lucide.createIcons({
                    attrs: {
                        class: ["lucide"]
                    },
                    nameAttr: "data-lucide",
                    node: container
                });
            }

            container.querySelectorAll(".purchase-item-qty").forEach(input => {
                input.addEventListener("change", e => {
                    const idx = Number(e.target.dataset.idx);
                    const val = Number(e.target.value) || 1;
                    updateItemInJson(idx, "quantity", val);
                });
            });

            container.querySelectorAll(".purchase-item-price").forEach(input => {
                input.addEventListener("change", e => {
                    const idx = Number(e.target.dataset.idx);
                    const val = Number(e.target.value) || 0;
                    updateItemInJson(idx, "unitPrice", val);
                });
            });

            container.querySelectorAll(".purchase-item-date").forEach(input => {
                input.addEventListener("change", e => {
                    const idx = Number(e.target.dataset.idx);
                    const val = e.target.value;
                    updateItemInJson(idx, "expectedDate", val);
                });
            });

            container.querySelectorAll(".purchase-item-note").forEach(input => {
                input.addEventListener("change", e => {
                    const idx = Number(e.target.dataset.idx);
                    const val = e.target.value;
                    updateItemInJson(idx, "note", val);
                });
            });

            container.querySelectorAll(".delete-purchase-item-row").forEach(btn => {
                btn.addEventListener("click", e => {
                    const button = e.target.closest("button");
                    const idx = Number(button.dataset.idx);
                    deleteItemFromJson(idx);
                });
            });

            if (selectedDocument) {
                const disabled = selectedDocument.reviewStatus !== "APPROVED" || selectedDocument.linkStatus === "LINKED";
                container.querySelectorAll("input, button").forEach(function (el) {
                    el.disabled = disabled;
                });
            }
        }

        function updateItemInJson(idx, field, value) {
            let items = [];
            try {
                items = JSON.parse(purchaseItemsJson.value || "[]");
            } catch (e) {
                items = [];
            }
            if (items[idx]) {
                items[idx][field] = value;
                purchaseItemsJson.value = JSON.stringify(items, null, 2);
                renderPurchaseItemsTable();
            }
        }

        function deleteItemFromJson(idx) {
            let items = [];
            try {
                items = JSON.parse(purchaseItemsJson.value || "[]");
            } catch (e) {
                items = [];
            }
            items.splice(idx, 1);
            purchaseItemsJson.value = JSON.stringify(items, null, 2);
            renderPurchaseItemsTable();
        }

        function prefillPurchaseLinkForm(detail) {
            vendorSearchKeyword.value = detail.extractedVendor || "";
            purchaseExpectedDate.value = detail.extractedDate || "";
            purchaseNote.value = detail.linkStatus === "LINKED"
                ? `OCR 문서 ${detail.id}에 이미 연결된 발주 메모야.`
                : `OCR 문서 ${detail.id} (${detail.originalFilename}) 기반 발주`;
            purchaseItemsJson.value = JSON.stringify(buildPurchaseItemDraft(detail), null, 2);
            vendorSearchResults.innerHTML = "";
            itemSearchResults.innerHTML = "";
            renderPurchaseItemsTable();
        }

        function prefillExpenseLinkForm(detail) {
            const draft = buildExpenseDraft(detail);
            expenseEmployeeId.value = draft.employeeId;
            expenseDate.value = draft.expenseDate;
            expenseCategory.value = draft.category;
            expenseAmount.value = draft.amount;
            expenseDescription.value = draft.description;
            expenseStatus.value = draft.status;
        }

        function prefillVoucherLinkForm(detail) {
            const draft = buildVoucherDraft(detail);
            voucherDate.value = draft.voucherDate;
            voucherType.value = draft.voucherType;
            voucherVatType.value = draft.vatType;
            voucherVendorId.value = draft.vendorId;
            voucherVendorName.value = draft.vendorNameSnapshot;
            voucherDescription.value = draft.description;
            voucherSupplyAmount.value = draft.supplyAmount;
            voucherVatAmount.value = draft.vatAmount;
            voucherFeeAmount.value = draft.feeAmount;
            voucherBusinessAccountId.value = draft.businessAccountId;
            voucherSettlementAccountId.value = draft.settlementAccountId;
            voucherBusinessAccountKeyword.value = "";
            voucherSettlementAccountKeyword.value = "";
            businessAccountResults.innerHTML = "";
            settlementAccountResults.innerHTML = "";
        }

        function syncReviewControls(detail) {
            const disabled = !detail || detail.processingStatus !== "PARSED" || detail.linkStatus === "LINKED";
            approveButton.disabled = disabled;
            rejectButton.disabled = disabled;
            [reviewVendorName, reviewTransactionDate, reviewTotalAmount, reviewCurrency, reviewNotes]
                .forEach(function (element) {
                    element.disabled = disabled;
                });
        }

        function syncRetryControls(detail) {
            retryButton.disabled = !detail || detail.processingStatus !== "FAILED" || detail.linkStatus === "LINKED";
        }

        function syncDeleteControls(detail) {
            if (deleteButton) {
                deleteButton.disabled = !detail || detail.linkStatus === "LINKED";
            }
        }

        function syncUnlinkControls(detail) {
            unlinkButton.disabled = !detail || detail.linkStatus !== "LINKED";
        }

        function syncPurchaseLinkControls(detail) {
            const disabled = !detail || detail.reviewStatus !== "APPROVED" || detail.linkStatus === "LINKED";
            [
                purchaseVendorId, purchaseExpectedDate, purchaseNote, purchaseItemsJson,
                linkPurchaseOrderButton, vendorSearchKeyword, searchVendorButton, itemSearchKeyword, searchItemButton
            ].forEach(function (element) {
                element.disabled = disabled;
            });

            const tableContainer = document.getElementById("purchaseItemsTableContainer");
            if (tableContainer) {
                tableContainer.querySelectorAll("input, button").forEach(function (el) {
                    el.disabled = disabled;
                });
            }
        }

        function syncExpenseLinkControls(detail) {
            const disabled = !detail || detail.reviewStatus !== "APPROVED" || detail.linkStatus === "LINKED";
            [expenseEmployeeId, expenseDate, expenseCategory, expenseAmount, expenseDescription, expenseStatus, linkExpenseButton]
                .forEach(function (element) {
                    element.disabled = disabled;
                });
        }

        function syncVoucherLinkControls(detail) {
            const disabled = !detail || detail.reviewStatus !== "APPROVED" || detail.linkStatus === "LINKED";
            [
                voucherDate, voucherType, voucherVatType, voucherVendorId, voucherVendorName, voucherDescription,
                voucherSupplyAmount, voucherVatAmount, voucherFeeAmount, voucherBusinessAccountId, voucherSettlementAccountId,
                voucherBusinessAccountKeyword, voucherSettlementAccountKeyword, searchBusinessAccountBtn, searchSettlementAccountBtn,
                linkVoucherButton
            ].forEach(function (element) {
                element.disabled = disabled;
            });
        }

        async function searchVendors() {
            const keyword = vendorSearchKeyword.value.trim();
            if (!keyword) {
                vendorSearchResults.innerHTML = `<div class="text-xs text-slate-400">거래처 검색어를 입력해.</div>`;
                return;
            }

            vendorSearchResults.innerHTML = `<div class="text-xs text-slate-400">거래처를 찾는 중이야.</div>`;
            try {
                const vendors = await requestJsonList(`/api/v1/inventory/vendors/search?keyword=${encodeURIComponent(keyword)}`, "거래처 검색에 실패했어.", {
                    headers: { "Content-Type": "application/json" }
                });
                renderVendorSearchResults(vendors || []);
            } catch (error) {
                vendorSearchResults.innerHTML = `<div class="text-xs text-rose-500">${error.message}</div>`;
            }
        }

        function renderVendorSearchResults(vendors) {
            if (!vendors.length) {
                vendorSearchResults.innerHTML = `<div class="text-xs text-slate-400">검색 결과가 없어.</div>`;
                return;
            }

            vendorSearchResults.innerHTML = vendors.slice(0, 8).map(function (vendor) {
                return `
                    <button type="button" class="vendor-search-result flex w-full items-start justify-between rounded-2xl border border-slate-200 bg-white px-4 py-3 text-left transition hover:border-sky-300 hover:bg-sky-50" data-vendor-id="${vendor.id}" data-vendor-name="${vendor.name}">
                        <span>
                            <span class="block font-semibold text-slate-900">${vendor.name}</span>
                            <span class="mt-1 block text-xs text-slate-500">${vendor.vendorCode || "-"} / ${vendor.representativeName || "-"}</span>
                        </span>
                        <span class="text-xs font-semibold text-sky-600">ID ${vendor.id}</span>
                    </button>
                `;
            }).join("");

            vendorSearchResults.querySelectorAll(".vendor-search-result").forEach(function (button) {
                button.addEventListener("click", function () {
                    purchaseVendorId.value = button.dataset.vendorId || "";
                    voucherVendorId.value = button.dataset.vendorId || "";
                    voucherVendorName.value = button.dataset.vendorName || "";
                    vendorSearchKeyword.value = button.dataset.vendorName || "";
                    setMessage(message, `거래처 ${button.dataset.vendorName}을 선택했어.`, "success");
                });
            });
        }

        async function searchItems() {
            const keyword = itemSearchKeyword.value.trim();
            if (!keyword) {
                itemSearchResults.innerHTML = `<div class="text-xs text-slate-400">품목 검색어를 입력해.</div>`;
                return;
            }

            itemSearchResults.innerHTML = `<div class="text-xs text-slate-400">품목을 찾는 중이야.</div>`;
            try {
                const items = await requestJsonList(`/api/v1/inventory/items/search?name=${encodeURIComponent(keyword)}`, "품목 검색에 실패했어.", {
                    headers: { "Content-Type": "application/json" }
                });
                renderItemSearchResults(items || []);
            } catch (error) {
                itemSearchResults.innerHTML = `<div class="text-xs text-rose-500">${error.message}</div>`;
            }
        }

        function renderItemSearchResults(items) {
            if (!items.length) {
                itemSearchResults.innerHTML = `<div class="text-xs text-slate-400">검색 결과가 없어.</div>`;
                return;
            }

            itemSearchResults.innerHTML = items.slice(0, 10).map(function (item) {
                return `
                    <button type="button" class="item-search-result flex w-full items-start justify-between rounded-2xl border border-slate-200 bg-white px-4 py-3 text-left transition hover:border-sky-300 hover:bg-sky-50"
                        data-item-id="${item.id}"
                        data-item-name="${item.name || ""}"
                        data-unit-price="${item.unitPrice || 0}">
                        <span>
                            <span class="block font-semibold text-slate-900">${item.name}</span>
                            <span class="mt-1 block text-xs text-slate-500">${item.category || "-"} / ${item.spec || "-"} / ${item.unit || "-"}</span>
                        </span>
                        <span class="text-xs font-semibold text-sky-600">ID ${item.id}</span>
                    </button>
                `;
            }).join("");

            itemSearchResults.querySelectorAll(".item-search-result").forEach(function (button) {
                button.addEventListener("click", function () {
                    let itemsDraft;
                    try {
                        itemsDraft = JSON.parse(purchaseItemsJson.value || "[]");
                        if (!Array.isArray(itemsDraft)) {
                            itemsDraft = [];
                        }
                    } catch (error) {
                        itemsDraft = [];
                    }
                    itemsDraft.push({
                        itemId: Number(button.dataset.itemId),
                        itemName: button.dataset.itemName || "",
                        quantity: 1,
                        unitPrice: button.dataset.unitPrice ? Number(button.dataset.unitPrice) : 0,
                        expectedDate: purchaseExpectedDate.value || null,
                        note: "OCR 문서 기반 추가"
                    });
                    purchaseItemsJson.value = JSON.stringify(itemsDraft, null, 2);
                    itemSearchKeyword.value = button.dataset.itemName || "";
                    setMessage(message, `품목 ${button.dataset.itemName}을 발주 항목에 추가했어.`, "success");
                    renderPurchaseItemsTable();
                });
            });
        }

        function validateReviewForm(detail) {
            if (!detail) {
                return "먼저 OCR 문서를 선택해.";
            }

            if (reviewTransactionDate.value && Number.isNaN(new Date(reviewTransactionDate.value).getTime())) {
                return "거래 일자 형식이 올바르지 않아.";
            }

            if (reviewTotalAmount.value.trim() !== "" && Number.isNaN(Number(reviewTotalAmount.value.trim()))) {
                return "총 금액은 숫자로 입력해.";
            }

            if (!reviewCurrency.value.trim()) {
                return "통화 값을 입력해.";
            }

            return null;
        }

        async function searchAccounts(keyword, type, cashOnly, resultContainer, onSelect) {
            if (!keyword.trim()) {
                resultContainer.innerHTML = `<div class="text-xs text-slate-400">계정 검색어를 입력해.</div>`;
                return;
            }

            resultContainer.innerHTML = `<div class="text-xs text-slate-400">계정을 찾는 중이야.</div>`;
            try {
                const params = new URLSearchParams();
                params.set("keyword", keyword.trim());
                if (type) {
                    params.set("type", type);
                }
                params.set("cashOnly", cashOnly ? "true" : "false");

                const accounts = await requestApi(`/api/v1/accounting/vouchers/accounts/search?${params.toString()}`, "계정 검색에 실패했어.", {
                    headers: { "Content-Type": "application/json" }
                });
                if (!accounts.length) {
                    resultContainer.innerHTML = `<div class="text-xs text-slate-400">검색 결과가 없어.</div>`;
                    return;
                }
                resultContainer.innerHTML = accounts.slice(0, 10).map(function (account) {
                    return `
                        <button type="button" class="account-search-result flex w-full items-start justify-between rounded-2xl border border-slate-200 bg-white px-4 py-3 text-left transition hover:border-sky-300 hover:bg-sky-50"
                            data-account-id="${account.id}"
                            data-account-code="${account.code}"
                            data-account-name="${account.name}">
                            <span>
                                <span class="block font-semibold text-slate-900">${account.name}</span>
                                <span class="mt-1 block text-xs text-slate-500">${account.code} / ${account.type}</span>
                            </span>
                            <span class="text-xs font-semibold text-sky-600">ID ${account.id}</span>
                        </button>
                    `;
                }).join("");
                resultContainer.querySelectorAll(".account-search-result").forEach(function (button) {
                    button.addEventListener("click", function () {
                        onSelect(button.dataset);
                    });
                });
            } catch (error) {
                resultContainer.innerHTML = `<div class="text-xs text-rose-500">${error.message}</div>`;
            }
        }

        function updateVoucherAccountSelections() {
            searchBusinessAccountBtn.addEventListener("click", function () {
                const type = voucherType.value === "SALES" ? "REVENUE" : "EXPENSE";
                searchAccounts(voucherBusinessAccountKeyword.value, type, false, businessAccountResults, function (dataset) {
                    voucherBusinessAccountId.value = dataset.accountId || "";
                    voucherBusinessAccountKeyword.value = `${dataset.accountCode} ${dataset.accountName}`.trim();
                    setMessage(message, `사업 계정 ${dataset.accountName}을 선택했어.`, "success");
                });
            });
            searchSettlementAccountBtn.addEventListener("click", function () {
                searchAccounts(voucherSettlementAccountKeyword.value, "ASSET", true, settlementAccountResults, function (dataset) {
                    voucherSettlementAccountId.value = dataset.accountId || "";
                    voucherSettlementAccountKeyword.value = `${dataset.accountCode} ${dataset.accountName}`.trim();
                    setMessage(message, `결제 계정 ${dataset.accountName}을 선택했어.`, "success");
                });
            });
        }

        async function updateReview(reviewStatus) {
            if (!selectedDocument) {
                setMessage(message, "먼저 OCR 문서를 선택해.", "error");
                return;
            }

            const validationMessage = validateReviewForm(selectedDocument);
            if (validationMessage) {
                setMessage(message, validationMessage, "error");
                return;
            }

            try {
                const reviewedPayload = JSON.stringify(buildReviewPayload(selectedDocument), null, 2);
                reviewedResultInput.value = reviewedPayload;
                const response = await fetch(`${getApiBaseUrl()}/api/v1/admin/ocr-documents/${selectedDocument.id}/review`, {
                    method: "PATCH",
                    headers: getAuthHeaders({ "Content-Type": "application/json" }),
                    body: JSON.stringify({
                        reviewStatus: reviewStatus,
                        reviewedResult: reviewedPayload
                    })
                });
                const detail = await parseApiResponse(response, "OCR 문서 검토 저장에 실패했어.");
                setMessage(message, `OCR 문서 ${detail.id} 검토 상태를 ${translateReviewStatus(detail.reviewStatus)}로 저장했어.`, "success");
                await openDocumentDetail(detail.id);
                await loadDocuments();
            } catch (error) {
                setMessage(message, error.message, "error");
            }
        }

        async function retryDocument() {
            if (!selectedDocument) {
                setMessage(message, "먼저 OCR 문서를 선택해.", "error");
                return;
            }
            try {
                const response = await fetch(`${getApiBaseUrl()}/api/v1/admin/ocr-documents/${selectedDocument.id}/retry`, {
                    method: "POST",
                    headers: getAuthHeaders({ "Content-Type": "application/json" })
                });
                const detail = await parseApiResponse(response, "OCR 재처리 요청에 실패했어.");
                setMessage(message, `OCR 문서 ${detail.id} 재처리를 요청했어.`, "success");
                await openDocumentDetail(detail.id);
                await loadDocuments();
            } catch (error) {
                setMessage(message, error.message, "error");
            }
        }

        async function deleteDocument() {
            if (!selectedDocument) {
                setMessage(message, "먼저 OCR 문서를 선택해.", "error");
                return;
            }

            if (!confirm(`OCR 문서 ${selectedDocument.id} (${selectedDocument.originalFilename})를 정말 삭제할까?`)) {
                return;
            }

            try {
                const response = await fetch(`${getApiBaseUrl()}/api/v1/admin/ocr-documents/${selectedDocument.id}`, {
                    method: "DELETE",
                    headers: getAuthHeaders({ "Content-Type": "application/json" })
                });

                const text = await response.text();
                let data = null;
                if (text) {
                    try {
                        data = JSON.parse(text);
                    } catch (e) {}
                }

                if (!response.ok) {
                    throw new Error(data && data.message ? data.message : "OCR 문서 삭제에 실패했어.");
                }

                setMessage(message, data && data.message ? data.message : "OCR 문서를 삭제했어.", "success");
                selectedDocument = null;
                detailSummary.innerHTML = "문서를 선택하면 상세 정보가 여기에 표시돼.";
                rawResult.textContent = "아직 선택한 문서가 없어.";
                previewImage.classList.add("hidden");
                previewImage.removeAttribute("src");
                previewHint.classList.remove("hidden");
                previewFallback.classList.add("hidden");
                
                if (deleteButton) deleteButton.disabled = true;
                if (unlinkButton) unlinkButton.disabled = true;
                if (retryButton) retryButton.disabled = true;

                await loadDocuments();
            } catch (error) {
                setMessage(message, error.message, "error");
            }
        }

        async function unlinkDocument() {
            if (!selectedDocument) {
                setMessage(message, "먼저 OCR 문서를 선택해.", "error");
                return;
            }

            try {
                const response = await fetch(`${getApiBaseUrl()}/api/v1/admin/ocr-documents/${selectedDocument.id}/link`, {
                    method: "DELETE",
                    headers: getAuthHeaders({ "Content-Type": "application/json" })
                });
                const detail = await parseApiResponse(response, "OCR 연결 해제에 실패했어.");
                setMessage(message, `OCR 문서 ${detail.id} 연결을 해제했어.`, "success");
                await openDocumentDetail(detail.id);
                await loadDocuments();
            } catch (error) {
                setMessage(message, error.message, "error");
            }
        }

        async function unlinkSelectedDocument() {
            if (!selectedDocument) {
                setMessage(message, "먼저 OCR 문서를 선택해.", "error");
                return;
            }

            try {
                const response = await fetch(`${getApiBaseUrl()}/api/v1/admin/ocr-documents/${selectedDocument.id}/link`, {
                    method: "DELETE",
                    headers: getAuthHeaders({ "Content-Type": "application/json" })
                });
                const detail = await parseApiResponse(response, "OCR 연결 해제에 실패했어.");
                setMessage(message, `OCR 문서 ${detail.id} 연결을 해제했어.`, "success");
                await openDocumentDetail(detail.id);
                await loadDocuments();
            } catch (error) {
                setMessage(message, error.message, "error");
            }
        }

        async function linkPurchaseOrder() {
            if (!selectedDocument) {
                setMessage(message, "먼저 OCR 문서를 선택해.", "error");
                return;
            }

            let items;
            try {
                items = JSON.parse(purchaseItemsJson.value);
            } catch (error) {
                setMessage(message, "발주 항목 JSON 형식이 올바르지 않아.", "error");
                return;
            }

            try {
                const response = await fetch(`${getApiBaseUrl()}/api/v1/admin/ocr-documents/${selectedDocument.id}/link/purchase-orders`, {
                    method: "POST",
                    headers: getAuthHeaders({ "Content-Type": "application/json" }),
                    body: JSON.stringify({
                        vendorId: purchaseVendorId.value ? Number(purchaseVendorId.value) : null,
                        expectedDate: purchaseExpectedDate.value || null,
                        note: purchaseNote.value || null,
                        items: Array.isArray(items) ? items.map(function (item) {
                            return {
                                itemId: item.itemId === null || item.itemId === undefined || item.itemId === "" ? null : Number(item.itemId),
                                itemName: item.itemName || null,
                                quantity: item.quantity === null || item.quantity === undefined || item.quantity === "" ? null : Number(item.quantity),
                                unitPrice: item.unitPrice,
                                expectedDate: item.expectedDate || purchaseExpectedDate.value || null,
                                note: item.note || null
                            };
                        }) : []
                    })
                });
                const purchaseOrder = await parseApiResponse(response, "구매 발주 연결에 실패했어.");
                setMessage(message, `구매 발주 ${purchaseOrder.purchaseOrderNo}로 OCR 문서를 연결했어.`, "success");
                await openDocumentDetail(selectedDocument.id);
                await loadDocuments();
            } catch (error) {
                setMessage(message, error.message, "error");
            }
        }

        async function linkExpense() {
            if (!selectedDocument) {
                setMessage(message, "먼저 OCR 문서를 선택해.", "error");
                return;
            }
            try {
                const response = await fetch(`${getApiBaseUrl()}/api/v1/admin/ocr-documents/${selectedDocument.id}/link/expenses`, {
                    method: "POST",
                    headers: getAuthHeaders({ "Content-Type": "application/json" }),
                    body: JSON.stringify({
                        employeeId: expenseEmployeeId.value ? Number(expenseEmployeeId.value) : null,
                        expenseDate: expenseDate.value || null,
                        category: expenseCategory.value || null,
                        amount: expenseAmount.value ? Number(expenseAmount.value) : null,
                        description: expenseDescription.value || null,
                        status: expenseStatus.value || null
                    })
                });
                const expense = await parseApiResponse(response, "비용 연결에 실패했어.");
                setMessage(message, `비용 ${expense.id}로 OCR 문서를 연결했어.`, "success");
                await openDocumentDetail(selectedDocument.id);
                await loadDocuments();
            } catch (error) {
                setMessage(message, error.message, "error");
            }
        }

        async function linkVoucher() {
            if (!selectedDocument) {
                setMessage(message, "먼저 OCR 문서를 선택해.", "error");
                return;
            }
            try {
                const response = await fetch(`${getApiBaseUrl()}/api/v1/admin/ocr-documents/${selectedDocument.id}/link/vouchers`, {
                    method: "POST",
                    headers: getAuthHeaders({ "Content-Type": "application/json" }),
                    body: JSON.stringify({
                        voucherDate: voucherDate.value || null,
                        voucherType: voucherType.value || null,
                        vatType: voucherVatType.value || null,
                        vendorId: voucherVendorId.value ? Number(voucherVendorId.value) : null,
                        vendorNameSnapshot: voucherVendorName.value || null,
                        description: voucherDescription.value || null,
                        supplyAmount: voucherSupplyAmount.value ? Number(voucherSupplyAmount.value) : null,
                        vatAmount: voucherVatAmount.value ? Number(voucherVatAmount.value) : null,
                        feeAmount: voucherFeeAmount.value ? Number(voucherFeeAmount.value) : null,
                        businessAccountId: voucherBusinessAccountId.value ? Number(voucherBusinessAccountId.value) : null,
                        settlementAccountId: voucherSettlementAccountId.value ? Number(voucherSettlementAccountId.value) : null
                    })
                });
                const voucher = await parseApiResponse(response, "전표 연결에 실패했어.");
                setMessage(message, `전표 ${voucher.voucherNo}로 OCR 문서를 연결했어.`, "success");
                await openDocumentDetail(selectedDocument.id);
                await loadDocuments();
            } catch (error) {
                setMessage(message, error.message, "error");
            }
        }

        async function loadPreview(fileEndpoint, mimeType) {
            previewImage.classList.add("hidden");
            previewFallback.classList.add("hidden");
            previewHint.classList.add("hidden");
            if (previewImage.src && typeof previewImage.src === "string" && previewImage.src.startsWith("blob:")) {
                URL.revokeObjectURL(previewImage.src);
            }
            previewImage.removeAttribute("src");

            try {
                const response = await fetch(`${getApiBaseUrl()}${fileEndpoint}`, {
                    headers: getAuthHeaders()
                });
                if (!response.ok) {
                    throw new Error();
                }
                const blob = await response.blob();
                if (mimeType && mimeType.startsWith("image/")) {
                    const objectUrl = URL.createObjectURL(blob);
                    previewImage.src = objectUrl;
                    previewImage.classList.remove("hidden");
                } else {
                    previewFallback.classList.remove("hidden");
                }
            } catch (error) {
                previewFallback.classList.remove("hidden");
            }
        }

        async function legacyUpdateReview(reviewStatus) {
            if (!selectedDocument) {
                setMessage(message, "먼저 OCR 문서를 선택해.", "error");
                return;
            }

            const validationMessage = validateReviewForm(selectedDocument);
            if (validationMessage) {
                setMessage(message, validationMessage, "error");
                return;
            }

            try {
                const reviewedPayload = JSON.stringify(buildReviewPayload(selectedDocument), null, 2);
                reviewedResultInput.value = reviewedPayload;
                const detail = await requestApi(`/api/v1/admin/ocr-documents/${selectedDocument.id}/review`, "OCR 문서 검토 저장에 실패했어.", {
                    method: "PATCH",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        reviewStatus,
                        reviewedResult: reviewedPayload
                    })
                });
                setMessage(message, `OCR 문서 ${detail.id} 검토 상태를 ${translateReviewStatus(detail.reviewStatus)}로 저장했어.`, "success");
                await openDocumentDetail(detail.id);
                await loadDocuments();
            } catch (error) {
                setMessage(message, error.message, "error");
            }
        }

        async function legacyRetryDocument() {
            if (!selectedDocument) {
                setMessage(message, "먼저 OCR 문서를 선택해.", "error");
                return;
            }
            try {
                const detail = await requestApi(`/api/v1/admin/ocr-documents/${selectedDocument.id}/retry`, "OCR 재처리 요청에 실패했어.", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" }
                });
                setMessage(message, `OCR 문서 ${detail.id} 재처리를 요청했어.`, "success");
                await openDocumentDetail(detail.id);
                await loadDocuments();
            } catch (error) {
                setMessage(message, error.message, "error");
            }
        }

        async function legacyUnlinkDocument() {
            if (!selectedDocument) {
                setMessage(message, "먼저 OCR 문서를 선택해.", "error");
                return;
            }

            try {
                const detail = await requestApi(`/api/v1/admin/ocr-documents/${selectedDocument.id}/link`, "OCR 연결 해제에 실패했어.", {
                    method: "DELETE",
                    headers: { "Content-Type": "application/json" }
                });
                setMessage(message, `OCR 문서 ${detail.id} 연결을 해제했어.`, "success");
                await openDocumentDetail(detail.id);
                await loadDocuments();
            } catch (error) {
                setMessage(message, error.message, "error");
            }
        }

        async function legacyUnlinkSelectedDocument() {
            if (!selectedDocument) {
                setMessage(message, "먼저 OCR 문서를 선택해.", "error");
                return;
            }

            try {
                const detail = await requestApi(`/api/v1/admin/ocr-documents/${selectedDocument.id}/link`, "OCR 연결 해제에 실패했어.", {
                    method: "DELETE",
                    headers: { "Content-Type": "application/json" }
                });
                setMessage(message, `OCR 문서 ${detail.id} 연결을 해제했어.`, "success");
                await openDocumentDetail(detail.id);
                await loadDocuments();
            } catch (error) {
                setMessage(message, error.message, "error");
            }
        }

        async function legacyLinkPurchaseOrder() {
            if (!selectedDocument) {
                setMessage(message, "먼저 OCR 문서를 선택해.", "error");
                return;
            }

            let items;
            try {
                items = JSON.parse(purchaseItemsJson.value);
            } catch (error) {
                setMessage(message, "발주 항목 JSON 형식이 올바르지 않아.", "error");
                return;
            }

            try {
                const purchaseOrder = await requestApi(`/api/v1/admin/ocr-documents/${selectedDocument.id}/link/purchase-orders`, "구매 발주 연결에 실패했어.", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        vendorId: purchaseVendorId.value ? Number(purchaseVendorId.value) : null,
                        expectedDate: purchaseExpectedDate.value || null,
                        note: purchaseNote.value || null,
                        items: Array.isArray(items) ? items.map(function (item) {
                            return {
                                itemId: item.itemId === null || item.itemId === undefined || item.itemId === "" ? null : Number(item.itemId),
                                itemName: item.itemName || null,
                                quantity: item.quantity === null || item.quantity === undefined || item.quantity === "" ? null : Number(item.quantity),
                                unitPrice: item.unitPrice,
                                expectedDate: item.expectedDate || purchaseExpectedDate.value || null,
                                note: item.note || null
                            };
                        }) : []
                    })
                });
                setMessage(message, `구매 발주 ${purchaseOrder.purchaseOrderNo}로 OCR 문서를 연결했어.`, "success");
                await openDocumentDetail(selectedDocument.id);
                await loadDocuments();
            } catch (error) {
                setMessage(message, error.message, "error");
            }
        }

        async function legacyLinkExpense() {
            if (!selectedDocument) {
                setMessage(message, "먼저 OCR 문서를 선택해.", "error");
                return;
            }
            try {
                const expense = await requestApi(`/api/v1/admin/ocr-documents/${selectedDocument.id}/link/expenses`, "비용 연결에 실패했어.", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        employeeId: expenseEmployeeId.value ? Number(expenseEmployeeId.value) : null,
                        expenseDate: expenseDate.value || null,
                        category: expenseCategory.value || null,
                        amount: expenseAmount.value ? Number(expenseAmount.value) : null,
                        description: expenseDescription.value || null,
                        status: expenseStatus.value || null
                    })
                });
                setMessage(message, `비용 ${expense.id}로 OCR 문서를 연결했어.`, "success");
                await openDocumentDetail(selectedDocument.id);
                await loadDocuments();
            } catch (error) {
                setMessage(message, error.message, "error");
            }
        }

        async function legacyLinkVoucher() {
            if (!selectedDocument) {
                setMessage(message, "먼저 OCR 문서를 선택해.", "error");
                return;
            }
            try {
                const voucher = await requestApi(`/api/v1/admin/ocr-documents/${selectedDocument.id}/link/vouchers`, "전표 연결에 실패했어.", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        voucherDate: voucherDate.value || null,
                        voucherType: voucherType.value || null,
                        vatType: voucherVatType.value || null,
                        vendorId: voucherVendorId.value ? Number(voucherVendorId.value) : null,
                        vendorNameSnapshot: voucherVendorName.value || null,
                        description: voucherDescription.value || null,
                        supplyAmount: voucherSupplyAmount.value ? Number(voucherSupplyAmount.value) : null,
                        vatAmount: voucherVatAmount.value ? Number(voucherVatAmount.value) : null,
                        feeAmount: voucherFeeAmount.value ? Number(voucherFeeAmount.value) : null,
                        businessAccountId: voucherBusinessAccountId.value ? Number(voucherBusinessAccountId.value) : null,
                        settlementAccountId: voucherSettlementAccountId.value ? Number(voucherSettlementAccountId.value) : null
                    })
                });
                setMessage(message, `전표 ${voucher.voucherNo}로 OCR 문서를 연결했어.`, "success");
                await openDocumentDetail(selectedDocument.id);
                await loadDocuments();
            } catch (error) {
                setMessage(message, error.message, "error");
            }
        }

        refreshButton.addEventListener("click", function () {
            setMessage(message, "", "");
            loadDocuments();
        });
        [statusFilter, reviewFilter, linkFilter, typeFilter].forEach(function (filter) {
            filter.addEventListener("change", function () {
                setMessage(message, "", "");
                loadDocuments();
            });
        });
        keywordInput.addEventListener("keydown", function (event) {
            if (event.key === "Enter") {
                loadDocuments();
            }
        });
        vendorSearchKeyword.addEventListener("keydown", function (event) {
            if (event.key === "Enter") {
                searchVendors();
            }
        });
        itemSearchKeyword.addEventListener("keydown", function (event) {
            if (event.key === "Enter") {
                searchItems();
            }
        });
        voucherBusinessAccountKeyword.addEventListener("keydown", function (event) {
            if (event.key === "Enter") {
                searchBusinessAccountBtn.click();
            }
        });
        voucherSettlementAccountKeyword.addEventListener("keydown", function (event) {
            if (event.key === "Enter") {
                searchSettlementAccountBtn.click();
            }
        });
        [reviewVendorName, reviewTransactionDate, reviewTotalAmount, reviewCurrency, reviewNotes].forEach(function (element) {
            element.addEventListener("input", function () {
                syncReviewJsonPreview(selectedDocument);
            });
        });

        searchVendorButton.addEventListener("click", searchVendors);
        searchItemButton.addEventListener("click", searchItems);
        updateVoucherAccountSelections();

        approveButton.addEventListener("click", function () {
            updateReview("APPROVED");
        });
        rejectButton.addEventListener("click", function () {
            updateReview("REJECTED");
        });
        unlinkButton.addEventListener("click", unlinkSelectedDocument);
        retryButton.addEventListener("click", retryDocument);
        if (deleteButton) {
            deleteButton.addEventListener("click", deleteDocument);
        }
        linkPurchaseOrderButton.addEventListener("click", linkPurchaseOrder);
        linkExpenseButton.addEventListener("click", linkExpense);
        linkVoucherButton.addEventListener("click", linkVoucher);

        loadDocuments();
    }

    initDocumentBoxPage();
})();
