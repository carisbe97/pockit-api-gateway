package com.pockit.finance.gateway.errors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class GlobalGatewayErrorHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        GatewayErrorResponseDto errorDto = resolveError(ex);
        HttpStatus httpStatus = HttpStatus.resolve(errorDto.statusCode());
        response.setStatusCode(httpStatus != null ? httpStatus : HttpStatus.INTERNAL_SERVER_ERROR);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bytes = serialize(errorDto);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private GatewayErrorResponseDto resolveError(Throwable ex) {
        if (ex instanceof ResponseStatusException rse) {
            int statusCode = rse.getStatusCode().value();
            HttpStatus resolved = HttpStatus.resolve(statusCode);
            String code = resolved != null ? resolved.name() : "HTTP_" + statusCode;
            String message = rse.getReason() != null && !rse.getReason().isBlank()
                    ? rse.getReason()
                    : (resolved != null ? resolved.getReasonPhrase() : "Request error");
            return new GatewayErrorResponseDto(statusCode, code, message, List.of());
        }

        if (hasCause(ex, java.util.concurrent.TimeoutException.class)
                || hasCause(ex, TimeoutException.class)
                || hasCause(ex, SocketTimeoutException.class)) {
            log.warn("Gateway upstream timeout: {}", ex.getMessage());
            return new GatewayErrorResponseDto(
                    HttpStatus.GATEWAY_TIMEOUT.value(),
                    HttpStatus.GATEWAY_TIMEOUT.name(),
                    "Upstream service timed out",
                    List.of()
            );
        }

        if (hasCause(ex, ConnectException.class)
                || hasCause(ex, ConnectTimeoutException.class)
                || hasCause(ex, UnknownHostException.class)) {
            log.warn("Gateway upstream connection failure: {}", ex.getMessage());
            return new GatewayErrorResponseDto(
                    HttpStatus.BAD_GATEWAY.value(),
                    HttpStatus.BAD_GATEWAY.name(),
                    "Upstream service unavailable",
                    List.of()
            );
        }

        log.error("Unhandled gateway exception", ex);
        return new GatewayErrorResponseDto(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.name(),
                "An unexpected error occurred in the API Gateway",
                List.of()
        );
    }

    private boolean hasCause(Throwable ex, Class<? extends Throwable> targetType) {
        Throwable current = ex;
        while (current != null) {
            if (targetType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private byte[] serialize(GatewayErrorResponseDto errorDto) {
        try {
            return objectMapper.writeValueAsBytes(errorDto);
        } catch (JsonProcessingException e) {
            String fallback = "{\"statusCode\":500,\"code\":\"INTERNAL_SERVER_ERROR\","
                    + "\"message\":\"Error serializing gateway error response\",\"details\":[]}";
            return fallback.getBytes(StandardCharsets.UTF_8);
        }
    }
}
