# HuskySU Canary: reviewed implementation contract

**Status:** Design reviewed against baseline
`4252df8cf47da5ffe2f2163d3d1226c372f4e8b7`; safety foundation implemented,
remaining milestones and device validation pending. See the
[implementation progress](../plans/2026-10-11-husky-canary-implementation-progress.md).
**Target:** Pixel 8 Pro (`husky`), `ZP11.260918.007`, 4 KB first.
**Parent:** [adaptation plan](../plans/2026-10-10-husky-canary-zp11.260918.007.md).

This document fixes the implementation boundaries and acceptance criteria. New
types, commands, files and capabilities below remain proposals unless explicitly
marked implemented in the progress report.
The current ramdisk installation route and production identity remain in use.
No device compatibility claim follows from approving this design.

## 1. Review findings and decisions

| Finding in current source | Implementation decision |
| --- | --- |
| `HuskyReleaseRepositoryImpl` selects one fixed 6.1 asset; `HomeViewModel` downloads through that repository | Introduce indexed assets and route verified release downloads through one download coordinator |
| `DownloadService` already provides a dataSync foreground service; `DownloadManager` holds state/callbacks only in memory | Extend this service with durable jobs and verification; callbacks cease to be the source of truth; no second download framework in the first milestone |
| Manager `BootKernelVersion` recognizes framed LZ4, gzip and XZ; target kernels use legacy LZ4 | Add bounded legacy-LZ4 image inspection and real target fixtures; raw compressed-string scanning is not sufficient page-size evidence |
| `boot_patch.rs` can choose current KMI before inspecting the local target; explicit `-m` can bypass asset selection | Authoritative target and asset checks run in ksud for every path, including user-selected modules |
| Both target variants share uname/KMI | Actual runtime/kernel Image page size participates in identity; boot-image header alignment is not kernel page size |
| Loader retries a vermagic replacement without a target policy | Carry a compact asset compatibility descriptor into the patched ramdisk and check it independently in ksuinit; late-load uses the same policy |
| `module::exec_script` discards blocking script exit status and asynchronous Child handles | Preserve exit status for health reporting; observe asynchronous startup without waiting for long-lived services |
| `allowlist.c` grants access through UID policy, with Manager/shell and already-root paths | Temporary grants need a separate atomic kernel grant operation; do not consume a one-use grant in a permission/query check |
| serde_json and ZIP dependencies are currently Android-only in ksud | Put host-compatible parsing/selection contracts in a small shared Rust crate; explicitly build host and Android targets before shipping desktop tooling |

Retain `LoaderClient` for boot/LKM/slot operations and `PackageClient` for
module operations. Add structured methods behind these facades. This work does
not require the separate dual-APK migration. Existing environment reports,
backup restoration and safe mode are extended rather than recreated.

## 2. Shared data contracts

Use schema version 1 with explicit enums, byte counts, SHA-256 hex digests and
UTC timestamps for display. Durations and timeouts use monotonic clocks. JSON
on stdout is for machines; diagnostic text goes to stderr. Consumers tolerate
unknown optional fields, but reject unsupported required schema/capabilities.

Proposed shared crate `userspace/ksu-contracts` contains models, bounded image
inspection, asset matching and plan validation. It has no Android property,
root IOCTL or partition-writing dependency. ksud supplies platform probes and
execution; Manager maps the versioned JSON into Kotlin models. UI probes can
give an early estimate, but ksud's result controls execution. CI golden fixtures
must produce equivalent results in the UI and authoritative backend.

| Model | Required fields / rules |
| --- | --- |
| TargetIdentity | device, firmware build/fingerprint when known, architecture, kernel release, KMI and generation, kernel page size in bytes, config/source identity when available, boot/init_boot SHA-256 when inspecting files, slot, evidence source and confidence |
| AssetDescriptor | immutable asset ID, module version, architecture, KMI/generation, page size, size, SHA-256, source/toolchain/config provenance, verified compatibility records, release channel and download URL |
| CompatibilityRecord | exact build + mode + asset digest + test report identity; separate `candidate`, `offline_checked`, `device_verified`, `unsupported`, `unknown` states |
| InstallPlan | schema version, operation, input hashes, target identity, selected asset digest, slot/partition identity and capacity, backup policy, ordered stages, blocking reasons, warnings, required capabilities and canonical plan digest |
| TaskRecord | random task ID, operation/plan digest, boot ID, state/stage, owner process identity, timestamps, bytes/progress, stage outcomes, completed mutations, recovery reference and structured error |
| BackupRecord | image SHA-256/size, build/device/slot/partition, source stock/patched/unknown classification, acquisition evidence, original task ID and verification state |
| PendingChange | task ID, previous boot ID, expected slot, written image digest, expected asset digest, expected page size and userspace version; observed completion is separate |

