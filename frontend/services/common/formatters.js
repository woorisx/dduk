/**
 * ERP Common Formatters
 */

/**
 * Format number as currency (KRW)
 */
export const formatCurrency = (value) => {
    return new Intl.NumberFormat('ko-KR', {
        style: 'currency',
        currency: 'KRW',
    }).format(value || 0);
};

/**
 * Format number with thousand separators
 */
export const formatNumber = (value) => {
    return new Intl.NumberFormat('ko-KR').format(value || 0);
};

/**
 * Format number compactly (e.g. 1.2M)
 */
export const formatCompactNumber = (value) => {
    return new Intl.NumberFormat('ko-KR', {
        notation: 'compact',
        maximumFractionDigits: 1
    }).format(value || 0);
};

/**
 * Format date string (YYYY-MM-DD)
 */
export const formatDate = (dateStr) => {
    if (!dateStr) return '-';
    const date = new Date(dateStr);
    return date.toISOString().split('T')[0];
};

/**
 * Get CSS class for status badges (Generic)
 */
export const getStatusBadgeClass = (status) => {
    const map = {
        'PENDING': 'badge_warning',
        'COMPLETED': 'badge_success',
        'CANCELED': 'badge_danger',
        'APPROVED': 'badge_success',
        'REJECTED': 'badge_danger'
    };
    return map[status] || 'badge_secondary';
};

/**
 * Mask resident number (Resident Registration Number)
 * @param {String} rrn 
 */
export const maskResidentNumber = (rrn) => {
    if (!rrn || rrn.length < 8) return rrn;
    return rrn.substring(0, 8) + '******';
};

/**
 * Mask bank account number
 * @param {String} account 
 */
export const maskBankAccount = (account) => {
    if (!account || account.length < 6) return account;
    return account.substring(0, 3) + '***' + account.substring(account.length - 3);
};
