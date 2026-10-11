# HuskySU: ZP11.260918.007 Android Canary adaptation plan

**Status:** Safety foundation implemented; candidate builds, remaining features
and device validation pending. See the
[implementation progress](2026-10-11-husky-canary-implementation-progress.md).
**Device:** Pixel 8 Pro (`husky`).
**Target:** `ZP11.260918.007`, Android Canary 2610.
**Repository baseline:** `4252df8cf47da5ffe2f2163d3d1226c372f4e8b7`.
**Release baseline:** `husky-v32630`.
**Implementation contract:** [reviewed details](../specs/2026-10-10-husky-canary-implementation.md).

The review includes the eight additional feature candidates F10-F17 and fixes
the target/asset models, trust policy, task lifecycle and delivery gates. New
APIs and capabilities in the linked contract remain proposed unless explicitly
marked implemented in the progress report.

## Verified target facts

The target was researched directly; it does not depend on the user supplying a
firmware link. Google's release announcement lists this build and Pixel 8 Pro.
The matching Google-hosted factory ZIP responds successfully and contains the
expected `husky_beta-zp11.260918.007` image package.

| Item | Observed value |
| --- | --- |
| Factory package | `husky_beta-zp11.260918.007-factory-00316935.zip` |
| Factory size | 3,994,531,598 bytes |
| Kernel release | `6.1.172-android14-11-g3fc92ed75e44-ab16169542` |
| Android KMI | `android14-6.1`, generation 11 |
| Public kernel commit | `3fc92ed75e44bfb2b12bc625fad7a054b939f8a6` |
| Compiler | Android Clang 17.0.2, build 10087095, r487747c |
| Default kernel pages | 4 KB (`CONFIG_ARM64_4K_PAGES=y`) |
| Optional `kernel_16k` pages | 16 KB (`CONFIG_ARM64_16K_PAGES=y`) |
| Kernel compression | Legacy LZ4 in both variants |
| boot image | Header v4; 64 MiB; kernel present; no ramdisk |
| init_boot image | Header v4; 8 MiB; ramdisk present; no kernel |
| Kernel features | Modules, module unload, modversions, Clang CFI, kallsyms, BTF |

The 4 KB and 16 KB variants have the **same release string and KMI**. An asset
selector using only uname/KMI cannot distinguish them. The default target is
not a new `android16-6.12` target. Existing 6.12 support must remain available
for its existing users and OTA paths, with regression tests.

The existing 6.1 release module has `6.1.166-dirty` vermagic. Its compiler string
matches the target compiler, which makes it useful for build regression;
the implementation rejects it for the 6.1.172 target. Matching compilers do
not prove ABI compatibility or page-size compatibility. KernelSU
uses kernel internals outside the stable vendor KMI contract.

Evidence was obtained through bounded HTTPS Range reads, boot header parsing,
LZ4 decompression, IKCONFIG extraction, and public source commit lookup. The
whole 4 GB factory ZIP was not downloaded or SHA-256 verified. No device was
flashed and no LKM was loaded. The 16 KB kernel ZIP entry was read completely
and its ZIP CRC checked; CRC is not an authenticity check.

Sources:

