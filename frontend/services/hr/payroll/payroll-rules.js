/**
 * Payroll Tax & Deduction Rules (Config Based)
 */

export const PAYROLL_RULES = {
    VERSION: '2026-V1',
    EFFECTIVE_DATE: '2026-01-01',
    
    // Deductions (Percentage based)
    DEDUCTIONS: {
        NATIONAL_PENSION: { label: '국민연금', rate: 0.045, maxAmount: 265500 },
        HEALTH_INSURANCE: { label: '건강보험', rate: 0.03545, maxAmount: null },
        LONGTERM_CARE: { label: '장기요양보험', rate: 0.1295, isRateOfHealth: true }, // 12.95% of Health Insurance
        EMPLOYMENT_INSURANCE: { label: '고용보험', rate: 0.009, maxAmount: null }
    },

    // Simplified Income Tax (Progressive simulation)
    INCOME_TAX_BRACKETS: [
        { limit: 12000000, rate: 0.06, deduction: 0 },
        { limit: 46000000, rate: 0.15, deduction: 1080000 },
        { limit: 88000000, rate: 0.24, deduction: 5220000 },
        { limit: Infinity, rate: 0.35, deduction: 14900000 }
    ],

    // Rounding Policies
    ROUNDING: {
        DEFAULT: 'FLOOR_1', // Cut off below 1 KRW
        TAX: 'FLOOR_10'     // Cut off below 10 KRW
    }
};

/**
 * Apply rounding policy
 */
export const applyRounding = (amount, policy = 'FLOOR_10') => {
    switch (policy) {
        case 'FLOOR_1': return Math.floor(amount);
        case 'FLOOR_10': return Math.floor(amount / 10) * 10;
        case 'ROUND_10': return Math.round(amount / 10) * 10;
        default: return Math.floor(amount);
    }
};
