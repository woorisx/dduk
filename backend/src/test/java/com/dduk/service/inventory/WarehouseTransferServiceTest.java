package com.dduk.service.inventory;

import com.dduk.dto.inventory.WarehouseTransferItemDto;
import com.dduk.dto.inventory.WarehouseTransferRequestDto;
import com.dduk.dto.inventory.WarehouseTransferResponseDto;
import com.dduk.entity.admin.Role;
import com.dduk.entity.admin.Member;
import com.dduk.entity.inventory.*;
import com.dduk.repository.admin.MemberRepository;
import com.dduk.repository.inventory.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;

import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
public class WarehouseTransferServiceTest {

    @Test
    @org.junit.jupiter.api.DisplayName("[DB 디버깅] 실제 원격 DB의 레코드 카운트를 측정하여 표시한다.")
    void printActualDbCounts() {
        System.out.println("==================================================");
        System.out.println("=== [DEBUG] START ACTUAL DB COUNT CHECK ===");
        String[] tables = {"items", "inventories", "stock_movements", "purchase_orders"};
        for (String table : tables) {
            try {
                Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
                System.out.println("[DB_COUNT] " + table + " : " + count);
            } catch (Exception ex) {
                System.out.println("[DB_COUNT] " + table + " failed: " + ex.getMessage());
            }
        }
        System.out.println("=== [DEBUG] END ACTUAL DB COUNT CHECK ===");
        System.out.println("==================================================");
    }

    @Autowired
    private WarehouseTransferService warehouseTransferService;

    @Autowired
    private WarehouseTransferRepository warehouseTransferRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Warehouse whSource;
    private Warehouse whTarget;
    private Item testItem;
    private Member testMember;

    @BeforeEach
    void setUp() {
        // 1. 테스트용 창고 적재
        whSource = warehouseRepository.save(Warehouse.builder()
                .warehouseCode("T-SRC")
                .warehouseName("테스트 출고 창고")
                .location("서울")
                .status("ACTIVE")
                .build());

        whTarget = warehouseRepository.save(Warehouse.builder()
                .warehouseCode("T-TRG")
                .warehouseName("테스트 입고 창고")
                .location("부산")
                .status("ACTIVE")
                .build());

        // 2. 테스트용 품목 적재
        testItem = itemRepository.save(Item.builder()
                .itemCode("T-ITM-001")
                .name("테스트 찹쌀떡")
                .itemType(ItemType.FINISHED_GOOD)
                .category("완제품")
                .spec("50g*10개입")
                .unit("BOX")
                .standardCost(BigDecimal.valueOf(5000))
                .unitPrice(BigDecimal.valueOf(8000))
                .active(true)
                .build());

        // 3. 테스트용 회원(등록자/승인자) 적재
        testMember = memberRepository.save(Member.builder()
                .loginId("tester")
                .password("testpass")
                .name("재고담당자")
                .role(Role.INVENTORY)
                .active(true)
                .build());

        // 4. 출발 창고에 기초 재고 할당
        inventoryRepository.save(Inventory.builder()
                .item(testItem)
                .warehouse(whSource)
                .currentStock(100)
                .allocatedStock(0)
                .safetyStock(10)
                .averageCost(BigDecimal.valueOf(4500))
                .inventoryValue(BigDecimal.valueOf(450000))
                .build());
    }

    @Test
    @DisplayName("창고 이동 요청 등록 시 예약재고(allocated_stock)가 정상 반영되어야 한다.")
    void requestTransfer_success() {
        // given
        WarehouseTransferRequestDto request = WarehouseTransferRequestDto.builder()
                .sourceWarehouseId(whSource.getId())
                .targetWarehouseId(whTarget.getId())
                .remarks("테스트 이동 요청")
                .items(Collections.singletonList(WarehouseTransferItemDto.builder()
                        .itemId(testItem.getId())
                        .quantity(30)
                        .build()))
                .build();

        // when
        WarehouseTransferResponseDto response = warehouseTransferService.requestTransfer(request, testMember.getId());

        // then
        assertNotNull(response.getTransferNo());
        assertEquals(TransferStatus.PENDING, response.getStatus());

        // 출발 창고의 예약재고 allocated_stock 이 30으로 설정되었는지 검증
        Inventory inv = inventoryRepository.findByItemIdAndWarehouseId(testItem.getId(), whSource.getId()).orElseThrow();
        assertEquals(30, inv.getAllocatedStock());
        assertEquals(100, inv.getCurrentStock()); // 현재고는 아직 차감되지 않음
    }

