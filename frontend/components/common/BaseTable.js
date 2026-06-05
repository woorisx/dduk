/**
 * ERP Common Base Table Component
 */

export class BaseTable {
    constructor(options = {}) {
        this.container = options.container;
        this.columns = options.columns || [];
        this.onSort = options.onSort || (() => {});
        this.onSelect = options.onSelect || (() => {});
        this.onRowClick = options.onRowClick || (() => {});
        
        this.data = [];
        this.selectedIds = new Set();
    }

    setData(data) {
        this.data = data;
        this.render();
    }

    setLoading(isLoading) {
        if (isLoading) {
            this.container.innerHTML = '<div class="erp_loading_state">데이터를 불러오는 중...</div>';
        }
    }

    render() {
        if (!this.data || this.data.length === 0) {
            this.container.innerHTML = '<div class="erp_empty_state">조회된 데이터가 없습니다.</div>';
            return;
        }

        const table = document.createElement('table');
        table.className = 'erp_table';
        
        this.renderHeader(table);
        this.renderBody(table);
        
        this.container.innerHTML = '';
        this.container.appendChild(table);
    }

    renderHeader(table) {
        const thead = document.createElement('thead');
        const tr = document.createElement('tr');

        // Checkbox column
        const thCheck = document.createElement('th');
        thCheck.style.width = '40px';
        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.addEventListener('change', (e) => this.toggleAll(e.target.checked));
        thCheck.appendChild(checkbox);
        tr.appendChild(thCheck);

        // Data columns
        this.columns.forEach(col => {
            const th = document.createElement('th');
            th.textContent = col.label;
            if (col.width) th.style.width = col.width;
            if (col.sortable) {
                th.style.cursor = 'pointer';
                th.addEventListener('click', () => this.onSort(col.key));
            }
            tr.appendChild(th);
        });

        thead.appendChild(tr);
        table.appendChild(thead);
    }

    renderBody(table) {
        const tbody = document.createElement('tbody');
        this.data.forEach(row => {
            const tr = document.createElement('tr');
            tr.addEventListener('click', () => this.onRowClick(row));

            // Checkbox
            const tdCheck = document.createElement('td');
            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.checked = this.selectedIds.has(row.id);
            checkbox.addEventListener('click', (e) => e.stopPropagation());
            checkbox.addEventListener('change', (e) => this.toggleRow(row.id, e.target.checked));
            tdCheck.appendChild(checkbox);
            tr.appendChild(tdCheck);

            // Columns
            this.columns.forEach(col => {
                const td = document.createElement('td');
                const val = row[col.key];
                td.innerHTML = col.render ? col.render(val, row) : (val ?? '-');
                if (col.align) td.style.textAlign = col.align;
                tr.appendChild(td);
            });

            tbody.appendChild(tr);
        });
        table.appendChild(tbody);
    }

    toggleRow(id, checked) {
        if (checked) this.selectedIds.add(id);
        else this.selectedIds.delete(id);
        this.onSelect(Array.from(this.selectedIds));
    }

    toggleAll(checked) {
        if (checked) this.data.forEach(row => this.selectedIds.add(row.id));
        else this.selectedIds.clear();
        this.render();
        this.onSelect(Array.from(this.selectedIds));
    }
}
