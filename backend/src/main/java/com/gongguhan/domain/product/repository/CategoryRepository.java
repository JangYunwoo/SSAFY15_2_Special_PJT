package com.gongguhan.domain.product.repository;

import com.gongguhan.domain.product.entity.Category;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByOrderByDepthAscSortOrderAscCategoryIdAsc();
}
