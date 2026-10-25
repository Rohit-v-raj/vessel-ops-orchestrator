package com.port.ops;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The whole application starts against the starter's in-memory test server,
 * so the build needs neither Docker nor a running Temporal.
 */
@SpringBootTest(properties = {
        "spring.temporal.test-server.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:ops"
})
class OpsApplicationTests {

    @Test
    void contextLoads() {
    }
}
