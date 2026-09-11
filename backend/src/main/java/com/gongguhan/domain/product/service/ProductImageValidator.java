package com.gongguhan.domain.product.service;

import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

final class ProductImageValidator {

    private static final Map<String, String> EXTENSIONS = Map.of(
            MediaType.IMAGE_JPEG_VALUE, ".jpg",
            MediaType.IMAGE_PNG_VALUE, ".png");

    private ProductImageValidator() {
    }

    static String validate(MultipartFile file, long maxFileSize) {
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
}
