package com.port.ops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * One process plays three roles: the REST API the berth planner calls, the
 * Temporal workers that run the visit, and the simulated crane system the
 * activities talk to. In production these would be separate deployments; one
 * process keeps it easy to run.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class OpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpsApplication.class, args);
    }
}
