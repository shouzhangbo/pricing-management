package com.pricing.management.application.scheme;

import org.springframework.stereotype.Component;

/** JVM-local Snowflake-style generator; node bits must be configured before multi-node deployment. */
@Component
public class TimeBasedIdGenerator {
    private static final long EPOCH = 1_704_067_200_000L;
    private long lastMillis = -1L;
    private int sequence;
    public synchronized long nextId() {
        long now = System.currentTimeMillis();
        if (now < lastMillis) throw new IllegalStateException("System clock moved backwards");
        if (now == lastMillis && ++sequence > 4095) { do { now = System.currentTimeMillis(); } while (now == lastMillis); sequence = 0; }
        else if (now != lastMillis) sequence = 0;
        lastMillis = now;
        return ((now - EPOCH) << 12) | sequence;
    }
}
