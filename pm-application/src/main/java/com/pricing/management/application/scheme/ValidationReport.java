package com.pricing.management.application.scheme;
import java.util.List;
public record ValidationReport(boolean valid, List<ValidationIssue> errors, List<ValidationIssue> warnings) {
    public record ValidationIssue(String path, String code, String message) { }
}
