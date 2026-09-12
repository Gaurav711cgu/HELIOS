package com.helios.sdk.model;

import java.io.Serializable;

public record RetryPolicy(int maxAttempts, long initialBackoffMillis, double multiplier) implements Serializable {
    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        if (initialBackoffMillis < 0) {
            throw new IllegalArgumentException("initialBackoffMillis cannot be negative");
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be >= 1.0");
        }
    }

    public static RetryPolicy none() {
        return new RetryPolicy(1, 0, 1.0);
    }

    public static RetryPolicy exponential(int maxAttempts, long initialBackoffMillis) {
        return new RetryPolicy(maxAttempts, initialBackoffMillis, 2.0);
    }

    public long delayForAttempt(int attemptNumber) {
        if (attemptNumber <= 1) {
            return 0;
        }
        return (long) (initialBackoffMillis * Math.pow(multiplier, attemptNumber - 2));
    }
}
