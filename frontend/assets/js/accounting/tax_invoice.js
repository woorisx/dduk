const TAX_INVOICE_API = '/api/v1/accounting/tax-invoices';
const VENDOR_SEARCH_API = '/api/v1/inventory/vendors/search';
const ITEM_SEARCH_API = '/api/v1/inventory/items/search';
const PURCHASE_ORDER_API = '/api/v1/inventory/purchase-orders';
const VAT_ZERO_TYPES = new Set(['ZERO_TAX', 'TAX_FREE', 'EXPORT', 'INVOICE']);
const moneyFormatter = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 0 });

const state = {
  invoices: [],
  currentPage: 1,
  pageSize: 10,
  statusAction: null,
  vendorLookupTarget: null,
  vendorLookupRequestId: 0,
  itemLookupResults: [],
  purchaseOrderLookupResults: [],
  lines: [],
};

document.addEventListener('DOMContentLoaded', initTaxInvoicePage);

function initTaxInvoicePage() {
  const issueDate = document.getElementById('issueDate');
  if (issueDate) issueDate.valueAsDate = new Date();

  bindEvents();
  setDefaultSupplier();
  setValue('quantity', '1');
  updateFormTypeLabel();
  recalculateAmounts();
  renderLineList();
  loadTaxInvoices();

  if (window.lucide) window.lucide.createIcons();
}

/** (주)아망티 기본 공급자 정보를 폼에 설정 */
function setDefaultSupplier() {
  setValue('supplierName', '(주)아망티');
  setValue('supplierBusinessNo', '120-88-12345');
  setValue('supplierRepresentativeName', '이서준');
  setValue('supplierEmail', 'contact@amantea.co.kr');
}

function bindEvents() {
  document.getElementById('taxInvoiceType')?.addEventListener('change', updateFormTypeLabel);

  ['quantity', 'lineUnitPrice', 'supplyAmount', 'vatAmount'].forEach((id) => {
    const input = document.getElementById(id);
    input?.addEventListener('input', () => {
      sanitizeMoneyInput(input);
      if (id === 'quantity' || id === 'lineUnitPrice') {
        recalculateLineFromQuantity();
        return;
      }
      if (id === 'supplyAmount') {
        calculateVat();
        return;
      }
      recalculateAmounts();
    });
    input?.addEventListener('blur', () => {
      input.value = formatNumber(parseMoney(input.value));
      if (id === 'quantity' || id === 'lineUnitPrice') {
        recalculateLineFromQuantity();
        return;
      }
      if (id === 'supplyAmount') {
        calculateVat();
        return;
      }
      recalculateAmounts();
    });
  });

  document.getElementById('vatType')?.addEventListener('change', calculateVat);
  document.getElementById('taxInvoiceForm')?.addEventListener('submit', handleCreate);
  document.getElementById('resetFormButton')?.addEventListener('click', resetForm);
  document.getElementById('reloadButton')?.addEventListener('click', () => loadTaxInvoices({ showList: true }));
  document.getElementById('hideListButton')?.addEventListener('click', hideTaxInvoiceList);
  document.getElementById('typeFilter')?.addEventListener('change', () => loadTaxInvoices({ showList: true }));
  document.getElementById('statusFilter')?.addEventListener('change', () => loadTaxInvoices({ showList: true }));
  document.getElementById('keywordFilter')?.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      loadTaxInvoices({ showList: true });
    }
  });
  document.getElementById('keywordFilter')?.addEventListener('search', () => loadTaxInvoices({ showList: true }));

  document.getElementById('statusDialogClose')?.addEventListener('click', closeStatusDialog);
  document.getElementById('statusCancelButton')?.addEventListener('click', closeStatusDialog);
  document.getElementById('statusDialog')?.addEventListener('click', (event) => {
    if (event.target.id === 'statusDialog') closeStatusDialog();
  });
  document.getElementById('statusForm')?.addEventListener('submit', handleStatusSubmit);
  document.getElementById('detailDialogClose')?.addEventListener('click', closeDetailDialog);
  document.getElementById('detailDialog')?.addEventListener('click', (event) => {
    if (event.target.id === 'detailDialog') closeDetailDialog();
  });

  document.getElementById('taxInvoiceListBody')?.addEventListener('click', handleListClick);
  document.getElementById('taxInvoicePagination')?.addEventListener('click', handlePaginationClick);

  document.querySelectorAll('[data-vendor-lookup]').forEach((button) => {
    button.addEventListener('click', () => openVendorLookup(button.dataset.vendorLookup));
  });
  document.getElementById('vendorLookupClose')?.addEventListener('click', closeVendorLookup);
  document.getElementById('vendorLookupSearchButton')?.addEventListener('click', searchVendors);
  document.getElementById('vendorLookupKeyword')?.addEventListener('input', debounceVendorSearch);
  document.getElementById('vendorLookupKeyword')?.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      searchVendors();
    }
  });
  document.getElementById('vendorLookupResults')?.addEventListener('click', handleVendorSelect);
  document.querySelectorAll('[data-hide-lookup-results]').forEach((button) => {
    button.addEventListener('click', () => hideLookupResults(button.dataset.hideLookupResults));
  });

  document.getElementById('itemLookupButton')?.addEventListener('click', openItemLookup);
  document.getElementById('itemLookupClose')?.addEventListener('click', closeItemLookup);
  document.getElementById('itemLookupDialog')?.addEventListener('click', (event) => {
    if (event.target.id === 'itemLookupDialog') closeItemLookup();
  });
  document.getElementById('itemLookupSearchButton')?.addEventListener('click', searchItems);
  document.getElementById('itemLookupKeyword')?.addEventListener('input', debounceItemSearch);
  document.getElementById('itemLookupKeyword')?.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      searchItems();
    }
  });
  document.getElementById('itemLookupResults')?.addEventListener('click', handleItemSelect);
  document.getElementById('addLineButton')?.addEventListener('click', addCurrentLine);
  document.getElementById('lineListBody')?.addEventListener('click', handleLineListClick);

  document.getElementById('purchaseOrderLookupButton')?.addEventListener('click', openPurchaseOrderLookup);
  document.getElementById('purchaseOrderLookupClose')?.addEventListener('click', closePurchaseOrderLookup);
  document.getElementById('purchaseOrderLookupDialog')?.addEventListener('click', (event) => {
    if (event.target.id === 'purchaseOrderLookupDialog') closePurchaseOrderLookup();
  });
  document.getElementById('purchaseOrderLookupSearchButton')?.addEventListener('click', searchPurchaseOrders);
  document.getElementById('purchaseOrderLookupKeyword')?.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      searchPurchaseOrders();
    }
  });
  document.getElementById('purchaseOrderLookupResults')?.addEventListener('click', handlePurchaseOrderSelect);
}

