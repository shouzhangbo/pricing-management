package com.pricing.management.infrastructure.redis;

public interface SchemeEditLock {
    boolean tryAcquire(long schemeId);
    void release(long schemeId);
}
