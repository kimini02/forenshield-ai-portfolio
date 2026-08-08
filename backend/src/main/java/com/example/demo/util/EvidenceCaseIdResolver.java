package com.example.demo.util;

import com.example.demo.domain.Evidence;

public final class EvidenceCaseIdResolver {

    private EvidenceCaseIdResolver() {
    }

    public static String resolve(Evidence evidence) {
        if (evidence.getCaseNumber() != null && !evidence.getCaseNumber().isBlank()) {
            return evidence.getCaseNumber();
        }
        if (evidence.getCaseName() != null && !evidence.getCaseName().isBlank()) {
            return evidence.getCaseName();
        }
        return "EVIDENCE-" + evidence.getEvidenceId();
    }
}
