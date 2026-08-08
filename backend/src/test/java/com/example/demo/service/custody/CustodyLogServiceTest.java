package com.example.demo.service.custody;

import com.example.demo.domain.CustodyLog;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
class CustodyLogServiceTest {

    private static final String SHA_256_HEX_PATTERN = "^[0-9a-f]{64}$";

    @Autowired
    private CustodyLogService custodyLogService;

    @Autowired
    private CustodyLogRepository custodyLogRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        custodyLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void record_firstLogStoresRequiredFieldsAndStartsHashChain() {
        CustodyLog log = custodyLogService.record(
                1L,
                CustodyTargetType.EVIDENCE,
                100L,
                "EVIDENCE_UPLOADED",
                null,
                null,
                "증거 파일 업로드 완료",
                null,
                null
        );

        assertThat(log.getPreviousLogHash()).isNull();
        assertThat(log.getCurrentLogHash()).matches(SHA_256_HEX_PATTERN);
        assertThat(log.getActorId()).isEqualTo(1L);
        assertThat(log.getTargetType()).isEqualTo(CustodyTargetType.EVIDENCE);
        assertThat(log.getTargetId()).isEqualTo(100L);
        assertThat(log.getActionType()).isEqualTo("EVIDENCE_UPLOADED");
    }

    @Test
    void record_secondLogLinksPreviousHashToFirstCurrentHash() {
        CustodyLog first = custodyLogService.record(
                1L,
                CustodyTargetType.EVIDENCE,
                100L,
                "EVIDENCE_UPLOADED",
                "a".repeat(64),
                "/storage/original/100",
                "증거 파일 업로드 완료",
                "{\"step\":\"upload\"}",
                "127.0.0.1"
        );

        CustodyLog second = custodyLogService.record(
                1L,
                CustodyTargetType.EVIDENCE,
                100L,
                "HASH_CREATED",
                "a".repeat(64),
                "/storage/original/100",
                "SHA-256 해시 생성 완료",
                "{\"step\":\"hash\"}",
                "127.0.0.1"
        );

        assertThat(second.getPreviousLogHash()).isEqualTo(first.getCurrentLogHash());
        assertThat(second.getCurrentLogHash()).matches(SHA_256_HEX_PATTERN);
        assertThat(second.getCurrentLogHash()).isNotEqualTo(first.getCurrentLogHash());
    }

    @Test
    void record_keepsSubjectHashSeparateFromCurrentLogHash() {
        String subjectHash = "b".repeat(64);

        CustodyLog log = custodyLogService.record(
                2L,
                CustodyTargetType.EVIDENCE,
                200L,
                "HASH_CREATED",
                subjectHash,
                "/storage/original/200",
                "SHA-256 해시 생성 완료",
                "{\"algorithm\":\"SHA-256\"}",
                "127.0.0.1"
        );

        assertThat(log.getSubjectHash()).isEqualTo(subjectHash);
        assertThat(log.getCurrentLogHash()).matches(SHA_256_HEX_PATTERN);
        assertThat(log.getCurrentLogHash()).isNotEqualTo(subjectHash);
    }

    @Test
    void record_canBeFoundByTargetTypeAndTargetId() {
        custodyLogService.record(
                1L,
                CustodyTargetType.EVIDENCE,
                100L,
                "EVIDENCE_UPLOADED",
                null,
                null,
                "증거 파일 업로드 완료",
                null,
                null
        );
        custodyLogService.record(
                1L,
                CustodyTargetType.EVIDENCE,
                100L,
                "HASH_CREATED",
                "c".repeat(64),
                null,
                "SHA-256 해시 생성 완료",
                null,
                null
        );
        custodyLogService.record(
                1L,
                CustodyTargetType.REPORT,
                300L,
                "REPORT_CREATED",
                null,
                null,
                "보고서 생성 완료",
                null,
                null
        );

        List<CustodyLog> logs = custodyLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAsc(CustodyTargetType.EVIDENCE, 100L);

        assertThat(logs)
                .hasSize(2)
                .extracting(CustodyLog::getActionType)
                .containsExactly("EVIDENCE_UPLOADED", "HASH_CREATED");
    }

