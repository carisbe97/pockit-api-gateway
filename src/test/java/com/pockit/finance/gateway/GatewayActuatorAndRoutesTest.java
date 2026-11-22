package com.pockit.finance.gateway;

import com.pockit.finance.gateway.filters.CorrelationIdGlobalFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("ares")
class GatewayActuatorAndRoutesTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldReturnHealth200WithSecurityHeadersAndCorrelationId() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER)
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("X-Frame-Options", "DENY")
                .expectHeader().valueEquals("Referrer-Policy", "no-referrer")
                .expectHeader().exists("Content-Security-Policy")
                .expectHeader().valueEquals("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void shouldEchoIncomingCorrelationIdOnActuatorHealth() {
        String customCorrelationId = "req-integration-001";

        webTestClient.get()
                .uri("/actuator/health")
                .header(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER, customCorrelationId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER, customCorrelationId);
    }

    @Test
    void shouldRejectProtectedRouteWithoutJwtWithUniform401Json() {
        webTestClient.get()
                .uri("/api/cuentas")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectHeader().exists(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER)
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectBody()
                .jsonPath("$.statusCode").isEqualTo(401)
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.message").value(msg -> assertThat(msg.toString()).isNotBlank())
                .jsonPath("$.details").isArray();
    }
}
