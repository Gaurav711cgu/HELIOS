package com.helios.orchestrator.infrastructure;

import com.helios.orchestrator.core.QuotaManager;
import redis.clients.jedis.JedisPooled;

import java.util.List;

public final class RedisQuotaManager implements QuotaManager {
    private static final String SCRIPT = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local state = redis.call('HMGET', key, 'tokens', 'ts')
            local tokens = tonumber(state[1]) or capacity
            local ts = tonumber(state[2]) or now
            local delta = math.max(0, now - ts) / 1000.0
            tokens = math.min(capacity, tokens + delta * refill)
            if tokens < 1 then
              redis.call('HMSET', key, 'tokens', tokens, 'ts', now)
              redis.call('PEXPIRE', key, 60000)
              return 0
            end
            tokens = tokens - 1
            redis.call('HMSET', key, 'tokens', tokens, 'ts', now)
            redis.call('PEXPIRE', key, 60000)
            return 1
            """;

    private final JedisPooled jedis;
    private final int capacity;
    private final double refillPerSecond;

    public RedisQuotaManager(JedisPooled jedis, int capacity, double refillPerSecond) {
        this.jedis = jedis;
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
    }

    @Override
    public boolean tryAcquire(String tenantId) {
        Object result = jedis.eval(SCRIPT, List.of("helios:tenant:" + tenantId + ":quota"), List.of(
                Integer.toString(capacity),
                Double.toString(refillPerSecond),
                Long.toString(System.currentTimeMillis())));
        return Long.valueOf(1L).equals(result);
    }
}
