/**
 * Settlement Data Adapter
 */

import { accountingService } from './accounting-service.js';
import { aggregateTransactions } from './settlement-utils.js';

export class SettlementAdapter {
    /**
     * Get Aggregated Data (Client-side implementation)
     * This can be swapped with a server-side API call in the future.
     */
    async getAggregatedData(options) {
        // Fetch raw data from accounting service
        // In a real scenario, we might want to fetch only the necessary date range
        const response = await accountingService.getTransactions({
            startDate: options.startDate,
            endDate: options.endDate,
            size: 999999 // Fetch all for client-side aggregation
        });

        // Perform aggregation using pure utility
        const result = aggregateTransactions(response.data, options);

        return {
            success: true,
            data: result.trendData,
            summary: result.summary,
            metadata: result.metadata,
            message: ""
        };
    }
}

/**
 * Example of how a Server-side Adapter would look
 */
/*
export class ServerSettlementAdapter {
    async getAggregatedData(options) {
        const params = new URLSearchParams(options);
        const response = await fetch(`/api/v1/accounting/settlement?${params}`);
        return await response.json();
    }
}
*/
