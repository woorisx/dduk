import { initShell, wireRefresh } from './hr-backend-pages-shared.js';
import {
    initAccountingDashboard,
    initReports,
    initJournalPage,
    initTrialBalance,
    initSettlement
} from './hr-backend-pages-accounting.js';
import {
    initPayrollCalculate,
    initPayrollDetail,
    initReconciliation
} from './hr-backend-pages-payroll.js';

const page = document.body.dataset.hrPage;

function bindCommonRefresh() {
    wireRefresh('btn_refresh_dashboard', initAccountingDashboard);
    wireRefresh('btn_refresh_trial', initTrialBalance);
    wireRefresh('btn_refresh_recon', initReconciliation);
}

async function init() {
    if (!initShell()) {
        return;
    }

    bindCommonRefresh();

    if (page === 'accounting-dashboard') await initAccountingDashboard();
    if (page === 'accounting-reports') await initReports();
    if (page === 'accounting-transactions') initJournalPage();
    if (page === 'accounting-trial-balance') await initTrialBalance();
    if (page === 'accounting-settlement') await initSettlement();
    if (page === 'payroll-list') initPayrollCalculate();
    if (page === 'payroll-detail') initPayrollDetail();
    if (page === 'payroll-reconciliation') await initReconciliation();
}

init();
