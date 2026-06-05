package com.dduk.dto.admin;

import com.dduk.entity.admin.Role;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MemberRoleUpdateRequestDto {
    @NotNull(message = "변경할 권한이 필요합니다.")
    private Role role;
}
