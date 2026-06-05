package com.dduk.service.admin.notice;

import com.dduk.repository.admin.notice.NoticeRepository;
import com.dduk.dto.admin.NoticeDetailDto;
import com.dduk.dto.admin.NoticeListDto;
import com.dduk.dto.admin.NoticeRequestDto;
import com.dduk.entity.admin.Notice;
import com.dduk.entity.admin.NoticeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {

    private final NoticeRepository noticeRepository;

    public Page<NoticeListDto> getNotices(NoticeType type, String keyword, Pageable pageable) {
        Page<Notice> noticePage;
        boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();

        if (type != null && hasKeyword) {
            noticePage = noticeRepository.findByTypeAndTitleContainingIgnoreCase(type, keyword.trim(), pageable);
        } else if (type != null) {
            noticePage = noticeRepository.findByType(type, pageable);
        } else if (hasKeyword) {
            noticePage = noticeRepository.findByTitleContainingIgnoreCase(keyword.trim(), pageable);
        } else {
            noticePage = noticeRepository.findAll(pageable);
        }

        return noticePage.map(NoticeListDto::fromEntity);
    }

    @Transactional
    public NoticeDetailDto getNoticeDetail(Long id, boolean increaseViewCount) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공지사항입니다."));

        if (increaseViewCount) {
            notice.incrementViewCount();
        }

        return NoticeDetailDto.fromEntity(notice);
    }

    @Transactional
    public Long createNotice(NoticeRequestDto requestDto, String authorId) {
        validateDisplayPeriod(requestDto);

        Notice notice = Notice.builder()
                .type(requestDto.getType())
                .title(requestDto.getTitle().trim())
                .content(requestDto.getContent().trim())
                .startDate(requestDto.getStartDate())
                .endDate(requestDto.getEndDate())
                .authorId(authorId)
                .build();

        return noticeRepository.save(notice).getId();
    }

    @Transactional
    public void updateNotice(Long id, NoticeRequestDto requestDto) {
        validateDisplayPeriod(requestDto);

        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공지사항입니다."));

        notice.update(
                requestDto.getType(),
                requestDto.getTitle().trim(),
                requestDto.getContent().trim(),
                requestDto.getStartDate(),
                requestDto.getEndDate()
        );
    }

    @Transactional
    public void deleteNotice(Long id) {
        noticeRepository.deleteById(id);
    }

    private void validateDisplayPeriod(NoticeRequestDto requestDto) {
        if (requestDto.getStartDate() != null
                && requestDto.getEndDate() != null
                && requestDto.getEndDate().isBefore(requestDto.getStartDate())) {
            throw new IllegalArgumentException("공지 게시 종료일은 시작일보다 빠를 수 없습니다.");
        }
    }
}
