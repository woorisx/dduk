package com.dduk.service.accounting.inventory;

import com.dduk.entity.accounting.Account;
import com.dduk.entity.accounting.AccountSide;
import com.dduk.entity.accounting.JournalEntry;
import com.dduk.entity.accounting.JournalLine;
import com.dduk.entity.accounting.voucher.Voucher;
import com.dduk.entity.accounting.voucher.VoucherLine;
import com.dduk.entity.accounting.voucher.enums.VoucherSourceType;
import com.dduk.entity.accounting.voucher.enums.VoucherStatus;
import com.dduk.entity.accounting.voucher.enums.VoucherType;
import com.dduk.entity.inventory.MovementType;
import com.dduk.entity.inventory.StockMovement;
import com.dduk.repository.accounting.AccountRepository;
import com.dduk.repository.accounting.JournalEntryRepository;
import com.dduk.repository.accounting.voucher.VoucherRepository;
import com.dduk.service.accounting.AccountingConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 재고 이동/조정 시 회계 전표 자동 생성 서비스.
 *
 * <p><b>책임 범위</b>: 회계 도메인의 자동분개 하위 서비스로,
 * StockMovement 엔티티를 입력받아 DRAFT 상태의 Voucher + VoucherLine을
 * 동일 트랜잭션 내에서 생성합니다.
 *
 * <p><b>트랜잭션 정책</b>: 호출자(WarehouseTransferService 등)의 트랜잭션에 참여합니다.
 * 전표 생성 실패 시 재고 이동 트랜잭션 전체가 롤백됩니다.
 *
 * <p><b>창고 이동(TRANSFER_IN/OUT) 정책</b>: 창고 간 이동은 회사 전체 재고자산 총액에
 * 변화가 없으므로 전표를 생성하지 않습니다. 이 메서드는 TRANSFER 유형을 무시합니다.
 *
 * <p><b>TODO</b>: inventory_account_mappings 테이블 기반 품목 카테고리별 계정 분리 예정.
 * 현재는 AccountingConstants 상수(1003 재고자산, 5001 매출원가)를 전체 품목에 동일 적용합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryVoucherService {

    private final VoucherRepository voucherRepository;
    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;

    /**
     * StockMovement 1건에 대한 DRAFT 전표를 생성합니다.
     *
     * <p>TRANSFER_IN / TRANSFER_OUT 유형은 내부 자산 이동으로 전표를 생성하지 않고 null을 반환합니다.
     *
     * <p>호출 전 반드시 회계기간 마감 검증을 완료해야 합니다 (completeTransfer 초입에서 처리).
     *
     * @param movement 원장에 기록된 재고 이동 엔티티
     * @return 생성된 DRAFT Voucher, TRANSFER 유형이면 null
     */
    @Transactional
    public Voucher createDraftVoucher(StockMovement movement) {
        MovementType type = movement.getMovementType();

        // 창고 간 이동은 재고자산 내부 이동 → 전표 생성 없음
        if (type == MovementType.TRANSFER_IN || type == MovementType.TRANSFER_OUT) {
            log.debug("[InventoryVoucherService] TRANSFER 유형은 전표 생성 대상 아님. movement.id={}", movement.getId());
            return null;
        }

        VoucherSourceType sourceType = resolveSourceType(type);
        LocalDate voucherDate = movement.getCreatedAt().toLocalDate();

        // 분개 계정 조회
        Account inventoryAccount = findAccountByCode(AccountingConstants.INVENTORY_ASSET);
        Account counterAccount = resolveCounterAccount(type);

        // 전표 헤더 생성
        String voucherNo = generateVoucherNo(voucherDate);
        String description = buildDescription(movement);

        Voucher voucher = Voucher.builder()
                .voucherNo(voucherNo)
                .voucherDate(voucherDate)
                .voucherType(VoucherType.INVENTORY)
                .sourceType(sourceType)
                .sourceReferenceId(movement.getId())
                .status(VoucherStatus.DRAFT)
                .description(description)
                .createdBy("SYSTEM")
                .build();

        // 전표 라인 생성 (MovementType에 따른 차대 방향 결정)
        AtomicInteger lineNo = new AtomicInteger(1);

        if (type == MovementType.INBOUND || type == MovementType.RETURN_IN || type == MovementType.ADJUSTMENT_IN) {
            // 입고/반품입고/조정증가: [차] 재고자산 / [대] 상대계정
            voucher.addLine(buildLine(voucher, lineNo.getAndIncrement(), inventoryAccount, AccountSide.DEBIT, movement, true));
            voucher.addLine(buildLine(voucher, lineNo.getAndIncrement(), counterAccount, AccountSide.CREDIT, movement, false));
        } else {
            // 출고/반품출고/조정감소: [차] 상대계정 / [대] 재고자산
            voucher.addLine(buildLine(voucher, lineNo.getAndIncrement(), counterAccount, AccountSide.DEBIT, movement, false));
            voucher.addLine(buildLine(voucher, lineNo.getAndIncrement(), inventoryAccount, AccountSide.CREDIT, movement, true));
        }

        Voucher savedVoucher = voucherRepository.saveAndFlush(voucher);

        // JournalEntry 자동 생성 (DRAFT 상태)
        JournalEntry journalEntry = createJournalEntry(savedVoucher);
        savedVoucher.setJournalEntry(journalEntry);

        log.info("[InventoryVoucherService] DRAFT 전표 생성 완료. voucherNo={}, movementId={}, sourceType={}",
                voucherNo, movement.getId(), sourceType);

        return savedVoucher;
    }

    // ─── Private 헬퍼 ──────────────────────────────────────────────────

    private VoucherSourceType resolveSourceType(MovementType type) {
        return switch (type) {
            case INBOUND, RETURN_IN -> VoucherSourceType.STOCK_INBOUND;
            case OUTBOUND, RETURN_OUT -> VoucherSourceType.STOCK_OUTBOUND;
            case ADJUSTMENT_IN -> VoucherSourceType.STOCK_ADJUSTMENT_IN;
            case ADJUSTMENT_OUT -> VoucherSourceType.STOCK_ADJUSTMENT_OUT;
            default -> VoucherSourceType.STOCK_INBOUND;
        };
    }

    /**
     * MovementType에 따라 재고자산의 상대 계정을 결정합니다.
     *
     * <p>TODO: inventory_account_mappings 도입 시 품목 카테고리별로 분기 예정.
     */
    private Account resolveCounterAccount(MovementType type) {
        String code = switch (type) {
            case INBOUND, RETURN_IN -> AccountingConstants.ACCOUNTS_PAYABLE;   // 외상매입금
            case OUTBOUND, RETURN_OUT -> AccountingConstants.COST_OF_SALES;    // 매출원가
            case ADJUSTMENT_IN -> AccountingConstants.ACCOUNTS_PAYABLE;        // 임시: 재고증가 상대계정
            case ADJUSTMENT_OUT -> AccountingConstants.INVENTORY_LOSS;         // 재고손실
            default -> AccountingConstants.ACCOUNTS_PAYABLE;
        };
        return findAccountByCode(code);
    }

    private VoucherLine buildLine(Voucher voucher, int lineNo, Account account, AccountSide side,
                                  StockMovement movement, boolean isInventoryLine) {
        return VoucherLine.builder()
                .lineNo(lineNo)
                .accountId(account.getId())
                .accountCode(account.getCode())
                .accountName(account.getName())
                .debitCredit(side)
                .supplyAmount(movement.getTotalAmount())
                .vatAmount(BigDecimal.ZERO)
                .totalAmount(movement.getTotalAmount())
                .quantity(movement.getQuantity())
                .unitPrice(movement.getUnitCost())
                .description(buildLineDescription(movement, side, isInventoryLine))
                .sortOrder(lineNo)
                // 재고자산 라인에만 movement 추적 정보 세팅
                .stockMovementId(isInventoryLine ? movement.getId() : null)
                .movementType(isInventoryLine ? movement.getMovementType().name() : null)
                .movementReferenceNo(isInventoryLine ? movement.getReferenceNo() : null)
                .build();
    }

    private String buildDescription(StockMovement movement) {
        String itemName = movement.getItem() != null ? movement.getItem().getName() : "품목";
        String warehouseName = movement.getWarehouse() != null ? movement.getWarehouse().getWarehouseName() : "창고";
        return String.format("[자동] %s %s %d%s (%s)",
                movement.getMovementType().name(),
                itemName,
                movement.getQuantity(),
                movement.getItem() != null ? movement.getItem().getUnit() : "",
                warehouseName);
    }

    private String buildLineDescription(StockMovement movement, AccountSide side, boolean isInventoryLine) {
        String movementLabel = switch (movement.getMovementType()) {
            case INBOUND, RETURN_IN -> "입고";
            case OUTBOUND, RETURN_OUT -> "출고";
            case ADJUSTMENT_IN -> "재고 조정 증가";
            case ADJUSTMENT_OUT -> "재고 조정 감소";
            default -> movement.getMovementType().name();
        };
        String itemName = movement.getItem() != null ? movement.getItem().getName() : "";
        return String.format("%s %s [%s]", movementLabel, itemName, movement.getReferenceNo());
    }

    private JournalEntry createJournalEntry(Voucher voucher) {
        JournalEntry journalEntry = JournalEntry.builder()
                .journalNo("J" + voucher.getVoucherNo().substring(1))
                .transactionDate(voucher.getVoucherDate())
                .description(voucher.getDescription())
                .status(AccountingConstants.JOURNAL_STATUS_DRAFT)
                .sourceType("VOUCHER")
                .sourceId(voucher.getId())
                .createdBy(voucher.getCreatedBy())
                .build();

        voucher.getLines().stream()
                .sorted(Comparator.comparing(VoucherLine::getSortOrder))
                .forEach(line -> {
                    Account account = findAccountByCode(line.getAccountCode());
                    JournalLine jLine = JournalLine.builder()
                            .account(account)
                            .debitAmount(line.getDebitCredit() == AccountSide.DEBIT ? line.getTotalAmount() : BigDecimal.ZERO)
                            .creditAmount(line.getDebitCredit() == AccountSide.CREDIT ? line.getTotalAmount() : BigDecimal.ZERO)
                            .description(line.getDescription())
                            .referenceType("VOUCHER_LINE")
                            .referenceId(line.getId())
                            .build();
                    journalEntry.addLine(jLine);
                });

        return journalEntryRepository.save(journalEntry);
    }

    private Account findAccountByCode(String code) {
        return accountRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException(
                        "[InventoryVoucherService] 계정과목을 찾을 수 없습니다: " + code
                        + " — dduk_bootstrap_schema.sql accounts seed를 확인하십시오."));
    }

    private String generateVoucherNo(LocalDate date) {
        return "V" + date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
