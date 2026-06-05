const EXPENSE_API = '/api/v1/accounting/expenses';
const moneyFormatter = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 0 });

const state = {
  expenses: [],
  editingId: null,
  currentEmployee: null,
  currentPage: 0,
  totalPages: 0,
  totalElements: 0,
  pageSize: 7,
};

document.addEventListener('DOMContentLoaded', initExpensePage);

function initExpensePage() {
  const expenseDate = document.getElementById('expenseDate');
  if (expenseDate) expenseDate.valueAsDate = new Date();

  bindEvents();
  loadCurrentEmployee();

  if (window.lucide) window.lucide.createIcons();
}

function bindEvents() {
  document.getElementById('expenseForm')?.addEventListener('submit', handleSubmit);
  document.getElementById('reloadButton')?.addEventListener('click', loadExpenses);
  document.getElementById('resetFilterButton')?.addEventListener('click', resetFilters);
  document.getElementById('resetFormButton')?.addEventListener('click', resetForm);
  document.getElementById('deleteButton')?.addEventListener('click', deleteCurrentExpense);
  document.getElementById('searchButton')?.addEventListener('click', () => { state.currentPage = 0; loadExpenses(); });
  document.getElementById('amount')?.addEventListener('input', handleAmountInput);
  document.getElementById('amount')?.addEventListener('blur', formatAmountInput);
  document.getElementById('expenseListBody')?.addEventListener('click', handleListClick);
  document.getElementById('prevPageBtn')?.addEventListener('click', () => goToPage(state.currentPage - 1));
  document.getElementById('nextPageBtn')?.addEventListener('click', () => goToPage(state.currentPage + 1));

  ['statusFilter', 'startDateFilter', 'endDateFilter'].forEach((id) => {
    document.getElementById(id)?.addEventListener('change', () => { state.currentPage = 0; loadExpenses(); });
  });

  document.getElementById('keywordFilter')?.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      state.currentPage = 0;
      loadExpenses();
    }
  });
  document.getElementById('keywordFilter')?.addEventListener('search', () => { state.currentPage = 0; loadExpenses(); });
}

async function loadExpenses() {
  try {
    const filterParams = buildFilterParams();
    const pageParams = new URLSearchParams(filterParams);
    pageParams.set('page', state.currentPage);
    pageParams.set('size', state.pageSize);

    // summary는 필터만, 목록은 필터+페이지
    const summaryParams = filterParams;
    const [listResponse, summaryResponse] = await Promise.all([
      window.ddukApi.get(`${EXPENSE_API}?${pageParams.toString()}`),
      window.ddukApi.get(`${EXPENSE_API}/summary?${summaryParams.toString()}`),
    ]);

    const pageData = listResponse.data || {};
    state.expenses = pageData.content || [];
    state.currentPage = pageData.page ?? 0;
    state.totalPages = pageData.totalPages ?? 0;
    state.totalElements = pageData.totalElements ?? 0;

    renderSummary(summaryResponse.data || {});
    renderExpenseList();
    renderPagination();
  } catch (error) {
    showToast(error.message || '비용 목록을 불러오지 못했습니다.', 'error');
  }
}

async function loadCurrentEmployee() {
  try {
    const response = await window.ddukApi.get(`${EXPENSE_API}/current-employee`);
    state.currentEmployee = response.data || null;

    if (!state.currentEmployee?.id) {
      console.warn('[expenses] 직원 정보가 없음 (data:', state.currentEmployee, ')');
      disableExpenseForm();
      setEmployeeHint('로그인 계정에 연결된 직원 정보가 없습니다. 관리자에게 문의하세요.');
      showToast('직원 정보를 찾을 수 없습니다. 관리자에게 직원 등록을 요청하세요.', 'warning');
      await loadExpenses();
      return;
    }
    applyCurrentEmployeeToForm();
    await loadExpenses();
  } catch (error) {
    console.error('[expenses] loadCurrentEmployee 실패:', error);
    state.currentEmployee = null;

    // 401 인증 만료는 apiClient.js handle401()에서 이미 로그아웃 처리됨
    if (error.type === 'unauthorized') {
      return;
    }

    // 403 권한 부족이나 기타 서버 에러 시 폼만 비활성화
    disableExpenseForm();
    setEmployeeHint('직원 정보를 불러오지 못했습니다: ' + (error.message || '알 수 없는 오류'));
    showToast('직원 정보를 불러오지 못했습니다. 페이지를 새로고침하거나 관리자에게 문의하세요.', 'error');

    // 비용 목록은 가능하면 보여줌
    try { await loadExpenses(); } catch (_) { /* ignore */ }
  }
}

