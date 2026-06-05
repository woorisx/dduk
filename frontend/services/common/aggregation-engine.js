/**
 * Generic Aggregation Engine
 */

/**
 * Aggregate entries based on strategy and rules
 * @param {Array} entries - Raw data array
 * @param {Object} options - { groupBy, filterFn, summaryRules }
 * @returns {Object} { summary, trendData, metadata }
 */
export const aggregateEntries = (entries, options = {}) => {
    const {
        groupBy = (item) => item.date?.substring(0, 7), // Default month
        filterFn = () => true,
        summaryRules = {} // e.g. { amount: 'SUM', count: 'COUNT' }
    } = options;

    // 1. Filter
    const filtered = entries.filter(filterFn);

    // 2. Initialize Summary
    const summary = {};
    Object.keys(summaryRules).forEach(key => {
        summary[key] = 0;
    });
    summary.totalCount = filtered.length;

    // 3. Grouping
    const groups = new Map();

    filtered.forEach(item => {
        // Update Summary
        Object.entries(summaryRules).forEach(([key, rule]) => {
            if (rule === 'SUM') summary[key] += (Number(item[key]) || 0);
            if (rule === 'COUNT') summary[key]++;
        });

        // Grouping
        const groupKey = typeof groupBy === 'function' ? groupBy(item) : item[groupBy];
        if (!groups.has(groupKey)) {
            const groupInit = { label: groupKey };
            Object.keys(summaryRules).forEach(key => groupInit[key] = 0);
            groups.set(groupKey, groupInit);
        }
        
        const group = groups.get(groupKey);
        Object.entries(summaryRules).forEach(([key, rule]) => {
            if (rule === 'SUM') group[key] += (Number(item[key]) || 0);
            if (rule === 'COUNT') group[key]++;
        });
    });

    const trendData = Array.from(groups.values()).sort((a, b) => 
        String(a.label).localeCompare(String(b.label))
    );

    return {
        summary,
        trendData,
        metadata: {
            generatedAt: new Date().toISOString(),
            count: filtered.length
        }
    };
};
