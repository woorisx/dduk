import { hrBackendApi } from './hr-backend-api.js';
import {
    byId,
    money,
    setText,
    safeRun,
    showToast,
    unwrapData,
    getCurrentLoginId
} from './hr-backend-pages-shared.js';

function renderAccountRows(id, rows = []) {
    const tbody = byId(id);
    if (!tbody) {
        return;
    }

    if (!rows.length) {
        tbody.innerHTML = '<tr><td colspan="3">표시할 계정 요약이 없어.</td></tr>';
        return;
    }

    tbody.innerHTML = rows.map((row) => `
        <tr>
            <td>${row.code || '-'}</td>
            <td>${row.name || '-'}</td>
            <td class="amount">${money(row.balance)}</td>
        </tr>
    `).join('');
}

function combineReportAccounts(balanceSheet, profitLoss) {
    return [
        ...(balanceSheet.assets || []).map((item) => ({ ...item, type: '자산' })),
        ...(balanceSheet.liabilities || []).map((item) => ({ ...item, type: '부채' })),
        ...(balanceSheet.equity || []).map((item) => ({ ...item, type: '자본' })),
        ...(profitLoss.revenue || []).map((item) => ({ ...item, type: '수익' })),
        ...(profitLoss.expenses || []).map((item) => ({ ...item, type: '비용' }))
    ];
}

async function loadReports(fiscalYear = null, fiscalMonth = null) {
    const [balanceSheetResponse, profitLossResponse] = await Promise.all([
        hrBackendApi.getBalanceSheet(fiscalYear, fiscalMonth),
        hrBackendApi.getProfitLoss(fiscalYear, fiscalMonth)
    ]);

    return {
        balanceSheet: unwrapData(balanceSheetResponse),
        profitLoss: unwrapData(profitLossResponse)
    };
}

export async function initAccountingDashboard() {
    await safeRun('page_status', async () => {
        const { balanceSheet, profitLoss } = await loadReports();
        setText('kpi_assets', money(balanceSheet.totalAssets));
        setText('kpi_liabilities', money(balanceSheet.totalLiabilities));
        setText('kpi_equity', money(balanceSheet.totalEquity));
        setText('kpi_net_income', money(profitLoss.netIncome));
        setText('balance_state', balanceSheet.isBalanced ? '대차 일치' : '대차 불일치');
        renderAccountRows('asset_rows', balanceSheet.assets);
        renderAccountRows('expense_rows', profitLoss.expenses);
    });
}

