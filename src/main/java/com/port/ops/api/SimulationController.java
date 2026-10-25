package com.port.ops.api;

import com.port.ops.planner.StubBerthPlannerClient;
import com.port.ops.planner.StubBerthPlannerClient.PlannerMessage;
import com.port.ops.sim.CraneSystem;
import com.port.ops.sim.CraneSystem.CraneStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Windows onto the simulated systems, to see what the workflow did to them. */
@RestController
@RequestMapping("/api")
public class SimulationController {

    private final CraneSystem cranes;
    private final StubBerthPlannerClient planner;

    public SimulationController(CraneSystem cranes, StubBerthPlannerClient planner) {
        this.cranes = cranes;
        this.planner = planner;
    }

    /** Who holds each crane, and which are broken down. */
    @GetMapping("/cranes")
    public List<CraneStatus> cranes() {
        return cranes.status();
    }

    /** Everything the berth planner would have been told. */
    @GetMapping("/planner/messages")
    public List<PlannerMessage> plannerMessages() {
        return planner.messages();
    }
}
