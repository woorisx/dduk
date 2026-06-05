/**
 * Accounting Service - 실제 API 연동 버전
 * /api/v1/accounting/vouchers 실제 호출
 */

const VOUCHER_API_BASE = '/api/v1/accounting/vouchers';

class AccountingService {

    /**
     * 전표 목록 조회 (거래내역 등록 페이지용)
     * GET /api/v1/accounting/vouchers?type=SALES|PURCHASE&status=...
     */
    async getTransactions(filters = {}) {
        const params = new URLSearchParams();

        if (filters.type && filters.type !== 'ALL') {
            params.set('type', filters.type);
        }
        if (filters.status && filters.status !== 'ALL') {
            params.set('status', filters.status);
        }
        if (filters.keyword) {
            params.set('keyword', filters.keyword);
        }
        if (filters.startDate) {
            params.set('startDate', filters.startDate);
        }
        if (filters.endDate) {
            params.set('endDate', filters.endDate);
        }

        const response = await fetch(`${VOUCHER_API_BASE}?${params.toString()}`);
        if (!response.ok) {
            const errorBody = await response.json().catch(() => ({}));
            throw new Error(errorBody.message || `거래내역 조회 실패 (${response.status})`);
        }
        const body = await response.json();
        return {
            success: true,
            data: body.data || [],
            message: body.message || ''
        };
    }

    /**
     * 전표 상태 변경
     * PATCH /api/v1/accounting/vouchers/{id}/status?status=POSTED|CANCELLED
     */
    async updateTransactionStatus(id, status) {
        const response = await fetch(`${VOUCHER_API_BASE}/${id}/status?status=${encodeURIComponent(status)}`, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' }
        });
        if (!response.ok) {
            const errorBody = await response.json().catch(() => ({}));
            throw new Error(errorBody.message || `상태 변경 실패 (${response.status})`);
        }
        return { success: true, message: '상태가 변경되었습니다.' };
    }

    /**
     * 전표 취소 (CANCELLED 상태로 변경)
     */
    async deleteTransaction(id) {
        return this.updateTransactionStatus(id, 'CANCELLED');
    }

    /**
     * 전표 요약 조회 (KPI용)
     * GET /api/v1/accounting/vouchers/summary
     */
    async getSummary() {
        const response = await fetch(`${VOUCHER_API_BASE}/summary`);
        if (!response.ok) {
            const errorBody = await response.json().catch(() => ({}));
            throw new Error(errorBody.message || `요약 조회 실패 (${response.status})`);
        }
        const body = await response.json();
        return body.data || {};
    }
}

export const accountingService = new AccountingService();
