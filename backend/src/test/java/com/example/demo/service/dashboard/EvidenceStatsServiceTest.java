package com.example.demo.service.dashboard;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.demo.dto.EvidenceStatsResponse;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.DashboardStatsProjection;
import com.example.demo.repository.EvidenceRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class EvidenceStatsServiceTest {

    @Test
    void cacheMissLoadsOneAggregateProjectionAndCachesResponse() {
        AnalysisRequestRepository requestRepository = mock(AnalysisRequestRepository.class);
        DashboardStatsCache cache = mock(DashboardStatsCache.class);
        DashboardStatsProjection projection = projection(90_000L, 5_000L, 7_500L, 15_000L);
        EvidenceStatsService service = service(requestRepository, cache);

        when(cache.get(1L)).thenReturn(null);
        when(requestRepository.findDashboardStatsByUploader(1L)).thenReturn(projection);

        EvidenceStatsResponse response = service.getDashboardStats(1L);

        assertStats(response, 90_000L, 5_000L, 7_500L, 15_000L);
        verify(requestRepository).findDashboardStatsByUploader(1L);
        verify(cache).put(1L, response);
        verifyNoMoreInteractions(requestRepository);
    }

    @Test
    void sameKeyConcurrentMissSharesOneAggregateResult() throws Exception {
        int callers = 5;
        AnalysisRequestRepository requestRepository = mock(AnalysisRequestRepository.class);
        DashboardStatsCache cache = mock(DashboardStatsCache.class);
        DashboardStatsProjection projection = projection(90_000L, 5_000L, 7_500L, 15_000L);
        EvidenceStatsService service = service(requestRepository, cache);
        CountDownLatch allCacheReads = new CountDownLatch(callers + 1);
        CountDownLatch queryEntered = new CountDownLatch(1);
        CountDownLatch releaseQuery = new CountDownLatch(1);

        when(cache.get(1L)).thenAnswer(invocation -> {
            allCacheReads.countDown();
            return null;
        });
        when(requestRepository.findDashboardStatsByUploader(1L)).thenAnswer(invocation -> {
            queryEntered.countDown();
            assertThat(releaseQuery.await(5, SECONDS)).isTrue();
            return projection;
        });

        ExecutorService executor = Executors.newFixedThreadPool(callers);
        try {
            CyclicBarrier start = new CyclicBarrier(callers);
            List<Future<EvidenceStatsResponse>> futures = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return service.getDashboardStats(1L);
                }));
            }

            assertThat(queryEntered.await(5, SECONDS)).isTrue();
            assertThat(allCacheReads.await(5, SECONDS)).isTrue();
            releaseQuery.countDown();

            List<EvidenceStatsResponse> responses = new ArrayList<>();
            for (Future<EvidenceStatsResponse> future : futures) {
                responses.add(future.get(5, SECONDS));
            }
            assertThat(responses).allSatisfy(response -> {
                assertThat(response).isSameAs(responses.get(0));
                assertStats(response, 90_000L, 5_000L, 7_500L, 15_000L);
            });
            verify(requestRepository).findDashboardStatsByUploader(1L);
            verify(cache).put(1L, responses.get(0));
        } finally {
            releaseQuery.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void differentKeysComputeConcurrently() throws Exception {
        AnalysisRequestRepository requestRepository = mock(AnalysisRequestRepository.class);
        DashboardStatsCache cache = mock(DashboardStatsCache.class);
        EvidenceStatsService service = service(requestRepository, cache);
        CountDownLatch bothQueriesEntered = new CountDownLatch(2);
        CountDownLatch releaseQueries = new CountDownLatch(1);

        when(cache.get(anyLong())).thenReturn(null);
        when(requestRepository.findDashboardStatsByUploader(anyLong())).thenAnswer(invocation -> {
            Long uploaderId = invocation.getArgument(0, Long.class);
            bothQueriesEntered.countDown();
            assertThat(releaseQueries.await(5, SECONDS)).isTrue();
            return projection(uploaderId * 10, uploaderId, uploaderId * 2, uploaderId * 3);
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier start = new CyclicBarrier(2);
            Future<EvidenceStatsResponse> first = executor.submit(() -> {
                start.await();
                return service.getDashboardStats(1L);
            });
            Future<EvidenceStatsResponse> second = executor.submit(() -> {
                start.await();
                return service.getDashboardStats(2L);
            });

            assertThat(bothQueriesEntered.await(5, SECONDS)).isTrue();
            releaseQueries.countDown();

            assertStats(first.get(5, SECONDS), 10L, 1L, 2L, 3L);
            assertStats(second.get(5, SECONDS), 20L, 2L, 4L, 6L);
            verify(requestRepository).findDashboardStatsByUploader(1L);
            verify(requestRepository).findDashboardStatsByUploader(2L);
        } finally {
            releaseQueries.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void warmCacheBypassesSingleFlightAndRepository() {
        AnalysisRequestRepository requestRepository = mock(AnalysisRequestRepository.class);
        DashboardStatsCache cache = mock(DashboardStatsCache.class);
        EvidenceStatsService service = service(requestRepository, cache);
        EvidenceStatsResponse cached = EvidenceStatsResponse.builder()
                .totalAnalysisCount(10L)
                .deepfakeDetectedCount(1L)
                .completedCount(2L)
                .inProgressCount(3L)
                .build();
        when(cache.get(1L)).thenReturn(cached);

        EvidenceStatsResponse response = service.getDashboardStats(1L);

        assertThat(response).isSameAs(cached);
        verifyNoInteractions(requestRepository);
    }

    @Test
    void failedFlightPropagatesAndNextRequestRetries() throws Exception {
        int callers = 3;
        AnalysisRequestRepository requestRepository = mock(AnalysisRequestRepository.class);
        DashboardStatsCache cache = mock(DashboardStatsCache.class);
        EvidenceStatsService service = service(requestRepository, cache);
        DashboardStatsProjection retryProjection = projection(10L, 1L, 2L, 3L);
        IllegalStateException failure = new IllegalStateException("aggregate failed");
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch allCacheReads = new CountDownLatch(callers + 1);
        CountDownLatch queryEntered = new CountDownLatch(1);
        CountDownLatch releaseFailure = new CountDownLatch(1);

        when(cache.get(1L)).thenAnswer(invocation -> {
            allCacheReads.countDown();
            return null;
        });
        when(requestRepository.findDashboardStatsByUploader(1L)).thenAnswer(invocation -> {
            if (attempts.incrementAndGet() == 1) {
                queryEntered.countDown();
                assertThat(releaseFailure.await(5, SECONDS)).isTrue();
                throw failure;
            }
            return retryProjection;
        });

        ExecutorService executor = Executors.newFixedThreadPool(callers);
        try {
            CyclicBarrier start = new CyclicBarrier(callers);
            List<Future<EvidenceStatsResponse>> futures = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return service.getDashboardStats(1L);
                }));
            }

            assertThat(queryEntered.await(5, SECONDS)).isTrue();
            assertThat(allCacheReads.await(5, SECONDS)).isTrue();
            releaseFailure.countDown();

            for (Future<EvidenceStatsResponse> future : futures) {
                assertThatThrownBy(() -> future.get(5, SECONDS))
                        .isInstanceOfSatisfying(ExecutionException.class, exception ->
                                assertThat(exception.getCause()).isSameAs(failure));
            }

            EvidenceStatsResponse retried = service.getDashboardStats(1L);
            assertStats(retried, 10L, 1L, 2L, 3L);
            verify(requestRepository, times(2)).findDashboardStatsByUploader(1L);
        } finally {
            releaseFailure.countDown();
            executor.shutdownNow();
        }
    }

    private EvidenceStatsService service(
            AnalysisRequestRepository requestRepository,
            DashboardStatsCache cache
    ) {
        return new EvidenceStatsService(
                requestRepository,
                mock(AnalysisResultRepository.class),
                mock(EvidenceRepository.class),
                cache
        );
    }

    private DashboardStatsProjection projection(
            long total,
            long deepfake,
            long completed,
            long inProgress
    ) {
        DashboardStatsProjection projection = mock(DashboardStatsProjection.class);
        when(projection.getTotalAnalysisCount()).thenReturn(total);
        when(projection.getDeepfakeDetectedCount()).thenReturn(deepfake);
        when(projection.getCompletedCount()).thenReturn(completed);
        when(projection.getInProgressCount()).thenReturn(inProgress);
        return projection;
    }

    private void assertStats(
            EvidenceStatsResponse response,
            long total,
            long deepfake,
            long completed,
            long inProgress
    ) {
        assertThat(response.getTotalAnalysisCount()).isEqualTo(total);
        assertThat(response.getDeepfakeDetectedCount()).isEqualTo(deepfake);
        assertThat(response.getCompletedCount()).isEqualTo(completed);
        assertThat(response.getInProgressCount()).isEqualTo(inProgress);
    }
}
