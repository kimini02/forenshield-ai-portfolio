package com.example.demo.repository;

import com.example.demo.domain.ReportPublicationSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReportPublicationSnapshotRepository extends JpaRepository<ReportPublicationSnapshot, Long> {

    Optional<ReportPublicationSnapshot> findByReportId(Long reportId);
}
