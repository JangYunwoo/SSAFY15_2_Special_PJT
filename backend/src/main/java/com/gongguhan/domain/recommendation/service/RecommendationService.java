package com.gongguhan.domain.recommendation.service;

import com.gongguhan.domain.recommendation.dto.RecommendationEventItem;
import com.gongguhan.domain.recommendation.dto.RecommendationEventRequest;
import com.gongguhan.domain.recommendation.dto.RecommendationResponse;
import com.gongguhan.domain.recommendation.dto.RecommendedGroupBuyResponse;
import com.gongguhan.domain.recommendation.repository.RecommendationCandidate;
import com.gongguhan.domain.recommendation.repository.RecommendationRepository;
import com.gongguhan.domain.recommendation.repository.RecommendationTrainingEvent;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final Set<String> EVENT_TYPES = Set.of("IMPRESSION", "CLICK", "DETAIL_VIEW");

    private final RecommendationRepository repository;
    private final AiRecommendationClient aiClient;
    private final RecommendationProperties properties;

    public RecommendationService(
            RecommendationRepository repository,
            AiRecommendationClient aiClient,
            RecommendationProperties properties) {
        this.repository = repository;
        this.aiClient = aiClient;
        this.properties = properties;
    }

    @Transactional
    public void recordEvents(long userId, RecommendationEventRequest request) {
        String sessionId = normalizedSessionId(request.sessionId());
        repository.findSessionOwner(sessionId).ifPresent(ownerId -> {
            if (ownerId != userId) {
                throw new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED);
            }
        });
        List<RecommendationEventItem> events = request.events().stream()
                .map(this::normalizeEvent)
                .toList();
        Set<Long> ids = events.stream().map(RecommendationEventItem::groupBuyId).collect(Collectors.toSet());
        Map<Long, RecommendationCandidate> snapshots = repository.findCandidateSnapshots(ids);
        if (snapshots.size() != ids.size()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "추천 행동의 공동구매를 찾을 수 없습니다.");
        }
        repository.saveEvents(userId, sessionId, events, snapshots);
    }

    @Transactional
    public void endSession(long userId, String rawSessionId) {
        repository.endSession(userId, normalizedSessionId(rawSessionId));
    }

    @Transactional(readOnly = true)
    public RecommendationResponse recommend(long userId, int limit) {
        if (limit < 1 || limit > 20) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "size는 1 이상 20 이하여야 합니다.");
        }
        List<RecommendationCandidate> candidates = repository.findCandidates(properties.maxCandidates());
        if (candidates.isEmpty()) {
            return new RecommendationResponse(List.of(), "EMPTY", Map.of());
        }
        List<RecommendationTrainingEvent> events = repository.findTrainingEvents(properties.trainingEventLimit());
        try {
            AiRecommendationClient.RankResponse ranked = aiClient.rank(userId, candidates, events, limit);
            if (ranked == null || ranked.rankedItems() == null) {
                return fallback(candidates, limit);
            }
            Map<Long, RecommendationCandidate> byId = candidates.stream()
                    .collect(Collectors.toMap(RecommendationCandidate::groupBuyId, Function.identity()));
            List<RecommendedGroupBuyResponse> items = ranked.rankedItems().stream()
                    .filter(item -> byId.containsKey(item.groupBuyId()))
                    .map(item -> new RecommendedGroupBuyResponse(
                            byId.get(item.groupBuyId()).summary(), item.score(), item.reason()))
                    .toList();
            return new RecommendationResponse(
                    items,
                    ranked.modelType(),
                    ranked.featureWeights() == null ? Map.of() : ranked.featureWeights());
        } catch (RestClientException error) {
            log.warn("추천 AI 호출 실패. 모집률 기반 순서로 대체합니다: {}", error.getClass().getSimpleName());
            return fallback(candidates, limit);
        }
    }

    private RecommendationResponse fallback(List<RecommendationCandidate> candidates, int limit) {
        List<RecommendedGroupBuyResponse> items = candidates.stream()
                .sorted(Comparator
                        .comparingDouble(RecommendationCandidate::recruitmentRate).reversed()
                        .thenComparing(Comparator.comparingDouble(
                                RecommendationCandidate::deadlineUrgency).reversed()))
                .limit(limit)
                .map(candidate -> new RecommendedGroupBuyResponse(
                        candidate.summary(),
                        candidate.recruitmentRate(),
                        "현재 참여가 활발해요"))
                .toList();
        return new RecommendationResponse(items, "BACKEND_FALLBACK", Map.of());
    }

    private RecommendationEventItem normalizeEvent(RecommendationEventItem event) {
        String type = event.eventType().trim().toUpperCase(Locale.ROOT);
        if (!EVENT_TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "지원하지 않는 추천 행동 유형입니다.");
        }
        String source = event.source() == null || event.source().isBlank()
                ? "UNKNOWN"
                : event.source().trim().toUpperCase(Locale.ROOT);
        return new RecommendationEventItem(event.groupBuyId(), type, source, event.position());
    }

    private String normalizedSessionId(String value) {
        try {
            return UUID.fromString(value.trim()).toString();
        } catch (IllegalArgumentException error) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "sessionId는 UUID 형식이어야 합니다.");
        }
    }
}
