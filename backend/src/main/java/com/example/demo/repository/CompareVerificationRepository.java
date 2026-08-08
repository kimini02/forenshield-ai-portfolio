package com.example.demo.repository;

import com.example.demo.domain.CompareVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CompareVerificationRepository extends JpaRepository<CompareVerification, Long> {

    Optional<CompareVerification> findByCompareIdAndUserId(Long compareId, Long userId);

    List<CompareVerification> findByCompareIdIn(Collection<Long> compareIds);
}
