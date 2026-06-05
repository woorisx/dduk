/**
 * Dashboard Recent Transactions Widget
 */
import { DashboardWidget } from './DashboardWidget.js';
import { accountingService } from '../../services/hr/accounting-service.js';
import { formatCurrency, formatDate, getStatusBadgeClass } from '../../services/hr/accounting-utils.js';
import { TRANSACTION_STATUS_LABEL } from '../../services/hr/accounting-constants.js';

export class DashboardRecentList extends DashboardWidget {
    async fetchData() {
        const response = await accountingService.getTransactions({
            page: 1,
            size: 5,
            sortBy: 'transactionDate',
            sortOrder: 'desc'
        });

        if (response.success) {
            return response.data;
        }
        throw new Error(response.message);
    }

    renderContent(container) {
        const data = this.state.data || [];
        if (data.length === 0) {
            container.innerHTML = '<div style="padding:20px; text-align:center; color:#94a3b8; font-size:0.875rem;">최근 거래가 없습니다.</div>';
            return;
        }

        container.innerHTML = `
            <table class="erp_table" style="width:100%; font-size:0.8125rem;">
                <tbody>
                    ${data.map(row => `
                        <tr>
                            <td style="padding:8px 16px; border-bottom:1px solid #f1f5f9;">
                                <div style="font-weight:500; color:#334155;">${row.vendorName}</div>
                                <div style="font-size:0.7rem; color:#94a3b8;">${formatDate(row.transactionDate)}</div>
                            </td>
                            <td style="padding:8px 16px; border-bottom:1px solid #f1f5f9; text-align:right;">
                                <div style="font-weight:600; color:#1e293b;">${formatCurrency(row.totalAmount)}</div>
                                <span class="erp_badge ${getStatusBadgeClass(row.status)}" style="font-size:0.65rem;">${TRANSACTION_STATUS_LABEL[row.status]}</span>
                            </td>
                        </tr>
                    `).join('')}
                </tbody>
            </table>
            <div style="padding:8px; text-align:center; border-top:1px solid #f1f5f9;">
                <a href="./transactions.html" style="font-size:0.75rem; color:#3182ce; text-decoration:none;">전체 보기 &gt;</a>
            </div>
        `;
    }
}
