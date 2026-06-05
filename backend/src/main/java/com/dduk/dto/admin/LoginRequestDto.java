package com.dduk.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class LoginRequestDto {
    @NotBlank(message = "아이디를 입력하세요.")
    @Size(max = 50, message = "아이디는 50자 이하여야 합니다.")
    private String loginId;

    @NotBlank(message = "비밀번호를 입력하세요.")
    @Size(max = 255, message = "비밀번호 형식이 올바르지 않습니다.")
    private String password;
}
