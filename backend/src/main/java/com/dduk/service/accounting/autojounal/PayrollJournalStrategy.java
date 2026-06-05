package com.dduk.service.accounting.autojounal;

import com.dduk.dto.accounting.JournalLineRequest;
import com.dduk.entity.hr.Payroll;
import com.dduk.service.accounting.AccountingConstants;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 급여 자동분개 전략
 * [차] 급여비용 / [대] 미지급비용(급여) + 예수금
 */
@Component
public class PayrollJournalStrategy implements JournalStrategy {

    @Override
    public String getSupportedSourceType() {
        return AccountingConstants.SOURCE_PAYROLL;
    }

    @Override
    public LocalDate getJournalDate(Object source) {
        Payroll payroll = (Payroll) source;
        String payMonth = payroll.getPayMonth(); // YYYY-MM
        return LocalDate.parse(payMonth + "-01").withDayOfMonth(
                LocalDate.parse(payMonth + "-01").lengthOfMonth());
    }

    @Override
    public String buildDescription(Object source) {
        Payroll payroll = (Payroll) source;
        return payroll.getPayMonth() + " 급여 확정 (사번: " + payroll.getEmployee().getEmployeeNo() + ")";
    }

    @Override
    public List<JournalLineRequest> buildLines(Object source) {
        Payroll payroll = (Payroll) source;
        BigDecimal totalGross = payroll.getBaseSalary().add(payroll.getAllowanceAmount());
        BigDecimal deduction = payroll.getDeductionAmount();
        BigDecimal netSalary = payroll.getNetSalary();

        List<JournalLineRequest> lines = new ArrayList<>();

        // 차변: 급여 (총지급액)
        JournalLineRequest debitLine = new JournalLineRequest();
        debitLine.setAccountCode(AccountingConstants.PAYROLL_EXPENSE);
        debitLine.setDebitAmount(totalGross);
        debitLine.setCreditAmount(BigDecimal.ZERO);
        debitLine.setDescription("급여비용");
        lines.add(debitLine);

        // 대변: 예수금 (공제액)
        if (deduction.compareTo(BigDecimal.ZERO) > 0) {
            JournalLineRequest withholding = new JournalLineRequest();
            withholding.setAccountCode(AccountingConstants.WITHHOLDING_PAYABLE);
            withholding.setDebitAmount(BigDecimal.ZERO);
            withholding.setCreditAmount(deduction);
            withholding.setDescription("예수금(공제)");
            lines.add(withholding);
        }

        // 대변: 미지급비용(급여) (실지급액)
        JournalLineRequest salaryPayable = new JournalLineRequest();
        salaryPayable.setAccountCode(AccountingConstants.SALARY_PAYABLE);
        salaryPayable.setDebitAmount(BigDecimal.ZERO);
        salaryPayable.setCreditAmount(netSalary);
        salaryPayable.setDescription("미지급급여");
        lines.add(salaryPayable);

        return lines;
    }
}
