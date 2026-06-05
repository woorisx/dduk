package com.dduk.service.admin;

import com.dduk.dto.admin.AdminAuditLogPageResponseDto;
import com.dduk.dto.admin.AuditLogResponseDto;
import com.dduk.entity.admin.AuditAction;
import com.dduk.entity.admin.AuditLog;
import com.dduk.entity.admin.Member;
import com.dduk.entity.admin.Role;
import com.dduk.repository.admin.AuditLogRepository;
import com.dduk.repository.admin.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuditLogService {

    private static final String TARGET_TYPE_MEMBER = "MEMBER";

    private final AuditLogRepository auditLogRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public void recordMemberCreated(Long actorMemberId, Member targetMember, String ipAddress, String userAgent) {
        saveAuditLog(
                actorMemberId,
                AuditAction.CREATE_MEMBER,
                targetMember,
                String.format("관리자 계정을 생성했습니다. role=%s, active=%s", targetMember.getRole().name(), targetMember.isActive()),
                ipAddress,
                userAgent
        );
    }

    @Transactional
    public void recordMemberRoleUpdated(Long actorMemberId, Member targetMember, Role previousRole, Role newRole,
                                        String ipAddress, String userAgent) {
        saveAuditLog(
                actorMemberId,
                AuditAction.UPDATE_MEMBER_ROLE,
                targetMember,
                String.format("관리자 권한을 변경했습니다. before=%s, after=%s", previousRole.name(), newRole.name()),
                ipAddress,
                userAgent
        );
    }

    @Transactional
    public void recordMemberStatusUpdated(Long actorMemberId, Member targetMember, boolean previousActive, boolean newActive,
                                          String ipAddress, String userAgent) {
        saveAuditLog(
                actorMemberId,
                AuditAction.UPDATE_MEMBER_STATUS,
                targetMember,
                String.format("관리자 계정 상태를 변경했습니다. before=%s, after=%s", previousActive, newActive),
                ipAddress,
                userAgent
        );
    }

    @Transactional
    public void recordMemberDeleted(Long actorMemberId, Member targetMember, String ipAddress, String userAgent) {
        saveAuditLog(
                actorMemberId,
                AuditAction.DELETE_MEMBER,
                targetMember,
                String.format("관리자 계정을 삭제했습니다. role=%s, active=%s", targetMember.getRole().name(), targetMember.isActive()),
                ipAddress,
                userAgent
        );
    }

    public AdminAuditLogPageResponseDto getAuditLogs(String target, String actor, AuditAction action,
                                                     LocalDate dateFrom, LocalDate dateTo, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Specification<AuditLog> specification = buildSpecification(target, actor, action, dateFrom, dateTo);

        Page<AuditLogResponseDto> result = auditLogRepository.findAll(specification, pageable)
                .map(AuditLogResponseDto::from);

        return AdminAuditLogPageResponseDto.from(result);
    }

    private void saveAuditLog(Long actorMemberId, AuditAction action, Member targetMember, String details,
                              String ipAddress, String userAgent) {
        AuditLog auditLog = AuditLog.builder()
                .actorMember(findActor(actorMemberId))
                .action(action)
                .targetType(TARGET_TYPE_MEMBER)
                .targetId(targetMember.getId())
                .targetLabel(buildTargetLabel(targetMember))
                .details(details)
                .ipAddress(ipAddress)
                .userAgent(trimUserAgent(userAgent))
                .build();

        auditLogRepository.save(auditLog);
    }

    private Member findActor(Long actorMemberId) {
        if (actorMemberId == null) {
            return null;
        }

        return memberRepository.findById(actorMemberId).orElse(null);
    }

    private String buildTargetLabel(Member member) {
        return member.getLoginId() + " (" + member.getName() + ")";
    }

    private String trimUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }

        return userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent;
    }

    private Specification<AuditLog> buildSpecification(String target, String actor, AuditAction action,
                                                        LocalDate dateFrom, LocalDate dateTo) {
        return (root, query, criteriaBuilder) -> {
            var predicate = criteriaBuilder.conjunction();
            var actorJoin = root.join("actorMember", jakarta.persistence.criteria.JoinType.LEFT);

            if (target != null && !target.trim().isEmpty()) {
                String likeTarget = "%" + target.trim().toLowerCase() + "%";
                predicate = criteriaBuilder.and(
                        predicate,
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("targetLabel")), likeTarget)
                );
            }

            if (actor != null && !actor.trim().isEmpty()) {
                String likeActor = "%" + actor.trim().toLowerCase() + "%";
                predicate = criteriaBuilder.and(
                        predicate,
                        criteriaBuilder.or(
                                criteriaBuilder.like(criteriaBuilder.lower(actorJoin.get("loginId")), likeActor),
                                criteriaBuilder.like(criteriaBuilder.lower(actorJoin.get("name")), likeActor)
                        )
                );
            }

            if (action != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("action"), action));
            }

            Optional<LocalDateTime> dateTimeFrom = Optional.ofNullable(dateFrom).map(LocalDate::atStartOfDay);
            Optional<LocalDateTime> dateTimeTo = Optional.ofNullable(dateTo).map(date -> date.plusDays(1).atStartOfDay());

            if (dateTimeFrom.isPresent()) {
                predicate = criteriaBuilder.and(
                        predicate,
                        criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), dateTimeFrom.get())
                );
            }

            if (dateTimeTo.isPresent()) {
                predicate = criteriaBuilder.and(
                        predicate,
                        criteriaBuilder.lessThan(root.get("createdAt"), dateTimeTo.get())
                );
            }

            return predicate;
        };
    }
}
