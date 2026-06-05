package com.dduk.service.admin;

import com.dduk.dto.admin.MemberCreateRequestDto;
import com.dduk.entity.admin.Member;
import com.dduk.entity.admin.Role;
import com.dduk.repository.admin.MemberRepository;
import com.dduk.repository.inventory.PurchaseOrderRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private AdminAuditLogService adminAuditLogService;

    @InjectMocks
    private AdminMemberService adminMemberService;

    @Test
    void createMemberSuccessRecordsAuditLog() {
        MemberCreateRequestDto requestDto = new MemberCreateRequestDto();
        ReflectionTestUtils.setField(requestDto, "loginId", "inventory");
        ReflectionTestUtils.setField(requestDto, "password", "temp1234");
        ReflectionTestUtils.setField(requestDto, "name", "재고담당");
        ReflectionTestUtils.setField(requestDto, "role", Role.INVENTORY);

        when(memberRepository.findByLoginId("inventory")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("temp1234")).thenReturn("encoded");
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> {
            Member saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.now());
            ReflectionTestUtils.setField(saved, "updatedAt", LocalDateTime.now());
            return saved;
        });

        var response = adminMemberService.createMember(requestDto, 1L, "127.0.0.1", "JUnit");

        assertThat(response.getLoginId()).isEqualTo("inventory");
        verify(adminAuditLogService).recordMemberCreated(eq(1L), any(Member.class), eq("127.0.0.1"), eq("JUnit"));
    }

    @Test
    void createMemberFailsWhenLoginIdAlreadyExists() {
        MemberCreateRequestDto requestDto = new MemberCreateRequestDto();
        ReflectionTestUtils.setField(requestDto, "loginId", "admin");
        ReflectionTestUtils.setField(requestDto, "password", "temp1234");
        ReflectionTestUtils.setField(requestDto, "name", "관리자");
        ReflectionTestUtils.setField(requestDto, "role", Role.ADMIN);

        when(memberRepository.findByLoginId("admin")).thenReturn(Optional.of(member(1L, "admin", "관리자", Role.ADMIN, true)));

        assertThatThrownBy(() -> adminMemberService.createMember(requestDto, 1L, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("이미 사용 중인 아이디");
    }

    @Test
    void updateRoleFailsWhenRemovingLastActiveAdminRole() {
        Member adminMember = member(1L, "admin", "관리자", Role.ADMIN, true);

        when(memberRepository.findById(1L)).thenReturn(Optional.of(adminMember));
        when(memberRepository.countByRoleAndActiveTrue(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> adminMemberService.updateRole(1L, Role.HR, 2L, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("마지막 활성 관리자 권한");

        verify(adminAuditLogService, never()).recordMemberRoleUpdated(any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateStatusFailsWhenSelfDeactivationIsRequested() {
        Member adminMember = member(1L, "admin", "관리자", Role.ADMIN, true);

        when(memberRepository.findById(1L)).thenReturn(Optional.of(adminMember));

        assertThatThrownBy(() -> adminMemberService.updateStatus(1L, false, 1L, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("자기 자신의 계정은 비활성화할 수 없습니다.");

        verify(adminAuditLogService, never()).recordMemberStatusUpdated(any(), any(), anyBoolean(), anyBoolean(), any(), any());
    }

    @Test
    void updateStatusFailsWhenDeactivatingLastActiveAdmin() {
        Member adminMember = member(1L, "admin", "관리자", Role.ADMIN, true);

        when(memberRepository.findById(1L)).thenReturn(Optional.of(adminMember));
        when(memberRepository.countByRoleAndActiveTrue(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> adminMemberService.updateStatus(1L, false, 2L, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("마지막 활성 관리자 계정");

        verify(adminAuditLogService, never()).recordMemberStatusUpdated(any(), any(), anyBoolean(), anyBoolean(), any(), any());
    }

    @Test
    void deleteMemberSuccessRecordsAuditLogAndDeletesMember() {
        Member member = member(5L, "inventory", "재고담당", Role.INVENTORY, true);

        when(memberRepository.findById(5L)).thenReturn(Optional.of(member));
        when(purchaseOrderRepository.existsByRequestedBy_Id(5L)).thenReturn(false);

        adminMemberService.deleteMember(5L, 1L, "127.0.0.1", "JUnit");

        verify(adminAuditLogService).recordMemberDeleted(eq(1L), eq(member), eq("127.0.0.1"), eq("JUnit"));
        verify(memberRepository).delete(member);
    }

    @Test
    void deleteMemberFailsWhenSelfDeletionIsRequested() {
        Member member = member(1L, "admin", "관리자", Role.ADMIN, true);

        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> adminMemberService.deleteMember(1L, 1L, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("자기 자신의 계정은 삭제할 수 없습니다.");

        verify(adminAuditLogService, never()).recordMemberDeleted(any(), any(), any(), any());
        verify(memberRepository, never()).delete(any(Member.class));
    }

    @Test
    void deleteMemberFailsWhenDeletingLastActiveAdmin() {
        Member member = member(1L, "admin", "관리자", Role.ADMIN, true);

        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(memberRepository.countByRoleAndActiveTrue(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> adminMemberService.deleteMember(1L, 2L, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("마지막 활성 관리자 계정은 삭제할 수 없습니다.");

        verify(adminAuditLogService, never()).recordMemberDeleted(any(), any(), any(), any());
        verify(memberRepository, never()).delete(any(Member.class));
    }

    @Test
    void deleteMemberFailsWhenPurchaseOrderRequesterReferenceExists() {
        Member member = member(9L, "buyer", "구매담당", Role.INVENTORY, true);

        when(memberRepository.findById(9L)).thenReturn(Optional.of(member));
        when(purchaseOrderRepository.existsByRequestedBy_Id(9L)).thenReturn(true);

        assertThatThrownBy(() -> adminMemberService.deleteMember(9L, 1L, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("발주 요청 이력이 있는 계정은 삭제할 수 없습니다.");

        verify(adminAuditLogService, never()).recordMemberDeleted(any(), any(), any(), any());
        verify(memberRepository, never()).delete(any(Member.class));
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
