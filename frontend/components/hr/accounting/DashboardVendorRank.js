/**
 * Dashboard Vendor Ranking Widget
 */
import { DashboardWidget } from './DashboardWidget.js';
import { settlementService } from '../../services/hr/settlement-service.js';
import { formatCurrency } from '../../services/hr/accounting-utils.js';

export class DashboardVendorRank extends DashboardWidget {
    async fetchData() {
        const response = await settlementService.getSettlement({
            groupBy: 'VENDOR',
            excludeCanceled: true
        });

        if (response.success) {
            // Sort by sales desc and take top 5
            return response.data
                .sort((a, b) => b.sales - a.sales)
                .slice(0, 5);
        }
        throw new Error(response.message);
    }

    renderContent(container) {
        const data = this.state.data || [];
        if (data.length === 0) {
            container.innerHTML = '<div style="padding:20px; text-align:center; color:#94a3b8; font-size:0.875rem;">데이터가 없습니다.</div>';
            return;
        }

        container.innerHTML = `
            <div style="padding: 12px 16px;">
                ${data.map((row, idx) => `
                    <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 12px;">
                        <span style="width: 20px; height: 20px; display: flex; align-items: center; justify-content: center; background: ${idx === 0 ? '#3182ce' : '#f1f5f9'}; color: ${idx === 0 ? '#fff' : '#64748b'}; border-radius: 4px; font-size: 0.75rem; font-weight: 700;">
                            ${idx + 1}
                        </span>
                        <div style="flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 0.8125rem; color: #334155;">
                            ${row.label}
                        </div>
                        <div style="font-weight: 600; font-size: 0.8125rem; color: #1e293b;">
                            ${formatCurrency(row.sales)}
                        </div>
                    </div>
                `).join('')}
            </div>
        `;
    }
}
