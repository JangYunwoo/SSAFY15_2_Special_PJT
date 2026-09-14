package com.gongguhan.domain.product.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.product.dto.CategoryResponse;
import com.gongguhan.domain.product.entity.Category;
import com.gongguhan.domain.product.repository.CategoryRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class CategoryServiceTest {

    @Test
    void categoriesAreReturnedAsTree() {
        CategoryRepository repository = mock(CategoryRepository.class);
        Category root = category(1L, null, "가전", 1);
        Category laptop = category(2L, 1L, "노트북", 2);
        Category monitor = category(3L, 1L, "모니터", 2);
        when(repository.findAllByActiveTrueOrderByDepthAscSortOrderAscCategoryIdAsc()).thenReturn(List.of(
                root, laptop, monitor));

        List<CategoryResponse> result = new CategoryService(repository).getTree();

        assertEquals(1, result.size());
        assertEquals("가전", result.getFirst().name());
        assertEquals(List.of("노트북", "모니터"),
                result.getFirst().children().stream().map(CategoryResponse::name).toList());
    }

    private Category category(Long id, Long parentId, String name, int depth) {
        Category category = mock(Category.class);
        when(category.getCategoryId()).thenReturn(id);
        when(category.getParentId()).thenReturn(parentId);
        when(category.getName()).thenReturn(name);
        when(category.getDepth()).thenReturn(depth);
        return category;
    }
}
