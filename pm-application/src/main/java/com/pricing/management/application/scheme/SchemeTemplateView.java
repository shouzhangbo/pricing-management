package com.pricing.management.application.scheme;

public record SchemeTemplateView(Long id, Long schemeId, String templateType, String templateCode,
                                 Integer templateVersion, String slotBindings, Integer rowVersion) { }
