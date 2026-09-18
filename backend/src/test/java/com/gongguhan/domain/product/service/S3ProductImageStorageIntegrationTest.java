package com.gongguhan.domain.product.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gongguhan.global.config.S3Config;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

class S3ProductImageStorageIntegrationTest {

    private static final String BUCKET = "gongguhan-local";
    private static final String REGION = "ap-northeast-2";
    private static final String ENDPOINT = "http://127.0.0.1:9000";
    private static final String PUBLIC_BASE_URL = ENDPOINT + "/" + BUCKET;

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_S3_INTEGRATION_TEST", matches = "true")
    void storesServesAndDeletesImageThroughMinio() throws Exception {
        byte[] imageBytes = pngBytes();

        try (S3Client s3Client = new S3Config().s3Client(REGION, ENDPOINT)) {
            S3ProductImageStorage storage = new S3ProductImageStorage(
                    s3Client, BUCKET, REGION, PUBLIC_BASE_URL, 1024 * 1024);
            ProductImageStorage.StoredImage stored = storage.store(new MockMultipartFile(
                    "images", "integration.png", MediaType.IMAGE_PNG_VALUE, imageBytes));

            try {
                assertTrue(stored.storedName().matches("products/[0-9a-f-]+\\.png"));
                assertEquals(PUBLIC_BASE_URL + "/" + stored.storedName(), stored.publicUrl());
                assertEquals(MediaType.IMAGE_PNG_VALUE, s3Client.headObject(HeadObjectRequest.builder()
                        .bucket(BUCKET)
                        .key(stored.storedName())
                        .build()).contentType());

                HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
                        HttpRequest.newBuilder(URI.create(stored.publicUrl())).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                assertEquals(200, response.statusCode());
                assertArrayEquals(imageBytes, response.body());
            } finally {
                storage.deleteByUrl(stored.publicUrl());
            }

            S3Exception missing = assertThrows(S3Exception.class, () -> s3Client.headObject(
                    HeadObjectRequest.builder().bucket(BUCKET).key(stored.storedName()).build()));
            assertEquals(404, missing.statusCode());
        }
    }

    private byte[] pngBytes() throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
