(function () {
  const money = new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 0 });
  let profitLossChart;
  let compositionChart;

  document.addEventListener("DOMContentLoaded", () => {
    initializePeriodControls();
    bindEvents();
    loadDashboard();
  });

  function initializePeriodControls() {
    const now = new Date();
    const filterYear = byId("filterYear");
    const filterMonth = byId("filterMonth");

    if (filterYear) {
      filterYear.value = now.getFullYear();
    }
    if (filterMonth) {
      filterMonth.innerHTML = Array.from({ length: 12 }, (_, index) => {
        const month = index + 1;
        return `<option value="${month}" ${month === now.getMonth() + 1 ? "selected" : ""}>${month}월</option>`;
      }).join("");
    }
  }

  function bindEvents() {
    const reloadBtn = byId("reloadButton");
    const filterYear = byId("filterYear");
    const filterMonth = byId("filterMonth");

    if (reloadBtn) reloadBtn.addEventListener("click", loadDashboard);
    if (filterYear) filterYear.addEventListener("change", loadDashboard);
    if (filterMonth) filterMonth.addEventListener("change", loadDashboard);
  }

  async function loadDashboard() {
    try {
      const query = new URLSearchParams({
        fiscalYear: byId("filterYear").value,
        fiscalMonth: byId("filterMonth").value
      });
      const payload = await window.ddukApi.get(`/api/v1/accounting/dashboard?${query}`);
      renderDashboard(payload.data);
    } catch (error) {
      toast(error.message || "대시보드 데이터를 불러오지 못했습니다.");
    }
  }

  function renderDashboard(data) {
    const period = data.periodSummary || {};

    const generatedAt = byId("generatedAt");
    if (generatedAt) {
      const periodLabel = period.currentPeriod ? `${period.currentPeriod} 기준` : "기준 기간 미설정";
      generatedAt.textContent = `${periodLabel} · 생성 시각 ${formatDateTime(data.generatedAt)}`;
    }

    renderBadge(byId("periodStatusBadge"), period.status || "NOT_CREATED");
    renderBadge(byId("validationBadge"), period.validationStatus || "UNKNOWN");
    renderKpis(data.kpiSummary || {});
    renderVoucherSummary(data.voucherSummary || {});
    renderProfitLossChart(data.financialTrends || []);
    renderCompositionChart(data.accountComposition || []);
    renderCashFlow(data.cashFlowSummary || {});
    renderPayroll(data.payrollSummary || {});
    renderPeriod(period);
    renderTrialBalance(data.trialBalanceSummary || {});
    renderActivities(data.recentActivities || []);
    renderQuickActions();
    renderIcons();
  }

  function renderKpis(kpi) {
    const kpiGrid = byId("kpiGrid");
    if (!kpiGrid) return;

    const items = [
      ["당월 총 매출", won(kpi.monthlyRevenue), rateLabel(kpi.monthlyRevenueChangeRate)],
      ["당월 총 비용", won(kpi.monthlyExpense), rateLabel(kpi.monthlyExpenseChangeRate)],
      ["영업 이익", won(kpi.operatingIncome), "매출 - 비용"],
      ["당기 순이익", won(kpi.netIncome), "원장 기준"],
      ["총 자산", won(kpi.totalAssets), "자산 계정 집계"],
      ["총 부채", won(kpi.totalLiabilities), "부채 계정 집계"]
    ];

    kpiGrid.innerHTML = items.map(([label, value, change]) => `
      <div class="kpi_card">
        <p class="kpi_label text-[11px] font-semibold text-slate-400 tracking-tight">${label}</p>
        <p class="kpi_value text-base font-bold text-slate-800 mt-1" title="${value}">${value}</p>
        <p class="kpi_change text-[10px] text-indigo-500 mt-0.5">${change}</p>
      </div>
    `).join("");
  }

  function renderVoucherSummary(summary) {
    const items = [
      ["DRAFT", summary.draftCount],
      ["APPROVED", summary.approvedCount],
      ["POSTED", summary.postedCount],
      ["CANCELLED", summary.cancelledCount]
    ];
    const grid = byId("voucherStatusGrid");
    if (grid) {
      grid.innerHTML = items.map(([label, value]) => `
        <div class="status_tile"><span>${label}</span><strong>${number(value)}건</strong></div>
      `).join("");
    }
  }

  function renderProfitLossChart(rows) {
    const ctx = byId("profitLossChart");
    if (!ctx) return;
    if (profitLossChart) profitLossChart.destroy();

    const chartBox = ctx.parentElement;
    let emptyMsg = byId("profitLossChartEmpty");
    if (!emptyMsg) {
      emptyMsg = document.createElement("div");
      emptyMsg.id = "profitLossChartEmpty";
      emptyMsg.className = "absolute inset-0 flex items-center justify-center text-slate-400 text-sm hidden";
      emptyMsg.textContent = "표시할 월 마감 데이터가 없습니다.";
      chartBox.classList.add("relative");
      chartBox.appendChild(emptyMsg);
    }

    if (!rows || rows.length === 0) {
      ctx.style.display = "none";
      emptyMsg.classList.remove("hidden");
      return;
    } else {
      ctx.style.display = "block";
      emptyMsg.classList.add("hidden");
    }

    profitLossChart = new Chart(ctx, {
      type: "line",
      data: {
        labels: rows.map((row) => row.period),
        datasets: [
          dataset("매출", rows.map((row) => row.revenue), "#3b82f6"),
          dataset("비용", rows.map((row) => row.expense), "#ef4444"),
          dataset("영업이익", rows.map((row) => row.operatingIncome), "#10b981"),
          dataset("당기순이익", rows.map((row) => row.netIncome), "#8b5cf6")
        ]
      },
      options: chartOptions()
    });
  }

  function renderCompositionChart(rows) {
    const ctx = byId("compositionChart");
    if (!ctx) return;
    if (compositionChart) compositionChart.destroy();

    const colors = ["#3b82f6", "#ef4444", "#10b981"];

    compositionChart = new Chart(ctx, {
      type: "doughnut",
      data: {
        labels: rows.map((row) => row.label),
        datasets: [{
          data: rows.map((row) => Number(row.amount || 0)),
          backgroundColor: colors,
          borderWidth: 0
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: { callbacks: { label: (context) => `${context.label}: ${won(context.raw)}` } }
        },
        cutout: "62%"
      }
    });

    const legendContainer = byId("compositionLegend");
    if (legendContainer) {
      legendContainer.innerHTML = rows.map((row, index) => {
        const color = colors[index % colors.length];
        return `
          <div class="legend_item flex flex-col items-center gap-1">
            <span class="flex items-center gap-1 font-semibold text-slate-700">
              <span class="w-2 h-2 rounded-full inline-block" style="background-color: ${color}"></span>
              ${row.label}
            </span>
            <span class="text-slate-900 font-bold mt-0.5">${won(row.amount)}</span>
            <span class="text-slate-400 text-[10px]">${Number(row.ratio || 0).toFixed(1)}%</span>
          </div>
        `;
      }).join("");
    }
  }

  function renderCashFlow(summary) {
    const basis = byId("cashFlowBasis");
    const list = byId("cashFlowList");
    if (basis) basis.textContent = summary.basis || "-";
    if (list) {
      list.innerHTML = metricRows([
        ["현금 유입", won(summary.cashInflow)],
        ["현금 유출", won(summary.cashOutflow)],
        ["순 현금 흐름", won(summary.netCashFlow)]
      ]);
    }
  }

  function renderPayroll(summary) {
    const list = byId("payrollList");
    if (list) {
      list.innerHTML = metricRows([
        ["당월 급여 총액", won(summary.monthlyPayrollAmount)],
        ["급여 계산 상태", summary.calculationCompleted ? "완료" : "처리 필요"],
        ["미지급 급여", won(summary.unpaidPayrollAmount)],
        ["지급 예정일", summary.nextPaymentDate || "-"],
        ["미정산 급여", `${number(summary.unsettledCount)}건`]
      ]);
    }
  }

  function renderPeriod(period) {
    const list = byId("periodList");
    if (list) {
      list.innerHTML = metricRows([
        ["현재 회계기간", period.currentPeriod || "-"],
        ["마감 상태", period.status || "NOT_CREATED"],
        ["검증 결과", period.validationStatus || "-"],
        ["미게시 전표 수", `${number(period.unpostedVoucherCount)}건`],
        ["차대 평형 일치", period.balanced ? "일치" : "불일치"],
        ["총 차변 / 총 대변", `${won(period.totalDebit)} / ${won(period.totalCredit)}`]
      ]);
    }
  }

  function renderTrialBalance(summary) {
    const list = byId("trialBalanceList");
    const major = byId("majorAccounts");

    if (list) {
      list.innerHTML = metricRows([
        ["총 차변", won(summary.totalDebit)],
        ["총 대변", won(summary.totalCredit)],
        ["불일치 여부", summary.balanced ? "정상" : "불일치 경고"]
      ]);
    }

    if (major) {
      const rows = summary.majorAccounts || [];
      major.innerHTML = rows.map((row) => `
        <div class="rank_row">
          <span>${row.accountCode}</span>
          <strong>${row.accountName}</strong>
          <span>${won(row.closingBalance)}</span>
        </div>
      `).join("") || `<div class="rank_row"><span>-</span><strong>주요 계정 잔액 없음</strong><span>-</span></div>`;
    }
  }

  function renderAlerts(alerts) {
    const list = byId("alertList");
    if (list) {
      list.innerHTML = alerts.map((alert) => `
        <div class="alert_item ${alert.severity}">
          <strong>${alert.title}</strong>
          <p>${alert.message}</p>
          <a href="${alert.actionUrl || "#"}">${alert.actionLabel || "확인"}</a>
        </div>
      `).join("");
    }
  }

  function renderActivities(rows) {
    const container = byId("activityRows");
    if (container) {
      container.innerHTML = rows.map((row) => `
        <div class="activity_row">
          <span>${row.activityType}</span>
          <span>${row.target || "-"}</span>
          <span>${row.actor || "-"}</span>
          <span>${formatDateTime(row.activityAt)}</span>
          <span>${row.status || "-"}</span>
        </div>
      `).join("") || `<div class="activity_row"><span>-</span><span>최근 활동 없음</span><span>-</span><span>-</span><span>-</span></div>`;
    }
  }


  function metricRows(items) {
    return items.map(([label, value]) => `
      <div class="metric_row"><span>${label}</span><strong>${value}</strong></div>
    `).join("");
  }

  function dataset(label, data, color) {
    return {
      label,
      data: data.map((value) => Number(value || 0)),
      borderColor: color,
      backgroundColor: color,
      tension: 0.35,
      pointRadius: 3,
      borderWidth: 2
    };
  }

  function chartOptions() {
    return {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: "index", intersect: false },
      plugins: {
        legend: { position: "bottom" },
        tooltip: { callbacks: { label: (context) => `${context.dataset.label}: ${won(context.raw)}` } }
      },
      scales: {
        y: { ticks: { callback: (value) => compactWon(value) }, grid: { color: "rgba(148, 163, 184, 0.18)" } },
        x: { grid: { display: false } }
      }
    };
  }

  function renderBadge(element, value) {
    if (!element) return;
    element.textContent = value;
    element.className = "status_badge";
    if (["WARNING", "PRE_CLOSING", "REOPENED"].includes(value)) element.classList.add("warning");
    if (["ERROR", "CRITICAL", "CLOSED", "ARCHIVED", "NOT_CREATED"].includes(value)) element.classList.add("danger");
    if (["UNKNOWN"].includes(value)) element.classList.add("muted");
  }

  function rateLabel(value) {
    const numeric = Number(value || 0);
    if (numeric === 0) return "전월 대비 0%";
    return `전월 대비 ${numeric > 0 ? "+" : ""}${numeric.toFixed(2)}%`;
  }

  function won(value) {
    return `₩${money.format(Number(value || 0))}`;
  }

  function compactWon(value) {
    const numeric = Number(value || 0);
    if (Math.abs(numeric) >= 100000000) return `${Math.round(numeric / 100000000)}억`;
    if (Math.abs(numeric) >= 10000) return `${Math.round(numeric / 10000)}만`;
    return money.format(numeric);
  }

  function number(value) {
    return money.format(Number(value || 0));
  }

  function formatDateTime(value) {
    if (!value) return "-";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")} ${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
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

  function renderQuickActions() {
    const container = byId("quickActions");
    if (!container) return;

    const actions = [
      { label: "전표 등록", url: "voucher_management.html?action=new", icon: "plus-circle" },
      { label: "전표 관리", url: "voucher_management.html", icon: "file-text" },
      { label: "월 마감", url: "monthly_closing.html", icon: "calendar-check" },
      { label: "급여 계산", url: "payroll_management.html", icon: "credit-card" },
      { label: "재무제표 조회", url: "trial_balance.html", icon: "bar-chart-2" },
      { label: "계정과목 관리", url: "accounts.html", icon: "list" }
    ];

    container.innerHTML = actions.map(act => `
      <a href="${act.url}" class="quick_action">
        <i data-lucide="${act.icon}" class="w-4 h-4"></i>
        <span>${act.label}</span>
      </a>
    `).join("");
  }

  function renderIcons() {
    if (window.lucide) window.lucide.createIcons();
  }

})();
