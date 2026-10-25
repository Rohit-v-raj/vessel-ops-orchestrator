# Vessel Operations Orchestrator

Runs a vessel visit at the quay as a durable [Temporal](https://temporal.io)
workflow, from the berth being confirmed to the ship sailing.

The berth planner in
[ship-eta-berth-scheduler](https://github.com/Rohit-v-raj/ship-eta-berth-scheduler)
decides **when and where** a ship berths. This service takes over **after** that.

## Quick start (Windows, cmd)

You need Java 21 and Docker Desktop (running). Gradle is not needed: `gradlew.bat`
downloads it on first use.

```cmd
docker compose up -d
gradlew.bat build
gradlew.bat bootRun
```

- Orchestrator: http://localhost:8081
- Temporal Web UI: http://localhost:8233

## Stack

Java 21 · Spring Boot 3.5 · Temporal Java SDK 1.40 (`temporal-spring-boot-starter`) ·
Gradle · Temporal dev server 1.32 in Docker.
