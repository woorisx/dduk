package com.dduk.service.inventory;

import com.dduk.dto.inventory.PurchaseRecommendationDto;
import com.dduk.entity.inventory.Inventory;
import com.dduk.entity.inventory.Item;
import com.dduk.entity.inventory.ItemType;
import com.dduk.entity.inventory.MovementType;
import com.dduk.entity.inventory.PurchaseOrder;
import com.dduk.entity.inventory.PurchaseOrderItem;
import com.dduk.entity.inventory.StockMovement;
import com.dduk.entity.inventory.Vendor;
import com.dduk.entity.inventory.Warehouse;
import com.dduk.repository.inventory.InventoryRepository;
import com.dduk.repository.inventory.PurchaseOrderItemRepository;
import com.dduk.repository.inventory.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseRecommendationServiceTests {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private PurchaseOrderItemRepository purchaseOrderItemRepository;

    private PurchaseRecommendationService purchaseRecommendationService;

    @BeforeEach
    void setUp() {
        purchaseRecommendationService = new PurchaseRecommendationService(
                inventoryRepository,
                stockMovementRepository,
                purchaseOrderItemRepository
        );
    }

    @Test
    void returnsReadyRecommendationWhenEvidenceIsSufficient() {
        Inventory inventory = inventory(5, 10, 1);
        when(inventoryRepository.findAllWithItemAndWarehouse()).thenReturn(List.of(inventory));
        when(stockMovementRepository.findAllWithFetch()).thenReturn(List.of(
                movement(inventory, 12, 3),
                movement(inventory, 8, 8),
                movement(inventory, 10, 18)
        ));
        when(purchaseOrderItemRepository.findRecentLeadTimeCandidates(any()))
                .thenReturn(List.of(purchaseOrderItem(inventory.getItem(), 5)));

        List<PurchaseRecommendationDto> recommendations = purchaseRecommendationService.getRecommendations();

        assertThat(recommendations).hasSize(1);
        PurchaseRecommendationDto recommendation = recommendations.get(0);
        assertThat(recommendation.getRecommendationStatus()).isEqualTo("READY");
        assertThat(recommendation.isOrderable()).isTrue();
        assertThat(recommendation.isLeadTimeReliable()).isTrue();
        assertThat(recommendation.getOutboundHistoryDays()).isEqualTo(3);
        assertThat(recommendation.getRecentPurchaseOrderCount()).isEqualTo(1);
    }

    @Test
    void disablesRecommendationWhenOutboundHistoryIsInsufficient() {
        Inventory inventory = inventory(4, 10, 0);
        when(inventoryRepository.findAllWithItemAndWarehouse()).thenReturn(List.of(inventory));
        when(stockMovementRepository.findAllWithFetch()).thenReturn(List.of(
                movement(inventory, 5, 2)
        ));
        when(purchaseOrderItemRepository.findRecentLeadTimeCandidates(any()))
                .thenReturn(List.of(purchaseOrderItem(inventory.getItem(), 6)));

        List<PurchaseRecommendationDto> recommendations = purchaseRecommendationService.getRecommendations();

        assertThat(recommendations).hasSize(1);
        PurchaseRecommendationDto recommendation = recommendations.get(0);
        assertThat(recommendation.getRecommendationStatus()).isEqualTo("DISABLED");
        assertThat(recommendation.isOrderable()).isFalse();
        assertThat(recommendation.getStatusReason()).contains("출고 이력");
    }

    @Test
    void marksRecommendationForReviewWhenStockIsNegative() {
        Inventory inventory = inventory(-3, 10, 0);
        when(inventoryRepository.findAllWithItemAndWarehouse()).thenReturn(List.of(inventory));
        when(stockMovementRepository.findAllWithFetch()).thenReturn(List.of(
                movement(inventory, 18, 4),
                movement(inventory, 15, 9),
                movement(inventory, 12, 16)
        ));
        when(purchaseOrderItemRepository.findRecentLeadTimeCandidates(any()))
                .thenReturn(List.of(purchaseOrderItem(inventory.getItem(), 7)));

        List<PurchaseRecommendationDto> recommendations = purchaseRecommendationService.getRecommendations();

        assertThat(recommendations).hasSize(1);
        PurchaseRecommendationDto recommendation = recommendations.get(0);
        assertThat(recommendation.getRecommendationStatus()).isEqualTo("REVIEW");
        assertThat(recommendation.isOrderable()).isFalse();
        assertThat(recommendation.getReviewMessage()).contains("자동 추천");
    }

    @Test
    void usesAvailableStockAsTheReorderBasisWhenAllocatedStockIsHigh() {
        Inventory inventory = inventory(20, 10, 18);
        when(inventoryRepository.findAllWithItemAndWarehouse()).thenReturn(List.of(inventory));
        when(stockMovementRepository.findAllWithFetch()).thenReturn(List.of(
                movement(inventory, 15, 3),
                movement(inventory, 12, 8),
                movement(inventory, 9, 17)
        ));
        when(purchaseOrderItemRepository.findRecentLeadTimeCandidates(any()))
                .thenReturn(List.of(purchaseOrderItem(inventory.getItem(), 5)));

        List<PurchaseRecommendationDto> recommendations = purchaseRecommendationService.getRecommendations();

        assertThat(recommendations).hasSize(1);
        PurchaseRecommendationDto recommendation = recommendations.get(0);
        assertThat(recommendation.getRecommendationStatus()).isEqualTo("READY");
        assertThat(recommendation.getRecommendedOrderQty()).isEqualTo(14);
        assertThat(recommendation.getUrgency()).isEqualTo("HIGH");
    }

    private Inventory inventory(int currentStock, int safetyStock, int allocatedStock) {
        Vendor vendor = Vendor.builder()
                .id(40L)
                .name("테스트 공급처")
                .build();
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
                .name("테스트 품목")
                .itemType(ItemType.FINISHED_GOOD)
                .category("원재료")
                .spec("1kg")
                .unit("EA")
                .defaultVendor(vendor)
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
                .inventoryValue(BigDecimal.valueOf(1000))
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private StockMovement movement(Inventory inventory, int quantity, int daysAgo) {
        return StockMovement.builder()
                .id((long) (100 + daysAgo))
                .item(inventory.getItem())
                .warehouse(inventory.getWarehouse())
                .movementType(MovementType.OUTBOUND)
                .quantity(quantity)
                .createdAt(LocalDateTime.now().minusDays(daysAgo))
                .referenceNo("OUT-" + daysAgo)
                .build();
    }

    private PurchaseOrderItem purchaseOrderItem(Item item, int leadTimeDays) {
        PurchaseOrder purchaseOrder = PurchaseOrder.builder()
                .id(70L)
                .purchaseOrderNo("PO-001")
                .orderDate(LocalDate.now().minusDays(leadTimeDays + 2L))
                .expectedDate(LocalDate.now().minusDays(2))
                .totalAmount(BigDecimal.valueOf(50000))
                .build();
        return PurchaseOrderItem.builder()
                .id(80L)
                .purchaseOrder(purchaseOrder)
                .item(item)
                .quantity(20)
                .unit("EA")
                .unitPrice(BigDecimal.valueOf(2500))
                .lineAmount(BigDecimal.valueOf(50000))
                .expectedDate(LocalDate.now().minusDays(2))
                .build();
    }
}
