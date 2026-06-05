/**
 * transactions.js — 거래내역 등록 페이지 전용 스크립트
 * API: /api/v1/accounting/vouchers (GET, PATCH)
 *      /api/v1/accounting/dashboard/vouchers (GET, KPI용)
 */

(function () {
    const VOUCHER_API = '/api/v1/accounting/vouchers';
    const SUMMARY_API = '/api/v1/accounting/vouchers/summary';

    const money = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 0 });

    const state = {
        statusFilter: '',
        keyword: ''
    };

    document.addEventListener('DOMContentLoaded', () => {
        bindEvents();
        loadKpis();
        loadVouchers();
    });

    /* ─── 이벤트 바인딩 ─────────────────────────────────────── */
    function bindEvents() {
        const statusFilter = document.getElementById('status_filter');
        if (statusFilter) {
            statusFilter.addEventListener('change', () => {
                state.statusFilter = statusFilter.value;
                loadVouchers();
            });
        }

        const searchInput = document.getElementById('search_input');
        if (searchInput) {
            let debounce;
            searchInput.addEventListener('input', () => {
                clearTimeout(debounce);
                debounce = setTimeout(() => {
                    state.keyword = searchInput.value.trim();
                    loadVouchers();
                }, 300);
            });
        }
    }

    /* ─── KPI 카드 조회 ─────────────────────────────────────── */
    async function loadKpis() {
        try {
            const body = await window.ddukApi.get(SUMMARY_API);
            const summary = body.data || {};

            // 오늘 등록된 전표 수 (VoucherSummaryResponse.todayCount)
            setKpi('kpi_today_count', summary.todayCount);
            // POSTED 전표 수
            setKpi('kpi_posted_count', summary.postedCount);
            // DRAFT 전표 수
            setKpi('kpi_draft_count', summary.draftCount);
            // 총 거래 금액 (전체 차변 합계)
            setKpiText('kpi_total_amount', formatWon(summary.totalAmount || 0));
        } catch (err) {
            console.warn('[transactions] KPI 조회 실패:', err.message);
        }
    }

    /* ─── 거래내역(전표) 목록 조회 ──────────────────────────── */
    async function loadVouchers() {
        const tbody = document.getElementById('transaction_list');
        if (!tbody) return;

        const loader = document.getElementById('tableLoader');

        tbody.innerHTML = `
            <tr>
                <td colspan="8" style="text-align: center; padding: 32px; color: var(--text-muted);">
                    <span>거래내역을 조회하고 있습니다...</span>
                </td>
            </tr>
        `;

        try {
            if (loader) loader.style.display = 'flex';

            const params = new URLSearchParams();
            if (state.statusFilter) params.set('status', state.statusFilter);
            if (state.keyword) params.set('keyword', state.keyword);

            const body = await window.ddukApi.get(`${VOUCHER_API}?${params.toString()}`);
            const vouchers = body.data || [];

            if (!vouchers.length) {
                tbody.innerHTML = `
                    <tr>
                        <td colspan="8" style="text-align: center; padding: 32px; color: var(--text-muted);">
                            조회된 거래내역이 없습니다.
                        </td>
                    </tr>
                `;
                return;
            }

            tbody.innerHTML = vouchers.map(v => `
                <tr>
                    <td>
                        <a class="link-cell" href="./voucher_management.html" title="${escHtml(v.voucherNo)}">
                            ${escHtml(v.voucherNo || '-')}
                        </a>
                    </td>
                    <td>${escHtml(v.voucherDate || '-')}</td>
                    <td>${escHtml(v.vendorNameSnapshot || '-')}</td>
                    <td>${voucherTypeLabel(v.voucherType)}</td>
                    <td class="amount-cell">${debitTotal(v.lines)}</td>
                    <td class="amount-cell">${creditTotal(v.lines)}</td>
                    <td><span class="status_badge ${statusClass(v.status)}">${escHtml(v.status || '-')}</span></td>
                    <td style="text-align: center;">
                        <button class="erp_btn erp_btn_secondary erp_btn_icon action-menu" title="상세보기" data-id="${v.id}">
                            <i data-lucide="eye"></i>
                        </button>
                    </td>
                </tr>
            `).join('');

            // 상세 보기 이벤트 바인딩
            tbody.querySelectorAll('.action-menu').forEach(btn => {
                btn.addEventListener('click', () => {
                    const id = btn.getAttribute('data-id');
                    const voucher = vouchers.find(v => String(v.id) === String(id));
                    if (voucher) {
                        showDetailModal(voucher);
                    }
                });
            });

            // Lucide 아이콘 재렌더링
            if (window.lucide) window.lucide.createIcons();

        } catch (err) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="8" style="text-align: center; padding: 32px; color: var(--danger);">
                        ${escHtml(err.message || '거래내역 조회 중 오류가 발생했습니다.')}
                    </td>
                </tr>
            `;
        } finally {
            if (loader) loader.style.display = 'none';
        }
    }

    /* ─── 헬퍼 ──────────────────────────────────────────────── */
    function showDetailModal(v) {
        const modal = document.getElementById('detail_modal');
        if (!modal) return;
        
        document.getElementById('detail_number').textContent = v.voucherNo || '-';
        document.getElementById('detail_date').textContent = v.voucherDate || '-';
        document.getElementById('detail_vendor').textContent = v.vendorNameSnapshot || '-';
        
        const statusEl = document.getElementById('detail_status');
        if (statusEl) {
            statusEl.textContent = v.status || '-';
            statusEl.className = `status_badge ${statusClass(v.status)}`;
        }
        
        modal.classList.add('is_active');
    }

    window.closeDetailModal = function() {
        const modal = document.getElementById('detail_modal');
        if (modal) modal.classList.remove('is_active');
    };

    function debitTotal(lines) {
        if (!Array.isArray(lines)) return '-';
        const sum = lines
            .filter(l => l.debitCredit === 'DEBIT')
            .reduce((acc, l) => acc + Number(l.totalAmount || 0), 0);
        return sum > 0 ? `₩${money.format(sum)}` : '-';
    }

    function creditTotal(lines) {
        if (!Array.isArray(lines)) return '-';
        const sum = lines
            .filter(l => l.debitCredit === 'CREDIT')
            .reduce((acc, l) => acc + Number(l.totalAmount || 0), 0);
        return sum > 0 ? `₩${money.format(sum)}` : '-';
    }

    function voucherTypeLabel(type) {
        switch (type) {
            case 'SALES': return '매출전표';
            case 'PURCHASE': return '매입전표';
            default: return escHtml(type || '-');
        }
    }

    function statusClass(status) {
        switch (status) {
            case 'POSTED': return 'success';
            case 'APPROVED': return 'success';
            case 'DRAFT': return 'warning';
            case 'REQUESTED': return 'warning';
            case 'CANCELLED': return 'danger';
            default: return 'muted';
        }
    }

    function formatWon(value) {
        return `₩${money.format(Number(value || 0))}`;
    }

    function setKpi(id, value) {
        const el = document.getElementById(id);
        if (el) el.textContent = Number(value || 0);
    }

    function setKpiText(id, text) {
        const el = document.getElementById(id);
        if (el) el.textContent = text;
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
