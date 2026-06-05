(function () {
  const state = {
    voucherType: 'SALES',
    lookupTarget: null,
    currentVoucher: null, // 상세 모달에 로드된 현재 전표 객체
    selected: {
      vendor: null,
      businessAccount: null,
      settlementAccount: null,
    },
  };

  const VAT_ZERO_TYPES = new Set(['ZERO_TAX', 'TAX_FREE', 'EXPORT', 'INVOICE']);
  const moneyFormatter = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 0 });

  let vatDebounceTimer = null;
  let lookupDebounceTimer = null;

  document.addEventListener('DOMContentLoaded', initVoucherWorkspace);

  function initVoucherWorkspace() {
    bindEvents();
    
    // 기본 테마 설정 (매출전표로 시작)
    applyVoucherTypeTheme('SALES');
    
    // 비동기 통계 및 리스트 로드
    loadSummary();
    loadVouchers();

    if (window.lucide) {
      window.lucide.createIcons();
    }
  }

  function bindEvents() {
    // 1. 유형 탭 클릭 스위칭 (매출 / 매입)
    document.querySelectorAll('[data-voucher-type]').forEach((button) => {
      button.addEventListener('click', () => switchVoucherType(button));
    });

    // 2. 공급가액, 부가세, 수수료 실시간 입력 감지 및 포맷팅
    ['supplyAmount', 'vatAmount', 'feeAmount'].forEach((id) => {
      const input = document.getElementById(id);
      if (!input) return;
      input.addEventListener('input', () => {
        sanitizeMoneyInput(input);
        if (id === 'supplyAmount') {
          debounceVatCalculation();
        } else {
          recalculateAmounts();
        }
      });
      input.addEventListener('blur', () => formatMoneyInput(input));
    });

    // 3. 부가세 유형 변경
    document.getElementById('vatType')?.addEventListener('change', () => {
      calculateVatFromSupply();
      recalculateAmounts();
    });

    // 4. 저장 및 임시저장 제출
    document.getElementById('voucherForm')?.addEventListener('submit', (e) => {
      e.preventDefault();
      handleSaveAction('REQUESTED'); // 저장(기표)
    });

    document.getElementById('draftSaveButton')?.addEventListener('click', () => {
      handleSaveAction('DRAFT'); // 임시저장
    });

    document.getElementById('resetFormButton')?.addEventListener('click', resetForm);

    // 5. 새로고침 버튼 및 상태 필터
    document.getElementById('reloadButton')?.addEventListener('click', () => {
      loadSummary();
      loadVouchers();
    });

    document.getElementById('statusFilter')?.addEventListener('change', () => {
      loadVouchers();
    });

    // 6. + 신규 전표 버튼 클릭 (Expand Slide-down & Focus & Scroll)
    document.getElementById('newVoucherBtn')?.addEventListener('click', expandInputSection);

    // 7. 닫기(X) 버튼 클릭 (Collapse Slide-up)
    document.getElementById('closeInputBtn')?.addEventListener('click', hideInputSection);

    // 8. ESC 키 입력 감지 (패널 닫기)
    document.addEventListener('keydown', (event) => {
      if (event.key === 'Escape' || event.key === 'Esc') {
        hideInputSection();
      }
    });

    // 9. 검색 조회용 모달 트리거
    document.querySelectorAll('[data-open-lookup]').forEach((button) => {
      button.addEventListener('click', () => openLookup(button.dataset.openLookup));
    });

    // 10. 계정과목 자동완성
    bindAccountAutocomplete('businessAccountName', 'businessAccount');
    bindAccountAutocomplete('settlementAccountName', 'settlementAccount');

    // 11. 검색 다이얼로그 키워드 감지
    document.getElementById('lookupSearchButton')?.addEventListener('click', runLookupSearch);
    document.getElementById('lookupKeyword')?.addEventListener('input', () => {
      clearTimeout(lookupDebounceTimer);
      lookupDebounceTimer = setTimeout(runLookupSearch, 250);
    });
    document.getElementById('lookupKeyword')?.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') {
        event.preventDefault();
        runLookupSearch();
      }
    });

    // 12. 모달 내 승인 / 반려 액션
    document.getElementById('modalApproveButton')?.addEventListener('click', () => handleStatusChange('approve'));
    document.getElementById('modalRejectButton')?.addEventListener('click', () => handleStatusChange('reject'));
  }

  // ==========================================
  // [1] 입력 패널 드로우다운/닫기 제어
  // ==========================================

  function expandInputSection() {
    const inputSection = document.getElementById('inputSection');
    if (!inputSection) return;

    // 패널 열기
    inputSection.classList.add('show');
    
    // 부드럽게 스크롤
    inputSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
    
    // 첫 입력 폼 요소 포커스
    setTimeout(() => {
      document.getElementById('voucherDate')?.focus();
    }, 450);
  }

  function hideInputSection() {
    const inputSection = document.getElementById('inputSection');
    if (inputSection && inputSection.classList.contains('show')) {
      inputSection.classList.remove('show');
      resetForm();
    }
  }

  // ==========================================
  // [2] 전표 유형 (매출/매입) 테마 및 placeholder 구분
  // ==========================================

  function switchVoucherType(button) {
    state.voucherType = button.dataset.voucherType;
    document.querySelectorAll('[data-voucher-type]').forEach((tab) => tab.classList.toggle('active', tab === button));
    
    clearSelectedAccounts();
    applyVoucherTypeTheme(state.voucherType);
    refreshLabels();
    refreshJournalPreview();
    
    // 하단 전표 리스트도 동기식 로드
    loadVouchers();
  }

  function applyVoucherTypeTheme(type) {
    const isSales = type === 'SALES';
    const root = document.documentElement;
    const badge = document.getElementById('voucherTypeBadge');
    const title = document.getElementById('inputFormTitle');
    const vendorInput = document.getElementById('vendorName');
    const bizAccInput = document.getElementById('businessAccountName');
    const setAccInput = document.getElementById('settlementAccountName');

    if (isSales) {
      // 매출: 파랑 인디고 Accent
      root.style.setProperty('--accent', '#4f46e5');
      if (badge) {
        badge.className = 'status_badge info';
        badge.textContent = '매출전표';
      }
      if (title) title.textContent = '전표 입력 (매출전표)';
      if (vendorInput) vendorInput.placeholder = '매출 거래처 검색';
      if (bizAccInput) bizAccInput.placeholder = '매출계정 (REVENUE 코드/계정명 검색)';
      if (setAccInput) setAccInput.placeholder = '입금계좌 (ASSET 보통예금/현금 검색)';
    } else {
      // 매입: 보라/주황 Accent
      root.style.setProperty('--accent', '#9333ea');
      if (badge) {
        badge.className = 'status_badge warning';
        badge.textContent = '매입전표';
        badge.style.backgroundColor = '#fae8ff';
        badge.style.color = '#a21caf';
      }
      if (title) title.textContent = '전표 입력 (매입전표)';
      if (vendorInput) vendorInput.placeholder = '매입 거래처 검색';
      if (bizAccInput) bizAccInput.placeholder = '매입/비용계정 (ASSET 또는 EXPENSE 검색)';
      if (setAccInput) setAccInput.placeholder = '출금계좌 (ASSET 보통예금/현금 검색)';
    }
  }

  function refreshLabels() {
    const isSales = state.voucherType === 'SALES';
    setText('businessAccountLabel', isSales ? '매출계정' : '매입/비용계정');
    setText('settlementAccountLabel', isSales ? '입금계좌' : '출금계좌');
  }

  // ==========================================
  // [3] 실시간 자동분개 Preview 및 수치 동기화
  // ==========================================

  function bindAccountAutocomplete(inputId, target) {
    const input = document.getElementById(inputId);
    if (!input) return;
    input.addEventListener('input', () => {
      state.selected[target] = null;
      document.getElementById(target === 'businessAccount' ? 'businessAccountId' : 'settlementAccountId').value = '';
      clearTimeout(lookupDebounceTimer);
      lookupDebounceTimer = setTimeout(() => {
        if (input.value.trim().length >= 1) {
          openLookup(target, input.value.trim());
        }
      }, 300);
    });
  }

  function sanitizeMoneyInput(input) {
    const cleaned = input.value.replace(/[^\d]/g, '');
    input.value = cleaned;
  }

  function debounceVatCalculation() {
    clearTimeout(vatDebounceTimer);
    vatDebounceTimer = setTimeout(() => {
      calculateVatFromSupply();
      recalculateAmounts();
    }, 180);
  }

  function calculateVatFromSupply() {
    const supplyAmount = parseMoney(getValue('supplyAmount'));
    const vatType = getValue('vatType');
    const vatAmount = VAT_ZERO_TYPES.has(vatType) ? 0 : Math.floor(supplyAmount * 0.1);
    setValue('vatAmount', formatNumber(vatAmount));
  }

  function recalculateAmounts() {
    const supplyAmount = parseMoney(getValue('supplyAmount'));
    const vatAmount = parseMoney(getValue('vatAmount'));
    const totalAmount = Math.max(supplyAmount + vatAmount, 0);
    
    setValue('totalAmount', formatNumber(totalAmount));
    
    // 요약 검증 정보 업데이트
    setText('summarySupply', formatWon(supplyAmount));
    setText('summaryVat', formatWon(vatAmount));
    setText('summaryTotal', formatWon(totalAmount));

    refreshJournalPreview();
  }

  function formatMoneyInput(input) {
    input.value = formatNumber(parseMoney(input.value));
    recalculateAmounts();
  }

  function refreshJournalPreview() {
    const lines = buildPreviewLines();
    const tbody = document.getElementById('journalPreviewBody');
    if (!tbody) return;

    if (lines.length === 0) {
      tbody.innerHTML = `
        <tr>
          <td colspan="5" style="text-align: center; color: var(--text-muted); padding: 20px;">
            금액과 계정과목을 입력하면 자동 분개 내역이 실시간 출력됩니다.
          </td>
        </tr>
      `;
      const balanceState = document.getElementById('balanceState');
      if (balanceState) {
        balanceState.className = 'status_badge muted';
        balanceState.textContent = '차대 검증 대기';
      }
      return;
    }

    tbody.innerHTML = lines.map((line) => `
      <tr>
        <td class="${line.side === 'DEBIT' ? 'preview-debit-text' : 'preview-credit-text'}">${line.side === 'DEBIT' ? '차변' : '대변'}</td>
        <td style="font-weight: 500;">${escapeHtml(line.accountName || '계정 선택 필요')}</td>
        <td class="amount preview-debit-text">${line.side === 'DEBIT' ? formatWon(line.amount) : '-'}</td>
        <td class="amount preview-credit-text">${line.side === 'CREDIT' ? formatWon(line.amount) : '-'}</td>
        <td style="color: var(--text-secondary); font-size: 0.8rem;">${escapeHtml(line.description)}</td>
      </tr>
    `).join('');

    const debit = lines.filter((line) => line.side === 'DEBIT').reduce((sum, line) => sum + line.amount, 0);
    const credit = lines.filter((line) => line.side === 'CREDIT').reduce((sum, line) => sum + line.amount, 0);
    const balanceState = document.getElementById('balanceState');
    if (!balanceState) return;
    
    if (debit > 0 && debit === credit) {
      balanceState.className = 'status_badge success';
      balanceState.style.backgroundColor = '#d1fae5';
      balanceState.style.color = '#065f46';
      balanceState.textContent = `차대 일치 ${formatWon(debit)}`;
    } else {
      balanceState.className = 'status_badge danger';
      balanceState.style.backgroundColor = '#fee2e2';
      balanceState.style.color = '#991b1b';
      balanceState.textContent = `차변 ${formatWon(debit)} / 대변 ${formatWon(credit)}`;
    }
  }

  function buildPreviewLines() {
    const businessAccount = state.selected.businessAccount;
    const settlementAccount = state.selected.settlementAccount;
    const supplyAmount = parseMoney(getValue('supplyAmount'));
    const vatAmount = parseMoney(getValue('vatAmount'));
    const feeAmount = parseMoney(getValue('feeAmount'));
    const totalAmount = supplyAmount + vatAmount;
    const lines = [];

    if (supplyAmount === 0 && vatAmount === 0 && !businessAccount && !settlementAccount) {
      return [];
    }

    if (state.voucherType === 'SALES') {
      lines.push({
        side: 'DEBIT',
        accountName: settlementAccount?.name,
        amount: Math.max(totalAmount - feeAmount, 0),
        description: '자산 (현금/매출채권)',
      });
      if (feeAmount > 0) {
        lines.push({ side: 'DEBIT', accountName: '지급수수료', amount: feeAmount, description: '카드/PG 수수료비용' });
      }
      lines.push({ side: 'CREDIT', accountName: businessAccount?.name, amount: supplyAmount, description: '수익 (매출액)' });
      if (vatAmount > 0) {
        lines.push({ side: 'CREDIT', accountName: '부가세예수금', amount: vatAmount, description: '부채 (부가세)' });
      }
    } else {
      lines.push({ side: 'DEBIT', accountName: businessAccount?.name, amount: supplyAmount, description: '자산/비용 (매입비용)' });
      if (vatAmount > 0) {
        lines.push({ side: 'DEBIT', accountName: '부가세대급금', amount: vatAmount, description: '자산 (부가세)' });
      }
      lines.push({ side: 'CREDIT', accountName: settlementAccount?.name, amount: totalAmount, description: '부채/자산 (출금/매입채무)' });
    }

    return lines.filter((line) => line.amount > 0 || line.accountName);
  }

  // ==========================================
  // [4] 저장 기동 및 Expand 패널 자동 닫기 처리
  // ==========================================

  async function handleSaveAction(nextStatus) {
    const payload = buildPayload();
    const validationMessage = validatePayload(payload);

    if (validationMessage) {
      window.ddukApi.showToast(validationMessage, 'error');
      return;
    }

    try {
      const response = await window.voucherCommandService.createVoucher(payload);
      const savedVoucher = response.data;
      const voucherNo = savedVoucher.voucherNo;

      if (nextStatus === 'REQUESTED') {
        await window.voucherCommandService.updateVoucherStatus(savedVoucher.id, 'REQUESTED');
        window.ddukApi.showToast(`전표가 성공적으로 등록 및 기표되었습니다.\n[${voucherNo}]`, 'success');
      } else {
        window.ddukApi.showToast(`전표가 임시저장되었습니다.\n[${voucherNo}]`, 'success');
      }

      // 연속 기표 모드 상태 체크 및 처리
      const isContinuous = document.getElementById('continuousMode')?.checked;
      if (isContinuous) {
        // 거래처 및 계정과목은 보존하고, 수치/적요만 리셋
        setValue('supplyAmount', '');
        setValue('vatAmount', '');
        setValue('totalAmount', '0');
        setValue('feeAmount', '');
        setValue('description', '');
        recalculateAmounts();
      } else {
        // 연속 입력 모드가 꺼져 있다면 패널 닫기 및 리셋
        hideInputSection();
      }

      // 목록 새로고침 및 하이라이트 UX 작동!
      await loadSummary();
      await loadVouchers();
      
      triggerHighlight(voucherNo);

    } catch (error) {
      window.ddukApi.showToast(error.message || '전표 기표에 실패했습니다.', 'error');
    }
  }

  function buildPayload() {
    return {
      voucherDate: getValue('voucherDate'),
      voucherType: state.voucherType,
      vatType: getValue('vatType'),
      vendorId: state.selected.vendor?.id || null,
      vendorNameSnapshot: getValue('vendorName').trim(),
      supplyAmount: parseMoney(getValue('supplyAmount')),
      vatAmount: parseMoney(getValue('vatAmount')),
      feeAmount: parseMoney(getValue('feeAmount')),
      businessAccountId: Number(getValue('businessAccountId')),
      settlementAccountId: Number(getValue('settlementAccountId')),
      description: getValue('description').trim(),
    };
  }

  function validatePayload(payload) {
    if (!payload.voucherDate) return '전표일자를 입력해주세요.';
    if (!payload.vendorNameSnapshot) return '거래처를 선택해주세요.';
    if (!payload.supplyAmount || payload.supplyAmount <= 0) return '공급가액은 0보다 커야 합니다.';
    if (!payload.businessAccountId || !state.selected.businessAccount) return '계정과목을 검색해서 선택해주세요.';
    if (!payload.settlementAccountId || !state.selected.settlementAccount) return '입금/출금 계좌를 검색해서 선택해주세요.';
    if (payload.feeAmount < 0) return '수수료는 음수일 수 없습니다.';
    return '';
  }

  // ==========================================
  // [5] 하단 영역 (Read & Approve Part) 제어
  // ==========================================

  async function loadSummary() {
    try {
      const response = await window.voucherQueryService.fetchSummary();
      const summary = response.data || {};
      
      document.querySelectorAll('[data-summary]').forEach((el) => {
        el.textContent = summary[el.dataset.summary] ?? 0;
      });
      document.querySelectorAll('[data-summary-money]').forEach((el) => {
        el.textContent = formatWon(Number(summary[el.dataset.summaryMoney] || 0));
      });
    } catch (e) {
      window.ddukApi.showToast('요약 통계 정보 로딩에 실패했습니다.', 'error');
    }
  }

  async function loadVouchers() {
    setLoadingText('voucherListBody', 11, '전표 목록을 조회하고 있습니다.');
    try {
      const status = document.getElementById('statusFilter')?.value || '';
      const response = await window.voucherQueryService.fetchVouchers(state.voucherType, { status });
      const vouchers = response.data || [];
      const tbody = document.getElementById('voucherListBody');
      if (!tbody) return;

      if (!vouchers.length) {
        tbody.innerHTML = `
          <tr>
            <td colspan="11" class="empty-state">
              조회된 전표 내역이 존재하지 않습니다.
            </td>
          </tr>
        `;
        return;
      }

      tbody.innerHTML = vouchers.map((voucher) => {
        const totals = summarizeLines(voucher.lines || []);
        return `
          <tr data-voucher-id="${voucher.id}" data-voucher-no="${escapeHtml(voucher.voucherNo)}" style="cursor: pointer;">
            <td class="vo-no-cell">${escapeHtml(voucher.voucherNo || '')}</td>
            <td>${voucher.voucherType === 'SALES' ? '매출전표' : '매입전표'}</td>
            <td>${escapeHtml(voucher.voucherDate || '')}</td>
            <td>${escapeHtml(voucher.vendorNameSnapshot || '')}</td>
            <td class="amount">${formatWon(totals.supply)}</td>
            <td class="amount">${formatWon(totals.vat)}</td>
            <td class="amount">${formatWon(totals.total)}</td>
            <td class="vo-status-cell"><span class="status_badge ${statusClass(voucher.status)}">${escapeHtml(voucher.status || '')}</span></td>
            <td>${escapeHtml(voucher.createdBy || '')}</td>
            <td>${formatDateTime(voucher.createdAt)}</td>
            <td>${voucher.status === 'POSTED' ? '게시' : '-'}</td>
          </tr>
        `;
      }).join('');

      tbody.querySelectorAll('tr').forEach((row) => {
        row.addEventListener('click', () => {
          const voucherId = row.dataset.voucherId;
          if (voucherId) {
            openDetailModal(voucherId);
          }
        });
      });
    } catch (e) {
      window.ddukApi.showToast('전표 현황 목록 로딩에 실패했습니다.', 'error');
    }
  }

  // 상세 모달 기동
  async function openDetailModal(id) {
    const dialog = document.getElementById('detailModal');
    if (!dialog) return;

    dialog.classList.add('is_active');
    setLoadingText('modalLinesBody', 7, '분개 라인 조회를 시작합니다...');

    try {
      const response = await window.voucherQueryService.fetchVoucherDetail(id);
      const voucher = response.data;
      state.currentVoucher = voucher;

      setText('modalVoucherNo', voucher.voucherNo || '-');
      setText('modalVoucherType', voucher.voucherType === 'SALES' ? '매출전표' : '매입전표');
      setText('modalVoucherDate', voucher.voucherDate || '-');
      setText('modalVendorName', voucher.vendorNameSnapshot || '미지정');
      setText('modalDescription', voucher.description || '적요 없음');
      setText('modalCreatedInfo', `${voucher.createdBy || 'unknown'} / ${formatDateTime(voucher.createdAt)}`);
      
      const statusEl = document.getElementById('modalStatus');
      if (statusEl) {
        statusEl.className = `status_badge ${statusClass(voucher.status)}`;
        statusEl.textContent = voucher.status || '';
      }

      // lines
      const linesBody = document.getElementById('modalLinesBody');
      if (linesBody) {
        if (!voucher.lines || !voucher.lines.length) {
          linesBody.innerHTML = '<tr><td colspan="7" style="text-align: center; padding: 20px;">분개 내역이 존재하지 않습니다.</td></tr>';
        } else {
          linesBody.innerHTML = voucher.lines.map((line) => `
            <tr>
              <td>${line.lineNo}</td>
              <td class="${line.debitCredit === 'DEBIT' ? 'preview-debit-text' : 'preview-credit-text'}">${line.debitCredit === 'DEBIT' ? '차변' : '대변'}</td>
              <td>${escapeHtml(line.accountCode)}</td>
              <td>${escapeHtml(line.accountName)}</td>
              <td class="amount preview-debit-text">${line.debitCredit === 'DEBIT' ? formatWon(line.totalAmount) : '-'}</td>
              <td class="amount preview-credit-text">${line.debitCredit === 'CREDIT' ? formatWon(line.totalAmount) : '-'}</td>
              <td>${escapeHtml(line.description || '')}</td>
            </tr>
          `).join('');
        }
      }

      setText('modalDebitSum', formatWon(voucher.debitTotal || 0));
      setText('modalCreditSum', formatWon(voucher.creditTotal || 0));

      applyApprovalGuards(voucher);

    } catch (e) {
      window.ddukApi.showToast('상세 분개 정보를 불러올 수 없습니다.', 'error');
      dialog.classList.remove('is_active');
    }
  }

  // 승인 가드
  function applyApprovalGuards(voucher) {
    const approvalArea = document.getElementById('modalApprovalActions');
    const approveBtn = document.getElementById('modalApproveButton');
    const rejectBtn = document.getElementById('modalRejectButton');
    if (!approvalArea || !approveBtn || !rejectBtn) return;

    const userRole = window.ddukSession?.getSession?.()?.role || localStorage.getItem('role') || 'USER';
    const isAuthorized = userRole === 'ADMIN' || userRole === 'HR';

    if (!isAuthorized) {
      approvalArea.style.display = 'none';
      return;
    }

    approvalArea.style.display = 'flex';

    if (voucher.status === 'DRAFT') {
      approveBtn.textContent = '기표 요청';
      approveBtn.disabled = false;
      rejectBtn.style.display = 'none';
    } else if (voucher.status === 'REQUESTED') {
      approveBtn.textContent = '전표 승인';
      approveBtn.disabled = false;
      rejectBtn.style.display = 'inline-block';
      rejectBtn.textContent = '반려/취소';
    } else if (voucher.status === 'APPROVED') {
      approveBtn.textContent = '전표 발행(POST)';
      approveBtn.disabled = false;
      rejectBtn.style.display = 'inline-block';
      rejectBtn.textContent = '반려/취소';
    } else {
      approvalArea.style.display = 'none';
    }
  }

  // 승인/반려 상태 변경 실행
  async function handleStatusChange(actionType) {
    const voucher = state.currentVoucher;
    if (!voucher) return;

    let targetStatus = '';
    if (actionType === 'approve') {
      if (voucher.status === 'DRAFT') targetStatus = 'REQUESTED';
      else if (voucher.status === 'REQUESTED') targetStatus = 'APPROVED';
      else if (voucher.status === 'APPROVED') targetStatus = 'POSTED';
    } else if (actionType === 'reject') {
      targetStatus = 'CANCELLED';
    }

    if (!targetStatus) return;

    try {
      await window.voucherCommandService.updateVoucherStatus(voucher.id, targetStatus);
      window.ddukApi.showToast(`전표 상태가 변경되었습니다. [${targetStatus}]`, 'success');
      
      document.getElementById('detailModal')?.classList.remove('is_active');
      
      loadSummary();
      loadVouchers();
    } catch (e) {
      window.ddukApi.showToast(e.message || '상태 변경에 실패했습니다.', 'error');
    }
  }

  // 저장 성공 시 highlight Glow & [NEW] 임시 배지
  function triggerHighlight(voucherNo) {
    setTimeout(() => {
      const rows = document.querySelectorAll('#voucherListBody tr');
      let targetRow = null;
      rows.forEach((row) => {
        if (row.dataset.voucherNo === voucherNo) {
          targetRow = row;
        }
      });

      if (targetRow) {
        // smooth scroll
        targetRow.scrollIntoView({ behavior: 'smooth', block: 'center' });
        
        // 글로우 하이라이트 애니메이션
        targetRow.style.transition = 'background-color 0.4s ease, box-shadow 0.4s ease';
        targetRow.style.backgroundColor = '#fef08a'; // 노란색
        targetRow.style.boxShadow = '0 0 16px rgba(250, 204, 21, 0.6)';
        
        // [NEW] 임시 배지 달기
        const cell = targetRow.querySelector('.vo-status-cell');
        if (cell) {
          const badge = document.createElement('span');
          badge.className = 'status_badge info';
          badge.style.marginLeft = '6px';
          badge.style.animation = 'pulse 1s infinite';
          badge.textContent = 'NEW';
          cell.appendChild(badge);
          
          setTimeout(() => {
            targetRow.style.backgroundColor = '';
            targetRow.style.boxShadow = '';
            badge.remove();
          }, 2500);
        }
      }
    }, 300);
  }

  // ==========================================
  // [6] 검색 다이얼로그 (Lookup) 제어
  // ==========================================

  function openLookup(target, initialKeyword = '') {
    state.lookupTarget = target;
    const dialog = document.getElementById('lookupDialog');
    const keyword = document.getElementById('lookupKeyword');
    const title = {
      vendor: '거래처 검색',
      businessAccount: state.voucherType === 'SALES' ? '매출계정 검색' : '매입계정 검색',
      settlementAccount: state.voucherType === 'SALES' ? '입금계좌 검색' : '출금계좌 검색',
    }[target] || '검색';

    setText('lookupTitle', title);
    if (keyword) keyword.value = initialKeyword;
    setHtml('lookupResults', '');
    if (dialog) dialog.classList.add('is_active');
    runLookupSearch();
  }

  async function runLookupSearch() {
    const keyword = getValue('lookupKeyword').trim();
    
    try {
      if (state.lookupTarget === 'vendor') {
        const response = await window.voucherQueryService.searchVendors(keyword);
        const vendors = response || [];
        renderLookupRows(vendors.map((vendor) => ({
          id: vendor.id,
          code: vendor.vendorCode || vendor.businessRegistrationNo || '',
          name: vendor.name,
          type: vendor.status || '',
          level: 1,
          raw: vendor,
        })));
        return;
      }

      let accountType = null;
      let cashOnly = false;
      if (state.lookupTarget === 'settlementAccount') {
        cashOnly = true;
      } else {
        accountType = state.voucherType === 'SALES' ? 'REVENUE' : 'EXPENSE';
      }

      const response = await window.voucherQueryService.searchAccounts(keyword, accountType, cashOnly);
      const accounts = response.data || [];
      renderLookupRows(accounts.map((account) => ({
        id: account.id,
        code: account.code,
        name: account.name,
        type: account.type,
        status: account.status,
        allowPosting: account.allowPosting,
        level: account.level || 1,
        raw: account,
      })));
    } catch (e) {
      window.ddukApi.showToast('검색 데이터 로드 중 오류가 발생했습니다.', 'error');
    }
  }

  function renderLookupRows(rows) {
    const results = document.getElementById('lookupResults');
    if (!results) return;
    if (!rows.length) {
      results.innerHTML = '<div class="lookup_empty">검색 결과가 없습니다.</div>';
      return;
    }

    results.innerHTML = rows.map((row) => `
      <button type="button" class="account_lookup_row" data-id="${row.id}" style="--depth:${Math.max((row.level || 1) - 1, 0)}; width: 100%;">
        <small>${escapeHtml(row.code)}</small>
        <strong>${escapeHtml(row.name)}</strong>
        <small>${escapeHtml(row.type)} · ${row.allowPosting === false ? '사용불가' : escapeHtml(row.status || 'ACTIVE')}</small>
      </button>
    `).join('');

    results.querySelectorAll('.account_lookup_row').forEach((button) => {
      const row = rows.find((item) => String(item.id) === button.dataset.id);
      if (row) {
        button.addEventListener('click', () => selectLookupRow(row.raw));
      }
    });
  }

  function selectLookupRow(row) {
    if (!row) return;
    if (state.lookupTarget === 'vendor') {
      state.selected.vendor = row;
      setValue('vendorName', row.name || '');
      setValue('vendorId', row.id || '');
    } else if (state.lookupTarget === 'businessAccount') {
      state.selected.businessAccount = row;
      setValue('businessAccountName', `${row.code} ${row.name}`);
      setValue('businessAccountId', row.id);
    } else if (state.lookupTarget === 'settlementAccount') {
      state.selected.settlementAccount = row;
      setValue('settlementAccountName', `${row.code} ${row.name}`);
      setValue('settlementAccountId', row.id);
    }

    document.getElementById('lookupDialog')?.classList.remove('is_active');
    refreshJournalPreview();
  }

  function resetForm() {
    document.getElementById('voucherForm')?.reset();
    const voucherDate = document.getElementById('voucherDate');
    if (voucherDate) voucherDate.valueAsDate = new Date();
    state.selected.vendor = null;
    clearSelectedAccounts();
    recalculateAmounts();
  }

  function clearSelectedAccounts() {
    state.selected.businessAccount = null;
    state.selected.settlementAccount = null;
    setValue('businessAccountName', '');
    setValue('businessAccountId', '');
    setValue('settlementAccountName', '');
    setValue('settlementAccountId', '');
  }

  // ==========================================
  // [7] 공용 유틸리티
  // ==========================================

  function summarizeLines(lines) {
    return lines.reduce((acc, line) => {
      acc.supply += Number(line.supplyAmount || 0);
      acc.vat += Number(line.vatAmount || 0);
      if (line.debitCredit === 'DEBIT') {
        acc.total += Number(line.totalAmount || 0);
      }
      return acc;
    }, { supply: 0, vat: 0, total: 0 });
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

  function formatDateTime(value) {
    if (!value) return '';
    return String(value).replace('T', ' ').slice(0, 16);
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

  function setHtml(id, value) {
    const el = document.getElementById(id);
    if (el) el.innerHTML = value;
  }

  function setLoadingText(tbodyId, colspan, message) {
    const tbody = document.getElementById(tbodyId);
    if (!tbody) return;
    tbody.innerHTML = `<tr><td colspan="${colspan}" style="text-align: center; color: var(--text-muted); padding: 30px;">${escapeHtml(message)}</td></tr>`;
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

  function escapeHtml(value) {
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }
})();
