/**
 * Settlement Summary Component
 */

import { formatCurrency } from '../../services/hr/accounting-utils.js';

export class SettlementSummary {
    constructor(options = {}) {
        this.container = options.container;
    }

    render(summary) {
        if (!summary) {
            this.container.innerHTML = '';
            return;
        }

        this.container.innerHTML = `
            <div class="settlement_summary_grid" style="display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 16px; margin-bottom: 24px;">
                <article class="erp_card" style="padding: 20px; background: #fff; border: 1px solid #e2e8f0; border-radius: 8px;">
                    <p style="font-size: 0.875rem; color: #64748b; margin-bottom: 8px;">총 매출액</p>
                    <h3 style="font-size: 1.5rem; font-weight: 700; color: #2b6cb0;">${formatCurrency(summary.salesTotal)}</h3>
                    <div style="margin-top: 12px; font-size: 0.75rem; color: #94a3b8;">
                        <span style="display:flex; justify-content:space-between;">완료: <span>${formatCurrency(summary.completedAmount)}</span></span>
                    </div>
                </article>

                <article class="erp_card" style="padding: 20px; background: #fff; border: 1px solid #e2e8f0; border-radius: 8px;">
                    <p style="font-size: 0.875rem; color: #64748b; margin-bottom: 8px;">총 매입액</p>
                    <h3 style="font-size: 1.5rem; font-weight: 700; color: #c53030;">${formatCurrency(summary.purchaseTotal)}</h3>
                    <div style="margin-top: 12px; font-size: 0.75rem; color: #94a3b8;">
                        <span style="display:flex; justify-content:space-between;">대기: <span>${formatCurrency(summary.pendingAmount)}</span></span>
                    </div>
                </article>

                <article class="erp_card" style="padding: 20px; background: #fff; border: 1px solid #e2e8f0; border-radius: 8px;">
                    <p style="font-size: 0.875rem; color: #64748b; margin-bottom: 8px;">순이익</p>
                    <h3 style="font-size: 1.5rem; font-weight: 700; color: #2d3748;">${formatCurrency(summary.netProfit)}</h3>
                    <p style="margin-top: 12px; font-size: 0.75rem; color: ${summary.netProfit >= 0 ? '#38a169' : '#e53e3e'};">
                        수익률: ${summary.salesTotal > 0 ? ((summary.netProfit / summary.salesTotal) * 100).toFixed(1) : 0}%
                    </p>
                </article>

                <article class="erp_card" style="padding: 20px; background: #fff; border: 1px solid #e2e8f0; border-radius: 8px;">
                    <p style="font-size: 0.875rem; color: #64748b; margin-bottom: 8px;">부가세 합계</p>
                    <h3 style="font-size: 1.5rem; font-weight: 700; color: #718096;">${formatCurrency(summary.vatTotal)}</h3>
                    <div style="margin-top: 12px; font-size: 0.75rem; color: #94a3b8;">
                        <span style="display:flex; justify-content:space-between;">증빙 발행: <span>${formatCurrency(summary.taxInvoiceIssuedAmount)}</span></span>
                    </div>
                </article>
            </div>
        `;
    }
}
