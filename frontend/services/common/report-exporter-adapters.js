/**
 * Report Exporter Adapters
 */

export const CSVAdapter = {
    format: 'csv',
    extension: 'csv',
    
    /**
     * Generate CSV content
     */
    generate(config, data) {
        const headers = config.columns
            .filter(col => !col.printOnly)
            .map(col => col.label)
            .join(',');
            
        const rows = data.map(item => {
            return config.columns
                .filter(col => !col.printOnly)
                .map(col => {
                    const val = item[col.key] || '';
                    // Simple CSV escaping: if comma exists, wrap in quotes
                    return typeof val === 'string' && val.includes(',') ? `"${val}"` : val;
                })
                .join(',');
        });

        return [headers, ...rows].join('\n');
    },

    /**
     * Trigger browser download
     */
    download(content, filename) {
        const blob = new Blob(["\ufeff" + content], { type: 'text/csv;charset=utf-8;' });
        const link = document.createElement("a");
        const url = URL.createObjectURL(blob);
        link.setAttribute("href", url);
        link.setAttribute("download", filename);
        link.style.visibility = 'hidden';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    }
};

/**
 * Excel Adapter
 */
export const ExcelAdapter = {
    format: 'excel',
    extension: 'xlsx',
    generate(config, data) {
        return CSVAdapter.generate(config, data);
    },
    download(content, filename) {
        CSVAdapter.download(content, filename);
    }
};

/**
 * PDF Adapter
 */
export const PDFAdapter = {
    format: 'pdf',
    extension: 'pdf',
    generate(config, data) {
        return null;
    },
    download(content, filename) {
        window.print();
    }
};
