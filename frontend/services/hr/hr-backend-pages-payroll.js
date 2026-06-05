import { hrBackendApi } from './hr-backend-api.js';
import {
    LAST_PAYROLL_KEY,
    byId,
    money,
    setText,
    showJson,
    escapeHtml,
    unwrapData,
    requestTaskHistory,
    showToast,
    safeRun,
    getCurrentSession,
    getCurrentLoginId
} from './hr-backend-pages-shared.js';

function parseTrace(value) {
    if (!value) {
        return {};
    }
    try {
        return JSON.parse(value);
    } catch (error) {
        return { raw: value };
    }
}

function normalizePayroll(record) {
    const trace = parseTrace(record.calculationTrace);
    return {
        id: record.id,
        employeeName: record.employee?.name || `직원 #${record.employee?.id || '-'}`,
        employeeNo: record.employee?.employeeNo || '-',
        payMonth: record.payMonth,
        baseSalary: Number(record.baseSalary || 0),
        allowanceAmount: Number(record.allowanceAmount || 0),
        deductionAmount: Number(record.deductionAmount || 0),
        netSalary: Number(record.netSalary || 0),
        status: record.status || '-',
        trace
    };
}

function saveLastPayroll(record) {
    localStorage.setItem(LAST_PAYROLL_KEY, JSON.stringify(record));
}

function getLastPayroll() {
    try {
        return JSON.parse(localStorage.getItem(LAST_PAYROLL_KEY) || 'null');
    } catch (error) {
        return null;
    }
}

function renderPayrollResult(record) {
    const payroll = normalizePayroll(record);
    setText('payroll_id', payroll.id || '-');
    setText('payroll_employee', payroll.employeeName);
    setText('payroll_month', payroll.payMonth || '-');
    setText('payroll_base', money(payroll.baseSalary));
    setText('payroll_allowance', money(payroll.allowanceAmount));
    setText('payroll_deduction', money(payroll.deductionAmount));
    setText('payroll_net', money(payroll.netSalary));
    setText('payroll_status', payroll.status);
    showJson('payroll_trace', payroll.trace);
}

function signedMoney(value) {
    if (value === null || value === undefined || value === '') {
        return '-';
    }
    const numberValue = Number(value || 0);
    const prefix = numberValue > 0 ? '+' : '';
    return `${prefix}${money(numberValue)}`;
}

function formatDateTime(value) {
    if (!value) {
        return '-';
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return value;
    }
    return date.toLocaleString('ko-KR');
}

function renderPayrollReferenceHistory(items) {
    const element = byId('hr_reference_history');
    if (!element) {
        return;
    }

    if (!Array.isArray(items) || !items.length) {
        element.textContent = '최근 실행 이력이 아직 없습니다.';
        return;
    }

    element.innerHTML = items.map((item) => `
        <div style="display:flex;justify-content:space-between;gap:12px;padding:12px 0;border-bottom:1px solid #e5e7eb;">
            <div style="min-width:0;">
                <div style="font-weight:800;color:#111827;">${escapeHtml(item.status || '-')}</div>
                <div style="font-size:12px;color:#64748b;margin-top:4px;">${escapeHtml(item.taskId || '-')}</div>
                <div style="font-size:12px;color:#64748b;margin-top:4px;">${escapeHtml(formatDateTime(item.requestedAt))}</div>
                <div style="font-size:12px;color:#dc2626;margin-top:4px;">${escapeHtml(item.errorMessage || '')}</div>
            </div>
            <div>
                <button class="hr_button" type="button" data-rerun-hr-reference>다시 실행</button>
            </div>
        </div>
    `).join('');

    element.querySelectorAll('[data-rerun-hr-reference]').forEach((button) => {
        button.addEventListener('click', triggerPayrollReference);
    });
}

