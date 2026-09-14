package com.gongguhan.domain.product.repository;

import com.gongguhan.domain.product.entity.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByActiveTrueOrderByDepthAscSortOrderAscCategoryIdAsc();

    Optional<Category> findFirstByNameIgnoreCaseAndActiveTrue(String name);

    Optional<Category> findByCategoryIdAndActiveTrue(Long categoryId);
}
