package com.gongguhan.domain.product.repository;

import com.gongguhan.domain.product.entity.Product;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByProductIdAndSellerIdAndStatus(Long productId, Long sellerId, String status);

    Page<Product> findBySellerIdAndStatus(Long sellerId, String status, Pageable pageable);

    @Query(value = """
            SELECT COUNT(*)
            FROM seller_profiles
            WHERE seller_id = :sellerId AND status IN ('ACTIVE', 'WARNED')
            """, nativeQuery = true)
    long countUsableSeller(@Param("sellerId") Long sellerId);

    @Query(value = """
            SELECT COUNT(*)
            FROM group_buys
            WHERE product_id = :productId
              AND status IN ('RECRUITING', 'FULFILLED', 'SHIPPING', 'DELIVERED')
            """, nativeQuery = true)
    long countActiveGroupBuys(@Param("productId") Long productId);
}
