package com.gongguhan.domain.returnrequest.repository;

import com.gongguhan.domain.returnrequest.entity.ReturnRequestHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReturnRequestHistoryRepository extends JpaRepository<ReturnRequestHistory, Long> {

    List<ReturnRequestHistory> findByReturnRequestReturnIdOrderByOccurredAtAscHistoryIdAsc(
            Long returnId);
}
