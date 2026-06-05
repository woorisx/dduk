/**
 * Generic Report Engine
 */

export class ReportEngine {
    constructor(config) {
        this.config = config;
        this.data = null;
        this.aggregated = null;
    }

    /**
     * Run Report Pipeline
     * @param {Function} fetchSource - Domain service call to get raw data
     * @param {Object} filterOptions - Filters from UI
     */
    async generate(fetchSource, filterOptions) {
        try {
            // 1. Fetch Source Data
            const sourceData = await fetchSource(filterOptions);
            
            // 2. Aggregate / Transform
            this.data = this.transform(sourceData);
            return {
                success: true,
                data: this.data,
                config: this.config
            };
        } catch (error) {
            console.error(`[ReportEngine] Pipeline failed:`, error);
            return {
                success: false,
                message: "리포트 생성 중 오류가 발생했습니다."
            };
        }
    }

    /**
     * Transform source data into flat list based on config
     */
    transform(source) {
        // If the source is already aggregated trendData, we use it
        // Otherwise, logic to flatten based on columns can be added
        return source.map(item => {
            const row = {};
            this.config.columns.forEach(col => {
                row[col.key] = item[col.key];
            });
            return row;
        });
    }

    /**
     * Get summary data based on summaryType in config
     */
    getSummary() {
        if (!this.data) return null;
        
        const summary = {};
        this.config.columns.forEach(col => {
            if (col.summaryType === 'SUM') {
                summary[col.key] = this.data.reduce((acc, row) => acc + (Number(row[col.key]) || 0), 0);
            }
        });
        return summary;
    }
}
