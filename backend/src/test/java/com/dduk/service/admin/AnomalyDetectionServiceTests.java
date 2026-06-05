package com.dduk.service.admin;

import com.dduk.dto.admin.AnomalyLogStatusUpdateDto;
import com.dduk.dto.admin.AnomalyRefreshResponseDto;
import com.dduk.dto.inventory.PurchaseRecommendationDto;
import com.dduk.entity.admin.AnomalyLog;
import com.dduk.entity.admin.AnomalyLogStatus;
import com.dduk.entity.inventory.Inventory;
import com.dduk.entity.inventory.Item;
import com.dduk.entity.inventory.ItemType;
import com.dduk.entity.inventory.Warehouse;
import com.dduk.repository.admin.AnomalyLogRepository;
import com.dduk.repository.inventory.InventoryRepository;
import com.dduk.service.inventory.PurchaseRecommendationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnomalyDetectionServiceTests {

    @Mock
    private AnomalyLogRepository anomalyLogRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private PurchaseRecommendationService purchaseRecommendationService;

    private AnomalyDetectionService anomalyDetectionService;

    @BeforeEach
    void setUp() {
        anomalyDetectionService = new AnomalyDetectionService(
                anomalyLogRepository,
                inventoryRepository,
                purchaseRecommendationService,
                new ObjectMapper()
        );
    }

    @Test
    void refreshAnomaliesCreatesExpectedWarnings() {
        Inventory inventory = inventory(-2, 4, 1);
        PurchaseRecommendationDto recommendation = PurchaseRecommendationDto.builder()
                .inventoryId(10L)
                .urgency("CRITICAL")
                .recommendedOrderQty(12)
                .daysUntilStockout(2)
                .leadTimeDays(7)
                .defaultVendorName("-")
                .build();

        when(inventoryRepository.findAllWithItemAndWarehouse()).thenReturn(List.of(inventory));
        when(purchaseRecommendationService.getRecommendations()).thenReturn(List.of(recommendation));
        when(anomalyLogRepository.findByAnomalyKeyIn(anyCollection())).thenReturn(List.of());
        when(anomalyLogRepository.findAll()).thenReturn(List.of());
        when(anomalyLogRepository.countByActiveTrue()).thenReturn(4L);
        when(anomalyLogRepository.countByActiveTrueAndStatus(AnomalyLogStatus.OPEN)).thenReturn(4L);
        when(anomalyLogRepository.countByActiveTrueAndStatus(AnomalyLogStatus.CONFIRMED)).thenReturn(0L);
        when(anomalyLogRepository.countByActiveTrueAndSeverity("CRITICAL")).thenReturn(2L);
        when(anomalyLogRepository.countByActiveTrueAndSeverity("HIGH")).thenReturn(2L);
        when(anomalyLogRepository.findFirstByActiveTrueOrderByLastDetectedAtDescIdDesc())
                .thenReturn(Optional.of(AnomalyLog.builder().lastDetectedAt(LocalDateTime.now()).build()));

        AnomalyRefreshResponseDto response = anomalyDetectionService.refreshAnomalies();

        ArgumentCaptor<AnomalyLog> captor = ArgumentCaptor.forClass(AnomalyLog.class);
        verify(anomalyLogRepository, atLeast(4)).save(captor.capture());
        List<String> ruleCodes = captor.getAllValues().stream().map(AnomalyLog::getRuleCode).toList();

        assertThat(ruleCodes).contains(
                "NEGATIVE_CURRENT_STOCK",
                "NEGATIVE_AVAILABLE_STOCK",
                "OUT_OF_STOCK_WITH_SAFETY",
                "REORDER_VENDOR_MISSING",
                "STOCKOUT_BEFORE_LEAD_TIME"
        );
        assertThat(response.getDetectedCount()).isEqualTo(5);
    }

    @Test
    void updateStatusAppliesReviewFields() {
        AnomalyLog anomalyLog = AnomalyLog.builder()
                .id(1L)
                .anomalyKey("NEGATIVE_CURRENT_STOCK:10")
                .ruleCode("NEGATIVE_CURRENT_STOCK")
                .severity("CRITICAL")
                .title("현재고가 음수야")
                .summary("summary")
                .sourceType("INVENTORY")
                .sourceLabel("찹쌀떡 / 본창고")
                .status(AnomalyLogStatus.OPEN)
                .firstDetectedAt(LocalDateTime.now().minusHours(1))
                .lastDetectedAt(LocalDateTime.now().minusMinutes(5))
                .build();
        AnomalyLogStatusUpdateDto request = new AnomalyLogStatusUpdateDto();
        request.setStatus("CONFIRMED");
        request.setReviewedBy("admin");
        request.setReviewNote("실재고 확인 예정");

        when(anomalyLogRepository.findById(1L)).thenReturn(Optional.of(anomalyLog));
        when(anomalyLogRepository.save(any(AnomalyLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AnomalyLog updated = anomalyDetectionService.updateStatus(1L, request);

        assertThat(updated.getStatus()).isEqualTo(AnomalyLogStatus.CONFIRMED);
        assertThat(updated.getReviewedBy()).isEqualTo("admin");
        assertThat(updated.getReviewNote()).isEqualTo("실재고 확인 예정");
        assertThat(updated.getReviewedAt()).isNotNull();
    }

    private Inventory inventory(int currentStock, int safetyStock, int allocatedStock) {
        Warehouse warehouse = Warehouse.builder()
                .id(20L)
                .warehouseCode("WH-01")
                .warehouseName("본창고")
                .location("서울")
                .status("ACTIVE")
                .build();
        Item item = Item.builder()
                .id(30L)
                .itemCode("ITEM-01")
                .name("찹쌀떡")
                .itemType(ItemType.FINISHED_GOOD)
                .category("떡")
                .spec("1kg")
                .unit("EA")
                .active(true)
                .build();
        return Inventory.builder()
                .id(10L)
                .item(item)
                .warehouse(warehouse)
                .location("A-01")
                .currentStock(currentStock)
                .safetyStock(safetyStock)
                .allocatedStock(allocatedStock)
                .averageCost(BigDecimal.TEN)
                .inventoryValue(BigDecimal.ZERO)
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
