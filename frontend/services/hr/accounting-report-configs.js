/**
 * Accounting Report Configurations
 */

export const ACCOUNTING_REPORTS = {
    MONTHLY_PL: {
        id: 'MONTHLY_PL',
        title: '월별 손익 리포트',
        columns: [
            { key: 'label', label: '기간(월)', width: '120px' },
            { key: 'sales', label: '매출액', formatter: 'currency', align: 'right', summaryType: 'SUM' },
            { key: 'purchase', label: '매입액', formatter: 'currency', align: 'right', summaryType: 'SUM' },
            { key: 'count', label: '거래 건수', align: 'center', summaryType: 'SUM' }
        ],
        aggregation: {
            groupBy: 'MONTH',
            referenceDate: 'transactionDate'
        },
        showSummary: true,
        printOptions: { landscape: false }
    },
    VENDOR_SUMMARY: {
        id: 'VENDOR_SUMMARY',
        title: '거래처별 거래 요약 리포트',
        columns: [
            { key: 'label', label: '거래처명', width: '200px' },
            { key: 'sales', label: '총 매출액', formatter: 'currency', align: 'right', summaryType: 'SUM' },
            { key: 'purchase', label: '총 매입액', formatter: 'currency', align: 'right', summaryType: 'SUM' },
            { key: 'count', label: '거래 횟수', align: 'center', summaryType: 'SUM' }
        ],
        aggregation: {
            groupBy: 'VENDOR',
            referenceDate: 'transactionDate'
        },
        showSummary: true,
        printOptions: { landscape: true }
    }
};
