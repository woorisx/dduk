/**
 * reports.js — 재무제표(시산표, 손익계산서, 재무상태표) 페이지 전용 스크립트
 * API: /api/v1/accounting/reports/trial-balance
 *      /api/v1/accounting/reports/profit-loss
 *      /api/v1/accounting/reports/balance-sheet
 */

(function () {
    const moneyFormatter = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 0 });

    const state = {
        year: '',
        month: '',
        reportType: 'balance-sheet'
    };

    document.addEventListener('DOMContentLoaded', () => {
        // 기본 회계연도 2026 설정
        const yearFilter = document.getElementById('filter_year');
        if (yearFilter) yearFilter.value = '2026';

        bindEvents();
        renderSelectedReport();
    });

    function bindEvents() {
        const applyBtn = document.getElementById('btn_apply_filter');
        if (applyBtn) {
            applyBtn.addEventListener('click', () => {
                updateFilters();
                renderSelectedReport();
            });
        }

        const refreshBtn = document.getElementById('btn_refresh_reports');
        if (refreshBtn) {
            refreshBtn.addEventListener('click', () => {
                renderSelectedReport();
            });
        }

        const reportTypeSelect = document.getElementById('report_type');
        if (reportTypeSelect) {
            reportTypeSelect.addEventListener('change', () => {
                state.reportType = reportTypeSelect.value;
                updateSegmentedTabs();
                renderSelectedReport();
            });
        }

        // 탭 버튼 클릭 이벤트 바인딩
        document.querySelectorAll('#reportTabs .segmented_tab').forEach(tab => {
            tab.addEventListener('click', () => {
                const targetTab = tab.getAttribute('data-tab');
                state.reportType = targetTab;
                if (reportTypeSelect) reportTypeSelect.value = targetTab;
                updateSegmentedTabs();
                renderSelectedReport();
            });
        });
    }

    function updateFilters() {
        state.year = document.getElementById('filter_year')?.value || '';
        state.month = document.getElementById('filter_month')?.value || '';
        state.reportType = document.getElementById('report_type')?.value || 'balance-sheet';
    }

    function updateSegmentedTabs() {
        document.querySelectorAll('#reportTabs .segmented_tab').forEach(tab => {
            tab.classList.toggle('active', tab.getAttribute('data-tab') === state.reportType);
        });
    }

    function showLoading(show) {
        const overlay = document.getElementById('loading_overlay');
        if (overlay) {
            overlay.style.display = show ? 'flex' : 'none';
        }
    }

    function showEmpty(show) {
        const emptyState = document.getElementById('empty_state');
        const container = document.getElementById('report_table_container');
        if (emptyState) emptyState.style.display = show ? 'flex' : 'none';
        if (container) container.style.display = show ? 'none' : 'block';
    }

    function formatMoney(value) {
        if (value === null || value === undefined || value === '') return '-';
        return `₩${moneyFormatter.format(Number(value || 0))}`;
    }

    async function renderSelectedReport() {
        showLoading(true);
        updateFilters();

        const params = new URLSearchParams();
        if (state.year) params.set('fiscalYear', state.year);
        if (state.month) params.set('fiscalMonth', state.month);

        let apiUrl = '';
        if (state.reportType === 'profit-loss') {
            apiUrl = `/api/v1/accounting/reports/profit-loss`;
        } else {
            apiUrl = `/api/v1/accounting/reports/balance-sheet`;
        }

        try {
            const res = await window.ddukApi.get(`${apiUrl}?${params.toString()}`);
            const data = res.data || {};

            if (state.reportType === 'profit-loss') {
                document.getElementById('report_title').textContent = '손익계산서';
                document.getElementById('report_badge').textContent = 'Profit & Loss';
                renderProfitLoss(data);
            } else {
                document.getElementById('report_title').textContent = '재무상태표';
                document.getElementById('report_badge').textContent = 'Balance Sheet';
                renderBalanceSheet(data);
            }
            showToast('성공적으로 조회되었습니다.', 'success');
        } catch (err) {
            console.error(err);
            showEmpty(true);
            showToast(err.message || '보고서 조회에 실패했습니다.', 'error');
        } finally {
            showLoading(false);
        }
    }


    function renderProfitLoss(data) {
        const tbody = document.getElementById('report_rows');
        if (!tbody) return;

        const allItems = [
            ...(data.revenue || []).map(item => ({ ...item, section: '매출', sectionClass: 'success' })),
            ...(data.expenses || []).map(item => ({ ...item, section: '비용', sectionClass: 'danger' }))
        ];

        if (!allItems.length) {
            showEmpty(true);
            return;
        }

        showEmpty(false);
        tbody.innerHTML = allItems.map(item => `
            <tr>
                <td style="font-family: monospace; font-weight: 600;">${escHtml(item.code)}</td>
                <td style="font-weight: 500;">${escHtml(item.name)}</td>
                <td><span class="status_badge ${item.sectionClass}">${escHtml(item.section)}</span></td>
                <td class="amount-cell">-</td>
                <td class="amount-cell">-</td>
                <td class="amount-cell" style="font-weight: 600;">${formatMoney(item.balance)}</td>
            </tr>
        `).join('');

        document.getElementById('total_debit').textContent = '-';
        document.getElementById('total_credit').textContent = '-';
        document.getElementById('total_balance').textContent = formatMoney(data.netIncome);

        // KPI 및 하단 카드
        document.getElementById('kpi_total_assets').textContent = '-';
        document.getElementById('kpi_total_liabilities').textContent = '-';
        document.getElementById('kpi_net_income').textContent = formatMoney(data.netIncome);
        document.getElementById('kpi_total_revenue').textContent = formatMoney(data.totalRevenue);

        document.getElementById('summary_revenue').textContent = formatMoney(data.totalRevenue);
        document.getElementById('summary_expenses').textContent = formatMoney(data.totalExpenses);
        document.getElementById('summary_operating_income').textContent = formatMoney(data.netIncome);

        const isPositive = Number(data.netIncome || 0) >= 0;
        const hintEl = document.getElementById('net_income_status');
        if (hintEl) {
            hintEl.textContent = isPositive ? '당기순이익 흑자' : '당기순손실 발생';
            hintEl.className = isPositive ? 'kpi_hint status-positive' : 'kpi_hint status-negative';
        }

        const badgeEl = document.getElementById('operating_income_badge');
        if (badgeEl) {
            badgeEl.textContent = isPositive ? '흑자' : '적자';
            badgeEl.className = isPositive ? 'status_badge success' : 'status_badge danger';
        }
    }

    function renderBalanceSheet(data) {
        const tbody = document.getElementById('report_rows');
        if (!tbody) return;

        const allItems = [
            ...(data.assets || []).map(item => ({ ...item, section: '자산', sectionClass: 'info' })),
            ...(data.liabilities || []).map(item => ({ ...item, section: '부채', sectionClass: 'warning' })),
            ...(data.equity || []).map(item => ({ ...item, section: '자본', sectionClass: 'success' }))
        ];

        if (!allItems.length) {
            showEmpty(true);
            return;
        }

        showEmpty(false);
        tbody.innerHTML = allItems.map(item => `
            <tr>
                <td style="font-family: monospace; font-weight: 600;">${escHtml(item.code)}</td>
                <td style="font-weight: 500;">${escHtml(item.name)}</td>
                <td><span class="status_badge ${item.sectionClass}">${escHtml(item.section)}</span></td>
                <td class="amount-cell">-</td>
                <td class="amount-cell">-</td>
                <td class="amount-cell" style="font-weight: 600;">${formatMoney(item.balance)}</td>
            </tr>
        `).join('');

        document.getElementById('total_debit').textContent = '-';
        document.getElementById('total_credit').textContent = '-';
        document.getElementById('total_balance').textContent = formatMoney(data.totalAssets);

        // KPI 및 하단 카드
        document.getElementById('kpi_total_assets').textContent = formatMoney(data.totalAssets);
        document.getElementById('kpi_total_liabilities').textContent = formatMoney(data.totalLiabilities);
        document.getElementById('kpi_net_income').textContent = '-';
        document.getElementById('kpi_total_revenue').textContent = '-';

        document.getElementById('summary_revenue').textContent = '-';
        document.getElementById('summary_expenses').textContent = '-';
        document.getElementById('summary_operating_income').textContent = formatMoney(data.totalEquity);

        const hintEl = document.getElementById('net_income_status');
        if (hintEl) {
            hintEl.textContent = data.isBalanced ? '차대 균형 일치' : '차대 균형 불일치';
            hintEl.className = data.isBalanced ? 'kpi_hint status-positive' : 'kpi_hint status-negative';
        }
    }

    function showToast(message, type = 'success') {
        const toast = document.getElementById('toast');
        if (!toast) return;
        toast.textContent = message;
        toast.className = `toast show ${type}`;
        setTimeout(() => {
            toast.className = 'toast';
        }, 2600);
    }

    function escHtml(value) {
        return String(value ?? '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }
})();
