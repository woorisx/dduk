package com.dduk.repository.admin;

import com.dduk.entity.admin.AnomalyLog;
import com.dduk.entity.admin.AnomalyLogStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AnomalyLogRepository extends JpaRepository<AnomalyLog, Long> {

    Optional<AnomalyLog> findByAnomalyKey(String anomalyKey);

    List<AnomalyLog> findByAnomalyKeyIn(Collection<String> anomalyKeys);

    long countByActiveTrue();

    long countByActiveTrueAndStatus(AnomalyLogStatus status);

    long countByActiveTrueAndSeverity(String severity);

    Optional<AnomalyLog> findFirstByActiveTrueOrderByLastDetectedAtDescIdDesc();

    @Query("""
            SELECT a
            FROM AnomalyLog a
            WHERE (:status IS NULL OR a.status = :status)
              AND (:active IS NULL OR a.active = :active)
              AND (:severity IS NULL OR a.severity = :severity)
              AND (:ruleCode IS NULL OR a.ruleCode = :ruleCode)
              AND (
                    :keyword IS NULL
                    OR LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(a.summary) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(a.sourceLabel) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  )
            """)
    Page<AnomalyLog> search(
            @Param("status") AnomalyLogStatus status,
            @Param("active") Boolean active,
            @Param("severity") String severity,
            @Param("ruleCode") String ruleCode,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
            SELECT COUNT(a)
            FROM AnomalyLog a
            WHERE a.active = true
              AND a.lastDetectedAt >= :threshold
            """)
    long countActiveDetectedAfter(@Param("threshold") LocalDateTime threshold);
}