function updateFormTypeLabel() {
  const type = getValue('taxInvoiceType') || 'SALES';
  setText('formTypeLabel', type === 'SALES' ? '매출 세금계산서' : '매입 세금계산서');
}

async function handleCreate(event) {
  event.preventDefault();
  const payload = buildCreatePayload();
  const validationMessage = validatePayload(payload);
  if (validationMessage) {
    showToast(validationMessage, 'error');
    return;
  }

  try {
    await window.ddukApi.post(TAX_INVOICE_API, payload);
    showToast('세금계산서가 저장되었습니다.', 'success');
    resetForm();
    setValue('typeFilter', '');
    setValue('statusFilter', '');
    setValue('keywordFilter', '');
    await loadTaxInvoices({ showList: true });
  } catch (error) {
    showToast(error.message || '세금계산서 저장에 실패했습니다.', 'error');
  }
}

function buildCreatePayload() {
  const draftLine = buildLineFromForm();
  const lines = state.lines.length ? state.lines : [draftLine].filter(Boolean);
  return {
    taxInvoiceType: getValue('taxInvoiceType') || 'SALES',
    vatType: getValue('vatType'),
    issueDate: getValue('issueDate'),
    supplierBusinessNo: getValue('supplierBusinessNo'),
    supplierName: getValue('supplierName'),
    supplierRepresentativeName: getValue('supplierRepresentativeName'),
    supplierEmail: getValue('supplierEmail'),
    recipientBusinessNo: getValue('recipientBusinessNo'),
    recipientName: getValue('recipientName'),
    recipientRepresentativeName: getValue('recipientRepresentativeName'),
    recipientEmail: getValue('recipientEmail'),
    memo: getValue('memo'),
    lines,
  };
}

function validatePayload(payload) {
  if (!payload.issueDate) return '작성일자를 입력해주세요.';
  if (!payload.supplierBusinessNo) return '공급자 사업자번호를 입력해주세요.';
  if (!payload.supplierName.trim()) return '공급자를 입력해주세요.';
  if (!payload.recipientBusinessNo) return '공급받는자 사업자번호를 입력해주세요.';
  if (!payload.recipientName.trim()) return '공급받는자를 입력해주세요.';
  if (!payload.lines.length) return '품목을 하나 이상 추가하거나 현재 품목 입력값을 완성해주세요.';
  if (payload.lines.some((line) => !line.itemName.trim())) return '품목명을 입력해주세요.';
  if (payload.lines.some((line) => Number(line.supplyAmount || 0) <= 0)) return '각 품목의 공급가액은 0보다 커야 합니다.';
  return '';
}

async function loadTaxInvoices(options = {}) {
  if (options.showList) showTaxInvoiceList();
  setLoading();
  const params = new URLSearchParams();
  if (getValue('typeFilter')) params.set('type', getValue('typeFilter'));
  if (getValue('statusFilter')) params.set('status', getValue('statusFilter'));
  if (getValue('keywordFilter').trim()) params.set('keyword', getValue('keywordFilter').trim());

  try {
    const response = await window.ddukApi.get(`${TAX_INVOICE_API}?${params.toString()}`);
    state.invoices = response.data || [];
    state.currentPage = 1;
    renderTaxInvoices();
  } catch (error) {
    showToast(error.message || '세금계산서 목록 조회에 실패했습니다.', 'error');
    renderEmpty('목록을 불러오지 못했습니다.');
  }
}

function showTaxInvoiceList() {
  const panel = document.getElementById('taxInvoiceListPanel');
  panel?.classList.remove('is_collapsed');
  panel?.setAttribute('aria-hidden', 'false');
}

function hideTaxInvoiceList() {
  const panel = document.getElementById('taxInvoiceListPanel');
  panel?.classList.add('is_collapsed');
  panel?.setAttribute('aria-hidden', 'true');
}

function renderTaxInvoices() {
  const tbody = document.getElementById('taxInvoiceListBody');
  if (!tbody) return;
  const totalCount = state.invoices.length;
  const totalPages = getTotalInvoicePages();
  state.currentPage = Math.min(Math.max(state.currentPage, 1), totalPages);
  const startIndex = (state.currentPage - 1) * state.pageSize;
  const pageItems = state.invoices.slice(startIndex, startIndex + state.pageSize);
  const rangeStart = totalCount ? startIndex + 1 : 0;
  const rangeEnd = Math.min(startIndex + state.pageSize, totalCount);
  setText('listSummary', totalCount > state.pageSize ? `${rangeStart}-${rangeEnd} / ${totalCount}건` : `${totalCount}건`);

  if (!totalCount) {
    renderEmpty('등록된 세금계산서가 없습니다.');
    return;
  }

  tbody.innerHTML = pageItems.map((invoice) => {
    const isSales = invoice.taxInvoiceType === 'SALES';
    const counterparty = isSales ? invoice.recipientName : invoice.supplierName;
    return `
      <tr>
        <td>${escapeHtml(invoice.taxInvoiceNo)}</td>
        <td>${escapeHtml(invoice.issueDate)}</td>
        <td>${isSales ? '매출' : '매입'} / ${escapeHtml(counterparty || '')}</td>
        <td class="amount">${formatWon(invoice.supplyAmount)}</td>
        <td class="amount">${formatWon(invoice.vatAmount)}</td>
        <td class="amount">${formatWon(invoice.totalAmount)}</td>
        <td><span class="status_badge ${statusClass(invoice.status)}">${statusLabel(invoice.status)}</span></td>
        <td>${escapeHtml(invoice.externalApprovalNo || '-')}</td>
        <td>${renderActions(invoice)}</td>
      </tr>
    `;
  }).join('');

  renderInvoicePagination();
  if (window.lucide) window.lucide.createIcons();
}

