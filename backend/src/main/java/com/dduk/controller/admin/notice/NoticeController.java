package com.dduk.controller.admin.notice;

import com.dduk.config.PrincipalDetails;
import com.dduk.service.admin.notice.NoticeService;
import com.dduk.dto.admin.NoticeDetailDto;
import com.dduk.dto.admin.NoticeListDto;
import com.dduk.dto.admin.NoticeRequestDto;
import com.dduk.dto.common.ApiResponse;
import com.dduk.entity.admin.NoticeType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping
    public ApiResponse<Page<NoticeListDto>> getNotices(
            @RequestParam(required = false) NoticeType type,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.success(noticeService.getNotices(type, keyword, pageable), "공지사항 목록을 조회했습니다.");
    }

    @GetMapping("/{id}")
    public ApiResponse<NoticeDetailDto> getNoticeDetail(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean increaseViewCount
    ) {
        return ApiResponse.success(noticeService.getNoticeDetail(id, increaseViewCount), "공지사항 상세를 조회했습니다.");
    }

    @PostMapping
    public ApiResponse<Long> createNotice(
            @Valid @RequestBody NoticeRequestDto requestDto,
            @AuthenticationPrincipal PrincipalDetails principalDetails
    ) {
        String authorId = principalDetails != null ? principalDetails.getMember().getLoginId() : null;
        return ApiResponse.success(noticeService.createNotice(requestDto, authorId), "공지사항을 등록했습니다.");
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> updateNotice(@PathVariable Long id, @Valid @RequestBody NoticeRequestDto requestDto) {
        noticeService.updateNotice(id, requestDto);
        return ApiResponse.success(null, "공지사항을 수정했습니다.");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteNotice(@PathVariable Long id) {
        noticeService.deleteNotice(id);
        return ApiResponse.success(null, "공지사항을 삭제했습니다.");
    }
}