function applyCurrentEmployeeToForm() {
  const employeeId = state.currentEmployee?.id || '';
  setValue('employeeId', employeeId);

  if (state.currentEmployee) {
    const { name, employeeNo, department, position } = state.currentEmployee;
    setEmployeeHint(`${name || '직원'} (${employeeNo || '-'}) · ${department || '-'} · ${position || '-'}`);
    return;
  }

  setEmployeeHint('로그인 계정에 연결된 직원 정보가 없습니다.');
}

function buildFilterParams() {
  const params = new URLSearchParams();
  addParam(params, 'status', getValue('statusFilter'));
  addParam(params, 'startDate', getValue('startDateFilter'));
  addParam(params, 'endDate', getValue('endDateFilter'));
  addParam(params, 'keyword', getValue('keywordFilter'));
  return params;
}

function renderSummary(summary) {
  setText('summaryTotalCount', summary.totalCount || 0);
  setText('summaryTotalAmount', won(summary.totalAmount || 0));
  setText('summarySubmittedCount', summary.submittedCount || 0);
  setText('summaryApprovedCount', summary.approvedCount || 0);
  setText('summaryRejectedCount', summary.rejectedCount || 0);
}

function renderExpenseList() {
  const tbody = document.getElementById('expenseListBody');
  const emptyState = document.getElementById('expenseEmptyState');
  const start = state.currentPage * state.pageSize + 1;
  const end = start + state.expenses.length - 1;
  const label = state.totalElements > 0 ? `${start}-${end} / ${state.totalElements}건` : '0건';
  setText('listSummary', label);

  if (!tbody || !emptyState) return;
  emptyState.hidden = state.expenses.length > 0;
  tbody.innerHTML = state.expenses.map(renderExpenseRow).join('');
  if (window.lucide) window.lucide.createIcons();
}

function renderExpenseRow(expense) {
  const canEdit = expense.status !== 'APPROVED';
  const canApprove = expense.status !== 'APPROVED';
  const canReject = expense.status !== 'REJECTED';

  return `
    <tr data-expense-id="${expense.id}">
      <td>${escapeHtml(expense.expenseDate || '-')}</td>
      <td>${escapeHtml(expense.category || '-')}</td>
      <td><div class="expense_desc" title="${escapeHtml(expense.description || '')}">${escapeHtml(expense.description || '-')}</div></td>
      <td>${expense.employeeId ? escapeHtml(String(expense.employeeId)) : '-'}</td>
      <td class="amount_col">${won(expense.amount || 0)}</td>
      <td>${statusBadge(expense.status)}</td>
      <td>
        <div class="row_actions">
          <button class="icon_action" type="button" title="수정" data-action="edit" ${canEdit ? '' : 'disabled'}>
            <i data-lucide="pencil"></i>
          </button>
          <button class="icon_action approve" type="button" title="승인" data-action="approve" ${canApprove ? '' : 'disabled'}>
            <i data-lucide="check"></i>
          </button>
          <button class="icon_action reject" type="button" title="반려" data-action="reject" ${canReject ? '' : 'disabled'}>
            <i data-lucide="x"></i>
          </button>
        </div>
      </td>
    </tr>
  `;
}

async function handleSubmit(event) {
  event.preventDefault();
  if (!state.currentEmployee?.id) {
    showToast('직원 정보가 연결되지 않아 비용을 저장할 수 없습니다. 관리자에게 문의하세요.', 'error');
    return;
  }
  const payload = buildPayload();
  const validationMessage = validatePayload(payload);
  if (validationMessage) {
    showToast(validationMessage, 'error');
    return;
  }

  try {
    if (state.editingId) {
      await window.ddukApi.put(`${EXPENSE_API}/${state.editingId}`, payload);
      showToast('비용이 수정되었습니다.', 'success');
    } else {
      await window.ddukApi.post(EXPENSE_API, { ...payload, status: 'SUBMITTED' });
      showToast('비용이 등록되었습니다.', 'success');
    }
    resetForm();
    await loadExpenses();
  } catch (error) {
    showToast(error.message || '비용 저장에 실패했습니다.', 'error');
  }
}

