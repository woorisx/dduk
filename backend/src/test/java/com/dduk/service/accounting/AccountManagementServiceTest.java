package com.dduk.service.accounting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dduk.dto.accounting.AccountCreateRequest;
import com.dduk.dto.accounting.AccountResponse;
import com.dduk.dto.accounting.AccountUpdateRequest;
import com.dduk.entity.accounting.Account;
import com.dduk.entity.accounting.AccountSide;
import com.dduk.entity.accounting.AccountStatus;
import com.dduk.entity.accounting.AccountType;
import com.dduk.repository.accounting.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * AccountManagementService 단위 테스트
 * - 계층 구조, 순환참조 방지, Soft Delete 통제, 시스템 보호 계정 등 핵심 로직 검증
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountManagementService 단위 테스트")
class AccountManagementServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AccountManagementService accountManagementService;

    // 테스트용 공통 계정 엔티티
    private Account assetRoot;
    private Account currentAssets;
    private Account cashAccount;

    @BeforeEach
    void setUp() {
        // 자산 루트 계정 (level=1, 자식 있음 -> Leaf 아님)
        assetRoot = Account.builder()
                .code("1000")
                .name("자산")
                .englishName("Assets")
                .type(AccountType.ASSET)
                .normalBalance(AccountSide.DEBIT)
                .level(1)
                .sortOrder(100)
                .status(AccountStatus.ACTIVE)
                .allowPosting(false)
                .systemAccount(true)
                .deleted(false)
                .children(new ArrayList<>())
                .build();
        setId(assetRoot, 1L);

        // 유동자산 (level=2)
        currentAssets = Account.builder()
                .code("1100")
                .name("유동자산")
                .englishName("Current Assets")
                .type(AccountType.ASSET)
                .normalBalance(AccountSide.DEBIT)
                .level(2)
                .sortOrder(110)
                .status(AccountStatus.ACTIVE)
                .allowPosting(false)
                .systemAccount(true)
                .deleted(false)
                .parentAccount(assetRoot)
                .parentCode("1000")
                .children(new ArrayList<>())
                .build();
        setId(currentAssets, 2L);

        // 현금 계정 (level=3, Leaf - 기표 가능)
        cashAccount = Account.builder()
                .code("1111")
                .name("현금")
                .englishName("Cash")
                .type(AccountType.ASSET)
                .normalBalance(AccountSide.DEBIT)
                .level(3)
                .sortOrder(1)
                .status(AccountStatus.ACTIVE)
                .allowPosting(true)
                .systemAccount(true)
                .deleted(false)
                .parentAccount(currentAssets)
                .parentCode("1100")
                .children(new ArrayList<>())  // 자식 없음 -> isLeaf() == true
                .build();
        setId(cashAccount, 3L);

        // 연관관계 연결
        assetRoot.getChildren().add(currentAssets);
        currentAssets.getChildren().add(cashAccount);
    }

    // =========================================================
    // createAccount 테스트
    // =========================================================

    @Test
    @DisplayName("계정과목 정상 생성 - 루트 계정 (parentId 없음)")
    void createAccount_루트계정_정상생성() {
        // given
        AccountCreateRequest request = new AccountCreateRequest();
        request.setCode("9000");
        request.setName("테스트 자산");
        request.setType(AccountType.ASSET);
        request.setNormalBalance(AccountSide.DEBIT);
        request.setSortOrder(900);
        request.setAllowPosting(false);
        request.setStatus(AccountStatus.ACTIVE);

        given(accountRepository.existsByCodeAndDeletedFalse("9000")).willReturn(false);
        given(accountRepository.save(any(Account.class))).willAnswer(inv -> {
            Account a = inv.getArgument(0);
            setId(a, 99L);
            return a;
        });

        // when
        AccountResponse response = accountManagementService.createAccount(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo("9000");
        assertThat(response.getLevel()).isEqualTo(1); // 루트이면 level=1
        assertThat(response.getParentId()).isNull();
        then(accountRepository).should().existsByCodeAndDeletedFalse("9000");
        then(accountRepository).should().save(any(Account.class));
    }

    @Test
    @DisplayName("계정과목 정상 생성 - 부모 계정 지정")
    void createAccount_부모지정_정상생성() {
        // given
        AccountCreateRequest request = new AccountCreateRequest();
        request.setCode("1115");
        request.setName("테스트 당좌예금");
        request.setType(AccountType.ASSET);
        request.setNormalBalance(AccountSide.DEBIT);
        request.setParentId(2L); // currentAssets의 ID

        given(accountRepository.existsByCodeAndDeletedFalse("1115")).willReturn(false);
        given(accountRepository.findById(2L)).willReturn(Optional.of(currentAssets));
        given(accountRepository.save(any(Account.class))).willAnswer(inv -> {
            Account a = inv.getArgument(0);
            setId(a, 100L);
            return a;
        });

        // when
        AccountResponse response = accountManagementService.createAccount(request);

        // then
        assertThat(response.getLevel()).isEqualTo(3); // currentAssets(level=2) + 1 = 3
        assertThat(response.getParentId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("계정과목 생성 실패 - 중복 코드")
    void createAccount_중복코드_예외발생() {
        // given
        AccountCreateRequest request = new AccountCreateRequest();
        request.setCode("1111"); // 이미 존재하는 코드

        given(accountRepository.existsByCodeAndDeletedFalse("1111")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> accountManagementService.createAccount(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 사용 중인 계정 코드입니다");
    }

    // =========================================================
    // deleteAccount 테스트
    // =========================================================

    @Test
    @DisplayName("계정과목 Soft Delete 성공 - 말단(Leaf) 비시스템 계정")
    void deleteAccount_말단계정_삭제성공() {
        // given: 일반(비시스템) 말단 계정 생성
        Account leafAccount = Account.builder()
                .code("9999")
                .name("테스트 잡계정")
                .type(AccountType.EXPENSE)
                .normalBalance(AccountSide.DEBIT)
                .level(2)
                .status(AccountStatus.ACTIVE)
                .allowPosting(true)
                .systemAccount(false)
                .deleted(false)
                .children(new ArrayList<>()) // children 비어있음 -> isLeaf() == true
                .build();
        setId(leafAccount, 50L);

        given(accountRepository.findById(50L)).willReturn(Optional.of(leafAccount));
        given(accountRepository.save(any(Account.class))).willReturn(leafAccount);

        // when
        accountManagementService.deleteAccount(50L);

        // then: deleted = true로 저장됨
        assertThat(leafAccount.getDeleted()).isTrue();
        then(accountRepository).should().save(leafAccount);
    }

    @Test
    @DisplayName("계정과목 삭제 실패 - 시스템 보호 계정")
    void deleteAccount_시스템계정_예외발생() {
        // given
        given(accountRepository.findById(1L)).willReturn(Optional.of(assetRoot));

        // when & then
        assertThatThrownBy(() -> accountManagementService.deleteAccount(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("시스템 보호 계정은 삭제할 수 없습니다");
    }

    @Test
    @DisplayName("계정과목 삭제 실패 - 자식이 있는 부모 계정")
    void deleteAccount_자식있는계정_예외발생() {
        // given: currentAssets는 cashAccount를 자식으로 가짐 -> isLeaf() == false
        given(accountRepository.findById(2L)).willReturn(Optional.of(currentAssets));
        currentAssets = Account.builder()
                .code("1100")
                .name("유동자산")
                .type(AccountType.ASSET)
                .normalBalance(AccountSide.DEBIT)
                .level(2)
                .status(AccountStatus.ACTIVE)
                .systemAccount(false) // 시스템 계정 아닌데 자식 있음
                .deleted(false)
                .children(List.of(cashAccount)) // 자식이 있음
                .build();
        setId(currentAssets, 2L);

        given(accountRepository.findById(2L)).willReturn(Optional.of(currentAssets));

        // when & then
        assertThatThrownBy(() -> accountManagementService.deleteAccount(2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("하위 자식 계정이 존재하는 계정은 삭제할 수 없습니다");
    }

    @Test
    @DisplayName("계정과목 삭제 실패 - 존재하지 않는 ID")
    void deleteAccount_존재하지않는ID_예외발생() {
        // given
        given(accountRepository.findById(9999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> accountManagementService.deleteAccount(9999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("계정을 찾을 수 없습니다");
    }

    // =========================================================
    // updateAccount - 순환참조(Cycle) 방지 테스트
    // =========================================================

    @Test
    @DisplayName("순환참조 방지 - 자기 자신을 부모로 지정 시 예외")
    void updateAccount_자기자신부모지정_예외발생() {
        // given: 비시스템 계정(ID=10)의 부모를 자기 자신(ID=10)으로 변경 시도
        Account normalAccount = Account.builder()
                .code("9100")
                .name("테스트 계정")
                .type(AccountType.EXPENSE)
                .normalBalance(AccountSide.DEBIT)
                .level(1)
                .sortOrder(0)
                .status(AccountStatus.ACTIVE)
                .systemAccount(false) // 비시스템 계정이어야 부모 변경 로직에 도달
                .deleted(false)
                .children(new ArrayList<>())
                .build();
        setId(normalAccount, 10L);

        AccountUpdateRequest request = new AccountUpdateRequest();
        request.setName("테스트 계정");
        request.setParentId(10L); // 자기 자신

        given(accountRepository.findById(10L)).willReturn(Optional.of(normalAccount));

        // when & then
        assertThatThrownBy(() -> accountManagementService.updateAccount(10L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("자기 자신을 부모 계정으로 지정할 수 없습니다");
    }

    @Test
    @DisplayName("순환참조 방지 - 하위 노드를 부모로 지정 시 예외")
    void updateAccount_하위노드부모지정_예외발생() {
        // given: 비시스템 부모(ID=20)의 부모를 그 자식(ID=21)으로 변경 → 순환 발생
        Account parentNode = Account.builder()
                .code("9200")
                .name("부모 계정")
                .type(AccountType.EXPENSE)
                .normalBalance(AccountSide.DEBIT)
                .level(1)
                .sortOrder(0)
                .status(AccountStatus.ACTIVE)
                .systemAccount(false) // 비시스템
                .deleted(false)
                .children(new ArrayList<>())
                .build();
        setId(parentNode, 20L);

        Account childNode = Account.builder()
                .code("9210")
                .name("자식 계정")
                .type(AccountType.EXPENSE)
                .normalBalance(AccountSide.DEBIT)
                .level(2)
                .sortOrder(1)
                .status(AccountStatus.ACTIVE)
                .systemAccount(false)
                .deleted(false)
                .parentAccount(parentNode) // 부모체인: childNode -> parentNode(=currentId=20)
                .parentCode("9200")
                .children(new ArrayList<>())
                .build();
        setId(childNode, 21L);

        parentNode.getChildren().add(childNode);

        AccountUpdateRequest request = new AccountUpdateRequest();
        request.setName("부모 계정");
        request.setParentId(21L); // 자식을 부모로 → 순환

        given(accountRepository.findById(20L)).willReturn(Optional.of(parentNode));
        given(accountRepository.findById(21L)).willReturn(Optional.of(childNode)); // validateCycle 내부

        // when & then
        assertThatThrownBy(() -> accountManagementService.updateAccount(20L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("순환 참조가 발생합니다");
    }

    @Test
    @DisplayName("계정과목 수정 성공 - 일반 비시스템 계정의 이름 변경")
    void updateAccount_이름변경_정상수정() {
        // given: 일반 비시스템 계정
        Account normalAccount = Account.builder()
                .code("9100")
                .name("테스트 계정")
                .type(AccountType.EXPENSE)
                .normalBalance(AccountSide.DEBIT)
                .level(1)
                .sortOrder(0)
                .status(AccountStatus.ACTIVE)
                .systemAccount(false)
                .deleted(false)
                .children(new ArrayList<>())
                .build();
        setId(normalAccount, 10L);

        AccountUpdateRequest request = new AccountUpdateRequest();
        request.setName("수정된 계정명");
        request.setEnglishName("Modified Account");
        request.setSortOrder(5);

        given(accountRepository.findById(10L)).willReturn(Optional.of(normalAccount));
        given(accountRepository.save(any(Account.class))).willReturn(normalAccount);

        // when
        AccountResponse response = accountManagementService.updateAccount(10L, request);

        // then
        assertThat(normalAccount.getName()).isEqualTo("수정된 계정명");
        assertThat(normalAccount.getSortOrder()).isEqualTo(5);
        then(accountRepository).should().save(normalAccount);
    }

    @Test
    @DisplayName("시스템 보호 계정 수정 - 허용 필드만 변경 가능")
    void updateAccount_시스템계정_제한적수정() {
        // given: systemAccount=true
        Account systemAcc = Account.builder()
                .code("1000")
                .name("자산")
                .type(AccountType.ASSET)
                .normalBalance(AccountSide.DEBIT)
                .level(1)
                .sortOrder(100)
                .status(AccountStatus.ACTIVE)
                .systemAccount(true)
                .deleted(false)
                .children(new ArrayList<>())
                .build();
        setId(systemAcc, 1L);

        AccountUpdateRequest request = new AccountUpdateRequest();
        request.setName("자산 (수정된 이름)");
        request.setEnglishName("Assets Updated");
        request.setSortOrder(99);
        request.setStatus(AccountStatus.INACTIVE); // 무시돼야 함

        given(accountRepository.findById(1L)).willReturn(Optional.of(systemAcc));
        given(accountRepository.save(any(Account.class))).willReturn(systemAcc);

        // when
        accountManagementService.updateAccount(1L, request);

        // then: 이름은 변경됐으나 상태는 시스템 계정이라 변경 시도가 무시됨
        assertThat(systemAcc.getName()).isEqualTo("자산 (수정된 이름)");
        assertThat(systemAcc.getStatus()).isEqualTo(AccountStatus.ACTIVE); // 상태 변경 무시
    }

    // =========================================================
    // isLeaf 계산 로직 검증
    // =========================================================

    @Test
    @DisplayName("isLeaf - children 리스트가 비어 있으면 말단 계정")
    void isLeaf_자식없음_true() {
        assertThat(cashAccount.isLeaf()).isTrue();
    }

    @Test
    @DisplayName("isLeaf - children 리스트가 비어 있지 않으면 말단 계정 아님")
    void isLeaf_자식있음_false() {
        assertThat(assetRoot.isLeaf()).isFalse();
        assertThat(currentAssets.isLeaf()).isFalse();
    }

    // =========================================================


    // =========================================================
    // 헬퍼 메서드 - Reflection으로 ID 설정 (테스트 전용)
    // =========================================================
    private void setId(Account account, Long id) {
        try {
            var field = Account.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(account, id);
        } catch (Exception e) {
            throw new RuntimeException("테스트 ID 설정 실패", e);
        }
    }
}
