package com.dduk.controller.admin;

import com.dduk.config.PrincipalDetails;
import com.dduk.dto.admin.AdminMemberPageResponseDto;
import com.dduk.dto.admin.MemberCreateRequestDto;
import com.dduk.dto.admin.MemberResponseDto;
import com.dduk.dto.admin.MemberRoleUpdateRequestDto;
import com.dduk.dto.admin.MemberStatusUpdateRequestDto;
import com.dduk.dto.common.ApiResponse;
import com.dduk.entity.admin.Role;
import com.dduk.service.admin.AdminMemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    @GetMapping
    public ApiResponse<AdminMemberPageResponseDto> getMembers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(
                adminMemberService.getMembers(keyword, role, active, page, size),
                "관리자 계정 목록을 조회했습니다."
        );
    }

    @GetMapping("/{memberId}")
    public ApiResponse<MemberResponseDto> getMemberDetail(@PathVariable Long memberId) {
        return ApiResponse.success(adminMemberService.getMemberDetail(memberId), "관리자 계정 상세를 조회했습니다.");
    }

    @PostMapping
    public ApiResponse<MemberResponseDto> createMember(
            @Valid @RequestBody MemberCreateRequestDto requestDto,
            @AuthenticationPrincipal PrincipalDetails principalDetails,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                adminMemberService.createMember(
                        requestDto,
                        principalDetails != null ? principalDetails.getMember().getId() : null,
                        request.getRemoteAddr(),
                        request.getHeader("User-Agent")
                ),
                "관리자 계정을 생성했습니다."
        );
    }

    @PatchMapping("/{memberId}/role")
    public ApiResponse<MemberResponseDto> updateRole(
            @PathVariable Long memberId,
            @Valid @RequestBody MemberRoleUpdateRequestDto requestDto,
            @AuthenticationPrincipal PrincipalDetails principalDetails,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                adminMemberService.updateRole(
                        memberId,
                        requestDto.getRole(),
                        principalDetails != null ? principalDetails.getMember().getId() : null,
                        request.getRemoteAddr(),
                        request.getHeader("User-Agent")
                ),
                "관리자 권한을 변경했습니다."
        );
    }

    @PatchMapping("/{memberId}/status")
    public ApiResponse<MemberResponseDto> updateStatus(
            @PathVariable Long memberId,
            @Valid @RequestBody MemberStatusUpdateRequestDto requestDto,
            @AuthenticationPrincipal PrincipalDetails principalDetails,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                adminMemberService.updateStatus(
                        memberId,
                        requestDto.getActive(),
                        principalDetails != null ? principalDetails.getMember().getId() : null,
                        request.getRemoteAddr(),
                        request.getHeader("User-Agent")
                ),
                "관리자 계정 상태를 변경했습니다."
        );
    }
    @DeleteMapping("/{memberId}")
    public ApiResponse<Void> deleteMember(
            @PathVariable Long memberId,
            @AuthenticationPrincipal PrincipalDetails principalDetails,
            HttpServletRequest request
    ) {
        adminMemberService.deleteMember(
                memberId,
                principalDetails != null ? principalDetails.getMember().getId() : null,
                request.getRemoteAddr(),
                request.getHeader("User-Agent")
        );
        return ApiResponse.success(null, "관리자 계정을 삭제했습니다.");
    }
}
