# BucketWatch — Implementation Plan

A milestone-based plan for delivering **BucketWatch v1.0** as specified in [`brd.md`](./brd.md). Designed for a **slow, intermittent pace** — you do not need to commit daily.

## How to read this plan

- **Each milestone is self-contained.** You can stop between any two milestones and the library is still in a coherent, compiling, tested state. No milestone leaves a half-built feature.
- **Each milestone opens with a Spike** — a short, throwaway investigation that removes the biggest unknown *before* you commit to the stories. A spike's output is a decision or a notes file, not production code.
- **Then a couple of stories** turn that decision into shipped, tested code with a clear Definition of Done (DoD).
- **No dates.** Sizes are relative (S / M / L) so you can pick up whatever fits the time you have. Milestones are mostly sequential because of dependencies; the dependency note on each says what it needs.
- **Global DoD for every story:** compiles, `detekt` clean, unit tests pass, public API has KDoc. (Gates land in M0.)

## Milestone overview

| # | Milestone | Goal | Depends on |
|---|-----------|------|------------|
| M0 | Foundations & Scaffolding | Buildable multi-module Gradle project with quality gates and CI | — |
| M1 | Domain Model | Events + state store — the vocabulary everything else uses | M0 |
| M2 | Configuration | `BucketWatchConfig` + builder, validation, Java-friendly | M1 |
| M3 | Change Detection Engine (LIST_BASED) | Diff two S3 listings into events | M1, M2 |
| M4 | Watcher Lifecycle, Scheduling & Listeners | A running, thread-safe watcher that emits events | M3 |
| M5 | HEAD_BASED Mode | Watch specific known keys efficiently | M4 |
| M6 | Integration Testing & Java Interop | Prove it works against real S3 semantics and from Java | M5 |
| M7 | Release & Documentation | Maven Central, docs, examples, v1.0.0 tag | M6 |

First runnable end-to-end watcher: **end of M4.** Feature-complete v1.0: **end of M5.** Releasable: **end of M7.**

---

## M0 — Foundations & Scaffolding

**Goal:** A `./gradlew build` that succeeds on a multi-module skeleton, with lint and coverage wired so every later story inherits the quality gates. *(BRD §8.3, §8.4, NFR15–17)*

### Spike M0 — Toolchain decisions
Stand up a throwaway Gradle Kotlin-DSL project and confirm the stack actually plays together: Kotlin (1.8+) + Java 11 toolchain, AWS SDK v2 BOM, JUnit 5, MockK, detekt, JaCoCo. Decide module layout (`bucketwatch-core` now; reserve `bucketwatch-spring-boot-starter` for the future). Resolve open questions: JaCoCo + Kotlin coverage config, detekt baseline vs. zero-tolerance, Gradle version catalog (`libs.versions.toml`) yes/no.
**Output:** a short decisions note (in PR description or `docs/adr/`), not code.

### Story M0.1 — Gradle multi-module skeleton
- `settings.gradle.kts` with `bucketwatch-core`; root `build.gradle.kts` with shared Kotlin/Java config.
- AWS SDK v2 S3 as `api` dependency of core; test deps (JUnit5, MockK) wired.
- One trivial placeholder class + one passing test to prove the toolchain.
- **DoD:** `./gradlew build` and `./gradlew test` pass from a clean checkout.

### Story M0.2 — Quality gates & CI
- detekt configured (fail on critical/major) and JaCoCo report task.
- GitHub Actions workflow: build + test + detekt on push/PR.
- **DoD:** CI green on a PR; `./gradlew detekt jacocoTestReport` runs locally.
- **Follow-up:** update `CLAUDE.md`'s "Planned Toolchain" section to reflect the real commands now that they exist.

---

## M1 — Domain Model

**Goal:** Define the immutable types the whole library speaks in, with no S3 or threading logic yet. *(BRD §8.2, §9.2)*

### Spike M1 — Event & state shape vs. Java interop
Prototype the `S3ChangeEvent` sealed hierarchy and `StateStore`/`ObjectState` interfaces, then write a tiny **Java** snippet that constructs/consumes them. Confirm Kotlin `sealed class` + `data class` subtypes read cleanly from Java (getters, no awkward `Companion` access). Decide how `ObjectState` is keyed (key → etag/size/lastModified).
**Output:** agreed signatures.

