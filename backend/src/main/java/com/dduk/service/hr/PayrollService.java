package com.dduk.service.hr;

import com.dduk.entity.admin.TaskHistory;
import com.dduk.entity.admin.TaskHistoryStatus;
import com.dduk.entity.admin.TaskHistoryType;
import com.dduk.entity.hr.Employee;
import com.dduk.entity.hr.Payroll;
import com.dduk.entity.hr.PayrollContract;
import com.dduk.repository.admin.TaskHistoryRepository;
import com.dduk.repository.hr.EmployeeRepository;
import com.dduk.repository.hr.PayrollContractRepository;
import com.dduk.repository.hr.PayrollRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PayrollService {

    private static final String HR_MIN_WAGE_ACTION = "collect_hr_reference";

    private final PayrollRepository payrollRepository;
    private final PayrollContractRepository contractRepository;
    private final EmployeeRepository employeeRepository;
    private final TaskHistoryRepository taskHistoryRepository;
    private final ObjectMapper objectMapper;

    /**
     * Calculate and Save Payroll
     */
    @Transactional
    public Payroll calculatePayroll(Long employeeId, String payMonth, Map<String, Object> inputs) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        PayrollContract contract = contractRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new RuntimeException("Payroll Contract not found for employee"));

        BigDecimal baseSalary = contract.getBaseSalary();
        BigDecimal overtimeHours = new BigDecimal(inputs.getOrDefault("overtimeHours", 0).toString());
        BigDecimal bonus = new BigDecimal(inputs.getOrDefault("bonus", 0).toString());
        BigDecimal allowance = new BigDecimal(inputs.getOrDefault("allowance", 0).toString());

        BigDecimal hourlyRate = baseSalary.divide(new BigDecimal("209"), 2, RoundingMode.HALF_UP);
        BigDecimal overtimePay = hourlyRate.multiply(new BigDecimal("1.5")).multiply(overtimeHours).setScale(0, RoundingMode.DOWN);

        BigDecimal grossPay = baseSalary.add(overtimePay).add(bonus).add(allowance);

        BigDecimal pension = grossPay.multiply(new BigDecimal("0.045")).setScale(0, RoundingMode.DOWN);
        BigDecimal health = grossPay.multiply(new BigDecimal("0.03545")).setScale(0, RoundingMode.DOWN);
        BigDecimal longTermCare = health.multiply(new BigDecimal("0.1295")).setScale(0, RoundingMode.DOWN);
        BigDecimal employmentInsurance = grossPay.multiply(new BigDecimal("0.009")).setScale(0, RoundingMode.DOWN);

        BigDecimal annualSalary = grossPay.multiply(new BigDecimal("12"));
        BigDecimal incomeTax;
        if (annualSalary.compareTo(new BigDecimal("14000000")) <= 0) {
            incomeTax = annualSalary.multiply(new BigDecimal("0.06")).divide(new BigDecimal("12"), 0, RoundingMode.DOWN);
        } else if (annualSalary.compareTo(new BigDecimal("50000000")) <= 0) {
            incomeTax = annualSalary.multiply(new BigDecimal("0.15")).subtract(new BigDecimal("1260000")).divide(new BigDecimal("12"), 0, RoundingMode.DOWN);
        } else {
            incomeTax = annualSalary.multiply(new BigDecimal("0.24")).subtract(new BigDecimal("5760000")).divide(new BigDecimal("12"), 0, RoundingMode.DOWN);
        }
        BigDecimal localIncomeTax = incomeTax.multiply(new BigDecimal("0.1")).setScale(0, RoundingMode.DOWN);

        BigDecimal totalDeductions = pension.add(health).add(longTermCare).add(employmentInsurance).add(incomeTax).add(localIncomeTax);
        BigDecimal netPay = grossPay.subtract(totalDeductions);

        Payroll payroll = payrollRepository.findByEmployeeIdAndPayMonth(employeeId, payMonth)
                .orElseGet(Payroll::new);

        payroll.setEmployee(employee);
        payroll.setPayMonth(payMonth);
        payroll.setBaseSalary(baseSalary);
        payroll.setAllowanceAmount(bonus.add(allowance).add(overtimePay));
        payroll.setDeductionAmount(totalDeductions);
        payroll.setNetSalary(netPay);
        payroll.setStatus("CALCULATED");
        payroll.setCalculationTrace(String.format(
                "{\"base\":%s, \"overtime\":%s, \"gross\":%s, \"deductions\":{\"pension\":%s, \"health\":%s, \"tax\":%s}, \"net\":%s}",
                baseSalary, overtimePay, grossPay, pension, health, incomeTax, netPay
        ));

        return payrollRepository.save(payroll);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPayrollReferenceSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();

        Optional<Payroll> latestPayroll = payrollRepository.findTopByOrderByUpdatedAtDescIdDesc();
        List<PayrollContract> contracts = contractRepository.findAll();
        long activeContracts = contracts.stream()
                .filter(contract -> "ACTIVE".equalsIgnoreCase(contract.getStatus()))
                .count();

        BigDecimal lowestHourlyRate = contracts.stream()
                .filter(contract -> "ACTIVE".equalsIgnoreCase(contract.getStatus()))
                .map(contract -> {
                    if (contract.getHourlyRate() != null && contract.getHourlyRate().compareTo(BigDecimal.ZERO) > 0) {
                        return contract.getHourlyRate();
                    }
                    if (contract.getBaseSalary() == null) {
                        return null;
                    }
                    return contract.getBaseSalary().divide(new BigDecimal("209"), 0, RoundingMode.HALF_UP);
                })
                .filter(rate -> rate != null && rate.compareTo(BigDecimal.ZERO) > 0)
                .min(BigDecimal::compareTo)
                .orElse(null);

        summary.put("employeeCount", employeeRepository.count());
        summary.put("activeContractCount", activeContracts);
        summary.put("lowestHourlyRate", lowestHourlyRate);
        summary.put("latestPayroll", latestPayroll.map(this::summarizePayroll).orElse(null));
        summary.put("hrReferenceRpa", buildHrReferenceRpa(lowestHourlyRate));
        return summary;
    }

    private Map<String, Object> summarizePayroll(Payroll payroll) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", payroll.getId());
        result.put("employeeName", payroll.getEmployee() == null ? null : payroll.getEmployee().getName());
        result.put("employeeNo", payroll.getEmployee() == null ? null : payroll.getEmployee().getEmployeeNo());
        result.put("payMonth", payroll.getPayMonth());
        result.put("baseSalary", payroll.getBaseSalary());
        result.put("allowanceAmount", payroll.getAllowanceAmount());
        result.put("deductionAmount", payroll.getDeductionAmount());
        result.put("netSalary", payroll.getNetSalary());
        result.put("status", payroll.getStatus());
        result.put("updatedAt", payroll.getUpdatedAt() == null ? null : payroll.getUpdatedAt().toString());
        return result;
    }

    private Map<String, Object> buildHrReferenceRpa(BigDecimal lowestHourlyRate) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskType", "HR_MIN_WAGE");
        result.put("actionName", HR_MIN_WAGE_ACTION);
        result.put("available", false);
        result.put("status", "EMPTY");
        result.put("message", "아직 완료된 HR 기준 정보 조회 결과가 없어.");
        result.put("latestTaskId", null);
        result.put("latestCollectedAt", null);
        result.put("referenceSource", null);
        result.put("effectiveDate", null);
        result.put("minimumHourlyWage", null);
        result.put("minimumMonthlySalary", null);
        result.put("erpLowestHourlyRate", lowestHourlyRate);
        result.put("hourlyRateGap", null);
        result.put("items", List.of());

        Optional<TaskHistory> latestTask = taskHistoryRepository
                .findFirstByTaskTypeAndActionNameAndStatusOrderByCompletedAtDescIdDesc(
                        TaskHistoryType.RPA,
                        HR_MIN_WAGE_ACTION,
                        TaskHistoryStatus.SUCCESS
                );

        if (latestTask.isEmpty()) {
            return result;
        }

        TaskHistory taskHistory = latestTask.get();
        result.put("latestTaskId", taskHistory.getTaskId());
        result.put("latestCollectedAt", taskHistory.getCompletedAt() == null ? null : taskHistory.getCompletedAt().toString());

        String sourceFilePath = readString(parseMap(taskHistory.getResponsePayload()), "data", "filePath");
        if (sourceFilePath == null || sourceFilePath.isBlank()) {
            result.put("status", "MISSING_FILE");
            result.put("message", "최근 HR 기준 조회 이력은 있지만 결과 파일 경로를 찾지 못했어.");
            return result;
        }

        Path resolvedPath = resolveProjectPath(sourceFilePath);
        if (resolvedPath == null || !Files.exists(resolvedPath)) {
            result.put("status", "MISSING_FILE");
            result.put("message", "최근 HR 기준 조회 이력은 있지만 결과 파일이 없어.");
            return result;
        }

        Map<String, Object> reference = readReferenceFile(resolvedPath);
        if (reference.isEmpty()) {
            result.put("status", "EMPTY_RESULT");
            result.put("message", "최근 HR 기준 조회 파일은 있지만 비교할 기준 정보가 비어 있어.");
            return result;
        }

        BigDecimal minimumHourlyWage = toBigDecimal(reference.get("minimumHourlyWage"));
        BigDecimal minimumMonthlySalary = toBigDecimal(reference.get("minimumMonthlySalary"));
        result.put("available", true);
        result.put("status", "READY");
        result.put("message", "최저임금과 가이드 기준을 최근 ERP 시급과 비교할 수 있어.");
        result.put("referenceSource", reference.get("sourceLabel"));
        result.put("effectiveDate", reference.get("effectiveDate"));
        result.put("minimumHourlyWage", minimumHourlyWage);
        result.put("minimumMonthlySalary", minimumMonthlySalary);
        result.put("erpLowestHourlyRate", lowestHourlyRate);
        result.put(
                "hourlyRateGap",
                minimumHourlyWage == null || lowestHourlyRate == null
                        ? null
                        : lowestHourlyRate.subtract(minimumHourlyWage).setScale(0, RoundingMode.HALF_UP)
        );
        result.put("items", List.of(reference));
        return result;
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (IOException exception) {
            return Map.of();
        }
    }

    private String readString(Map<String, Object> root, String parentKey, String childKey) {
        Object parent = root.get(parentKey);
        if (parent instanceof Map<?, ?> nested) {
            Object value = nested.get(childKey);
            return value == null ? null : String.valueOf(value);
        }
        return null;
    }

    private Map<String, Object> readReferenceFile(Path filePath) {
        try {
            return objectMapper.readValue(filePath.toFile(), new TypeReference<Map<String, Object>>() {});
        } catch (IOException exception) {
            return Map.of();
        }
    }

    private Path resolveProjectPath(String filePath) {
        Path rawPath = Paths.get(filePath);
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        Path projectRoot = "backend".equalsIgnoreCase(workingDir.getFileName().toString())
                ? workingDir.getParent()
                : workingDir;
        Path outputRoot = projectRoot.resolve("rpa").resolve("outputs").normalize();
        Path resolved = rawPath.isAbsolute()
                ? rawPath.normalize()
                : projectRoot.resolve(filePath).normalize();

        if (!resolved.startsWith(outputRoot)) {
            return null;
        }
        return resolved;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.setScale(0, RoundingMode.HALF_UP);
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue()).setScale(0, RoundingMode.HALF_UP);
        }
        try {
            return new BigDecimal(String.valueOf(value).replace(",", "")).setScale(0, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
