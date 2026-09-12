package com.helios.orchestrator.core;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class TenantQuotaManager implements QuotaManager {
    private final ConcurrentMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final int capacity;
    private final double refillPerSecond;
    private final Clock clock;

    public TenantQuotaManager(int capacity, double refillPerSecond) {
        this(capacity, refillPerSecond, Clock.systemUTC());
    }

    public TenantQuotaManager(int capacity, double refillPerSecond, Clock clock) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (refillPerSecond <= 0) {
            throw new IllegalArgumentException("refillPerSecond must be positive");
        }
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.clock = clock;
    }

    @Override
    public boolean tryAcquire(String tenantId) {
        return buckets.computeIfAbsent(tenantId, ignored -> new TokenBucket(capacity, refillPerSecond, clock.millis()))
                .tryAcquire(clock.millis());
    }

    public Map<String, Double> utilization() {
        Map<String, Double> result = new ConcurrentHashMap<>();
        buckets.forEach((tenant, bucket) -> result.put(tenant, bucket.tokens()));
        return result;
    }

    private static final class TokenBucket {
        private final int capacity;
        private final double refillPerSecond;
        private double tokens;
        private long lastRefillMillis;

        TokenBucket(int capacity, double refillPerSecond, long nowMillis) {
            this.capacity = capacity;
            this.refillPerSecond = refillPerSecond;
            this.tokens = capacity;
            this.lastRefillMillis = nowMillis;
        }

        synchronized boolean tryAcquire(long nowMillis) {
            refill(nowMillis);
            if (tokens < 1) {
                return false;
            }
            tokens -= 1;
            return true;
        }

        synchronized double tokens() {
            return tokens;
        }

        private void refill(long nowMillis) {
            long elapsedMillis = Math.max(0, nowMillis - lastRefillMillis);
            tokens = Math.min(capacity, tokens + (elapsedMillis / 1000.0) * refillPerSecond);
            lastRefillMillis = nowMillis;
        }
    }
}
