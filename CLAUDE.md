# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Status

**M0 complete.** Gradle scaffold, quality gates, and CI are in place. `brd.md` is the source of truth for what is being built — read it before implementing anything. Next milestone: **M1** (Domain Model — `S3ChangeEvent`, `StateStore`, `InMemoryStateStore`).

## What BucketWatch Is

A lightweight Kotlin/JVM library that detects changes (create / modify / delete) in Amazon S3 buckets by **polling**, with **zero additional AWS infrastructure** (no SQS, SNS, Lambda, or EventBridge). It needs only S3 read permissions (`ListObjectsV2`, `HeadObject`, `GetObject`). The library must be fully usable from Java as well as Kotlin.

## Core Design (from brd.md §8–9)

Change detection is **stateful diffing between polls**, not real-time events:
- **Presence** determines Created/Deleted; **ETag comparison** determines Modified.
- Same ETag across two polls → no event. Rapid changes between polls may collapse into one event — this is an accepted constraint of the polling model.

Key components and their responsibilities:
- **`BucketWatch`** — public entry point. Lifecycle is `initialize()` (load current state, fire **no** events) → `start()` (begin scheduled polling) → `stop()` / `close()`. `checkNow()` runs a poll on demand. Created via `BucketWatch.create(...)` (simple) or `BucketWatch.builder()` (full control).
- **`BucketWatchConfig`** — bucket, prefix, file-extension filters, poll interval, watch mode, maxKeys.
- **`S3ChangeEvent`** — sealed class with `Created` / `Modified` / `Deleted` subtypes (all carry bucket/key/timestamp; ETag fields vary by type).
- **`S3ChangeListener`** — interface with `onCreated`/`onModified`/`onDeleted`/`onError`. Must also support a Kotlin DSL builder form and individual-callback registration (see brd.md §6.2 FR4).
- **`StateStore`** — pluggable interface tracking `ObjectState` per key between polls. `InMemoryStateStore` is the default. Custom stores are user-injectable via the builder.
- **Internal engine** — `ChangeDetector` (diff logic) driven by a `ScheduledExecutorService`, plus an event emitter.

### Two watch modes (mutually exclusive per watcher)
- **`LIST_BASED`** — `ListObjectsV2` each poll; diffs the full listing. Detects *new* files. Use for dynamic/unknown file sets.
- **`HEAD_BASED`** — `HeadObject` per registered key. Cheaper, but **cannot detect new files** — only watches keys you register. Use for known config files.

### Non-negotiable constraints
- **Thread safety**: all public APIs must be thread-safe (NFR6).
- **Error isolation**: transient S3 errors are reported via `onError` and must not crash the watcher (FR10, NFR5).
- **Java interop**: design APIs so Java callers work naturally (builder accepts a config lambda; avoid Kotlin-only constructs in public signatures). See brd.md §9.4.
- Poll interval bounds: **min 10s, max 24h, default 5m**.

## Toolchain

- **Build**: Gradle 8.10.2 with Kotlin DSL, multi-module (`bucketwatch-core`; `bucketwatch-spring-boot-starter` reserved for v1.1).
- **Targets**: Java 11 bytecode (toolchain auto-provisioned via foojay), Kotlin 2.0.21, AWS SDK v2 (`software.amazon.awssdk:s3` 2.28.16).
- **Test**: JUnit 5 (`junit-jupiter`), MockK for unit tests. Testcontainers + LocalStack land in M6 for integration tests.
- **Quality gates**: detekt 1.23.7 (`config/detekt/detekt.yml`, zero issues fail the build), JaCoCo HTML + XML report (coverage target ≥ 80% enforced from M6).
- **CI**: GitHub Actions (`.github/workflows/ci.yml`) — build → test → detekt → jacocoTestReport on push/PR.

Commands:
- Build: `./gradlew build`
- All tests: `./gradlew test`
- Single test class: `./gradlew test --tests "io.github.surajcm.bucketwatch.BucketWatchTest"`
- Single test method: `./gradlew test --tests "io.github.surajcm.bucketwatch.ChangeDetectorTest.methodName"`
- Lint: `./gradlew detekt`
- Coverage: `./gradlew jacocoTestReport`

## Scope Discipline

The BRD deliberately keeps v1.0 minimal. Out of scope until later versions: Spring Boot auto-config (v1.1), coroutines/Flow API (v1.2), other cloud providers and distributed coordination (v2.0). The only runtime dependency for core should be the AWS SDK — do not add dependencies to `bucketwatch-core` without a strong reason (NFR17).
