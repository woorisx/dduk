/**
 * Payroll Domain Models & Aggregate Root
 */

export const PAYROLL_STATUS = {
    DRAFT: 'DRAFT',
    CALCULATING: 'CALCULATING',
    CALCULATED: 'CALCULATED',
    PENDING_APPROVAL: 'PENDING_APPROVAL',
    APPROVED: 'APPROVED',
    POSTED: 'POSTED',
    PAID: 'PAID',
    REVERSED: 'REVERSED',
    CANCELLED: 'CANCELLED'
};

export const REVISION_TYPE = {
    ADJUSTMENT: 'ADJUSTMENT',
    REVERSAL: 'REVERSAL',
    RECALCULATION: 'RECALCULATION',
    CORRECTION: 'CORRECTION'
};

/**
 * Factory for PayrollRun (Aggregate Root)
 */
export const createPayrollRun = (yearMonth) => ({
    header: {
        id: `RUN-${Date.now()}`,
        yearMonth,
        status: PAYROLL_STATUS.DRAFT,
        version: 1,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        totalGrossPay: 0,
        totalNetPay: 0,
        totalDeductions: 0
    },
    records: [], // PayrollEmployeeRecord[]
    snapshot: {
        rules: null,       // Snapshot of PAYROLL_RULES
        contracts: {},     // Snapshot of employee contracts
        inputs: {}         // Raw calculation inputs
    },
    revisions: {
        revisionNo: 1,
        baseRunId: null,
        revisionType: null,
        reason: null
    },
    accounting: {
        journalEntries: [],
        postingStatus: 'NONE', // NONE, PENDING, POSTED, FAILED
        reconciliationStatus: 'NONE',
        lastSyncAt: null,
        retryCount: 0
    },
    auditTrail: [] // Event-style logs
});
