package com.dduk.dto.admin;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MemberStatusUpdateRequestDto {
    @NotNull(message = "변경할 활성 상태가 필요합니다.")
    private Boolean active;
}
