package com.dduk.service.accounting;

import com.dduk.dto.accounting.payroll.PayrollLedgerCreateRequest;
import com.dduk.dto.accounting.payroll.PayrollLedgerResponse;
import com.dduk.dto.accounting.voucher.VoucherLineRequest;
import com.dduk.dto.accounting.voucher.VoucherRequest;
import com.dduk.dto.accounting.voucher.VoucherResponse;
import com.dduk.entity.accounting.Account;
import com.dduk.entity.accounting.AccountSide;
import com.dduk.entity.accounting.payroll.PayrollSelectionMode;
import com.dduk.entity.accounting.payroll.PayrollSettlementCycle;
import com.dduk.entity.accounting.payroll.PayrollStatus;
import com.dduk.entity.accounting.payroll.PayrollTargetPeriodMode;
import com.dduk.entity.accounting.payroll.PayrollTaxType;
import com.dduk.entity.accounting.payroll.PayrollType;
import com.dduk.entity.accounting.period.AccountingPeriod;
import com.dduk.entity.accounting.period.AccountingPeriodStatus;
import com.dduk.entity.accounting.period.ClosingActionType;
import com.dduk.entity.accounting.period.ClosingLog;
import com.dduk.entity.accounting.voucher.enums.VatType;
import com.dduk.entity.accounting.voucher.enums.VoucherStatus;
import com.dduk.entity.accounting.voucher.enums.VoucherType;
import com.dduk.entity.hr.Employee;
import com.dduk.entity.hr.PayrollContract;
import com.dduk.entity.inventory.Vendor;
import com.dduk.repository.accounting.AccountRepository;
import com.dduk.repository.accounting.JournalEntryRepository;
import com.dduk.repository.accounting.period.AccountingPeriodRepository;
import com.dduk.repository.accounting.period.ClosingLogRepository;
import com.dduk.repository.accounting.voucher.VoucherRepository;
import com.dduk.repository.hr.EmployeeRepository;
import com.dduk.repository.hr.PayrollContractRepository;
import com.dduk.repository.inventory.VendorRepository;
import com.dduk.service.accounting.payroll.PayrollManagementService;
import com.dduk.service.accounting.report.TrialBalanceReportService;
import com.dduk.service.accounting.voucher.VoucherService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import com.dduk.repository.accounting.payroll.PayrollLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class AccountingDataSeeder {

    private final VoucherService voucherService;
    private final PayrollManagementService payrollManagementService;
    private final EmployeeRepository employeeRepository;
    private final PayrollContractRepository payrollContractRepository;
    private final VendorRepository vendorRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final ClosingLogRepository closingLogRepository;
    private final VoucherRepository voucherRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final AccountRepository accountRepository;
    private final TrialBalanceReportService trialBalanceReportService;
    private final AccountManagementService accountManagementService;
    private final PayrollLedgerRepository payrollLedgerRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Value("${app.accounting.seed:true}")
    private boolean seedEnabled;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        if (!seedEnabled) {
            log.info("[AccountingDataSeeder] Seeding is disabled in configuration.");
            return;
        }
        try {
            accountManagementService.seedDefaultChartOfAccounts();
            seedPipeline();
        } catch (Exception e) {
            log.error("[AccountingDataSeeder] Critical error occurred during the seeding pipeline", e);
        }
    }

    @Transactional
    public void seedPipeline() {
        syncAccountingPeriodsForPresentation();

        boolean hasExistingVouchers = voucherRepository.count() > 0 || journalEntryRepository.count() > 0;
        if (hasExistingVouchers) {
            log.info("[AccountingDataSeeder] Existing vouchers/journals detected. Performing targeted presentation data upsert instead of full rebuild.");
            
            // 사원 마스터 데이터를 한글명 5명으로 업데이트/추가
            seedPhase2OrgAndHr();
            
            // 5월 발표용 손익계산서 전표(매출 150M, 원가 90M, 판관비 25M, 법인세 7M)가 없다면 강제 추가
            upsertPresentationVouchersForMay2026();
            
            // 급여대장 데이터가 없거나 부족하면 2~5월 급여 데이터 강제 생성
            if (payrollLedgerRepository.count() == 0) {
                log.info("[AccountingDataSeeder] No payroll ledgers found. Seeding payroll-only data...");
                seedPhase5PayrollAndPosting();
            }
            
            // 보고서 분석 테이블 리빌드
            trialBalanceReportService.rebuildAll();
            return;
        }

        log.info("[AccountingDataSeeder] Starting full accounting demo seed pipeline...");

        seedPhase1MasterData();
        seedPhase2OrgAndHr();
        seedPhase3VoucherDrafts();
        seedPhase4VoucherPosting();
        seedPhase5PayrollAndPosting();
        seedPhase6ReportingRebuild();

        log.info("[AccountingDataSeeder] Accounting demo seed pipeline completed successfully.");
    }

    private void seedPhase1MasterData() {
        log.info(">> [Phase 1] Seeding accounting periods and fallback vendors...");

        for (int year : List.of(2025, 2026)) {
            for (int month = 1; month <= 12; month++) {
                if (!accountingPeriodRepository.existsByFiscalYearAndFiscalMonth(year, month)) {
                    YearMonth yearMonth = YearMonth.of(year, month);
                    accountingPeriodRepository.save(AccountingPeriod.builder()
                            .fiscalYear(year)
                            .fiscalMonth(month)
                            .startDate(yearMonth.atDay(1))
                            .endDate(yearMonth.atEndOfMonth())
                            .status(AccountingPeriodStatus.OPEN)
                            .build());
                }
            }
        }

        if (vendorRepository.count() == 0) {
            vendorRepository.save(Vendor.builder()
                    .vendorCode("V005")
                    .businessRegistrationNo("120-81-12345")
                    .name("Amante Direct")
                    .representativeName("Kim Sangman")
                    .businessType("Retail")
                    .businessItem("Bedding")
                    .status("ACTIVE")
                    .memo("Fallback vendor for accounting demo seed")
                    .build());

            vendorRepository.save(Vendor.builder()
                    .vendorCode("V001")
                    .businessRegistrationNo("101-81-54321")
                    .name("Korea Card")
                    .representativeName("Lee Daesoon")
                    .businessType("Finance")
                    .businessItem("Corporate card")
                    .status("ACTIVE")
                    .memo("Fallback card vendor")
                    .build());

            vendorRepository.save(Vendor.builder()
                    .vendorCode("V002")
                    .businessRegistrationNo("104-86-98765")
                    .name("Tax Partner")
                    .representativeName("Kang Gamchan")
                    .businessType("Service")
                    .businessItem("Tax advisory")
                    .status("ACTIVE")
                    .memo("Fallback tax vendor")
                    .build());

            vendorRepository.save(Vendor.builder()
                    .vendorCode("V003")
                    .businessRegistrationNo("105-82-11111")
                    .name("Info Line")
                    .representativeName("Jeon Moondeok")
                    .businessType("Manufacturing")
                    .businessItem("Office supplies")
                    .status("ACTIVE")
                    .memo("Fallback office vendor")
                    .build());
        }
    }

    private void seedPhase2OrgAndHr() {
        log.info(">> [Phase 2] Seeding employees and active payroll contracts...");

        Employee emp1 = upsertEmployee(
                "EMP20240001",
                "gildong.hong@dduk.com",
                "홍길동",
                "Development",
                "Lead",
                "010-1111-2222",
                LocalDate.of(2024, 1, 1)
        );
        ensurePayrollContract(emp1, "CON-20240001", new BigDecimal("5000000"), LocalDate.of(2025, 1, 1));

        Employee emp2 = upsertEmployee(
                "EMP20240002",
                "chulsoo.kim@dduk.com",
                "김철수",
                "Planning",
                "Manager",
                "010-1234-5678",
                LocalDate.of(2024, 2, 1)
        );
        ensurePayrollContract(emp2, "CON-20240002", new BigDecimal("4000000"), LocalDate.of(2025, 1, 1));

        Employee emp3 = upsertEmployee(
                "EMP20240003",
                "younghee.lee@dduk.com",
                "이영희",
                "HR",
                "Staff",
                "010-2345-6789",
                LocalDate.of(2024, 3, 1)
        );
        ensurePayrollContract(emp3, "CON-20240003", new BigDecimal("3200000"), LocalDate.of(2025, 1, 1));

        Employee emp4 = upsertEmployee(
                "EMP20240004",
                "minsu.park@dduk.com",
                "박민수",
                "Accounting",
                "Manager",
                "010-3456-7890",
                LocalDate.of(2024, 4, 1)
        );
        ensurePayrollContract(emp4, "CON-20240004", new BigDecimal("4500000"), LocalDate.of(2025, 1, 1));

        Employee emp5 = upsertEmployee(
                "EMP20240005",
                "jihoon.choi@dduk.com",
                "최지훈",
                "Sales",
                "Staff",
                "010-4567-8901",
                LocalDate.of(2024, 5, 1)
        );
        ensurePayrollContract(emp5, "CON-20240005", new BigDecimal("3800000"), LocalDate.of(2025, 1, 1));

        employeeRepository.findAll().forEach(this::ensureFallbackPayrollContract);
    }

    private void seedPhase3VoucherDrafts() {
        log.info(">> [Phase 3] Seeding voucher draft data...");

        List<Vendor> vendors = vendorRepository.findAll();
        if (vendors.isEmpty()) {
            return;
        }
        Vendor primaryVendor = vendors.get(0);

        Account revenueAccount = resolveSeedAccount("revenue", "4110", "4001");
        Account bankAccount = resolveSeedAccount("bank", "1112", "1002");
        Account welfareAccount = resolveSeedAccount("welfare", "5230", "5003");

        if (revenueAccount == null || bankAccount == null || welfareAccount == null) {
            log.warn("[AccountingDataSeeder] Required accounts missing for draft vouchers.");
            return;
        }

        VoucherRequest salesRequest = new VoucherRequest();
        salesRequest.setVoucherDate(LocalDate.of(2026, 5, 28));
        salesRequest.setVoucherType(VoucherType.SALES);
        salesRequest.setVatType(VatType.TAX_INVOICE);
        salesRequest.setVendorId(primaryVendor.getId());
        salesRequest.setVendorNameSnapshot(primaryVendor.getName());
        salesRequest.setSupplyAmount(new BigDecimal("6000000"));
        salesRequest.setVatAmount(new BigDecimal("600000"));
        salesRequest.setFeeAmount(BigDecimal.ZERO);
        salesRequest.setBusinessAccountId(revenueAccount.getId());
        salesRequest.setSettlementAccountId(bankAccount.getId());
        salesRequest.setDescription("Demo sales voucher pending posting");
        voucherService.createVoucher(salesRequest);

        VoucherRequest purchaseRequest = new VoucherRequest();
        purchaseRequest.setVoucherDate(LocalDate.of(2026, 5, 29));
        purchaseRequest.setVoucherType(VoucherType.PURCHASE);
        purchaseRequest.setVatType(VatType.TAX_INVOICE);
        purchaseRequest.setVendorId(primaryVendor.getId());
        purchaseRequest.setVendorNameSnapshot(primaryVendor.getName());
        purchaseRequest.setSupplyAmount(new BigDecimal("1500000"));
        purchaseRequest.setVatAmount(new BigDecimal("150000"));
        purchaseRequest.setFeeAmount(BigDecimal.ZERO);
        purchaseRequest.setBusinessAccountId(welfareAccount.getId());
        purchaseRequest.setSettlementAccountId(bankAccount.getId());
        purchaseRequest.setDescription("Demo purchase voucher pending posting");
        voucherService.createVoucher(purchaseRequest);
    }

    private void seedPhase4VoucherPosting() {
        log.info(">> [Phase 4] Seeding posted voucher history...");

        List<Vendor> vendors = vendorRepository.findAll();
        if (vendors.isEmpty()) {
            return;
        }
        Vendor primaryVendor = vendors.get(0);
        Vendor officeVendor = vendors.size() > 3 ? vendors.get(3) : primaryVendor;

        Account bankAccount = resolveSeedAccount("bank", "1112", "1002");
        Account capitalAccount = resolveSeedAccount("capital", "3110");
        Account revenueAccount = resolveSeedAccount("revenue", "4110", "4001");
        Account welfareAccount = resolveSeedAccount("welfare", "5230", "5003");
        Account rentAccount = resolveSeedAccount("rent", "5280");
        Account utilityAccount = resolveSeedAccount("utility", "5300");
        Account officeAccount = resolveSeedAccount("office", "5330");

        if (bankAccount == null || capitalAccount == null || revenueAccount == null || welfareAccount == null) {
            log.warn("[AccountingDataSeeder] Required accounts missing for voucher posting phase.");
            return;
        }

        VoucherRequest capitalRequest = new VoucherRequest();
        capitalRequest.setVoucherDate(LocalDate.of(2025, 12, 1));
        capitalRequest.setVoucherType(VoucherType.GENERAL);
        capitalRequest.setVatType(VatType.ZERO_TAX);
        capitalRequest.setVendorNameSnapshot("Founding capital");
        capitalRequest.setSupplyAmount(new BigDecimal("100000000"));
        capitalRequest.setVatAmount(BigDecimal.ZERO);
        capitalRequest.setFeeAmount(BigDecimal.ZERO);
        capitalRequest.setDescription("Demo capital injection");

        VoucherLineRequest debitLine = new VoucherLineRequest();
        debitLine.setAccountId(bankAccount.getId());
        debitLine.setAccountCode(bankAccount.getCode());
        debitLine.setAccountName(bankAccount.getName());
        debitLine.setDebitCredit(AccountSide.DEBIT);
        debitLine.setTotalAmount(new BigDecimal("100000000"));
        debitLine.setSupplyAmount(BigDecimal.ZERO);
        debitLine.setVatAmount(BigDecimal.ZERO);
        debitLine.setDescription("Capital deposited to bank");

        VoucherLineRequest creditLine = new VoucherLineRequest();
        creditLine.setAccountId(capitalAccount.getId());
        creditLine.setAccountCode(capitalAccount.getCode());
        creditLine.setAccountName(capitalAccount.getName());
        creditLine.setDebitCredit(AccountSide.CREDIT);
        creditLine.setTotalAmount(new BigDecimal("100000000"));
        creditLine.setSupplyAmount(BigDecimal.ZERO);
        creditLine.setVatAmount(BigDecimal.ZERO);
        creditLine.setDescription("Capital account recognized");

        capitalRequest.setLines(List.of(debitLine, creditLine));
        createAndTransitionVoucher(capitalRequest, VoucherStatus.POSTED);

        int[] salesAmounts = {12000000, 15000000, 18000000, 22000000, 16000000};
        int[] purchaseAmounts = {3500000, 4200000, 3800000, 5000000, 2800000};

        for (int index = 0; index < 4; index++) {
            int month = index + 1;

            VoucherRequest salesVoucher = new VoucherRequest();
            salesVoucher.setVoucherDate(LocalDate.of(2026, month, 15));
            salesVoucher.setVoucherType(VoucherType.SALES);
            salesVoucher.setVatType(VatType.TAX_INVOICE);
            salesVoucher.setVendorId(primaryVendor.getId());
            salesVoucher.setVendorNameSnapshot(primaryVendor.getName());
            salesVoucher.setSupplyAmount(new BigDecimal(salesAmounts[index]));
            salesVoucher.setVatAmount(new BigDecimal(salesAmounts[index] / 10));
            salesVoucher.setFeeAmount(BigDecimal.ZERO);
            salesVoucher.setBusinessAccountId(revenueAccount.getId());
            salesVoucher.setSettlementAccountId(bankAccount.getId());
            salesVoucher.setDescription("Demo monthly sales voucher for " + month + " month");
            createAndTransitionVoucher(salesVoucher, VoucherStatus.POSTED);

            VoucherRequest purchaseVoucher = new VoucherRequest();
            purchaseVoucher.setVoucherDate(LocalDate.of(2026, month, 20));
            purchaseVoucher.setVoucherType(VoucherType.PURCHASE);
            purchaseVoucher.setVatType(VatType.TAX_INVOICE);
            purchaseVoucher.setVendorId(officeVendor.getId());
            purchaseVoucher.setVendorNameSnapshot(officeVendor.getName());
            purchaseVoucher.setSupplyAmount(new BigDecimal(purchaseAmounts[index]));
            purchaseVoucher.setVatAmount(new BigDecimal(purchaseAmounts[index] / 10));
            purchaseVoucher.setFeeAmount(BigDecimal.ZERO);

            Account targetExpense = welfareAccount;
            if (month == 2 && rentAccount != null) {
                targetExpense = rentAccount;
            }
            if (month == 3 && utilityAccount != null) {
                targetExpense = utilityAccount;
            }
            if (month == 4 && officeAccount != null) {
                targetExpense = officeAccount;
            }

            purchaseVoucher.setBusinessAccountId(targetExpense.getId());
            purchaseVoucher.setSettlementAccountId(bankAccount.getId());
            purchaseVoucher.setDescription("Demo shared expense voucher for " + month + " month");
            createAndTransitionVoucher(purchaseVoucher, VoucherStatus.POSTED);
        }

        VoucherRequest requestedSales = new VoucherRequest();
        requestedSales.setVoucherDate(LocalDate.of(2026, 5, 27));
        requestedSales.setVoucherType(VoucherType.SALES);
        requestedSales.setVatType(VatType.TAX_INVOICE);
        requestedSales.setVendorId(primaryVendor.getId());
        requestedSales.setVendorNameSnapshot(primaryVendor.getName());
        requestedSales.setSupplyAmount(new BigDecimal("7500000"));
        requestedSales.setVatAmount(new BigDecimal("750000"));
        requestedSales.setFeeAmount(BigDecimal.ZERO);
        requestedSales.setBusinessAccountId(revenueAccount.getId());
        requestedSales.setSettlementAccountId(bankAccount.getId());
        requestedSales.setDescription("Demo requested sales voucher");
        createAndTransitionVoucher(requestedSales, VoucherStatus.REQUESTED);
    }

    private void seedPhase5PayrollAndPosting() {
        log.info(">> [Phase 5] Seeding payroll ledger data...");
        seedPayrollHelper("2026-02", LocalDate.of(2026, 2, 25), true);
        seedPayrollHelper("2026-03", LocalDate.of(2026, 3, 25), true);
        seedPayrollHelper("2026-04", LocalDate.of(2026, 4, 25), true);
        seedPayrollHelper("2026-05", LocalDate.of(2026, 5, 25), false);
    }

    private void seedPhase6ReportingRebuild() {
        log.info(">> [Phase 6] Rebuilding reports and closing historical periods...");
        trialBalanceReportService.rebuildAll();
        closePeriodHelper(2025, 12);
        closePeriodHelper(2026, 1);
        closePeriodHelper(2026, 2);
        closePeriodHelper(2026, 3);
        closePeriodHelper(2026, 4);
    }

    private void createAndTransitionVoucher(VoucherRequest request, VoucherStatus targetStatus) {
        if (isPeriodClosed(request.getVoucherDate())) {
            log.info("[AccountingDataSeeder] Skipping seed voucher for closed period: {}", request.getVoucherDate());
            return;
        }
        VoucherResponse response = voucherService.createVoucher(request);
        if (targetStatus == VoucherStatus.DRAFT) {
            return;
        }
        voucherService.updateStatus(response.getId(), VoucherStatus.REQUESTED);
        if (targetStatus == VoucherStatus.REQUESTED) {
            return;
        }
        voucherService.updateStatus(response.getId(), VoucherStatus.APPROVED);
        if (targetStatus == VoucherStatus.APPROVED) {
            return;
        }
        voucherService.updateStatus(response.getId(), VoucherStatus.POSTED);
    }

    private void seedPayrollHelper(String yearMonth, LocalDate paymentDate, boolean confirm) {
        try {
            if (isPeriodClosed(paymentDate)) {
                log.info("[AccountingDataSeeder] Skipping seed payroll for closed period: {}", yearMonth);
                return;
            }
            if (payrollLedgerRepository.findFirstByPaymentYearMonthOrderByPaymentDateAscIdAsc(yearMonth).isPresent()) {
                log.info("[AccountingDataSeeder] Payroll ledger already exists for {}. Skipping.", yearMonth);
                return;
            }
            PayrollLedgerCreateRequest request = new PayrollLedgerCreateRequest();
            request.setAttributionYearMonth(yearMonth);
            request.setPaymentYearMonth(yearMonth);
            request.setPayrollType(PayrollType.SALARY);
            request.setTaxType(PayrollTaxType.TAXABLE);
            request.setSettlementCycle(PayrollSettlementCycle.MONTHLY);
            request.setTargetPeriodMode(PayrollTargetPeriodMode.BULK);
            request.setPaymentDate(paymentDate);
            request.setLedgerName(yearMonth + " demo payroll");
            request.setSettlementItemSelectionMode(PayrollSelectionMode.ALL);
            request.setEmployeeSelectionMode(PayrollSelectionMode.ALL);
            request.setCreatedBy("system");

            PayrollLedgerResponse response = payrollManagementService.createLedger(request, true);
            if (confirm) {
                payrollManagementService.confirmLedger(response.getId());
            }
        } catch (Exception e) {
            log.error("[AccountingDataSeeder] Failed to seed payroll for period {}", yearMonth, e);
        }
    }

    private void closePeriodHelper(int year, int month) {
        accountingPeriodRepository.findByFiscalYearAndFiscalMonth(year, month)
                .ifPresent(period -> {
                    try {
                        period.close("system");
                        accountingPeriodRepository.save(period);

                        closingLogRepository.save(ClosingLog.builder()
                                .accountingPeriod(period)
                                .actionType(ClosingActionType.MONTH_CLOSED)
                                .fromStatus(AccountingPeriodStatus.OPEN)
                                .toStatus(AccountingPeriodStatus.CLOSED)
                                .actor("system")
                                .ipAddress("127.0.0.1")
                                .message("Seeder closed this historical period during bootstrap.")
                                .build());
                    } catch (Exception e) {
                        log.error("[AccountingDataSeeder] Failed to lock period {}-{}", year, month, e);
                    }
                });
    }

    private Employee upsertEmployee(
            String employeeNo,
            String email,
            String name,
            String department,
            String position,
            String phone,
            LocalDate hireDate
    ) {
        Employee employee = employeeRepository.findByEmployeeNo(employeeNo)
                .or(() -> employeeRepository.findByEmail(email))
                .orElseGet(Employee::new);

        employee.setEmployeeNo(employeeNo);
        employee.setEmail(email);
        employee.setName(name);
        employee.setDepartment(department);
        employee.setPosition(position);
        employee.setEmploymentStatus("ACTIVE");
        employee.setHireDate(hireDate);
        employee.setPhone(phone);
        return employeeRepository.save(employee);
    }

    private void ensurePayrollContract(Employee employee, String contractNo, BigDecimal baseSalary, LocalDate contractDate) {
        PayrollContract contract = payrollContractRepository.findByEmployeeId(employee.getId())
                .orElseGet(() -> payrollContractRepository.findByContractNo(contractNo).orElseGet(PayrollContract::new));

        contract.setEmployee(employee);
        contract.setContractNo(contractNo);
        contract.setBaseSalary(baseSalary);
        contract.setContractDate(contractDate);
        contract.setStatus("ACTIVE");
        payrollContractRepository.save(contract);
    }

    private void ensureFallbackPayrollContract(Employee employee) {
        if (payrollContractRepository.findByEmployeeId(employee.getId()).isPresent()) {
            return;
        }

        ensurePayrollContract(
                employee,
                "AUTO-" + employee.getId(),
                new BigDecimal("3600000"),
                employee.getHireDate() != null ? employee.getHireDate() : LocalDate.of(2025, 1, 1)
        );
        log.warn("[AccountingDataSeeder] Added fallback payroll contract for employee {}", employee.getEmployeeNo());
    }

    private Account resolveSeedAccount(String label, String... candidateCodes) {
        for (String candidateCode : candidateCodes) {
            Account account = accountRepository.findByCode(candidateCode).orElse(null);
            if (account != null) {
                return account;
            }
        }
        log.warn("[AccountingDataSeeder] Missing {} account. Tried codes: {}", label, Arrays.toString(candidateCodes));
        return null;
    }

    private void syncAccountingPeriodsForPresentation() {
        log.info(">> Syncing accounting periods status for presentation...");
        
        try {
            jdbcTemplate.update("UPDATE payroll_ledger_employee SET employee_name_snapshot = ? WHERE employee_no_snapshot = ?", "Hong Gil Dong", "EMP20240001");
            jdbcTemplate.update("UPDATE payroll_ledger_employee SET employee_name_snapshot = ? WHERE employee_no_snapshot = ?", "Kim Chul Soo", "EMP20240002");
            jdbcTemplate.update("UPDATE payroll_ledger_employee SET employee_name_snapshot = ? WHERE employee_no_snapshot = ?", "Lee Young Hee", "EMP20240003");
            jdbcTemplate.update("UPDATE payroll_ledger_employee SET employee_name_snapshot = ? WHERE employee_no_snapshot = ?", "Park Min Soo", "EMP20240004");
            jdbcTemplate.update("UPDATE payroll_ledger_employee SET employee_name_snapshot = ? WHERE employee_no_snapshot = ?", "Choi Ji Hoon", "EMP20240005");
        } catch (Exception e) {
            log.warn("[AccountingDataSeeder] Payroll employee snapshot normalization skipped: {}", e.getMessage());
        }

        for (int month = 1; month <= 12; month++) {
            final int m = month;
            AccountingPeriod period = accountingPeriodRepository.findByFiscalYearAndFiscalMonth(2026, month)
                    .orElseGet(() -> {
                        YearMonth ym = YearMonth.of(2026, m);
                        return accountingPeriodRepository.save(AccountingPeriod.builder()
                                .fiscalYear(2026)
                                .fiscalMonth(m)
                                .startDate(ym.atDay(1))
                                .endDate(ym.atEndOfMonth())
                                .status(AccountingPeriodStatus.OPEN)
                                .build());
                    });

            if (period.isClosed()) {
                log.debug("[AccountingDataSeeder] Existing closed accounting period preserved: {}", period.getPeriodKey());
            }
        }
    }

    private boolean isPeriodClosed(LocalDate date) {
        return accountingPeriodRepository.findByFiscalYearAndFiscalMonth(date.getYear(), date.getMonthValue())
                .map(AccountingPeriod::isClosed)
                .orElse(false);
    }

    private void upsertPresentationVouchersForMay2026() {
        log.info(">> Upserting presentation vouchers for May 2026...");
        boolean hasPresentationSales = voucherRepository.findAll().stream()
                .anyMatch(v -> "5월 발표용 매출 전표".equals(v.getDescription()));
        
        if (hasPresentationSales) {
            log.info(">> Presentation vouchers for May 2026 already exist. Skipping upsert.");
            return;
        }

        List<Vendor> vendors = vendorRepository.findAll();
        if (vendors.isEmpty()) {
            log.warn("Cannot upsert presentation vouchers: no vendors found.");
            return;
        }
        Vendor primaryVendor = vendors.get(0);

        Account bankAccount = resolveSeedAccount("bank", "1112", "1002");
        Account salesAccount = resolveSeedAccount("sales", "4110", "4001");
        Account costAccount = resolveSeedAccount("cogs", "5110", "5002");
        Account expenseAccount = resolveSeedAccount("welfare", "5250", "5003");
        Account taxAccount = resolveSeedAccount("tax", "8000");

        if (bankAccount == null || salesAccount == null || costAccount == null || expenseAccount == null || taxAccount == null) {
            log.warn("Required accounts missing for presentation vouchers.");
            return;
        }

        // 1. 5월 매출 전표: 150M
        VoucherRequest salesReq = new VoucherRequest();
        salesReq.setVoucherDate(LocalDate.of(2026, 5, 15));
        salesReq.setVoucherType(VoucherType.SALES);
        salesReq.setVatType(VatType.TAX_INVOICE);
        salesReq.setVendorId(primaryVendor.getId());
        salesReq.setVendorNameSnapshot(primaryVendor.getName());
        salesReq.setSupplyAmount(new BigDecimal("150000000"));
        salesReq.setVatAmount(new BigDecimal("15000000"));
        salesReq.setFeeAmount(BigDecimal.ZERO);
        salesReq.setBusinessAccountId(salesAccount.getId());
        salesReq.setSettlementAccountId(bankAccount.getId());
        salesReq.setDescription("5월 발표용 매출 전표");
        createAndTransitionVoucher(salesReq, VoucherStatus.POSTED);

        // 2. 5월 매출원가 전표: 90M
        VoucherRequest cogsReq = new VoucherRequest();
        cogsReq.setVoucherDate(LocalDate.of(2026, 5, 20));
        cogsReq.setVoucherType(VoucherType.PURCHASE);
        cogsReq.setVatType(VatType.TAX_INVOICE);
        cogsReq.setVendorId(primaryVendor.getId());
        cogsReq.setVendorNameSnapshot(primaryVendor.getName());
        cogsReq.setSupplyAmount(new BigDecimal("90000000"));
        cogsReq.setVatAmount(new BigDecimal("9000000"));
        cogsReq.setFeeAmount(BigDecimal.ZERO);
        cogsReq.setBusinessAccountId(costAccount.getId());
        cogsReq.setSettlementAccountId(bankAccount.getId());
        cogsReq.setDescription("5월 발표용 매출원가 전표");
        createAndTransitionVoucher(cogsReq, VoucherStatus.POSTED);

        // 3. 5월 판관비 전표: 25M
        VoucherRequest expenseReq = new VoucherRequest();
        expenseReq.setVoucherDate(LocalDate.of(2026, 5, 22));
        expenseReq.setVoucherType(VoucherType.PURCHASE);
        expenseReq.setVatType(VatType.TAX_INVOICE);
        expenseReq.setVendorId(primaryVendor.getId());
        expenseReq.setVendorNameSnapshot(primaryVendor.getName());
        expenseReq.setSupplyAmount(new BigDecimal("25000000"));
        expenseReq.setVatAmount(new BigDecimal("2500000"));
        expenseReq.setFeeAmount(BigDecimal.ZERO);
        expenseReq.setBusinessAccountId(expenseAccount.getId());
        expenseReq.setSettlementAccountId(bankAccount.getId());
        expenseReq.setDescription("5월 발표용 판매관리비 전표");
        createAndTransitionVoucher(expenseReq, VoucherStatus.POSTED);

        // 4. 5월 법인세비용 전표: 7M
        VoucherRequest taxReq = new VoucherRequest();
        taxReq.setVoucherDate(LocalDate.of(2026, 5, 25));
        taxReq.setVoucherType(VoucherType.PURCHASE);
        taxReq.setVatType(VatType.ZERO_TAX);
        taxReq.setVendorId(primaryVendor.getId());
        taxReq.setVendorNameSnapshot(primaryVendor.getName());
        taxReq.setSupplyAmount(new BigDecimal("7000000"));
        taxReq.setVatAmount(BigDecimal.ZERO);
        taxReq.setFeeAmount(BigDecimal.ZERO);
        taxReq.setBusinessAccountId(taxAccount.getId());
        taxReq.setSettlementAccountId(bankAccount.getId());
        taxReq.setDescription("5월 발표용 법인세비용 전표");
        createAndTransitionVoucher(taxReq, VoucherStatus.POSTED);
        
        log.info(">> Presentation vouchers for May 2026 upserted successfully.");
    }
}
