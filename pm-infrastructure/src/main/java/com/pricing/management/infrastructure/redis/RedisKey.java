package com.pricing.management.infrastructure.redis;

public final class RedisKey {
    private RedisKey() { }
    public static String schemeEditLock(long schemeId) { return "pm:lock:edit:" + schemeId; }
    public static String publishBatch(String batchId) { return "pm:publish:batch:" + batchId; }
}
