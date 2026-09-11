package com.gongguhan.domain.product.repository;

import com.gongguhan.domain.product.entity.Product;
import com.gongguhan.domain.product.entity.RestrictionReviewStatus;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByProductIdAndSellerIdAndStatus(Long productId, Long sellerId, String status);

    Page<Product> findBySellerIdAndStatus(Long sellerId, String status, Pageable pageable);

    List<Product> findByWithdrawalRestrictedTrueAndRestrictionReviewStatusOrderByProductIdAsc(
            RestrictionReviewStatus status);

}
