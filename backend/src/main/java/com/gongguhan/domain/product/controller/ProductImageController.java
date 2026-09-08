package com.gongguhan.domain.product.controller;

import com.gongguhan.domain.product.service.ProductImageStorage;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/product-images")
public class ProductImageController {

    private final ProductImageStorage imageStorage;

    public ProductImageController(ProductImageStorage imageStorage) {
        this.imageStorage = imageStorage;
    }

    @GetMapping("/{storedName:.+}")
    public ResponseEntity<Resource> getImage(@PathVariable String storedName) {
        ProductImageStorage.LoadedImage image = imageStorage.load(storedName);
        return ResponseEntity.ok().contentType(image.mediaType()).body(image.resource());
    }
}
