package com.gongguhan.domain.product.service;

import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalProductImageStorage implements ProductImageStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalProductImageStorage.class);
    private static final Map<String, String> EXTENSIONS = Map.of(
            MediaType.IMAGE_JPEG_VALUE, ".jpg",
            MediaType.IMAGE_PNG_VALUE, ".png");

    private final Path root;
    private final long maxFileSize;

    public LocalProductImageStorage(
            @Value("${app.storage.product-image-dir:uploads/products}") String root,
            @Value("${app.storage.product-image-max-bytes:10485760}") long maxFileSize) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.maxFileSize = maxFileSize;
    }

    @Override
    public StoredImage store(MultipartFile file) {
        String extension = validate(file);
        String storedName = UUID.randomUUID() + extension;
        Path target = resolve(storedName);
        try {
            Files.createDirectories(root);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredImage(storedName, "/api/v1/product-images/" + storedName);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "상품 이미지를 저장하지 못했습니다.");
        }
    }

    @Override
    public LoadedImage load(String storedName) {
        Path path = resolve(storedName);
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        try {
            Resource resource = new UrlResource(path.toUri());
            MediaType mediaType = storedName.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
            return new LoadedImage(resource, mediaType);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Override
    public void deleteByUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        String storedName = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
        try {
            Files.deleteIfExists(resolve(storedName));
        } catch (IOException e) {
            log.warn("Failed to delete product image: {}", storedName, e);
        }
    }

    private String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "빈 이미지는 업로드할 수 없습니다.");
        }
        if (file.getSize() > maxFileSize) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "이미지 파일 크기 제한을 초과했습니다.");
        }
        String extension = EXTENSIONS.get(file.getContentType());
        if (extension == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "JPG 또는 PNG 이미지만 업로드할 수 있습니다.");
        }
        try (InputStream input = file.getInputStream()) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "손상되었거나 올바르지 않은 이미지입니다.");
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "이미지 파일을 읽을 수 없습니다.");
        }
        return extension;
    }

    private Path resolve(String storedName) {
        Path resolved = root.resolve(storedName).normalize();
        if (!resolved.startsWith(root) || !storedName.equals(Path.of(storedName).getFileName().toString())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return resolved;
    }
}