async function loadPayrollReferenceHistory() {
    const element = byId('hr_reference_history');
    const currentSession = getCurrentSession();
    if (!currentSession || !['ADMIN', 'HR'].includes(currentSession.role)) {
        if (element) {
            element.textContent = '최근 RPA 실행 이력은 관리자만 볼 수 있습니다.';
        }
        return;
    }

    if (element) {
        element.textContent = '최근 실행 이력을 불러오는 중...';
    }

    try {
        const payload = await requestTaskHistory('/api/v1/admin/tasks?taskType=RPA&actionName=collect_hr_reference&size=5&sort=requestedAt,desc');
        const pageData = payload?.data;
        const items = Array.isArray(pageData?.content) ? pageData.content : [];
        renderPayrollReferenceHistory(items);
    } catch (error) {
        if (element) {
            element.textContent = error.message || '최근 실행 이력을 불러오지 못했습니다.';
        }
    }
}

function renderPayrollReference(summary) {
    const rpa = summary?.hrReferenceRpa || {};
    const latestPayroll = summary?.latestPayroll || null;

    setText('hr_reference_min_wage', money(rpa.minimumHourlyWage));
    setText('hr_reference_min_salary', money(rpa.minimumMonthlySalary));
    setText('hr_reference_erp_hourly', money(rpa.erpLowestHourlyRate));
    setText('hr_reference_gap', signedMoney(rpa.hourlyRateGap));
    setText('hr_reference_source', rpa.referenceSource || '-');
    setText('hr_reference_effective_date', rpa.effectiveDate || '-');
    setText('hr_reference_collected_at', formatDateTime(rpa.latestCollectedAt));
    setText('hr_reference_message', rpa.message || '최근 HR 기준 정보가 아직 없습니다.');

    const statusBadge = byId('hr_reference_status_badge');
    if (statusBadge) {
        statusBadge.textContent = rpa.status === 'READY'
            ? '비교 가능'
            : (rpa.status === 'MISSING_FILE' || rpa.status === 'EMPTY_RESULT' ? '결과 부족' : '대기');
        statusBadge.className = `hr_badge ${rpa.status === 'READY' ? 'ok' : 'warn'}`;
    }

    showJson('hr_reference_trace', {
        latestPayroll,
        hrReference: rpa.items?.[0] || null,
        employeeCount: summary?.employeeCount ?? 0,
        activeContractCount: summary?.activeContractCount ?? 0
    });
}

async function loadPayrollReferenceSummary() {
    try {
        const response = await hrBackendApi.getPayrollReferenceSummary();
        renderPayrollReference(unwrapData(response));
        loadPayrollReferenceHistory();
    } catch (error) {
        setText('hr_reference_message', error.message || 'HR 기준 정보를 불러오지 못했습니다.');
        const statusBadge = byId('hr_reference_status_badge');
        if (statusBadge) {
            statusBadge.textContent = '조회 실패';
            statusBadge.className = 'hr_badge error';
        }
        const history = byId('hr_reference_history');
        if (history) {
            history.textContent = '최근 실행 이력을 불러오지 못했습니다.';
        }
    }
}

async function triggerPayrollReference() {
    const session = window.ddukSession?.getSession?.();
    if (!session || !['ADMIN', 'HR'].includes(session.role)) {
        showToast('RPA 실행은 관리자만 가능합니다.', 'warning');
        return;
    }

    const button = byId('btn_trigger_hr_reference');
    if (button) {
        button.disabled = true;
    }

    try {
        const response = await hrBackendApi.triggerAdminRpa('HR_MIN_WAGE');
        setText('hr_reference_message', `HR 기준 조회를 요청했습니다. Task ID: ${response?.data?.taskId || '-'}`);
        const statusBadge = byId('hr_reference_status_badge');
        if (statusBadge) {
            statusBadge.textContent = '실행 요청 중';
            statusBadge.className = 'hr_badge warn';
        }
        loadPayrollReferenceHistory();
        window.setTimeout(loadPayrollReferenceSummary, 2000);
    } catch (error) {
        showToast(error.message || 'HR 기준 조회 실행 요청에 실패했습니다.', 'error');
    } finally {
        if (button) {
            button.disabled = false;
        }
    }
}

