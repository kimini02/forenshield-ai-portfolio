package com.example.demo.service.evidence;

import com.example.demo.domain.Evidence;
import com.example.demo.domain.enums.FileType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceStoragePathsTest {

    @Test
    @DisplayName("S3 original key uses caseName-evidenceId and hides original filename")
    void originalKey_usesCaseNameAndEvidenceId() {
        Evidence evidence = Evidence.builder()
                .caseName("딥페이크 테스트")
                .caseNumber("딥페이크 테스트")
                .fileName("홍길동_증거영상.mp4")
                .fileType(FileType.VIDEO)
                .build();
        ReflectionTestUtils.setField(evidence, "evidenceId", 148L);

        String caseKey = EvidenceStoragePaths.resolveCaseKey(evidence);

        assertThat(EvidenceStoragePaths.storedObjectFileName(evidence, "홍길동_증거영상.mp4"))
                .isEqualTo("딥페이크-테스트-148.mp4");
        assertThat(EvidenceStoragePaths.originalKey(caseKey, evidence, "홍길동_증거영상.mp4"))
                .isEqualTo("cases/딥페이크-테스트/148/original/딥페이크-테스트-148.mp4");
        assertThat(EvidenceStoragePaths.copyKey(caseKey, evidence, "홍길동_증거영상.mp4"))
                .isEqualTo("cases/딥페이크-테스트/148/copy/딥페이크-테스트-148.mp4");
        assertThat(EvidenceStoragePaths.manifestKey(caseKey, 148L))
                .isEqualTo("cases/딥페이크-테스트/148/manifest/evidence-manifest.json");
    }

    @Test
    @DisplayName("storedObjectBaseName falls back when caseName is blank")
    void storedObjectBaseName_withoutCaseName() {
        Evidence evidence = Evidence.builder()
                .fileName("clip.mp4")
                .fileType(FileType.VIDEO)
                .build();
        ReflectionTestUtils.setField(evidence, "evidenceId", 99L);

        assertThat(EvidenceStoragePaths.storedObjectFileName(evidence, "clip.MOV"))
                .isEqualTo("evidence-99.mov");
    }

    @Test
    @DisplayName("storedObjectFileName normalizes extension to lowercase")
    void storedObjectFileName_lowercasesExtension() {
        Evidence evidence = Evidence.builder()
                .caseName("사건-a")
                .fileName("clip.MOV")
                .fileType(FileType.VIDEO)
                .build();
        ReflectionTestUtils.setField(evidence, "evidenceId", 7L);

        assertThat(EvidenceStoragePaths.storedObjectFileName(evidence, "clip.MOV"))
                .isEqualTo("사건-a-7.mov");
    }
}