export async function initReports() {
    const loadingOverlay = byId('loading_overlay');
    const emptyState = byId('empty_state');
    const tableContainer = byId('report_table_container');

    function showLoading(show) {
        if (loadingOverlay) {
            loadingOverlay.style.display = show ? 'flex' : 'none';
        }
    }

    function showEmpty(show) {
        if (emptyState) {
            emptyState.style.display = show ? 'block' : 'none';
        }
        if (tableContainer) {
            tableContainer.style.display = show ? 'none' : 'block';
        }
    }

    function formatMoney(value) {
        if (value === null || value === undefined || value === '') {
            return '-';
        }
        return money(value);
    }

    function updateTrialBalanceKPIs(data) {
        const totalDebit = Number(data.totalDebit || 0);
        const totalCredit = Number(data.totalCredit || 0);
        setText('kpi_total_assets', formatMoney(totalDebit));
        setText('kpi_total_liabilities', formatMoney(totalCredit));
        setText('kpi_net_income', formatMoney(totalDebit - totalCredit));
        setText('kpi_total_revenue', '-');
        setText('summary_revenue', '-');
        setText('summary_expenses', '-');
        setText('summary_operating_income', '-');

        const status = byId('net_income_status');
        if (status) {
            status.textContent = totalDebit === totalCredit ? '차변 일치' : '차변 불일치';
            status.className = totalDebit === totalCredit ? 'hr_kpi_hint status-positive' : 'hr_kpi_hint status-negative';
        }
    }

    function updateProfitLossSummary(data) {
        setText('summary_revenue', formatMoney(data.totalRevenue));
        setText('summary_expenses', formatMoney(data.totalExpenses));
        setText('summary_operating_income', formatMoney(data.netIncome));
        setText('kpi_total_assets', '-');
        setText('kpi_total_liabilities', '-');
        setText('kpi_net_income', formatMoney(data.netIncome));
        setText('kpi_total_revenue', formatMoney(data.totalRevenue));

        const netIncomeStatus = byId('net_income_status');
        const operatingIncomeBadge = byId('operating_income_badge');
        const isPositive = Number(data.netIncome || 0) >= 0;

        if (netIncomeStatus) {
            netIncomeStatus.textContent = isPositive ? '흑자' : '적자';
            netIncomeStatus.className = isPositive ? 'hr_kpi_hint status-positive' : 'hr_kpi_hint status-negative';
        }

        if (operatingIncomeBadge) {
            operatingIncomeBadge.textContent = isPositive ? '흑자' : '적자';
            operatingIncomeBadge.style.background = isPositive ? '#e8f5e9' : '#ffebee';
            operatingIncomeBadge.style.color = isPositive ? '#2e7d32' : '#c62828';
        }
    }

    function updateBalanceSheetKPIs(data) {
        setText('kpi_total_assets', formatMoney(data.totalAssets));
        setText('kpi_total_liabilities', formatMoney(data.totalLiabilities));
        setText('kpi_net_income', '-');
        setText('kpi_total_revenue', '-');
        setText('summary_revenue', '-');
        setText('summary_expenses', '-');
        setText('summary_operating_income', formatMoney(data.totalEquity));

        const netIncomeStatus = byId('net_income_status');
        if (netIncomeStatus) {
            netIncomeStatus.textContent = data.isBalanced ? '대차 일치' : '대차 불일치';
            netIncomeStatus.className = data.isBalanced ? 'hr_kpi_hint status-positive' : 'hr_kpi_hint status-negative';
        }
    }

    function renderTrialBalance(data) {
        const items = data.items || [];
        const tbody = byId('report_rows');

        if (!items.length) {
            showEmpty(true);
            return;
        }

        showEmpty(false);
        tbody.innerHTML = items.map((item) => `
            <tr>
                <td><code>${item.code || '-'}</code></td>
                <td>${item.name || '-'}</td>
                <td><span class="hr_badge" style="background: #f0f0f0; color: #666;">${item.type || '-'}</span></td>
                <td class="amount">${formatMoney(item.totalDebit)}</td>
                <td class="amount">${formatMoney(item.totalCredit)}</td>
                <td class="amount">${formatMoney(item.balance)}</td>
            </tr>
        `).join('');

        setText('total_debit', formatMoney(data.totalDebit));
        setText('total_credit', formatMoney(data.totalCredit));
        setText('total_balance', formatMoney(Number(data.totalDebit || 0) - Number(data.totalCredit || 0)));
        updateTrialBalanceKPIs(data);
    }

    function renderProfitLoss(data) {
        const tbody = byId('report_rows');
        const allItems = [
            ...(data.revenue || []).map((item) => ({ ...item, section: '매출' })),
            ...(data.expenses || []).map((item) => ({ ...item, section: '비용' }))
        ];

        if (!allItems.length) {
            showEmpty(true);
            return;
        }

        showEmpty(false);
        tbody.innerHTML = allItems.map((item) => `
            <tr>
                <td><code>${item.code || '-'}</code></td>
                <td>${item.name || '-'}</td>
                <td><span class="hr_badge" style="background: ${item.section === '매출' ? '#e3f2fd' : '#ffebee'}; color: ${item.section === '매출' ? '#1976d2' : '#d32f2f'};">${item.section}</span></td>
                <td class="amount">-</td>
                <td class="amount">-</td>
                <td class="amount">${formatMoney(item.balance)}</td>
            </tr>
        `).join('');

        setText('total_debit', '-');
        setText('total_credit', '-');
        setText('total_balance', formatMoney(data.netIncome));
        updateProfitLossSummary(data);
    }

    function renderBalanceSheet(data) {
        const tbody = byId('report_rows');
        const allItems = [
            ...(data.assets || []).map((item) => ({ ...item, section: '자산' })),
            ...(data.liabilities || []).map((item) => ({ ...item, section: '부채' })),
            ...(data.equity || []).map((item) => ({ ...item, section: '자본' }))
        ];

        if (!allItems.length) {
            showEmpty(true);
            return;
        }

        showEmpty(false);
        tbody.innerHTML = allItems.map((item) => `
            <tr>
                <td><code>${item.code || '-'}</code></td>
                <td>${item.name || '-'}</td>
                <td><span class="hr_badge" style="background: #f0f0f0; color: #666;">${item.section}</span></td>
                <td class="amount">-</td>
                <td class="amount">-</td>
                <td class="amount">${formatMoney(item.balance)}</td>
            </tr>
        `).join('');

        setText('total_debit', '-');
        setText('total_credit', '-');
        setText('total_balance', formatMoney(data.totalAssets));
        updateBalanceSheetKPIs(data);
    }

    async function renderSelectedReport() {
        const year = byId('filter_year')?.value || null;
        const month = byId('filter_month')?.value || null;
        const reportType = byId('report_type')?.value || 'trial-balance';
        const fiscalYear = year ? Number(year) : null;
        const fiscalMonth = month ? Number(month) : null;

        showLoading(true);
        try {
            let data;
            if (reportType === 'trial-balance') {
                data = unwrapData(await hrBackendApi.getTrialBalance(fiscalYear, fiscalMonth));
                setText('report_title', '시산표');
                setText('report_badge', 'Trial Balance');
                renderTrialBalance(data);
            } else if (reportType === 'profit-loss') {
                data = unwrapData(await hrBackendApi.getProfitLoss(fiscalYear, fiscalMonth));
                setText('report_title', '손익계산서');
                setText('report_badge', 'Profit & Loss');
                renderProfitLoss(data);
            } else {
                data = unwrapData(await hrBackendApi.getBalanceSheet(fiscalYear, fiscalMonth));
                setText('report_title', '재무상태표');
                setText('report_badge', 'Balance Sheet');
                renderBalanceSheet(data);
            }

            showToast('리포트 조회 완료', 'success');
        } catch (error) {
            console.error('Failed to load reports:', error);
            showEmpty(true);
            showToast(error.message || '리포트 조회 실패', 'error');
        } finally {
            showLoading(false);
        }
    }

    byId('btn_apply_filter')?.addEventListener('click', renderSelectedReport);
    byId('btn_refresh_reports')?.addEventListener('click', renderSelectedReport);
    byId('report_type')?.addEventListener('change', renderSelectedReport);

    await renderSelectedReport();
}

