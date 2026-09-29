package com.pricing.management.application.scheme;
import java.time.LocalDateTime;
public record SchemeView(Long schemeId, Long feeId, String schemeName, String status, LocalDateTime startTime, LocalDateTime endTime, String remark, Integer rowVersion) { }
