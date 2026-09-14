package com.gongguhan.domain.product.service;

import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Profile("local")
public class LocalProductImageStorage implements ProductImageStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalProductImageStorage.class);
    private static final String PUBLIC_PREFIX = "/api/v1/product-images/";

    private final LocalProductImagePaths paths;
    private final long maxFileSize;

    public LocalProductImageStorage(
            LocalProductImagePaths paths,
            @Value("${app.storage.product-image-max-bytes:10485760}") long maxFileSize) {
        this.paths = paths;
        this.maxFileSize = maxFileSize;
    }

    @Override
    public StoredImage store(MultipartFile file) {
        String extension = ProductImageValidator.validate(file, maxFileSize);
        String storedName = UUID.randomUUID() + extension;
        Path target = paths.resolve(storedName);
        try {
            Files.createDirectories(paths.root());
            Files.write(target, file.getBytes(), StandardOpenOption.CREATE_NEW);
            return new StoredImage(storedName, PUBLIC_PREFIX + storedName);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "상품 이미지를 로컬 저장소에 저장하지 못했습니다.");
        }
    }

    @Override
    public void deleteByUrl(String imageUrl) {
        String storedName = extractStoredName(imageUrl);
        if (storedName == null) {
            return;
        }
        try {
            Files.deleteIfExists(paths.resolve(storedName));
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Failed to delete local product image: {}", storedName, e);
        }
    }

    private String extractStoredName(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        try {
            String path = URI.create(imageUrl).getPath();
            int prefixIndex = path.indexOf(PUBLIC_PREFIX);
            return prefixIndex < 0 ? null : path.substring(prefixIndex + PUBLIC_PREFIX.length());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
