package com.pricing.management.application.scheme;
import java.util.List;
public record PageResult<T>(long total, int pageNo, int pageSize, List<T> records) { }
