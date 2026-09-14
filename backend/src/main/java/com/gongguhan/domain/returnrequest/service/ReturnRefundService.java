package com.gongguhan.domain.returnrequest.service;

import com.gongguhan.domain.returnrequest.dto.ReturnDetailResponse;
import com.gongguhan.domain.returnrequest.event.ReturnNeglectEvent;
import com.gongguhan.domain.returnrequest.port.RefundPort;
import com.gongguhan.domain.returnrequest.port.RefundPort.RefundResult;
import com.gongguhan.domain.returnrequest.service.ReturnRefundRecordService.Preparation;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/** 반품과 결제수단별 환불 어댑터를 연결하되 외부 호출 중 DB 트랜잭션을 열지 않는다. */
@Service
public class ReturnRefundService {

    private final ReturnRefundRecordService recordService;
    private final ReturnRequestService returnRequestService;
    private final List<RefundPort> refundPorts;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ReturnRefundService(ReturnRefundRecordService recordService,
                               ReturnRequestService returnRequestService,
                               List<RefundPort> refundPorts,
                               ApplicationEventPublisher events,
                               Clock clock) {
        this.recordService = recordService;
        this.returnRequestService = returnRequestService;
        this.refundPorts = refundPorts;
        this.events = events;
        this.clock = clock;
    }

    public ReturnDetailResponse pickupComplete(Long sellerId, Long returnId) {
        Preparation preparation = recordService.prepareSeller(sellerId, returnId);
        process(preparation);
        return returnRequestService.findSellerDetail(sellerId, returnId);
    }

    public void processAutomatic(Long returnId) {
        Preparation preparation = recordService.prepareAutomatic(returnId);
        if (!preparation.shouldInvoke()) {
            return;
        }
        events.publishEvent(new ReturnNeglectEvent(
                preparation.context().returnId(), preparation.context().sellerId(),
                preparation.context().participationId(), LocalDateTime.now(clock),
                ReturnNeglectEvent.TYPE));
        process(preparation);
    }

    private void process(Preparation preparation) {
        if (!preparation.shouldInvoke()) {
            return;
        }
        RefundPort port = refundPorts.stream()
                .filter(candidate -> candidate.supports(preparation.command().paymentMethod()))
                .findFirst()
                .orElse(null);
        if (port == null) {
            recordService.fail(preparation.context().returnId(),
                    "지원하는 환불 어댑터가 없습니다: " + preparation.command().paymentMethod());
            return;
        }
        try {
            RefundResult result = port.refund(preparation.command());
            if (result.status() == RefundResult.Status.SUCCEEDED) {
                recordService.complete(preparation.context().returnId(), result.providerRefundId());
            } else {
                recordService.fail(preparation.context().returnId(), result.failureReason());
            }
        } catch (BusinessException exception) {
            recordService.fail(preparation.context().returnId(), exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            recordService.fail(preparation.context().returnId(), "환불 어댑터 호출 중 오류가 발생했습니다.");
            throw new BusinessException(ErrorCode.EXTERNAL_PAYMENT_ERROR,
                    "환불 처리 중 오류가 발생했습니다.");
        }
    }
}
