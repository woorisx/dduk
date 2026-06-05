/**
 * Payroll Lifecycle State Machine
 */
import { PAYROLL_STATUS } from './payroll-models.js';
import { payrollAuditService } from './payroll-audit-service.js';

export class PayrollStateMachine {
    static VALID_TRANSITIONS = {
        [PAYROLL_STATUS.DRAFT]: [PAYROLL_STATUS.CALCULATING, PAYROLL_STATUS.CANCELLED],
        [PAYROLL_STATUS.CALCULATING]: [PAYROLL_STATUS.CALCULATED, PAYROLL_STATUS.DRAFT],
        [PAYROLL_STATUS.CALCULATED]: [PAYROLL_STATUS.PENDING_APPROVAL, PAYROLL_STATUS.DRAFT],
        [PAYROLL_STATUS.PENDING_APPROVAL]: [PAYROLL_STATUS.APPROVED, PAYROLL_STATUS.DRAFT],
        [PAYROLL_STATUS.APPROVED]: [PAYROLL_STATUS.POSTED, PAYROLL_STATUS.REVERSED],
        [PAYROLL_STATUS.POSTED]: [PAYROLL_STATUS.PAID, PAYROLL_STATUS.REVERSED],
        [PAYROLL_STATUS.PAID]: [PAYROLL_STATUS.REVERSED],
        [PAYROLL_STATUS.REVERSED]: [], // Terminal for this record
        [PAYROLL_STATUS.CANCELLED]: []  // Terminal
    };

    /**
     * Transition to a new state
     */
    static transition(payrollRun, nextStatus, userId, reason = '') {
        const currentStatus = payrollRun.header.status;
        const allowed = this.VALID_TRANSITIONS[currentStatus] || [];

        if (!allowed.includes(nextStatus)) {
            throw new Error(`Invalid transition: ${currentStatus} -> ${nextStatus}`);
        }

        // Apply changes
        payrollRun.header.status = nextStatus;
        payrollRun.header.updatedAt = new Date().toISOString();

        // Automated Audit
        payrollAuditService.log(payrollRun.header.id, `STATE_TRANSITION`, userId, {
            from: currentStatus,
            to: nextStatus,
            reason
        });

        return payrollRun;
    }

    /**
     * Check if status is immutable
     */
    static isImmutable(status) {
        return [PAYROLL_STATUS.APPROVED, PAYROLL_STATUS.POSTED, PAYROLL_STATUS.PAID, PAYROLL_STATUS.REVERSED].includes(status);
    }
}
