/**
 * Accounting Master Data Service
 */

export const ACCOUNT_TYPE = {
    ASSET: 'ASSET',
    LIABILITY: 'LIABILITY',
    EQUITY: 'EQUITY',
    REVENUE: 'REVENUE',
    EXPENSE: 'EXPENSE'
};

export class AccountingMasterService {
    constructor() {
        this.coa = [
            { code: '1000', name: '현금', type: ACCOUNT_TYPE.ASSET, level: 1 },
            { code: '1100', name: '보통예금', type: ACCOUNT_TYPE.ASSET, level: 1 },
            { code: '2000', name: '미지급금', type: ACCOUNT_TYPE.LIABILITY, level: 1 },
            { code: '2100', name: '예수금', type: ACCOUNT_TYPE.LIABILITY, level: 1 },
            { code: '3000', name: '자본금', type: ACCOUNT_TYPE.EQUITY, level: 1 },
            { code: '4000', name: '매출액', type: ACCOUNT_TYPE.REVENUE, level: 1 },
            { code: '5000', name: '급여비용', type: ACCOUNT_TYPE.EXPENSE, level: 1 },
            { code: '5100', name: '판매관리비', type: ACCOUNT_TYPE.EXPENSE, level: 1 }
        ];
        
        this.fiscalPeriods = [
            { yearMonth: '2026-05', status: 'OPEN' },
            { yearMonth: '2026-04', status: 'CLOSED' }
        ];
    }

    getCOA() { return this.coa; }
    
    getAccountByCode(code) {
        return this.coa.find(a => a.code === code);
    }

    isPeriodOpen(yearMonth) {
        const period = this.fiscalPeriods.find(p => p.yearMonth === yearMonth);
        return period && period.status === 'OPEN';
    }
}

export const accountingMasterService = new AccountingMasterService();
