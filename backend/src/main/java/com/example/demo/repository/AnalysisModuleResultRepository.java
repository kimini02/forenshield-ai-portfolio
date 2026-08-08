package com.example.demo.repository;

import com.example.demo.domain.AnalysisModuleResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnalysisModuleResultRepository extends JpaRepository<AnalysisModuleResult, Long> {

    List<AnalysisModuleResult> findByAnalysisResultIdOrderByCreatedAtAsc(Long analysisResultId);
}
