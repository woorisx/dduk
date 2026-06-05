package com.dduk.repository.accounting;

import com.dduk.entity.accounting.Account;
import com.dduk.entity.accounting.AccountStatus;
import com.dduk.entity.accounting.AccountType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByCodeAndDeletedFalse(String code);

    default Optional<Account> findByCode(String code) {
        return findByCodeAndDeletedFalse(code);
    }

    List<Account> findByTypeAndDeletedFalseOrderByCodeAsc(AccountType type);

    default List<Account> findByTypeOrderByCodeAsc(String type) {
        return findByTypeAndDeletedFalseOrderByCodeAsc(AccountType.valueOf(type));
    }

    @Query("SELECT a FROM Account a WHERE a.deleted = false AND (a.status = 'ACTIVE' OR a.status = 'LOCKED') ORDER BY a.code ASC")
    List<Account> findByIsActiveTrueOrderByCodeAsc();

    /** 
     * 실시간으로 자식이 없는(children IS EMPTY) 말단계정 중 활성인 계정만 조회
     */
    @Query("SELECT a FROM Account a WHERE a.deleted = false AND (a.status = 'ACTIVE' OR a.status = 'LOCKED') AND a.children IS EMPTY ORDER BY a.code ASC")
    List<Account> findAllLeafAccounts();

    @Query("""
            SELECT a
            FROM Account a
            WHERE a.deleted = false
              AND a.status = 'ACTIVE'
              AND a.allowPosting = true
              AND a.children IS EMPTY
              AND (:type IS NULL OR a.type = :type)
              AND (:keyword IS NULL OR lower(a.code) LIKE lower(concat('%', :keyword, '%'))
                   OR lower(a.name) LIKE lower(concat('%', :keyword, '%'))
                   OR lower(coalesce(a.englishName, '')) LIKE lower(concat('%', :keyword, '%')))
            ORDER BY a.code ASC
            """)
    List<Account> searchPostingAccounts(@Param("keyword") String keyword, @Param("type") AccountType type);

    @Query("""
            SELECT a
            FROM Account a
            WHERE a.deleted = false
              AND a.status = 'ACTIVE'
              AND a.allowPosting = true
              AND a.children IS EMPTY
              AND a.type = :assetType
              AND (a.code LIKE '111%' OR a.code IN ('1001', '1002'))
              AND (:keyword IS NULL OR lower(a.code) LIKE lower(concat('%', :keyword, '%'))
                   OR lower(a.name) LIKE lower(concat('%', :keyword, '%'))
                   OR lower(coalesce(a.englishName, '')) LIKE lower(concat('%', :keyword, '%')))
            ORDER BY a.code ASC
            """)
    List<Account> searchCashAccounts(@Param("keyword") String keyword, @Param("assetType") AccountType assetType);

    boolean existsByCodeAndDeletedFalse(String code);

    @Query("""
            SELECT count(a)
            FROM Account a
            WHERE a.deleted = false
              AND (a.status <> 'ACTIVE' OR a.allowPosting = false OR a.children IS NOT EMPTY)
            """)
    long countAccountsBlockedForPosting();

    /**
     * N+1 문제를 방지하여 전체 계정을 단 1회 쿼리로 페치 조인 조회하는 고성능 쿼리
     */
    @Query("SELECT DISTINCT a FROM Account a LEFT JOIN FETCH a.children WHERE a.deleted = false ORDER BY a.sortOrder ASC, a.code ASC")
    List<Account> findAllWithChildrenAndDeletedFalse();
}
