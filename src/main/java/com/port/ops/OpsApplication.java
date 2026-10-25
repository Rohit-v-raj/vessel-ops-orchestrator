package com.port.ops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * One process plays three roles: the REST API the berth planner calls, the
 * Temporal workers that run the visit, and the simulated terminal systems
 * (cranes, labour, equipment) the activities talk to. In production these
 * would be separate deployments; one process keeps it easy to run.
 */
@SpringBootApplication
public class OpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpsApplication.class, args);
    }
}
