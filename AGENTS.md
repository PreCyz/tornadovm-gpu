# TornadoVM Simulation Agent Guidance

This repository uses project-scoped custom agents from `.codex/agents/` and the
`simulation-development` skill from `.agents/skills/`.

## Routing

- For work that changes or evaluates simulation physics, CPU/GPU placement,
  TornadoVM kernels, or JavaFX simulation UI, use `simulation-development`.
- For end-to-end work, delegate to `simulation_architect`.
- For one phase, delegate to `physics_lead`, `compute_partition_lead`, or
  `implementation_lead`.
- For focused verification or cleanup, delegate to `test_engineer` or
  `code_quality_engineer`. For an explicitly requested release, delegate to
  `release_engineer` after implementation, test, quality, and build gates pass.
  The only exception is an explicitly accepted `UNVERIFIED` prerelease when all
  non-GPU gates pass and GPU parity alone could not run.
- The end-to-end architect directly uses leaf specialists; it does not nest the
  phase coordinators. Phase coordinators are alternative entry points for
  single-phase work. Leaf agents do not create more agents unless explicitly
  asked. This bounded hierarchy avoids recursive delegation and wasted context.

## Risk-based routing

Before delegation, the primary records one risk tier and a one-sentence reason:

- `T0 — trivial/nonfunctional`: documentation, comments, agent configuration,
  or formatting with no runtime behavior change. The primary handles it without
  subagents and performs only proportionate checks.
- `T1 — isolated low risk`: a small local fix with no physics-law, numerical,
  TornadoVM kernel/transfer, concurrency, JavaFX-thread, public-contract, or
  release impact. Use one relevant implementer and `test_engineer`. Run targeted
  IntelliJ problems/inspection; spawn `code_quality_engineer` only when findings
  or the diff justify specialist cleanup.
- `T2 — normal feature`: behavior changes that do not alter physics, numerical
  stability, GPU partitioning, or release state. Use `implementation_lead`, the
  relevant implementer(s), independent tests, quality review, and final build.
- `T3 — physics/GPU high risk`: laws, equations, integrators, tolerances,
  TornadoVM kernels, transfers, synchronization, frame pacing, concurrency, or
  performance claims. Use the applicable physics and partition gates plus the
  complete implementation, test, quality, and build pipeline.
- `T4 — release`: use the required underlying tier and then the release gate.

If scope crosses tiers, use the highest tier. Escalate when exploration reveals
a higher-risk trigger. Never downgrade solely to save tokens. Do not spawn the
full architect for `T0`–`T2` work.

## Delegation policy

- Delegate bounded work with a concrete output. Keep requirements, decisions,
  and final synthesis in the parent context.
- The primary agent owns user intake before delegation. If a spawned agent finds
  a materially missing choice, it returns `NEEDS_INPUT` with exact questions and
  stops; the primary relays them to the user.
- Run independent read-only reviews in parallel. Run dependent phases in order:
  physics, validation, CPU/GPU partition, implementation, verification.
- Do not run multiple write agents against the same file. The coordinator must
  assign phase-specific file ownership before every write agent: implementation
  production files, test files, cleanup-eligible files, build files such as
  `pom.xml`, and version/release documentation. Ownership ends at phase handoff
  unless explicitly reassigned.
- Return conclusions, evidence, assumptions, risks, and file/symbol references;
  do not return raw search output or full build logs.
- Stop at a stage gate when a high-severity objection is unresolved.
- Physics-invalidating and parity-invalidating findings cannot be waived. Other
  high-severity risks require the user to accept a written rationale and
  residual risk; agents cannot waive them themselves.
- For `T2`–`T4` implementation, `test_engineer` writes and executes missing unit
  and parity tests. Then `code_quality_engineer` runs IntelliJ inspections and
  SpotBugs when configured, triages findings, and fixes only worthwhile issues.
  Any cleanup must be followed by another independent `test_engineer` run. `T1`
  still requires tests, but uses the quality specialist only when targeted IDE
  inspection or the diff identifies meaningful cleanup.
- Every implementation handoff includes verified development-run instructions.
  Every release includes release notes and verified source and packaged-artifact
  run instructions.
- Creating commits, tags, pushing, publishing artifacts, or creating a remote
  release requires an explicit user request. Release preparation alone does not
  authorize those external or repository-history changes.
- Every specialist returns `gate_status = PASS | FAIL | UNVERIFIED |
  NEEDS_INPUT`. Coordinators do not advance without the required explicit
  terminal status. Cleanup may additionally return
  `transition = CLEANED_REQUIRES_INDEPENDENT_RETEST`.

## Repository workflow

- If `.codegraph/` exists, use CodeGraph before text search for code discovery.
- Use PowerShell for command-line work.
- Use `mvnd` for Maven commands, falling back to `mvn` only when unavailable.
- Keep JavaFX scene-graph mutation on the JavaFX Application Thread.
- Treat the CPU implementation as the correctness oracle for GPU parity tests.
- Validate GPU decisions against the TornadoVM version in `pom.xml` and official
  TornadoVM documentation. Measure transfer and synchronization cost; do not
  infer a speedup from parallel structure alone.
