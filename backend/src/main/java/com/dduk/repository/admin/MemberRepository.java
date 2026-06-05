package com.dduk.repository.admin;

import com.dduk.entity.admin.Member;
import com.dduk.entity.admin.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long>, JpaSpecificationExecutor<Member> {

    Optional<Member> findByLoginId(String loginId);

    List<Member> findAllByOrderByIdDesc();

    long countByActiveTrue();

    long countByRoleAndActiveTrue(Role role);

    long countByLastLoginAtAfter(LocalDateTime lastLoginAt);
}