Define canonical InstallPlan encoding for hashing: deterministic field order,
integer byte counts, explicit nulls and no floating-point values. Exclude
display-only labels, progress and timestamps. Include input/asset digests,
target boot ID where applicable, slot/partition identity and every mutation
option. Cross-language golden vectors prevent different plan digests for the
same execution request.

Asset provenance and support are separate: a correctly signed candidate is not
a device-verified module. An offline-checked module is not advertised as
supported. Do not infer stock-image authenticity from a label or hash alone.

Example target fragment; unknown fields remain null rather than borrowed from
the currently running firmware:

```json
{
  "schema_version": 1,
  "device": "husky",
  "firmware_build": "ZP11.260918.007",
  "architecture": "aarch64",
  "kernel_release": "6.1.172-android14-11-g3fc92ed75e44-ab16169542",
  "kmi": "android14-6.1",
  "kmi_generation": 11,
  "page_size_bytes": 4096,
  "slot": null,
  "evidence_source": "factory_kernel_image",
  "support_state": "candidate"
}
```

## 3. Target selection and loader enforcement

1. Running-kernel operations probe uname, actual kernel page size and available
   firmware identity. Do not use ELF alignment, UI density or Android's
   userspace page-size compatibility/emulation mode as a kernel-mode signal.
2. Local boot operations inspect the selected kernel. Decode legacy LZ4,
   framed LZ4, gzip/XZ and uncompressed ARM64 Image with explicit size/bounds
   checks. Use ARM64 Image page-size flags; cross-check IKCONFIG when available.
   Contradictory evidence stops selection. Decompression has bounded output,
   checked arithmetic and cancellation; malformed/truncated data cannot pass.
3. init_boot contains no kernel. Require a matching companion boot, or a
   validated descriptor bound to the input images/build. A user-entered KMI or
   the running kernel is not enough to establish the intended page mode.
4. OTA inspects the prepared inactive target and intended mode. Slot metadata,
   snapshot/update readiness and image evidence must agree. Do not substitute
   the active slot's page size. No automatic page-mode switch is included.
5. Match device/build policy, architecture, KMI/generation, page size and asset
   digest. Unknown or ambiguous critical evidence is a blocking result. Existing
   supported targets need explicit migration records and regression coverage;
   this work must not globally relabel all older builds unsupported.

The loader descriptor is versioned and size-bounded, with asset digest, ABI/page
constraints, allowed target identities and validation tier. Generate it from the validated manifest
when packaging; do not add full JSON/ZIP parsing to PID 1. ksuinit probes its
actual kernel and verifies module content and policy before init_module or any
vermagic retry. ELF checks alone cannot establish page configuration: compare
build provenance/config too. Failed checks preserve the loader's existing
stock-init fallback and report a concise boot diagnostic. Late-load has the
same rejection rules before changing services. The exact compact encoding and
hash implementation must be proven on both architectures before code is merged.

Early PID 1 cannot rely on boot-completed Android properties. Its minimum
checks use the actual kernel release/page mode and packaged ABI policy;
installation binds the firmware/image evidence beforehand. If an essential
loader check is unavailable, skip loading rather than assume compatibility.
Candidate testing is an explicit engineering path recorded in both plan and
descriptor, restricted to known target/page/ABI evidence on a recoverable test
device. It may test an offline-checked candidate without an existing device
support record; it cannot waive a mismatch or publish `device_verified` itself.

## 4. Online/offline artifact trust (F10)