export function initPayrollCalculate() {
    const month = byId('pay_month');
    if (month) {
        month.value = new Date().toISOString().slice(0, 7);
    }

    const last = getLastPayroll();
    if (last) {
        renderPayrollResult(last);
    }

    byId('btn_refresh_hr_reference')?.addEventListener('click', loadPayrollReferenceSummary);
    byId('btn_refresh_hr_reference_history')?.addEventListener('click', loadPayrollReferenceHistory);
    byId('btn_trigger_hr_reference')?.addEventListener('click', triggerPayrollReference);

    const currentSession = getCurrentSession();
    if (!currentSession || !['ADMIN', 'HR'].includes(currentSession.role)) {
        const triggerButton = byId('btn_trigger_hr_reference');
        if (triggerButton) {
            triggerButton.disabled = true;
            triggerButton.title = 'RPA 실행은 관리자만 가능합니다.';
        }
        setText('hr_reference_history', '최근 RPA 실행 이력은 관리자만 볼 수 있습니다.');
    }

    loadPayrollReferenceSummary();

    byId('payroll_form')?.addEventListener('submit', (event) => {
        event.preventDefault();
        safeRun('page_status', async () => {
            const payload = {
                employeeId: Number(byId('employee_id').value),
                payMonth: byId('pay_month').value,
                inputs: {
                    overtimeHours: Number(byId('overtime_hours').value || 0),
                    bonus: Number(byId('bonus').value || 0),
                    allowance: Number(byId('allowance').value || 0)
                }
            };

            if (!payload.employeeId || !payload.payMonth) {
                throw new Error('직원 ID와 지급월을 입력해 주세요.');
            }

            const result = await hrBackendApi.calculatePayroll(payload);
            saveLastPayroll(result);
            renderPayrollResult(result);
        });
    });
}

export function initPayrollDetail() {
    const queryId = new URLSearchParams(window.location.search).get('id');
    const last = getLastPayroll();
    if (last) {
        renderPayrollResult(last);
        const idInput = byId('transition_payroll_id');
        if (idInput && !idInput.value) {
            idInput.value = last.id || '';
        }
    }
    if (queryId && byId('transition_payroll_id')) {
        byId('transition_payroll_id').value = queryId;
    }

    byId('transition_form')?.addEventListener('submit', (event) => {
        event.preventDefault();
        safeRun('page_status', async () => {
            const id = byId('transition_payroll_id').value;
            const payload = {
                nextStatus: byId('next_status').value,
                userId: byId('transition_user_id').value || getCurrentLoginId(),
                reason: byId('transition_reason').value
            };

            if (!id || !payload.nextStatus) {
                throw new Error('급여 ID와 다음 상태를 입력해 주세요.');
            }

            const result = await hrBackendApi.transitionPayroll(id, payload);
            saveLastPayroll(result);
            renderPayrollResult(result);
        });
    });
}

export async function initReconciliation() {
    const last = getLastPayroll();
    if (last) {
        const payroll = normalizePayroll(last);
        setText('recon_payroll_gross', money(payroll.baseSalary + payroll.allowanceAmount));
        setText('recon_payroll_deduction', money(payroll.deductionAmount));
        setText('recon_payroll_net', money(payroll.netSalary));
        setText('recon_payroll_status', payroll.status);
    }

    await safeRun('page_status', async () => {
        const profitLoss = unwrapData(await hrBackendApi.getProfitLoss());
        setText('recon_expenses', money(profitLoss.totalExpenses));
        setText('recon_revenue', money(profitLoss.totalRevenue));
        setText('recon_net_income', money(profitLoss.netIncome));
    });
}
