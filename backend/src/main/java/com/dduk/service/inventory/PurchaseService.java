package com.dduk.service.inventory;

import com.dduk.service.accounting.AccountingConstants;
import com.dduk.service.accounting.autojounal.AutoJournalService;
import com.dduk.dto.admin.OcrPurchaseOrderLinkRequestDto;
import com.dduk.dto.inventory.PurchaseOrderCreateDto;
import com.dduk.dto.inventory.PurchaseOrderItemCreateDto;
import com.dduk.dto.inventory.PurchaseOrderItemResponseDto;
import com.dduk.dto.inventory.PurchaseOrderItemUpdateDto;
import com.dduk.dto.inventory.PurchaseOrderResponseDto;
import com.dduk.dto.inventory.PurchaseOrderStatusUpdateDto;
import com.dduk.dto.inventory.PurchaseOrderUpdateDto;
import com.dduk.dto.inventory.PurchaseRequestCreateDto;
import com.dduk.dto.inventory.PurchaseRequestResponseDto;
import com.dduk.entity.inventory.Item;
import com.dduk.entity.inventory.MovementReason;
import com.dduk.entity.inventory.MovementType;
import com.dduk.entity.inventory.PurchaseOrder;
import com.dduk.entity.inventory.PurchaseOrderItem;
import com.dduk.entity.inventory.PurchaseStatus;
import com.dduk.entity.inventory.Vendor;
import com.dduk.entity.inventory.Warehouse;
import com.dduk.repository.inventory.ItemRepository;
import com.dduk.repository.inventory.PurchaseOrderRepository;
import com.dduk.repository.inventory.StockMovementRepository;
import com.dduk.repository.inventory.VendorRepository;
import com.dduk.repository.inventory.WarehouseRepository;
import com.dduk.entity.admin.Member;
import com.dduk.repository.admin.MemberRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.lang.reflect.Field;

