package com.example.demo.service.evidence.hls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.config.HlsPackagingProperties;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.HlsStatus;
import com.example.demo.repository.EvidenceHlsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class EvidenceHlsSeedServiceTest {

    @Mock
    private EvidenceHlsRepository evidenceHlsRepository;

    @Mock
    private HlsPackagingEnqueuer hlsPackagingEnqueuer;

    @Mock
    private ObjectProvider<HlsPackagingEnqueuer> enqueuerProvider;

    private HlsPackagingProperties properties;
    private EvidenceHlsSeedService seedService;

    @BeforeEach
    void setUp() {
        properties = new HlsPackagingProperties();
        seedService = new EvidenceHlsSeedService(properties, evidenceHlsRepository, enqueuerProvider);
    }

    @Test
    void seedPendingAndEnqueue_videoEnabled_createsPendingAndEnqueues() {
        properties.setEnabled(true);
        when(evidenceHlsRepository.findByEvidenceId(42L)).thenReturn(java.util.Optional.empty());
        when(evidenceHlsRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(enqueuerProvider.getIfAvailable()).thenReturn(hlsPackagingEnqueuer);

        seedService.seedPendingAndEnqueue(42L, FileType.VIDEO);

        verify(evidenceHlsRepository).save(org.mockito.ArgumentMatchers.argThat(row ->
                row.getEvidenceId().equals(42L) && row.getHlsStatus() == HlsStatus.PENDING));
        verify(hlsPackagingEnqueuer).enqueue(42L);
    }

    @Test
    void seedPendingAndEnqueue_image_skips() {
        seedService.seedPendingAndEnqueue(42L, FileType.IMAGE);

        verify(evidenceHlsRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(enqueuerProvider, never()).getIfAvailable();
    }

    @Test
    void seedPendingAndEnqueue_disabled_seedsOnlyWithoutEnqueue() {
        properties.setEnabled(false);
        when(evidenceHlsRepository.findByEvidenceId(42L)).thenReturn(java.util.Optional.empty());
        when(evidenceHlsRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        seedService.seedPendingAndEnqueue(42L, FileType.VIDEO);

        verify(evidenceHlsRepository).save(org.mockito.ArgumentMatchers.any());
        verify(enqueuerProvider, never()).getIfAvailable();
    }
}
