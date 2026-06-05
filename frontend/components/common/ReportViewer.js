/**
 * Common Report Viewer Component
 */
import { formatCurrency, formatDate } from '../../services/hr/accounting-utils.js';

export class ReportViewer {
    constructor(options = {}) {
        this.container = options.container;
    }

    render(config, data, summary = null) {
        if (!data || data.length === 0) {
            this.container.innerHTML = '<div class="erp_empty_state">표시할 리포트 데이터가 없습니다.</div>';
            return;
        }

        const formatters = {
            'currency': formatCurrency,
            'date': formatDate
        };

        let html = `
            <div class="report_paper ${config.printOptions?.landscape ? 'is_landscape' : ''}">
                <header class="report_header">
                    <h1>${config.title}</h1>
                    <div class="report_meta">
                        출력일시: ${new Date().toLocaleString()}
                    </div>
                </header>

                <div class="report_content">
                    <table class="report_table">
                        <thead>
                            <tr>
                                ${config.columns.filter(c => !c.exportOnly).map(col => `
                                    <th style="width:${col.width || 'auto'}; text-align:${col.align || 'left'};">
                                        ${col.label}
                                    </th>
                                `).join('')}
                            </tr>
                        </thead>
                        <tbody>
                            ${data.map(row => `
                                <tr>
                                    ${config.columns.filter(c => !c.exportOnly).map(col => {
                                        const val = row[col.key];
                                        const formatted = col.formatter && formatters[col.formatter] 
                                            ? formatters[col.formatter](val) 
                                            : val;
                                        return `<td style="text-align:${col.align || 'left'};">${formatted}</td>`;
                                    }).join('')}
                                </tr>
                            `).join('')}
                        </tbody>
                        ${config.showSummary && summary ? `
                            <tfoot>
                                <tr>
                                    ${config.columns.filter(c => !c.exportOnly).map(col => {
                                        const val = summary[col.key] || '';
                                        const formatted = col.formatter && formatters[col.formatter] 
                                            ? formatters[col.formatter](val) 
                                            : (col.summaryType === 'SUM' ? val : '');
                                        return `<td style="text-align:${col.align || 'left'}; font-weight:bold;">${formatted}</td>`;
                                    }).join('')}
                                </tr>
                            </tfoot>
                        ` : ''}
                    </table>
                </div>

                <footer class="report_footer">
                    <p>DDUK ERP - 경영 리포트 서비스</p>
                </footer>
            </div>
        `;

        this.container.innerHTML = html;
        this.applyPrintStyles();
    }

    applyPrintStyles() {
        if (document.getElementById('report_print_styles')) return;

        const style = document.createElement('style');
        style.id = 'report_print_styles';
        style.innerHTML = `
            @media print {
                body * { visibility: hidden; }
                .report_paper, .report_paper * { visibility: visible; }
                .report_paper {
                    position: absolute;
                    left: 0;
                    top: 0;
                    width: 100%;
                }
                .app-sidebar, .app-header, .report_controls { display: none !important; }
            }
            .report_paper {
                background: #fff;
                padding: 40px;
                box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
                border: 1px solid #e2e8f0;
                margin-top: 24px;
            }
            .report_header { text-align: center; margin-bottom: 32px; }
            .report_header h1 { font-size: 1.5rem; color: #1a202c; margin-bottom: 8px; }
            .report_meta { font-size: 0.75rem; color: #718096; }
            .report_table { width: 100%; border-collapse: collapse; font-size: 0.8125rem; }
            .report_table th { background: #f8fafc; border-bottom: 2px solid #e2e8f0; padding: 12px 8px; color: #475569; }
            .report_table td { border-bottom: 1px solid #f1f5f9; padding: 10px 8px; color: #1e293b; }
            .report_footer { margin-top: 40px; text-align: center; font-size: 0.7rem; color: #a0aec0; border-top: 1px solid #edf2f7; padding-top: 20px; }
        `;
        document.head.appendChild(style);
    }
}
