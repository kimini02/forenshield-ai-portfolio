package com.example.demo.service.custody;

import com.example.demo.domain.enums.SecurityAlertCode;
import com.example.demo.service.custody.CustodyLogService.TargetChainVerifyResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustodyChainVerifierTest {

    @Mock
    private CustodyLogService custodyLogService;

    @InjectMocks
    private CustodyChainVerifier custodyChainVerifier;

    @Test
    @DisplayName("무결성 검증용 COC_CHAIN 항목을 생성한다")
    void toIntegrityCheckItem_invalidChain() {
        TargetChainVerifyResult result = new TargetChainVerifyResult(
                false, 2, 99L, "HASH_MISMATCH"
        );

        var item = custodyChainVerifier.toIntegrityCheckItem(result);

        assertThat(item.isValid()).isFalse();
        assertThat(item.getCheckType()).isEqualTo("COC_CHAIN");
        assertThat(item.getErrorCode()).isEqualTo(SecurityAlertCode.CHAIN_INTEGRITY_FAILED.name());
    }

    @Test
    @DisplayName("CoC API 응답을 생성한다")
    void toCocChainResponse_validChain() {
        var response = custodyChainVerifier.toCocChainResponse(
                1L,
                new TargetChainVerifyResult(true, 3, null, null)
        );

        assertThat(response.isValid()).isTrue();
        assertThat(response.getEvidenceId()).isEqualTo(1L);
        assertThat(response.getLogCount()).isEqualTo(3);
        assertThat(response.getMessage()).contains("성공");
    }

    @Test
    @DisplayName("증거 CoC 체인 검증을 위임한다")
    void verifyEvidenceChain_delegatesToCustodyLogService() {
        TargetChainVerifyResult expected = new TargetChainVerifyResult(true, 0, null, null);
        when(custodyLogService.verifyTargetChain(
                com.example.demo.domain.enums.CustodyTargetType.EVIDENCE, 42L
        )).thenReturn(expected);

        assertThat(custodyChainVerifier.verifyEvidenceChain(42L)).isEqualTo(expected);
        assertThat(custodyChainVerifier.isEvidenceChainValid(42L)).isTrue();
    }
}
