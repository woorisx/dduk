/**
 * Settlement Management Service
 */

import { SettlementAdapter } from './settlement-adapter.js';

class SettlementService {
    constructor() {
        this.adapter = new SettlementAdapter();
    }

    /**
     * Switch adapter if needed (e.g. based on data volume or environment)
     */
    setAdapter(adapter) {
        this.adapter = adapter;
    }

    async getSettlement(options = {}) {
        try {
            return await this.adapter.getAggregatedData(options);
        } catch (error) {
            console.error("Settlement data fetch error:", error);
            return {
                success: false,
                message: "정산 데이터를 불러오는 중 오류가 발생했습니다."
            };
        }
    }
}

export const settlementService = new SettlementService();
