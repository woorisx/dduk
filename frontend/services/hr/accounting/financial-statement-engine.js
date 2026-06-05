/**
 * Financial Statement Generation Engine
 */
import { generalLedgerService } from './general-ledger-service.js';
import { ACCOUNT_TYPE } from './accounting-master-service.js';

const API_BASE_URL = (() => {
    return window.ddukSession?.getApiBaseUrl?.() || window.ddukApi?.getBaseUrl?.() || '';
})();

const getHeaders = () => {
    return window.ddukSession?.getAuthHeaders?.({ 'Content-Type': 'application/json' })
        || { 'Content-Type': 'application/json' };
};

export class FinancialStatementEngine {
    /**
     * Generate Balance Sheet (B/S) (Backend-only)
     */
    async generateBalanceSheet() {
        // Step 1: Call Backend API (Single Source of Truth)
        const response = await fetch(`${API_BASE_URL}/api/v1/accounting/reports/balance-sheet`, {
            headers: getHeaders()
        });
        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.message || `대차대조표 생성 서버 오류 (${response.status})`);
        }
        return await response.json();
    }

    /**
     * Generate Profit & Loss (P/L) (Backend-only)
     */
    async generateProfitAndLoss() {
        // Step 1: Call Backend API (Single Source of Truth)
        const response = await fetch(`${API_BASE_URL}/api/v1/accounting/reports/profit-loss`, {
            headers: getHeaders()
        });
        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.message || `손익계산서 생성 서버 오류 (${response.status})`);
        }
        return await response.json();
    }
}

export const financialStatementEngine = new FinancialStatementEngine();
