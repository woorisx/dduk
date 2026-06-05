/**
 * Payroll Approval Service (Multi-step & Authority)
 */
import { PayrollStateMachine } from './payroll-state-machine.js';
import { PAYROLL_STATUS } from './payroll-models.js';

export class PayrollApprovalService {
    /**
     * Request Approval for a record
     */
    async requestApproval(payrollRun, userId) {
        return PayrollStateMachine.transition(payrollRun, PAYROLL_STATUS.PENDING_APPROVAL, userId);
    }

    /**
     * Approve with Authority Check
     */
    async approve(payrollRun, userId, role) {
        // Multi-role authority check simulation
        if (role !== 'FINANCE' && role !== 'ADMIN') {
            throw new Error("Insufficient authority to approve payroll.");
        }
        return PayrollStateMachine.transition(payrollRun, PAYROLL_STATUS.APPROVED, userId);
    }

    /**
     * Reject and back to Draft
     */
    async reject(payrollRun, userId, comment) {
        return PayrollStateMachine.transition(payrollRun, PAYROLL_STATUS.DRAFT, userId, `REJECTED: ${comment}`);
    }
}

export const payrollApprovalService = new PayrollApprovalService();
