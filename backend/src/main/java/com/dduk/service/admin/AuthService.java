package com.dduk.service.admin;

import com.dduk.config.JwtTokenProvider;
import com.dduk.config.PrincipalDetails;
import com.dduk.dto.admin.AuthMeResponseDto;
import com.dduk.dto.admin.LoginRequestDto;
import com.dduk.dto.admin.LoginResponseDto;
import com.dduk.entity.admin.Member;
import com.dduk.repository.admin.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public LoginResponseDto login(LoginRequestDto requestDto) {
        Member member = memberRepository.findByLoginId(requestDto.getLoginId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(requestDto.getPassword(), member.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        if (!member.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "비활성화된 계정입니다. 관리자에게 문의하세요.");
        }

        member.updateLastLoginAt(LocalDateTime.now());

        String token = jwtTokenProvider.createToken(member.getLoginId(), member.getRole().getKey());

        return LoginResponseDto.builder()
                .token(token)
                .loginId(member.getLoginId())
                .name(member.getName())
                .role(member.getRole().name())
                .build();
    }

    public AuthMeResponseDto getCurrentMember(PrincipalDetails principalDetails) {
        if (principalDetails == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증 정보가 없습니다.");
        }

        Member member = memberRepository.findByLoginId(principalDetails.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자 정보를 찾을 수 없습니다."));

        return AuthMeResponseDto.from(member);
    }
}
