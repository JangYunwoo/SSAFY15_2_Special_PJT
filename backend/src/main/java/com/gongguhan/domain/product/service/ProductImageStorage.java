package com.gongguhan.domain.product.service;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

public interface ProductImageStorage {

    StoredImage store(MultipartFile file);

    LoadedImage load(String storedName);

    void deleteByUrl(String imageUrl);

    record StoredImage(String storedName, String publicUrl) {
    }

    record LoadedImage(Resource resource, MediaType mediaType) {
    }
}
