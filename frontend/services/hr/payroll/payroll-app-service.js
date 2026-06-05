/**
 * Payroll Application Service (Orchestration Layer)
 */
import { payrollService } from './payroll-service.js';
import { payrollAccountingBridge } from './payroll-accounting-bridge.js';
import { UIUtils } from '../../utils/ui-utils.js';

export class PayrollAppService {
    /**
     * Run batch payroll for a period.
     */
    async runBatchPayroll(yearMonth, employees) {
        const results = [];

        for (const employee of employees) {
            const result = await payrollService.calculatePayroll(employee, yearMonth, { overtimeHours: 0 });
            results.push(result.data);
        }

        return results;
    }

    /**
     * Finalize and post payroll to accounting.
     * The current backend contract does not expose a dedicated payroll detail query,
     * so this flow uses the latest reference summary instead of an in-memory record store.
     */
    async finalizeAndPost(payrollId, userId) {
        try {
            UIUtils.setLoading('btn-finalize', true);

            const summary = (await payrollService.getPayrollReferenceSummary()).data || {};
            const record = summary.latestPayroll;

            if (!record || String(record.id) !== String(payrollId)) {
                throw new Error('급여 상세 조회 API가 아직 없어 최신 급여 기준으로만 후속 처리를 지원해.');
            }

            const postResult = await payrollAccountingBridge.postPayrollRun(record);
            if (!postResult.success) {
                throw new Error(postResult.message || '급여 전표 반영에 실패했어.');
            }

            await payrollService.transitionStatus(payrollId, 'POSTED', userId, '급여 확정 및 회계 반영');
            UIUtils.showToast('급여가 확정되어 회계에 반영됐어.', 'success');
            return { success: true };
        } catch (error) {
            UIUtils.showToast(error.message, 'error');
            return { success: false, message: error.message };
        } finally {
            UIUtils.setLoading('btn-finalize', false);
        }
    }

    /**
     * Explicitly block revision creation until a dedicated backend endpoint exists.
     */
    async initiateRevision() {
        throw new Error('급여 리비전 생성은 전용 백엔드 API가 준비된 뒤에만 지원해.');
    }
}

export const payrollAppService = new PayrollAppService();
