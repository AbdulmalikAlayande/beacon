# 0001. Beacon is an embedded starter with no required infrastructure

- Status: Accepted
- Date: 2026-11-02
- Register entries: D-001, D-002, D-004, D-005
- Document status: Approved, last checked against the code on 2026-11-02

## Context

Teams need notifications they can trust: none for work that rolled back, no duplicates, and no sends lost when a provider fails or the application restarts. The first design for Beacon read like a microservice specification that needed Kafka and Redis. A team that only wants reliable receipts and OTPs would have to deploy and operate another service and two more pieces of infrastructure, which would put most adopters off.

Beacon also has to work with the database the host already runs. Its default queue relies on `FOR UPDATE SKIP LOCKED`, which needs PostgreSQL 14 or later.

## Decision

- Beacon ships as a Spring Boot starter that runs inside the host application. The host adds one dependency, implements `UserPreferenceResolver`, and calls `NotificationService.send()`.
- Beacon supports three channels: `PUSH`, `SMS` and `EMAIL`.
- Every default (the queue, deduplication and locking) runs on the host's own PostgreSQL. Redis, Kafka, RabbitMQ and Pulsar are optional, pluggable alternatives, never requirements.
- Supported platform: Java 21, PostgreSQL 14 or later, Spring Boot 4.0.x.

## Consequences

- Adopting Beacon means adding a dependency and one interface, not deploying a service.
- Beacon shares the host's process, threads, transactions and database. Every design choice has to respect that it is a guest: it must not assume the host's schema, take over the host's Flyway history, or force dependencies the host does not need (see ADR 0002, ADR 0003 and ADR 0007).
- Every default must work on PostgreSQL alone. A feature that needs Redis or a broker can only be an optional alternative.
- Spring Boot 3.x cannot be supported: the code depends on Jackson 3 (`tools.jackson`) and Hibernate 7, which come with Spring Boot 4.

## Alternatives rejected

- **A standalone notification microservice** with its own deployment, Kafka and Redis. Rejected because teams should get reliable notifications without running and operating another service.
- **Requiring Redis or a message broker out of the box.** Rejected for the same reason; brokers are opt-in alternatives (see ADR 0024 for the in-memory queue default that was also dropped).
- **Spring Boot 3.2 or later** (stated in the old README). Rejected because the code depends on Jackson 3 and Hibernate 7.

## In the code

- Channels: `beacon-api/src/main/java/io/github/abdulmalikalayande/beacon/api/enums/NotificationChannel.java`.
- Platform: `pom.xml` (parent `spring-boot-starter-parent` 4.0.6, `java.version` 21).
- PostgreSQL-only defaults: `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/queue/DatabaseNotificationQueue.java`, `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/dedup/JdbcDeduplicationStore.java`, and the migrations in `beacon-core/src/main/resources/db/migration/beacon/`.
- Optional alternative already present: `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/dedup/RedisDeduplicationStore.java`.
- Not built yet: the starter itself. `beacon-spring-boot-starter` is still a scaffold, so a host cannot add Beacon as one dependency today.
