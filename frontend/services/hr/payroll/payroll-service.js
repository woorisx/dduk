/**
 * Payroll Management Service
 * Backend remains the single source of truth for payroll data/state.
 */

class PayrollService {
    getApiBaseUrl() {
        if (window.ddukSession && typeof window.ddukSession.getApiBaseUrl === 'function') {
            return window.ddukSession.getApiBaseUrl();
        }
        return window.ddukApi?.getBaseUrl?.() || '';
    }

    getHeaders(extraHeaders = {}) {
        if (window.ddukSession && typeof window.ddukSession.getAuthHeaders === 'function') {
            return window.ddukSession.getAuthHeaders(extraHeaders);
        }

        const token = localStorage.getItem('token');
        return {
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
            ...extraHeaders
        };
    }

    async request(path, options = {}) {
        const method = (options.method || 'GET').toUpperCase();
        const body = options.body;

        if (window.ddukApi) {
            if (method === 'GET') return window.ddukApi.get(path, options);
            if (method === 'POST') return window.ddukApi.post(path, body || {}, options);
            if (method === 'PUT') return window.ddukApi.put(path, body || {}, options);
            if (method === 'PATCH') return window.ddukApi.patch(path, body || {}, options);
            if (method === 'DELETE') return window.ddukApi.delete(path, options);
        }

        const response = await fetch(`${this.getApiBaseUrl()}${path}`, {
            method,
            headers: this.getHeaders({ 'Content-Type': 'application/json' }),
            body: body ? JSON.stringify(body) : undefined
        });

        const text = await response.text();
        let payload = null;

        if (text) {
            try {
                payload = JSON.parse(text);
            } catch (error) {
                payload = null;
            }
        }

        if (!response.ok) {
            throw new Error(payload?.message || `급여 요청 처리에 실패했어. (${response.status})`);
        }

        return payload;
    }

    /**
     * Calculate and save payroll through the backend.
     */
    async calculatePayroll(employee, yearMonth, input = {}) {
        const payroll = await this.request('/api/v1/hr/payroll/calculate', {
            method: 'POST',
            body: {
                employeeId: employee.id,
                payMonth: yearMonth,
                inputs: input
            }
        });

        return { success: true, data: payroll };
    }

    /**
     * Fetch the backend reference summary used by the payroll screens.
     */
    async getPayrollReferenceSummary() {
        const summary = await this.request('/api/v1/hr/payroll/reference-summary');
        return { success: true, data: summary };
    }

    /**
     * Keep compatibility for callers that previously expected a list.
     * The current backend only exposes a reference summary, so we surface
     * the latest payroll as a one-item list instead of faking a full store.
     */
    async getPayrolls(filters = {}) {
        const summary = (await this.getPayrollReferenceSummary()).data || {};
        let payrolls = [];

        if (summary.latestPayroll) {
            payrolls = [summary.latestPayroll];
        }

        if (filters.yearMonth) {
            payrolls = payrolls.filter((record) => record.payMonth === filters.yearMonth);
        }

        if (filters.status) {
            payrolls = payrolls.filter((record) => record.status === filters.status);
        }

        return { success: true, data: payrolls };
    }

    /**
     * Transition status via backend API.
     */
    async transitionStatus(id, nextStatus, userId, reason) {
        const updated = await this.request(`/api/v1/hr/payroll/${id}/transition`, {
            method: 'POST',
            body: { nextStatus, userId, reason }
        });

        return { success: true, data: updated };
    }
}

export const payrollService = new PayrollService();
