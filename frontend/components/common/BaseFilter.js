/**
 * ERP Common Base Filter Component
 */

export class BaseFilter {
    constructor(options = {}) {
        this.container = options.container;
        this.fields = options.fields || []; // [{ id, label, type, options }]
        this.onFilter = options.onFilter || (() => {});
        this.values = options.initialValues || {};
    }

    render() {
        let html = `
            <div class="erp_filter_container" style="display: flex; gap: 16px; flex-wrap: wrap; padding: 20px; background: #fff; border: 1px solid #e2e8f0; border-radius: 8px;">
                ${this.fields.map(field => this.renderField(field)).join('')}
                <div style="margin-left: auto; display: flex; gap: 8px; align-items: flex-end;">
                    <button type="button" class="erp_button erp_button_secondary btn_reset">초기화</button>
                    <button type="button" class="erp_button erp_button_primary btn_search">조회</button>
                </div>
            </div>
        `;
        this.container.innerHTML = html;
        this.initEvents();
    }

    renderField(field) {
        const value = this.values[field.id] || '';
        let inputHtml = '';

        if (field.type === 'select') {
            inputHtml = `
                <select id="filter_${field.id}" class="erp_select" style="width: ${field.width || '120px'};">
                    ${field.options.map(opt => `<option value="${opt.value}" ${opt.value === value ? 'selected' : ''}>${opt.label}</option>`).join('')}
                </select>
            `;
        } else if (field.type === 'date') {
            inputHtml = `<input type="date" id="filter_${field.id}" class="erp_input" value="${value}" style="width: 140px;">`;
        } else {
            inputHtml = `<input type="text" id="filter_${field.id}" class="erp_input" value="${value}" placeholder="${field.placeholder || ''}" style="width: 160px;">`;
        }

        return `
            <div class="filter_field" style="display: flex; flex-direction: column; gap: 4px;">
                <label style="font-size: 0.75rem; font-weight: 600; color: #64748b;">${field.label}</label>
                ${inputHtml}
            </div>
        `;
    }

    initEvents() {
        this.container.querySelector('.btn_search').addEventListener('click', () => {
            this.fields.forEach(field => {
                const el = document.getElementById(`filter_${field.id}`);
                this.values[field.id] = el.value;
            });
            this.onFilter(this.values);
        });

        this.container.querySelector('.btn_reset').addEventListener('click', () => {
            this.values = {};
            this.render();
            this.onFilter(this.values);
        });
    }
}
