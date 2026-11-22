package com.pockit.finance.gateway.filters;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdGlobalFilterTest {

    private CorrelationIdGlobalFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdGlobalFilter();
    }

    @Test
    void shouldHaveHighestPrecedenceOrder() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    @Test
    void shouldGenerateCorrelationIdWhenMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/cuentas").build()
        );
        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();
        WebFilterChain chain = ex -> {
            capturedExchange.set(ex);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        String downstreamId = capturedExchange.get()
                .getRequest()
                .getHeaders()
                .getFirst(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER);
        String responseId = exchange.getResponse()
                .getHeaders()
                .getFirst(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER);

        assertThat(downstreamId).isNotBlank();
        assertThat(UUID.fromString(downstreamId)).isNotNull();
        assertThat(capturedExchange.get().getRequest().getHeaders()
                .get(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER))
                .containsExactly(downstreamId);
        assertThat(exchange.getResponse().getHeaders()
                .get(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER))
                .containsExactly(responseId);
        assertThat(responseId).isEqualTo(downstreamId);
    }

    @Test
    void shouldGenerateCorrelationIdWhenBlank() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/cuentas")
                        .header(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER, "   ")
                        .build()
        );
        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();
        WebFilterChain chain = ex -> {
            capturedExchange.set(ex);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        String downstreamId = capturedExchange.get()
                .getRequest()
                .getHeaders()
                .getFirst(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER);
        assertThat(downstreamId).isNotBlank();
        assertThat(UUID.fromString(downstreamId)).isNotNull();
        assertThat(capturedExchange.get().getRequest().getHeaders()
                .get(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER))
                .containsExactly(downstreamId);
    }

    @Test
    void shouldPropagateExistingCorrelationIdWithoutDuplicatingHeader() {
        String existingId = "corr-12345-abcde";
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/auth/login")
                        .header(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER, existingId)
                        .build()
        );
        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();
        WebFilterChain chain = ex -> {
            capturedExchange.set(ex);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(capturedExchange.get().getRequest().getHeaders()
                .get(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER))
                .containsExactly(existingId);
        assertThat(exchange.getResponse().getHeaders()
                .get(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER))
                .containsExactly(existingId);
    }
}
