/**
 * Payroll Audit Service (Audit Trail)
 */

export class PayrollAuditService {
    constructor() {
        this.logs = [];
    }

    /**
     * Log an action on a payroll record
     * @param {String} payrollId 
     * @param {String} action - CALCULATED, APPROVED, PAID, RECALCULATED, SYNCED
     * @param {String} userId 
     * @param {Object} details 
     */
    log(payrollId, action, userId, details = {}) {
        const logEntry = {
            id: `LOG-${Date.now()}-${Math.floor(Math.random() * 1000)}`,
            payrollId,
            action,
            userId,
            timestamp: new Date().toISOString(),
            details
        };
        this.logs.push(logEntry);
        return logEntry;
    }

    getLogs(payrollId) {
        return this.logs.filter(l => l.payrollId === payrollId);
    }
}

export const payrollAuditService = new PayrollAuditService();
