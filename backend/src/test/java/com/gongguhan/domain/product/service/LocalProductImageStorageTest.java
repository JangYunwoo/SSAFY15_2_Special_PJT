package com.gongguhan.domain.product.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

class LocalProductImageStorageTest {

    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void validPngCanBeStoredLoadedAndDeleted() throws IOException {
        LocalProductImageStorage storage = new LocalProductImageStorage(tempDir.toString(), 1024 * 1024);

        ProductImageStorage.StoredImage stored = storage.store(new MockMultipartFile(
                "images", "image.png", MediaType.IMAGE_PNG_VALUE, pngBytes()));

        ProductImageStorage.LoadedImage loaded = storage.load(stored.storedName());
        assertTrue(loaded.resource().exists());
        assertEquals(MediaType.IMAGE_PNG, loaded.mediaType());

        storage.deleteByUrl(stored.publicUrl());
        BusinessException exception = assertThrows(BusinessException.class, () -> storage.load(stored.storedName()));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void unsupportedContentTypeIsRejected() {
        LocalProductImageStorage storage = new LocalProductImageStorage(tempDir.toString(), 1024);

        BusinessException exception = assertThrows(BusinessException.class, () -> storage.store(
                new MockMultipartFile("images", "image.gif", "image/gif", new byte[] {1, 2, 3})));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
    }

    private byte[] pngBytes() throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