function readJournalItems() {
    return Array.from(document.querySelectorAll('.journal_item_row')).map((row) => ({
        accountCode: row.querySelector('[data-field="accountCode"]').value.trim(),
        side: row.querySelector('[data-field="side"]').value,
        amount: Number(row.querySelector('[data-field="amount"]').value || 0)
    })).filter((item) => item.accountCode && item.amount > 0);
}

function addJournalRow() {
    const container = byId('journal_items');
    if (!container) {
        return;
    }

    const row = document.createElement('div');
    row.className = 'journal_item_row';
    row.innerHTML = `
        <div class="hr_field">
            <label>계정 코드</label>
            <input class="hr_input" data-field="accountCode" placeholder="예: 5100">
        </div>
        <div class="hr_field">
            <label>차변 구분</label>
            <select class="hr_select" data-field="side">
                <option value="DEBIT">차변</option>
                <option value="CREDIT">대변</option>
            </select>
        </div>
        <div class="hr_field">
            <label>금액</label>
            <input class="hr_input" data-field="amount" type="number" min="0" step="1">
        </div>
        <button class="hr_button" type="button" data-remove-row>제거</button>
    `;

    row.querySelector('[data-remove-row]')?.addEventListener('click', () => {
        row.remove();
        updateJournalBalances();
    });
    container.appendChild(row);
}