function getTotalInvoicePages() {
  return Math.max(Math.ceil(state.invoices.length / state.pageSize), 1);
}

function renderInvoicePagination() {
  const pagination = document.getElementById('taxInvoicePagination');
  if (!pagination) return;
  const totalPages = getTotalInvoicePages();
  if (state.invoices.length <= state.pageSize) {
    pagination.innerHTML = '';
    return;
  }

  const pages = Array.from({ length: totalPages }, (_, index) => index + 1);
  pagination.innerHTML = `
    <button class="erp_btn erp_btn_secondary" type="button" data-page-action="prev" ${state.currentPage === 1 ? 'disabled' : ''}>
      이전
    </button>
    <div class="pagination_pages">
      ${pages.map((page) => `
        <button class="pagination_page ${page === state.currentPage ? 'is_active' : ''}" type="button" data-page="${page}" aria-label="${page}페이지">
          ${page}
        </button>
      `).join('')}
    </div>
    <button class="erp_btn erp_btn_secondary" type="button" data-page-action="next" ${state.currentPage === totalPages ? 'disabled' : ''}>
      다음
    </button>
  `;
}

function handlePaginationClick(event) {
  const pageButton = event.target.closest('[data-page]');
  const actionButton = event.target.closest('[data-page-action]');
  if (!pageButton && !actionButton) return;

  const totalPages = getTotalInvoicePages();
  if (pageButton) {
    state.currentPage = Number(pageButton.dataset.page);
  } else if (actionButton.dataset.pageAction === 'prev') {
    state.currentPage = Math.max(state.currentPage - 1, 1);
  } else if (actionButton.dataset.pageAction === 'next') {
    state.currentPage = Math.min(state.currentPage + 1, totalPages);
  }

  renderTaxInvoices();
}

function renderActions(invoice) {
  const actions = [buttonHtml(invoice.id, 'detail', '상세', 'eye')];
  if (invoice.status === 'DRAFT' || invoice.status === 'SEND_FAILED') {
    actions.push(buttonHtml(invoice.id, 'request-issue', '발급요청', 'send'));
  }
  if (invoice.status === 'ISSUE_REQUESTED') {
    actions.push(buttonHtml(invoice.id, 'issued', '발급완료', 'badge-check'));
    actions.push(buttonHtml(invoice.id, 'send-failed', '실패', 'circle-alert'));
  }
  if (invoice.status === 'ISSUED' || invoice.status === 'SEND_FAILED') {
    actions.push(buttonHtml(invoice.id, 'sent', '전송완료', 'check-circle-2'));
  }
  if (invoice.status !== 'SENT' && invoice.status !== 'CANCELLED' && invoice.status !== 'AMENDED') {
    actions.push(buttonHtml(invoice.id, 'cancel', '취소', 'x-circle'));
  }
  if (invoice.status === 'SENT') {
    actions.push(buttonHtml(invoice.id, 'amended', '수정발급', 'file-pen-line'));
  }
  return `<div class="row_actions">${actions.join('') || '-'}</div>`;
}

function buttonHtml(id, action, label, icon) {
  return `
    <button class="erp_btn erp_btn_secondary" type="button" data-id="${id}" data-action="${action}">
      <i data-lucide="${icon}"></i>${label}
    </button>
  `;
}

function handleListClick(event) {
  const button = event.target.closest('[data-action]');
  if (!button) return;
  const id = Number(button.dataset.id);
  const action = button.dataset.action;
  if (action === 'detail') {
    openDetailDialog(id);
    return;
  }
  if (action === 'request-issue') {
    runSimpleAction(id, 'request-issue', '발급요청 처리되었습니다.');
    return;
  }
  openStatusDialog(id, action);
}

async function openDetailDialog(id) {
  const dialog = document.getElementById('detailDialog');
  dialog?.classList.add('is_active');
  dialog?.setAttribute('aria-hidden', 'false');
  setText('detailDialogSubtitle', '');
  setDetailLoading('세금계산서 상세를 불러오고 있습니다.');

  try {
    const response = await window.ddukApi.get(`${TAX_INVOICE_API}/${id}`);
    renderDetail(response.data);
  } catch (error) {
    setDetailLoading(error.message || '상세 정보를 불러오지 못했습니다.');
  }
}

function closeDetailDialog() {
  document.getElementById('detailDialog')?.classList.remove('is_active');
  document.getElementById('detailDialog')?.setAttribute('aria-hidden', 'true');
}

