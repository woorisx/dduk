/**
 * ERP Common Export System
 */

export const CSVExporter = {
    /**
     * Generate and Download CSV
     * @param {Object} config - { columns: [{key, label}] }
     * @param {Array} data - Flat data array
     * @param {String} filename
     */
    export(config, data, filename) {
        const headers = config.columns.map(c => c.label).join(',');
        const rows = data.map(item => {
            return config.columns.map(c => {
                const val = item[c.key] ?? '';
                return typeof val === 'string' && val.includes(',') ? `"${val}"` : val;
            }).join(',');
        });

        const content = [headers, ...rows].join('\n');
        const blob = new Blob(["\ufeff" + content], { type: 'text/csv;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.setAttribute("href", url);
        link.setAttribute("download", filename || `export_${Date.now()}.csv`);
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    }
};