    @Test
    @DisplayName("가용재고를 초과하여 창고 이동을 요청하는 경우 예외가 발생해야 한다.")
    void requestTransfer_insufficientStock() {
        // given (현재고 100, 예약 0 -> 가용재고 100)
        WarehouseTransferRequestDto request = WarehouseTransferRequestDto.builder()
                .sourceWarehouseId(whSource.getId())
                .targetWarehouseId(whTarget.getId())
                .remarks("초과 요청 테스트")
                .items(Collections.singletonList(WarehouseTransferItemDto.builder()
                        .itemId(testItem.getId())
                        .quantity(120) // 가용재고 100 초과
                        .build()))
                .build();

        // when & then
        assertThrows(IllegalArgumentException.class, () -> 
            warehouseTransferService.requestTransfer(request, testMember.getId())
        );
    }

    @Test
    @DisplayName("창고 이동 승인 시 상태가 APPROVED로 변경되어야 한다.")
    void approveTransfer_success() {
        // given
        WarehouseTransferRequestDto request = WarehouseTransferRequestDto.builder()
                .sourceWarehouseId(whSource.getId())
                .targetWarehouseId(whTarget.getId())
                .remarks("승인 대기 건")
                .items(Collections.singletonList(WarehouseTransferItemDto.builder()
                        .itemId(testItem.getId())
                        .quantity(20)
                        .build()))
                .build();
        WarehouseTransferResponseDto pending = warehouseTransferService.requestTransfer(request, testMember.getId());

        // when
        WarehouseTransferResponseDto approved = warehouseTransferService.approveTransfer(pending.getId(), testMember.getId());

        // then
        assertEquals(TransferStatus.APPROVED, approved.getStatus());
        assertEquals("재고담당자", approved.getApprovedByName());
    }

    @Test
    @DisplayName("창고 이동 완료 시 재고 증감 및 양방향 이력 적재가 단일 트랜잭션으로 완벽히 실행되어야 한다.")
    void completeTransfer_success() {
        // given
        WarehouseTransferRequestDto request = WarehouseTransferRequestDto.builder()
                .sourceWarehouseId(whSource.getId())
                .targetWarehouseId(whTarget.getId())
                .remarks("이동 완료 테스트")
                .items(Collections.singletonList(WarehouseTransferItemDto.builder()
                        .itemId(testItem.getId())
                        .quantity(30)
                        .build()))
                .build();
        WarehouseTransferResponseDto pending = warehouseTransferService.requestTransfer(request, testMember.getId());
        warehouseTransferService.approveTransfer(pending.getId(), testMember.getId());

        // when
        WarehouseTransferResponseDto completed = warehouseTransferService.completeTransfer(pending.getId());

        // then
        assertEquals(TransferStatus.COMPLETED, completed.getStatus());

        // 1. 출발 창고 재고 확인 (현재고 100 -> 70, 예약 30 -> 0)
        Inventory sourceInv = inventoryRepository.findByItemIdAndWarehouseId(testItem.getId(), whSource.getId()).orElseThrow();
        assertEquals(70, sourceInv.getCurrentStock());
        assertEquals(0, sourceInv.getAllocatedStock());

        // 2. 도착 창고 재고 확인 (현재고 0 -> 30)
        Inventory targetInv = inventoryRepository.findByItemIdAndWarehouseId(testItem.getId(), whTarget.getId()).orElseThrow();
        assertEquals(30, targetInv.getCurrentStock());
        assertEquals(BigDecimal.valueOf(4500).setScale(4, RoundingMode.HALF_UP), targetInv.getAverageCost());

        // 3. 양방향 StockMovement 적재 확인 (TRANSFER_OUT, TRANSFER_IN 총 2건)
        List<StockMovement> movements = stockMovementRepository.findByItemId(testItem.getId());
        long outCount = movements.stream().filter(m -> m.getMovementType() == MovementType.TRANSFER_OUT).count();
        long inCount = movements.stream().filter(m -> m.getMovementType() == MovementType.TRANSFER_IN).count();
        assertEquals(1, outCount);
        assertEquals(1, inCount);
    }

