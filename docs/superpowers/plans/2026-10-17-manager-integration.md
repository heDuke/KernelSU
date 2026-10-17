# Manager integration

Base: main `f3f9a23e`. Integrate reviewed branch contents as three independently
buildable commits, retaining the original remote branches for comparison.

- [x] ZIP: ksud-supported compression, typed preflight failures, localized error
  categories, exportable failure logs, metadata and error-category regressions.
- [x] Terminal: squash both dependent source commits, serialize startup/exit,
  clean up owned processes, verify root readiness and interrupt results, bound
  output, retain line-oriented (non-PTY) scope, run lifecycle regressions.
- [x] Updates: distinguish request errors, manifest errors and missing exact
  targets; preserve APK updates and exact-target validation; cover transitions.
- [x] Build each commit, run final clean Release build and unit suite with both
  native ABI payloads, open PR, run full CI, then merge into main.

Device acceptance remains on the PC side. This integration does not assert
Canary runtime acceptance or publish a new production release. Module-page
redesign remains a separate, confirmed-design implementation batch.

ZIP validation: `compileReleaseKotlin testDebugUnitTest --tests *ModulePackageChecksTest` passed.

Terminal validation: both reproduced lifecycle failures now pass, together with
root-readiness, background-before-start, failed-interrupt, bounded-output and
cleanup-after-owner-cancellation regressions. Release Kotlin compilation passed.
The shell requires Android toybox setsid; deliberately detached processes and
full PTY interactions remain outside the first version. Verify on the PC device.

Update validation: exact-target assessment, Canary mismatch, rejected metadata,
independent APK availability, invalid device targets and existing home/asset
regressions passed with Release Kotlin compilation. Full diagnostics are separate
from localized summaries; cancellation is propagated instead of becoming an error.

## Completion

- Integration PR: https://github.com/heDuke/KernelSU/pull/2 (merged).
- Feature head: `3a4d2b77a4418eea2ecbc51476dc0b9f62da965a`.
- Merge: `030ceb93f0ed4b9137501e5568a48a6a5ade4ca0`; its tree matches the
  tested PR feature head.
- CI: https://github.com/heDuke/KernelSU/actions/runs/38998256497 — all 18
  jobs passed, including seven KMI builds, both Android ksud ABIs, additional
  host targets, Manager unit tests and APK repacking.
- Local final suite: 76 tests, 75 passed, 1 skipped, no failures or errors.
  Release assembly passed; APK signature and both packaged ABI ELF payloads
  were verified against AGP stripped outputs.
- Real host subprocess tests cover stale PID rejection, explicit session cleanup
  and ordinary background jobs when the command shell exits normally.
- Actions artifact download is blocked by the current environment proxy's Azure
  HTTPS tunnel (403). Independent CI APK/report inspection and Android device
  acceptance remain with PC Codex. Local builds use test signing; no new
  production release was created.