function buildPayload() {
  return {
    employeeId: getValue('employeeId') ? Number(getValue('employeeId')) : null,
    expenseDate: getValue('expenseDate') || null,
    category: getValue('category'),
    amount: parseMoney(getValue('amount')),
    description: getValue('description'),
  };
}

function validatePayload(payload) {
  if (!payload.expenseDate) return '비용 일자를 입력해 주세요.';
  if (!payload.category) return '비용 분류를 입력해 주세요.';
  if (!payload.amount || payload.amount <= 0) return '0보다 큰 금액을 입력해 주세요.';
  if (!payload.description) return '내용을 입력해 주세요.';
  return '';
}

function handleListClick(event) {
  const button = event.target.closest('[data-action]');
  if (!button || button.disabled) return;

  const row = button.closest('[data-expense-id]');
  const expense = state.expenses.find((item) => String(item.id) === row?.dataset.expenseId);
  if (!expense) return;

  const action = button.dataset.action;
  if (action === 'edit') {
    editExpense(expense);
    return;
  }
  if (action === 'approve') {
    updateExpenseStatus(expense.id, 'APPROVED');
    return;
  }
  if (action === 'reject') {
    updateExpenseStatus(expense.id, 'REJECTED');
  }
}

function editExpense(expense) {
  state.editingId = expense.id;
  setValue('expenseId', expense.id);
  setValue('expenseDate', expense.expenseDate || '');
  setValue('category', expense.category || '');
  setValue('amount', formatNumber(expense.amount || 0));
  setValue('employeeId', expense.employeeId || '');
  setValue('description', expense.description || '');
  setText('formTitle', `비용 수정 #${expense.id}`);
  setText('formHint', '승인 전 비용만 수정할 수 있습니다.');
  document.getElementById('deleteButton').hidden = false;
  document.getElementById('expenseDate')?.focus();
}

async function updateExpenseStatus(id, status) {
  try {
    await window.ddukApi.patch(`${EXPENSE_API}/${id}/status`, { status });
    showToast(status === 'APPROVED' ? '비용이 승인되었습니다.' : '비용이 반려되었습니다.', 'success');
    await loadExpenses();
  } catch (error) {
    showToast(error.message || '상태 변경에 실패했습니다.', 'error');
  }
}

async function deleteCurrentExpense() {
  if (!state.editingId) return;
  if (!window.confirm('선택한 비용을 삭제할까요?')) return;

  try {
    await window.ddukApi.delete(`${EXPENSE_API}/${state.editingId}`);
    showToast('비용이 삭제되었습니다.', 'success');
    resetForm();
    await loadExpenses();
  } catch (error) {
    showToast(error.message || '비용 삭제에 실패했습니다.', 'error');
  }
}

function resetForm() {
  state.editingId = null;
  setValue('expenseId', '');
  setValue('expenseDate', new Date().toISOString().slice(0, 10));
  setValue('category', '');
  setValue('amount', '');
  applyCurrentEmployeeToForm();
  setValue('description', '');
  setText('formTitle', '비용 등록');
  setText('formHint', '필수 항목을 입력한 뒤 제출 상태로 저장합니다.');
  document.getElementById('deleteButton').hidden = true;
}

function resetFilters() {
  setValue('statusFilter', '');
  setValue('startDateFilter', '');
  setValue('endDateFilter', '');
  setValue('keywordFilter', '');
  state.currentPage = 0;
  loadExpenses();
}

function handleAmountInput(event) {
  event.target.value = event.target.value.replace(/[^0-9]/g, '');
}

function formatAmountInput(event) {
  event.target.value = event.target.value ? formatNumber(parseMoney(event.target.value)) : '';
}

