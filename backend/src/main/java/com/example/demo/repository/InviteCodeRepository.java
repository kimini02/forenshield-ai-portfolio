package com.example.demo.repository;

import com.example.demo.domain.InviteCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InviteCodeRepository extends JpaRepository<InviteCode, Long> {

    Optional<InviteCode> findByCode(String code);

    boolean existsByCode(String code);

    List<InviteCode> findAllByOrderByCreatedAtDesc();

    @Query("""
            SELECT COUNT(i) FROM InviteCode i
            WHERE i.status = com.example.demo.domain.enums.InviteStatus.ACTIVE
              AND (i.expiresAt IS NULL OR i.expiresAt > :now)
            """)
    long countUnused(@Param("now") LocalDateTime now);
}
