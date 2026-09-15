package com.gongguhan.domain.payment.card.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * SSAFY 금융망 API 호출 이력. 기관거래고유번호({@code institutionTxNo})의 유일성을
 * 보장하고(H1007 방지), 성공·실패 관계없이 모든 호출을 남긴다.
 *
 * <p>{@code requestJson}에는 카드번호·CVC를 절대 담지 않는다 - 호출자가 그 필드를 뺀
 * 별도 스냅샷 객체를 직렬화해서 넘긴다.
 */
@Entity
@Table(name = "ssafy_api_logs")
public class SsafyApiLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @Column(name = "api_service_code", nullable = false, length = 30)
    private String apiServiceCode;

    @Column(name = "institution_tx_no", nullable = false, length = 20)
    private String institutionTxNo;

    @Column(name = "transmission_date", nullable = false, length = 8)
    private String transmissionDate;

    @Column(name = "target_type", length = 30)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "request_json", columnDefinition = "json")
    private String requestJson;

    @Column(name = "response_code", length = 10)
    private String responseCode;

    @Column(name = "response_message", length = 255)
    private String responseMessage;

    @Column(name = "response_json", columnDefinition = "json")
    private String responseJson;

    @Column(name = "called_at", nullable = false)
    private LocalDateTime calledAt;

    protected SsafyApiLog() {
    }

    private SsafyApiLog(String apiServiceCode, String institutionTxNo, String transmissionDate,
                         String targetType, Long targetId, String requestJson,
                         String responseCode, String responseMessage, String responseJson,
                         LocalDateTime calledAt) {
        this.apiServiceCode = apiServiceCode;
        this.institutionTxNo = institutionTxNo;
        this.transmissionDate = transmissionDate;
        this.targetType = targetType;
        this.targetId = targetId;
        this.requestJson = requestJson;
        this.responseCode = responseCode;
        this.responseMessage = responseMessage;
        this.responseJson = responseJson;
        this.calledAt = calledAt;
    }

    public static SsafyApiLog create(String apiServiceCode, String institutionTxNo, String transmissionDate,
                                      String targetType, Long targetId, String requestJson,
                                      String responseCode, String responseMessage, String responseJson,
                                      LocalDateTime calledAt) {
        return new SsafyApiLog(apiServiceCode, institutionTxNo, transmissionDate, targetType, targetId,
                requestJson, responseCode, responseMessage, responseJson, calledAt);
    }

    public Long getLogId() {
        return logId;
    }

    public String getApiServiceCode() {
        return apiServiceCode;
    }

    public String getInstitutionTxNo() {
        return institutionTxNo;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public String getResponseMessage() {
        return responseMessage;
    }

    public String getTransmissionDate() {
        return transmissionDate;
    }

    public String getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public LocalDateTime getCalledAt() {
        return calledAt;
    }
}
