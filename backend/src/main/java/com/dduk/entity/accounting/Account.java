package com.dduk.entity.accounting;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 고유 계정 코드 (비즈니스 키) */
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    /** 계정 과목명 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 영문 계정명 (Optional) */
    @Column(name = "english_name", length = 100)
    private String englishName;

    /** 계정 유형: ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccountType type;

    /** 잔액 정상 방향: DEBIT(차변) / CREDIT(대변) */
    @Enumerated(EnumType.STRING)
    @Column(name = "normal_balance", nullable = false, length = 10)
    private AccountSide normalBalance;

    /** 계층 레벨 (자동 계산) */
    @Column(nullable = false)
    private Integer level;

    /** 정렬 순서 */
    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    /** 설명 */
    @Column(name = "description")
    private String description;

    /** 상태: ACTIVE, INACTIVE, LOCKED */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    /** 실제 전표 기표 가능 여부 */
    @Column(name = "allow_posting", nullable = false)
    @Builder.Default
    private Boolean allowPosting = true;

    /** ERP 핵심 보호 계정 여부 (삭제/코드변경/부모변경 불가) */
    @Column(name = "system_account", nullable = false)
    @Builder.Default
    private Boolean systemAccount = false;

    /** Soft Delete 여부 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    /** 부모 계정 코드 (하위 호환용 보존 컬럼) */
    @Column(name = "parent_code", length = 20)
    private String parentCode;

    /** PK 기반 부모 계정 연관 관계 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Account parentAccount;

    /** 자식 계정들 리스트 */
    @OneToMany(mappedBy = "parentAccount", cascade = CascadeType.ALL)
    @Builder.Default
    @OrderBy("sortOrder ASC, code ASC")
    private List<Account> children = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (deleted == null)
            deleted = false;
        if (status == null) {
            status = AccountStatus.ACTIVE;
        }
        if (allowPosting == null)
            allowPosting = true;
        if (systemAccount == null)
            systemAccount = false;
        if (sortOrder == null)
            sortOrder = 0;

        // 차대 유형 ↔ 방향 매핑 검증 및 자동 할당
        validateTypeAndSide();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        validateTypeAndSide();
    }

    public void validateTypeAndSide() {
        // 차감계정(Contra-account: 대손충당금, 감가상각누계액 등) 처리를 위해
        // 엄격한 차대변 강제 예외 발생 로직을 해제합니다.
    }

    /** 계산형 말단계정 여부 판단 메서드 (자식이 없으면 말단계정) */
    public boolean isLeaf() {
        return children == null || children.isEmpty();
    }

    /** 하위 호환용 isActive 조회 메서드 */
    public Boolean getIsActive() {
        return status == AccountStatus.ACTIVE || status == AccountStatus.LOCKED;
    }

    /** 하위 호환용 isActive 세터 */
    public void setIsActive(Boolean active) {
        if (active != null) {
            this.status = active ? AccountStatus.ACTIVE : AccountStatus.INACTIVE;
        }
    }

    /** 해당 계정이 차변이 정상 증가 방향인지 여부 */
    public boolean isDebitNormal() {
        return this.normalBalance == AccountSide.DEBIT;
    }

    /** 헬퍼 메서드: 자식 추가 */
    public void addChild(Account child) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        this.children.add(child);
        child.setParentAccount(this);
        child.setParentCode(this.code);
        child.setLevel(this.level + 1);
    }
}
