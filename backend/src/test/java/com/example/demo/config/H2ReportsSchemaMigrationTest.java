package com.example.demo.config;

import com.example.demo.domain.Evidence;
import com.example.demo.domain.Report;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.ReportRepository;
import com.example.demo.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
class H2ReportsSchemaMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private H2ErdSchemaInitializer schemaInitializer;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private EvidenceRepository evidenceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private Evidence evidence;

    @BeforeEach
    void setUp() {
        reportRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .loginId("schema-test")
                .email("schema-test@local.dev")
                .password(passwordEncoder.encode("pass"))
                .name("schema test")
                .organizationType(OrgType.ETC)
                .department("test")
                .role(UserRole.ROLE_USER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        evidence = evidenceRepository.save(Evidence.builder()
                .uploaderId(testUser.getUserId())
                .caseName("schema-case")
                .caseNumber("schema-case")
                .fileName("sample.mp4")
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(12L)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue("a".repeat(64))
                .originalStoragePath("s3://bucket/schema")
                .uploadedAt(LocalDateTime.now())
                .build());
    }

    @AfterEach
    void tearDown() {
        reportRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void compareReport_persistsWhenAnalysisResultIdIsNull_afterLegacyNotNullConstraint() {
        try {
            jdbcTemplate.execute("ALTER TABLE reports ALTER COLUMN analysis_result_id SET NOT NULL");
        } catch (Exception ignored) {
            // already NOT NULL on some H2 builds
        }

        schemaInitializer.migrateReportsTableForComparePdf();

        Report report = new Report();
        report.setCompareId(99L);
        report.setEvidenceId(evidence.getEvidenceId());
        report.setCreatedBy(testUser.getUserId());
        report.setReportFileName("compare-report-99.pdf");
        report.setStoragePath("reports/compare/99/compare-report-99.pdf");
        report.setReportHash("b".repeat(64));
        report.setFileSize(1024L);
        report.setCreatedAt(LocalDateTime.now());

        assertThatCode(() -> reportRepository.saveAndFlush(report)).doesNotThrowAnyException();
        assertThat(report.getReportId()).isNotNull();
        assertThat(report.getAnalysisResultId()).isNull();
    }
}
