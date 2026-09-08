package com.gongguhan.domain.product.service;

import org.springframework.web.multipart.MultipartFile;

public interface ProductImageStorage {

    StoredImage store(MultipartFile file);

    void deleteByUrl(String imageUrl);

    record StoredImage(String storedName, String publicUrl) {
    }

}