function renderDetail(invoice) {
  if (!invoice) {
    setDetailLoading('상세 정보가 없습니다.');
    return;
  }
  setText('detailDialogSubtitle', `${invoice.taxInvoiceNo || ''} / ${statusLabel(invoice.status)}`);
  const content = document.getElementById('detailContent');
  if (!content) return;

  content.innerHTML = `
    <div class="detail_summary">
      ${detailField('유형', invoice.taxInvoiceType === 'SALES' ? '매출' : '매입')}
      ${detailField('작성일자', invoice.issueDate || '-')}
      ${detailField('상태', statusLabel(invoice.status))}
      ${detailField('승인번호', invoice.externalApprovalNo || '-')}
      ${detailField('공급가액', formatWon(invoice.supplyAmount || 0))}
      ${detailField('부가세', formatWon(invoice.vatAmount || 0))}
      ${detailField('합계금액', formatWon(invoice.totalAmount || 0))}
      ${detailField('외부상태', invoice.externalStatus || '-')}
    </div>
    <div class="detail_parties">
      ${partyCard('공급자', invoice.supplierName, invoice.supplierBusinessNo, invoice.supplierRepresentativeName, invoice.supplierEmail)}
      ${partyCard('공급받는자', invoice.recipientName, invoice.recipientBusinessNo, invoice.recipientRepresentativeName, invoice.recipientEmail)}
    </div>
    <div class="detail_lines">
      <table class="erp_table line_table">
        <thead>
          <tr>
            <th>품목</th>
            <th class="amount">수량</th>
            <th>단위</th>
            <th class="amount">단가</th>
            <th class="amount">공급가액</th>
            <th class="amount">부가세</th>
            <th class="amount">합계</th>
          </tr>
        </thead>
        <tbody>
          ${(invoice.lines || []).map((line) => `
            <tr>
              <td>${escapeHtml(line.itemName || '')}</td>
              <td class="amount">${line.quantity ?? '-'}</td>
              <td>${escapeHtml(line.unit || '-')}</td>
              <td class="amount">${line.unitPrice ? formatWon(line.unitPrice) : '-'}</td>
              <td class="amount">${formatWon(line.supplyAmount || 0)}</td>
              <td class="amount">${formatWon(line.vatAmount || 0)}</td>
              <td class="amount">${formatWon(line.totalAmount || 0)}</td>
            </tr>
          `).join('') || '<tr><td colspan="7" class="erp_empty_state compact">품목 라인이 없습니다.</td></tr>'}
        </tbody>
      </table>
    </div>
    ${invoice.memo ? `<div class="detail_field">${detailField('메모', invoice.memo)}</div>` : ''}
    ${invoice.failureReason ? `<div class="detail_field">${detailField('처리 사유', invoice.failureReason)}</div>` : ''}
  `;
}

function detailField(label, value) {
  return `<div class="detail_field"><span>${escapeHtml(label)}</span><strong>${escapeHtml(value)}</strong></div>`;
}

function partyCard(title, name, businessNo, representativeName, email) {
  return `
    <div class="detail_party">
      <h4>${escapeHtml(title)}</h4>
      <p><strong>${escapeHtml(name || '-')}</strong></p>
      <p>사업자번호 ${escapeHtml(businessNo || '-')}</p>
      <p>대표자 ${escapeHtml(representativeName || '-')}</p>
      <p>이메일 ${escapeHtml(email || '-')}</p>
    </div>
  `;
}

function setDetailLoading(message) {
  const content = document.getElementById('detailContent');
  if (content) {
    content.innerHTML = `<div class="erp_empty_state">${escapeHtml(message)}</div>`;
  }
}

async function runSimpleAction(id, action, message) {
  try {
    await window.ddukApi.patch(`${TAX_INVOICE_API}/${id}/${action}`, {});
    showToast(message, 'success');
    await loadTaxInvoices({ showList: true });
  } catch (error) {
    showToast(error.message || '상태 처리에 실패했습니다.', 'error');
  }
}

function openStatusDialog(id, action) {
  state.statusAction = action;
  const invoice = state.invoices.find((item) => item.id === id);
  setValue('statusInvoiceId', id);
  setValue('statusAction', action);
  setValue('approvalNo', invoice?.externalApprovalNo || '');
  setValue('externalStatus', invoice?.externalStatus || '');
  setValue('statusReason', '');
  setText('statusDialogTitle', actionLabel(action));
  setText('statusDialogSubtitle', invoice?.taxInvoiceNo || '');

  const needsApproval = action === 'issued' || action === 'sent';
  const needsExternal = action === 'issued' || action === 'sent' || action === 'send-failed';
  const needsReason = action === 'send-failed' || action === 'cancel' || action === 'amended';
  toggleField('approval', needsApproval);
  toggleField('external', needsExternal);
  toggleField('reason', needsReason);

  document.getElementById('statusDialog')?.classList.add('is_active');
  document.getElementById('statusDialog')?.setAttribute('aria-hidden', 'false');
}

function closeStatusDialog() {
  document.getElementById('statusDialog')?.classList.remove('is_active');
  document.getElementById('statusDialog')?.setAttribute('aria-hidden', 'true');
}

function openVendorLookup(target) {
  state.vendorLookupTarget = target;
  state.vendorLookupRequestId += 1;
  const nameValue = target === 'supplier' ? getValue('supplierName') : getValue('recipientName');
  setText('vendorLookupTitle', target === 'supplier' ? '공급자 검색' : '공급받는자 검색');
  setValue('vendorLookupKeyword', nameValue);
  showLookupResults('vendorLookupResults');
  renderVendorLookupEmpty('상호명을 입력한 뒤 검색하세요.');
  const dialog = document.getElementById('vendorLookupDialog');
  dialog?.classList.add('is_active');
  dialog?.setAttribute('aria-hidden', 'false');
  document.getElementById('vendorLookupKeyword')?.focus();
  if (nameValue.trim()) {
    searchVendors();
  }
}

function closeVendorLookup() {
  document.getElementById('vendorLookupDialog')?.classList.remove('is_active');
  document.getElementById('vendorLookupDialog')?.setAttribute('aria-hidden', 'true');
  state.vendorLookupTarget = null;
  state.vendorLookupRequestId += 1;
  setVendorSearchBusy(false);
}

let vendorSearchTimer = null;

function debounceVendorSearch() {
  clearTimeout(vendorSearchTimer);
  vendorSearchTimer = setTimeout(() => {
    if (document.getElementById('vendorLookupDialog')?.classList.contains('is_active')) {
      searchVendors();
    }
  }, 280);
}

