package com.gongguhan.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClientException;

class FastApiProductExtractionClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void postsRepresentativeImageAsMultipartAndMapsResponse() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/extract", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
            assertThat(exchange.getRequestHeaders().getFirst("Content-Type")).startsWith("multipart/form-data");
            assertThat(request).contains("name=\"images\"").contains("filename=\"main.jpg\"");
            respond(exchange, 200, """
                    {"status":"COMPLETED","fields":{"name":{"value":"키보드","confidence":0.95},
                    "category":{"value":"전자제품","confidence":0.88}},"warnings":[]}
                    """);
        });
        server.start();

        var client = client(1_000);
        var response = client.extract(new MockMultipartFile(
                "images", "main.jpg", "image/jpeg", new byte[] {1, 2, 3}));

        assertThat(response.fields().name().value()).isEqualTo("키보드");
        assertThat(response.fields().category().value()).isEqualTo("전자제품");
    }

    @Test
    void readTimeoutIsEnforced() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/extract", exchange -> {
            try {
                Thread.sleep(250);
                respond(exchange, 200, "{}");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();

        var client = client(50);

        assertThrows(RestClientException.class, () -> client.extract(new MockMultipartFile(
                "images", "main.jpg", "image/jpeg", new byte[] {1})));
    }

    private FastApiProductExtractionClient client(int timeoutMs) {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        return new FastApiProductExtractionClient(new AiExtractionProperties(baseUrl, "/extract", timeoutMs));
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
