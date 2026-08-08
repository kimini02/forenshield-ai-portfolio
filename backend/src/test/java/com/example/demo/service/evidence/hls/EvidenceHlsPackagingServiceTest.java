package com.example.demo.service.evidence.hls;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceHls;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.HlsStatus;
import com.example.demo.repository.EvidenceHlsRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.support.EvidenceTestFixtures;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration",
        "hls.packaging.enabled=false"
})
@ActiveProfiles("test")
class EvidenceHlsPackagingServiceTest {

    @Autowired
    private EvidenceHlsPackagingService packagingService;

    @Autowired
    private EvidenceHlsRepository evidenceHlsRepository;

    @Autowired
    private EvidenceRepository evidenceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long evidenceId;

    @BeforeEach
    void setUp() {
        evidenceHlsRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();

        User user = userRepository.save(EvidenceTestFixtures.defaultApprovedUser(passwordEncoder));
        Evidence evidence = evidenceRepository.save(
                EvidenceTestFixtures.videoEvidence(user.getUserId(), "packaging-test.mp4", 'p')
        );
        evidenceId = evidence.getEvidenceId();
    }

    @Test
    void tryMarkPackaging_pendingRow_returnsTrueAndSetsPackaging() {
        LocalDateTime now = LocalDateTime.now();
        evidenceHlsRepository.save(EvidenceHls.createPending(evidenceId, now));

        boolean acquired = packagingService.tryMarkPackaging(evidenceId);

        assertThat(acquired).isTrue();
        assertThat(evidenceHlsRepository.findByEvidenceId(evidenceId))
                .get()
                .extracting(EvidenceHls::getHlsStatus)
                .isEqualTo(HlsStatus.PACKAGING);
    }

    @Test
    void tryMarkPackaging_readyRow_returnsFalse() {
        LocalDateTime now = LocalDateTime.now();
        EvidenceHls ready = EvidenceHls.createPending(evidenceId, now);
        ready.markReady("hls/" + evidenceId + "/", new byte[] {1}, now);
        evidenceHlsRepository.save(ready);

        boolean acquired = packagingService.tryMarkPackaging(evidenceId);

        assertThat(acquired).isFalse();
        assertThat(evidenceHlsRepository.findByEvidenceId(evidenceId))
                .get()
                .extracting(EvidenceHls::getHlsStatus)
                .isEqualTo(HlsStatus.READY);
    }

    @Test
    void markFailed_setsFailedStatusAndError() {
        LocalDateTime now = LocalDateTime.now();
        evidenceHlsRepository.save(EvidenceHls.createPending(evidenceId, now));

        packagingService.markFailed(evidenceId, "ffmpeg failed", false);

        EvidenceHls row = evidenceHlsRepository.findByEvidenceId(evidenceId).orElseThrow();
        assertThat(row.getHlsStatus()).isEqualTo(HlsStatus.FAILED);
        assertThat(row.getHlsError()).isEqualTo("ffmpeg failed");
    }

    @Test
    void markFailed_permanentFailurePrefixesError() {
        LocalDateTime now = LocalDateTime.now();
        evidenceHlsRepository.save(EvidenceHls.createPending(evidenceId, now));

        packagingService.markFailed(evidenceId, "missing original", true);

        EvidenceHls row = evidenceHlsRepository.findByEvidenceId(evidenceId).orElseThrow();
        assertThat(row.getHlsError()).startsWith(HlsPackagingFailureClassifier.PERMANENT_PREFIX);
    }

    @Test
    void rollbackStalePackaging_resetsPackagingToPending() {
        LocalDateTime stale = LocalDateTime.now().minusHours(2);
        EvidenceHls packaging = EvidenceHls.createPending(evidenceId, stale);
        packaging.markPackaging(stale);
        evidenceHlsRepository.save(packaging);

        int rolledBack = packagingService.rollbackStalePackaging();

        assertThat(rolledBack).isEqualTo(1);
        assertThat(evidenceHlsRepository.findByEvidenceId(evidenceId))
                .get()
                .extracting(EvidenceHls::getHlsStatus)
                .isEqualTo(HlsStatus.PENDING);
    }
}
