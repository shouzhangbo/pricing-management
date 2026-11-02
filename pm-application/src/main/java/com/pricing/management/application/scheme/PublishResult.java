package com.pricing.management.application.scheme;
import java.time.LocalDateTime;
public record PublishResult(String publishBatchId, String groupNo, LocalDateTime startTime, int schemeCount) { }
