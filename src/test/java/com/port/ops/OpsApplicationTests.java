package com.port.ops;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The whole application starts against the starter's in-memory test server,
 * so the build needs neither Docker nor a running Temporal. This checks the
 * wiring: auto-discovery finds the workflow and activities and builds a
 * worker for each task queue.
 */
@SpringBootTest(properties = "spring.temporal.test-server.enabled=true")
class OpsApplicationTests {

    @Test
    void contextLoads() {
    }
}
