# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Status

**Pre-implementation.** The repo currently contains only `brd.md` (the Business Requirements Document) and a stub `README.md`. There is no source code, no `build.gradle.kts`, and no tests yet. `brd.md` is the source of truth for what is being built — read it before implementing anything, and keep this file aligned with it as code lands.

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

## Planned Toolchain (not yet present — confirm against brd.md §8.3–8.4 when scaffolding)

- **Build**: Gradle with Kotlin DSL (`build.gradle.kts`), multi-module (`bucketwatch-core`, future `bucketwatch-spring-boot-starter`).
- **Targets**: Java 11+, Kotlin 1.8+, AWS SDK **v2** (`software.amazon.awssdk:s3`).
- **Test**: JUnit 5 (`junit-jupiter`), MockK for unit tests, **Testcontainers + LocalStack** for S3 integration tests.
- **Quality gates**: detekt (zero critical/major), JaCoCo coverage > 80%.

Once `build.gradle.kts` exists, the expected commands will be:
- Build: `./gradlew build`
- All tests: `./gradlew test`
- Single test class: `./gradlew test --tests "io.github.*.BucketWatchTest"`
- Single test method: `./gradlew test --tests "io.github.*.ChangeDetectorTest.methodName"`
- Lint: `./gradlew detekt`
- Coverage: `./gradlew jacocoTestReport`

Verify these against the actual Gradle config once it is committed, and update this section.

## Scope Discipline

The BRD deliberately keeps v1.0 minimal. Out of scope until later versions: Spring Boot auto-config (v1.1), coroutines/Flow API (v1.2), other cloud providers and distributed coordination (v2.0). The only runtime dependency for core should be the AWS SDK — do not add dependencies to `bucketwatch-core` without a strong reason (NFR17).
