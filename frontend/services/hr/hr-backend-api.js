const API_BASE_URL = (() => {
    return window.ddukSession?.getApiBaseUrl?.() || window.ddukApi?.getBaseUrl?.() || '';
})();

async function request(path, options = {}) {
    const headers = window.ddukSession?.getAuthHeaders?.({ 'Content-Type': 'application/json' })
        || { 'Content-Type': 'application/json' };

    const response = await fetch(`${API_BASE_URL}${path}`, {
        method: options.method || 'GET',
        headers,
        body: options.body ? JSON.stringify(options.body) : undefined
    });

    const text = await response.text();
    let data = null;
    try {
        data = text ? JSON.parse(text) : null;
    } catch (error) {
        data = { message: text };
    }

    if (!response.ok) {
        throw new Error(data?.message || data?.error || `API 요청 실패 (${response.status})`);
    }

    return data;
}

function buildPeriodBody(value, field) {
    if (typeof value === 'string') {
        return { [field]: value || 'SYSTEM' };
    }
    return value || {};
}

function buildReportQuery(fiscalYear, fiscalMonth) {
    const params = new URLSearchParams();
    if (fiscalYear) params.set('fiscalYear', fiscalYear);
    if (fiscalMonth) params.set('fiscalMonth', fiscalMonth);
    const query = params.toString();
    return query ? `?${query}` : '';
}

export const hrBackendApi = {
    getBalanceSheet(fiscalYear, fiscalMonth) {
        return request(`/api/v1/accounting/reports/balance-sheet${buildReportQuery(fiscalYear, fiscalMonth)}`);
    },

    getProfitLoss(fiscalYear, fiscalMonth) {
        return request(`/api/v1/accounting/reports/profit-loss${buildReportQuery(fiscalYear, fiscalMonth)}`);
    },

    getTrialBalance(fiscalYear, fiscalMonth) {
        return request(`/api/v1/accounting/reports/trial-balance${buildReportQuery(fiscalYear, fiscalMonth)}`);
    },

    getJournals(params = {}) {
        const query = new URLSearchParams(params).toString();
        return request(`/api/v1/accounting/journals${query ? `?${query}` : ''}`);
    },

    createJournal(payload) {
        return request('/api/v1/accounting/journals', {
            method: 'POST',
            body: payload
        });
    },

    postJournal(id) {
        return request(`/api/v1/accounting/journals/${id}/post`, { method: 'POST' });
    },

    cancelJournal(id) {
        return request(`/api/v1/accounting/journals/${id}/cancel`, { method: 'POST' });
    },

    deleteJournal(id) {
        return request(`/api/v1/accounting/journals/${id}`, { method: 'DELETE' });
    },

    getPeriods() {
        return request('/api/v1/accounting/periods');
    },

    closePeriod(yearMonth, closedByOrBody) {
        return request(`/api/v1/accounting/periods/${yearMonth}/close`, {
            method: 'POST',
            body: buildPeriodBody(closedByOrBody, 'closedBy')
        });
    },

    reopenPeriod(yearMonth, reopenedByOrBody) {
        return request(`/api/v1/accounting/periods/${yearMonth}/reopen`, {
            method: 'POST',
            body: buildPeriodBody(reopenedByOrBody, 'reopenedBy')
        });
    },

    calculatePayroll(payload) {
        return request('/api/v1/hr/payroll/calculate', {
            method: 'POST',
            body: payload
        });
    },

    getPayrollReferenceSummary() {
        return request('/api/v1/hr/payroll/reference-summary');
    },

    transitionPayroll(id, payload) {
        return request(`/api/v1/hr/payroll/${id}/transition`, {
            method: 'POST',
            body: payload
        });
    },

    triggerAdminRpa(taskType) {
        return request('/api/v1/admin/rpa/trigger', {
            method: 'POST',
            body: { taskType }
        });
    },

    getAccountTree() {
        return request('/api/v1/accounting/accounts/tree');
    },

    getAccountList() {
        return request('/api/v1/accounting/accounts/list');
    },

    searchAccounts(params = {}) {
        const query = new URLSearchParams(params).toString();
        return request(`/api/v1/accounting/accounts/search${query ? `?${query}` : ''}`);
    },

    createAccount(payload) {
        return request('/api/v1/accounting/accounts', {
            method: 'POST',
            body: payload
        });
    },

    updateAccount(id, payload) {
        return request(`/api/v1/accounting/accounts/${id}`, {
            method: 'PUT',
            body: payload
        });
    },

    deleteAccount(id) {
        return request(`/api/v1/accounting/accounts/${id}`, {
            method: 'DELETE'
        });
    },

    seedAccounts() {
        return request('/api/v1/accounting/accounts/seed', {
            method: 'POST'
        });
    },

    createVoucher(payload) {
        return request('/api/v1/accounting/vouchers', {
            method: 'POST',
            body: payload
        });
    }
};
