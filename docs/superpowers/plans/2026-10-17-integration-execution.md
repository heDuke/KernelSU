# Integration execution record

## Scope and source

The isolated `integration/upstream-20261017` worktree starts at
`e677d6291ccb3071c4f8fcab78fd5456b9df16b7` and merges upstream
`0faec0cc7b5f655170d89bf9c4d28bb5ca271fa2` with its ancestry. The
[104-commit disposition](2026-10-17-upstream-commit-disposition.json) records
intentional deviations. No experimental-loader implementation was copied from
upstream.

Implemented together:

- Coupled kernel/Rust/JNI UAPI 6, preinit and service-stage changes; retained local
  package/certificate, loader exact-target validation and signed-byte preservation.
- Optional official registry adapter alongside MMRL/custom providers, authenticated
  download metadata and final staging verification; one-shot script confirmation,
  actual hot-install/reboot result, opt-in boot module blocklist and ADB shell-root
  setting. The live official index returned HTTP 403, so it defaults off.
- One Discover repository entry, Remote/Local installation chooser, GitHub/JSON/ZIP
  discovery with explicit selection, native preview/install Bottom Sheets, compact
  release rows and omission of absent metadata. Multiple assets require selection.
- Enabled/disabled module cards with one main action and right-side More sheet.
  Superuser changes are confined to grouped application-list presentation.
- Native Expressive terminal output/composer/options, exact-command favorites,
  groups, pins, editing/reordering and local AtomicFile persistence.
- Explicit background mode, non-exported specialUse foreground service, private
  task actions, serial native supervisor, real exit/signal status, bounded private
  SQLite/log storage and adjustable queue/history/log caps. A re-run only fills
  the composer; it never executes automatically. Restarted history is interrupted,
  never replayed. Commands/favorites/logs are excluded from automatic backups.

## Validation

Local evidence is in `/workspace/artifacts/husky-integration-*` and the named
worker/loader/website logs. These paths are execution-machine evidence, not release
assets.

| Check | Result / scope |
| --- | --- |
| Manager clean assembleRelease and JVM tests | Passed; 186 tests, two optional fixture tests initially skipped |
| Manager real image/module fixtures | Passed: all 186 tests, no skips, using authentic 4K/16K researched images and pinned candidate |
| Android ARM64 Rust check, strict Clippy, fmt | Passed, including the new task supervisor; x86_64 Android check also passed |
| Native host tests | Contracts 7, supervisor 5, loader 6 passed; the separate pinned-candidate loader test also passed |
| Supervisor boundaries | Framed stdout cannot spoof exit, real nonzero exit, EOF cleanup, ordinary descendants after completion, TERM-to-KILL grace period |
| Python release/Canary regressions | 13 passed with real candidate/image/config and built module fixtures; observer C harness also passed |
| Observer harness | Failure cleanup, repeated/concurrent lifecycle, deferred task-work scan, exiting task and allocation/task-work failure fallback |
| Experimental loader | Unmodified source; 10 host tests passed, one optional target-proof fixture test initially skipped |
| ARM64 android14-6.1 kernel | Built successfully with real preinit runner/blob and explicit production signer/package |
| Website | bun install and production docs build passed; generated alternative lockfile was not committed |
| Source hygiene | Conflict markers removed; XML checked; diff whitespace checks passed |

The local Manager build used previously available ABI payloads to validate Kotlin,
resources, JNI and packaging. It is not a deliverable for the new task-runner
feature. Production delivery must rebuild both ksud ABI payloads and both supported
KMI modules from the exact final commit in the manual release workflow, then
independently verify the published APK, signer, embedded identity, native payloads
and checksums.

## Review limits and rollout

No Android emulator or KVM was available. Light/dark, large-font and sheet/keyboard
flows were reviewed in code and compiled, not visually accepted on a device.
Service restart/notification instrumentation, OEM background behavior and privileged
installation remain unverified runtime behavior. Host/JVM tests do not establish
those results. There is no PC or device acceptance gate, per the user's instruction.

The supervisor owns only its fresh process group and verified child lifetime. It
cleans ordinary descendants before reaping the leader and never sends signals to
an arbitrary persisted PID. Root commands can deliberately detach or change the
system; this is not a sandbox and arbitrary detached descendants are not promised
to remain supervised. Controller loss is interruption, not successful completion.
No pause/resume or percentage is invented for arbitrary commands.

Generic production KMI compilation does not certify ZP11.260918.007 private ABI/CFI
or cold-boot behavior. The earlier pinned 4K candidate's offline checks remain
separate evidence. The production package and sole trusted signer remain unchanged;
the separate old `.pr` package is not removed or overwritten.

Final source merge/push, two-KMI/two-ABI CI and public-asset verification are recorded
in the delivery addendum after the candidate completes them. Retain husky-v32650 as
the known-good reference; any rollback rebuild must respect increasing Android
version codes and compatible local data schemas.
