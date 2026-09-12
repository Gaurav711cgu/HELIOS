package com.helios.orchestrator.api;

import com.helios.orchestrator.core.DagExecutor;
import com.helios.orchestrator.core.WorkflowStateStore;
import com.helios.worker.v1.Ack;
import com.helios.worker.v1.HeartbeatRequest;
import com.helios.worker.v1.PollRequest;
import com.helios.worker.v1.TaskLease;
import com.helios.worker.v1.TaskResult;
import com.helios.worker.v1.WorkerInfo;
import com.helios.worker.v1.WorkerServiceGrpc;
import com.helios.worker.v1.WorkerToken;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@GrpcService
public class GrpcWorkerService extends WorkerServiceGrpc.WorkerServiceImplBase {
    private static final Logger log = LoggerFactory.getLogger(GrpcWorkerService.class);
    private final DagExecutor executor;
    private final WorkflowStateStore stateStore;

    public GrpcWorkerService(DagExecutor executor, WorkflowStateStore stateStore) {
        this.executor = executor;
        this.stateStore = stateStore;
    }

    @Override
    public void register(WorkerInfo request, StreamObserver<WorkerToken> responseObserver) {
        log.info("Worker registered: {}", request.getWorkerId());
        WorkerToken token = WorkerToken.newBuilder()
                .setToken(UUID.randomUUID().toString())
                .setExpiresAtEpochMs(System.currentTimeMillis() + 3600000)
                .build();
        responseObserver.onNext(token);
        responseObserver.onCompleted();
    }

    @Override
    public void poll(PollRequest request, StreamObserver<TaskLease> responseObserver) {
        // Basic placeholder for polling mechanism
        responseObserver.onCompleted();
    }

    @Override
    public void complete(TaskResult request, StreamObserver<Ack> responseObserver) {
        try {
            if (request.getSuccess()) {
                // Assuming task completion logic
            } else {
                // Assuming task failure logic
            }
            responseObserver.onNext(Ack.newBuilder().setAccepted(true).setMessage("OK").build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void heartbeat(HeartbeatRequest request, StreamObserver<Ack> responseObserver) {
        responseObserver.onNext(Ack.newBuilder().setAccepted(true).setMessage("OK").build());
        responseObserver.onCompleted();
    }
}