Use a versioned release manifest signed with a dedicated Ed25519 release key;
pin the verification public key in consumers. This key is distinct from the
APK signing certificate and preserves the existing Manager identity. Keep the
private key outside the repository in controlled release signing infrastructure.
Specify key ID and rotation policy before the first signed release. Verify the
exact manifest bytes before parsing; signatures bind every asset digest, target
policy and support record. Test vectors must agree across Rust and Kotlin.

An offline bundle contains `manifest.json`, `manifest.sig` and referenced LKM
files. Import through the Android document picker into app-private staging;
verify signature, schema, bounded archive entries, sizes and hashes before
atomic cache promotion. Reject traversal, duplicate entries and unexpected
links. The root executor receives a private staged copy and rechecks the same
digest, avoiding trust in a shared Downloads file or a mutable content URI.

Legacy releases have no signed manifest. Map verified known baseline assets
through a pinned migration index; do not invent offline signature provenance
for them. A self-created bundle/hash can be inspected as unverified input, but
does not gain the trusted one-click Canary installation path. Existing manual
administrator workflows must still apply target/ABI checks. Support requires
independent build/device acceptance, regardless of who supplied the module.

Downloads use unique IDs, `.part` files, bounded retry/backoff and cancellation.
Resume only when the source validator/version remains the same; always verify
the final expected size and digest. Publish a completed cache file after fsync
and atomic rename. Cancelled/failed jobs do not masquerade as completed files.
Implement Android foreground-service timeout cleanup; app/OS termination marks
the durable job interrupted. Restart reconciles it, never replays installation
from a completion callback. Android boot-time restrictions are respected; do
not enable the Magica boot receiver merely to restart dataSync downloads.

## 5. Preview, execution and durable task state (F2/F3)

Proposed CLI surface, finalized in the first contract milestone:

- `ksud inspect-target --json`: read-only device/image inspection.
- `ksud install-plan --json`: read-only compatibility and stage preview.
- `ksud task start --plan <private-file> --json`: explicit mutation request.
- `ksud task status <id> --json` and `ksud task cancel <id> --json`.

Preview does not download, extract/install helpers, create root task files,
change mounts, flash or activate a slot. It can use caller-provided staged
artifacts. The UI shows unknown capability results rather than claiming success
without root. Execution acquires a device-wide ksud lock, repeats target/slot/
capacity checks, checks the displayed plan digest and rejects changed critical
inputs with `PLAN_STALE`. All existing boot patch, restore, uninstall, update,
late-load and unload entry points participate; disabled UI buttons are not the
lock. Module operations use their own serialized domain; multi-domain actions
have a defined lock order and cannot race their next-boot markers.

Use an advisory file lock with a fixed private path, not PID-file existence.
Record PID plus process start identity and boot ID for diagnostics. The worker
performing partition I/O holds the lock through sync/readback; critical child
writers must keep ownership even if the supervising parent dies. Unrelated
children must not retain it. Recovery never steals a live writer's lock.
Privileged external tools bypassing this protocol are outside its guarantees.

The root worker is detached from the Manager shell/session and supervised;
Manager observes rather than owns its lifetime. Persist intent before each
mutation and outcome after it, with atomic metadata writes and directory fsync.
This narrows the uncertainty window but does not make raw partition I/O atomic.

Stage sequence: `queued -> preflight -> backup -> patch -> verify_output ->
write -> verify_write -> prepare_slot -> await_reboot -> observe_boot`.
Operations omit inapplicable stages. Helper preparation belongs before write
and activation; `prepare_slot` here is final checked activation, not helper
extraction. State is separate from stage: `running`, `awaiting_reboot`,
`succeeded`, `failed`, `cancelled`, `interrupted`, `needs_recovery`.

- Only safe read/download/patch phases are cancellable. Cancellation must be
  acknowledged by the worker; no timeout or closed screen means cancellation.
- Critical write/sync/slot phases reject cancellation with a visible reason.
- After worker loss, reconcile journal with actual slot/files and pending boot
  evidence. Never automatically replay a write or declare success from a PID.
- Installation success means verified image/userspace writes; boot health is
  pending until a later boot is observed. Show these as distinct outcomes.