### Story M1.1 — `S3ChangeEvent` sealed hierarchy
- Sealed class with `Created` / `Modified` / `Deleted` per BRD §9.2 (bucket, key, timestamp; etag/size/prev-etag fields by subtype).
- Unit tests for construction/equality.
- **DoD:** types compile, documented, used by a Java test snippet to confirm interop.

### Story M1.2 — `StateStore` + `InMemoryStateStore`
- `StateStore` interface (`get`/`put`/`remove`/`getAllKeys`/`clear`) and `ObjectState`.
- Thread-safe `InMemoryStateStore` (default impl) — backing map must tolerate concurrent poll/read.
- **DoD:** unit tests including a basic concurrency test; interface is pluggable (no concrete leak into signatures).

---

## M2 — Configuration

**Goal:** A validated, Java-friendly `BucketWatchConfig` and its builder. *(BRD §6.2 FR5–FR7, §9.3, constraints in §4.4)*

### Spike M2 — Builder ergonomics & validation rules
Decide the builder API that satisfies both the Kotlin DSL (`config { ... }`) and Java (`config -> config...`) forms in BRD §9. Pin the validation rules: poll interval **min 10s / max 24h / default 5m**, mutually-exclusive `pollIntervalSeconds/Minutes/Hours`, required `bucket`, optional `prefix`/`fileExtensions`/`maxKeys`, `WatchMode` enum default.
**Output:** finalized config surface + validation matrix.

### Story M2.1 — `BucketWatchConfig` + builder + `WatchMode`
- Immutable config, builder with the methods in BRD §9.3, `WatchMode { LIST_BASED, HEAD_BASED }`.
- Construction-time validation with clear, actionable error messages (NFR10).
- **DoD:** unit tests cover valid configs, each invalid case, and boundary values (9s rejected, 10s ok, 24h ok, 24h+1s rejected).

### Story M2.2 — Filtering predicates
- Prefix and file-extension filtering logic as a pure, testable function (used later by the detector).
- **DoD:** table-driven unit tests for extension/prefix matching edge cases.

---

## M3 — Change Detection Engine (LIST_BASED)

**Goal:** Pure diff logic: given previous state + a current S3 listing, produce the correct `Created`/`Modified`/`Deleted` events. No scheduling yet. *(BRD §6.2 FR1–FR3)*

### Spike M3 — ETag semantics
Verify the assumption that ETag reliably signals change. Investigate the gotcha that **multipart-uploaded objects have non-MD5 ETags** and that ETags still change on re-upload — confirm diffing works regardless. Decide fallback/augmentation (size + lastModified) if ETag alone is insufficient. Test against LocalStack or a scratch bucket.
**Output:** documented detection rule and any edge cases to guard.

### Story M3.1 — `ChangeDetector` core
- Internal `ChangeDetector` taking previous `StateStore` snapshot + current listing → `List<S3ChangeEvent>`, applying M2 filters.
- Implements the truth table in BRD §6.2 (presence → created/deleted; etag change → modified; no change → no event).
- Updates state store as part of (or alongside) the diff.
- **DoD:** exhaustive unit tests for every transition incl. collapsed-rapid-change behavior (documented as accepted, §4.4); fully mocked, no real S3.

### Story M3.2 — S3 listing adapter (`ListObjectsV2`)
- Thin wrapper over `S3Client.listObjectsV2` handling **pagination** and `maxKeys`, mapping results to the detector's input model.
- **DoD:** unit tests with a mocked `S3Client` covering single-page, multi-page, and empty-bucket responses.

---

## M4 — Watcher Lifecycle, Scheduling & Listeners

**Goal:** The first end-to-end runnable watcher — schedule polls, run the detector, dispatch events to listeners, shut down cleanly. *(BRD §6.2 FR4, FR8–FR10; NFR5–NFR6)*

### Spike M4 — Threading & shutdown model
Decide the concurrency design: single-thread `ScheduledExecutorService`, how `checkNow()` interacts with the scheduled poll (must not run concurrently / corrupt state), reentrancy, and graceful shutdown semantics for `stop()` vs `close()`. Decide listener dispatch threading and how a throwing listener is isolated.
**Output:** threading model decision.

### Story M4.1 — `S3ChangeListener` registration (3 forms)
- Interface form, Kotlin DSL builder form, and individual-callback form per BRD §6.2 FR4.
- A throwing listener routes to `onError` and never breaks the poll loop or other listeners (FR10, NFR5).
- **DoD:** unit tests for all three registration styles + error-isolation test.

