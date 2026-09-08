package com.gongguhan.domain.product.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3ProductImageStorageTest {

    @Test
    void validPngIsUploadedWithContentTypeAndPublicUrl() throws IOException {
        S3Client s3Client = mock(S3Client.class);
        S3ProductImageStorage storage = storage(s3Client);

        ProductImageStorage.StoredImage stored = storage.store(new MockMultipartFile(
                "images", "image.png", MediaType.IMAGE_PNG_VALUE, pngBytes()));

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertEquals("gongguhan-test", captor.getValue().bucket());
        assertEquals(MediaType.IMAGE_PNG_VALUE, captor.getValue().contentType());
        assertTrue(captor.getValue().key().matches("products/[0-9a-f-]+\\.png"));
        assertEquals("https://cdn.example.com/" + captor.getValue().key(), stored.publicUrl());
    }

    @Test
    void deleteUsesProductsObjectKeyFromStoredUrl() {
        S3Client s3Client = mock(S3Client.class);
        S3ProductImageStorage storage = storage(s3Client);

        storage.deleteByUrl("https://cdn.example.com/products/image.jpg");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertEquals("gongguhan-test", captor.getValue().bucket());
        assertEquals("products/image.jpg", captor.getValue().key());
    }

    @Test
    void unsupportedContentTypeIsRejectedWithoutCallingS3() {
        S3Client s3Client = mock(S3Client.class);
        S3ProductImageStorage storage = storage(s3Client);

        BusinessException exception = assertThrows(BusinessException.class, () -> storage.store(
                new MockMultipartFile("images", "image.gif", "image/gif", new byte[] {1, 2, 3})));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    private S3ProductImageStorage storage(S3Client s3Client) {
        return new S3ProductImageStorage(
                s3Client, "gongguhan-test", "ap-northeast-2", "https://cdn.example.com/", 1024 * 1024);
    }

    private byte[] pngBytes() throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
