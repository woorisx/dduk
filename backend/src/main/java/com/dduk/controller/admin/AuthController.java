package com.dduk.controller.admin;

import com.dduk.config.PrincipalDetails;
import com.dduk.dto.admin.AuthMeResponseDto;
import com.dduk.dto.admin.LoginRequestDto;
import com.dduk.dto.admin.LoginResponseDto;
import com.dduk.dto.common.ApiResponse;
import com.dduk.service.admin.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto requestDto) {
        return ApiResponse.success(authService.login(requestDto), "로그인에 성공했습니다.");
    }

    @GetMapping("/me")
    public ApiResponse<AuthMeResponseDto> me(@AuthenticationPrincipal PrincipalDetails principalDetails) {
        return ApiResponse.success(authService.getCurrentMember(principalDetails), "현재 사용자 정보를 조회했습니다.");
    }
}
