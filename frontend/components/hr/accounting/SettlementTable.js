/**
 * Settlement Table Component
 */

import { formatCurrency } from '../../services/hr/accounting-utils.js';

export class SettlementTable {
    constructor(options = {}) {
        this.container = options.container;
        this.columns = options.columns || [
            { key: 'label', label: '항목' },
            { key: 'sales', label: '매출액', align: 'right', format: formatCurrency },
            { key: 'purchase', label: '매입액', align: 'right', format: formatCurrency },
            { key: 'profit', label: '수익', align: 'right', format: formatCurrency },
            { key: 'count', label: '거래 건수', align: 'center' }
        ];
    }

    render(data) {
        if (!data || data.length === 0) {
            this.container.innerHTML = '<div class="erp_empty_state">정산 데이터가 없습니다.</div>';
            return;
        }

        const table = document.createElement('table');
        table.className = 'erp_table';

        // Header
        const thead = document.createElement('thead');
        const headerRow = document.createElement('tr');
        this.columns.forEach(col => {
            const th = document.createElement('th');
            th.textContent = col.label;
            if (col.align) th.style.textAlign = col.align;
            headerRow.appendChild(th);
        });
        thead.appendChild(headerRow);
        table.appendChild(thead);

        // Body
        const tbody = document.createElement('tbody');
        let totalSales = 0, totalPurchase = 0, totalCount = 0;

        data.forEach(row => {
            const tr = document.createElement('tr');
            const profit = row.sales - row.purchase;
            
            totalSales += row.sales;
            totalPurchase += row.purchase;
            totalCount += row.count;

            this.columns.forEach(col => {
                const td = document.createElement('td');
                if (col.align) td.style.textAlign = col.align;
                
                let val = row[col.key];
                if (col.key === 'profit') val = profit;
                
                td.textContent = col.format ? col.format(val) : val;
                tr.appendChild(td);
            });
            tbody.appendChild(tr);
        });
        table.appendChild(tbody);

        // Footer (Summary Row)
        const tfoot = document.createElement('tfoot');
        const footerRow = document.createElement('tr');
        footerRow.style.backgroundColor = '#f8fafc';
        footerRow.style.fontWeight = '700';

        this.columns.forEach(col => {
            const td = document.createElement('td');
            if (col.align) td.style.textAlign = col.align;
            
            if (col.key === 'label') td.textContent = '합계';
            else if (col.key === 'sales') td.textContent = formatCurrency(totalSales);
            else if (col.key === 'purchase') td.textContent = formatCurrency(totalPurchase);
            else if (col.key === 'profit') td.textContent = formatCurrency(totalSales - totalPurchase);
            else if (col.key === 'count') td.textContent = totalCount;
            
            footerRow.appendChild(td);
        });
        tfoot.appendChild(footerRow);
        table.appendChild(tfoot);

        this.container.innerHTML = '';
        this.container.appendChild(table);
    }
}
