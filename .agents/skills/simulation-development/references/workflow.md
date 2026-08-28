# Simulation development workflow

## Risk router

The primary records `risk_tier` and its reason before spawning agents:

| Tier | Trigger | Minimum route |
| --- | --- | --- |
| `T0` | Nonfunctional documentation, comments, agent config, or formatting | Primary only; proportionate checks |
| `T1` | Small isolated fix without physics, numerical, GPU, concurrency, JavaFX-thread, public-contract, or release impact | Relevant implementer, targeted IntelliJ inspection, `test_engineer`; quality specialist only for meaningful findings |
| `T2` | Normal behavior feature without physics/GPU/release risk | `implementation_lead`, relevant implementers, tests, quality, final build |
| `T3` | Physics, integrator/tolerance, TornadoVM kernel/transfer/sync, concurrency/frame pacing, or performance claim | Physics/partition gates as applicable, then the complete implementation pipeline |
| `T4` | Versioned release or prerelease | Required underlying tier, then release gate |

Use the highest tier touched by the change. Escalate after discovery when a
trigger appears. Do not run the architect for `T0`–`T2`, and do not downgrade a
real risk merely to save tokens.

## Phase 0: intake

Before derivation, identify the intended phenomenon, dimensions, scale, force
or interaction family, boundaries, collision behavior, accuracy target, and UI
behavior. The primary agent discovers these from the request and repository
when possible and obtains user acceptance of any proposed assumptions. A
spawned coordinator that finds a material gap returns `NEEDS_INPUT` with exact
questions and stops. Do not silently inherit n-body gravity merely because this
repository contains it.

## Phase 1: physics contract

`physics_lead` first asks `physics_theorist` for a candidate contract. It then
passes that exact contract to `physics_sanity_reviewer` and
`math_numerics_reviewer` in parallel. The lead resolves findings and returns:

- modeled phenomena and deliberately excluded effects;
- equations with symbol definitions and units;
- initial and boundary conditions;
- invariants or expected monotonic quantities;
- singularity, collision, and softening policy;
- numerical integrator, timestep policy, accuracy order, and stability limits;
- testable predictions and unresolved assumptions.

Return two separate artifacts:

- `physics_contract`, containing the resolved model;
- `validation_report`, containing every reviewer finding, severity,
  disposition, evidence, reference-test plan, and gate status.

The gate passes only when dimensional and algebraic checks pass and every
high-severity sanity or numerical objection is resolved. Physics-invalidating
findings cannot be waived. Another high-severity risk may be accepted only by
the user with its rationale and residual risk recorded.

## Phase 2: CPU/GPU partition

`compute_partition_lead` gives the accepted physics contract and relevant code
paths to `tornadovm_researcher`. It develops a candidate partition, then asks
`partition_challenger` to attack it. Return one row per operation:

| Operation | Owner | Why | Inputs/outputs | Transfer/sync | Validation |
| --- | --- | --- | --- | --- | --- |

Use CPU for orchestration, JavaFX scene-graph work, irregular low-volume logic,
and operations whose transfer or launch overhead dominates. Consider GPU for
large regular data-parallel loops with supported data types and enough work per
transfer. Treat this as a hypothesis until measured.

Require a TornadoVM version match, supported-feature evidence, a task-graph and
device-residency plan, transfer cadence, warmup/synchronization points, CPU
fallback, parity tolerances, and an end-to-end benchmark design.

## Phase 3: implementation

`implementation_lead` defines interfaces and assigns non-overlapping files.
Then it may run at most two of `cpu_implementer`, `tornado_gpu_implementer`, and
`javafx_implementer` concurrently when boundaries are genuinely disjoint; it
runs the third after a slot is free.

Before each later write phase, the coordinator creates a new ownership map:
assigned test files for `test_engineer`, cleanup-eligible source/test files for
`code_quality_engineer`, `pom.xml` or other build files for
`maven_build_engineer` when changes are allowed, and version/release-note files
for `release_engineer`. No ownership is implied by an earlier phase.

