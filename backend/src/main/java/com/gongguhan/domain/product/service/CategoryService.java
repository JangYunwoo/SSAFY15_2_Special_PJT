package com.gongguhan.domain.product.service;

import com.gongguhan.domain.product.dto.CategoryResponse;
import com.gongguhan.domain.product.entity.Category;
import com.gongguhan.domain.product.repository.CategoryRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<CategoryResponse> getTree() {
        List<Category> categories =
                categoryRepository.findAllByActiveTrueOrderByDepthAscSortOrderAscCategoryIdAsc();
        Map<Long, List<Category>> childrenByParent = categories.stream()
                .filter(category -> category.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId));

        return categories.stream()
                .filter(category -> category.getParentId() == null)
                .map(category -> toResponse(category, childrenByParent))
                .toList();
    }

    private CategoryResponse toResponse(Category category, Map<Long, List<Category>> childrenByParent) {
        List<CategoryResponse> children = childrenByParent
                .getOrDefault(category.getCategoryId(), List.of())
                .stream()
                .map(child -> toResponse(child, childrenByParent))
                .toList();
        return new CategoryResponse(
                String.valueOf(category.getCategoryId()), category.getName(), category.getDepth(), children);
    }
}
