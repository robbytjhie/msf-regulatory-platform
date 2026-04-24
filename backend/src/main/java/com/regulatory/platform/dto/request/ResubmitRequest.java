package com.regulatory.platform.dto.request;

/**
 * All fields are optional — operator only needs to provide what changed.
 * Spec UC1: "Operator updates only the flagged sections — no need to re-enter entire application"
 */
public record ResubmitRequest(
        String businessName,
        String businessAddress,
        String contactPhone,
        String activityDescription
) {}