- Errors include code, stage, retry safety and completed mutations. At minimum:
  `TARGET_UNKNOWN`, `ASSET_UNTRUSTED`, `ABI_MISMATCH`, `PLAN_STALE`,
  `TASK_BUSY`, `BACKUP_FAILED`, `WRITE_PARTIAL`, `READBACK_FAILED`,
  `SLOT_FAILED`, `USERSPACE_INSTALL_FAILED`, `BOOT_UNCONFIRMED`.

## 6. Backups, OTA and reboot reconciliation (F4/F5/F13)

Required backups are hashed, synced and verified before partition mutation;
failure aborts. Preserve the last usable recovery image. Keep legacy SHA-1
ramdisk markers readable, and add SHA-256 metadata without silently changing
the legacy recovery association. Do not erase the previous fallback directory
just to prepare a new backup. Unknown/patched inputs remain visibly classified.

Use partition identity/capacity from the actual block device and validated
slot mapping. Recheck just before write, flush and read back exactly the written
image length. Record expected digest and untouched tail policy. No automatic
retry follows partial-write or readback failure. A power cut may still require
external stock-image restoration.

OTA checks AIDL BootControl, prepared inactive images, update/snapshot state,
slot mapping and helper readiness first. Prepare files before partition writes;
only activate after checked writes. Rollback is conditional on recorded state
and platform safety, never hard-coded slot `a`. Delegate successful-boot marking
to Android's validated health flow; do not prematurely mark success in
post-fs-data. Device tests must establish the actual snapshot/health ordering.

Pending-change UI shows runtime versus next-boot LKM, userspace and module
state. Reconcile boot ID, slot, page size and verified loader-reported asset
identity after reboot. Matching uname is insufficient. Until the loader exposes
trusted runtime asset identity through a negotiated capability, report module
identity as unconfirmed; do not claim exact digest from a version string alone.

## 7. Additional feature implementations

| ID | Implementation boundary | Acceptance / dependency |
| --- | --- | --- |
| F11 Desktop-assisted installation | First MVP is a host CLI using shared inspection/patch contracts and optional ADB collection; outputs patched image plus device/build/slot-specific instructions. Preserve ramdisk route; do not substitute boot-patch-v2 just to obtain host support. No automatic unlock, wipe, flash or slot activation. | Host build/boot-image tooling feasibility is a separate gate; compare output structure with Android patcher, including 4 KB and 16 KB fixtures. Without trusted device/image pairing, inspection-only output, no claim of ready-to-flash compatibility. |
| F12 Configuration snapshot and diff restore | Versioned JSON for app profiles/templates, supported Manager settings, module enablement and validated module config; exclude binaries and secrets. Resolve package + Android user + signing lineage to current UID. Never copy `.allowlist` raw or reuse an old UID. Shared-UID apps restore as a group; unknown identities stay disabled. | Preview additions/changes/removals and privileged grants; explicit apply, local pre-restore snapshot and item outcomes. Use existing JNI profile and module config APIs; restore requires installed compatible modules, does not execute their actions or auto-install ZIPs. |
| F14 Module startup timing | Instrument existing script boundaries in `module.rs`, `metamodule.rs`, `init_event.rs`; record boot ID, stage, module, monotonic spawn/blocking duration and exit result. | Blocking scripts retain exit code; nonblocking service spawn duration is labeled as startup overhead, not full service lifetime. Observe exits through a bounded supervisor without changing stage scheduling; no automatic termination. Include common scripts and metamodule priority. |
| F15 Before/after update comparison | Join health/task records by before/after build, asset, page mode and boot ID; show boot-stage timings, sampled failures and reboot evidence with sample counts. | Compare like-for-like measurements; no inferred battery/performance claims from one boot. Missing baseline remains missing; instrumentation overhead is measured. |
| F16 Canary build monitor | Separate firmware availability from HuskySU verified support; manual refresh first. Use documented official machine-readable metadata if available, otherwise curated signed support index with upstream links. | Current research confirms the target URL, not a stable official feed API. Do not ship scraping/guessed URLs as authoritative support; bounded cache/backoff and optional notification, no firmware download/install automation. |
| F17 Temporary root authorization | Kernel-enforced one successful grant or bounded duration for future grants; capability-negotiated new IOCTLs mirrored in ksud/JNI/Manager. Separate later milestone from Canary ABI repair. | Concurrency, expiry, restart, multiuser/shared UID and compatibility tests pass; older kernels hide/disable it, never fall back to permanent authorization. Existing root processes retain acquired privileges. |

