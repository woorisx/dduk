(function () {
  const API_PATH = '/api/v1/accounting/payroll-ledgers';
  const state = {
    ledgers: [],
    selectedLedgerId: null,
    selectedEmployees: []
  };

  const labels = {
    SALARY: '급여',
    BONUS: '상여',
    INCENTIVE: '성과급',
    SEVERANCE: '퇴직금',
    OTHER_ALLOWANCE: '기타수당',
    DRAFT: '임시',
    READY: '준비',
    CALCULATED: '계산완료',
    CONFIRMED: '확정',
    POSTED: '전표처리',
    CLOSED: '마감',
    CANCELLED: '취소'
  };

  const $ = (selector) => document.querySelector(selector);
  const $$ = (selector) => Array.from(document.querySelectorAll(selector));
  const money = (value) => Number(value || 0).toLocaleString('ko-KR');
  const label = (value) => labels[value] || value || '-';

  function toast(message) {
    const el = $('#toast');
    el.textContent = message;
    el.classList.add('show');
    window.clearTimeout(toast.timer);
    toast.timer = window.setTimeout(() => el.classList.remove('show'), 2600);
  }

  /** Unified API helper – delegates to window.ddukApi (apiClient.js) */
  async function request(path, options = {}) {
    const url = `${API_PATH}${path}`;
    const method = (options.method || 'GET').toUpperCase();
    const body = options.body ? (typeof options.body === 'string' ? JSON.parse(options.body) : options.body) : undefined;
    if (method === 'GET') return window.ddukApi.get(url);
    if (method === 'POST') return window.ddukApi.post(url, body);
    if (method === 'PUT') return window.ddukApi.put(url, body);
    if (method === 'PATCH') return window.ddukApi.patch(url, body);
    if (method === 'DELETE') return window.ddukApi.delete(url);
    return window.ddukApi.get(url);
  }

  async function loadAll() {
    try {
      const [summary, ledgers] = await Promise.all([
        request('/summary'),
        request('')
      ]);
      state.ledgers = ledgers;
      renderSummary(summary);
      renderLedgers();
      syncToolbar();
    } catch (error) {
      toast(error.message);
    }
  }

  function renderSummary(summary) {
    Object.entries(summary || {}).forEach(([key, value]) => {
      const textTarget = document.querySelector(`[data-summary="${key}"]`);
      const moneyTarget = document.querySelector(`[data-summary-money="${key}"]`);
      if (textTarget) textTarget.textContent = value ?? 0;
      if (moneyTarget) moneyTarget.textContent = money(value);
    });
  }

  function renderLedgers() {
    const body = $('#ledgerTableBody');
    body.innerHTML = '';
    if (!state.ledgers.length) {
      body.innerHTML = '<tr><td colspan="17">등록된 급여대장이 없습니다.</td></tr>';
      return;
    }

    state.ledgers.forEach((ledger) => {
      const tr = document.createElement('tr');
      tr.dataset.id = ledger.id;
      if (ledger.id === state.selectedLedgerId) tr.classList.add('selected');
      tr.innerHTML = `
        <td>${ledger.attributionYearMonth}</td>
        <td>${label(ledger.payrollType)}</td>
        <td>${ledger.ledgerName}</td>
        <td>${ledger.paymentDate || '-'}</td>
        <td>${ledger.paymentYearMonth || '-'}</td>
        <td><div class="precheck">${precheck(ledger.preEmployeeChecked, '대상자')}${precheck(ledger.preInsuranceCalculated, '보험')}${precheck(ledger.preSettlementValidated, '정산')}${precheck(ledger.preAccountValidated, '계좌')}</div></td>
        <td>${ledger.status === 'CALCULATED' || ledger.status === 'CONFIRMED' ? '완료' : '대기'}</td>
        <td class="amount">${ledger.headCount || 0}</td>
        <td><button type="button" class="table-action" data-action="detail" data-id="${ledger.id}">조회</button></td>
        <td><button type="button" class="table-action" data-action="payslip" data-id="${ledger.id}">명세서</button></td>
        <td class="amount">${money(ledger.grossAmount)}</td>
        <td class="amount">${money(ledger.deductionAmount)}</td>
        <td class="amount">${money(ledger.netAmount)}</td>
        <td>${ledger.bonusRateOrAmount || '-'}</td>
        <td><span class="status-badge ${ledger.status}">${label(ledger.status)}</span></td>
        <td>${ledger.createdBy || '-'}</td>
        <td>${ledger.createdAt ? ledger.createdAt.replace('T', ' ').slice(0, 16) : '-'}</td>
      `;
      tr.addEventListener('click', (event) => {
        if (!event.target.closest('button')) {
          state.selectedLedgerId = ledger.id;
          renderLedgers();
          syncToolbar();
        }
      });
      body.appendChild(tr);
    });
  }

  function precheck(ok, text) {
    return `<span class="${ok ? 'ok' : ''}">${text}</span>`;
  }

  function syncToolbar() {
    const selected = state.ledgers.find((ledger) => ledger.id === state.selectedLedgerId);
    $('#calculateButton').disabled = !selected || !['READY', 'DRAFT', 'CALCULATED'].includes(selected.status);
    $('#confirmButton').disabled = !selected || selected.status !== 'CALCULATED';
    $('#payslipButton').disabled = !selected || !['CALCULATED', 'CONFIRMED', 'POSTED', 'CLOSED'].includes(selected.status);
  }

  function readPayload() {
    const employeeMode = document.querySelector('input[name="employeeMode"]:checked').value;
    const settlementMode = document.querySelector('input[name="settlementMode"]:checked').value;
    const payload = {
      attributionYearMonth: $('#attributionYearMonth').value,
      payrollType: $('#payrollType').value,
      taxType: $('#taxType').value,
      settlementCycle: $('#settlementCycle').value,
      targetPeriodMode: $('#targetPeriodMode').value,
      paymentDate: $('#paymentDate').value,
      paymentYearMonth: $('#paymentYearMonth').value,
      ledgerName: $('#ledgerName').value.trim(),
      settlementItemSelectionMode: settlementMode,
      settlementItems: $$('#settlementItems input:checked').map((item) => item.value),
      employeeSelectionMode: employeeMode,
      employeeIds: state.selectedEmployees.map((employee) => employee.id),
      bonusRateOrAmount: $('#bonusRateOrAmount').value.trim(),
      createdBy: 'system'
    };
    validatePayload(payload);
    return payload;
  }

  function validatePayload(payload) {
    const required = ['attributionYearMonth', 'payrollType', 'paymentDate', 'paymentYearMonth', 'ledgerName'];
    for (const key of required) {
      if (!payload[key]) throw new Error('필수값을 입력해주세요.');
    }
    if (payload.employeeSelectionMode === 'SELECTED' && payload.employeeIds.length === 0) {
      throw new Error('선택 대상사원을 1명 이상 지정해주세요.');
    }
    if (payload.settlementItemSelectionMode === 'SELECTED' && payload.settlementItems.length === 0) {
      throw new Error('선택 정산항목을 1개 이상 지정해주세요.');
    }
  }

  function resetForm() {
    $('#attributionYearMonth').value = new Date().toISOString().slice(0, 7);
    $('#paymentYearMonth').value = new Date().toISOString().slice(0, 7);
    $('#paymentDate').value = new Date().toISOString().slice(0, 10);
    $('#payrollType').value = 'SALARY';
    $('#taxType').value = 'TAXABLE';
    $('#settlementCycle').value = 'MONTHLY';
    $('#targetPeriodMode').value = 'BULK';
    $('#ledgerName').value = `${new Date().getFullYear()}년 ${new Date().getMonth() + 1}월 급여대장`;
    $('#bonusRateOrAmount').value = '';
    document.querySelector('input[name="settlementMode"][value="ALL"]').checked = true;
    document.querySelector('input[name="employeeMode"][value="ALL"]').checked = true;
    $$('#settlementItems input').forEach((input) => { input.checked = false; });
    state.selectedEmployees = [];
    renderSelectedEmployees();
  }

  function renderSelectedEmployees() {
    const wrap = $('#selectedEmployees');
    const employeeMode = document.querySelector('input[name="employeeMode"]:checked').value;
    $('#selectedEmployeeSummary').textContent = employeeMode === 'ALL'
      ? '전체 사원 대상'
      : `${state.selectedEmployees.length}명 선택`;
    wrap.innerHTML = state.selectedEmployees.map((employee) => `
      <button type="button" data-remove-employee="${employee.id}">${employee.name} (${employee.employeeNo}) ×</button>
    `).join('');
  }

  async function saveLedger(calculateNow) {
    try {
      const payload = readPayload();
      const saved = await request(calculateNow ? '/calculate' : '', {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      state.selectedLedgerId = saved.id;
      $('#payrollDialog').close();
      toast(calculateNow ? '급여계산이 완료되었습니다.' : '급여대장이 저장되었습니다.');
      await loadAll();
    } catch (error) {
      toast(error.message);
    }
  }

  async function calculateSelected() {
    if (!state.selectedLedgerId) return;
    try {
      await request(`/${state.selectedLedgerId}/calculate`, { method: 'POST' });
      toast('급여계산이 완료되었습니다.');
      await loadAll();
    } catch (error) {
      toast(error.message);
    }
  }

  async function confirmSelected() {
    if (!state.selectedLedgerId) return;
    try {
      await request(`/${state.selectedLedgerId}/confirm`, { method: 'POST' });
      toast('급여대장이 확정되었습니다.');
      await loadAll();
    } catch (error) {
      toast(error.message);
    }
  }

  async function showDetail(id) {
    try {
      const ledger = await request(`/${id}`);
      $('#detailTitle').textContent = ledger.ledgerName;
      $('#detailContent').innerHTML = `
        <section class="detail-grid">
          <article><span>지급총액</span><strong>${money(ledger.grossAmount)}</strong></article>
          <article><span>공제총액</span><strong>${money(ledger.deductionAmount)}</strong></article>
          <article><span>실지급액</span><strong>${money(ledger.netAmount)}</strong></article>
        </section>
        <div class="table-wrap">
          <table>
            <thead><tr><th>사번</th><th>성명</th><th>부서</th><th>직급</th><th>지급항목</th><th>공제항목</th><th>실지급액</th><th>지급상태</th></tr></thead>
            <tbody>
              ${(ledger.employees || []).map((employee) => `
                <tr>
                  <td>${employee.employeeNo}</td>
                  <td>${employee.employeeName}</td>
                  <td>${employee.department || '-'}</td>
                  <td>${employee.position || '-'}</td>
                  <td>${employee.payItems.map((item) => `${item.name} ${money(item.amount)}`).join('<br>')}</td>
                  <td>${employee.deductionItems.map((item) => `${item.name} ${money(item.amount)}`).join('<br>')}</td>
                  <td class="amount">${money(employee.netAmount)}</td>
                  <td>${employee.paymentStatus}</td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      `;
      $('#detailDialog').showModal();
      if (window.lucide) lucide.createIcons();
    } catch (error) {
      toast(error.message);
    }
  }

  async function showPayslips(id) {
    try {
      const slips = await request(`/${id}/payslips`);
      $('#payslipContent').innerHTML = slips.map((slip) => `
        <article class="payslip-card">
          <h3>${slip.employeeName} (${slip.employeeNo})</h3>
          <p>${slip.department || '-'} · ${slip.position || '-'} · 지급일 ${slip.paymentDate}</p>
          <div class="detail-grid">
            <article><span>지급총액</span><strong>${money(slip.grossAmount)}</strong></article>
            <article><span>공제총액</span><strong>${money(slip.deductionAmount)}</strong></article>
            <article><span>실지급액</span><strong>${money(slip.netAmount)}</strong></article>
          </div>
        </article>
      `).join('') || '<p>조회할 급여명세서가 없습니다.</p>';
      $('#payslipDialog').showModal();
      if (window.lucide) lucide.createIcons();
    } catch (error) {
      toast(error.message);
    }
  }

  async function searchEmployees() {
    try {
      const keyword = encodeURIComponent($('#employeeKeyword').value.trim());
      const employees = await request(`/employees/search?keyword=${keyword}`);
      $('#employeeResults').innerHTML = employees.map((employee) => `
        <div class="lookup-result">
          <div>
            <strong>${employee.name}</strong>
            <p>${employee.employeeNo} · ${employee.department || '-'} · ${employee.position || '-'}</p>
          </div>
          <button type="button" class="table-action" data-pick-employee="${employee.id}">선택</button>
        </div>
      `).join('') || '<p>검색 결과가 없습니다.</p>';
      $('#employeeResults').querySelectorAll('[data-pick-employee]').forEach((button) => {
        button.addEventListener('click', () => {
          const picked = employees.find((employee) => String(employee.id) === button.dataset.pickEmployee);
          if (picked && !state.selectedEmployees.some((employee) => employee.id === picked.id)) {
            state.selectedEmployees.push(picked);
            document.querySelector('input[name="employeeMode"][value="SELECTED"]').checked = true;
            renderSelectedEmployees();
          }
        });
      });
    } catch (error) {
      toast(error.message);
    }
  }

  function bindEvents() {
    $('#openCreateButton').addEventListener('click', () => {
      resetForm();
      $('#payrollDialog').showModal();
      if (window.lucide) lucide.createIcons();
    });
    $('#saveLedgerButton').addEventListener('click', () => saveLedger(false));
    $('#draftSaveButton').addEventListener('click', () => saveLedger(false));
    $('#runCalculationButton').addEventListener('click', () => saveLedger(true));
    $('#calculateButton').addEventListener('click', calculateSelected);
    $('#confirmButton').addEventListener('click', confirmSelected);
    $('#payslipButton').addEventListener('click', () => showPayslips(state.selectedLedgerId));
    $('#reloadButton').addEventListener('click', loadAll);
    $('#openEmployeeSearchButton').addEventListener('click', () => {
      $('#employeeDialog').showModal();
      searchEmployees();
    });
    $('#employeeSearchButton').addEventListener('click', searchEmployees);
    $('#employeeKeyword').addEventListener('keydown', (event) => {
      if (event.key === 'Enter') {
        event.preventDefault();
        searchEmployees();
      }
    });

    document.addEventListener('click', (event) => {
      const closeButton = event.target.closest('[data-close-dialog]');
      if (closeButton) document.getElementById(closeButton.dataset.closeDialog).close();
      const removeButton = event.target.closest('[data-remove-employee]');
      if (removeButton) {
        state.selectedEmployees = state.selectedEmployees.filter((employee) => String(employee.id) !== removeButton.dataset.removeEmployee);
        renderSelectedEmployees();
      }
      const actionButton = event.target.closest('[data-action]');
      if (actionButton) {
        state.selectedLedgerId = Number(actionButton.dataset.id);
        if (actionButton.dataset.action === 'detail') showDetail(actionButton.dataset.id);
        if (actionButton.dataset.action === 'payslip') showPayslips(actionButton.dataset.id);
        renderLedgers();
        syncToolbar();
      }
    });

    $$('input[name="employeeMode"]').forEach((input) => input.addEventListener('change', renderSelectedEmployees));
  }

  document.addEventListener('DOMContentLoaded', () => {
    bindEvents();
    loadAll();
    if (window.lucide) lucide.createIcons();
  });
})();
