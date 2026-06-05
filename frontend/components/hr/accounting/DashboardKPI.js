/**
 * Dashboard KPI Card Widget
 */
import { DashboardWidget } from './DashboardWidget.js';
import { formatCurrency, formatCompactNumber } from '../../services/hr/accounting-utils.js';
import { settlementService } from '../../services/hr/settlement-service.js';

export class DashboardKPI extends DashboardWidget {
    async fetchData() {
        const today = new Date();
        const firstDay = new Date(today.getFullYear(), today.getMonth(), 1).toISOString().split('T')[0];
        const lastDay = today.toISOString().split('T')[0];

        const response = await settlementService.getSettlement({
            startDate: firstDay,
            endDate: lastDay,
            excludeCanceled: false // Include all for KPI breakdown
        });

        if (response.success) {
            return this.extractValue(response.summary);
        }
        throw new Error(response.message);
    }

    extractValue(summary) {
        switch (this.config.metric) {
            case 'SALES': return summary.salesTotal;
            case 'PURCHASE': return summary.purchaseTotal;
            case 'PROFIT': return summary.netProfit;
            case 'PENDING_COUNT': return summary.transactionCount;
            case 'UNSETTLED': return summary.pendingAmount;
            case 'VAT': return summary.vatTotal;
            default: return 0;
        }
    }

    renderContent(container) {
        const value = this.state.data;
        const formattedValue = this.config.formatter === 'currency' 
            ? formatCurrency(value) 
            : formatCompactNumber(value);

        container.innerHTML = `
            <div style="padding: 20px; display: flex; flex-direction: column; justify-content: center; height: 100%;">
                <div style="font-size: 1.75rem; font-weight: 700; color: #1e293b; margin-bottom: 4px;">
                    ${formattedValue}
                </div>
                ${this.config.trend ? `
                    <div style="font-size: 0.75rem; color: ${this.config.trend > 0 ? '#10b981' : '#ef4444'};">
                        ${this.config.trend > 0 ? '↑' : '↓'} ${Math.abs(this.config.trend)}% 전월 대비
                    </div>
                ` : ''}
            </div>
        `;
    }
}
