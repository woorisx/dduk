/**
 * Payroll Closing & Period Management Service
 */
import { payrollEventBus, PAYROLL_EVENTS } from './payroll-event-bus.js';

export class PayrollClosingService {
    constructor() {
        this.closedPeriods = new Set();
    }

    /**
     * Close a payroll period (Locking)
     */
    async closePeriod(yearMonth, userId) {
        // 1. Validation Logic
        // - Check if all records are APPROVED or POSTED
        // - Check if reconciliation is MATCHED
        
        this.closedPeriods.add(yearMonth);
        
        payrollEventBus.publish(PAYROLL_EVENTS.CLOSED, { yearMonth, userId, timestamp: new Date().toISOString() });
        return { success: true, message: `${yearMonth} 마감이 완료되었습니다.` };
    }

    /**
     * Check if a period is locked
     */
    isLocked(yearMonth) {
        return this.closedPeriods.has(yearMonth);
    }

    /**
     * Rollback a closed period (High authority only)
     */
    async rollbackClose(yearMonth, userId) {
        this.closedPeriods.delete(yearMonth);
        return { success: true };
    }
}

export const payrollClosingService = new PayrollClosingService();
