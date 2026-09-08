package com.gongguhan.domain.product.controller;

import com.gongguhan.domain.product.dto.CategoryResponse;
import com.gongguhan.domain.product.service.CategoryService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategories(HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                categoryService.getTree(), TraceIdUtils.from(httpRequest)));
    }
}
