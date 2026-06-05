(function () {
  const state = {
    rows: [],
    collapsed: new Set()
  };
  const money = new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 0 });

  document.addEventListener("DOMContentLoaded", () => {
    setDefaultPeriod();
    bindEvents();
    loadTrialBalance();
  });

  function bindEvents() {
    byId("searchButton").addEventListener("click", loadTrialBalance);
    byId("expandAllButton").addEventListener("click", () => {
      state.collapsed.clear();
      renderRows();
    });
    byId("collapseAllButton").addEventListener("click", () => {
      state.rows.filter(row => hasChildren(row.accountId)).forEach(row => state.collapsed.add(row.accountId));
      renderRows();
    });
    byId("excelButton").addEventListener("click", downloadExcel);
    byId("printButton").addEventListener("click", () => window.print());
  }

  async function loadTrialBalance() {
    const query = buildQuery();
    if (!query) return;
    
    showLoader(true);
    
    try {
      const payload = await window.ddukApi.get(`/api/v1/accounting/reports/trial-balance?${query}`);
      state.rows = payload.data.rows || [];
      state.collapsed.clear();
      renderSummary(payload.data.summary);
      
      const periodLabel = byId("periodLabel");
      if (periodLabel) {
        periodLabel.textContent = `${payload.data.startDate} ~ ${payload.data.endDate}`;
      }
      
      renderRows();
    } catch (error) {
      toast(error.message || "조회 중 오류가 발생했습니다.");
    } finally {
      showLoader(false);
    }
  }

  function showLoader(show) {
    const loader = byId("tableLoader");
    const container = document.querySelector(".erp_table_container");
    if (loader) {
      loader.style.display = show ? "flex" : "none";
    }
    if (container) {
      container.style.opacity = show ? "0.5" : "1";
    }
  }

  function renderSummary(summary) {
    setMoney("openingDebitTotal", summary?.openingDebitTotal || 0);
    setMoney("openingCreditTotal", summary?.openingCreditTotal || 0);
    setMoney("periodDebitTotal", summary?.periodDebitTotal || 0);
    setMoney("periodCreditTotal", summary?.periodCreditTotal || 0);
    setMoney("closingDebitTotal", summary?.closingDebitTotal || 0);
    setMoney("closingCreditTotal", summary?.closingCreditTotal || 0);
    
    const status = document.querySelector('[data-summary="balanceStatus"]');
    if (status) {
      status.textContent = summary?.balanceStatus || "-";
      status.className = summary?.balanceStatus === "일치" ? "balance-ok" : "balance-warning";
    }
    
    const foot = byId("trialTableFoot");
    if (foot) {
      foot.innerHTML = `
        <tr class="summary-row">
          <td colspan="2">합계</td>
          <td class="amount">${format(summary?.openingDebitTotal)}</td>
          <td class="amount">${format(summary?.openingCreditTotal)}</td>
          <td class="amount">${format(summary?.periodDebitTotal)}</td>
          <td class="amount">${format(summary?.periodCreditTotal)}</td>
          <td class="amount">${format(summary?.closingDebitTotal)}</td>
          <td class="amount">${format(summary?.closingCreditTotal)}</td>
        </tr>
      `;
    }
  }

  function renderRows() {
    const body = byId("trialTableBody");
    if (!body) return;

    if (!state.rows.length) {
      body.innerHTML = `
        <tr>
          <td colspan="8">
            <div class="empty-state">
              <i data-lucide="info" style="width: 2rem; height: 2rem; color: var(--text-muted); margin-bottom: 0.5rem;"></i>
              <p>조회된 회계 원장 및 합계잔액 데이터가 존재하지 않습니다.</p>
            </div>
          </td>
        </tr>
      `;
      renderIcons();
      return;
    }
    
    body.innerHTML = state.rows.map(row => {
      const hidden = isHidden(row);
      const children = hasChildren(row.accountId);
      const indent = Math.max(0, (row.level || 1) - 1) * 18;
      const toggle = children
        ? `<button type="button" class="tree-button" data-toggle="${row.accountId}" aria-label="계정 펼침"><i data-lucide="${state.collapsed.has(row.accountId) ? "chevron-right" : "chevron-down"}"></i></button>`
        : `<span class="tree-spacer"></span>`;
      return `
        <tr class="${hidden ? "hidden-row" : ""}" data-row-id="${row.accountId}" data-parent-id="${row.parentAccountId || ""}">
          <td>${row.accountCode}</td>
          <td>
            <span class="account-name level-${row.level || 1} ${row.leaf ? "leaf" : ""}" style="padding-left:${indent}px">
              ${toggle}${row.accountName}
            </span>
          </td>
          <td class="amount">${format(row.openingDebit)}</td>
          <td class="amount">${format(row.openingCredit)}</td>
          <td class="amount">${format(row.periodDebit)}</td>
          <td class="amount">${format(row.periodCredit)}</td>
          <td class="amount">${format(row.closingDebit)}</td>
          <td class="amount">${format(row.closingCredit)}</td>
        </tr>
      `;
    }).join("");
    
    body.querySelectorAll("[data-toggle]").forEach(button => {
      button.addEventListener("click", () => {
        const id = Number(button.dataset.toggle);
        if (state.collapsed.has(id)) state.collapsed.delete(id);
        else state.collapsed.add(id);
        renderRows();
      });
    });
    renderIcons();
  }

  function isHidden(row) {
    let parentId = row.parentAccountId;
    while (parentId) {
      if (state.collapsed.has(parentId)) return true;
      const parent = state.rows.find(item => item.accountId === parentId);
      parentId = parent?.parentAccountId;
    }
    return false;
  }

  function hasChildren(accountId) {
    return state.rows.some(row => row.parentAccountId === accountId);
  }

  function downloadExcel() {
    const query = buildQuery();
    if (!query) return;
    const baseUrl = window.ddukApi.getBaseUrl();
    window.location.href = `${baseUrl}/api/v1/accounting/reports/trial-balance/export?${query}`;
  }

  function buildQuery() {
    const startDate = byId("startDate").value;
    const endDate = byId("endDate").value;
    if (!startDate || !endDate) {
      toast("시작일과 종료일을 입력해주세요.");
      return null;
    }
    if (endDate < startDate) {
      toast("종료일은 시작일보다 빠를 수 없습니다.");
      return null;
    }
    const params = new URLSearchParams({
      startDate,
      endDate,
      reportBasis: byId("reportBasis").value,
      accountLevel: byId("accountLevel").value,
      includeZeroBalance: byId("includeZeroBalance").checked,
      summaryOnly: byId("summaryOnly").checked,
      profitLossFormat: byId("profitLossFormat").checked
    });
    return params.toString();
  }

  function setDefaultPeriod() {
    const now = new Date();
    const start = new Date(now.getFullYear(), now.getMonth(), 1);
    const end = new Date(now.getFullYear(), now.getMonth() + 1, 0);
    byId("startDate").value = toDateInput(start);
    byId("endDate").value = toDateInput(end);
  }

  function setMoney(key, value) {
    const element = document.querySelector(`[data-summary-money="${key}"]`);
    if (element) element.textContent = format(value);
  }

  function format(value) {
    const numeric = Number(value || 0);
    if (numeric < 0) return `(${money.format(Math.abs(numeric))})`;
    return money.format(numeric);
  }

  function toDateInput(date) {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
  }

  function byId(id) {
    return document.getElementById(id);
  }

  function toast(message) {
    const element = byId("toast");
    if (!element) return;
    element.textContent = message;
    element.classList.add("show");
    window.setTimeout(() => element.classList.remove("show"), 2800);
  }

  function renderIcons() {
    if (window.lucide) window.lucide.createIcons();
  }
})();
