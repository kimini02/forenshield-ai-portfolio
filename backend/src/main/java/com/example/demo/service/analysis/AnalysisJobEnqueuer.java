package com.example.demo.service.analysis;

import com.example.demo.dto.AnalysisJobMessage;

public interface AnalysisJobEnqueuer {

    void enqueue(AnalysisJobMessage message);
}
