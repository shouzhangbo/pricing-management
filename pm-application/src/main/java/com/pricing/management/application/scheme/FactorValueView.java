package com.pricing.management.application.scheme;

public record FactorValueView(Long id, Long schemeId, String factorCode, String valueJson, Integer rowVersion) { }
