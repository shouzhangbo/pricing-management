package com.pricing.management.application.scheme;

public record FactorView(Long id, String factorCode, String factorName, String category,
                         String valueType, String unit, String dictJson, String validateRule) { }
