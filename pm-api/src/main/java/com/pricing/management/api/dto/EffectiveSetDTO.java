package com.pricing.management.api.dto;

import java.io.Serializable;
import java.util.List;

/** Placeholder for a group or historical effective set. */
public record EffectiveSetDTO(List<EffectiveSchemeDTO> schemes) implements Serializable {
    public record EffectiveSchemeDTO(String feeCode, Long schemeId, String startTime,
                                     String contentHash, String dimSummary) implements Serializable {
    }
}
