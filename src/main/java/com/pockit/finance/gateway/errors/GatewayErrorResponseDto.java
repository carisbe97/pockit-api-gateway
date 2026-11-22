package com.pockit.finance.gateway.errors;

import java.util.List;

public record GatewayErrorResponseDto(
        int statusCode,
        String code,
        String message,
        List<String> details
) {
    public GatewayErrorResponseDto {
        details = details == null ? List.of() : List.copyOf(details);
    }
}
