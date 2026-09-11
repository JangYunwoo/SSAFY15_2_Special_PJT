package com.gongguhan.domain.payment.kakaopay.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpKakaoPayGatewayTest {

    private HttpServer server;
    private HttpKakaoPayGateway gateway;
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        KakaoPayProperties properties = new KakaoPayProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "TC0ONETIME", "test-secret-key", "SECRET_KEY",
                "http://localhost:5173", 1_000);
        gateway = new HttpKakaoPayGateway(properties);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void readyUsesCurrentJsonContractAndAuthorizationHeader() {
        server.createContext("/online/v1/payment/ready", exchange -> {
            capture(exchange);
            respond(exchange, 200, """
                    {"tid":"T123","next_redirect_pc_url":"https://pay.example/bridge"}
                    """);
        });

        KakaoPayGateway.ReadyResult result = gateway.ready(new KakaoPayGateway.ReadyCommand(
                "PAYMENT-9001", "100", "공동구매 #701", 54_000,
                "http://localhost/success", "http://localhost/cancel", "http://localhost/fail"));

        assertThat(authorization.get()).isEqualTo("SECRET_KEY test-secret-key");
        assertThat(requestBody.get())
                .contains("\"cid\":\"TC0ONETIME\"")
                .contains("\"partner_order_id\":\"PAYMENT-9001\"")
                .contains("\"total_amount\":54000")
                .contains("\"tax_free_amount\":0");
        assertThat(result.tid()).isEqualTo("T123");
        assertThat(result.nextRedirectPcUrl()).isEqualTo("https://pay.example/bridge");
    }

    @Test
    void approveMapsAndReturnsServerVerificationFields() {
        server.createContext("/online/v1/payment/approve", exchange -> {
            capture(exchange);
            respond(exchange, 200, """
                    {"aid":"A123","tid":"T123","partner_order_id":"PAYMENT-9001",
                     "partner_user_id":"100","payment_method_type":"MONEY",
                     "amount":{"total":54000,"tax_free":0,"vat":4909}}
                    """);
        });

        KakaoPayGateway.ApproveResult result = gateway.approve(new KakaoPayGateway.ApproveCommand(
                "T123", "PAYMENT-9001", "100", "pg-token"));

        assertThat(requestBody.get()).contains("\"pg_token\":\"pg-token\"");
        assertThat(result.aid()).isEqualTo("A123");
        assertThat(result.totalAmount()).isEqualTo(54_000);
        assertThat(result.vatAmount()).isEqualTo(4_909);
        assertThat(result.paymentMethodType()).isEqualTo("MONEY");
    }

    private void capture(HttpExchange exchange) throws IOException {
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
