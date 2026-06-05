package com.dduk.repository.admin;

import com.dduk.entity.admin.TaskHistory;
import com.dduk.entity.admin.TaskHistoryStatus;
import com.dduk.entity.admin.TaskHistoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface TaskHistoryRepository extends JpaRepository<TaskHistory, Long> {

    Optional<TaskHistory> findByTaskId(String taskId);

    long countByTaskType(TaskHistoryType taskType);

    long countByTaskTypeAndStatus(TaskHistoryType taskType, TaskHistoryStatus status);

    long countByTaskTypeAndStatusIn(TaskHistoryType taskType, Collection<TaskHistoryStatus> statuses);

    long countByStatusAndCompletedAtAfter(TaskHistoryStatus status, LocalDateTime completedAt);

    Optional<TaskHistory> findFirstByTaskTypeAndActionNameAndStatusOrderByCompletedAtDescIdDesc(
            TaskHistoryType taskType,
            String actionName,
            TaskHistoryStatus status
    );

    @Query("""
            SELECT t
            FROM TaskHistory t
            WHERE (:taskType IS NULL OR t.taskType = :taskType)
              AND (:actionName IS NULL OR t.actionName = :actionName)
            ORDER BY t.requestedAt DESC, t.id DESC
            """)
    Page<TaskHistory> findRecentByTaskScope(
            @Param("taskType") TaskHistoryType taskType,
            @Param("actionName") String actionName,
            Pageable pageable
    );
}
