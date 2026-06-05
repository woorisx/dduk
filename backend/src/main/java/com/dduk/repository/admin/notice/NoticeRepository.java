package com.dduk.repository.admin.notice;

import com.dduk.entity.admin.Notice;
import com.dduk.entity.admin.NoticeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NoticeRepository extends JpaRepository<Notice, Long> {
    Page<Notice> findByTypeAndTitleContainingIgnoreCase(NoticeType type, String title, Pageable pageable);
    Page<Notice> findByTitleContainingIgnoreCase(String title, Pageable pageable);
    Page<Notice> findByType(NoticeType type, Pageable pageable);
}
