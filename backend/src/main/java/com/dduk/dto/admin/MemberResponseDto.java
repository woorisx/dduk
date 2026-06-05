package com.dduk.dto.admin;

import com.dduk.entity.admin.Member;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MemberResponseDto {
    private Long id;
    private String loginId;
    private String name;
    private String role;
    private boolean active;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;

    public static MemberResponseDto from(Member member) {
        return MemberResponseDto.builder()
                .id(member.getId())
                .loginId(member.getLoginId())
                .name(member.getName())
                .role(member.getRole().name())
                .active(member.isActive())
                .lastLoginAt(member.getLastLoginAt())
                .createdAt(member.getCreatedAt())
                .build();
    }
}
