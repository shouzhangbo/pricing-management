package com.pricing.management.application.scheme;
import java.time.LocalDateTime;
public record DimensionView(Long dimensionId, Long schemeId, String dimCode, String dimValues, String matchMode, LocalDateTime startTime, LocalDateTime endTime, Integer rowVersion) { }
