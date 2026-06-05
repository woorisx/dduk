package com.dduk.service.accounting.voucher;

import com.dduk.dto.accounting.AccountResponse;
import com.dduk.dto.accounting.voucher.VoucherLineRequest;
import com.dduk.dto.accounting.voucher.VoucherLineResponse;
import com.dduk.dto.accounting.voucher.VoucherRequest;
import com.dduk.dto.accounting.voucher.VoucherResponse;
import com.dduk.dto.accounting.voucher.VoucherSummaryResponse;
import com.dduk.dto.accounting.voucher.VoucherDetailResponse;
import com.dduk.service.accounting.period.MonthlyClosingService;
import com.dduk.entity.accounting.Account;
import com.dduk.entity.accounting.AccountSide;
import com.dduk.entity.accounting.AccountType;
import com.dduk.entity.accounting.JournalEntry;
import com.dduk.entity.accounting.JournalLine;
import com.dduk.entity.accounting.voucher.Voucher;
import com.dduk.entity.accounting.voucher.VoucherLine;
import com.dduk.entity.accounting.voucher.enums.VatType;
import com.dduk.entity.accounting.voucher.enums.VoucherStatus;
import com.dduk.entity.accounting.voucher.enums.VoucherType;
import com.dduk.repository.accounting.AccountRepository;
import com.dduk.repository.accounting.JournalEntryRepository;
import com.dduk.repository.accounting.voucher.VoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VoucherService {

    private static final String SOURCE_TYPE = "VOUCHER";
    private static final BigDecimal VAT_RATE = new BigDecimal("0.10");

    private final VoucherRepository voucherRepository;
    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final MonthlyClosingService monthlyClosingService;

    @Transactional
    public VoucherResponse createVoucher(VoucherRequest request) {
        validateHeader(request);
        monthlyClosingService.assertPeriodMutable(request.getVoucherDate());

        List<VoucherLineRequest> journalLines = request.getLines();
        if (journalLines == null || journalLines.isEmpty()) {
            journalLines = buildAutoJournalLines(request);
        }

        validateBalanced(journalLines);

        String voucherNo = createVoucherNo(request.getVoucherDate());
        Voucher voucher = Voucher.builder()
                .voucherNo(voucherNo)
                .voucherDate(request.getVoucherDate())
                .voucherType(request.getVoucherType())
                .vatType(request.getVatType())
                .vendorId(request.getVendorId())
                .vendorNameSnapshot(request.getVendorNameSnapshot())
                .description(request.getDescription())
                .status(VoucherStatus.DRAFT)
                .createdBy("system")
                .build();

        AtomicInteger lineNo = new AtomicInteger(1);
        for (VoucherLineRequest lineRequest : journalLines) {
            Account account = findPostingAccount(lineRequest.getAccountId());
            BigDecimal amount = positive(lineRequest.getTotalAmount(), "line totalAmount");

            VoucherLine line = VoucherLine.builder()
                    .lineNo(lineNo.get())
                    .accountId(account.getId())
                    .accountCode(account.getCode())
                    .accountName(account.getName())
                    .debitCredit(lineRequest.getDebitCredit())
                    .supplyAmount(nullToZero(lineRequest.getSupplyAmount()))
                    .vatAmount(nullToZero(lineRequest.getVatAmount()))
                    .totalAmount(amount)
                    .quantity(lineRequest.getQuantity())
                    .unitPrice(lineRequest.getUnitPrice())
                    .description(lineRequest.getDescription())
                    .sortOrder(lineRequest.getSortOrder() != null ? lineRequest.getSortOrder() : lineNo.get())
                    .build();
            voucher.addLine(line);
            lineNo.incrementAndGet();
        }

        Voucher savedVoucher = voucherRepository.saveAndFlush(voucher);
        JournalEntry journalEntry = createJournalEntry(savedVoucher);
        savedVoucher.setJournalEntry(journalEntry);

        return mapToResponse(savedVoucher);
    }

    @Transactional(readOnly = true)
    public VoucherDetailResponse getVoucherDetail(Long id) {
        Voucher voucher = voucherRepository.findByIdWithLines(id)
                .orElseThrow(() -> new IllegalArgumentException("Voucher not found: " + id));

        List<VoucherLineResponse> lines = voucher.getLines().stream()
                .map(this::mapLine)
                .collect(Collectors.toList());

        BigDecimal debitTotal = lines.stream()
                .filter(l -> l.getDebitCredit() == com.dduk.entity.accounting.AccountSide.DEBIT)
                .map(VoucherLineResponse::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal creditTotal = lines.stream()
                .filter(l -> l.getDebitCredit() == com.dduk.entity.accounting.AccountSide.CREDIT)
                .map(VoucherLineResponse::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return VoucherDetailResponse.builder()
                .id(voucher.getId())
                .voucherNo(voucher.getVoucherNo())
                .voucherDate(voucher.getVoucherDate())
                .voucherType(voucher.getVoucherType())
                .vatType(voucher.getVatType())
                .vendorId(voucher.getVendorId())
                .vendorNameSnapshot(voucher.getVendorNameSnapshot())
                .status(voucher.getStatus())
                .description(voucher.getDescription())
                .journalEntryId(voucher.getJournalEntry() != null ? voucher.getJournalEntry().getId() : null)
                .createdBy(voucher.getCreatedBy())
                .createdAt(voucher.getCreatedAt())
                .lines(lines)
                .debitTotal(debitTotal)
                .creditTotal(creditTotal)
                .build();
    }

    @Transactional(readOnly = true)
    public List<VoucherResponse> getVouchers(
            VoucherType voucherType,
            VoucherStatus status,
            LocalDate startDate,
            LocalDate endDate,
            String keyword
    ) {
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        List<Voucher> vouchers = voucherRepository.findWithFilters(voucherType, status, startDate, endDate, kw);
        return vouchers.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public VoucherSummaryResponse getSummary() {
        List<Voucher> vouchers = voucherRepository.findAllWithLinesForList();
        BigDecimal supply = vouchers.stream()
                .flatMap(v -> v.getLines().stream())
                .map(VoucherLine::getSupplyAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal vat = vouchers.stream()
                .flatMap(v -> v.getLines().stream())
                .map(VoucherLine::getVatAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = vouchers.stream()
                .flatMap(v -> v.getLines().stream())
                .filter(line -> line.getDebitCredit() == AccountSide.DEBIT)
                .map(VoucherLine::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalVoucherCount = vouchers.size();

        return new VoucherSummaryResponse(
                voucherRepository.countByVoucherDate(LocalDate.now()),
                voucherRepository.countByStatus(VoucherStatus.DRAFT),
                voucherRepository.countByStatus(VoucherStatus.REQUESTED),
                voucherRepository.countByStatus(VoucherStatus.APPROVED),
                voucherRepository.countByStatus(VoucherStatus.POSTED),
                totalVoucherCount,
                totalVoucherCount,
                supply,
                vat,
                total
        );
    }

    @Transactional
    public VoucherResponse updateStatus(Long id, VoucherStatus nextStatus) {
        Voucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Voucher not found: " + id));
        monthlyClosingService.assertPeriodMutable(voucher.getVoucherDate());
        VoucherStatus current = voucher.getStatus();

        if (nextStatus == VoucherStatus.REQUESTED && current != VoucherStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT vouchers can be requested.");
        }
        if (nextStatus == VoucherStatus.APPROVED && current != VoucherStatus.REQUESTED) {
            throw new IllegalStateException("Only REQUESTED vouchers can be approved.");
        }
        if (nextStatus == VoucherStatus.POSTED && current != VoucherStatus.APPROVED) {
            throw new IllegalStateException("Only APPROVED vouchers can be posted.");
        }
        if (nextStatus == VoucherStatus.CANCELLED && current == VoucherStatus.POSTED) {
            throw new IllegalStateException("Posted vouchers must be reversed instead of cancelled.");
        }

        voucher.updateStatus(nextStatus);
        if (nextStatus == VoucherStatus.POSTED && voucher.getJournalEntry() != null) {
            voucher.getJournalEntry().post();
            journalEntryRepository.save(voucher.getJournalEntry());
        }
        voucherRepository.save(voucher);
        return mapToResponse(voucher);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> searchAccounts(String keyword, AccountType type, boolean cashOnly) {
        String normalizedKeyword = blankToNull(keyword);
        boolean initialSearch = isInitialKeyword(normalizedKeyword);
        List<Account> accounts = cashOnly
                ? accountRepository.searchCashAccounts(initialSearch ? null : normalizedKeyword, AccountType.ASSET)
                : accountRepository.searchPostingAccounts(initialSearch ? null : normalizedKeyword, type);
        if (initialSearch) {
            accounts = accounts.stream()
                    .filter(account -> matchesInitials(account.getName(), normalizedKeyword))
                    .collect(Collectors.toList());
        }
        return accounts.stream().map(AccountResponse::from).collect(Collectors.toList());
    }

    private JournalEntry createJournalEntry(Voucher voucher) {
        JournalEntry journalEntry = JournalEntry.builder()
                .journalNo("J" + voucher.getVoucherNo().substring(1))
                .transactionDate(voucher.getVoucherDate())
                .description(voucher.getDescription() != null ? voucher.getDescription() : voucher.getVoucherNo())
                .status("DRAFT")
                .sourceType(SOURCE_TYPE)
                .sourceId(voucher.getId())
                .createdBy(voucher.getCreatedBy())
                .build();

        voucher.getLines().stream()
                .sorted(Comparator.comparing(VoucherLine::getSortOrder))
                .forEach(line -> {
                    Account account = findPostingAccount(line.getAccountId());
                    JournalLine journalLine = JournalLine.builder()
                            .account(account)
                            .debitAmount(line.getDebitCredit() == AccountSide.DEBIT ? line.getTotalAmount() : BigDecimal.ZERO)
                            .creditAmount(line.getDebitCredit() == AccountSide.CREDIT ? line.getTotalAmount() : BigDecimal.ZERO)
                            .description(line.getDescription())
                            .referenceType("VOUCHER_LINE")
                            .referenceId(line.getId())
                            .build();
                    journalEntry.addLine(journalLine);
                });

        validateJournalEntry(journalEntry);
        return journalEntryRepository.save(journalEntry);
    }

    private List<VoucherLineRequest> buildAutoJournalLines(VoucherRequest request) {
        Account businessAccount = findPostingAccount(request.getBusinessAccountId());
        Account settlementAccount = findPostingAccount(request.getSettlementAccountId());
        BigDecimal supplyAmount = positive(request.getSupplyAmount(), "supplyAmount");
        BigDecimal vatAmount = request.getVatAmount() != null ? nonNegative(request.getVatAmount(), "vatAmount") : calculateVat(request);
        BigDecimal feeAmount = nullToZero(request.getFeeAmount());
        if (feeAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("feeAmount cannot be negative.");
        }

        validateBusinessAccount(request.getVoucherType(), businessAccount);
        validateSettlementAccount(settlementAccount);

        List<VoucherLineRequest> lines = new ArrayList<>();
        BigDecimal totalAmount = supplyAmount.add(vatAmount);

        if (request.getVoucherType() == VoucherType.SALES) {
            lines.add(line(settlementAccount, AccountSide.DEBIT, BigDecimal.ZERO, BigDecimal.ZERO, totalAmount.subtract(feeAmount), "입금/매출채권"));
            if (feeAmount.compareTo(BigDecimal.ZERO) > 0) {
                lines.add(line(findSystemAccount("5370", AccountType.EXPENSE, "Commission Expenses"), AccountSide.DEBIT, BigDecimal.ZERO, BigDecimal.ZERO, feeAmount, "카드/PG 수수료"));
            }
            lines.add(line(businessAccount, AccountSide.CREDIT, supplyAmount, BigDecimal.ZERO, supplyAmount, "매출계정"));
            addVatLine(lines, List.of("2128", "2141", "2004"), AccountType.LIABILITY, AccountSide.CREDIT, vatAmount, "부가세예수금");
        } else if (request.getVoucherType() == VoucherType.PURCHASE) {
            lines.add(line(businessAccount, AccountSide.DEBIT, supplyAmount, BigDecimal.ZERO, supplyAmount, "매입/비용계정"));
            addVatLine(lines, List.of("1146"), AccountType.ASSET, AccountSide.DEBIT, vatAmount, "부가세대급금");
            lines.add(line(settlementAccount, AccountSide.CREDIT, BigDecimal.ZERO, BigDecimal.ZERO, totalAmount, "출금/매입채무"));
        } else {
            throw new IllegalArgumentException("Auto journaling currently supports SALES and PURCHASE vouchers.");
        }

        AtomicInteger sort = new AtomicInteger(1);
        lines.forEach(line -> line.setSortOrder(sort.getAndIncrement()));
        return lines;
    }

    private void addVatLine(List<VoucherLineRequest> lines, List<String> codes, AccountType type, AccountSide side, BigDecimal vatAmount, String description) {
        if (vatAmount.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(line(findFirstSystemAccount(codes, type, description), side, BigDecimal.ZERO, vatAmount, vatAmount, description));
        }
    }

    private VoucherLineRequest line(Account account, AccountSide side, BigDecimal supply, BigDecimal vat, BigDecimal total, String description) {
        VoucherLineRequest request = new VoucherLineRequest();
        request.setAccountId(account.getId());
        request.setAccountCode(account.getCode());
        request.setAccountName(account.getName());
        request.setDebitCredit(side);
        request.setSupplyAmount(supply);
        request.setVatAmount(vat);
        request.setTotalAmount(total);
        request.setDescription(description);
        return request;
    }

    private void validateHeader(VoucherRequest request) {
        if (request.getVoucherDate() == null) {
            request.setVoucherDate(LocalDate.now());
        }
        if (request.getVoucherType() == null) {
            throw new IllegalArgumentException("voucherType is required.");
        }
        if (request.getVatType() == null) {
            request.setVatType(VatType.TAX_INVOICE);
        }
        if (!StringUtils.hasText(request.getVendorNameSnapshot())) {
            throw new IllegalArgumentException("vendorNameSnapshot is required.");
        }
    }

    private void validateBusinessAccount(VoucherType voucherType, Account account) {
        if (voucherType == VoucherType.SALES && account.getType() != AccountType.REVENUE) {
            throw new IllegalArgumentException("Sales vouchers require a REVENUE account.");
        }
        if (voucherType == VoucherType.PURCHASE && account.getType() != AccountType.ASSET && account.getType() != AccountType.EXPENSE) {
            throw new IllegalArgumentException("Purchase vouchers require an ASSET or EXPENSE account.");
        }
    }

    private void validateSettlementAccount(Account account) {
        if (account.getType() != AccountType.ASSET) {
            throw new IllegalArgumentException("Settlement account must be an ASSET account.");
        }
    }

    private Account findPostingAccount(Long accountId) {
        if (accountId == null) {
            throw new IllegalArgumentException("accountId is required.");
        }
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        if (!Boolean.TRUE.equals(account.getAllowPosting()) || !account.isLeaf()) {
            throw new IllegalArgumentException("Only leaf posting accounts can be selected: " + account.getCode());
        }
        return account;
    }

    private Account findSystemAccount(String code, AccountType expectedType, String label) {
        Account account = accountRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Required account is missing for " + label + ": " + code));
        if (account.getType() != expectedType || !Boolean.TRUE.equals(account.getAllowPosting())) {
            throw new IllegalStateException("Required account is not a posting " + expectedType + " account: " + code);
        }
        return account;
    }

    private Account findFirstSystemAccount(List<String> codes, AccountType expectedType, String label) {
        for (String code : codes) {
            try {
                return findSystemAccount(code, expectedType, label);
            } catch (IllegalStateException ignored) {
                // Try the next locally known CoA code. Seed versions may differ.
            }
        }
        throw new IllegalStateException("Required account is missing for " + label + ": " + String.join(", ", codes));
    }

    private void validateBalanced(List<VoucherLineRequest> lines) {
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (VoucherLineRequest line : lines) {
            if (line.getDebitCredit() == null) {
                throw new IllegalArgumentException("debitCredit is required.");
            }
            BigDecimal amount = positive(line.getTotalAmount(), "line totalAmount");
            if (line.getDebitCredit() == AccountSide.DEBIT) {
                debit = debit.add(amount);
            } else {
                credit = credit.add(amount);
            }
        }
        if (debit.compareTo(credit) != 0) {
            throw new IllegalArgumentException("Debit total and credit total must match. debit=" + debit + ", credit=" + credit);
        }
    }

    private void validateJournalEntry(JournalEntry journalEntry) {
        if (journalEntry.getTotalDebit().compareTo(journalEntry.getTotalCredit()) != 0) {
            throw new IllegalStateException("Journal entry is not balanced.");
        }
    }

    private BigDecimal calculateVat(VoucherRequest request) {
        if (request.getVatType() == VatType.ZERO_TAX || request.getVatType() == VatType.TAX_FREE || request.getVatType() == VatType.EXPORT || request.getVatType() == VatType.INVOICE) {
            return BigDecimal.ZERO;
        }
        return request.getSupplyAmount().multiply(VAT_RATE);
    }

    private BigDecimal positive(BigDecimal value, String fieldName) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero.");
        }
        return value;
    }

    private BigDecimal nonNegative(BigDecimal value, String fieldName) {
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative.");
        }
        return value;
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String createVoucherNo(LocalDate date) {
        return "V" + date.format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private boolean isInitialKeyword(String keyword) {
        return keyword != null && keyword.matches("[ㄱ-ㅎ]+");
    }

    private boolean matchesInitials(String value, String initials) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(initials)) {
            return false;
        }
        return getKoreanInitials(value).contains(initials);
    }

    private String getKoreanInitials(String value) {
        final char[] initials = {'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'};
        StringBuilder builder = new StringBuilder();
        for (char ch : value.toLowerCase(Locale.KOREAN).toCharArray()) {
            if (ch >= '가' && ch <= '힣') {
                int index = (ch - '가') / (21 * 28);
                builder.append(initials[index]);
            } else if (Character.isLetterOrDigit(ch)) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private VoucherResponse mapToResponse(Voucher voucher) {
        VoucherResponse response = new VoucherResponse();
        response.setId(voucher.getId());
        response.setVoucherNo(voucher.getVoucherNo());
        response.setVoucherDate(voucher.getVoucherDate());
        response.setVoucherType(voucher.getVoucherType());
        response.setVatType(voucher.getVatType());
        response.setVendorId(voucher.getVendorId());
        response.setVendorNameSnapshot(voucher.getVendorNameSnapshot());
        response.setStatus(voucher.getStatus());
        response.setDescription(voucher.getDescription());
        response.setJournalEntryId(voucher.getJournalEntry() != null ? voucher.getJournalEntry().getId() : null);
        response.setCreatedBy(voucher.getCreatedBy());
        response.setCreatedAt(voucher.getCreatedAt());
        response.setLines(voucher.getLines().stream().map(this::mapLine).collect(Collectors.toList()));
        return response;
    }

    private VoucherLineResponse mapLine(VoucherLine line) {
        VoucherLineResponse response = new VoucherLineResponse();
        response.setId(line.getId());
        response.setLineNo(line.getLineNo());
        response.setAccountId(line.getAccountId());
        response.setAccountCode(line.getAccountCode());
        response.setAccountName(line.getAccountName());
        response.setDebitCredit(line.getDebitCredit());
        response.setSupplyAmount(line.getSupplyAmount());
        response.setVatAmount(line.getVatAmount());
        response.setTotalAmount(line.getTotalAmount());
        response.setQuantity(line.getQuantity());
        response.setUnitPrice(line.getUnitPrice());
        response.setDescription(line.getDescription());
        response.setSortOrder(line.getSortOrder());
        return response;
    }
}