- [Google release announcement](https://www.reddit.com/r/android_canary/comments/1x07rlx/android_canary_2610_is_now_available/)
- [Google factory ZIP](https://dl.google.com/developers/android/CANARY/images/factory/husky_beta-zp11.260918.007-factory-00316935.zip)
- [Public kernel commit](https://android.googlesource.com/kernel/common/+/3fc92ed75e44bfb2b12bc625fad7a054b939f8a6)
- [Android Canary documentation](https://developer.android.com/about/canary)
- [AOSP stable KMI scope](https://source.android.com/docs/core/architecture/kernel/stable-kmi)

## Scope and milestones

Cover first installation, local image patching, direct LKM updates, late-load,
and retaining root through a supported Canary OTA. Validate default 4 KB mode
first, then optional 16 KB mode as a separate ABI/build target. Declare support
per build and mode only after the corresponding device checks pass.

Keep the production package and signing identity. Preserve kernel/ksud/JNI UAPI
contracts during Canary adaptation; F17 has a separate later capability/UAPI
milestone. Use the current ramdisk `boot-patch` production route. Moving users
to `boot-patch-v2` kernel injection is a separate feature and is not required
to support this Canary build.

## Phase 0: Pin the target and prepare reproducible fixtures

- [x] Locate the official target factory package through web research.
- [x] Inspect the default and optional kernels, configs, compiler, boot headers,
  and public kernel source commit.
- [ ] Record a trusted factory digest from available official build metadata;
  verify the complete archive when preparing installation fixtures. A filename
  hash prefix must not be treated as a verified SHA-256.
- [ ] Prepare matching stock boot/init_boot fixtures, configs and a matching
  kernel build environment/manifest. Store exact source and toolchain revisions.
- [ ] Record build fingerprint, actual page size, active kernel, BootControl HAL,
  and OTA state on the eventual test device. Device properties cannot be
  inferred from a factory image alone.

Output: an evidence manifest with source URL, hashes, kernel release, KMI,
generation, page size, config and test status; verified fixture hashes.

## Phase 1: Select modules by the actual target

Files: Manager `data/model/HuskyRelease.kt`,
`data/repository/HuskyReleaseRepositoryImpl.kt`, `ui/viewmodel/HomeViewModel.kt`,
`ui/util/KsuCli.kt`, `core/tasks/BootKernelVersion.kt`; ksud `assets.rs` and
`boot_patch.rs`.

- Replace the single fixed LKM URL with an indexed asset descriptor containing
  KMI, page size, architecture, source/build provenance, digest and size.
- Determine the target before selecting the module:
  - Direct updates: the running kernel and actual page size.
  - Local boot patching: the selected boot's kernel, not the running kernel.
  - Local init_boot patching: a matching companion boot or explicit validated
    target descriptor, because init_boot contains no kernel.
  - OTA: the prepared inactive slot and intended boot mode. Fail if target mode
    cannot be determined rather than borrowing the running slot's page size.
- Extract page-size evidence from ARM64 Image flags/config when inspecting an
  image; use the runtime page size for a running kernel. Define precedence when
  a user-selected 16 KB variant is involved.
- Add bounded legacy-LZ4 inspection. Manager currently recognizes framed LZ4,
  gzip and XZ, whereas both target kernels use legacy LZ4. Test the actual
  fixtures and reject contradictory/truncated page-size evidence. The 4096-byte
  boot-header alignment is not evidence of 4 KB kernel pages.
- Validate release digest, size, ELF type/architecture and the descriptor's
  target compatibility before packaging or flashing a module. DDK vermagic may
  omit the Android KMI tag, so filename parsing alone is insufficient.
- Bind asset digests and support records to a signed release manifest. A hash
  inside a user-created offline archive proves neither provenance nor support;
  use a pinned migration index for known unsigned baseline releases.
- Reuse a shared 6.1 asset only if both the old baseline and this build pass ABI
  and device validation. If private ABI differences prevent sharing, publish
  distinct indexed assets; do not silently overwrite a same-KMI variant.
- Use an explicit embedded resource index for page-size variants. Do not extend
  filenames and continue treating every `_kernelsu.ko` basename as a KMI.
- Review `ensure_gki_kernel()`'s patch-number-based predicate and replace it
  with supported-version/KMI/capability checks. Include valid low patch-number
  kernels and unsupported kernel families in rejection tests.

Acceptance: the same target always selects the same verified asset; a 4 KB
module is never selected for an unverified 16 KB target; unavailable or
ambiguous targets stop before a partition write.

## Phase 2: Bound loading and fix lifecycle failures

Files: `userspace/ksuinit/src/lib.rs`, `kernel/manager/pkg_observer.c`,
`kernel/core/init.c`, `kernel/runtime/boot_event.c`, and ksud `unload.rs`.

- Preserve useful same-KMI vermagic adaptation only within a validated ABI and
  page-size compatibility policy. Refuse cross-KMI retry and unknown variants.
  Retain enough target/asset identity to make the loader decision independently
  of the Manager UI. Boot-patch validation alone does not protect late-load.
- Compare target symbol availability, relevant function types/CFI requirements,
  structure layout and generated headers against the proposed module build.
  `check_symbol` against DDK vmlinux alone is insufficient target validation.
- Correct observer initialization error propagation, invalid group cleanup and
  the released-but-not-cleared directory path. Cover early unload too.
- Make unload failures observable after attempting to restore services;
  `delete_module` or service restoration errors must not report success.

Acceptance: incompatible input is rejected before module initialization;
allocation-failure cleanup and unload error cases retain valid state and return
the correct status. Use a dedicated test kernel for fault injection.

## Phase 3: Build the Canary candidates

Files: `.github/workflows/ddk-lkm.yml`, `husky-release.yml`, `ksud.yml`, and
asset preparation/repacking scripts.

- Start with `android14-6.1`, ARM64, 4 KB. Compare the existing 6.1.166 module to
  the target before deciding whether a DDK update/rebuild is needed.
- Pin a tested DDK/source/config/toolchain combination, preferably by digest
  and exact revision. Do not assume the current `20260313` tag matches this
  target merely because its KMI name matches.
- Prepare a separate 16 KB configuration and matching generated headers/build
  directory. Do not produce a 16 KB asset by renaming the 4 KB output or
  changing only vermagic. Verify ARM64 page-table/patch-memory assumptions.
- Keep existing 6.12 outputs and the x86_64 build path intact. Husky runtime
  acceptance concerns ARM64; passing x86_64 builds is regression coverage.
- Embed both required page-size resources in ksud, rebuild compressed assets
  cleanly, and verify embedded content against published digests.
- Keep production package/certificate identity, and propagate ksud extraction
  or installation failure into the final Manager installation result.
- Generate an asset/support manifest separating candidate, offline-checked and
  device-verified states. CI success alone must not mark a build supported.

Acceptance: reproducible candidates, matching resource manifests, production
signature checks, and exact embedded/published asset comparisons.

## Phase 4: OTA and failure recovery

Files: ksud `boot_patch.rs`, the embedded/extracted `bootctl` path, and Manager
flash/home flows.

- Verify AIDL BootControl support and extraction of the intended bootctl binary;
  the older HIDL failure is documented in this repository.
- Preflight HAL, valid slot information, completed OTA preparation, image
  compatibility, partition capacity and auxiliary-file readiness before writing.
- Prepare auxiliary files before slot activation. Record completed mutations,
  propagate partial-install errors, and restore state where recovery is valid.
- Verify the expected Virtual A/B snapshot/reboot sequence on the test device.
  Do not instruct users to enter an unprepared inactive slot.
- Review early `mark-boot-successful`: use the established system boot-health
  flow or a verified boot-completed stage, preserving failed-boot recovery.
- Document recovery using matching stock init_boot and the actually recorded
  slot/state. Do not prescribe a fixed slot `a` as a universal rollback.

Acceptance: failure before flashing leaves partitions/slots unchanged; failure
after a mutation identifies its extent; a completed supported OTA boots with
root and completes normal system update health checks.

## Verification matrix

| Case | Required result |
| --- | --- |
| Canary default 4 KB, fresh install | Correct ramdisk patch; normal boot; root and Manager recognition |
| Canary 4 KB, direct update | Correct asset; ksud install checked; root after reboot |
| Canary optional 16 KB | Correct variant; normal boot; root; correct module memory patching |
| Same uname/KMI, different page size | Distinct descriptor selection or explicit unsupported result |
| Existing supported 6.1 build | No regression after selecting shared/new 6.1 candidate |
| Existing 6.12 direct/OTA route | Matching 6.12 module; no accidental 6.1 selection |
| Local image differs from running kernel | Select target image identity; no current-kernel fallback |
| init_boot without a reliable target | Stop with actionable missing-target information |
| Corrupt asset or wrong architecture/KMI/page size | Reject before flashing/loading |
| Canary OTA after system preparation | Correct inactive target; slot/reboot/snapshot health preserved |
| HAL, helper write, slot activation, ksud install failure | Correct stage error and recovery state |
| Observer allocation failure / early unload / unload EBUSY | Safe cleanup and failure status |
| Legacy LZ4 / framed LZ4 / malformed compressed kernel | Bounded correct inspection; no compressed-string-only compatibility claim |
| Offline bundle with bad signature, digest or archive paths | Reject before private-cache promotion and execution |
| Concurrent CLI/Manager operation or stale preview | One mutation owner; changed slot/input returns structured failure |
| Worker loss before/during/after partition mutation | Journal reports observed extent; no automatic replay or false success |
| Reboot with same uname but wrong page mode/asset | Pending change remains failed/unconfirmed rather than successful |

After Rust code changes, follow `AGENTS.md` in order: `cargo ndk -t arm64-v8a
check`, `cargo ndk -t arm64-v8a clippy`, then `cargo fmt`; resolve failures.
Prepare ksud binaries before local Manager Gradle checks. Run targeted Manager
selection/patching tests and the relevant release build. Kernel changes require
the matching ARM64 builds and symbol/ABI checks. Device smoke tests must include
root grant/deny, SELinux enforcement, modules, reboot and suspend/resume.

## Stability enhancement backlog

These items extend Phases 1-4. Priority means delivery order: P0 is required
before declaring public Canary support; P1 follows the core stability work.
Existing environment checks, image backup/restore and safe mode are foundations
to extend, not features presumed missing from the repository.

| ID | Priority | Enhancement | Acceptance |
| --- | --- | --- | --- |
| S1 | P0 | Target compatibility gates across every entry point: device, selected kernel, KMI, page size, architecture and asset identity | Direct update, local patch, late-load and OTA use the same validated policy; wrong or unresolved targets cannot reach flashing/loading |
| S2 | P0 | One active boot/LKM mutation task, with unique temporary files and a persistent stage journal | Concurrent Manager/CLI attempts are rejected; reopening the app shows the real task state; interruption never blindly replays a partition write |
| S3 | P0 | Reliable downloads: bounded retries/timeouts, digest/size checks, atomic cache promotion and stale-job cleanup | Interrupted or mismatched assets are rejected; failed jobs cannot replace a verified artifact; root writes are excluded from automatic retries |
| S4 | P0 | Reliable backups: target identity, hashes, durable writes and retention of a known-good recovery image | Required backup failure aborts before flashing; another build/slot's image cannot be silently restored; retention does not discard the last usable recovery image |
| S5 | P0 | Partition I/O preflight and readback verification | Image size fits the actual partition; target slot is rechecked immediately before writing; written bytes are verified; partial-write failures report the affected partition without automatic retry |
| S6 | P0 | Safe loader/lifecycle cleanup and truthful stage results | Observer failures/early unload are safe; cross-KMI or unverified page-size retry is refused; unload, bootctl and ksud installation errors remain visible |
| S7 | P0 | OTA preparation, slot transaction and boot-health completion | Unprepared slots are blocked; helper setup precedes activation; intermediate failure reports completed mutations; boot success is not declared before the validated health stage |
| S8 | P0 tests; P1 polish | Bounded resource use and meaningful regression/failure tests | S1-S7 fault/regression tests gate support; required operation limits are P0; later resource/UI polish follows, with measured boot/suspend behavior for each supported mode |

S2 must use a ksud/device-wide lock, not merely disabled UI buttons. Distinguish
download cancellation from a partition write that must finish or return a
recorded failure. A timeout in the UI is not proof that the daemon stopped.

S4 addresses a concrete current behavior: `boot_patch.rs` logs a failed required
`do_backup` and continues. Define and enforce the required-backup policy for
each destructive installation path. Backup metadata must identify the source
build and whether the image is stock or already patched; a patched image must
not become a mislabeled stock recovery point.

S5 cannot make a raw partition write atomic across power loss. Preserve recovery
evidence and expose the partial state. Recovery may require matching firmware
and an external computer, especially if root or normal boot is lost.

## Planned user-facing features

These features reuse the stability primitives above. Keep exact-build/page-size
support states explicit; preserve the existing Material 3 Expressive component
system. Unknown status is visible and is never rendered as a passed check.

| ID | Delivery | Feature | User-visible behavior and acceptance |
| --- | --- | --- | --- |
| F1 | First batch | Compatibility center | Shows device/build, kernel, KMI, actual page size, installed/target LKM, Manager/ksud versions and tested status; explains the reason for a mismatch |
| F2 | First batch | Installation preview / dry run | Shows source image, target partition/slot, selected module, backup destination and planned steps before mutation; backend preview cannot flash or activate a slot |
| F3 | First batch | Task center and installation history | Stage progress, logs and final result survive navigation/app restart; history records build, mode, asset digest and affected slot; cancellation is offered only in safe phases |
| F4 | First batch | Backup and recovery center (existing capability upgrade) | Lists verified recovery images by build/slot, supports export and compatible restore; when root is unavailable, provides matching external recovery instructions rather than claiming in-app one-click repair |
| F5 | First batch | Canary OTA assistant | Shows current/target slot, OTA readiness, target KMI/page size and matching LKM; guides prepare, patch and system reboot while preserving the platform update workflow |
| F6 | Later batch | Diagnostic bundle export (environment report upgrade) | Combines environment results, task history and selected boot/LKM logs into a local bundle; redacts sensitive identifiers and lets users review before sharing; no automatic upload |
| F7 | Later batch | Stable/experimental release channels | Separates validated and candidate releases, filters by compatibility, and exposes a compatible previous module version where available; lower version alone never establishes rollback compatibility |
| F8 | Later batch | Post-boot health report | Compares expected and actual LKM/ksud versions, root readiness, SELinux state and module-stage failures; distinguishes daemon/UI delay from a failed boot-stage operation |
| F9 | Later batch | Module troubleshooting assistant (safe-mode upgrade) | Prepares a minimal next-boot module environment and staged re-enablement to locate conflicts; retains a documented recovery route and does not promise to recover from every kernel/firmware boot failure |
| F10 | Core UI batch | Offline adaptation bundle | Imports a signed manifest and matching LKM through the document picker; enforces the same target/ABI checks as online assets; unsupported or untrusted bundles do not become supported merely by import |
| F11 | Independent host milestone | Desktop-assisted installation CLI | Collects optional ADB evidence and patches a verified local image after cross-platform parity checks; exports image and specific instructions; no automated unlock/wipe/flash/slot switch |
| F12 | Later batch | Configuration snapshot and diff restore | Previews restoration of supported settings, app profiles and installed-module state; matches package/user/signing identity to current UID; never restores stale UID allowlists or implicitly installs modules |
| F13 | Core UI batch | Pending-reboot change list | Distinguishes runtime and next-boot LKM/userspace/module state; reconciles boot ID, slot, asset and actual page mode; matching uname alone cannot close a pending change |
| F14 | Later batch | Module startup timing | Shows blocking-script durations/exit results and nonblocking spawn overhead by stage/module; keeps execution order and does not time an entire long-lived service as boot delay |
| F15 | Later batch | Before/after update comparison | Compares recorded boot-stage timings, failures and reboot evidence with sample counts; missing baselines stay unknown; no unsupported battery/performance attribution |
| F16 | Later batch | Canary build monitor | Separates new firmware availability from verified HuskySU support; manual refresh first, bounded optional notifications later; documented source or curated signed index, no guessed compatibility |
| F17 | Separate kernel milestone | Temporary root authorization | Kernel-enforced one successful grant or time-limited future grants; expires at reboot, requires negotiated capability and concurrent-grant tests; does not revoke credentials already acquired |

F1 must distinguish firmware identity, actual boot mode and asset identity.
Both kernel variants in this factory image share uname/KMI, so a display based
only on these strings is insufficient. This is a diagnostics/install selection
feature; automatically replacing the system's kernel or changing page-size
mode is not part of it.

F2 should expose a structured backend plan, reused by F3 and F5, so displayed
steps and executed steps do not drift. Mutating preflight actions must not run
during a read-only preview. Revalidate device/slot/OTA state at execution time.

F4 extends current stock-image restoration. F6 extends the existing copyable
environment report. F9 extends existing safe mode; it is scoped to third-party
module troubleshooting and must not disable baseline platform protections.

F3 extends the existing `DownloadService` dataSync foreground service with
durable jobs, verification and OS-timeout cleanup. Current in-memory
`DownloadManager` callbacks are not sufficient recovery state. Consolidate the
HuskyRelease download path under this coordinator. Root partition operations
remain owned by a supervised ksud task; closing the Manager cannot cancel a
critical write. Download resume validates the same source/version and final
digest. Follow Android background/boot-start restrictions rather than enabling
the separate Magica boot receiver to restart downloads.

F10/F13 reuse the shared asset/task/boot contracts and join the first UI batch.
F11 has a separate host feasibility and output-parity gate, since several ksud
dependencies are currently Android-only. F12/F14-F16 are later independent
increments. F17 changes grant semantics and needs new negotiated IOCTLs; it
cannot be implemented safely as a Manager timer or a consuming UID query.
See the implementation contract for per-feature fields, boundaries and tests.

## Delivery and support declaration

Suggested implementation batches:

1. Shared schemas, signed manifest/migration policy and exact target fixtures;
   asset descriptors, target/page-size detection including legacy LZ4,
   compatibility gates and download verification (S1/S3; F1/F10 foundation).
2. Loader bounds, observer cleanup, unload and stage error propagation (S6).
3. Device-wide task locking, journals, verified backups and partition I/O
   validation (S2/S4/S5; F2/F3/F4 foundations).
4. Reproducible 4 KB candidate, embedded-resource validation and CI metadata;
   targeted fault/regression/resource checks (S8).
5. OTA preflight/recovery, boot-health timing and Manager stage results
   (S7; F5 foundation).
6. Ship F1-F5, F10 and F13 progressively using shared backend primitives,
   after the associated acceptance checks pass. UI completion does not delay
   independently verified safety fixes.
7. Build and accept the separate 16 KB candidate, preserving independent mode
   support status; update README/device guide/release notes with tested modes.
8. Deliver F6-F9, F12 and F14-F16 independently after the baseline is stable;
   check identity-aware restoration, module scheduling/timing and monitoring
   provenance. F11 follows its independent host feasibility/parity gate.
9. Deliver F17 only after the separate kernel/UAPI design, atomic grant and
   expiry tests, and regression checks pass. Unsupported older kernels must
   never turn temporary authorization into permanent authorization.

The first support milestone is verified 4 KB operation on this exact build.
The second is independently verified 16 KB operation. Mark each mode pending
until its tests pass; do not advertise blanket support for all future `ZP11`
builds. Future Canary builds require target inspection and regression results.

Planning deliverables are this document, the linked reviewed implementation
contract, stability/feature backlogs and adjacent target evidence JSON. The
backlogs are planned work, not completed runtime features. Implementation can
start with contracts/fixtures and safety fixes; public support still requires
verified artifacts and mode-specific device acceptance.
No runtime implementation, firmware installation or release publication is
performed as part of this planning task.