async function searchVendors() {
  const keyword = getValue('vendorLookupKeyword').trim();
  showLookupResults('vendorLookupResults');
  if (!keyword) {
    renderVendorLookupEmpty('검색할 상호명을 입력해주세요.');
    return;
  }

  const requestId = ++state.vendorLookupRequestId;
  setVendorSearchBusy(true);
  const results = document.getElementById('vendorLookupResults');
  if (results) results.innerHTML = '<div class="erp_empty_state">거래처를 검색하고 있습니다.</div>';

  try {
    const vendors = await window.ddukApi.get(`${VENDOR_SEARCH_API}?keyword=${encodeURIComponent(keyword)}`);
    if (requestId !== state.vendorLookupRequestId) return;
    renderVendorLookupResults(vendors || []);
  } catch (error) {
    if (requestId !== state.vendorLookupRequestId) return;
    renderVendorLookupEmpty(error.message || '거래처 검색에 실패했습니다.');
  } finally {
    if (requestId === state.vendorLookupRequestId) {
      setVendorSearchBusy(false);
    }
  }
}

function renderVendorLookupResults(vendors) {
  const results = document.getElementById('vendorLookupResults');
  if (!results) return;
  if (!vendors.length) {
    results.innerHTML = `
      <div class="erp_empty_state">
        검색된 거래처가 없습니다. 필요한 경우 하단의 거래처 등록으로 이동하세요.
      </div>
    `;
    return;
  }

  results.innerHTML = vendors.map((vendor) => `
    <button class="vendor_lookup_row" type="button" data-vendor-id="${vendor.id}">
      <span>
        <strong>${escapeHtml(vendor.name || '')}</strong>
        <small>${escapeHtml(vendor.businessRegistrationNo || '-')} / 대표 ${escapeHtml(vendor.representativeName || '-')} / ${escapeHtml(vendor.email || '-')}</small>
      </span>
      <span class="vendor_lookup_code">${escapeHtml(vendor.vendorCode || `ID ${vendor.id}`)}</span>
    </button>
  `).join('');

  state.vendorLookupResults = vendors;
}

function renderVendorLookupEmpty(message) {
  const results = document.getElementById('vendorLookupResults');
  if (results) {
    results.innerHTML = `<div class="erp_empty_state">${escapeHtml(message)}</div>`;
  }
  state.vendorLookupResults = [];
}

function setVendorSearchBusy(isBusy) {
  const button = document.getElementById('vendorLookupSearchButton');
  const keyword = document.getElementById('vendorLookupKeyword');
  if (button) {
    button.disabled = isBusy;
    button.classList.toggle('is_loading', isBusy);
  }
  if (keyword) {
    keyword.setAttribute('aria-busy', String(isBusy));
  }
}

function handleVendorSelect(event) {
  const row = event.target.closest('[data-vendor-id]');
  if (!row) return;
  const vendor = (state.vendorLookupResults || []).find((item) => String(item.id) === row.dataset.vendorId);
  if (!vendor || !state.vendorLookupTarget) return;

  const prefix = state.vendorLookupTarget === 'supplier' ? 'supplier' : 'recipient';
  setValue(`${prefix}BusinessNo`, vendor.businessRegistrationNo || '');
  setValue(`${prefix}Name`, vendor.name || '');
  setValue(`${prefix}RepresentativeName`, vendor.representativeName || '');
  setValue(`${prefix}Email`, vendor.email || '');
  closeVendorLookup();
  showToast(`${vendor.name || '거래처'} 정보를 반영했습니다.`, 'success');
}

function openItemLookup() {
  const keyword = getValue('itemName');
  setValue('itemLookupKeyword', keyword);
  showLookupResults('itemLookupResults');
  renderItemLookupEmpty('품목명을 입력한 뒤 검색하세요.');
  const dialog = document.getElementById('itemLookupDialog');
  dialog?.classList.add('is_active');
  dialog?.setAttribute('aria-hidden', 'false');
  document.getElementById('itemLookupKeyword')?.focus();
  if (keyword.trim()) {
    searchItems();
  }
}

function closeItemLookup() {
  document.getElementById('itemLookupDialog')?.classList.remove('is_active');
  document.getElementById('itemLookupDialog')?.setAttribute('aria-hidden', 'true');
}

let itemSearchTimer = null;

function debounceItemSearch() {
  clearTimeout(itemSearchTimer);
  itemSearchTimer = setTimeout(() => {
    if (document.getElementById('itemLookupDialog')?.classList.contains('is_active')) {
      searchItems();
    }
  }, 280);
}

async function searchItems() {
  clearTimeout(itemSearchTimer);
  const keyword = getValue('itemLookupKeyword').trim();
  showLookupResults('itemLookupResults');
  if (!keyword) {
    renderItemLookupEmpty('검색할 품목명을 입력해주세요.');
    return;
  }

  const results = document.getElementById('itemLookupResults');
  if (results) results.innerHTML = '<div class="erp_empty_state">품목을 검색하고 있습니다.</div>';

  try {
    const items = await window.ddukApi.get(`${ITEM_SEARCH_API}?name=${encodeURIComponent(keyword)}`);
    renderItemLookupResults(items || []);
  } catch (error) {
    renderItemLookupEmpty(error.message || '품목 검색에 실패했습니다.');
  }
}

const ITEM_LOOKUP_PAGE_SIZE = 5;

