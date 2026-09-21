package com.gongguhan.domain.recommendation.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gongguhan.domain.recommendation.repository.RecommendationCandidate;
import com.gongguhan.domain.recommendation.repository.RecommendationTrainingEvent;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AiRecommendationClient {

    private final RestClient restClient;
    private final String path;

    public AiRecommendationClient(RecommendationProperties properties) {
        Duration timeout = Duration.ofMillis(properties.timeoutMs());
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(timeout)
                        .build());
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        this.path = properties.path();
    }

    public RankResponse rank(
            long targetUserId,
            List<RecommendationCandidate> candidates,
            List<RecommendationTrainingEvent> events,
            int limit) {
        RankRequest request = new RankRequest(
                targetUserId,
                candidates.stream().map(Candidate::from).toList(),
                events.stream().map(Event::from).toList(),
                limit);
        return restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(RankResponse.class);
    }

    private record RankRequest(
            @JsonProperty("target_user_id") long targetUserId,
            List<Candidate> candidates,
            List<Event> events,
            int limit
    ) {
    }

    private record Candidate(
            @JsonProperty("group_buy_id") long groupBuyId,
            @JsonProperty("product_id") long productId,
            String brand,
            @JsonProperty("category_id") Long categoryId,
            @JsonProperty("recruitment_rate") double recruitmentRate,
            @JsonProperty("discount_rate") double discountRate,
            @JsonProperty("deadline_urgency") double deadlineUrgency
    ) {
        private static Candidate from(RecommendationCandidate source) {
            return new Candidate(
                    source.groupBuyId(), source.productId(), source.brand(), source.categoryId(),
                    source.recruitmentRate(), source.discountRate(), source.deadlineUrgency());
        }
    }

    private record Event(
            @JsonProperty("user_id") long userId,
            @JsonProperty("group_buy_id") long groupBuyId,
            @JsonProperty("product_id") long productId,
            @JsonProperty("event_type") String eventType,
            @JsonProperty("occurred_at") OffsetDateTime occurredAt,
            String brand,
            @JsonProperty("category_id") Long categoryId,
            @JsonProperty("recruitment_rate") double recruitmentRate,
            @JsonProperty("discount_rate") double discountRate,
            @JsonProperty("deadline_urgency") double deadlineUrgency
    ) {
        private static Event from(RecommendationTrainingEvent source) {
            return new Event(
                    source.userId(), source.groupBuyId(), source.productId(),
                    source.eventType(), source.occurredAt(),
                    source.brand(), source.categoryId(), source.recruitmentRate(),
                    source.discountRate(), source.deadlineUrgency());
        }
    }

    public record RankResponse(
            @JsonProperty("model_type") String modelType,
            @JsonProperty("ranked_items") List<RankedItem> rankedItems,
            @JsonProperty("feature_weights") Map<String, Double> featureWeights
    ) {
    }

    public record RankedItem(
            @JsonProperty("group_buy_id") long groupBuyId,
            double score,
            String reason
    ) {
    }
}
