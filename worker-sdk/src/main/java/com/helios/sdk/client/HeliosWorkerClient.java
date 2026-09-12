package com.helios.sdk.client;

import com.helios.worker.v1.Ack;
import com.helios.worker.v1.HeartbeatRequest;
import com.helios.worker.v1.PollRequest;
import com.helios.worker.v1.TaskLease;
import com.helios.worker.v1.TaskResult;
import com.helios.worker.v1.WorkerInfo;
import com.helios.worker.v1.WorkerServiceGrpc;
import com.helios.worker.v1.WorkerToken;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class HeliosWorkerClient {
    private final WorkerServiceGrpc.WorkerServiceBlockingStub blockingStub;
    private final String workerId;
    private final String tenantId;
    private String currentToken;

    public HeliosWorkerClient(String host, int port, String tenantId) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.blockingStub = WorkerServiceGrpc.newBlockingStub(channel);
        this.workerId = UUID.randomUUID().toString();
        this.tenantId = tenantId;
    }

    public void register(List<String> taskTypes, int maxConcurrentTasks) {
        WorkerInfo info = WorkerInfo.newBuilder()
                .setWorkerId(workerId)
                .setTenantId(tenantId)
                .addAllTaskTypes(taskTypes)
                .setMaxConcurrentTasks(maxConcurrentTasks)
                .build();
        WorkerToken response = blockingStub.register(info);
        this.currentToken = response.getToken();
    }

    public TaskLease poll(int maxTasks) {
        PollRequest request = PollRequest.newBuilder()
                .setToken(currentToken)
                .setWorkerId(workerId)
                .setMaxTasks(maxTasks)
                .build();
        return blockingStub.poll(request);
    }

    public boolean complete(TaskResult result) {
        Ack ack = blockingStub.complete(result);
        return ack.getAccepted();
    }
}