function renderItemLookupResults(items, page) {
  const results = document.getElementById('itemLookupResults');
  if (!results) return;
  state.itemLookupResults = items;

  if (!items.length) {
    renderItemLookupEmpty('검색된 품목이 없습니다.');
    return;
  }

  const totalPages = Math.ceil(items.length / ITEM_LOOKUP_PAGE_SIZE);
  const currentPage = Math.min(Math.max(page || 1, 1), totalPages);
  state.itemLookupPage = currentPage;

  const startIdx = (currentPage - 1) * ITEM_LOOKUP_PAGE_SIZE;
  const pageItems = items.slice(startIdx, startIdx + ITEM_LOOKUP_PAGE_SIZE);

  let html = pageItems.map((item) => `
    <button class="item_lookup_row" type="button" data-item-id="${item.id}">
      <span>
        <strong>${escapeHtml(item.name || '')}</strong>
        <small>${escapeHtml(item.category || '-')} / ${escapeHtml(item.spec || '-')} / 단위 ${escapeHtml(item.unit || '-')} / 단가 ${formatWon(item.unitPrice || 0)}</small>
      </span>
      <span class="item_lookup_code">${escapeHtml(item.itemCode || `ID ${item.id}`)}</span>
    </button>
  `).join('');

  if (totalPages > 1) {
    html += `<div class="item_lookup_pagination" style="display:flex;align-items:center;justify-content:center;gap:.375rem;padding:.625rem 0;">`;
    html += `<button type="button" class="erp_btn erp_btn_secondary" data-item-page="${currentPage - 1}" style="padding:.25rem .625rem;font-size:.75rem;" ${currentPage <= 1 ? 'disabled' : ''}>&laquo; 이전</button>`;
    for (let i = 1; i <= totalPages; i++) {
      const activeStyle = i === currentPage
        ? 'background:#4f46e5;color:#fff;border-color:#4f46e5'
        : '';
      html += `<button type="button" class="erp_btn erp_btn_secondary" data-item-page="${i}" style="min-width:1.75rem;padding:.25rem .375rem;font-size:.75rem;font-weight:700;${activeStyle}">${i}</button>`;
    }
    html += `<button type="button" class="erp_btn erp_btn_secondary" data-item-page="${currentPage + 1}" style="padding:.25rem .625rem;font-size:.75rem;" ${currentPage >= totalPages ? 'disabled' : ''}>다음 &raquo;</button>`;
    html += `<span style="font-size:.7rem;color:#9ca3af;margin-left:.375rem;">총 ${items.length}건</span>`;
    html += `</div>`;
  }

  results.innerHTML = html;

  results.querySelectorAll('[data-item-page]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const p = Number(btn.dataset.itemPage);
      if (p >= 1 && p <= totalPages) {
        renderItemLookupResults(state.itemLookupResults, p);
      }
    });
  });
}

function renderItemLookupEmpty(message) {
  const results = document.getElementById('itemLookupResults');
  if (results) {
    results.innerHTML = `<div class="erp_empty_state">${escapeHtml(message)}</div>`;
  }
  state.itemLookupResults = [];
}

function handleItemSelect(event) {
  const row = event.target.closest('[data-item-id]');
  if (!row) return;
  const item = state.itemLookupResults.find((found) => String(found.id) === row.dataset.itemId);
  if (!item) return;

  setValue('itemName', item.name || '');
  setValue('itemUnit', item.unit || '');
  setValue('itemUnitPrice', item.unitPrice || '');
  setValue('lineUnitPrice', formatNumber(Number(item.unitPrice || 0)));
  if (parseMoney(getValue('quantity')) === 0) {
    setValue('quantity', '1');
  }
  if (Number(item.unitPrice || 0) > 0 && parseMoney(getValue('supplyAmount')) === 0) {
    setValue('supplyAmount', formatNumber(Number(item.unitPrice)));
    calculateVat();
  }
  setText('itemMetaText', `${item.itemCode || '품목'} / ${item.spec || '-'} / ${item.unit || '-'} / 단가 ${formatWon(item.unitPrice || 0)}`);
  closeItemLookup();
  showToast(`${item.name || '품목'} 정보를 반영했습니다.`, 'success');
}

function buildLineFromForm() {
  const supplyAmount = parseMoney(getValue('supplyAmount'));
  const vatAmount = parseMoney(getValue('vatAmount'));
  const itemName = getValue('itemName').trim();
  if (!itemName && supplyAmount === 0 && vatAmount === 0) {
    return null;
  }
  return {
    itemName,
    unit: getValue('itemUnit'),
    quantity: parseMoney(getValue('quantity')) || null,
    unitPrice: parseMoney(getValue('lineUnitPrice')) || parseMoney(getValue('itemUnitPrice')) || null,
    supplyAmount,
    vatAmount,
    totalAmount: supplyAmount + vatAmount,
  };
}

function addCurrentLine() {
  const headerSnapshot = readHeaderSnapshot();
  const line = buildLineFromForm();
  if (!line || !line.itemName) {
    showToast('추가할 품목을 입력하거나 검색해주세요.', 'error');
    return;
  }
  if (line.supplyAmount <= 0) {
    showToast('공급가액은 0보다 커야 합니다.', 'error');
    return;
  }
  state.lines.push(line);
  clearLineEditor();
  restoreHeaderSnapshot(headerSnapshot);
  renderLineList();
  showToast('품목 라인을 추가했습니다.', 'success');
}

function readHeaderSnapshot() {
  return {
    supplierBusinessNo: getValue('supplierBusinessNo'),
    supplierName: getValue('supplierName'),
    supplierRepresentativeName: getValue('supplierRepresentativeName'),
    supplierEmail: getValue('supplierEmail'),
    recipientBusinessNo: getValue('recipientBusinessNo'),
    recipientName: getValue('recipientName'),
    recipientRepresentativeName: getValue('recipientRepresentativeName'),
    recipientEmail: getValue('recipientEmail'),
    issueDate: getValue('issueDate'),
    vatType: getValue('vatType'),
    memo: getValue('memo'),
  };
}

function restoreHeaderSnapshot(snapshot) {
  Object.entries(snapshot).forEach(([id, value]) => setValue(id, value));
}

