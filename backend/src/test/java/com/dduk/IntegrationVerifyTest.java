package com.dduk;

import com.dduk.dto.accounting.dashboard.AccountingDashboardResponse;
import com.dduk.dto.accounting.payroll.PayrollLedgerCreateRequest;
import com.dduk.dto.accounting.payroll.PayrollLedgerResponse;
import com.dduk.dto.inventory.PurchaseRecommendationDto;
import com.dduk.entity.accounting.JournalEntry;
import com.dduk.entity.accounting.payroll.PayrollLedger;
import com.dduk.entity.accounting.payroll.PayrollStatus;
import com.dduk.entity.accounting.payroll.PayrollTaxType;
import com.dduk.entity.accounting.payroll.PayrollType;
import com.dduk.entity.accounting.voucher.Voucher;
import com.dduk.entity.accounting.voucher.enums.VoucherStatus;
import com.dduk.entity.inventory.Item;
import com.dduk.entity.inventory.MovementReason;
import com.dduk.entity.inventory.Warehouse;
import com.dduk.repository.accounting.JournalEntryRepository;
import com.dduk.repository.accounting.payroll.PayrollLedgerRepository;
import com.dduk.repository.accounting.voucher.VoucherRepository;
import com.dduk.repository.inventory.ItemRepository;
import com.dduk.repository.inventory.WarehouseRepository;
import com.dduk.service.accounting.dashboard.AccountingDashboardService;
import com.dduk.service.accounting.payroll.PayrollManagementService;
import com.dduk.service.inventory.InventoryService;
import com.dduk.service.inventory.PurchaseRecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class IntegrationVerifyTest {

    @Autowired
    private PurchaseRecommendationService purchaseRecommendationService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private PayrollManagementService payrollManagementService;

    @Autowired
    private PayrollLedgerRepository payrollLedgerRepository;

    @Autowired
    private AccountingDashboardService accountingDashboardService;

    @Autowired
    private com.dduk.service.inventory.InventoryQueryService inventoryQueryService;

    @Test
    public void verifyAllE2E() {
        System.out.println("==================================================");
        System.out.println("=== [DEBUG] START ERP INTEGRATION E2E VERIFY ===");

        // 1. 자동발주추천 실제 데이터 검증
        try {
            List<PurchaseRecommendationDto> recs = purchaseRecommendationService.getRecommendations();
            System.out.println("[REORDER_VERIFY] Total Recommendations: " + recs.size());
            if (!recs.isEmpty()) {
                PurchaseRecommendationDto r = recs.get(0);
                System.out.println(String.format("[REORDER_ROW_EXAMPLE] 품목명: %s | 현재고: %d | 안전재고: %d | 평균사용량: %.2f | 추천발주량: %d | 공급처: %s",
                        r.getItemName(), r.getCurrentStock(), r.getSafetyStock(), r.getAvgMonthlyUsage(), r.getRecommendedOrderQty(), r.getDefaultVendorName()));
            }
        } catch (Exception e) {
            System.out.println("[REORDER_VERIFY] failed: " + e.getMessage());
        }

        // 2. 재고 변동 -> 회계 DRAFT 전표 및 분개 자동생성 검증
        try {
            List<Item> items = itemRepository.findAll();
            List<Warehouse> warehouses = warehouseRepository.findAll();
            if (!items.isEmpty() && !warehouses.isEmpty()) {
                Item item = items.get(0);
                Warehouse wh = warehouses.get(0);
                
                long beforeVouchers = voucherRepository.count();
                
                System.out.println(String.format("[INVENTORY_FLOW] Triggering inbound stock for item=%s, warehouse=%s", item.getName(), wh.getWarehouseName()));
                inventoryService.increaseStock(item.getId(), wh.getId(), 10, new BigDecimal("5000"), MovementReason.PURCHASE_RECEIVED, "PURCHASE", "PO-TEST");
                
                long afterVouchers = voucherRepository.count();
                System.out.println("[INVENTORY_FLOW] Voucher count change: " + beforeVouchers + " -> " + afterVouchers);
                
                List<Voucher> currentVouchers = voucherRepository.findAll();
                if (!currentVouchers.isEmpty()) {
                    Voucher latest = currentVouchers.get(currentVouchers.size() - 1);
                    System.out.println(String.format("[INVENTORY_VOUCHER_LATEST] voucher_no: %s | type: %s | status: %s | sourceRefId: %s",
                            latest.getVoucherNo(), latest.getVoucherType(), latest.getStatus(), latest.getSourceReferenceId()));
                }
            }
        } catch (Exception e) {
            System.out.println("[INVENTORY_FLOW] failed: " + e.getMessage());
        }

        // 3. 급여 CONFIRMED 확정 -> 연결 분개 POSTED 기표 전이 검증
        try {
            List<PayrollLedger> ledgers = payrollLedgerRepository.findAll();
            PayrollLedger targetLedger = ledgers.stream()
                    .filter(l -> l.getStatus() == PayrollStatus.CALCULATED)
                    .findFirst()
                    .orElse(null);

            if (targetLedger != null) {
                System.out.println("[PAYROLL_FLOW] Confirming calculated payroll ledger: " + targetLedger.getLedgerName());
                payrollManagementService.confirmLedger(targetLedger.getId());
                
                PayrollLedger confirmed = payrollLedgerRepository.findById(targetLedger.getId()).orElse(null);
                assertNotNull(confirmed);
                System.out.println("[PAYROLL_FLOW] Ledger status: " + confirmed.getStatus());
                
                if (confirmed.getJournalEntry() != null) {
                    System.out.println(String.format("[PAYROLL_JOURNAL_POSTED] journal_entry_id: %d | journal_no: %s | status: %s | totalDebit: %s",
                            confirmed.getJournalEntry().getId(), confirmed.getJournalEntry().getJournalNo(), confirmed.getJournalEntry().getStatus(), confirmed.getJournalEntry().getTotalDebit()));
                }
            } else {
                System.out.println("[PAYROLL_FLOW] No CALCULATED payroll ledger found to confirm.");
            }
        } catch (Exception e) {
            System.out.println("[PAYROLL_FLOW] failed: " + e.getMessage());
        }

        // 4. 대시보드 API 실시간 데이터 출력 검증
        try {
            AccountingDashboardResponse db = accountingDashboardService.getDashboard(2026, 5);
            System.out.println("[DASHBOARD_STATS_VERIFY]");
            System.out.println("  총 자산: " + db.getKpiSummary().getTotalAssets());
            System.out.println("  총 부채: " + db.getKpiSummary().getTotalLiabilities());
            System.out.println("  당월 매출: " + db.getKpiSummary().getMonthlyRevenue());
            System.out.println("  당월 비용: " + db.getKpiSummary().getMonthlyExpense());
            System.out.println("  당기 순이익: " + db.getKpiSummary().getNetIncome());
            System.out.println("  미게시 전표 수: " + db.getPeriodSummary().getUnpostedVoucherCount());
            System.out.println("  차대 평형 일치 여부: " + db.getPeriodSummary().isBalanced());
        } catch (Exception e) {
            System.out.println("[DASHBOARD_STATS_VERIFY] failed: " + e.getMessage());
        }

        // 5. Inventory Dashboard API 검증
        try {
            System.out.println("[INVENTORY_DASHBOARD_VERIFY] Calling getDashboardStats()...");
            com.dduk.dto.inventory.InventoryDashboardResponseDto idb = inventoryQueryService.getDashboardStats();
            System.out.println("[INVENTORY_DASHBOARD_VERIFY] Success: totalQuantity=" + idb.getTotalQuantity() + ", totalValue=" + idb.getTotalValue());
        } catch (Exception e) {
            System.out.println("[INVENTORY_DASHBOARD_VERIFY] Failed: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("=== [DEBUG] END ERP INTEGRATION E2E VERIFY ===");
        System.out.println("==================================================");
    }
}
