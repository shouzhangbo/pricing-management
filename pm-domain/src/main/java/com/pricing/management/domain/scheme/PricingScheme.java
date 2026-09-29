package com.pricing.management.domain.scheme;

import java.time.LocalDateTime;
import java.util.Objects;

/** Aggregate-root placeholder for the versioned pricing-scheme model. */
public final class PricingScheme {
    private final Long schemeId;
    private final Long rootSchemeId;
    private final Long feeId;
    private final LocalDateTime startTime;

    public PricingScheme(Long schemeId, Long rootSchemeId, Long feeId, LocalDateTime startTime) {
        this.schemeId = Objects.requireNonNull(schemeId, "schemeId must not be null");
        this.rootSchemeId = Objects.requireNonNull(rootSchemeId, "rootSchemeId must not be null");
        this.feeId = Objects.requireNonNull(feeId, "feeId must not be null");
        this.startTime = startTime;
    }

    public Long schemeId() { return schemeId; }
    public Long rootSchemeId() { return rootSchemeId; }
    public Long feeId() { return feeId; }
    public LocalDateTime startTime() { return startTime; }
}
