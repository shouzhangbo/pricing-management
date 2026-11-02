package com.pricing.management.application.scheme;

public record TemplateView(Long id, String templateCode, String templateName, String templateType,
                           Integer versionNo, String contentJson, String pairCode, String outputsJson) { }