function statusBadge(status) {
  const normalized = (status || '').toLowerCase();
  const labels = {
    submitted: '제출',
    approved: '승인',
    rejected: '반려',
  };
  return `<span class="status_badge ${normalized}">${labels[normalized] || escapeHtml(status || '-')}</span>`;
}

function addParam(params, key, value) {
  if (value) params.set(key, value);
}

function getValue(id) {
  return document.getElementById(id)?.value.trim() || '';
}

function setValue(id, value) {
  const el = document.getElementById(id);
  if (el) el.value = value;
}

function setText(id, value) {
  const el = document.getElementById(id);
  if (el) el.textContent = value;
}

function setEmployeeHint(message) {
  setText('employeeHint', message);
}

function parseMoney(value) {
  return Number(String(value || '').replace(/[^0-9]/g, '')) || 0;
}

function formatNumber(value) {
  return moneyFormatter.format(Number(value || 0));
}

function won(value) {
  return `${formatNumber(value)}원`;
}

function showToast(message, type) {
  if (window.ddukApi?.showToast) {
    window.ddukApi.showToast(message, type);
    return;
  }
  console[type === 'error' ? 'error' : 'warn'](message);
}

function forceLogoutToLogin(message) {
  disableExpenseForm();
  setEmployeeHint(message);
  showToast(message, 'error');
  localStorage.clear();
  sessionStorage.clear();
  window.setTimeout(() => {
    window.location.href = resolveLoginUrl();
  }, 900);
}

function disableExpenseForm() {
  document.querySelectorAll('#expenseForm input, #expenseForm textarea, #expenseForm button')
    .forEach((element) => {
      element.disabled = true;
    });
}

function resolveLoginUrl() {
  const path = window.location.pathname.replace(/\\/g, '/');
  if (path.includes('/pages/')) {
    const depth = path.split('/pages/')[1].split('/').length;
    return '../'.repeat(depth) + 'index.html';
  }
  return 'index.html';
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function goToPage(page) {
  if (page < 0 || page >= state.totalPages) return;
  state.currentPage = page;
  loadExpenses();
}

function renderPagination() {
  const bar = document.getElementById('paginationBar');
  const pageNumbers = document.getElementById('pageNumbers');
  const prevBtn = document.getElementById('prevPageBtn');
  const nextBtn = document.getElementById('nextPageBtn');
  const pageInfo = document.getElementById('pageInfo');
  if (!bar) return;

  if (state.totalPages <= 1) {
    bar.hidden = true;
    return;
  }

  bar.hidden = false;
  prevBtn.disabled = state.currentPage === 0;
  nextBtn.disabled = state.currentPage >= state.totalPages - 1;

  if (pageInfo) {
    pageInfo.textContent = `${state.currentPage + 1} / ${state.totalPages} 페이지`;
  }

  // 표시할 페이지 번호 범위 계산 (최대 7개)
  const maxVisible = 7;
  let startPage = Math.max(0, state.currentPage - Math.floor(maxVisible / 2));
  let endPage = Math.min(state.totalPages - 1, startPage + maxVisible - 1);
  startPage = Math.max(0, endPage - maxVisible + 1);

  let html = '';
  if (startPage > 0) {
    html += `<button class="pagination_page" type="button" data-page="0">1</button>`;
    if (startPage > 1) html += `<span class="page_ellipsis">…</span>`;
  }

  for (let i = startPage; i <= endPage; i++) {
    const active = i === state.currentPage ? ' is_active' : '';
    html += `<button class="pagination_page${active}" type="button" data-page="${i}">${i + 1}</button>`;
  }

  if (endPage < state.totalPages - 1) {
    if (endPage < state.totalPages - 2) html += `<span class="page_ellipsis">…</span>`;
    html += `<button class="pagination_page" type="button" data-page="${state.totalPages - 1}">${state.totalPages}</button>`;
  }

  pageNumbers.innerHTML = html;

  // 번호 버튼 클릭 이벤트
  pageNumbers.querySelectorAll('.pagination_page').forEach((btn) => {
    btn.addEventListener('click', () => goToPage(parseInt(btn.dataset.page, 10)));
  });

  if (window.lucide) window.lucide.createIcons();
}