The CPU path is the oracle. Compare GPU results on tiny hand-checkable cases,
deterministic seeded cases, edge cases, and a realistic case. Tolerances must
follow the physics contract. The gate requires compilation, focused tests,
CPU/GPU parity, and a short record of commands run. If a configured TornadoVM
runtime and device are unavailable, report `UNVERIFIED` and do not describe the
accelerated implementation as complete. The implementation gate requires
preliminary compilation and focused checks; final broad Maven verification is a
separate post-quality build gate. Performance claims require measurements;
correctness is never inferred from visual similarity.

## Phase 4: tests, quality, and cleanup

After integration, run `test_engineer` to inspect the implementation, add
missing unit and parity tests, and execute focused tests. Then run
`code_quality_engineer` over the changed code and directly coupled paths. It
uses IntelliJ inspections and configured SpotBugs checks, classifies findings,
and fixes only findings marked `CLEAN_NOW`.

If cleanup changes any source or test file, run `test_engineer` again with the
cleanup diff and prior test evidence. Only after that independent retest passes
does `maven_build_engineer` run the final broad verification. The quality gate
cannot pass with an untested cleanup.

The implementation handoff always contains tested commands for running the
affected application during development.

## Phase 5: release when requested

Run `release_engineer` only after implementation, test, quality, and build gates
pass. One exception is an explicitly requested prerelease: when every non-GPU
gate passes and implementation is `UNVERIFIED` solely because GPU parity could
not run, the primary may obtain the user's explicit acceptance and dispatch a
prerelease marked `UNVERIFIED`. It requires the intended version; if missing,
it returns `NEEDS_INPUT`.
It prepares the version change, release notes, clean package, artifact checks,
and verified instructions for running from source and from the packaged
release. Stable release status requires GPU parity where the release claims GPU
support; unavailable parity can only produce an explicitly accepted prerelease
marked `UNVERIFIED`.

Committing, tagging, pushing, publishing artifacts, or creating a remote release
is outside release preparation and requires explicit authorization.

## Gate status contract

Every specialist returns exactly one `gate_status`: `PASS`, `FAIL`,
`UNVERIFIED`, or `NEEDS_INPUT`, with evidence and next action. A quality cleanup
may also return `transition = CLEANED_REQUIRES_INDEPENDENT_RETEST`; its quality
gate remains `UNVERIFIED` until `test_engineer` returns `PASS`, after which the
coordinator records quality `PASS`. Unfixed `CLEAN_NOW` findings are `FAIL`.
Unavailable required IntelliJ or SpotBugs checks are `UNVERIFIED`, not `PASS`.
The Maven engineer reports aggregate build status, not only GPU status.

## Final package

Return gate status for intake, physics, validation, partition, implementation,
tests, quality, build, and optional release; both physics artifacts; the CPU/GPU
table; final changed files and ownership; unit and parity cases/results; the
quality triage and cleanup list; exact focused and broad Maven commands;
development and release launch/device instructions; benchmark method and
measurements or `NO PERFORMANCE CLAIM`; release notes when applicable;
assumptions, runtime limits, and every unverified item.

## Context and token discipline

- Give each leaf only the accepted artifact, relevant files/symbols, and output
  schema.
- Use at most two concurrent leaves under a spawned coordinator so the
  configured three-thread cap is respected.
- Parallelize independent reading and review, not dependent decisions.
- Summarize tool output; preserve exact references, equations, failures, and
  source links.
- Reuse accepted artifacts instead of asking later agents to rediscover them.
- Escalate reasoning only for ambiguity, cross-domain conflicts, or failed
  validation—not routine scans and builds.
- Do not spawn unused phase agents. Pass `risk_tier`, accepted artifacts, the
  smallest relevant file/symbol set, and the expected output schema to each
  leaf.
