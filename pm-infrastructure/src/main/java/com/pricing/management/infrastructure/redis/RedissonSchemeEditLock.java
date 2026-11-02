package com.pricing.management.infrastructure.redis;

import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/** Redis is used only for the short-lived draft edit lock. DB CAS remains the correctness guard. */
@Component
public class RedissonSchemeEditLock implements SchemeEditLock {
    private static final String PREFIX = "pm:lock:edit:";
    private final RedissonClient redisson;
    public RedissonSchemeEditLock(RedissonClient redisson) { this.redisson = redisson; }
    @Override public boolean tryAcquire(long schemeId) {
        try { return redisson.getLock(PREFIX + schemeId).tryLock(0, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
    }
    @Override public void release(long schemeId) {
        RLock lock = redisson.getLock(PREFIX + schemeId);
        if (lock.isHeldByCurrentThread()) lock.unlock();
    }
}
