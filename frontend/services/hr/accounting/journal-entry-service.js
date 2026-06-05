/**
 * Journal Entry Management Service
 */
import { generalLedgerService } from './general-ledger-service.js';
import { accountingMasterService } from './accounting-master-service.js';

const API_BASE_URL = (() => {
    return window.ddukSession?.getApiBaseUrl?.() || window.ddukApi?.getBaseUrl?.() || '';
})();

const getHeaders = () => {
    return window.ddukSession?.getAuthHeaders?.({ 'Content-Type': 'application/json' })
        || { 'Content-Type': 'application/json' };
};

export class JournalEntryService {
    constructor() {
        this.journals = [];
    }

    /**
     * Create and Post a Journal Entry (Backend-only)
     */
    async createAndPost(data) {
        // data: { date, description, items: [{accountCode, amount, side}] }
        
        // Step 1: Call Backend API (Single Source of Truth)
        const response = await fetch(`${API_BASE_URL}/api/v1/accounting/journals`, {
            method: 'POST',
            headers: getHeaders(),
            body: JSON.stringify(data)
        });

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.message || `전표 생성 서버 오류 (${response.status})`);
        }

        const apiData = await response.json();
        this.journals.push(apiData);
        return { success: true, id: apiData.id };
    }

    getJournals() { return this.journals; }
}

export const journalEntryService = new JournalEntryService();
