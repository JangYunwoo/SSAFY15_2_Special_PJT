package com.gongguhan.domain.returnrequest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "return_request_histories")
public class ReturnRequestHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_id", nullable = false)
    private ReturnRequest returnRequest;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 500)
    private String note;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    protected ReturnRequestHistory() {
    }

    public static ReturnRequestHistory record(ReturnRequest returnRequest, String status,
                                               String note, LocalDateTime occurredAt) {
        ReturnRequestHistory history = new ReturnRequestHistory();
        history.returnRequest = returnRequest;
        history.status = status;
        history.note = note;
        history.occurredAt = occurredAt;
        return history;
    }

    public String getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}
