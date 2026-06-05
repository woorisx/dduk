/**
 * Dashboard Trend Chart Widget
 */
import { DashboardWidget } from './DashboardWidget.js';
import { settlementService } from '../../services/hr/settlement-service.js';
import { normalizeChartData } from '../../services/hr/settlement-utils.js';
import { SettlementChart } from './SettlementChart.js';

export class DashboardTrend extends DashboardWidget {
    async fetchData() {
        const today = new Date();
        const start = new Date(today.getFullYear(), today.getMonth() - 5, 1).toISOString().split('T')[0];
        const end = today.toISOString().split('T')[0];

        const response = await settlementService.getSettlement({
            startDate: start,
            endDate: end,
            groupBy: 'MONTH'
        });

        if (response.success) {
            return normalizeChartData(response.data);
        }
        throw new Error(response.message);
    }

    renderContent(container) {
        if (!this.chartComp) {
            this.chartComp = new SettlementChart({ container });
        }
        this.chartComp.render(this.state.data);
    }
}