function updateJournalBalances() {
    const items = readJournalItems();
    let totalDebit = 0;
    let totalCredit = 0;

    items.forEach((item) => {
        if (item.side === 'DEBIT') {
            totalDebit += item.amount;
        } else {
            totalCredit += item.amount;
        }
    });

    setText('sum_debit', money(totalDebit));
    setText('sum_credit', money(totalCredit));

    const diff = totalDebit - totalCredit;
    const diffDisplay = byId('diff_display');
    if (diffDisplay) {
        diffDisplay.textContent = diff === 0
            ? ''
            : `차액: ${money(Math.abs(diff))} ${diff > 0 ? '(차변 초과)' : '(대변 초과)'}`;
        diffDisplay.style.color = diff > 0 ? '#dc3545' : '#ffc107';
    }

    const isBalanced = totalDebit > 0 && totalDebit === totalCredit && items.length >= 2;
    const badge = byId('balance_badge');
    if (badge) {
        badge.textContent = isBalanced ? '대차 일치' : '차변 불일치';
        badge.className = `hr_badge ${isBalanced ? 'ok' : 'warn'}`;
    }

    const message = byId('validation_msg');
    if (message) {
        if (items.length < 2) {
            message.textContent = '전표 라인은 최소 2개 이상이어야 해.';
        } else if (totalDebit === 0 || totalCredit === 0) {
            message.textContent = '차변 또는 대변 합계가 0이야.';
        } else if (!isBalanced) {
            message.textContent = '차변 금액이 일치하지 않아.';
        } else {
            message.textContent = '전표를 생성할 수 있어.';
        }
        message.className = isBalanced ? 'hr_text_ok' : 'hr_text_error';
    }

    const submitButton = byId('btn_submit_journal');
    if (submitButton) {
        submitButton.disabled = !isBalanced;
    }
}

async function renderJournalList() {
    try {
        const statusFilter = byId('status_filter')?.value || '';
        const response = await hrBackendApi.getJournals(statusFilter ? { status: statusFilter } : {});
        const journals = unwrapData(response) || [];
        const tbody = byId('journal_list_rows');
        if (!tbody) {
            return;
        }

        if (!journals.length) {
            tbody.innerHTML = '<tr><td colspan="7">표시할 전표가 없어.</td></tr>';
            return;
        }

        tbody.innerHTML = journals.map((journal) => {
            const statusClass = {
                DRAFT: 'status-draft',
                POSTED: 'status-posted',
                CANCELLED: 'status-cancelled'
            }[journal.status] || '';

            return `
                <tr style="cursor: pointer;" onclick="handleJournalAction(${journal.id}, 'view')">
                    <td>${journal.transactionDate || '-'}</td>
                    <td><code>${journal.journalNo || '-'}</code></td>
                    <td>${journal.description || '-'}</td>
                    <td class="amount">${money(journal.totalDebit)}</td>
                    <td class="amount">${money(journal.totalCredit)}</td>
                    <td><span class="hr_badge ${statusClass}">${journal.status}</span></td>
                    <td>
                        <div class="hr_actions" style="gap: 4px;" onclick="event.stopPropagation()">
                            ${journal.status === 'DRAFT' ? `<button class="hr_button small primary" onclick="handleJournalAction(${journal.id}, 'post')">기표</button>` : ''}
                            ${journal.status === 'POSTED' ? `<button class="hr_button small warn" onclick="handleJournalAction(${journal.id}, 'cancel')">취소</button>` : ''}
                            ${journal.status === 'DRAFT' ? `<button class="hr_button small error" onclick="handleJournalAction(${journal.id}, 'delete')">삭제</button>` : ''}
                        </div>
                    </td>
                </tr>
            `;
        }).join('');

        if (window.lucide) {
            lucide.createIcons();
        }
    } catch (error) {
        console.error('Failed to load journals:', error);
        showToast(error.message || '전표 목록 로드 실패', 'error');
    }
}

async function openJournalModal(id) {
    try {
        const response = await hrBackendApi.getJournals();
        const journals = unwrapData(response) || [];
        const journal = journals.find((item) => item.id === id);
        if (!journal) {
            throw new Error('전표 정보를 찾을 수 없어.');
        }

        setText('detail_journal_no', journal.journalNo);
        setText('detail_date', journal.transactionDate);
        setText('detail_desc', journal.description);

        const status = byId('detail_status');
        if (status) {
            const statusClass = {
                DRAFT: 'status-draft',
                POSTED: 'status-posted',
                CANCELLED: 'status-cancelled'
            }[journal.status] || '';
            status.textContent = journal.status;
            status.className = `hr_badge ${statusClass}`;
        }

        const tbody = byId('detail_line_rows');
        if (tbody) {
            tbody.innerHTML = (journal.lines || []).map((line) => `
                <tr>
                    <td>${line.account?.code || '-'}</td>
                    <td>${line.account?.name || '-'}</td>
                    <td class="amount">${money(line.debitAmount)}</td>
                    <td class="amount">${money(line.creditAmount)}</td>
                    <td>${line.description || '-'}</td>
                </tr>
            `).join('');
        }

        setText('detail_sum_debit', money(journal.totalDebit));
        setText('detail_sum_credit', money(journal.totalCredit));
        byId('journal_detail_modal')?.style.setProperty('display', 'flex');
    } catch (error) {
        showToast(error.message || '전표 상세 조회 실패', 'error');
    }
}