@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final VendorRepository vendorRepository;
    private final MemberRepository memberRepository;
    private final ItemRepository itemRepository;
    private final WarehouseRepository warehouseRepository;
    private final AutoJournalService autoJournalService;
    private final InventoryService inventoryService;
    private final StockMovementRepository stockMovementRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private static final BigDecimal TAX_RATE = new BigDecimal("0.1");

    @Transactional(readOnly = true)
    public List<PurchaseOrderResponseDto> searchOrderResponses(String keyword, boolean all) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (!all && normalizedKeyword.isEmpty()) {
            return List.of();
        }

        StringBuilder sql = new StringBuilder("""
                SELECT
                    po.id,
                    po.purchase_order_no,
                    po.status,
                    po.vendor_id,
                    v.name AS vendor_name,
                    po.requested_by_member_id,
                    requested.name AS requested_name,
                    po.approved_by_member_id,
                    approved.name AS approved_name,
                    po.order_date,
                    po.expected_date,
                    po.created_at,
                    po.total_amount,
                    po.note
                FROM purchase_orders po
                JOIN vendors v ON v.id = po.vendor_id
                LEFT JOIN members requested ON requested.id = po.requested_by_member_id
                LEFT JOIN members approved ON approved.id = po.approved_by_member_id
                WHERE UPPER(po.status) IN ('ORDERED', 'PENDING', 'REQUESTED', 'PENDING-PO', 'PENDING_PO')
                """);

        if (!all) {
            sql.append("""
                  AND (
                      LOWER(po.purchase_order_no) LIKE LOWER(CONCAT('%', :keyword, '%'))
                      OR LOWER(v.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                      OR LOWER(requested.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                      OR LOWER(requested.login_id) LIKE LOWER(CONCAT('%', :keyword, '%'))
                      OR CAST(requested.id AS CHAR) LIKE CONCAT('%', :keyword, '%')
                  )
                """);
        }

        sql.append(" ORDER BY po.created_at DESC");

        Query query = entityManager.createNativeQuery(sql.toString());
        if (!all) {
            query.setParameter("keyword", normalizedKeyword);
        }

        return ((List<Object[]>) query.getResultList()).stream()
                .map(this::toPurchaseOrderResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderResponseDto> searchManagementOrderResponses(String keyword, boolean all) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (!all && normalizedKeyword.isEmpty()) {
            return List.of();
        }

        StringBuilder sql = new StringBuilder("""
                SELECT
                    po.id,
                    po.purchase_order_no,
                    po.status,
                    po.vendor_id,
                    v.name AS vendor_name,
                    po.requested_by_member_id,
                    requested.name AS requested_name,
                    po.approved_by_member_id,
                    approved.name AS approved_name,
                    po.order_date,
                    po.expected_date,
                    po.created_at,
                    po.total_amount,
                    po.note
                FROM purchase_orders po
                JOIN vendors v ON v.id = po.vendor_id
                LEFT JOIN members requested ON requested.id = po.requested_by_member_id
                LEFT JOIN members approved ON approved.id = po.approved_by_member_id
                WHERE UPPER(po.status) IN ('ORDERED', 'PENDING', 'REQUESTED', 'PENDING-PO', 'PENDING_PO')
                """);

        if (!all) {
            sql.append("""
                  AND (
                      LOWER(po.purchase_order_no) LIKE LOWER(CONCAT('%', :keyword, '%'))
                      OR LOWER(v.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                      OR LOWER(requested.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                      OR LOWER(requested.login_id) LIKE LOWER(CONCAT('%', :keyword, '%'))
                      OR CAST(requested.id AS CHAR) LIKE CONCAT('%', :keyword, '%')
                  )
                """);
        }

        sql.append(" ORDER BY po.created_at DESC");

        Query query = entityManager.createNativeQuery(sql.toString());
        if (!all) {
            query.setParameter("keyword", normalizedKeyword);
        }

        return ((List<Object[]>) query.getResultList()).stream()
                .map(this::toPurchaseOrderResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderResponseDto> getReceivableOrderResponses(String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        boolean hasKeyword = !normalizedKeyword.isEmpty();

        StringBuilder sql = new StringBuilder("""
                SELECT
                    po.id,
                    po.purchase_order_no,
                    po.status,
                    po.vendor_id,
                    v.name AS vendor_name,
                    po.requested_by_member_id,
                    requested.name AS requested_name,
                    po.approved_by_member_id,
                    approved.name AS approved_name,
                    po.order_date,
                    po.expected_date,
                    po.created_at,
                    po.total_amount,
                    po.note
                FROM purchase_orders po
                JOIN vendors v ON v.id = po.vendor_id
                LEFT JOIN members requested ON requested.id = po.requested_by_member_id
                LEFT JOIN members approved ON approved.id = po.approved_by_member_id
                WHERE UPPER(po.status) NOT IN ('RECEIVING', 'RECEIVED', 'COMPLETED', 'CANCELLED')
                  AND NOT EXISTS (
                      SELECT 1
                      FROM stock_movements sm
                      WHERE sm.reference_type = 'PURCHASE'
                        AND sm.reference_id = po.purchase_order_no
                        AND sm.movement_type = 'INBOUND'
                  )
                """);

        if (hasKeyword) {
            sql.append("""
                  AND (
                      LOWER(po.purchase_order_no) LIKE LOWER(CONCAT('%', :keyword, '%'))
                      OR LOWER(v.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                      OR LOWER(requested.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                      OR LOWER(requested.login_id) LIKE LOWER(CONCAT('%', :keyword, '%'))
                      OR CAST(requested.id AS VARCHAR(50)) LIKE CONCAT('%', :keyword, '%')
                  )
                """);
        }

        sql.append("\nORDER BY po.created_at DESC");

        Query query = entityManager.createNativeQuery(sql.toString());
        if (hasKeyword) {
            query.setParameter("keyword", normalizedKeyword);
        }

        return ((List<Object[]>) query.getResultList()).stream()
                .map(this::toPurchaseOrderResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponseDto getOrderResponse(Long id) {
        return toPurchaseOrderResponse(findPurchaseOrderHeaderRow(id));
    }

    private PurchaseOrderResponseDto toPurchaseOrderResponse(Object[] row) {
        Long purchaseOrderId = toLong(row[0]);
        return PurchaseOrderResponseDto.builder()
                .purchaseOrderId(purchaseOrderId)
                .purchaseOrderNo(toStringValue(row[1]))
                .status(normalizeStatus(toStringValue(row[2])))
                .vendorId(toLong(row[3]))
                .vendorName(toStringValue(row[4]))
                .requestedByMemberId(toLong(row[5]))
                .requestedByMemberName(toStringValue(row[6]))
                .approvedByMemberId(toLong(row[7]))
                .approvedByMemberName(toStringValue(row[8]))
                .orderDate(toLocalDate(row[9]))
                .expectedDate(toLocalDate(row[10]))
                .createdAt(toLocalDateTime(row[11]))
                .totalAmount(toBigDecimal(row[12]))
                .note(toStringValue(row[13]))
                .items(getOrderItemResponses(purchaseOrderId))
                .build();
    }

    private PurchaseOrderResponseDto getReceivedOrderResponse(Long purchaseOrderId) {
        return toPurchaseOrderResponse(findPurchaseOrderHeaderRow(purchaseOrderId));
    }

    private Object[] findPurchaseOrderHeaderRow(Long purchaseOrderId) {
        String sql = """
                SELECT
                    po.id,
                    po.purchase_order_no,
                    po.status,
                    po.vendor_id,
                    v.name AS vendor_name,
                    po.requested_by_member_id,
                    requested.name AS requested_name,
                    po.approved_by_member_id,
                    approved.name AS approved_name,
                    po.order_date,
                    po.expected_date,
                    po.created_at,
                    po.total_amount,
                    po.note,
                    po.warehouse_id
                FROM purchase_orders po
                JOIN vendors v ON v.id = po.vendor_id
                LEFT JOIN members requested ON requested.id = po.requested_by_member_id
                LEFT JOIN members approved ON approved.id = po.approved_by_member_id
                WHERE po.id = :purchaseOrderId
                """;
        List<Object[]> rows = entityManager.createNativeQuery(sql)
                .setParameter("purchaseOrderId", purchaseOrderId)
                .getResultList();
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "발주서를 찾을 수 없습니다.");
        }
        return rows.get(0);
    }

    private Long findWarehouseId(Object[] orderRow) {
        Long warehouseId = orderRow.length > 14 ? toLong(orderRow[14]) : null;
        if (warehouseId != null) return warehouseId;

        List<?> rows = entityManager.createNativeQuery("SELECT id FROM warehouses ORDER BY id LIMIT 1").getResultList();
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "입고할 창고가 없습니다.");
        }
        return toLong(rows.get(0));
    }

    private void updatePurchaseOrderStatusNative(Long purchaseOrderId, String status) {
        entityManager.createNativeQuery("""
                        UPDATE purchase_orders
                        SET status = :status,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = :purchaseOrderId
                        """)
                .setParameter("status", status)
                .setParameter("purchaseOrderId", purchaseOrderId)
                .executeUpdate();
    }

    private List<PurchaseOrderItemResponseDto> getOrderItemResponses(Long purchaseOrderId) {
        String sql = """
                SELECT
                    poi.id,
                    poi.item_id,
                    i.name,
                    poi.quantity,
                    poi.unit,
                    poi.unit_price,
                    poi.supply_amount,
                    poi.tax_amount,
                    poi.line_amount,
                    poi.expected_date,
                    poi.note
                FROM purchase_order_items poi
                JOIN items i ON i.id = poi.item_id
                WHERE poi.purchase_order_id = :purchaseOrderId
                ORDER BY poi.id
                """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("purchaseOrderId", purchaseOrderId);
        return ((List<Object[]>) query.getResultList()).stream()
                .map(row -> PurchaseOrderItemResponseDto.builder()
                        .purchaseOrderItemId(toLong(row[0]))
                        .itemId(toLong(row[1]))
                        .itemName(toStringValue(row[2]))
                        .quantity(toInteger(row[3]))
                        .unit(toStringValue(row[4]))
                        .unitPrice(toBigDecimal(row[5]))
                        .supplyAmount(toBigDecimal(row[6]))
                        .taxAmount(toBigDecimal(row[7]))
                        .lineAmount(toBigDecimal(row[8]))
                        .expectedDate(toLocalDate(row[9]))
                        .note(toStringValue(row[10]))
                        .build())
                .toList();
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return PurchaseStatus.DRAFT.name();
        return switch (status.trim().toUpperCase()) {
            case "PENDING-PO", "PENDING_PO" -> PurchaseStatus.ORDERED.name();
            case "입고중" -> PurchaseStatus.RECEIVING.name();
            case "입고지연" -> PurchaseStatus.INBOUND_DELAY.name();
            case "거래처 발송" -> PurchaseStatus.SENT_TO_VENDOR.name();
            default -> status.trim().toUpperCase();
        };
    }

    @Transactional
    public PurchaseOrderResponseDto updatePurchaseOrder(Long id, PurchaseOrderUpdateDto requestDto) {
        PurchaseOrder order = getOrder(id);
        if (requestDto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "수정할 발주 정보가 필요합니다.");
        }

        if (requestDto.getApprovedByMemberId() != null) {
            order.setApprovedBy(findMember(requestDto.getApprovedByMemberId()));
        }
        if (requestDto.getExpectedDate() != null) {
            order.setExpectedDate(requestDto.getExpectedDate());
        }
        order.setNote(requestDto.getNote());

        if (requestDto.getItems() != null) {
            updatePurchaseOrderItems(order, requestDto.getItems());
        }

        recalculateTotalAmount(order);
        PurchaseOrder savedOrder = purchaseOrderRepository.saveAndFlush(order);
        return PurchaseOrderResponseDto.from(savedOrder, savedOrder.getItems());
    }

    @Transactional
    public PurchaseOrderResponseDto receivePurchaseOrder(Long id) {
        Object[] orderRow = findPurchaseOrderHeaderRow(id);
        String purchaseOrderNo = toStringValue(orderRow[1]);
        String status = normalizeStatus(toStringValue(orderRow[2]));
        if (PurchaseStatus.RECEIVED.name().equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 입고 처리된 발주입니다.");
        }
        if (PurchaseStatus.CANCELLED.name().equals(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "취소된 발주는 입고 처리할 수 없습니다.");
        }
        if (stockMovementRepository.existsByReferenceTypeAndReferenceIdAndMovementType("PURCHASE", purchaseOrderNo, MovementType.INBOUND)) {
            updatePurchaseOrderStatusNative(id, PurchaseStatus.RECEIVING.name());
            return getReceivedOrderResponse(id);
        }

        Long warehouseId = findWarehouseId(orderRow);
        for (PurchaseOrderItemResponseDto item : getOrderItemResponses(id)) {
            inventoryService.increaseStock(
                    item.getItemId(),
                    warehouseId,
                    item.getQuantity(),
                    item.getUnitPrice(),
                    MovementReason.PURCHASE_RECEIVED,
                    "PURCHASE",
                    purchaseOrderNo
            );
        }

        updatePurchaseOrderStatusNative(id, PurchaseStatus.RECEIVING.name());
        return getReceivedOrderResponse(id);
    }

    @Transactional
    public PurchaseOrderResponseDto cancelPurchaseOrder(Long id) {
        Object[] orderRow = findPurchaseOrderHeaderRow(id);
        String purchaseOrderNo = toStringValue(orderRow[1]);
        String status = normalizeStatus(toStringValue(orderRow[2]));

        if (PurchaseStatus.CANCELLED.name().equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 취소된 발주입니다.");
        }
        if (PurchaseStatus.RECEIVING.name().equals(status) || PurchaseStatus.RECEIVED.name().equals(status) || PurchaseStatus.COMPLETED.name().equals(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "입고중, 입고 완료 또는 발주완료된 발주는 취소할 수 없습니다.");
        }
        if (stockMovementRepository.existsByReferenceTypeAndReferenceIdAndMovementType("PURCHASE", purchaseOrderNo, MovementType.INBOUND)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 입고 이력이 있는 발주는 취소할 수 없습니다.");
        }

        updatePurchaseOrderStatusNative(id, PurchaseStatus.CANCELLED.name());
        return getReceivedOrderResponse(id);
    }

    @Transactional
    public PurchaseOrderResponseDto sendPurchaseOrderToVendor(Long id) {
        PurchaseOrder order = getOrder(id);
        order.setStatus(PurchaseStatus.SENT_TO_VENDOR);
        PurchaseOrder savedOrder = purchaseOrderRepository.saveAndFlush(order);
        return PurchaseOrderResponseDto.from(savedOrder, savedOrder.getItems());
    }

    public PurchaseOrder getOrder(Long id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "발주서를 찾을 수 없습니다."));
    }

    @Transactional
    public PurchaseOrder createOrder(PurchaseOrder order) {
        return purchaseOrderRepository.save(order);
    }

    @Transactional
    public PurchaseOrderResponseDto createPurchaseOrder(PurchaseOrderCreateDto requestDto, Long requestedByMemberId) {
        validateCreateRequest(requestDto);

        Vendor vendor = vendorRepository.findById(requestDto.getVendorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "거래처를 찾을 수 없습니다."));
        Member requestedBy = findRequester(requestedByMemberId != null ? requestedByMemberId : requestDto.getRequestedByMemberId());
        Member approvedBy = findMember(requestDto.getApprovedByMemberId());
        
        // 기본 창고 설정 (실제 구현에서는 요청에서 받거나 기본값을 설정해야 함)
        Warehouse warehouse = warehouseRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "등록된 창고가 없습니다."));

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<PurchaseOrderItem> items = new ArrayList<>();

        PurchaseOrder purchaseOrder = PurchaseOrder.builder()
                .purchaseOrderNo(generatePurchaseOrderNo())
                .vendor(vendor)
                .warehouse(warehouse)
                .requestedBy(requestedBy)
                .approvedBy(approvedBy)
                .orderDate(LocalDate.now())
                .expectedDate(requestDto.getExpectedDate())
                .status(PurchaseStatus.ORDERED)
                .totalAmount(BigDecimal.ZERO) // 임시
                .note(requestDto.getNote())
                .build();

        for (PurchaseOrderItemCreateDto itemDto : requestDto.getItems()) {
            Item item = itemRepository.findById(itemDto.getItemId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "품목을 찾을 수 없습니다."));
            
            BigDecimal unitPrice = itemDto.getUnitPrice().setScale(2, RoundingMode.HALF_UP);
            BigDecimal supplyAmount = unitPrice.multiply(BigDecimal.valueOf(itemDto.getQuantity())).setScale(2, RoundingMode.HALF_UP);
            BigDecimal taxAmount = supplyAmount.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineAmount = supplyAmount.add(taxAmount);

            PurchaseOrderItem orderItem = PurchaseOrderItem.builder()
                    .purchaseOrder(purchaseOrder)
                    .item(item)
                    .quantity(itemDto.getQuantity())
                    .unit(item.getUnit())
                    .unitPrice(unitPrice)
                    .supplyAmount(supplyAmount)
                    .taxAmount(taxAmount)
                    .lineAmount(lineAmount)
                    .expectedDate(itemDto.getExpectedDate() != null ? itemDto.getExpectedDate() : requestDto.getExpectedDate())
                    .note(itemDto.getNote())
                    .build();
            
            items.add(orderItem);
            totalAmount = totalAmount.add(lineAmount);
        }

        purchaseOrder.setTotalAmount(totalAmount);
        purchaseOrder.setItems(items);
        
        PurchaseOrder savedOrder = purchaseOrderRepository.saveAndFlush(purchaseOrder);
        return PurchaseOrderResponseDto.from(savedOrder, items);
    }

    @Transactional
    public PurchaseOrderResponseDto createPurchaseOrderFromOcrLink(OcrPurchaseOrderLinkRequestDto requestDto, Long requestedByMemberId) {
        PurchaseOrderCreateDto purchaseOrderCreateDto = new PurchaseOrderCreateDto();
        setField(purchaseOrderCreateDto, "vendorId", requestDto.getVendorId());
        setField(purchaseOrderCreateDto, "approvedByMemberId", requestDto.getApprovedByMemberId());
        setField(purchaseOrderCreateDto, "expectedDate", requestDto.getExpectedDate());
        setField(purchaseOrderCreateDto, "note", requestDto.getNote());

        List<PurchaseOrderItemCreateDto> items = new ArrayList<>();
        if (requestDto.getItems() != null) {
            for (OcrPurchaseOrderLinkRequestDto.Item item : requestDto.getItems()) {
                PurchaseOrderItemCreateDto itemCreateDto = new PurchaseOrderItemCreateDto();
                setField(itemCreateDto, "itemId", item.getItemId());
                setField(itemCreateDto, "quantity", item.getQuantity());
                setField(itemCreateDto, "unitPrice", item.getUnitPrice());
                setField(itemCreateDto, "expectedDate", item.getExpectedDate());
                setField(itemCreateDto, "note", item.getNote());
                items.add(itemCreateDto);
            }
        }
        setField(purchaseOrderCreateDto, "items", items);
        return createPurchaseOrder(purchaseOrderCreateDto, requestedByMemberId);
    }

    @Transactional
    public PurchaseOrderResponseDto updatePurchaseOrderStatus(Long id, PurchaseOrderStatusUpdateDto requestDto, Long memberId) {
        if (requestDto == null || requestDto.getStatus() == null || requestDto.getStatus().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "변경할 발주 상태가 필요합니다.");
        }
        PurchaseStatus nextStatus = PurchaseStatus.valueOf(normalizeStatus(requestDto.getStatus()));
        if (nextStatus == PurchaseStatus.APPROVED) {
            validateApprover(id, memberId);
        }
        PurchaseOrder order = transitionStatus(id, nextStatus);
        
        if (nextStatus == PurchaseStatus.APPROVED) {
            order.setApprovedBy(findMember(memberId));
            purchaseOrderRepository.save(order);
        }

        return PurchaseOrderResponseDto.from(order, order.getItems());
    }

    @Transactional
    public PurchaseOrderResponseDto updatePurchaseOrderStatusForManagement(Long id, PurchaseOrderStatusUpdateDto requestDto) {
        if (requestDto == null || requestDto.getStatus() == null || requestDto.getStatus().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "변경할 발주 상태가 필요합니다.");
        }
        PurchaseStatus nextStatus = PurchaseStatus.valueOf(normalizeStatus(requestDto.getStatus()));
        PurchaseOrder order = getOrder(id);
        String currentStatus = order.getStatus() != null ? order.getStatus().name() : PurchaseStatus.DRAFT.name();
        if (!List.of(PurchaseStatus.ORDERED.name(), "PENDING", "REQUESTED").contains(currentStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "발주 상태는 발주요청 상태에서 한 번만 변경할 수 있습니다.");
        }
        order.setStatus(nextStatus);
        PurchaseOrder savedOrder = purchaseOrderRepository.saveAndFlush(order);
        return PurchaseOrderResponseDto.from(savedOrder, savedOrder.getItems());
    }

    private void validateApprover(Long orderId, Long memberId) {
        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        PurchaseOrder order = getOrder(orderId);
        String currentStatus = order.getStatus() != null ? order.getStatus().name() : PurchaseStatus.DRAFT.name();
        if (!List.of(PurchaseStatus.ORDERED.name(), "PENDING", "REQUESTED").contains(currentStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "발주요청 상태의 발주만 승인할 수 있습니다.");
        }

        Member member = findMember(memberId);
        if ("ADMIN".equalsIgnoreCase(String.valueOf(member.getRole()))) {
            return;
        }

        Long approvedByMemberId = order.getApprovedBy() != null ? order.getApprovedBy().getId() : null;
        if (approvedByMemberId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "승인 담당자가 지정되지 않았습니다.");
        }
        if (!approvedByMemberId.equals(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "지정된 승인 담당자만 승인할 수 있습니다.");
        }
    }

    private void updatePurchaseOrderItems(PurchaseOrder order, List<PurchaseOrderItemUpdateDto> itemDtos) {
        Map<Long, PurchaseOrderItem> itemMap = order.getItems().stream()
                .collect(Collectors.toMap(PurchaseOrderItem::getId, Function.identity()));

        for (PurchaseOrderItemUpdateDto itemDto : itemDtos) {
            if (itemDto.getPurchaseOrderItemId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "수정할 발주 품목 ID가 필요합니다.");
            }
            PurchaseOrderItem item = itemMap.get(itemDto.getPurchaseOrderItemId());
            if (item == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "발주에 포함되지 않은 품목은 수정할 수 없습니다.");
            }
            if (itemDto.getQuantity() == null || itemDto.getQuantity() <= 0
                    || itemDto.getUnitPrice() == null || itemDto.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "발주 품목 수량과 단가를 확인해 주세요.");
            }

            item.setQuantity(itemDto.getQuantity());
            item.setUnitPrice(itemDto.getUnitPrice().setScale(2, RoundingMode.HALF_UP));
            item.setExpectedDate(itemDto.getExpectedDate());
            item.setNote(itemDto.getNote());
        }
    }

    private void recalculateTotalAmount(PurchaseOrder order) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (PurchaseOrderItem item : order.getItems()) {
            BigDecimal unitPrice = item.getUnitPrice().setScale(2, RoundingMode.HALF_UP);
            BigDecimal supplyAmount = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())).setScale(2, RoundingMode.HALF_UP);
            BigDecimal taxAmount = supplyAmount.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineAmount = supplyAmount.add(taxAmount);

            item.setUnitPrice(unitPrice);
            item.setSupplyAmount(supplyAmount);
            item.setTaxAmount(taxAmount);
            item.setLineAmount(lineAmount);
            totalAmount = totalAmount.add(lineAmount);
        }
        order.setTotalAmount(totalAmount);
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrder transitionStatus(Long id, PurchaseStatus nextStatus) {
        PurchaseOrder order = getOrder(id);
        PurchaseStatus currentStatus = order.getStatus();

        if (currentStatus == nextStatus) return order;
        
        if (nextStatus == PurchaseStatus.RECEIVED) {
            if (currentStatus != PurchaseStatus.RECEIVED) {
                boolean alreadyReceived = stockMovementRepository.existsByReferenceTypeAndReferenceIdAndMovementType(
                        "PURCHASE", order.getPurchaseOrderNo(), MovementType.INBOUND
                );
                
                if (!alreadyReceived) {
                    for (PurchaseOrderItem item : order.getItems()) {
                        inventoryService.increaseStock(
                                item.getItem().getId(),
                                order.getWarehouse().getId(),
                                item.getQuantity(),
                                item.getUnitPrice(),
                                MovementReason.PURCHASE_RECEIVED,
                                "PURCHASE",
                                order.getPurchaseOrderNo()
                        );
                    }
                }
                
                autoJournalService.createAndPostJournal(
                        AccountingConstants.SOURCE_PURCHASE,
                        order.getId(),
                        order
                );
            }
        }

        order.setStatus(nextStatus);
        return purchaseOrderRepository.save(order);
    }

    private void validateCreateRequest(PurchaseOrderCreateDto requestDto) {
        if (requestDto == null || requestDto.getVendorId() == null || requestDto.getItems() == null || requestDto.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "발주 정보가 부족합니다.");
        }
        for (PurchaseOrderItemCreateDto itemDto : requestDto.getItems()) {
            if (itemDto.getItemId() == null || itemDto.getQuantity() == null || itemDto.getQuantity() <= 0
                    || itemDto.getUnitPrice() == null || itemDto.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "발주 품목 정보가 부족합니다.");
            }
        }
    }

    private Member findMember(Long memberId) {
        if (memberId == null) return null;
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private Member findRequester(Long memberId) {
        if (memberId != null) {
            return findMember(memberId);
        }
        return memberRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "발주 요청자를 지정할 수 없습니다."));
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        return Long.valueOf(value.toString());
    }

    private Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        return Integer.valueOf(value.toString());
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        return new BigDecimal(value.toString());
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate localDate) return localDate;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        return LocalDate.parse(value.toString().substring(0, 10));
    }

    private java.time.LocalDateTime toLocalDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof java.time.LocalDateTime localDateTime) return localDateTime;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime();
        return java.time.LocalDateTime.parse(value.toString().replace(" ", "T"));
    }

    private String toStringValue(Object value) {
        return value == null ? null : value.toString();
    }

    @Transactional
    public PurchaseRequestResponseDto createPurchaseRequest(PurchaseRequestCreateDto requestDto, Long requestedByMemberId) {
        validatePurchaseRequest(requestDto, requestedByMemberId);

        Item item = itemRepository.findById(requestDto.getItemId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "품목을 찾을 수 없습니다."));
        Vendor vendor = vendorRepository.findById(requestDto.getVendorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "거래처를 찾을 수 없습니다."));
        Member requestedBy = findRequester(requestedByMemberId != null ? requestedByMemberId : requestDto.getRequestedByMemberId());
        
        Warehouse warehouse = warehouseRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "등록된 창고가 없습니다."));

        BigDecimal unitPrice = requestDto.getUnitPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal supplyAmount = unitPrice.multiply(BigDecimal.valueOf(requestDto.getQuantity())).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxAmount = supplyAmount.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = supplyAmount.add(taxAmount);

        PurchaseOrder purchaseOrder = PurchaseOrder.builder()
                .purchaseOrderNo(generatePurchaseRequestNo())
                .vendor(vendor)
                .warehouse(warehouse)
                .requestedBy(requestedBy)
                .orderDate(LocalDate.now())
                .expectedDate(requestDto.getExpectedDate())
                .status(PurchaseStatus.DRAFT) // 요청 상태를 DRAFT로 매핑 (혹은 REQUESTED 추가 가능)
                .totalAmount(totalAmount)
                .note(requestDto.getNote())
                .build();

        PurchaseOrderItem orderItem = PurchaseOrderItem.builder()
                .purchaseOrder(purchaseOrder)
                .item(item)
                .quantity(requestDto.getQuantity())
                .unit(item.getUnit())
                .unitPrice(unitPrice)
                .supplyAmount(supplyAmount)
                .taxAmount(taxAmount)
                .lineAmount(totalAmount)
                .expectedDate(requestDto.getExpectedDate())
                .note(requestDto.getNote())
                .build();

        purchaseOrder.getItems().add(orderItem);
        PurchaseOrder savedOrder = purchaseOrderRepository.saveAndFlush(purchaseOrder);
        
        return PurchaseRequestResponseDto.from(savedOrder, orderItem);
    }

    private void validatePurchaseRequest(PurchaseRequestCreateDto requestDto, Long requestedByMemberId) {
        if (requestDto == null || requestDto.getItemId() == null || requestDto.getVendorId() == null
                || requestDto.getQuantity() == null || requestDto.getQuantity() <= 0
                || requestDto.getUnitPrice() == null || requestDto.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "구매 요청 정보가 부족합니다.");
        }
    }

    private String generatePurchaseOrderNo() {
        return "PO-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + System.currentTimeMillis();
    }

    private String generatePurchaseRequestNo() {
        return "PR-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + System.currentTimeMillis();
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("필드 매핑에 실패했습니다: " + fieldName, exception);
        }
    }
}
