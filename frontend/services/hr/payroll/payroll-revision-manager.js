/**
 * Payroll Revision Manager
 */
import { REVISION_TYPE, createPayrollRun, PAYROLL_STATUS } from './payroll-models.js';
import { payrollAuditService } from './payroll-audit-service.js';

export class PayrollRevisionManager {
    /**
     * Create a new revision from an existing Run
     * @param {Object} baseRun - Original PayrollRun
     * @param {String} type - ADJUSTMENT, REVERSAL, etc.
     * @param {String} userId
     * @param {String} reason
     */
    static createRevision(baseRun, type, userId, reason) {
        if (!baseRun) throw new Error("Base run is required");

        const newRun = createPayrollRun(baseRun.header.yearMonth);
        
        // Setup Revision Links
        newRun.revisions = {
            revisionNo: (baseRun.revisions.revisionNo || 1) + 1,
            baseRunId: baseRun.header.id,
            revisionType: type,
            reason: reason
        };

        // If REVERSAL, we clone with negative amounts (Simplified logic)
        if (type === REVISION_TYPE.REVERSAL) {
            newRun.records = baseRun.records.map(r => ({
                ...r,
                amounts: Object.fromEntries(Object.entries(r.amounts).map(([k, v]) => [k, -v]))
            }));
            newRun.header.status = PAYROLL_STATUS.CALCULATED; // Reversal is pre-calculated
        } else {
            // For others, we might copy records for manual adjustment
            newRun.records = JSON.parse(JSON.stringify(baseRun.records));
        }

        payrollAuditService.log(newRun.header.id, 'REVISION_CREATED', userId, {
            baseRunId: baseRun.header.id,
            type,
            reason
        });

        return newRun;
    }
}
