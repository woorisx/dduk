package com.dduk.service.accounting;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dduk.dto.accounting.AccountCreateRequest;
import com.dduk.dto.accounting.AccountResponse;
import com.dduk.dto.accounting.AccountUpdateRequest;
import com.dduk.entity.accounting.Account;
import com.dduk.entity.accounting.AccountSide;
import com.dduk.entity.accounting.AccountStatus;
import com.dduk.entity.accounting.AccountType;
import com.dduk.repository.accounting.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountManagementService {

    private final AccountRepository accountRepository;
    private final ObjectMapper objectMapper;

    /**
     * O(N) 성능 최적화 계정 트리 조회
     */
    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountTree() {
        List<Account> accounts = accountRepository.findAllWithChildrenAndDeletedFalse();

        // 1차 DTO 생성 및 Map 매핑
        Map<Long, AccountResponse> dtoMap = accounts.stream()
                .collect(Collectors.toMap(Account::getId, AccountResponse::from));

        List<AccountResponse> roots = new ArrayList<>();

        // 2차 O(N) 구조 조립
        for (Account account : accounts) {
            AccountResponse dto = dtoMap.get(account.getId());
            if (account.getParentAccount() == null) {
                roots.add(dto);
            } else {
                AccountResponse parentDto = dtoMap.get(account.getParentAccount().getId());
                if (parentDto != null) {
                    parentDto.getChildren().add(dto);
                    parentDto.setLeaf(false); // 자식이 있으므로 Leaf 아님
                } else {
                    roots.add(dto);
                }
            }
        }

        // 정렬 수행
        sortTree(roots);

        return roots;
    }

    private void sortTree(List<AccountResponse> nodes) {
        if (nodes == null) return;
        nodes.sort(Comparator.comparing(AccountResponse::getSortOrder)
                .thenComparing(AccountResponse::getCode));
        for (AccountResponse node : nodes) {
            sortTree(node.getChildren());
        }
    }

    /**
     * 플랫 리스트 조회
     */
    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountList() {
        return accountRepository.findAllWithChildrenAndDeletedFalse().stream()
                .map(AccountResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> searchAccounts(String keyword, AccountType type, boolean cashOnly) {
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        List<Account> accounts = cashOnly
                ? accountRepository.searchCashAccounts(normalizedKeyword, AccountType.ASSET)
                : accountRepository.searchPostingAccounts(normalizedKeyword, type);
        return accounts.stream()
                .map(AccountResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 계정과목 생성
     */
    @Transactional
    public AccountResponse createAccount(AccountCreateRequest request) {
        if (accountRepository.existsByCodeAndDeletedFalse(request.getCode())) {
            throw new IllegalArgumentException("이미 사용 중인 계정 코드입니다: " + request.getCode());
        }

        Account parent = null;
        int level = 1;
        if (request.getParentId() != null) {
            parent = accountRepository.findById(request.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("지정한 부모 계정을 찾을 수 없습니다. ID: " + request.getParentId()));
            level = parent.getLevel() + 1;
        }

        Account account = Account.builder()
                .code(request.getCode())
                .name(request.getName())
                .englishName(request.getEnglishName())
                .type(request.getType())
                .normalBalance(request.getNormalBalance())
                .level(level)
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .description(request.getDescription())
                .status(request.getStatus() != null ? request.getStatus() : AccountStatus.ACTIVE)
                .allowPosting(request.getAllowPosting() != null ? request.getAllowPosting() : true)
                .systemAccount(false) // 사용자가 수동 생성한 계정은 systemAccount 아님
                .parentAccount(parent)
                .parentCode(parent != null ? parent.getCode() : null)
                .build();

        // validateTypeAndSide()는 @PrePersist에서 호출됨
        Account saved = accountRepository.save(account);
        return AccountResponse.from(saved);
    }

    /**
     * 계정과목 수정
     */
    @Transactional
    public AccountResponse updateAccount(Long id, AccountUpdateRequest request) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("계정을 찾을 수 없습니다. ID: " + id));

        if (Boolean.TRUE.equals(account.getSystemAccount())) {
            // 시스템 보호 계정은 최소한의 필드만 수정 가능
            account.setName(request.getName());
            account.setEnglishName(request.getEnglishName());
            account.setDescription(request.getDescription());
            account.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : account.getSortOrder());
            // systemAccount인 경우 parentAccount, code, allowPosting, status 등은 통제 필요
            log.info("시스템 보호 계정 제한적 수정 적용. ID: {}", id);
        } else {
            account.setName(request.getName());
            account.setEnglishName(request.getEnglishName());
            account.setDescription(request.getDescription());
            account.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : account.getSortOrder());
            
            if (request.getStatus() != null) {
                account.setStatus(request.getStatus());
            }
            if (request.getAllowPosting() != null) {
                account.setAllowPosting(request.getAllowPosting());
            }

            // 부모 계정 변경 처리 및 순환참조 검증
            if (request.getParentId() != null) {
                if (account.getParentAccount() == null || !account.getParentAccount().getId().equals(request.getParentId())) {
                    validateCycle(id, request.getParentId());
                    Account newParent = accountRepository.findById(request.getParentId())
                            .orElseThrow(() -> new IllegalArgumentException("지정한 부모 계정을 찾을 수 없습니다. ID: " + request.getParentId()));
                    account.setParentAccount(newParent);
                    account.setParentCode(newParent.getCode());
                    
                    // 레벨 재계산 및 하위 노드 DFS 레벨 업데이트
                    recalculateLevels(account, newParent.getLevel() + 1);
                }
            } else {
                // 루트 계정으로 변경
                if (account.getParentAccount() != null) {
                    account.setParentAccount(null);
                    account.setParentCode(null);
                    recalculateLevels(account, 1);
                }
            }
        }

        Account updated = accountRepository.save(account);
        return AccountResponse.from(updated);
    }

    /**
     * 계정과목 삭제 (Soft Delete)
     */
    @Transactional
    public void deleteAccount(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("계정을 찾을 수 없습니다. ID: " + id));

        if (Boolean.TRUE.equals(account.getSystemAccount())) {
            throw new IllegalStateException("시스템 보호 계정은 삭제할 수 없습니다. 코드: " + account.getCode());
        }

        if (!account.isLeaf()) {
            throw new IllegalStateException("하위 자식 계정이 존재하는 계정은 삭제할 수 없습니다. 먼저 자식 계정을 이동하거나 삭제하세요.");
        }

        // Soft Delete 처리
        account.setDeleted(true);
        accountRepository.save(account);
        log.info("계정과목 Soft Delete 완료. ID: {}, Code: {}", id, account.getCode());
    }

    /**
     * 순환참조 Cycle 방지 유효성 검증
     */
    private void validateCycle(Long currentId, Long parentId) {
        if (currentId == null || parentId == null) return;
        if (currentId.equals(parentId)) {
            throw new IllegalArgumentException("자기 자신을 부모 계정으로 지정할 수 없습니다.");
        }

        Account parent = accountRepository.findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("지정한 부모 계정을 찾을 수 없습니다. ID: " + parentId));

        Account current = parent;
        while (current != null) {
            if (current.getId().equals(currentId)) {
                throw new IllegalArgumentException("순환 참조가 발생합니다. 선택한 부모 계정은 해당 계정의 하위 노드입니다.");
            }
            current = current.getParentAccount();
        }
    }

    /**
     * 부모 노드 이동 시 하위 노드 레벨 DFS 일괄 업데이트
     */
    private void recalculateLevels(Account account, int newLevel) {
        account.setLevel(newLevel);
        if (account.getChildren() != null) {
            for (Account child : account.getChildren()) {
                if (!Boolean.TRUE.equals(child.getDeleted())) {
                    recalculateLevels(child, newLevel + 1);
                }
            }
        }
    }

    /**
     * 멱등한 기본 CoA Seed 적재
     */
    @Transactional
    public void seedDefaultChartOfAccounts() {
        log.info("기본 계정과목(CoA) 마스터 데이터 적재 프로세스 시작...");
        try {
            ClassPathResource resource = new ClassPathResource("seeds/chart-of-accounts/default_coa.json");
            if (!resource.exists()) {
                log.warn("기본 계정과목 시드 파일(default_coa.json)을 찾을 수 없습니다.");
                return;
            }

            InputStream inputStream = resource.getInputStream();
            List<Map<String, Object>> coaList = objectMapper.readValue(inputStream, new TypeReference<List<Map<String, Object>>>() {});

            // 1단계 패스: 모든 JSON 노드들 임포트 (부모 없이 code 기준으로 등록/업데이트)
            Map<String, Account> dbAccountMap = accountRepository.findAll().stream()
                    .collect(Collectors.toMap(Account::getCode, a -> a));

            List<Map<String, Object>> legacyAccounts = getLegacyAccountsSeed();
            List<Map<String, Object>> allSeeds = new ArrayList<>(coaList);
            // 레거시 계정들도 시드 목록에 추가
            for (Map<String, Object> legacy : legacyAccounts) {
                if (!dbAccountMap.containsKey(legacy.get("code"))) {
                    allSeeds.add(legacy);
                }
            }

            // 1. 등록/수정 (기본 정보 설정)
            for (Map<String, Object> data : allSeeds) {
                String code = (String) data.get("code");
                Account account = dbAccountMap.get(code);
                
                String name = (String) data.get("name");
                String englishName = (String) data.get("englishName");
                AccountType type = AccountType.valueOf((String) data.get("type"));
                AccountSide normalBalance = AccountSide.valueOf((String) data.get("normalBalance"));
                Integer sortOrder = (Integer) data.get("sortOrder");
                Boolean allowPosting = (Boolean) data.get("allowPosting");
                Boolean systemAccount = (Boolean) data.get("systemAccount");
                String description = (String) data.get("description");

                if (account == null) {
                    account = Account.builder()
                            .code(code)
                            .name(name)
                            .englishName(englishName)
                            .type(type)
                            .normalBalance(normalBalance)
                            .level(1) // 2단계 패스에서 부모 관계 맺으며 갱신됨
                            .sortOrder(sortOrder != null ? sortOrder : 0)
                            .allowPosting(allowPosting != null ? allowPosting : true)
                            .systemAccount(systemAccount != null ? systemAccount : false)
                            .description(description)
                            .status(AccountStatus.ACTIVE)
                            .deleted(false)
                            .build();
                } else {
                    // 기존 계정이 있을 경우, Soft Delete 복구 및 필요한 필드 갱신
                    account.setDeleted(false);
                    account.setName(name);
                    account.setEnglishName(englishName);
                    account.setType(type);
                    account.setNormalBalance(normalBalance);
                    account.setSortOrder(sortOrder != null ? sortOrder : account.getSortOrder());
                    account.setAllowPosting(allowPosting != null ? allowPosting : account.getAllowPosting());
                    account.setSystemAccount(systemAccount != null ? systemAccount : account.getSystemAccount());
                    account.setDescription(description);
                }
                
                Account saved = accountRepository.save(account);
                dbAccountMap.put(code, saved);
            }

            // 2단계 패스: parentCode 기반 parentAccount 설정 및 레벨 산정
            for (Map<String, Object> data : allSeeds) {
                String code = (String) data.get("code");
                String parentCode = (String) data.get("parentCode");
                Account account = dbAccountMap.get(code);

                if (account != null) {
                    if (parentCode != null && !parentCode.trim().isEmpty()) {
                        Account parent = dbAccountMap.get(parentCode);
                        if (parent != null) {
                            account.setParentAccount(parent);
                            account.setParentCode(parent.getCode());
                            account.setLevel(parent.getLevel() + 1);
                        } else {
                            log.warn("부모 계정 코드를 매핑할 수 없습니다. 자식 코드: {}, 부모 코드: {}", code, parentCode);
                            account.setParentAccount(null);
                            account.setParentCode(null);
                            account.setLevel(1);
                        }
                    } else {
                        account.setParentAccount(null);
                        account.setParentCode(null);
                        account.setLevel(1);
                    }
                    accountRepository.save(account);
                }
            }

            log.info("기본 계정과목(CoA) 마스터 데이터 적재 성공! 총 {}개 계정 등록 완료.", allSeeds.size());
        } catch (Exception e) {
            log.error("계정과목 시드 데이터 적재 도중 예외가 발생했습니다.", e);
            throw new RuntimeException("CoA Seed 적재 실패", e);
        }
    }

    /**
     * 레거시 비즈니스 호환용 계정 시드 정보 반환
     */
    private List<Map<String, Object>> getLegacyAccountsSeed() {
        List<Map<String, Object>> legacy = new ArrayList<>();
        legacy.add(createLegacyMap("1001", "[레거시] 현금", "Legacy Cash", "ASSET", "DEBIT", 9901, true, true, "1110", "레거시 현금 계정"));
        legacy.add(createLegacyMap("1002", "[레거시] 보통예금", "Legacy Ordinary Deposits", "ASSET", "DEBIT", 9902, true, true, "1110", "레거시 보통예금 계정"));
        legacy.add(createLegacyMap("1003", "[레거시] 재고자산", "Legacy Inventories", "ASSET", "DEBIT", 9903, true, true, "1150", "레거시 재고자산 계정"));
        legacy.add(createLegacyMap("1004", "[레거시] 외상매출금", "Legacy Accounts Receivable", "ASSET", "DEBIT", 9904, true, true, "1120", "레거시 외상매출금 계정"));
        
        legacy.add(createLegacyMap("2001", "[레거시] 외상매입금", "Legacy Accounts Payable", "LIABILITY", "CREDIT", 9905, true, true, "2110", "레거시 외상매입금 계정"));
        legacy.add(createLegacyMap("2002", "[레거시] 미지급금", "Legacy Non-trade Payables", "LIABILITY", "CREDIT", 9906, true, true, "2120", "레거시 미지급금 계정"));
        legacy.add(createLegacyMap("2003", "[레거시] 미지급급여", "Legacy Salary Payable", "LIABILITY", "CREDIT", 9907, true, true, "2120", "레거시 미지급급여 계정"));
        legacy.add(createLegacyMap("2004", "[레거시] 예수금", "Legacy Withholdings", "LIABILITY", "CREDIT", 9908, true, true, "2120", "레거시 예수금 계정"));

        legacy.add(createLegacyMap("4001", "[레거시] 매출", "Legacy Sales", "REVENUE", "CREDIT", 9909, true, true, "4100", "레거시 매출 계정"));

        legacy.add(createLegacyMap("5001", "[레거시] 급여비용", "Legacy Payroll Expense", "EXPENSE", "DEBIT", 9910, true, true, "5200", "레거시 급여비용 계정"));
        legacy.add(createLegacyMap("5002", "[레거시] 매출원가", "Legacy Cost of Sales", "EXPENSE", "DEBIT", 9911, true, true, "5100", "레거시 매출원가 계정"));
        legacy.add(createLegacyMap("5003", "[레거시] 복리후생비", "Legacy Welfare Expense", "EXPENSE", "DEBIT", 9912, true, true, "5200", "레거시 복리후생비 계정"));
        return legacy;
    }

    private Map<String, Object> createLegacyMap(String code, String name, String engName, String type, String side, int sort, boolean posting, boolean system, String parent, String desc) {
        Map<String, Object> map = new HashMap<>();
        map.put("code", code);
        map.put("name", name);
        map.put("englishName", engName);
        map.put("type", type);
        map.put("normalBalance", side);
        map.put("sortOrder", sort);
        map.put("allowPosting", posting);
        map.put("systemAccount", system);
        map.put("parentCode", parent);
        map.put("description", desc);
        return map;
    }
}