    @Test
    void record_requiresActorIdTargetTypeTargetIdAndActionType() {
        assertThatThrownBy(() -> custodyLogService.record(
                null, CustodyTargetType.EVIDENCE, 100L, "EVIDENCE_UPLOADED",
                null, null, null, null, null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> custodyLogService.record(
                1L, null, 100L, "EVIDENCE_UPLOADED",
                null, null, null, null, null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> custodyLogService.record(
                1L, CustodyTargetType.EVIDENCE, null, "EVIDENCE_UPLOADED",
                null, null, null, null, null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> custodyLogService.record(
                1L, CustodyTargetType.EVIDENCE, 100L, " ",
                null, null, null, null, null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordUserActionStillStoresUserTargetLog() {
        User actor = userRepository.save(user("admin01", "admin01@test.dev", UserRole.ROLE_ADMIN));
        User target = userRepository.save(user("user01", "user01@test.dev", UserRole.ROLE_USER));

        custodyLogService.recordUserAction(actor, target, "USER_APPROVED", target.getLoginId());

        List<CustodyLog> logs = custodyLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAsc(CustodyTargetType.USER, target.getUserId());

        assertThat(logs).hasSize(1);
        CustodyLog log = logs.get(0);
        assertThat(log.getActorId()).isEqualTo(actor.getUserId());
        assertThat(log.getTargetId()).isEqualTo(target.getUserId());
        assertThat(log.getActionType()).isEqualTo("USER_APPROVED");
        assertThat(log.getReason()).isEqualTo(target.getLoginId());
        assertThat(log.getCurrentLogHash()).matches(SHA_256_HEX_PATTERN);
    }

    @Test
    void verifyTargetChain_returnsValidForLinkedEvidenceLogs() {
        CustodyLog first = custodyLogService.record(
                1L, CustodyTargetType.EVIDENCE, 100L, "EVIDENCE_UPLOADED",
                "a".repeat(64), "/storage/original/100", "업로드", null, null
        );
        CustodyLog second = custodyLogService.record(
                1L, CustodyTargetType.EVIDENCE, 100L, "HASH_CREATED",
                "a".repeat(64), "/storage/original/100", "해시", null, null
        );

        assertThat(second.getPreviousLogHash()).isEqualTo(first.getCurrentLogHash());

        CustodyLogService.TargetChainVerifyResult result =
                custodyLogService.verifyTargetChain(CustodyTargetType.EVIDENCE, 100L);

        assertThat(result.valid()).isTrue();
        assertThat(result.logCount()).isEqualTo(2);
        assertThat(custodyLogService.verifyChainIntegrity(CustodyTargetType.EVIDENCE, 100L)).isTrue();
    }

    @Test
    void verifyTargetChain_toleratesJsonWhitespaceReformatting() {
        CustodyLog log = custodyLogService.record(
                1L, CustodyTargetType.EVIDENCE, 100L, "EVIDENCE_UPLOADED",
                "a".repeat(64), "/storage/original/100", "업로드",
                "{\"caseName\":\"demo\",\"fileSize\":10}",
                null
        );

        // Simulate PostgreSQL json column returning spaced JSON on read.
        log.setEventPayloadJson("{\"caseName\": \"demo\", \"fileSize\": 10}");
        custodyLogRepository.save(log);

        CustodyLogService.TargetChainVerifyResult result =
                custodyLogService.verifyTargetChain(CustodyTargetType.EVIDENCE, 100L);

        assertThat(result.valid()).isTrue();
        assertThat(result.logCount()).isEqualTo(1);
    }

    @Test
    void verifyTargetChain_ignoresLogsFromOtherTargets() {
        custodyLogService.record(
                1L, CustodyTargetType.EVIDENCE, 100L, "EVIDENCE_UPLOADED",
                null, null, "업로드", null, null
        );
        custodyLogService.record(
                1L, CustodyTargetType.REPORT, 300L, "REPORT_CREATED",
                null, null, "보고서", null, null
        );

        CustodyLogService.TargetChainVerifyResult result =
                custodyLogService.verifyTargetChain(CustodyTargetType.EVIDENCE, 100L);

        assertThat(result.valid()).isTrue();
        assertThat(result.logCount()).isEqualTo(1);
    }

    private User user(String loginId, String email, UserRole role) {
        return User.builder()
                .loginId(loginId)
                .email(email)
                .password("encoded-password")
                .name(loginId)
                .organizationType(OrgType.ETC)
                .department("test")
                .role(role)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build();
    }
}
