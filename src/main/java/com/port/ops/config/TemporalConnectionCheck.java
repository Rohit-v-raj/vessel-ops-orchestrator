package com.port.ops.config;

import io.temporal.api.workflowservice.v1.GetSystemInfoRequest;
import io.temporal.api.workflowservice.v1.GetSystemInfoResponse;
import io.temporal.client.WorkflowClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Asks the Temporal server for its version once the application has started.
 *
 * Without this, a missing server shows up only as gRPC retries in the log,
 * because the SDK connects lazily and keeps retrying. This application is a
 * Temporal client and worker and can do nothing useful without a server, so it
 * stops at once and says how to start one.
 */
@Component
class TemporalConnectionCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TemporalConnectionCheck.class);

    private final WorkflowClient client;

    TemporalConnectionCheck(WorkflowClient client) {
        this.client = client;
    }

    @Override
    public void run(ApplicationArguments args) {
        String target = client.getWorkflowServiceStubs().getOptions().getTarget();
        try {
            GetSystemInfoResponse info = client.getWorkflowServiceStubs().blockingStub()
                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                    .getSystemInfo(GetSystemInfoRequest.getDefaultInstance());
            log.info("Connected to Temporal server {} at {}, namespace '{}'",
                    info.getServerVersion().isEmpty() ? "(test server)" : info.getServerVersion(),
                    target == null ? "in-process" : target, client.getOptions().getNamespace());
        } catch (RuntimeException e) {
            throw new IllegalStateException("No Temporal server at " + target
                    + ". Start the dev server first: docker compose up -d", e);
        }
    }
}
