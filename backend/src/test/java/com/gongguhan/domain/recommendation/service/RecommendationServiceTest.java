package com.gongguhan.domain.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.groupbuy.dto.GroupBuySummaryResponse;
import com.gongguhan.domain.recommendation.dto.RecommendationResponse;
import com.gongguhan.domain.recommendation.repository.RecommendationCandidate;
import com.gongguhan.domain.recommendation.repository.RecommendationRepository;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

class RecommendationServiceTest {
    private final RecommendationRepository repository = mock(RecommendationRepository.class);
    private final AiRecommendationClient ai = mock(AiRecommendationClient.class);
    private final RecommendationService service = new RecommendationService(repository, ai,
            new RecommendationProperties("http://localhost", "/rank", 1000, 100, 100, false));
    private final GroupBuySummaryResponse first = mock(GroupBuySummaryResponse.class);
    private final GroupBuySummaryResponse second = mock(GroupBuySummaryResponse.class);

    @BeforeEach
    void candidates() {
        when(repository.findCandidates(100)).thenReturn(List.of(
                new RecommendationCandidate(1, 11, null, 1L, .2, .1, .1, first),
                new RecommendationCandidate(2, 12, null, 1L, .8, .1, .5, second)));
        when(repository.findTrainingEvents(100)).thenReturn(List.of());
    }

    private RecommendationResponse rank(List<AiRecommendationClient.RankedItem> items, int size) {
        when(ai.rank(eq(10L), anyList(), anyList(), anyInt())).thenReturn(
                new AiRecommendationClient.RankResponse("HEURISTIC_FALLBACK", items, Map.of()));
        return service.recommend(10, size);
    }

    private AiRecommendationClient.RankedItem item(long id, double score) {
        return new AiRecommendationClient.RankedItem(id, score, "추천 이유");
    }

    @Test void validRankingOrderIsPreserved() {
        var response = rank(List.of(item(1, .9), item(2, .8)), 2);
        assertThat(response.items()).extracting(i -> i.groupBuy()).containsExactly(first, second);
        assertThat(response.modelType()).isEqualTo("HEURISTIC_FALLBACK");
    }

    @Test void nullItemDoesNotTurnRecommendationInto500() {
        var response = rank(Arrays.asList(null, item(1, .9)), 2);
        assertThat(response.items()).extracting(i -> i.groupBuy()).containsExactly(first);
    }

    @Test void duplicateIdsAreReturnedOnlyOnce() {
        var response = rank(List.of(item(1, .9), item(1, .8), item(2, .7)), 2);
        assertThat(response.items()).extracting(i -> i.groupBuy()).containsExactly(first, second);
    }

    @Test void requestedSizeIsEnforcedEvenIfAiIgnoresIt() {
        assertThat(rank(List.of(item(1, .9), item(2, .8)), 1).items()).hasSize(1);
    }

    @Test void unknownCandidatesDoNotCreateAnEmptyRecommendation() {
        var response = rank(List.of(item(999, .9)), 2);
        assertThat(response.modelType()).isEqualTo("BACKEND_FALLBACK");
        assertThat(response.items()).extracting(i -> i.groupBuy()).containsExactly(second, first);
    }

    @Test void emptyAiRankingUsesFallbackWhenCandidatesExist() {
        assertThat(rank(List.of(), 1).modelType()).isEqualTo("BACKEND_FALLBACK");
    }

    @Test void nonFiniteScoresAreRejected() {
        assertThat(rank(List.of(item(1, Double.NaN), item(2, Double.POSITIVE_INFINITY)), 2).modelType())
                .isEqualTo("BACKEND_FALLBACK");
    }

    @Test void emptyReasonGetsReadableDefault() {
        var response = rank(List.of(new AiRecommendationClient.RankedItem(1, .9, null)), 1);
        assertThat(response.items().getFirst().reason()).isNotBlank();
    }

    @Test void missingScoreIsNotSilentlyTreatedAsZero() {
        assertThat(rank(List.of(new AiRecommendationClient.RankedItem(1, null, "추천")), 1).modelType())
                .isEqualTo("BACKEND_FALLBACK");
    }

    @Test void invalidDuplicateDoesNotHideLaterValidItem() {
        var response = rank(List.of(item(1, Double.NaN), item(1, .9)), 1);
        assertThat(response.items()).extracting(i -> i.groupBuy()).containsExactly(first);
        assertThat(response.items().getFirst().score()).isEqualTo(.9);
    }

    @Test void unknownModelTypeUsesFallbackInsteadOfBreakingFrontendContract() {
        when(ai.rank(eq(10L), anyList(), anyList(), anyInt())).thenReturn(
                new AiRecommendationClient.RankResponse("UNKNOWN", List.of(item(1, .9)), Map.of()));
        assertThat(service.recommend(10, 1).modelType()).isEqualTo("BACKEND_FALLBACK");
    }

    @Test void lightGbmRankingIsAcceptedByFrontendContract() {
        when(ai.rank(eq(10L), anyList(), anyList(), anyInt())).thenReturn(
                new AiRecommendationClient.RankResponse("LIGHTGBM_RANKER", List.of(item(1, .9)), Map.of()));
        assertThat(service.recommend(10, 1).modelType()).isEqualTo("LIGHTGBM_RANKER");
    }

    @Test void invalidFeatureWeightsAreNotReturnedAsJson() {
        Map<String, Double> weights = new HashMap<>();
        weights.put("valid", .5); weights.put("nan", Double.NaN); weights.put("missing", null);
        when(ai.rank(eq(10L), anyList(), anyList(), anyInt())).thenReturn(
                new AiRecommendationClient.RankResponse("HEURISTIC_FALLBACK", List.of(item(1, .9)), weights));
        assertThat(service.recommend(10, 1).featureWeights()).containsExactlyEntriesOf(Map.of("valid", .5));
    }

    @Test void transportErrorUsesExistingFallback() {
        when(ai.rank(eq(10L), anyList(), anyList(), anyInt())).thenThrow(new RestClientException("test"));
        assertThat(service.recommend(10, 1).modelType()).isEqualTo("BACKEND_FALLBACK");
    }

    @Test void noCandidatesReturnsEmptyWithoutInventedRecommendations() {
        when(repository.findCandidates(100)).thenReturn(List.of());
        var response = service.recommend(10, 2);
        assertThat(response.items()).isEmpty();
        assertThat(response.modelType()).isEqualTo("EMPTY");
    }
}
