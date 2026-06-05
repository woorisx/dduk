(function () {
    function getApiBaseUrl() {
        if (window.ddukSession && typeof window.ddukSession.getApiBaseUrl === "function") {
            return window.ddukSession.getApiBaseUrl();
        }
        return "";
    }

    function getAuthHeaders(extraHeaders) {
        if (window.ddukSession && typeof window.ddukSession.getAuthHeaders === "function") {
            return window.ddukSession.getAuthHeaders(extraHeaders || {});
        }
        return extraHeaders || {};
    }

    async function requestApi(path, fallbackMessage, options = {}) {
        if (window.ddukApi && typeof window.ddukApi.requestData === "function") {
            return window.ddukApi.requestData(path, {
                method: options.method || "GET",
                headers: options.headers || {},
                body: options.body,
                cache: options.cache
            });
        }

        const response = await fetch(`${getApiBaseUrl()}${path}`, {
            method: options.method || "GET",
            headers: getAuthHeaders(options.headers || {}),
            body: options.body,
            cache: options.cache
        });
        return parseApiResponse(response, fallbackMessage);
    }

    async function requestJsonList(path, fallbackMessage, options = {}) {
        if (window.ddukApi && typeof window.ddukApi.requestList === "function") {
            return window.ddukApi.requestList(path, {
                method: options.method || "GET",
                headers: options.headers || {},
                body: options.body,
                cache: options.cache
            });
        }

        const response = await fetch(`${getApiBaseUrl()}${path}`, {
            method: options.method || "GET",
            headers: getAuthHeaders(options.headers || {}),
            body: options.body,
            cache: options.cache
        });
        return parseJsonListResponse(response, fallbackMessage);
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

    function formatCurrency(value) {
        if (value === null || value === undefined || value === "") {
            return "-";
        }
        const numericValue = Number(value);
        if (Number.isNaN(numericValue)) {
            return value;
        }
        return new Intl.NumberFormat("ko-KR", {
            style: "currency",
            currency: "KRW",
            maximumFractionDigits: 0
        }).format(numericValue);
    }

    function formatCurrencyWithoutSymbol(value) {
        if (value === null || value === undefined || value === "") {
            return "-";
        }
        const numericValue = Number(value);
        if (Number.isNaN(numericValue)) {
            return value;
        }
        return new Intl.NumberFormat("ko-KR", {
            maximumFractionDigits: 0
        }).format(numericValue);
    }

    function setMessage(element, text, type) {
        if (!element) {
            return;
        }
        if (!text) {
            element.className = "hidden";
            element.textContent = "";
            return;
        }
        const palette = type === "error"
            ? "border-red-200 bg-red-50 text-red-700"
            : "border-emerald-200 bg-emerald-50 text-emerald-700";
        element.className = `mt-5 rounded-2xl border px-4 py-3 text-sm font-medium ${palette}`;
        element.textContent = text;
    }

    const documentTypeMap = {
        RECEIPT: "영수증",
        INVOICE: "세금계산서",
        PURCHASE_ORDER: "발주서",
        STATEMENT: "거래명세서"
    };

    const processingStatusMap = {
        UPLOADED: "업로드됨",
        PROCESSING: "분석 중",
        PARSED: "분석 완료",
        FAILED: "분석 실패"
    };

    const reviewStatusMap = {
        PENDING: "검토 대기",
        APPROVED: "확인 완료",
        REJECTED: "반려됨"
    };

    const linkStatusMap = {
        UNLINKED: "연결 대기",
        LINKED: "연결 완료",
        UNLINKED_BY_DELETE: "연결 해제"
    };

    function translateDocumentType(val) {
        return documentTypeMap[val] || val || "-";
    }

    function translateProcessingStatus(val) {
        return processingStatusMap[val] || val || "-";
    }

    function translateReviewStatus(val) {
        return reviewStatusMap[val] || val || "-";
    }

    function translateLinkStatus(val) {
        return linkStatusMap[val] || val || "-";
    }

    function translateLinkedDomainType(val) {
        if (!val) {
            return "";
        }
        switch (val) {
            case "PURCHASE_ORDER":
                return "구매 발주";
            case "EXPENSE":
                return "비용";
            case "VOUCHER":
                return "전표";
            default:
                return val;
        }
    }

    function badgeClass(value) {
        switch (value) {
            case "APPROVED":
                return "bg-emerald-100 text-emerald-700";
            case "REJECTED":
            case "FAILED":
                return "bg-rose-100 text-rose-700";
            case "PARSED":
                return "bg-sky-100 text-sky-700";
            case "LINKED":
                return "bg-indigo-100 text-indigo-700";
            default:
                return "bg-slate-200 text-slate-700";
        }
    }

    async function parseApiResponse(response, fallbackMessage) {
        const text = await response.text();
        let payload = null;

        if (text) {
            try {
                payload = JSON.parse(text);
            } catch (error) {
                throw new Error(fallbackMessage);
            }
        }

        if (!response.ok || !payload || payload.status !== "success") {
            throw new Error(payload && payload.message ? payload.message : fallbackMessage);
        }

        return payload.data;
    }

    async function parseJsonListResponse(response, fallbackMessage) {
        const text = await response.text();
        if (!response.ok) {
            throw new Error(fallbackMessage);
        }
        if (!text) {
            return [];
        }
        try {
            const payload = JSON.parse(text);
            return Array.isArray(payload) ? payload : [];
        } catch (error) {
            throw new Error(fallbackMessage);
        }
    }

    function safeParseJson(text) {
        if (!text || !text.trim()) {
            return null;
        }
        try {
            return JSON.parse(text);
        } catch (error) {
            return null;
        }
    }

    function buildPurchaseItemDraft(detail) {
        const payload = safeParseJson(detail.reviewedResult) || safeParseJson(detail.rawOcrResult);
        const items = payload && Array.isArray(payload.items) ? payload.items : [];
        const defaultExpectedDate = detail.extractedDate || "";

        return items.map(function (item) {
            return {
                itemId: null,
                itemName: item.name || item.itemName || "",
                quantity: item.quantity || 1,
                unitPrice: item.unitPrice || item.amount || 0,
                expectedDate: item.expectedDate || defaultExpectedDate || null,
                note: "OCR 초안"
            };
        });
    }

    function buildExpenseDraft(detail) {
        const payload = safeParseJson(detail.reviewedResult) || safeParseJson(detail.rawOcrResult) || {};
        return {
            employeeId: payload.employeeId || "",
            expenseDate: detail.extractedDate || payload.transactionDate || payload.expenseDate || "",
            category: payload.category || "",
            amount: detail.extractedAmount || payload.totalAmount || payload.amount || "",
            description: payload.description || payload.memo || `OCR 문서 ${detail.id} (${detail.originalFilename}) 비용 등록`,
            status: payload.status || "PENDING"
        };
    }

    function buildVoucherDraft(detail) {
        const payload = safeParseJson(detail.reviewedResult) || safeParseJson(detail.rawOcrResult) || {};
        const amount = detail.extractedAmount || payload.totalAmount || payload.amount || "";

        return {
            voucherDate: detail.extractedDate || payload.transactionDate || payload.voucherDate || "",
            voucherType: payload.voucherType || "PURCHASE",
            vatType: payload.vatType || "TAX_INVOICE",
            vendorId: payload.vendorId || "",
            vendorNameSnapshot: detail.extractedVendor || payload.vendorNameSnapshot || payload.vendorName || "",
            description: payload.description || payload.memo || `OCR 문서 ${detail.id} (${detail.originalFilename}) 전표 생성`,
            supplyAmount: payload.supplyAmount || amount || "",
            vatAmount: payload.vatAmount || "",
            feeAmount: payload.feeAmount || 0,
            businessAccountId: payload.businessAccountId || "",
            settlementAccountId: payload.settlementAccountId || ""
        };
    }

    function buildReviewDraft(detail) {
        const payload = safeParseJson(detail.reviewedResult) || safeParseJson(detail.rawOcrResult) || {};
        const resolvedAmount = detail.extractedAmount !== null && detail.extractedAmount !== undefined && detail.extractedAmount !== ""
            ? detail.extractedAmount
            : (payload.totalAmount !== undefined ? payload.totalAmount : payload.amount);

        return {
            documentType: payload.documentType || detail.documentType || "RECEIPT",
            vendorName: payload.vendorName || detail.extractedVendor || "",
            transactionDate: payload.transactionDate || detail.extractedDate || "",
            totalAmount: resolvedAmount ?? "",
            currency: payload.currency || "KRW",
            items: Array.isArray(payload.items) ? payload.items : [],
            confidence: payload.confidence ?? null,
            notes: payload.notes || ""
        };
    }

    function renderSearchResults(container, html) {
        if (!container) {
            return;
        }
        container.innerHTML = html;
    }

    function clampAmount(value) {
        if (value === null || value === undefined || value === "") {
            return "";
        }
        const numericValue = Number(value);
        if (Number.isNaN(numericValue)) {
            return value;
        }
        return Math.max(0, numericValue);
    }

    window.OcrDocumentsShared = {
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
        parseJsonListResponse,
        safeParseJson,
        buildPurchaseItemDraft,
        buildExpenseDraft,
        buildVoucherDraft,
        buildReviewDraft,
        renderSearchResults,
        clampAmount
    };
})();
