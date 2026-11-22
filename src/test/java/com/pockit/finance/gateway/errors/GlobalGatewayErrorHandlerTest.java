package com.pockit.finance.gateway.errors;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.timeout.ReadTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalGatewayErrorHandlerTest {

    private ObjectMapper objectMapper;
    private GlobalGatewayErrorHandler errorHandler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        errorHandler = new GlobalGatewayErrorHandler(objectMapper);
    }

    @Test
    void shouldHandleResponseStatusException() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/unknown").build()
        );
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "Route not found");

        StepVerifier.create(errorHandler.handle(exchange, ex))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);

        String body = exchange.getResponse().getBodyAsString().block();
        GatewayErrorResponseDto error = objectMapper.readValue(body, GatewayErrorResponseDto.class);
        assertThat(error.statusCode()).isEqualTo(404);
        assertThat(error.code()).isEqualTo("NOT_FOUND");
        assertThat(error.message()).isEqualTo("Route not found");
    }

    @Test
    void shouldHandleTimeoutExceptionAsGatewayTimeout504() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/cuentas").build()
        );
        RuntimeException ex = new RuntimeException("Upstream timeout", new TimeoutException("Timed out"));

        StepVerifier.create(errorHandler.handle(exchange, ex))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        String body = exchange.getResponse().getBodyAsString().block();
        GatewayErrorResponseDto error = objectMapper.readValue(body, GatewayErrorResponseDto.class);
        assertThat(error.statusCode()).isEqualTo(504);
        assertThat(error.code()).isEqualTo("GATEWAY_TIMEOUT");
        assertThat(body).doesNotContain("TimeoutException");
    }

    @Test
    void shouldHandleNettyReadTimeoutExceptionAs504() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/cuentas").build()
        );

        StepVerifier.create(errorHandler.handle(exchange, ReadTimeoutException.INSTANCE))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        String body = exchange.getResponse().getBodyAsString().block();
        GatewayErrorResponseDto error = objectMapper.readValue(body, GatewayErrorResponseDto.class);
        assertThat(error.statusCode()).isEqualTo(504);
        assertThat(error.code()).isEqualTo("GATEWAY_TIMEOUT");
    }

    @Test
    void shouldHandleConnectExceptionAsBadGateway502() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/auth/login").build()
        );
        ConnectException ex = new ConnectException("Connection refused: localhost:8081");

        StepVerifier.create(errorHandler.handle(exchange, ex))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        String body = exchange.getResponse().getBodyAsString().block();
        GatewayErrorResponseDto error = objectMapper.readValue(body, GatewayErrorResponseDto.class);
        assertThat(error.statusCode()).isEqualTo(502);
        assertThat(error.code()).isEqualTo("BAD_GATEWAY");
        assertThat(body).doesNotContain("localhost:8081");
    }

    @Test
    void shouldHandleUnexpectedExceptionAsInternalServerError500WithoutStackTrace() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/dashboards").build()
        );
        IllegalStateException ex = new IllegalStateException("Sensitive internal database detail");

        StepVerifier.create(errorHandler.handle(exchange, ex))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        String body = exchange.getResponse().getBodyAsString().block();
        GatewayErrorResponseDto error = objectMapper.readValue(body, GatewayErrorResponseDto.class);
        assertThat(error.statusCode()).isEqualTo(500);
        assertThat(error.code()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(body).doesNotContain("Sensitive internal database detail");
        assertThat(body).doesNotContain("IllegalStateException");
    }
}
