package com.pricing.management.api.dto;

import java.io.Serializable;
import java.util.Map;

/** Versioned scheme content; fields will be expanded with the domain implementation. */
public record SchemeContentDTO(Long schemeId, String startTime, String contentHash,
                               Map<String, Object> content) implements Serializable {
}
