/**
 * Settlement Management Utilities (Using Generic Engine)
 */

import { TRANSACTION_TYPE, TRANSACTION_STATUS } from './accounting-constants.js';
import { aggregateEntries } from '../common/aggregation-engine.js';

export const aggregateTransactions = (transactions, options = {}) => {
    const {
        groupBy = 'MONTH',
        referenceDate = 'transactionDate',
        excludeCanceled = true,
        startDate,
        endDate
    } = options;

    const aggregationOptions = {
        filterFn: (t) => {
            const date = t[referenceDate];
            if (startDate && date < startDate) return false;
            if (endDate && date > endDate) return false;
            if (excludeCanceled && t.status === TRANSACTION_STATUS.CANCELED) return false;
            return !t.isDeleted;
        },
        groupBy: (t) => {
            const date = t[referenceDate];
            if (groupBy === 'MONTH') return date.substring(0, 7);
            if (groupBy === 'YEAR') return date.substring(0, 4);
            if (groupBy === 'VENDOR') return t.vendorName;
            return date;
        },
        summaryRules: {
            sales: 'SUM',
            purchase: 'SUM',
            vat: 'SUM',
            totalAmount: 'SUM'
        }
    };

    // Pre-process for summaryRules (transform type to individual fields for aggregateEntries)
    const processedTransactions = transactions.map(t => ({
        ...t,
        sales: t.type === TRANSACTION_TYPE.SALES ? t.totalAmount : 0,
        purchase: t.type === TRANSACTION_TYPE.PURCHASE ? t.totalAmount : 0
    }));

    const result = aggregateEntries(processedTransactions, aggregationOptions);

    // Post-process to match Accounting expected summary format
    const summary = {
        salesTotal: result.summary.sales,
        purchaseTotal: result.summary.purchase,
        netProfit: result.summary.sales - result.summary.purchase,
        vatTotal: result.summary.vat,
        transactionCount: result.summary.totalCount
    };

    return {
        summary,
        trendData: result.trendData,
        metadata: { ...result.metadata, groupBy, referenceDate }
    };
};

export const normalizeChartData = (trendData) => {
    return {
        labels: trendData.map(d => d.label),
        datasets: [
            { label: '매출', data: trendData.map(d => d.sales), color: '#3182ce' },
            { label: '매입', data: trendData.map(d => d.purchase), color: '#e53e3e' }
        ]
    };
};
