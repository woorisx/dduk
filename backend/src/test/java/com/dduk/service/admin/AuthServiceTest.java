package com.dduk.service.admin;

import com.dduk.config.JwtTokenProvider;
import com.dduk.dto.admin.LoginRequestDto;
import com.dduk.dto.admin.LoginResponseDto;
import com.dduk.entity.admin.Member;
import com.dduk.entity.admin.Role;
import com.dduk.repository.admin.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    @Test
    void loginSuccessReturnsTokenAndUpdatesLastLogin() {
        LoginRequestDto requestDto = new LoginRequestDto();
        ReflectionTestUtils.setField(requestDto, "loginId", "admin");
        ReflectionTestUtils.setField(requestDto, "password", "secret");

        Member member = member(1L, "admin", "관리자", Role.ADMIN, true);

        when(memberRepository.findByLoginId("admin")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("secret", member.getPassword())).thenReturn(true);
        when(jwtTokenProvider.createToken("admin", "ROLE_ADMIN")).thenReturn("jwt-token");

        LoginResponseDto response = authService.login(requestDto);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getLoginId()).isEqualTo("admin");
        assertThat(response.getRole()).isEqualTo("ADMIN");
        assertThat(member.getLastLoginAt()).isNotNull();
        verify(jwtTokenProvider).createToken("admin", "ROLE_ADMIN");
    }

    @Test
    void loginFailsWhenPasswordDoesNotMatch() {
        LoginRequestDto requestDto = new LoginRequestDto();
        ReflectionTestUtils.setField(requestDto, "loginId", "admin");
        ReflectionTestUtils.setField(requestDto, "password", "wrong");

        Member member = member(1L, "admin", "관리자", Role.ADMIN, true);

        when(memberRepository.findByLoginId("admin")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("wrong", member.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(requestDto))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode().value()).isEqualTo(401));
    }

    @Test
    void loginFailsWhenAccountIsInactive() {
        LoginRequestDto requestDto = new LoginRequestDto();
        ReflectionTestUtils.setField(requestDto, "loginId", "admin");
        ReflectionTestUtils.setField(requestDto, "password", "secret");

        Member member = member(1L, "admin", "관리자", Role.ADMIN, false);

        when(memberRepository.findByLoginId("admin")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("secret", member.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(requestDto))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode().value()).isEqualTo(403));
    }

    private Member member(Long id, String loginId, String name, Role role, boolean active) {
        Member member = Member.builder()
                .loginId(loginId)
                .password("encoded-password")
                .name(name)
                .role(role)
                .active(active)
                .build();
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(member, "updatedAt", LocalDateTime.now());
        return member;
    }
}