function clearLineEditor() {
  setValue('itemName', '');
  setValue('itemUnit', '');
  setValue('itemUnitPrice', '');
  setValue('quantity', '1');
  setValue('lineUnitPrice', '0');
  setValue('supplyAmount', '0');
  setValue('vatAmount', '0');
  setValue('totalAmount', '0');
  setText('itemMetaText', '품목을 검색해 선택할 수 있습니다.');
}

function handleLineListClick(event) {
  const button = event.target.closest('[data-remove-line]');
  if (!button) return;
  const index = Number(button.dataset.removeLine);
  state.lines.splice(index, 1);
  renderLineList();
}

function renderLineList() {
  const tbody = document.getElementById('lineListBody');
  if (!tbody) return;
  if (!state.lines.length) {
    tbody.innerHTML = '<tr><td colspan="8" class="erp_empty_state compact">추가된 품목이 없습니다.</td></tr>';
    return;
  }

  tbody.innerHTML = state.lines.map((line, index) => `
    <tr>
      <td>${escapeHtml(line.itemName)}</td>
      <td class="amount">${line.quantity ?? '-'}</td>
      <td>${escapeHtml(line.unit || '-')}</td>
      <td class="amount">${line.unitPrice ? formatWon(line.unitPrice) : '-'}</td>
      <td class="amount">${formatWon(line.supplyAmount)}</td>
      <td class="amount">${formatWon(line.vatAmount)}</td>
      <td class="amount">${formatWon(line.totalAmount)}</td>
      <td>
        <button class="erp_btn erp_btn_secondary erp_btn_icon" type="button" data-remove-line="${index}" aria-label="품목 삭제">
          <i data-lucide="trash-2"></i>
        </button>
      </td>
    </tr>
  `).join('');
  if (window.lucide) window.lucide.createIcons();
}

function openPurchaseOrderLookup() {
  setValue('purchaseOrderLookupKeyword', getValue('recipientName') || getValue('supplierName'));
  showLookupResults('purchaseOrderLookupResults');
  renderPurchaseOrderLookupEmpty('발주번호 또는 거래처명을 입력한 뒤 검색하세요.');
  const dialog = document.getElementById('purchaseOrderLookupDialog');
  dialog?.classList.add('is_active');
  dialog?.setAttribute('aria-hidden', 'false');
  document.getElementById('purchaseOrderLookupKeyword')?.focus();
  if (getValue('purchaseOrderLookupKeyword').trim()) {
    searchPurchaseOrders();
  }
}

function closePurchaseOrderLookup() {
  document.getElementById('purchaseOrderLookupDialog')?.classList.remove('is_active');
  document.getElementById('purchaseOrderLookupDialog')?.setAttribute('aria-hidden', 'true');
}

async function searchPurchaseOrders() {
  const keyword = getValue('purchaseOrderLookupKeyword').trim();
  showLookupResults('purchaseOrderLookupResults');
  const params = new URLSearchParams();
  if (keyword) params.set('keyword', keyword);
  params.set('all', 'true');
  const results = document.getElementById('purchaseOrderLookupResults');
  if (results) results.innerHTML = '<div class="erp_empty_state">발주서를 검색하고 있습니다.</div>';

  try {
    const orders = await window.ddukApi.get(`${PURCHASE_ORDER_API}?${params.toString()}`);
    renderPurchaseOrderLookupResults(orders || []);
  } catch (error) {
    renderPurchaseOrderLookupEmpty(error.message || '발주서 검색에 실패했습니다.');
  }
}

function renderPurchaseOrderLookupResults(orders) {
  const results = document.getElementById('purchaseOrderLookupResults');
  if (!results) return;
  state.purchaseOrderLookupResults = orders;
  if (!orders.length) {
    renderPurchaseOrderLookupEmpty('검색된 발주서가 없습니다.');
    return;
  }

  results.innerHTML = orders.map((order) => `
    <button class="purchase_order_lookup_row" type="button" data-purchase-order-id="${order.purchaseOrderId}">
      <span>
        <strong>${escapeHtml(order.purchaseOrderNo || '')}</strong>
        <small>${escapeHtml(order.vendorName || '-')} / ${escapeHtml(order.status || '-')} / ${formatWon(order.totalAmount || 0)} / 품목 ${(order.items || []).length}개</small>
      </span>
      <span class="item_lookup_code">${escapeHtml(order.orderDate || '')}</span>
    </button>
  `).join('');
}

function renderPurchaseOrderLookupEmpty(message) {
  const results = document.getElementById('purchaseOrderLookupResults');
  if (results) {
    results.innerHTML = `<div class="erp_empty_state">${escapeHtml(message)}</div>`;
  }
  state.purchaseOrderLookupResults = [];
}

function showLookupResults(resultsId) {
  const results = document.getElementById(resultsId);
  const head = document.querySelector(`[data-result-head="${resultsId}"]`);
  results?.classList.remove('is_collapsed');
  head?.classList.remove('is_collapsed');
  if (results) results.setAttribute('aria-hidden', 'false');
}

function hideLookupResults(resultsId) {
  const results = document.getElementById(resultsId);
  const head = document.querySelector(`[data-result-head="${resultsId}"]`);
  results?.classList.add('is_collapsed');
  head?.classList.add('is_collapsed');
  if (results) results.setAttribute('aria-hidden', 'true');
}