    @Test
    @DisplayName("이동 완료 시점에 출발 창고 현재고가 부족할 경우 완료 처리가 차단되고 예외를 유발해야 한다. (이중 재검증)")
    void completeTransfer_doubleVerification_insufficient() {
        // given
        WarehouseTransferRequestDto request = WarehouseTransferRequestDto.builder()
                .sourceWarehouseId(whSource.getId())
                .targetWarehouseId(whTarget.getId())
                .remarks("이중 검증 테스트")
                .items(Collections.singletonList(WarehouseTransferItemDto.builder()
                        .itemId(testItem.getId())
                        .quantity(50)
                        .build()))
                .build();
        WarehouseTransferResponseDto pending = warehouseTransferService.requestTransfer(request, testMember.getId());
        warehouseTransferService.approveTransfer(pending.getId(), testMember.getId());

        // [강제 시나리오] 승인 후 완료 전에 다른 출고로 출발 창고 현재고를 강제로 30으로 깎음
        Inventory sourceInv = inventoryRepository.findByItemIdAndWarehouseId(testItem.getId(), whSource.getId()).orElseThrow();
        sourceInv.setCurrentStock(30); // 요청 수량 50보다 작아짐
        inventoryRepository.save(sourceInv);

        // when & then
        assertThrows(IllegalStateException.class, () -> 
            warehouseTransferService.completeTransfer(pending.getId())
        );
    }

    @Test
    @DisplayName("창고 이동 완료 처리는 멱등적으로 수행되어 중복 재고 가감이 없어야 한다.")
    void completeTransfer_idempotent() {
        // given
        WarehouseTransferRequestDto request = WarehouseTransferRequestDto.builder()
                .sourceWarehouseId(whSource.getId())
                .targetWarehouseId(whTarget.getId())
                .remarks("멱등성 테스트")
                .items(Collections.singletonList(WarehouseTransferItemDto.builder()
                        .itemId(testItem.getId())
                        .quantity(20)
                        .build()))
                .build();
        WarehouseTransferResponseDto pending = warehouseTransferService.requestTransfer(request, testMember.getId());
        warehouseTransferService.approveTransfer(pending.getId(), testMember.getId());
        warehouseTransferService.completeTransfer(pending.getId()); // 1차 완료

        // when
        WarehouseTransferResponseDto secondComplete = warehouseTransferService.completeTransfer(pending.getId()); // 2차 완료

        // then
        assertEquals(TransferStatus.COMPLETED, secondComplete.getStatus());

        // 재고가 이중으로 차감되지 않고 1회(20개)만 반영되었는지 검증
        Inventory sourceInv = inventoryRepository.findByItemIdAndWarehouseId(testItem.getId(), whSource.getId()).orElseThrow();
        assertEquals(80, sourceInv.getCurrentStock()); // 100 - 20 (중복 차감 시 60이 됨)
    }

    @Test
    @DisplayName("창고 이동 요청 취소 시 예약재고(allocated_stock)가 정상 해제되어야 한다.")
    void cancelTransfer_success() {
        // given
        WarehouseTransferRequestDto request = WarehouseTransferRequestDto.builder()
                .sourceWarehouseId(whSource.getId())
                .targetWarehouseId(whTarget.getId())
                .remarks("취소 테스트")
                .items(Collections.singletonList(WarehouseTransferItemDto.builder()
                        .itemId(testItem.getId())
                        .quantity(40)
                        .build()))
                .build();
        WarehouseTransferResponseDto pending = warehouseTransferService.requestTransfer(request, testMember.getId());

        // when
        WarehouseTransferResponseDto cancelled = warehouseTransferService.cancelTransfer(pending.getId());

        // then
        assertEquals(TransferStatus.CANCELLED, cancelled.getStatus());

        // 출발 창고의 예약재고 allocated_stock 이 0으로 복귀했는지 검증
        Inventory inv = inventoryRepository.findByItemIdAndWarehouseId(testItem.getId(), whSource.getId()).orElseThrow();
        assertEquals(0, inv.getAllocatedStock());
        assertEquals(100, inv.getCurrentStock());
    }

    @Test
    @DisplayName("상태 전이 규칙에 위배되는 조치는 원천 차단되고 예외를 유발해야 한다.")
    void stateMachine_protection() {
        // given
        WarehouseTransferRequestDto request = WarehouseTransferRequestDto.builder()
                .sourceWarehouseId(whSource.getId())
                .targetWarehouseId(whTarget.getId())
                .remarks("상태 보호 테스트")
                .items(Collections.singletonList(WarehouseTransferItemDto.builder()
                        .itemId(testItem.getId())
                        .quantity(10)
                        .build()))
                .build();
        WarehouseTransferResponseDto pending = warehouseTransferService.requestTransfer(request, testMember.getId());
        
        // APPROVED를 거치지 않고 바로 COMPLETE 시도 시 예외 발생 검증
        assertThrows(IllegalStateException.class, () -> 
            warehouseTransferService.completeTransfer(pending.getId())
        );

        // 취소된 건에 대해 승인 시도 시 예외 발생 검증
        warehouseTransferService.cancelTransfer(pending.getId());
        assertThrows(IllegalStateException.class, () -> 
            warehouseTransferService.approveTransfer(pending.getId(), testMember.getId())
        );
    }
}
