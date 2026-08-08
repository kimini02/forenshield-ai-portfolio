package com.example.demo.repository;

import com.example.demo.domain.CustodyLog;
import com.example.demo.domain.enums.CustodyTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CustodyLogRepository extends JpaRepository<CustodyLog, Long>, JpaSpecificationExecutor<CustodyLog> {

    List<CustodyLog> findByTargetTypeAndTargetIdOrderByCreatedAtAsc(
            CustodyTargetType targetType,
            Long targetId
    );

    List<CustodyLog> findByTargetTypeOrderByLogIdAsc(CustodyTargetType targetType);

    Optional<CustodyLog> findTopByOrderByLogIdDesc();

    Optional<CustodyLog> findTopByLogIdLessThanOrderByLogIdDesc(Long logId);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime start, LocalDateTime end);

    long countByActionTypeIn(Collection<String> actionTypes);

    List<CustodyLog> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
            CustodyTargetType targetType,
            Long targetId
    );
}