window.handleJournalAction = async function handleJournalAction(id, action) {
    if (action === 'view') {
        await openJournalModal(id);
        return;
    }

    const messages = {
        post: `이 전표(${id})를 기표할까? 기표 이후에는 수정할 수 없어.`,
        cancel: `이 전표(${id})를 취소할까?`,
        delete: `이 전표(${id})를 삭제할까?`
    };

    if (!confirm(messages[action] || '계속할까?')) {
        return;
    }

    try {
        if (action === 'post') {
            await hrBackendApi.postJournal(id);
        } else if (action === 'cancel') {
            await hrBackendApi.cancelJournal(id);
        } else if (action === 'delete') {
            await hrBackendApi.deleteJournal(id);
        }

        showToast('전표 상태가 변경됐어.', 'success');
        await renderJournalList();
    } catch (error) {
        showToast(error.message || '전표 상태 변경 실패', 'error');
    }
};

window.closeJournalModal = function closeJournalModal() {
    byId('journal_detail_modal')?.style.setProperty('display', 'none');
};

function initVoucherManagementLogic() {
    const tableBody = byId('voucherLinesBody');
    const balanceDisplay = byId('voucher_balance_display');

    function calculateVatAmount(supplyAmount, vatType) {
        if (vatType === 'TAX_FREE') {
            return 0;
        }
        return Math.floor(supplyAmount * 0.1);
    }

    function calculateTotalAmount(supplyAmount, vatAmount) {
        return supplyAmount + vatAmount;
    }

    function updateTotals() {
        let totalDebit = 0;
        let totalCredit = 0;

        document.querySelectorAll('#voucherLinesBody tr').forEach((row) => {
            const side = row.querySelector('[data-field="accountSide"]').value;
            const total = Number(row.querySelector('[data-field="totalAmount"]').value) || 0;
            if (side === 'DEBIT') {
                totalDebit += total;
            } else if (side === 'CREDIT') {
                totalCredit += total;
            }
        });

        if (balanceDisplay) {
            balanceDisplay.textContent = `차변 ${money(totalDebit)} - 대변 ${money(totalCredit)}`;
            balanceDisplay.style.color = totalDebit > 0 && totalDebit === totalCredit ? '#2e7d32' : '#d32f2f';
        }
    }

    function updateRowAmounts(row) {
        const supplyInput = row.querySelector('[data-field="supplyAmount"]');
        const vatInput = row.querySelector('[data-field="vatAmount"]');
        const totalInput = row.querySelector('[data-field="totalAmount"]');
        const supplyAmount = Number(supplyInput.value) || 0;
        let vatAmount = Number(vatInput.value) || 0;
        const currentVatType = byId('vatType').value;
        const expectedVat = calculateVatAmount(supplyAmount, currentVatType);

        if (vatAmount === 0 && supplyAmount > 0) {
            vatAmount = expectedVat;
            vatInput.value = vatAmount;
        }

        totalInput.value = calculateTotalAmount(supplyAmount, vatAmount);
        updateTotals();
    }

    function addVoucherLine() {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td><input type="text" data-field="accountCode" class="hr_input" placeholder="예: 5100" required></td>
            <td>
                <select data-field="accountSide" class="hr_input hr_select">
                    <option value="DEBIT">차변</option>
                    <option value="CREDIT">대변</option>
                </select>
            </td>
            <td><input type="number" data-field="supplyAmount" class="hr_input amount-cell" value="0"></td>
            <td><input type="number" data-field="vatAmount" class="hr_input amount-cell" value="0"></td>
            <td><input type="number" data-field="totalAmount" class="hr_input amount-cell" value="0" readonly style="background:#f5f5f5;"></td>
            <td><input type="text" data-field="description" class="hr_input" placeholder="적요"></td>
            <td><button type="button" class="hr_button small warn" data-action="removeLine">제거</button></td>
        `;

        tr.querySelectorAll('input[type="number"]').forEach((input) => {
            input.addEventListener('input', () => updateRowAmounts(tr));
        });

        tr.querySelector('[data-action="removeLine"]').addEventListener('click', () => {
            tr.remove();
            updateTotals();
        });

        tableBody.appendChild(tr);
        updateTotals();
    }

    byId('btn_add_voucher_line')?.addEventListener('click', addVoucherLine);
    byId('vatType')?.addEventListener('change', () => {
        document.querySelectorAll('#voucherLinesBody tr').forEach((row) => {
            const vatInput = row.querySelector('[data-field="vatAmount"]');
            vatInput.value = 0;
            updateRowAmounts(row);
        });
    });

    byId('voucherForm')?.addEventListener('submit', async (event) => {
        event.preventDefault();
        safeRun('page_status', async () => {
            const lines = [];
            document.querySelectorAll('#voucherLinesBody tr').forEach((row) => {
                lines.push({
                    accountCode: row.querySelector('[data-field="accountCode"]').value,
                    accountSide: row.querySelector('[data-field="accountSide"]').value,
                    supplyAmount: Number(row.querySelector('[data-field="supplyAmount"]').value) || 0,
                    vatAmount: Number(row.querySelector('[data-field="vatAmount"]').value) || 0,
                    description: row.querySelector('[data-field="description"]').value
                });
            });

            if (lines.length === 0) {
                throw new Error('전표 라인을 하나 이상 입력해줘.');
            }

            const payload = {
                voucherType: byId('voucherType').value,
                voucherDate: byId('voucherDate').value,
                vendorId: Number(byId('vendorId').value) || null,
                vendorName: byId('vendorName').value,
                vatType: byId('vatType').value,
                lines
            };

            await hrBackendApi.createVoucher(payload);
            showToast('전표가 저장(임시 기표)됐어.', 'success');
            document.getElementById('voucher_management_card').style.display = 'none';
            await renderJournalList();
        });
    });

    addVoucherLine();
    addVoucherLine();
}

export function initJournalPage() {
    byId('status_filter')?.addEventListener('change', renderJournalList);
    byId('btn_refresh_journals')?.addEventListener('click', renderJournalList);
    byId('btn_add_journal_row')?.addEventListener('click', addJournalRow);
    byId('journal_items')?.addEventListener('input', updateJournalBalances);
    byId('journal_form')?.addEventListener('submit', async (event) => {
        event.preventDefault();
        await safeRun('page_status', async () => {
            const payload = {
                transactionDate: byId('transaction_date')?.value,
                description: byId('description')?.value,
                createdBy: getCurrentLoginId({ allowSystem: true }),
                items: readJournalItems()
            };

            if (!payload.transactionDate || !payload.description) {
                throw new Error('전표 일자와 설명을 입력해줘.');
            }

            if (payload.items.length < 2) {
                throw new Error('전표 라인은 최소 2개 이상 필요해.');
            }

            await hrBackendApi.createJournal(payload);
            showToast('전표를 저장했어.', 'success');
            await renderJournalList();
        });
    });

    const container = byId('voucher_form_container');
    if (container) {
        fetch('voucher_management.html')
            .then((res) => res.text())
            .then((html) => {
                container.innerHTML = html;
                initVoucherManagementLogic();
            })
            .catch((error) => console.error('Failed to load voucher_management.html', error));
    }

    byId('btn_toggle_form')?.addEventListener('click', () => {
        const formSection = document.getElementById('form_section');
        if (formSection) {
            formSection.classList.toggle('active');
        }
        const card = document.getElementById('voucher_management_card');
        if (card) {
            card.style.display = 'block';
        }
    });

    renderJournalList();
}

export async function initTrialBalance() {
    await safeRun('page_status', async () => {
        const { balanceSheet, profitLoss } = await loadReports();
        const rows = combineReportAccounts(balanceSheet, profitLoss);
        const tbody = byId('trial_rows');
        if (!tbody) {
            return;
        }

        tbody.innerHTML = rows.map((row) => `
            <tr>
                <td>${row.code || '-'}</td>
                <td>${row.name || '-'}</td>
                <td><span class="hr_badge">${row.type}</span></td>
                <td class="amount">${money(row.balance)}</td>
            </tr>
        `).join('') || '<tr><td colspan="4">표시할 시산표 데이터가 없어.</td></tr>';

        setText('trial_total', money(rows.reduce((sum, row) => sum + Number(row.balance || 0), 0)));
    });
}

async function renderPeriodList() {
    try {
        const response = await hrBackendApi.getPeriods();
        const periods = unwrapData(response) || [];
        const tbody = byId('period_list_rows');
        if (!tbody) {
            return;
        }

        const yearFilter = byId('year_filter')?.value || '';
        const statusFilter = byId('status_filter')?.value || '';

        let filtered = periods;
        if (yearFilter) {
            filtered = filtered.filter((period) => String(period.fiscalYear) === yearFilter);
        }
        if (statusFilter) {
            filtered = filtered.filter((period) => period.status === statusFilter);
        }

        const yearSelect = byId('year_filter');
        if (yearSelect) {
            const years = [...new Set(periods.map((period) => period.fiscalYear))].sort((a, b) => b - a);
            const currentValue = yearSelect.value;
            yearSelect.innerHTML = '<option value="">전체 연도</option>'
                + years.map((year) => `<option value="${year}" ${String(year) === currentValue ? 'selected' : ''}>${year}년</option>`).join('');
        }

        if (!filtered.length) {
            tbody.innerHTML = '<tr><td colspan="7">표시할 회계 기간이 없어.</td></tr>';
            return;
        }

        tbody.innerHTML = filtered.map((period) => {
            const periodKey = `${period.fiscalYear}-${String(period.fiscalMonth).padStart(2, '0')}`;
            const isOpen = period.status === 'OPEN';
            return `
                <tr>
                    <td>${period.fiscalYear}</td>
                    <td>${period.fiscalMonth}</td>
                    <td><code>${periodKey}</code></td>
                    <td><span class="hr_badge ${isOpen ? 'status-open' : 'status-closed'}">${period.status}</span></td>
                    <td>${period.closedAt ? period.closedAt.replace('T', ' ').slice(0, 16) : '-'}</td>
                    <td>${period.closedBy || '-'}</td>
                    <td>
                        <div class="hr_actions" style="gap: 4px;">
                            ${isOpen ? `<button class="hr_button small primary" onclick="handlePeriodAction('${periodKey}', 'close')">마감</button>` : ''}
                            ${!isOpen ? `<button class="hr_button small warn" onclick="handlePeriodAction('${periodKey}', 'reopen')">재오픈</button>` : ''}
                        </div>
                    </td>
                </tr>
            `;
        }).join('');
    } catch (error) {
        console.error('Failed to load periods:', error);
        showToast(error.message || '회계 기간 목록 로드 실패', 'error');
    }
}

window.handlePeriodAction = async function handlePeriodAction(periodKey, action) {
    const sessionLoginId = getCurrentLoginId({ allowSystem: true });
    const messages = {
        close: `이 기간(${periodKey})을 마감할까? 마감 후에는 전표 생성과 기표가 불가해.`,
        reopen: `이 기간(${periodKey})을 재오픈할까?`
    };

    if (!confirm(messages[action] || '계속할까?')) {
        return;
    }

    try {
        if (action === 'close') {
            await hrBackendApi.closePeriod(periodKey, { closedBy: sessionLoginId });
            showToast('회계 기간을 마감했어.', 'success');
        } else if (action === 'reopen') {
            await hrBackendApi.reopenPeriod(periodKey, { reopenedBy: sessionLoginId });
            showToast('회계 기간을 재오픈했어.', 'success');
        }
        await renderPeriodList();
    } catch (error) {
        showToast(error.message || '회계 기간 상태 변경 실패', 'error');
    }
};

export async function initSettlement() {
    await renderPeriodList();

    byId('year_filter')?.addEventListener('change', renderPeriodList);
    byId('status_filter')?.addEventListener('change', renderPeriodList);
    byId('btn_refresh_settlement')?.addEventListener('click', renderPeriodList);
}
