package com.gongguhan.domain.product.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

class LocalProductImageStorageTest {

    @TempDir
    Path tempDirectory;

    @Test
    void storesAndDeletesValidatedImageInsideConfiguredDirectory() throws IOException {
        LocalProductImageStorage storage = new LocalProductImageStorage(
                new LocalProductImagePaths(tempDirectory.toString()), 1024 * 1024);

        ProductImageStorage.StoredImage stored = storage.store(new MockMultipartFile(
                "images", "image.png", MediaType.IMAGE_PNG_VALUE, pngBytes()));
        Path storedFile = tempDirectory.resolve(stored.storedName());

        assertTrue(Files.isRegularFile(storedFile));
        assertEquals("/api/v1/product-images/" + stored.storedName(), stored.publicUrl());

        storage.deleteByUrl(stored.publicUrl());

        assertFalse(Files.exists(storedFile));
    }

    private byte[] pngBytes() throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
