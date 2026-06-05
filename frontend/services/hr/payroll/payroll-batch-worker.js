/**
 * Payroll Batch Processing Worker
 */
import { payrollService } from './payroll-service.js';

export class PayrollBatchWorker {
    constructor(options = {}) {
        this.chunkSize = options.chunkSize || 50;
        this.onProgress = options.onProgress || (() => {});
    }

    /**
     * Process a large set of employees in chunks
     */
    async process(yearMonth, employees) {
        const total = employees.length;
        let processed = 0;
        const results = [];
        const errors = [];

        for (let i = 0; i < total; i += this.chunkSize) {
            const chunk = employees.slice(i, i + this.chunkSize);
            
            // Concurrent processing within a chunk
            const chunkPromises = chunk.map(async (emp) => {
                try {
                    const res = await payrollService.calculatePayroll(emp, yearMonth);
                    return { success: true, data: res.data };
                } catch (err) {
                    return { success: false, employeeId: emp.id, error: err.message };
                }
            });

            const chunkResults = await Promise.all(chunkPromises);
            
            chunkResults.forEach(r => {
                if (r.success) results.push(r.data);
                else errors.push(r);
            });

            processed += chunk.length;
            this.onProgress({
                total,
                processed,
                percent: Math.round((processed / total) * 100),
                currentResults: results.length,
                currentErrors: errors.length
            });
        }

        return { success: true, results, errors };
    }
}
