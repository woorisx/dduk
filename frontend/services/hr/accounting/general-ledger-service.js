/**
 * General Ledger (GL) Service
 */
import { accountingMasterService, ACCOUNT_TYPE } from './accounting-master-service.js';

export class GeneralLedgerService {
    constructor() {
        this.balances = new Map(); // Map<accountCode, amount>
    }

    /**
     * Post an entry to the ledger
     */
    async postEntry(accountCode, amount, side) {
        // side: DEBIT (차변), CREDIT (대변)
        const account = accountingMasterService.getAccountByCode(accountCode);
        if (!account) throw new Error(`Invalid account code: ${accountCode}`);

        const currentBalance = this.balances.get(accountCode) || 0;
        let newBalance = currentBalance;

        // Balance increase logic based on account type
        // Assets/Expenses increase on Debit
        // Liabilities/Equity/Revenue increase on Credit
        if (side === 'DEBIT') {
            if ([ACCOUNT_TYPE.ASSET, ACCOUNT_TYPE.EXPENSE].includes(account.type)) newBalance += amount;
            else newBalance -= amount;
        } else {
            if ([ACCOUNT_TYPE.LIABILITY, ACCOUNT_TYPE.EQUITY, ACCOUNT_TYPE.REVENUE].includes(account.type)) newBalance += amount;
            else newBalance -= amount;
        }

        this.balances.set(accountCode, newBalance);
    }

    /**
     * Get Trial Balance (합계잔액시산표)
     */
    getTrialBalance() {
        const coa = accountingMasterService.getCOA();
        return coa.map(acc => ({
            ...acc,
            balance: this.balances.get(acc.code) || 0
        }));
    }
}

export const generalLedgerService = new GeneralLedgerService();
