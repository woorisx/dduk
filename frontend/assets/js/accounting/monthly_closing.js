(function () {
  const API_PATH = '/api/v1/accounting/monthly-closing';
  const state = {
    periods: [],
    selected: null,
    lastValidation: new Map()
  };

  const money = new Intl.NumberFormat("ko-KR", { style: "currency", currency: "KRW", maximumFractionDigits: 0 });
  const number = new Intl.NumberFormat("ko-KR");

  document.addEventListener("DOMContentLoaded", () => {
    const now = new Date();
    byId("filterYear").value = now.getFullYear();
    byId("periodYear").value = now.getFullYear();
    byId("periodMonth").value = now.getMonth() + 1;
    setDefaultDates();
    bindEvents();
    loadAll();
    renderIcons();
  });

  function bindEvents() {
    byId("reloadButton").addEventListener("click", loadAll);
    byId("createPeriodButton").addEventListener("click", () => byId("periodDialog").showModal());
    byId("createYearButton").addEventListener("click", createYearPeriods);
    byId("savePeriodButton").addEventListener("click", createPeriod);
    byId("periodMonth").addEventListener("change", setDefaultDates);
    byId("periodYear").addEventListener("change", setDefaultDates);

    // 하단 마감 통제 버튼 이벤트 바인딩
    byId("validateButton").addEventListener("click", () => {
      if (state.selected) validatePeriod(state.selected);
    });
    byId("closeButton").addEventListener("click", () => {
      if (state.selected) closePeriod(state.selected);
    });
    byId("reopenButton").addEventListener("click", () => {
      if (state.selected) reopenPeriod(state.selected);
    });
    byId("logButton").addEventListener("click", () => {
      if (state.selected) loadLogs(state.selected);
    });
  }

  async function loadAll() {
    await Promise.all([loadPeriods(), loadSummary()]);
    if (state.selected) {
      const updated = state.periods.find(p => p.id === state.selected.id);
      if (updated) {
        state.selected = updated;
        updateActionButtons(updated);
      }
    }
  }

  async function loadPeriods() {
    const year = byId("filterYear").value;
    const response = await api(`/periods${year ? `?fiscalYear=${encodeURIComponent(year)}` : ""}`);
    let periods = response.data || [];
    if (year === "2026") {
      periods = periods.filter(p => p.fiscalMonth <= 6);
    }
    state.periods = periods;
    renderPeriods();
  }

  async function loadSummary(period) {
    const target = period || state.selected;
    const query = target ? `?fiscalYear=${target.fiscalYear}&fiscalMonth=${target.fiscalMonth}` : "";
    try {
      const response = await api(`/summary${query}`);
      renderSummary(response.data);
    } catch (error) {
      renderSummary(null);
    }
  }

  function renderPeriods() {
    const body = byId("periodTableBody");
    body.innerHTML = "";
    if (!state.periods.length) {
      body.innerHTML = `<tr><td colspan="12">등록된 회계기간이 없습니다.</td></tr>`;
      return;
    }
    body.innerHTML = state.periods.map(period => {
      const canClose = period.status === "OPEN" || period.status === "REOPENED" || period.status === "PRE_CLOSING";
      const canReopen = period.status === "CLOSED";
      return `
        <tr data-period-id="${period.id}">
          <td><button class="text-link" type="button" data-action="select" data-id="${period.id}">${period.periodKey}</button></td>
          <td>${period.startDate || "-"}</td>
          <td>${period.endDate || "-"}</td>
          <td>${pill(period.status)}</td>
          <td class="amount">${number.format(period.voucherCount || 0)}</td>
          <td class="amount">${money.format(period.totalDebit || 0)}</td>
          <td class="amount">${money.format(period.totalCredit || 0)}</td>
          <td>${pill(period.validationStatus || "SUCCESS")}</td>
          <td>${formatDateTime(period.closedAt)}</td>
          <td>${period.closedBy || "-"}</td>
          <td>${period.reopened ? "Y" : "N"}</td>
          <td>
            <div class="row-actions">
              <button type="button" data-action="validate" data-id="${period.id}" title="↺ 재검증" aria-label="재검증"><i data-lucide="check-circle"></i></button>
              <button type="button" data-action="close" data-id="${period.id}" title="🔒 기간 마감" aria-label="기간 마감" ${canClose ? "" : "disabled"}><i data-lucide="lock"></i></button>
              <button type="button" data-action="reopen" data-id="${period.id}" title="🔓 마감 해제" aria-label="마감 해제" ${canReopen ? "" : "disabled"}><i data-lucide="unlock"></i></button>
              <button type="button" data-action="logs" data-id="${period.id}" title="마감 로그" aria-label="마감 로그"><i data-lucide="history"></i></button>
            </div>
          </td>
        </tr>
      `;
    }).join("");
    body.querySelectorAll("button[data-action]").forEach(button => {
      button.addEventListener("click", handlePeriodAction);
    });
    renderIcons();
  }

  async function handlePeriodAction(event) {
    try {
      const action = event.currentTarget.dataset.action;
      const period = state.periods.find(item => String(item.id) === event.currentTarget.dataset.id);
      if (!period) return;
      state.selected = period;
      byId("selectedPeriodHint").textContent = `${period.periodKey} 선택됨`;
      updateActionButtons(period);
      await loadSummary(period);
      resetChecklistAndAlertsBySummary(period);
      if (action === "select") return;
      if (action === "validate") return validatePeriod(period);
      if (action === "close") return closePeriod(period);
      if (action === "reopen") return reopenPeriod(period);
      if (action === "logs") return loadLogs(period);
    } catch (error) {
      toast(error.message);
    }
  }

  function updateActionButtons(period) {
    const actionsArea = byId("closingActions");
    if (!period) {
      if (actionsArea) actionsArea.style.display = "none";
      return;
    }
    if (actionsArea) actionsArea.style.display = "flex";

    const canClose = period.status === "OPEN" || period.status === "REOPENED" || period.status === "PRE_CLOSING";
    const canReopen = period.status === "CLOSED";

    const validateBtn = byId("validateButton");
    const closeBtn = byId("closeButton");
    const reopenBtn = byId("reopenButton");

    if (validateBtn) validateBtn.disabled = !canClose;
    if (closeBtn) closeBtn.disabled = !canClose;
    if (reopenBtn) reopenBtn.disabled = !canReopen;
  }

  async function validatePeriod(period) {
    const response = await api(`/periods/${period.fiscalYear}/${period.fiscalMonth}/validate`, {
      method: "POST",
      body: { actor: "SYSTEM" }
    });
    state.lastValidation.set(period.periodKey, response.data);
    renderValidation(response.data);
  }

  async function closePeriod(period) {
    const validation = state.lastValidation.get(period.periodKey);
    if (!validation || !validation.closable) {
      await validatePeriod(period);
      const refreshed = state.lastValidation.get(period.periodKey);
      if (!refreshed || !refreshed.closable) {
        toast("마감 검증 오류를 먼저 처리해야 합니다.");
        return;
      }
    }
    await api(`/periods/${period.fiscalYear}/${period.fiscalMonth}/close`, {
      method: "POST",
      body: { actor: "SYSTEM" }
    });
    toast(`${period.periodKey} 월 마감이 완료되었습니다.`);
    await loadAll();
  }

  async function reopenPeriod(period) {
    await api(`/periods/${period.fiscalYear}/${period.fiscalMonth}/reopen`, {
      method: "POST",
      body: { actor: "SYSTEM" }
    });
    toast(`${period.periodKey} 기간이 재오픈되었습니다.`);
    await loadAll();
  }

  async function loadLogs(period) {
    const response = await api(`/periods/${period.id}/logs`);
    const logs = response.data || [];
    byId("logList").innerHTML = logs.length ? logs.map(log => `
      <div class="log-item">
        <strong>${log.actionType} · ${log.actor || "SYSTEM"}</strong>
        <span>${formatDateTime(log.actionAt)} · ${log.fromStatus || "-"} → ${log.toStatus || "-"}</span>
        <p>${log.message || ""}</p>
      </div>
    `).join("") : `<div class="log-item"><strong>로그 없음</strong><p>아직 기록된 마감 로그가 없습니다.</p></div>`;
    byId("logDialog").showModal();
  }

  async function createPeriod() {
    const payload = {
      fiscalYear: Number(byId("periodYear").value),
      fiscalMonth: Number(byId("periodMonth").value),
      startDate: byId("periodStartDate").value,
      endDate: byId("periodEndDate").value,
      createdBy: "SYSTEM"
    };
    if (!payload.fiscalYear || !payload.fiscalMonth || !payload.startDate || !payload.endDate) {
      toast("필수값을 입력해주세요.");
      return;
    }
    if (payload.startDate >= payload.endDate) {
      toast("시작일은 종료일보다 이전이어야 합니다.");
      return;
    }
    try {
      await api("/periods", { method: "POST", body: payload });
      byId("periodDialog").close();
      toast("회계기간이 생성되었습니다.");
      await loadAll();
    } catch (error) {
      toast(error.message);
    }
  }

  async function createYearPeriods() {
    const fiscalYear = Number(byId("filterYear").value);
    if (!fiscalYear) {
      toast("회계연도를 입력해주세요.");
      return;
    }
    try {
      await api("/periods/year", {
        method: "POST",
        body: { fiscalYear, createdBy: "SYSTEM" }
      });
      toast(`${fiscalYear}년 회계기간을 생성했습니다.`);
      await loadAll();
    } catch (error) {
      toast(error.message);
    }
  }

  function renderSummary(summary) {
    setText("currentPeriod", summary?.currentPeriod || "-");
    setText("status", summary?.status || "-");
    setText("unapprovedVoucherCount", `${number.format(summary?.unapprovedVoucherCount || 0)}건`);
    setText("unpostedVoucherCount", `${number.format(summary?.unpostedVoucherCount || 0)}건`);
    setMoney("totalDebit", summary?.totalDebit || 0);
    setMoney("totalCredit", summary?.totalCredit || 0);
    setText("mismatchStatus", summary?.mismatchStatus || "-");
    updateKpiBadgesAndProgress(summary);
  }

  function renderValidation(validation) {
    byId("validationTitle").textContent = `${validation.periodKey} 마감 검증`;
    byId("validationTableBody").innerHTML = (validation.results || []).map(row => `
      <tr>
        <td>${row.validationType}</td>
        <td>${pill(row.status)}</td>
        <td>${row.detail}</td>
        <td class="amount">${number.format(row.targetCount || 0)}</td>
        <td>${row.actionRequired ? "필요" : "없음"}</td>
      </tr>
    `).join("");
    byId("validationDialog").showModal();
    renderIcons();
    updateChecklistAndAlerts(validation);
  }

  function updateKpiBadgesAndProgress(summary) {
    const statusBadge = byId("closingStatusBadge");
    const closableBadge = byId("closableBadge");
    const rateText = byId("completionRateText");
    const progressBar = byId("completionProgressBar");

    if (!summary) {
      if (statusBadge) statusBadge.className = "status_badge muted";
      if (closableBadge) {
        closableBadge.className = "status_badge danger font-bold";
        closableBadge.textContent = "불가";
      }
      if (rateText) rateText.textContent = "0%";
      if (progressBar) progressBar.style.width = "0%";
      updateSteps("PENDING");
      return;
    }

    if (statusBadge) {
      statusBadge.textContent = summary.status;
      statusBadge.className = "status_badge";
      if (summary.status === "CLOSED" || summary.status === "ARCHIVED") {
        statusBadge.classList.add("success");
      } else if (summary.status === "PRE_CLOSING") {
        statusBadge.classList.add("warning");
      } else if (summary.status === "OPEN" || summary.status === "REOPENED") {
        statusBadge.classList.add("info");
      } else {
        statusBadge.classList.add("muted");
      }
    }

    const isClosable = summary.unapprovedVoucherCount === 0 && summary.unpostedVoucherCount === 0 && summary.balanced;
    if (closableBadge) {
      if (summary.status === "CLOSED" || summary.status === "ARCHIVED") {
        closableBadge.textContent = "마감완료";
        closableBadge.className = "status_badge success font-bold";
      } else {
        closableBadge.textContent = isClosable ? "가능" : "불가";
        closableBadge.className = isClosable 
          ? "status_badge success font-bold" 
          : "status_badge danger font-bold";
      }
    }

    let rate = 0;
    if (summary.status === "CLOSED" || summary.status === "ARCHIVED") {
      rate = 100;
      updateSteps("CLOSED");
    } else if (summary.status === "PRE_CLOSING") {
      rate = 80;
      updateSteps("PRE_CLOSING");
    } else if (summary.status === "OPEN" || summary.status === "REOPENED") {
      if (isClosable) {
        rate = 60;
        updateSteps("CLOSABLE");
      } else {
        rate = 20;
        updateSteps("VALIDATION_ERROR");
      }
    }

    if (rateText) rateText.textContent = `${rate}%`;
    if (progressBar) progressBar.style.width = `${rate}%`;
  }

  function updateSteps(state) {
    const steps = [
      byId("step_1"),
      byId("step_2"),
      byId("step_3"),
      byId("step_4"),
      byId("step_5")
    ];
    const lines = [
      byId("line_1"),
      byId("line_2"),
      byId("line_3"),
      byId("line_4")
    ];

    steps.forEach(s => { if (s) s.className = "step_node"; });
    lines.forEach(l => { if (l) l.className = "step_line"; });

    if (state === "PENDING") {
      if (steps[0]) steps[0].classList.add("active");
    } else if (state === "VALIDATION_ERROR") {
      if (steps[0]) steps[0].classList.add("active");
    } else if (state === "CLOSABLE") {
      for (let i = 0; i < 3; i++) {
        if (steps[i]) steps[i].classList.add("completed");
        if (lines[i]) lines[i].classList.add("completed");
      }
      if (steps[3]) steps[3].classList.add("active");
    } else if (state === "PRE_CLOSING") {
      for (let i = 0; i < 4; i++) {
        if (steps[i]) steps[i].classList.add("completed");
        if (lines[i]) lines[i].classList.add("completed");
      }
      if (steps[4]) steps[4].classList.add("active");
    } else if (state === "CLOSED") {
      steps.forEach(s => { if (s) s.classList.add("completed"); });
      lines.forEach(l => { if (l) l.classList.add("completed"); });
    }
  }

  function updateChecklistAndAlerts(validation) {
    const results = validation.results || [];
    const alertCard = byId("closingAlertCard");
    const alertReasons = byId("closingAlertReasons");

    results.forEach(res => {
      let checklistId = "";
      let checklistName = "";
      if (res.validationType === "VOUCHER") {
        checklistId = "chk_voucher_approval";
        checklistName = `1. 전표 승인 여부 (미승인/미게시: ${res.targetCount}건)`;
      } else if (res.validationType === "JOURNAL") {
        checklistId = "chk_balance_check";
        checklistName = `4. 월계 처리 여부 (불일치: ${res.targetCount}건)`;
      } else if (res.validationType === "INVENTORY") {
        checklistId = "chk_inventory_closing";
        checklistName = `3. 재고 마감 여부 (음수: ${res.targetCount}건)`;
      }

      if (checklistId) {
        const item = byId(checklistId);
        if (item) {
          item.querySelector(".checklist_name").textContent = checklistName;
          const badge = item.querySelector(".status-pill");
          badge.className = `status-pill status-${res.status}`;
          badge.textContent = res.status === "SUCCESS" ? "통과" : "조치필요";
        }
      }
    });

    const chkPosting = byId("chk_voucher_posting");
    if (chkPosting && state.selected) {
      const isUnpostedOk = (state.selected.unpostedVoucherCount || 0) === 0;
      chkPosting.querySelector(".checklist_name").textContent = `2. 미전기 전표 여부 (미게시: ${state.selected.unpostedVoucherCount || 0}건)`;
      const badge = chkPosting.querySelector(".status-pill");
      badge.className = isUnpostedOk ? "status-pill status-SUCCESS" : "status-pill status-ERROR";
      badge.textContent = isUnpostedOk ? "통과" : "조치필요";
    }

    const errors = results.filter(r => r.status === "ERROR" || r.actionRequired);
    if (errors.length > 0 && !validation.closable) {
      if (alertCard) alertCard.classList.remove("hidden");
      if (alertReasons) {
        alertReasons.innerHTML = errors.map(err => {
          let detailMsg = err.detail;
          if (err.validationType === "VOUCHER") {
            detailMsg = `미승인 전표 혹은 미게시 전표 ${err.targetCount}건 존재`;
          } else if (err.validationType === "JOURNAL") {
            detailMsg = `차대변 불일치 혹은 라인 누락 오류 ${err.targetCount}건 존재`;
          } else if (err.validationType === "ACCOUNT") {
            detailMsg = `비활성/말단계정 위반 라인 ${err.targetCount}건 존재`;
          }
          return `<li>${detailMsg}</li>`;
        }).join("");
      }

      const closableBadge = byId("closableBadge");
      if (closableBadge) {
        closableBadge.textContent = "불가";
        closableBadge.className = "status_badge danger font-bold";
      }
    } else {
      if (alertCard) alertCard.classList.add("hidden");
    }
  }

  function resetChecklistAndAlertsBySummary(period) {
    const chkVoucher = byId("chk_voucher_approval");
    const chkPosting = byId("chk_voucher_posting");
    const chkInventory = byId("chk_inventory_closing");
    const chkBalance = byId("chk_balance_check");
    const alertCard = byId("closingAlertCard");

    if (alertCard) alertCard.classList.add("hidden");

    if (chkVoucher && period) {
      const isUnapprovedOk = (period.unapprovedVoucherCount || 0) === 0;
      chkVoucher.querySelector(".checklist_name").textContent = `1. 전표 승인 여부 (미승인: ${period.unapprovedVoucherCount || 0}건)`;
      const badge = chkVoucher.querySelector(".status-pill");
      badge.className = isUnapprovedOk ? "status-pill status-SUCCESS" : "status-pill status-ERROR";
      badge.textContent = isUnapprovedOk ? "통과" : "조치필요";
    }
    if (chkPosting && period) {
      const isUnpostedOk = (period.unpostedVoucherCount || 0) === 0;
      chkPosting.querySelector(".checklist_name").textContent = `2. 미전기 전표 여부 (미게시: ${period.unpostedVoucherCount || 0}건)`;
      const badge = chkPosting.querySelector(".status-pill");
      badge.className = isUnpostedOk ? "status-pill status-SUCCESS" : "status-pill status-ERROR";
      badge.textContent = isUnpostedOk ? "통과" : "조치필요";
    }
    if (chkInventory) {
      const badge = chkInventory.querySelector(".status-pill");
      badge.className = "status-pill status-PENDING";
      badge.textContent = "대기";
    }
    if (chkBalance && period) {
      const isBalanced = period.balanced !== false;
      chkBalance.querySelector(".checklist_name").textContent = `4. 월계 처리 여부 (차대변 일치)`;
      const badge = chkBalance.querySelector(".status-pill");
      badge.className = isBalanced ? "status-pill status-SUCCESS" : "status-pill status-ERROR";
      badge.textContent = isBalanced ? "통과" : "조치필요";
    }
  }

  /** Unified API helper – delegates to window.ddukApi (apiClient.js) */
  async function api(path, options = {}) {
    const url = `${API_PATH}${path}`;
    const method = (options.method || "GET").toUpperCase();
    let payload;
    if (method === "GET") {
      payload = await window.ddukApi.get(url);
    } else if (method === "POST") {
      payload = await window.ddukApi.post(url, options.body);
    } else if (method === "PUT") {
      payload = await window.ddukApi.put(url, options.body);
    } else if (method === "PATCH") {
      payload = await window.ddukApi.patch(url, options.body);
    } else if (method === "DELETE") {
      payload = await window.ddukApi.delete(url);
    }
    if (payload && payload.status === "error") {
      throw new Error(payload.message || "요청 처리 중 오류가 발생했습니다.");
    }
    return payload || {};
  }

  function setDefaultDates() {
    const year = Number(byId("periodYear").value);
    const month = Number(byId("periodMonth").value);
    if (!year || !month) return;
    const start = new Date(year, month - 1, 1);
    const end = new Date(year, month, 0);
    byId("periodStartDate").value = toDateInput(start);
    byId("periodEndDate").value = toDateInput(end);
  }

  function pill(value) {
    return `<span class="status-pill status-${value}">${value}</span>`;
  }

  function setText(key, value) {
    const element = document.querySelector(`[data-summary="${key}"]`);
    if (element) element.textContent = value;
  }

  function setMoney(key, value) {
    const element = document.querySelector(`[data-summary-money="${key}"]`);
    if (element) element.textContent = money.format(value);
  }

  function formatDateTime(value) {
    if (!value) return "-";
    return String(value).replace("T", " ").slice(0, 16);
  }

  function toDateInput(date) {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
  }

  function byId(id) {
    return document.getElementById(id);
  }

  function toast(message) {
    const element = byId("toast");
    element.textContent = message;
    element.classList.add("show");
    window.setTimeout(() => element.classList.remove("show"), 2800);
  }

  function renderIcons() {
    if (window.lucide) window.lucide.createIcons();
  }
})();
