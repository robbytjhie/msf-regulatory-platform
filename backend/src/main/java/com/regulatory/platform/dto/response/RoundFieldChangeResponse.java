package com.regulatory.platform.dto.response;

public record RoundFieldChangeResponse(
        String field,
        String previousValue,
        String currentValue
) {}
