---
name: simulation-development
description: Design, validate, partition, implement, test, clean, or release physics simulations that combine a Java CPU reference, TornadoVM acceleration, and JavaFX visualization. Use for simulation laws, numerical methods, CPU/GPU boundaries, TornadoVM kernels, parity and unit testing, IntelliJ/SpotBugs quality checks, Maven verification, release notes, and run instructions in this repository.
---

# Simulation Development

Use staged evidence and bounded specialist delegation. Read
[references/workflow.md](references/workflow.md) for the relevant phase and
[references/project-context.md](references/project-context.md) when locating
code, commands, or TornadoVM documentation. Read
[references/quality-and-release.md](references/quality-and-release.md) for test,
cleanup, or release work.

## Route the request

First assign the risk tier defined in
[references/workflow.md](references/workflow.md). Use the smallest route that
satisfies that tier:

- `T0`: primary agent only; no subagents.
- `T1`: one relevant implementer plus `test_engineer`; use
  `code_quality_engineer` only when targeted inspection finds meaningful work.
- `T2`: use `implementation_lead` and its implementation/test/quality/build
  pipeline.
- `T3` end-to-end work: use `simulation_architect`.
- `T4`: run the required underlying tier, then `release_engineer`.
- Physics selection or correction: use `physics_lead` as a `T3` phase route.
- CPU/GPU placement or performance architecture: use
  `compute_partition_lead` as a `T3` phase route.
- Approved normal/high-risk implementation work: use `implementation_lead`.
- Focused test or cleanup work: use `test_engineer` or
  `code_quality_engineer`.
- Explicitly requested release work: use `release_engineer` after all required
  gates pass.

The coordinator must spawn the leaf specialists named in its custom-agent
instructions. Do not replace independent validation with self-review. Keep the
hierarchy to coordinator plus leaf agents unless the user explicitly requests a
deeper decomposition.

## Stage gates

Apply only the gates required by the selected risk tier. Do not advance when a
required artifact is missing or a high-severity objection is unresolved:

0. Intake: intended phenomenon, dimensions, scale, boundaries, interactions,
   accuracy target, and visualization behavior are known or the user's explicit
   acceptance of proposed assumptions is recorded.
1. Physics contract: laws, equations, assumptions, units, initial/boundary
   conditions, invariants, and numerical method.
2. Validation report: reviewer findings and dispositions, dimensional/algebraic checks, limiting cases,
   conservation expectations, numerical stability, and executable reference
   tests.
3. Compute partition: an explicit CPU/GPU table backed by code evidence,
   official TornadoVM documentation, data-transfer analysis, and a challenged
   alternative.
4. Implementation: CPU oracle, GPU path, JavaFX integration, preliminary
   compilation/focused checks, owned files, and verified development-run
   instructions. If GPU parity cannot run, status is `UNVERIFIED`, never `PASS`.
5. Quality: missing tests are implemented and executed; IntelliJ and SpotBugs
   findings are triaged; worthwhile cleanup is completed and independently
   retested.
6. Build: broad Maven verification runs after all cleanup and retesting.
7. Release, when requested: version, release notes, clean package, checks,
   limitations, and source/artifact run instructions are complete. Tags,
   publishing, and pushing remain explicit-only actions.

Every specialist returns `gate_status = PASS | FAIL | UNVERIFIED |
NEEDS_INPUT`; cleanup may also return
`transition = CLEANED_REQUIRES_INDEPENDENT_RETEST`.

Prefer concise decision records and targeted evidence over large transcripts.
When only one phase is requested, perform that phase without manufacturing the
others. Escalate the tier when discovered scope requires it; never downgrade a
tier merely to reduce token usage.