### Story M4.2 — `BucketWatch` lifecycle & scheduling
- `create(...)` and `builder()`; lifecycle `initialize()` (load state, **emit no events**) → `start()` → `stop()` / `close()`; `checkNow()`; `isRunning()` / `getConfig()`.
- Wires scheduler + detector + listeners; thread-safe per the M4 spike.
- **DoD:** unit tests (mocked S3) prove: initialize fires nothing; first poll after a change fires events; stop halts polling; close releases the executor; concurrent `checkNow()` is safe.
- **Milestone payoff:** LIST_BASED watching works end to end.

---

## M5 — HEAD_BASED Mode

**Goal:** Add the efficient per-key watch mode for known files. *(BRD §6.3)*

### Spike M5 — HeadObject behavior & mode boundaries
Confirm `HeadObject` returns the ETag/size needed, and how a **404 (deleted key)** surfaces in the SDK so it maps to `Deleted`. Reconfirm the documented limitation: HEAD_BASED **cannot discover new keys**. Decide how watched keys are registered for this mode.
**Output:** HEAD_BASED contract + key-registration approach.

### Story M5.1 — HEAD_BASED detection path
- Per-key `HeadObject` adapter; feed results into the existing `ChangeDetector` (reuse, don't duplicate, the diff logic from M3).
- Missing key → `Deleted`; new keys are *not* discovered (by design).
- **DoD:** unit tests for present/modified/deleted-key transitions with mocked S3.

### Story M5.2 — Mode wiring & guardrails
- `BucketWatch` selects LIST_BASED vs HEAD_BASED from config; HEAD_BASED requires registered keys (validation/clear error if none).
- **DoD:** tests prove both modes run through the same lifecycle; mode-specific config errors are clear.
- **Milestone payoff:** v1.0 feature set complete.

---

## M6 — Integration Testing & Java Interop

**Goal:** Prove the library against real S3 semantics and from Java before publishing. *(BRD §8.3 integration tests, §9.4, NFR11/NFR14)*

### Spike M6 — LocalStack harness
Stand up Testcontainers + LocalStack S3, confirm the watcher works against it end to end, and settle test patterns (seeding objects, advancing/forcing polls deterministically via `checkNow()` rather than waiting on timers).
**Output:** reusable integration-test base.

### Story M6.1 — Integration tests for both modes
- LocalStack-backed tests: create/modify/delete objects, assert correct events for LIST_BASED and HEAD_BASED; pagination with many objects (NFR4-ish).
- **DoD:** integration suite green in CI (gated/taggable so it can be skipped in fast local runs).

### Story M6.2 — Java interop sample & verification
- A small Java source set or `examples/` Java program exercising builder + listener + lifecycle per BRD §9.4.
- **DoD:** Java sample compiles and runs against LocalStack; any awkward-from-Java API friction is fixed.

---

## M7 — Release & Documentation

**Goal:** Ship v1.0.0 to Maven Central with the docs the BRD promises. *(BRD §10 NFR9, §12 release checklist)*

### Spike M7 — Maven Central publishing
Work out the current Central Portal (Sonatype) path: namespace/`groupId` verification, GPG signing, `maven-publish` + signing Gradle config, snapshot vs release. Do a dry run / publish to a staging repo.
**Output:** working publish config, not yet released.

### Story M7.1 — Documentation & examples
- README with usage + badges; `docs/` getting-started, configuration, examples, API reference; `CHANGELOG.md`, `CONTRIBUTING.md`, Apache-2.0 `LICENSE`.
- `examples/basic-usage` (and a config-reload example).
- **DoD:** every public API documented (NFR9); a new reader can reach a working watcher in <15 min (NFR8).

### Story M7.2 — Publish v1.0.0
- Release pipeline (tag → build → sign → publish); GitHub Release.
- Run the BRD §12.2 release checklist.
- **DoD:** artifact resolvable from Maven Central; GitHub Release created; CHANGELOG reflects 1.0.0.

---

## Deferred (explicitly NOT in this plan — BRD §4.2)

Spring Boot starter (v1.1), coroutines/Flow & Reactor (v1.2), other cloud providers and distributed coordination (v2.0). Keep `bucketwatch-core` dependencies minimal (NFR17) so these stay clean add-ons later.
