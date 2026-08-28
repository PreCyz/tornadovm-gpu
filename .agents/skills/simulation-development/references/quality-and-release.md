# Test, quality, and release policy

## Unit and integration tests

`test_engineer` owns test design and execution independently of the implementer.
It maps changed behavior to tests, writes missing tests in assigned test files,
and runs the smallest useful test set before the broader suite. Include:

- hand-checkable physics and boundary cases;
- deterministic CPU reference behavior and invariants;
- CPU/GPU parity with contract-derived tolerances;
- transfer/readback and fallback policy where testable without hardware;
- JavaFX state mapping and interaction logic separated from scene rendering;
- regression tests for every corrected defect.

Do not create tests that merely reproduce implementation details, assert source
text, or pass only because tolerances are widened. GPU-unavailable tests must be
reported as `UNVERIFIED` or explicitly skipped with the environmental reason.

## Quality triage and cleanup

`code_quality_engineer` examines the changed files and directly coupled code,
not the entire repository by default. Use IntelliJ `get_file_problems`, lint,
build, reformat, and safe refactoring capabilities when available. Run a pinned
SpotBugs Maven check or installed IDE integration when configured. Otherwise,
resolve a compatible version from official SpotBugs sources and invoke fully
versioned Maven plugin coordinates; do not silently edit `pom.xml`. Report an
unavailable tool honestly.

Classify every actionable finding:

- `CLEAN_NOW`: correctness, concurrency, resource, security, numerical,
  performance-cliff, confusing duplicated-domain-logic, or clear maintainability
  risk in the changed path. Fix it within assigned ownership.
- `DEFER`: valid but broad, unrelated, risky, or low-value cleanup. Record the
  reason and suggested future scope; do not churn code.
- `FALSE_POSITIVE`: tool output that is intentional or inapplicable, with a
  concrete justification.

After `CLEAN_NOW` edits, run focused tests immediately and return
`gate_status = UNVERIFIED` plus
`transition = CLEANED_REQUIRES_INDEPENDENT_RETEST`. The coordinator then invokes
`test_engineer`; quality becomes `PASS` only when that independent run passes.
An untouched clean scan returns `PASS`; an unavailable required IntelliJ or
SpotBugs check returns `UNVERIFIED`; an unfixed `CLEAN_NOW` item returns `FAIL`.

## Release package

`release_engineer` receives the requested version, accepted gate artifacts,
final diff, test/build evidence, and intended release scope. It must:

1. verify release version and working-tree scope;
2. update only owned version and release-documentation files;
3. write release notes with changes, physics/numerical behavior, CPU/GPU scope,
   compatibility, migration notes, fixes, known limitations, and verification;
4. run a clean Maven verification/package and inspect the produced artifact;
5. smoke-test launch when the environment permits;
6. provide tested `pwsh`/Maven source-run commands and packaged-release commands,
   including TornadoVM and device-selection prerequisites;
7. report artifact paths, checksums when useful, and PASS/FAIL/UNVERIFIED status.

Never invent a version. Never commit, tag, push, publish, upload, or create a
remote release without an explicit user request for that action. If release
instructions or commands cannot be tested, label them `UNVERIFIED`.

Every test, quality, build, and release report uses
`gate_status = PASS | FAIL | UNVERIFIED | NEEDS_INPUT`.