async function handlePurchaseOrderSelect(event) {
  const row = event.target.closest('[data-purchase-order-id]');
  if (!row) return;
  const order = state.purchaseOrderLookupResults.find((found) => String(found.purchaseOrderId) === row.dataset.purchaseOrderId);
  if (!order) return;

  setValue('taxInvoiceType', 'PURCHASE');
  updateFormTypeLabel();
  setValue('supplierName', order.vendorName || getValue('supplierName'));
  if (order.vendorId) {
    try {
      const vendor = await window.ddukApi.get(`/api/v1/inventory/vendors/${order.vendorId}`);
      setValue('supplierBusinessNo', vendor.businessRegistrationNo || getValue('supplierBusinessNo'));
      setValue('supplierName', vendor.name || order.vendorName || getValue('supplierName'));
      setValue('supplierRepresentativeName', vendor.representativeName || '');
      setValue('supplierEmail', vendor.email || '');
    } catch (error) {
      showToast('발주 거래처 상세 정보는 불러오지 못했습니다. 사업자번호를 확인해주세요.', 'error');
    }
  }
  if (!getValue('memo')) {
    setValue('memo', `${order.purchaseOrderNo || '발주서'} 기반 세금계산서`);
  }
  const newLines = (order.items || []).map((item) => ({
    itemName: item.itemName || '',
    unit: item.unit || '',
    quantity: Number(item.quantity || 0) || null,
    unitPrice: Number(item.unitPrice || 0) || null,
    supplyAmount: Number(item.supplyAmount || 0),
    vatAmount: Number(item.taxAmount || 0),
    totalAmount: Number(item.lineAmount || 0),
  })).filter((line) => line.itemName && line.supplyAmount > 0);
  state.lines.push(...newLines);
  renderLineList();
  closePurchaseOrderLookup();
  showToast(`${order.purchaseOrderNo || '발주서'}의 품목 ${newLines.length}건을 추가했습니다.`, 'success');
}

async function handleStatusSubmit(event) {
  event.preventDefault();
  const id = getValue('statusInvoiceId');
  const action = getValue('statusAction');
  const payload = {
    approvalNo: getValue('approvalNo'),
    externalStatus: getValue('externalStatus'),
    reason: getValue('statusReason'),
  };

  try {
    await window.ddukApi.patch(`${TAX_INVOICE_API}/${id}/${action}`, payload);
    closeStatusDialog();
    showToast('상태가 처리되었습니다.', 'success');
    await loadTaxInvoices({ showList: true });
  } catch (error) {
    showToast(error.message || '상태 처리에 실패했습니다.', 'error');
  }
}

function calculateVat() {
  const supplyAmount = parseMoney(getValue('supplyAmount'));
  const vatType = getValue('vatType');
  const vatAmount = VAT_ZERO_TYPES.has(vatType) ? 0 : Math.floor(supplyAmount * 0.1);
  setValue('vatAmount', formatNumber(vatAmount));
  recalculateAmounts();
}

function recalculateAmounts() {
  const supplyAmount = parseMoney(getValue('supplyAmount'));
  const vatAmount = parseMoney(getValue('vatAmount'));
  setValue('totalAmount', formatNumber(supplyAmount + vatAmount));
}

function recalculateLineFromQuantity() {
  const quantity = parseMoney(getValue('quantity')) || 1;
  const unitPrice = parseMoney(getValue('lineUnitPrice'));
  if (unitPrice <= 0) return;
  setValue('supplyAmount', formatNumber(quantity * unitPrice));
  calculateVat();
}

function resetForm() {
  document.getElementById('taxInvoiceForm')?.reset();
  const issueDate = document.getElementById('issueDate');
  if (issueDate) issueDate.valueAsDate = new Date();
  state.lines = [];
  setDefaultSupplier();
  renderLineList();
  clearLineEditor();
  recalculateAmounts();
}

function setLoading() {
  const tbody = document.getElementById('taxInvoiceListBody');
  if (tbody) {
    tbody.innerHTML = '<tr><td colspan="9" class="erp_empty_state">세금계산서를 조회하고 있습니다.</td></tr>';
  }
}

function renderEmpty(message) {
  const tbody = document.getElementById('taxInvoiceListBody');
  if (tbody) {
    tbody.innerHTML = `<tr><td colspan="9" class="erp_empty_state">${escapeHtml(message)}</td></tr>`;
  }
  setText('listSummary', '0건');
  const pagination = document.getElementById('taxInvoicePagination');
  if (pagination) pagination.innerHTML = '';
}

function statusClass(status) {
  switch (status) {
    case 'DRAFT': return 'draft';
    case 'ISSUE_REQUESTED': return 'pending';
    case 'ISSUED': return 'success';
    case 'SENT': return 'success';
    case 'SEND_FAILED': return 'danger';
    case 'CANCELLED': return 'closed';
    case 'AMENDED': return 'closed';
    default: return 'draft';
  }
}

function statusLabel(status) {
  return {
    DRAFT: '작성중',
    ISSUE_REQUESTED: '발급요청',
    ISSUED: '발급완료',
    SEND_FAILED: '전송실패',
    SENT: '전송완료',
    CANCELLED: '취소',
    AMENDED: '수정발급',
  }[status] || status;
}

function actionLabel(action) {
  return {
    issued: '발급완료',
    sent: '전송완료',
    'send-failed': '전송실패',
    cancel: '취소',
    amended: '수정발급',
  }[action] || '상태 처리';
}

function toggleField(name, visible) {
  const field = document.querySelector(`[data-status-field="${name}"]`);
  if (field) field.style.display = visible ? '' : 'none';
}

function sanitizeMoneyInput(input) {
  input.value = input.value.replace(/[^\d]/g, '');
}

function parseMoney(value) {
  const parsed = Number(String(value || '').replace(/[^\d]/g, ''));
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 0;
}

function formatNumber(value) {
  const safe = Number.isFinite(Number(value)) ? Math.max(Math.floor(Number(value)), 0) : 0;
  return safe ? moneyFormatter.format(safe) : '0';
}

function formatWon(value) {
  return `${formatNumber(value)}원`;
}

function getValue(id) {
  return document.getElementById(id)?.value || '';
}

function setValue(id, value) {
  const el = document.getElementById(id);
  if (el) el.value = value;
}

function setText(id, value) {
  const el = document.getElementById(id);
  if (el) el.textContent = value;
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

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}
