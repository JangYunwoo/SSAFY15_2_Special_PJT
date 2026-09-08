package com.gongguhan.domain.product.service;

import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class S3ProductImageStorage implements ProductImageStorage {

    private static final Logger log = LoggerFactory.getLogger(S3ProductImageStorage.class);
    private static final String KEY_PREFIX = "products/";
    private static final Map<String, String> EXTENSIONS = Map.of(
            MediaType.IMAGE_JPEG_VALUE, ".jpg",
            MediaType.IMAGE_PNG_VALUE, ".png");

    private final S3Client s3Client;
    private final String bucket;
    private final String publicBaseUrl;
    private final long maxFileSize;

    public S3ProductImageStorage(
            S3Client s3Client,
            @Value("${app.storage.s3.bucket:}") String bucket,
            @Value("${app.storage.s3.region:ap-northeast-2}") String region,
            @Value("${app.storage.s3.public-base-url:}") String publicBaseUrl,
            @Value("${app.storage.product-image-max-bytes:10485760}") long maxFileSize) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl.isBlank()
                ? "https://" + bucket + ".s3." + region + ".amazonaws.com"
                : trimTrailingSlash(publicBaseUrl);
        this.maxFileSize = maxFileSize;
    }

    @Override
    public StoredImage store(MultipartFile file) {
        if (bucket.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "S3 버킷이 설정되지 않았습니다.");
        }
        String extension = validate(file);
        String key = KEY_PREFIX + UUID.randomUUID() + extension;
        try {
            byte[] bytes = file.getBytes();
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength((long) bytes.length)
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(bytes));
            return new StoredImage(key, publicBaseUrl + "/" + key);
        } catch (IOException | SdkException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "상품 이미지를 S3에 저장하지 못했습니다.");
        }
    }

    @Override
    public void deleteByUrl(String imageUrl) {
        String key = extractObjectKey(imageUrl);
        if (key == null || bucket.isBlank()) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException e) {
            log.warn("Failed to delete product image from S3: {}", key, e);
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

    private String extractObjectKey(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        try {
            String path = URI.create(imageUrl).getPath();
            int prefixIndex = path.indexOf("/" + KEY_PREFIX);
            return prefixIndex < 0 ? null : path.substring(prefixIndex + 1);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid product image URL: {}", imageUrl);
            return null;
        }
    }

    private static String trimTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
