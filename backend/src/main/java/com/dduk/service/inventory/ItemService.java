package com.dduk.service.inventory;

import com.dduk.dto.inventory.ItemCreateDto;
import com.dduk.dto.inventory.ItemResponseDto;
import com.dduk.entity.admin.Member;
import com.dduk.entity.inventory.Item;
import com.dduk.entity.inventory.ItemType;
import com.dduk.entity.inventory.Vendor;
import com.dduk.repository.admin.MemberRepository;
import com.dduk.repository.inventory.ItemRepository;
import com.dduk.repository.inventory.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemService {

    private static final DateTimeFormatter ITEM_CODE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final ItemRepository itemRepository;
    private final VendorRepository vendorRepository;
    private final MemberRepository memberRepository;

    public List<ItemResponseDto> searchItems(String name) {
        String keyword = normalizeName(name);
        if (keyword.isEmpty()) {
            return List.of();
        }

        return itemRepository.findTop10ByNameContainingIgnoreCaseOrderByIdAsc(keyword).stream()
                .map(ItemResponseDto::from)
                .toList();
    }

    @Transactional
    public ItemResponseDto createItem(ItemCreateDto requestDto, Long authenticatedMemberId) {
        if (requestDto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "품목 정보가 필요합니다.");
        }

        String name = normalizeName(requestDto.getName());
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "품목명을 입력해야 합니다.");
        }

        String category = requireText(requestDto.getCategory(), "카테고리를 입력해야 합니다.");
        String spec = requireText(requestDto.getSpec(), "규격을 입력해야 합니다.");
        String unit = requireText(requestDto.getUnit(), "단위를 입력해야 합니다.");
        BigDecimal unitPrice = requireUnitPrice(requestDto.getUnitPrice());

        Vendor defaultVendor = findDefaultVendor(requestDto.getVendorId());
        Member registeredBy = findRegisteredBy(authenticatedMemberId != null ? authenticatedMemberId : requestDto.getRegisteredById());

        List<Item> existingItems = itemRepository.findByNameIgnoreCaseOrderByIdAsc(name);
        if (!existingItems.isEmpty()) {
            return ItemResponseDto.from(existingItems.get(0));
        }

        Item item = Item.builder()
                .itemCode(generateItemCode())
                .barcode(generateBarcode())
                .name(name)
                .itemType(ItemType.FINISHED_GOOD)
                .category(category)
                .spec(spec)
                .unit(unit)
                .defaultVendor(defaultVendor)
                .registeredBy(registeredBy)
                .unitPrice(unitPrice)
                .active(true)
                .build();

        return ItemResponseDto.from(itemRepository.save(item));
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }

    private String requireText(String value, String message) {
        String normalizedValue = value == null ? "" : value.trim();
        if (normalizedValue.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return normalizedValue;
    }

    private BigDecimal requireUnitPrice(BigDecimal unitPrice) {
        if (unitPrice == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "단가를 입력해야 합니다.");
        }
        if (unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "단가는 0 이상이어야 합니다.");
        }
        return unitPrice;
    }

    private Vendor findDefaultVendor(Long vendorId) {
        if (vendorId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "거래처를 선택한 뒤 품목을 등록해야 합니다.");
        }
        return vendorRepository.findById(vendorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "거래처를 찾을 수 없습니다."));
    }

    private Member findRegisteredBy(Long memberId) {
        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "등록자 ID가 필요합니다.");
        }
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "등록자를 찾을 수 없습니다."));
    }

    private String generateItemCode() {
        String itemCode;
        do {
            itemCode = "ITEM-" + LocalDateTime.now().format(ITEM_CODE_FORMAT);
        } while (itemRepository.findByItemCode(itemCode).isPresent());
        return itemCode;
    }

    private String generateBarcode() {
        String barcode;
        do {
            barcode = "BC-" + LocalDateTime.now().format(ITEM_CODE_FORMAT);
        } while (itemRepository.findByBarcode(barcode).isPresent());
        return barcode;
    }
}