F10 is specified in section 4. F13 is specified in section 6. F6-F9 reuse task,
boot and module records for local diagnostic export, signed channel/support
selection, health reporting and staged third-party module troubleshooting.
F12 validates profile versions, template references, module config keys/values
and SELinux policy inputs before apply; partial application reports each item
and does not claim transaction-wide rollback. Temporary grants are excluded
from snapshots, and restoration cannot silently convert one to a permanent grant.
Keep standard phone Material 3 Expressive components and text labels for
critical actions; progress/unknown/failure must not be distinguished only by
color. Notification deep links reopen persisted task details.

### Temporary grant details (F17)

Retain the existing app-profile struct layout for current contracts. Add a
separate versioned temporary-policy command and capability discovery only in
this later milestone. Ordinary permission checks and sucompat visibility are
non-consuming probes. `do_grant_root` performs the authoritative atomic
check/reservation and consumes only a successful credential transition; a
failed transition releases the reservation. Concurrent requests cannot both
use a one-grant policy. Define lock/RCU lifetime before changing allowlist code.

Temporary policy is applied to an otherwise denied app; the UI requires
replacing any existing permanent grant. Do not modify Manager's bootstrap
access. Shell exceptions and already-root KSU-domain calls are explicitly
audited so they cannot accidentally expand the meaning of an app's grant.
Scope is the Android UID/shared-UID group, with verified package membership;
present that scope in the authorization UI.

Durations use a suspend-aware monotonic kernel clock and expire at reboot.
Do not persist temporary policies into the permanent allowlist. App restart,
wall-clock changes and Manager absence do not extend them. Revocation blocks
new grants; it does not claw back credentials or descendants of a process that
already obtained root. This limitation must be visible in the UI.

## 8. Delivery gates and acceptance

| Gate | Deliverable | Required evidence before advancing |
| --- | --- | --- |
| M0 | Contracts/fixtures/manifest trust | Exact target fixtures and authenticity evidence; bounded legacy-LZ4/page detection; schema golden tests and signing test vectors; host/Android dependency feasibility |
| M1 | S1-S7 core safety | Every entry point uses gates/lock; lifecycle fault tests; journal interruption tests; backup/partition/OTA injected failures; no false success |
| M2 | 4 KB candidate | Pinned reproducible build, packaged descriptor, APK identity and embedded/published digest comparison; device boot/root/SELinux/modules/reboot/suspend and existing-target regressions |
| M3 | F1-F5, F10 and F13 | UI follows authoritative plans; durable notifications/task restart; offline trust/invalid bundle cases; runtime/next-boot distinction. Core features land progressively; do not delay safety fixes for all screens. |
| M4 | Independent 16 KB candidate | Separate headers/config/ABI audit and device checks; same-uname opposite-mode rejection. F11 gets its own host parity gate and cannot weaken this requirement. |
| M5 | F6-F9, F12, F14-F16 | Redaction, restore identity/diffs, module staging/timing behavior, comparison baseline and firmware feed provenance tests. Ship each independently when ready. |
| M6 | F17 | New negotiated kernel/userspace/JNI capability; atomic grant/expiry/bypass analysis and dedicated device regression results |

S8's regression/fault tests for S1-S7 are P0 release gates, even if resource/UI
polish is later. Tests focus on real wrong-target, concurrency and failure
behavior rather than mirroring implementation helpers. Include cancellation
during download and rejection during write, death before/after mutation,
same-KMI opposite pages, oversized/truncated compressed input, signature/digest
failure, missing companion boot, capacity change, stale plan, backup failure,
unload EBUSY and OTA helper/slot failures. Root-offline recovery is documented
and practiced on a recoverable test device, never implied by in-app availability.

Apply the component checks from AGENTS.md when runtime code changes. This
planning review only requires document/schema/link consistency checks. Device
access, full verified fixtures, matching DDK and signing infrastructure remain
release prerequisites, not reasons to block the first implementation work.
