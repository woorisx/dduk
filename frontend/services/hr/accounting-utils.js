/**
 * Accounting Utilities (Proxy to Common Formatters)
 */
import * as common from '../common/formatters.js';

export const formatCurrency = common.formatCurrency;
export const formatNumber = common.formatNumber;
export const formatCompactNumber = common.formatCompactNumber;
export const formatDate = common.formatDate;
export const getStatusBadgeClass = common.getStatusBadgeClass;

// Accounting Specific Calculation Logic
export const calculateVat = (supplyPrice) => Math.floor(supplyPrice * 0.1);
export const calculateTotalAmount = (supplyPrice, vat) => supplyPrice + vat;
