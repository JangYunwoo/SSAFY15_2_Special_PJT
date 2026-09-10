package com.gongguhan.domain.product;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AiExtractionIntegrationTest {

    private static HttpServer aiServer;

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void aiProperties(DynamicPropertyRegistry registry) {
        try {
            aiServer = HttpServer.create(new InetSocketAddress(0), 0);
            aiServer.createContext("/extract", exchange -> respond(exchange, """
                    {"status":"COMPLETED",
                     "result_json":{"name":"키보드","category_id":null,"category_name":"전자제품"},
                     "confidence_json":{"name":0.95,"category":0.88},"error_message":null}
                    """));
            aiServer.start();
        } catch (IOException e) {
            throw new IllegalStateException("AI 테스트 서버를 시작할 수 없습니다.", e);
        }
        registry.add("app.ai-extraction.base-url",
                () -> "http://127.0.0.1:" + aiServer.getAddress().getPort());
        registry.add("app.ai-extraction.path", () -> "/extract");
        registry.add("app.ai-extraction.timeout-ms", () -> "1000");
    }

    @AfterAll
    static void stopServer() {
        if (aiServer != null) {
            aiServer.stop(0);
        }
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "INSERT INTO categories (category_id, name, depth, sort_order) VALUES (12, '전자제품', 1, 1)");
    }

    @Test
    void sellerReceivesMappedExtractionWithoutPersistingIntermediateResult() throws Exception {
        MockMultipartFile first = new MockMultipartFile(
                "images", "main.jpg", "image/jpeg", new byte[] {1, 2, 3});
        MockMultipartFile second = new MockMultipartFile(
                "images", "detail.png", "image/png", new byte[] {4, 5, 6});

        mockMvc.perform(multipart("/sellers/products/ai-extraction")
                        .file(first)
                        .file(second)
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                20L, null, List.of(new SimpleGrantedAuthority("ROLE_SELLER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.fields.name.value").value("키보드"))
                .andExpect(jsonPath("$.data.fields.categoryId.value").value("12"));

        org.assertj.core.api.Assertions.assertThat(
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Long.class)).isZero();
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
