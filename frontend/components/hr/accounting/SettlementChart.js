/**
 * Settlement Chart Component (Library Independent Wrapper)
 */

export class SettlementChart {
    constructor(options = {}) {
        this.container = options.container;
        this.chartType = options.chartType || 'bar'; // bar | line
    }

    /**
     * Render chart based on normalized data
     * data format: { labels: [], datasets: [{ label: '', data: [], color: '' }] }
     */
    render(data) {
        if (!data || !data.labels || data.labels.length === 0) {
            this.container.innerHTML = '<div class="erp_empty_state">차트 데이터가 없습니다.</div>';
            return;
        }

        const maxVal = Math.max(...data.datasets.flatMap(d => d.data), 1);
        
        let html = `
            <div class="erp_chart_wrapper" style="padding: 24px; background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; height: 300px; display: flex; flex-direction: column;">
                <div style="display: flex; justify-content: flex-end; gap: 16px; margin-bottom: 16px; font-size: 0.75rem;">
                    ${data.datasets.map(d => `
                        <span style="display: flex; align-items: center; gap: 4px;">
                            <span style="width: 12px; height: 12px; background: ${d.color}; border-radius: 2px;"></span>
                            ${d.label}
                        </span>
                    `).join('')}
                </div>
                <div class="chart_bars" style="flex: 1; display: flex; align-items: flex-end; gap: 20px; padding-bottom: 20px; border-bottom: 1px solid #e2e8f0;">
                    ${data.labels.map((label, idx) => {
                        return `
                            <div class="bar_group" style="flex: 1; display: flex; flex-direction: column; align-items: center; gap: 4px; height: 100%;">
                                <div style="flex: 1; display: flex; align-items: flex-end; gap: 4px; width: 100%;">
                                    ${data.datasets.map(ds => {
                                        const height = (ds.data[idx] / maxVal) * 100;
                                        return `<div title="${ds.label}: ${ds.data[idx]}" style="flex: 1; height: ${height}%; background: ${ds.color}; border-radius: 2px 2px 0 0; transition: height 0.3s;"></div>`;
                                    }).join('')}
                                </div>
                                <span style="font-size: 0.7rem; color: #64748b; white-space: nowrap;">${label}</span>
                            </div>
                        `;
                    }).join('')}
                </div>
            </div>
        `;

        this.container.innerHTML = html;
    }
}
