package com.dduk.service.admin;

import com.dduk.dto.admin.AdminMemberPageResponseDto;
import com.dduk.dto.admin.MemberCreateRequestDto;
import com.dduk.dto.admin.MemberResponseDto;
import com.dduk.entity.admin.Member;
import com.dduk.entity.admin.Role;
import com.dduk.repository.admin.MemberRepository;
import com.dduk.repository.inventory.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMemberService {

    private final MemberRepository memberRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuditLogService adminAuditLogService;

    public AdminMemberPageResponseDto getMembers(String keyword, Role role, Boolean active, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Specification<Member> specification = buildMemberSpecification(keyword, role, active);

        Page<MemberResponseDto> result = memberRepository.findAll(specification, pageable)
                .map(MemberResponseDto::from);

        return AdminMemberPageResponseDto.from(result);
    }

    public MemberResponseDto getMemberDetail(Long memberId) {
        return MemberResponseDto.from(getMember(memberId));
    }

    @Transactional
    public MemberResponseDto createMember(MemberCreateRequestDto requestDto, Long actorMemberId, String ipAddress, String userAgent) {
        validateCreateRequest(requestDto);

        if (memberRepository.findByLoginId(requestDto.getLoginId().trim()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다.");
        }

        Member member = Member.builder()
                .loginId(requestDto.getLoginId().trim())
                .password(passwordEncoder.encode(requestDto.getPassword()))
                .name(requestDto.getName().trim())
                .role(requestDto.getRole())
                .active(true)
                .build();

        Member savedMember = memberRepository.save(member);
        adminAuditLogService.recordMemberCreated(actorMemberId, savedMember, ipAddress, userAgent);
        return MemberResponseDto.from(savedMember);
    }

    @Transactional
    public MemberResponseDto updateRole(Long memberId, Role role, Long actorMemberId, String ipAddress, String userAgent) {
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "변경할 권한이 필요합니다.");
        }

        Member member = getMember(memberId);
        Role previousRole = member.getRole();
        validateLastAdminRoleChange(member, role);
        member.updateRole(role);
        adminAuditLogService.recordMemberRoleUpdated(actorMemberId, member, previousRole, role, ipAddress, userAgent);
        return MemberResponseDto.from(member);
    }

    @Transactional
    public MemberResponseDto updateStatus(Long memberId, boolean active, Long actorMemberId, String ipAddress, String userAgent) {
        Member member = getMember(memberId);
        boolean previousActive = member.isActive();
        validateSelfDeactivation(member, active, actorMemberId);
        validateLastAdminDeactivation(member, active);
        member.updateActive(active);
        adminAuditLogService.recordMemberStatusUpdated(actorMemberId, member, previousActive, active, ipAddress, userAgent);
        return MemberResponseDto.from(member);
    }

    @Transactional
    public void deleteMember(Long memberId, Long actorMemberId, String ipAddress, String userAgent) {
        Member member = getMember(memberId);
        validateSelfDeletion(member, actorMemberId);
        validateLastAdminDeletion(member);
        validatePurchaseOrderReference(member);
        adminAuditLogService.recordMemberDeleted(actorMemberId, member, ipAddress, userAgent);
        memberRepository.delete(member);
    }

    private Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "계정을 찾을 수 없습니다."));
    }

    private Specification<Member> buildMemberSpecification(String keyword, Role role, Boolean active) {
        return (root, query, criteriaBuilder) -> {
            var predicate = criteriaBuilder.conjunction();

            if (keyword != null && !keyword.trim().isEmpty()) {
                String likeKeyword = "%" + keyword.trim().toLowerCase() + "%";
                predicate = criteriaBuilder.and(
                        predicate,
                        criteriaBuilder.or(
                                criteriaBuilder.like(criteriaBuilder.lower(root.get("loginId")), likeKeyword),
                                criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), likeKeyword)
                        )
                );
            }

            if (role != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("role"), role));
            }

            if (active != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("active"), active));
            }

            return predicate;
        };
    }

    private void validateCreateRequest(MemberCreateRequestDto requestDto) {
        if (requestDto.getLoginId() == null || requestDto.getLoginId().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "아이디를 입력하세요.");
        }
        if (requestDto.getPassword() == null || requestDto.getPassword().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비밀번호를 입력하세요.");
        }
        if (requestDto.getName() == null || requestDto.getName().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이름을 입력하세요.");
        }
        if (requestDto.getRole() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "권한을 선택하세요.");
        }
    }

    private void validateSelfDeactivation(Member member, boolean active, Long actorMemberId) {
        if (!active && actorMemberId != null && member.getId().equals(actorMemberId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 자신의 계정은 비활성화할 수 없습니다.");
        }
    }

    private void validateLastAdminRoleChange(Member member, Role newRole) {
        if (member.getRole() != Role.ADMIN || newRole == Role.ADMIN || !member.isActive()) {
            return;
        }

        if (memberRepository.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "마지막 활성 관리자 권한은 제거할 수 없습니다.");
        }
    }

    private void validateLastAdminDeactivation(Member member, boolean active) {
        if (active || member.getRole() != Role.ADMIN || !member.isActive()) {
            return;
        }

        if (memberRepository.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "마지막 활성 관리자 계정은 비활성화할 수 없습니다.");
        }
    }
    private void validateSelfDeletion(Member member, Long actorMemberId) {
        if (actorMemberId != null && member.getId().equals(actorMemberId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 자신의 계정은 삭제할 수 없습니다.");
        }
    }

    private void validateLastAdminDeletion(Member member) {
        if (member.getRole() != Role.ADMIN || !member.isActive()) {
            return;
        }

        if (memberRepository.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "마지막 활성 관리자 계정은 삭제할 수 없습니다.");
        }
    }

    private void validatePurchaseOrderReference(Member member) {
        if (purchaseOrderRepository.existsByRequestedBy_Id(member.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "발주 요청 이력이 있는 계정은 삭제할 수 없습니다.");
        }
    }
}
