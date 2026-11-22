package com.pockit.finance.gateway.filters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pockit.finance.gateway.errors.GatewayErrorResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class JwtPreValidationGlobalFilterTest {

    private ObjectMapper objectMapper;
    private JwtPreValidationGlobalFilter filter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        filter = new JwtPreValidationGlobalFilter(objectMapper);
    }

    @Test
    void shouldHaveExpectedOrder() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 20);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/auth/login",
            "/api/auth/register",
            "/api/security/public-key",
            "/actuator/health",
            "/actuator/health/liveness",
            "/swagger-ui/index.html",
            "/v3/api-docs/swagger-config"
    })
    void shouldAllowPublicPathsWithoutAuthorizationHeader(String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(path).build()
        );
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(chainCalled.get()).isTrue();
    }

    @Test
    void shouldAllowOptionsPreflightRequestWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options("/api/cuentas").build()
        );
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(chainCalled.get()).isTrue();
    }

    @Test
    void shouldAllowProtectedPathWithValidThreeSegmentBase64UrlJwt() {
        String validFormatJwt = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyLTEifQ.sig_part-123";
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/cuentas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validFormatJwt)
                        .build()
        );
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(chainCalled.get()).isTrue();
    }

    @Test
    void shouldRejectProtectedPathWhenAuthorizationHeaderMissing() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/cuentas").build()
        );
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(chainCalled.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);

        String body = exchange.getResponse().getBodyAsString().block();
        GatewayErrorResponseDto error = objectMapper.readValue(body, GatewayErrorResponseDto.class);
        assertThat(error.statusCode()).isEqualTo(401);
        assertThat(error.code()).isEqualTo("UNAUTHORIZED");
        assertThat(error.message()).isNotBlank();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Basic dXNlcjpwYXNz",
            "Bearer ",
            "Bearer only-one-segment",
            "Bearer two.segments",
            "Bearer four.segments.are.invalid",
            "Bearer invalid+char.payload.sig",
            "Bearer invalid/char.payload.sig"
    })
    void shouldRejectProtectedPathWithMalformedAuthorizationHeader(String headerValue) throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/transacciones")
                        .header(HttpHeaders.AUTHORIZATION, headerValue)
                        .build()
        );
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(chainCalled.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);

        String body = exchange.getResponse().getBodyAsString().block();
        GatewayErrorResponseDto error = objectMapper.readValue(body, GatewayErrorResponseDto.class);
        assertThat(error.statusCode()).isEqualTo(401);
        assertThat(error.code()).isEqualTo("UNAUTHORIZED");
        assertThat(error.details()).isNotEmpty();
    }
}
