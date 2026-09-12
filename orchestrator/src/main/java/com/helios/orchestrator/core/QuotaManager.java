package com.helios.orchestrator.core;

public interface QuotaManager {
    boolean tryAcquire(String tenantId);
}
