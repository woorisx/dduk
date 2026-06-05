package com.dduk.service.admin;

import com.dduk.entity.admin.AuditAction;
import com.dduk.entity.admin.AuditLog;
import com.dduk.entity.admin.Member;
import com.dduk.entity.admin.Role;
import com.dduk.repository.admin.AuditLogRepository;
import com.dduk.repository.admin.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private AdminAuditLogService adminAuditLogService;

    @Test
    void recordMemberCreatedSavesAuditLog() {
        Member actor = member(1L, "admin", "관리자", Role.ADMIN, true);
        Member target = member(2L, "inventory", "재고담당", Role.INVENTORY, true);

        when(memberRepository.findById(1L)).thenReturn(Optional.of(actor));

        adminAuditLogService.recordMemberCreated(1L, target, "127.0.0.1", "JUnit");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog auditLog = captor.getValue();
        assertThat(auditLog.getAction()).isEqualTo(AuditAction.CREATE_MEMBER);
        assertThat(auditLog.getActorMember().getLoginId()).isEqualTo("admin");
        assertThat(auditLog.getTargetId()).isEqualTo(2L);
        assertThat(auditLog.getTargetLabel()).isEqualTo("inventory (재고담당)");
        assertThat(auditLog.getDetails()).contains("role=INVENTORY");
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
