package com.dduk.service.admin;

import com.dduk.repository.accounting.AccountRepository;
import com.dduk.repository.accounting.JournalEntryRepository;
import com.dduk.repository.admin.AnomalyLogRepository;
import com.dduk.repository.admin.TaskHistoryRepository;
import com.dduk.repository.hr.EmployeeRepository;
import com.dduk.repository.hr.PayrollContractRepository;
import com.dduk.repository.inventory.InventoryRepository;
import com.dduk.repository.inventory.PurchaseOrderRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(4)
@RequiredArgsConstructor
@Slf4j
public class DemoSeedDiagnosticsService {

    private final InventoryRepository inventoryRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final EmployeeRepository employeeRepository;
    private final PayrollContractRepository payrollContractRepository;
    private final AccountRepository accountRepository;
    private final AnomalyLogRepository anomalyLogRepository;
    private final TaskHistoryRepository taskHistoryRepository;
    private final JournalEntryRepository journalEntryRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void logDiagnostics() {
        long inventoryCount = inventoryRepository.count();
        long purchaseOrderCount = purchaseOrderRepository.count();
        long employeeCount = employeeRepository.count();
        long payrollContractCount = payrollContractRepository.count();
        long anomalyCount = anomalyLogRepository.count();
        long taskHistoryCount = taskHistoryRepository.count();
        long journalEntryCount = journalEntryRepository.count();

        log.info(
                "[DemoSeedDiagnostics] counts inventory={}, purchaseOrders={}, employees={}, payrollContracts={}, anomalies={}, taskHistory={}, journalEntries={}",
                inventoryCount,
                purchaseOrderCount,
                employeeCount,
                payrollContractCount,
                anomalyCount,
                taskHistoryCount,
                journalEntryCount
        );

        if (purchaseOrderCount > 0 && inventoryCount == 0) {
            log.error("[DemoSeedDiagnostics] Purchase orders exist but inventories are empty. Check inventory schema/sample SQL alignment.");
        }

        if (employeeCount > 0 && payrollContractCount < employeeCount) {
            log.error(
                    "[DemoSeedDiagnostics] Payroll contracts are incomplete. employees={}, payrollContracts={}.",
                    employeeCount,
                    payrollContractCount
            );
        }

        if (purchaseOrderCount > 0 && anomalyCount == 0) {
            log.error("[DemoSeedDiagnostics] Purchase activity exists but anomaly_logs is empty. Demo anomaly seed likely failed.");
        }

        if (taskHistoryCount == 0) {
            log.warn("[DemoSeedDiagnostics] task_history is empty. Admin and inventory RPA widgets will look blank.");
        }

        List<String> missingAccounts = new ArrayList<>();
        for (String code : List.of("1112", "3110", "4110", "5230", "5280", "5300", "5330")) {
            if (accountRepository.findByCode(code).isEmpty()) {
                missingAccounts.add(code);
            }
        }
        if (!missingAccounts.isEmpty()) {
            log.error("[DemoSeedDiagnostics] Required accounting seed accounts are missing: {}", missingAccounts);
        }
    }
}
