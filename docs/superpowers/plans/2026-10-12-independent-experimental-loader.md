# Independent experimental Husky loader

Date: 2026-10-12. Updated 2026-10-13: independent CLI 0.2.0, bounded settings,
diagnostics, public/private inventory, private-only mutation tests, read-only
device harness and host pilot implemented and offline tested. Real-device loading
and comparative stability/performance remain unverified.

Execution resumed by user direction alongside Manager's official Material 3
Expressive/native components. See
[Manager visual specification](../specs/2026-10-12-manager-material3-expressive-native.md).
The CLI's load is blocked by an immutable gate pending private ABI/CFI evidence
and an approved matching-device recovery campaign. No flag/preference disables
this gate. The ARM64 binary uses /system/bin/linker64; early-init availability is
unverified. See [execution record](2026-10-13-implementation-status.md) and
[PC handoff](2026-10-13-pc-codex-handoff.md).

Plan review: 2026-10-12. Dependency/trust issues and settings/measurement
ambiguities have been addressed in the plan; implementation and device gates
remain pending. See [review findings](2026-10-12-independent-loader-plan-review.md).

## Implementation boundary

Author new application-level source in `experiments/husky-loader/`, outside the
existing Cargo workspace and production boot path. Do not copy, import, include
or wrap KernelSU/ksuinit/ksu-contracts/MMRL or another project's implementation.
Use Rust's standard library and documented Linux/Android system interfaces.
Public ELF, kallsyms, module metadata and syscall formats are interoperability
contracts, not implementation code to transplant. No new third-party Rust crates
are needed for the first prototype. Tests may inspect the existing compiled .ko
as input data without incorporating its source.

This is an independently authored implementation, not a claim that the entire
operating system or compiler is proprietary, nor a formal clean-room process:
the current conversation has already examined the production loader.

## Goals and measurable acceptance criteria

- Richer diagnosis: read-only inspection, machine-readable results, explicit load
  strategy, compatibility reasons, bounded logs, and distinct failure categories.
- Stable failure behavior: reject malformed/truncated ELF, overflow, missing
  metadata and ambiguous symbols before loading. No automatic cross-release
  loading, removal of CRC records, stripping signatures or disabled CFI.
- Resource discipline: bounded metadata parsing, streaming input where possible,
  store only needed symbols, avoid repeated full-file copies and expose measured
  elapsed time, peak memory and binary size.
- Ease of use: `inspect`, `check`, and an explicitly invoked experimental `load`;
  deterministic output and actionable failure reasons. Read-only modes work
  without root. Production Manager and boot behavior remain separate.
- Comparisons must use the same device, artifact and operation. No claim of
  better stability, lower memory or faster loading without equivalent measurements.

## Compatibility strategies

Standard kernel resolution preserves signed bytes and uses the kernel module-load
interface. It avoids application-side relocation and most symbol-table work, but
cannot be assumed to load the current HuskySU candidate: 71 of its imports lack
public CRC metadata and its production load path uses private kernel symbols.

Current-candidate compatibility therefore requires a separate, independently
implemented experimental strategy. It must pin exact kernel/source/config,
preflight all imports, account for checks not performed by normal kernel import
resolution, and verify relocation and CFI on the device. Signed modules must not
enter a byte-mutating strategy. Selecting this strategy is not proof of safety
or a mechanism to force unsupported targets.

The user selected current HuskySU candidate compatibility for the first release.
The private-symbol strategy is implemented behind explicit experimental opt-in;
standard signed-module loading remains a future strategy.

## Stages

1. Define the target, source boundary, failure model and independent output schema.
2. Build a read-only ELF/metadata inspector using newly authored source; exercise
   real candidate data, malformed inputs and resource caps.
3. Add explicitly selected loading strategies, serialization of mutation and
   bounded diagnostics. Verify loaded-state results rather than syscall success
   alone. Keep destructive operations out of inspect/check.
4. Test on Pixel 8 Pro: load failure, restart, root use, signature policy, CFI and
   recovery. Benchmark equivalent paths against production with documented inputs.
5. Consider opt-in Manager integration only after evidence supports it. Do not
   replace the production boot loader during experimentation.

## Pending decisions and limits

- First-release target selected: current unsigned ARM64 4K HuskySU candidate.
- Full CRC/runtime ABI verification and trustworthy metadata for private imports.
- Capability probing and target proof format without depending on production code.
- Trusted signature handling and exact submitted-byte contract.
- Bounded rollback/recovery behavior and supported device/test matrix.

No superiority, device compatibility or production readiness is established by
this plan. Experimental load controls require their strategy-specific gates.

## Implemented prototype

Location: `experiments/husky-loader/`, an isolated Cargo workspace containing only
its own package and no third-party crates. New source provides ELF64/metadata/BTF
bounds checking, streaming SHA-256, target proof parsing, CRC value/coverage
checks, kallsyms scanning, private-symbol binding, a nonblocking root-owned lock,
bounded kernel diagnostics, explicit experimental syscall invocation and JSON
results. No production parser, loader or contract helper is linked.

Input pins cover the real candidate, official config and Module.symvers. The
source-commit value is a claim, not a device measurement. Runtime release/page
size checks do not independently verify the running kernel image/config or private
ABI. Signed modules, 16K, force-loading, unloading, recovery automation and Manager
integration are unsupported in this prototype.

Six tests cover SHA-256 vectors including padding/streaming, malformed ELF/ranges,
hidden/missing/ambiguous symbols, signed/incomplete mutation refusal, strict
options/JSON and real candidate pins/CRC/identity with rejection paths. All tests
run without module-loading syscalls. Additional CLI checks reject oversized files
and loading without opt-in. ARM64 API 26 check, Clippy and release build are part
of validation. Read-only host measurements are recorded in
`2026-10-12-independent-loader-evidence.json`; they are not Android measurements
or equivalent production-loader comparisons.

Measured release snapshot (seven runs per operation, x86_64 host, real 700,224-byte
candidate): inspect median 3,642 us / post-exec VmHWM at most 1,708 KiB; check median
10,074 us / post-exec VmHWM at most 2,476 KiB. VmHWM is sampled by the executable
after the operation and before final JSON output. Child wait4 rusage also recorded
12,160 KiB, but can include pre-exec spawn memory and is not the executable-only
measurement. ARM64 API 26 stripped executable: 362,984 bytes, SHA-256
`a220c470d048d97df113ee144a5e256a3ada12324c3b002a12fc84b496230a3a`.

Next device gate: controlled execution with recovery prepared; confirm the exact
running image/config, actual relocation and CFI, module presence, authorized
Manager/root use, failure behavior and restart/recovery. Then compare equivalent
load operations, CPU, memory and boot overhead against production. Kernel module
initialization cannot be made safely cancellable by a CLI timeout alone; recovery
and watchdog behavior need a separate design and device evidence.

## Complete delivery plan

The sections below are the implementation and acceptance plan. They supersede
the abbreviated stage list above. A checked prototype feature is not a checked
device capability. This planning update changes documentation only.

### 1. Product boundary and first support profile

Name: Husky Loader Experimental. Deliver one independently authored executable,
versioned CLI/output contract, target profiles, offline verification tooling,
tests, benchmark records and an eventual opt-in Manager adapter.

Initial profile is limited to Pixel 8 Pro / husky, ZP11.260918.007,
`6.1.172-android14-11-g3fc92ed75e44-ab16169542`, ARM64 and real 4K pages. The current
700,224-byte candidate is the first fixture, not a declaration of universal KSU
compatibility. New kernel releases, 16K and other devices need separate profiles
and repeat the acceptance gates. Do not infer private-ABI compatibility from KMI.

The loader does not obtain root, implement a kernel, recreate the LKM, install an
APK, flash a partition, or replace production boot handling by itself. It requires
an already authorized privileged/engineering execution context for actual loading.

Application-level loader code, parsers, policies and adapters must be newly
authored. Do not import/copy/include/link or invoke an existing loader as an
implementation or failure fallback. Standard library and public OS interfaces
are allowed. Existing .ko, kernel artifacts and public formats are interoperability
inputs. An external benchmark may drive a production executable as a black-box
comparator; it must not become a product dependency. Keep a provenance inventory
and review dependencies/source references for every release.

### 2. Current implementation versus planned capabilities

| Capability | Current state | Required next work |
| --- | --- | --- |
| Isolated Rust workspace, no third-party crates | Implemented | CI dependency/provenance checks |
| ELF64, selected identity, CRC and BTF bounds inspection | Implemented prototype | Full parser invariants, relocation inventory and fuzz corpus |
| Streaming SHA-256 algorithm and input-file pins | Implemented | More independent vectors and differential checks; controlled profile distribution |
| Config, module identity and CRC coverage checks | Implemented for one strict profile | Device identity evidence, profile schema/version lifecycle |
| Private-symbol binding | Implemented; currently binds all imports | Separate actual exported/private imports and retain kernel checks where possible |
| Signed input in private strategy | Rejected without mutation | Separate standard-kernel strategy and signed-input test matrix |
| Load lock, explicit opt-in, one syscall attempt, bounded logs | Implemented | Concurrency/fault injection, typed results and phase-specific diagnostics |
| Module-presence check after syscall | Implemented | Authorized Manager/root health verification |
| inspect/check/load JSON CLI | Implemented | Help, doctor, device-check, explainable load plan, stable reason codes |
| CPU/memory/binary-size evidence | Host read-only snapshot only | Equivalent-device measurements and production comparison |
| Early-init and Manager integration | Not implemented | Independently authored integration after device gates |
| Recovery, sustained stability and device compatibility | Not verified | Controlled device campaigns and restore evidence |

### 3. Independent architecture

Split the present large CLI module into small components after their boundaries
are covered by tests; do not rewrite working code merely to rename it.

| Component | Responsibility | Boundary |
| --- | --- | --- |
| input | Open immutable snapshots, enforce size/ownership rules and hash inputs | No kernel mutation; detect changed/truncated files |
| elf | Parse headers, sections, symbols, versions, metadata, BTF and relocation inventory | Checked ranges/arithmetic; no syscall or target policy |
| digest | Stream SHA-256 and report file identity | No claim of publisher authenticity |
| profile | Read schema-versioned target inventory and provenance | Reject unknown major versions, duplicate keys and unsupported target pairs |
| settings | Resolve typed preferences, presets and per-operation overrides | Separate user preferences from immutable target/verification policy |
| device | Probe release, architecture, pages, boot identity and available config/build evidence | Read-only; distinguish unavailable from mismatched |
| policy | Classify imports, select strategy and produce reasoned load plan | No hidden force flags or automatic unsupported-target fallback |
| symbols | Read built-in symbol metadata, validate required addresses/types and ambiguity | Stream table; retain requested items only; no automatic visibility-policy changes |
| executor | Hold lock, validate final payload and perform one supported kernel load operation | Explicit privilege/experimental gate; no implicit unload or flash |
| diagnostics | Bounded phase events, kernel errors and redacted export | No raw symbol-address telemetry by default |
| cli/adapter | Structured command input/output and later Manager integration | Execute argv directly; never construct an executable shell string from module metadata |

Only required unsafe OS calls belong in a reviewed executor boundary. Document
pointer lifetimes, ABI width, syscall number/platform guards and descriptor
ownership. ELF parsing and planning remain safe Rust. Do not port production
helper implementations into these modules.

### 4. Target profile and device proof

Move the prototype's simple pins into a documented, versioned profile with:

- Supported device/build, complete kernel release, architecture and page size.
- Module SHA-256/size and exact modinfo identity/vermagic expectations.
- Official source commit, toolchain, image/vmlinux/config/Module.symvers identities
  and their association with the same official build.
- Explicit public/private import inventory, CRCs, available type/CFI evidence,
  namespaces, expected symbol kinds and allowed load strategy.
- Signature policy, required privileges/capabilities and support-state evidence.
- Profile identifier/version, generator version and linked test/measurement records.

Build this inventory with an independently authored offline profile tool. Verify
all inputs before emitting a profile; re-generate when any pinned artifact changes.
Pinning a file supplied by the user is not the same as authenticating its publisher.
Start with controlled reviewed profiles; signed catalog/distribution requires a
separate trust/key design. Do not add an unaudited custom signature algorithm just
to preserve a zero-crate dependency count.

Initial trust anchor: a reviewed campaign profile digest embedded in the
experimental build or packaged with the reviewed test harness. Privileged loading
requires a matching approved snapshot, not an arbitrary --proof file whose fields
agree with each other. Offline check may inspect an unapproved profile but reports
that distinction. Profile generation does not approve deployment; settings/import
commands cannot add approved pins. Changing a module/target digest requires a new
reviewed campaign profile/build. The 0.1.0 prototype does not enforce this anchor
and is not an approved device-campaign build.

Device evidence must exceed the current uname/page-size checks before stronger
support claims. Investigate actual kernel build notes/identifiers, exposed running
config and current-slot boot-image identity. Parse any boot/compression formats
independently, with input/output limits; reject unsupported formats rather than
guessing. Record how the boot artifact is associated with the running kernel;
a partition hash alone does not prove that kernel is currently running.

Use explicit states: input_valid, offline_target_match, device_identity_partial,
device_target_match, load_tested, root_tested and boot_recovery_tested. Missing
evidence is unknown, not success. Keep the engineering opt-in distinguishable
from validated installation; never expose unknown identity as verified support.

Before a real private-symbol trial, record the exact test image/profile association,
available runtime build/config evidence and restoration procedure. Any remaining
running-image uncertainty is a limitation of that narrowly scoped engineering
campaign, not permission for a general Manager install path.

### 5. Loading strategies and kernel-check preservation

**First priority: current-candidate experimental compatibility.** Refine the
present all-import mutation into a hybrid strategy if the matching kernel's
actual export inventory supports it:

1. Establish which imports the exact running kernel really exports. A
   Module.symvers record alone must not be assumed sufficient proof of a live
   export; account for trimming, namespaces and module ownership.
2. Leave actual public exports undefined in ELF so kernel resolution can perform
   its CRC, namespace and applicable GKI-access checks.
3. Bind only approved private built-in imports, using an exact-target profile,
   complete preflight, non-zero unique addresses and available type evidence.
4. Validate CRC value/coverage before mutation and preserve all version records,
   module_layout, signature policy and executable metadata.
5. Treat remaining private-ABI/CFI uncertainty as an engineering limitation needing
   device evidence. No address match or one CFI ID comparison proves the entire ABI.

The current split of 135 imports with public CRC metadata and 71 without it is
an offline inventory, not an already verified hybrid load path. Prototype behavior
must not silently change until the hybrid path passes matching-device tests.

Make classification reproducible: associate the pinned vmlinux export tables,
Module.symvers owners/namespaces and available symbol/type data with the running
target evidence. Produce a per-import decision record with provenance and reason.
Missing CRC metadata does not by itself approve an import as private. Unclassified
imports block the campaign build; they are not automatically bound. Only approved
private names/types may be patched, and standard resolution failures cannot
silently promote public imports into the private set.

**Later: standard kernel strategy.** Preserve the original signed/unsigned bytes
and let the kernel resolve imports and apply its policy. Use an appropriate
documented file-descriptor load interface where it avoids userspace copies without
creating a file-change race. Signature presence, cryptographic validity and trust
remain separate states; the kernel's acceptance is not publisher authentication.

Strategy selection is explicit or explained by a read-only plan. No automatic
fallback from signature failure to stripping/mutating input, from a failed standard
load to private binding, or from a failed new loader to the production loader.
Do not remove CRCs, rewrite cross-release vermagic, turn off CFI/GKI protection,
load ambiguous addresses, or inject addresses from a previous boot.

### 6. Workflow, CLI and user-facing behavior

Target CLI contract:

| Command | Planned behavior | Privilege/side effects |
| --- | --- | --- |
| inspect | Describe file, imports, versions, signatures and structural limits | Read-only; no root needed |
| check | Verify module/profile/config/symvers snapshots offline | Read-only; no device-load claim |
| doctor | Show device capabilities, target evidence, missing permissions and unsupported conditions | Read-only; privileged probes only when already authorized |
| plan | Explain strategy, public/private counts, skipped-kernel-check limitations and blockers | Read-only; not an automatically executable authorization token |
| check-device | Combine target-file checks with current-device evidence | Read-only; no load syscall |
| load | Revalidate actual inputs/device, lock, preflight, execute one strategy and report result | Requires explicit experimental selection and existing privilege |
| status | Distinguish module presence, known attempt state and externally verified root health | Read-only; no inference of full success from presence alone |
| settings | Show/validate/diff preferences; explicitly import/export/reset saved preferences | Saving settings never loads a module; privileged store needs authorized access |

Keep current command behavior compatible where possible; introduce a new schema
major if result semantics change incompatibly. Add --help and human-readable
summaries while retaining JSON for tooling. Return stable machine reason codes
for file/ELF/profile/target/symbol/CRC/signature/policy/lock/kernel/postcheck failures,
plus a bounded explanatory message and next useful action.

State flow: read -> validate_input -> validate_profile -> probe_device -> acquire_lock
-> resolve_policy -> preflight_symbols -> prepare_payload -> kernel_load -> postcheck
-> report. Each failure records its phase. A lock conflict is busy, not a load
failure; unsupported is not corrupted; unknown signature trust is not invalid.

Cancellation before the syscall is cooperative at parser/hash/scan checkpoints;
a blocked OS read is not necessarily interruptible. Report cancellation_requested
until the checkpoint actually exits. Once kernel initialization starts, do not
promise cancellation or safe rollback, and never automatically retry an uncertain
result. The client timeout/deadline is not a transaction rollback mechanism.

For future integrated load attempts, keep one bounded root-owned current-attempt
record with boot ID, attempt ID, PID/start identity, payload/profile/settings
digests and phase. Ephemeral current-state coordination is separate from optional
historical logs and does not retain runtime addresses. Without such a record,
status must report attempt state unknown after a crash, not reconstructed success.
Reconcile actual module lifecycle state (loading/live/unloading), task liveness
and separate root health before an explicit retry; /sys/module presence alone
cannot prove initialization has finished. The 0.1.0 CLI lacks this coordination.

### 7. Stability and failure containment

- Validate all ranges, strides, links, string termination, reserved/duplicate
  identity fields and relocation references before touching the kernel.
- Reject unsupported architectures/page sizes, weak/missing-import situations
  outside the profile, owner/type mismatches, hidden or ambiguous addresses and
  incomplete CRC coverage. Do not silently relax a failed condition.
- Snapshot verified payload bytes once; complete resolution before applying any
  patch. Do not overwrite the user's .ko or depend on a path reopened after hashing.
- Retain a reviewed per-attempt root-owned nonblocking lock. Test symlink/hardlink,
  ownership, permission and concurrent-launch behavior. Do not remove/recreate the
  lock inode while another attempt could hold it.
- Avoid modifying global symbol-visibility or security settings. Report the
  execution capability needed rather than masking denied access as missing ABI.
- Preserve original and submitted payload digests as distinct diagnostic fields.
- Bound input parsing, scanning and log collection; report unavailable/truncated
  diagnostics explicitly. Exclude raw addresses from normal logs and redact exported
  kernel messages before sharing.
- A userspace timeout cannot safely undo kernel initialization or a panic. Define
  device recovery and boot supervision separately; never sell them as transactional
  rollback of loaded kernel code.
- Keep actual root/Manager authorization as a separate postcheck. A test-signed
  Manager rejected by the LKM is an authorization issue, not automatically a
  loader failure. Full acceptance needs a matching authorized Manager.

### 8. Performance plan and resource budgets

Goals below are provisional budgets for the current approximately 684 KiB module,
not measured guarantees. Freeze them after the first comparable device baseline.

| Metric | Proposed budget/gate |
| --- | --- |
| Stripped ARM64 CLI | At most 512 KiB for the initial feature set; current artifact 362,984 bytes |
| Current-profile offline precheck device RSS | Target at most 8 MiB; measure real post-exec process peak |
| Payload storage | At most one full owned module payload plus bounded metadata; avoid a second full mutation copy |
| Current-profile preflight latency | Initial target p95 at most 250 ms on Pixel 8 Pro; report cold/warm separately |
| Kernel initialization latency | Measure separately; no arbitrary deadline advertised as safe cancellation |
| Idle resources | No resident daemon, polling loop or network traffic in the CLI |
| Diagnostics | Bounded per attempt; proposed persisted limit 64 KiB with bounded record count |
| Cache | Optional immutable metadata only; proposed 1 MiB cap per active profile; no persisted runtime addresses |

Optimize after profiling: stream symvers/metadata when it demonstrably reduces
peak memory, avoid repeated hashing/copies and per-row allocation, retain only
required symbols, and invalidate any transient cache with boot ID and target/profile
identity. Do not skip the complete ambiguity scan to win a latency benchmark.

Benchmark with the same device/build, module, input proofs, privilege context,
initial module state and logging mode. Use at least 30 read-only samples per
variant and separate cold/warm behavior; report median, p95, CPU and process peak
memory. Actual load comparisons require separate fresh/controlled boot trials;
do not repeatedly unload a critical module solely to increase sample count.

Suggested superiority gate: demonstrate at least 20% lower peak userspace memory
on an equivalent operation with no more than 10% p95 latency regression, or an
explicitly justified alternative tradeoff. These are proposed decision thresholds,
not existing achievements. Stability claims additionally require the device matrix
below; performance alone cannot justify bypassing verification.

First define comparable scope: inputs, validation guarantees, phase endpoints,
logging and privilege setup. If production has no equivalent offline check, do
not compare its load time with this CLI's check time. Record sample distributions
and measurement uncertainty. Thirty samples are a pilot; a proposed superiority
claim needs a larger repeatable campaign (initial target at least 200 comparable
read-only samples per variant), with confidence intervals and no selective outlier
discarding. Define cold/warm cache conditions; a new process alone is not a cold
filesystem read. VmHWM measured before final output excludes later allocations;
label it as a sampled lower bound on whole-process lifetime peak, not exact final
peak. Measure Android loading separately before any load-performance claim.

### 9. Verification matrix

| Area | Required evidence |
| --- | --- |
| Digest | Public known-answer vectors covering empty, short, padding boundaries and large/streamed inputs; independent differential checks |
| ELF | Real candidate plus truncated/overflow/overlap/stride/link/string/version/BTF/relocation mutations; controlled fuzz budget and reproducible failure corpus |
| Profile | Missing/duplicate/unknown keys, unsupported versions, wrong target, altered pins, public/private inventory mismatch and CRC omissions |
| Symbols | Missing/zero/ambiguous/duplicate aliases, owner/type conflicts, long/partial/bad records, interrupted reads and scan limits |
| Executor | Lock races, invalid lock ownership/links, missing privilege, existing module, target change, log failure and errno classification; mock OS boundary |
| Signed strategy | Original-byte preservation; trusted/untrusted/invalid signatures on a suitable test kernel; no mutation fallback |
| Resources | All size/record caps; malformed inputs cannot cause unbounded allocation or nonterminating preflight |
| CLI/adapter | Valid structured output, stable reason codes, argument escaping, explicit opt-in, cancellation boundaries and interrupted-result reconciliation |
| Device target | Verified evidence for the exact husky build, architecture, 4K and required capabilities |
| Runtime | Actual relocation, module presence, CFI behavior, authorized Manager, root operations and representative module lifecycle |
| Recovery | Restart, repeated cold boots, failed early load and documented restoration to the known-good environment |

Prototype six-test evidence is the baseline only. Add targeted unit/integration
tests before each implementation stage. Fuzzing and negative loading should first
use isolated fixtures or a suitable disposable test kernel; do not deliberately
feed corrupt executable code to the primary phone just to test rejection.

Proposed first-device campaign: at least 10 successful controlled cold boots,
5 controlled load/root sessions with the exact candidate, and 24 hours of ordinary
use without loader-attributable failures. This is an initial acceptance campaign,
not proof of long-term reliability or a statistically established improvement.
Collect comparable production observations before asserting stronger stability.

### 10. Milestones and completion gates

| Milestone | Work/deliverable | Gate | Current status |
| --- | --- | --- | --- |
| M0: original prototype | Independent CLI, strict first profile, offline fixtures and ARM64 build | Reproducible source/build; no production-code dependencies | Completed as prototype |
| M1: verification foundation | Profile schema/tool, full parser checks, typed diagnostics/settings, expanded tests/fuzz corpus | Negative inputs/settings fail before syscall; pins/provenance and unknown states explicit | Settings/diagnostics and 11 offline tests pass; wider fuzz campaign pending |
| M2: check-preserving strategy | Actual export inventory, hybrid public/private policy and private-type evidence | Public checks retained where applicable; no unapproved private binding or cross-target relaxation | 135 public / 71 private inventory and private-only mutation tested; private ABI/CFI pending; load blocked |
| M3: usable diagnostics | help/doctor/plan/check-device/status/settings, presets/one-shot overrides, structured lifecycle and capability explanations | Commands have documented semantics; settings changes never imply loading | CLI/preferences implemented; root health not probed; device validation pending |
| M4: resource optimization | Profiled buffers/streaming, bounded metadata cache, reproducible device benchmark harness | Meets frozen device budgets without reducing validation | 90-check host pilot recorded; cache/history unavailable; device comparison pending |
| M5a: campaign preparation | Independent privileged test harness, executable runtime dependencies, approved profile, exact test image and restore materials | Module absent; required capabilities/policy and runtime prerequisites proven; no test load yet | Original read-only harness and host dry-run ready; PC device evidence pending |
| M5b: controlled runtime trial | Actual CLI load/root/CFI and initial resource baseline | M1/M2 and M5a gates met; results reconciled and no unclassified imports | Pending, after M5a |
| M5c: boot/recovery trial | Minimal independently authored test adapter, cold boots, failure transfer and restore campaign | Test adapter verified without a load first; actual boot/recovery evidence recorded | Pending, after M5b |
| M6: optional integration | Product early-init adapter, Manager opt-in/settings UI and validated settings snapshots | Relevant M5b/M5c gates pass; no automatic second-loader fallback | App-private Manager settings/import/export ready; boot/root activation blocked pending M5 |
| M7: experimental release | Source provenance, profiles, binaries/checksums, support matrix, known limits and rollback guide | Reviewed evidence and artifacts; only explicitly tested profiles listed | Engineering handoff ready; verified release pending M5 |
| M8: future strategies | Standard signed loading, further targets or features | Separate strategy/device-specific acceptance; no silent broadening | Optional |

M1 precedes M2. M3 can progress alongside M2 after the result schema is settled.
M4 follows correctness and needs M5b's initial device baseline to freeze budgets;
that baseline does not require M4 optimizations to be completed. M5a/M5c may build
minimal test-only adapters before product integration, removing the previous
M5/M6 dependency cycle. M6/M7 require the relevant M5b/M5c gates. Unknown device timing is not assigned a
fictional calendar deadline; schedule the device campaign when access exists.

For each milestone: update this plan, implement only the scoped change, run its
meaningful tests, run Android check/Clippy/format/build as applicable, review the
diff and record evidence before moving on. Do not rebuild unchanged APKs or the
kernel for CLI/documentation-only changes.

### 11. Manager and early-init integration

Manager later shows the selected loader version, supported profile, compatibility
reasons, verification states, actual phase/result and exportable redacted diagnosis.
The experimental choice defaults off. An explicit action launches the independent
binary with validated argv and verified artifact/profile identities; metadata must
not generate arbitrary privileged commands. Existing root authorization remains
an external prerequisite, not a privilege bypass implemented by this loader.

An early-init adapter must be newly authored and keep process I/O, mount/read
prerequisites, privilege, failure handling and the transfer to real system init
explicit. Do not reuse production init-wrapper source. Test that userspace rejection
continues system startup; module initialization panic/hang needs recovery evidence.

Campaign prerequisites must be explicit: UID 0 alone is insufficient. Check
CAP_SYS_MODULE, modules_disabled, relevant namespace/SELinux/lockdown policy and
symbol visibility without changing security policy automatically. The module must
not already be loaded; root supplied by an already-loaded same-name KernelSU
module is not a valid clean first-load environment, and it must not be unloaded
automatically for testing. Record compatible external ksud/runtime assets and an
authorized Manager for full root-health testing; these are interoperability
fixtures, not loader source dependencies.

Current ARM64 artifact is dynamically linked with /system/bin/linker64. It cannot
be assumed executable before /system is available. M5a must choose and verify a
static/self-contained test helper or a defined later privileged stage where its
linker/libraries are mounted. Record /proc, /sys, /dev, I/O descriptors, required
data/assets and settings availability at that stage. Test startup/transfer without
any module load before the actual campaign. Product adapter promotion is M6;
the minimum harness needed to test it is not deferred until after boot testing.
Selection of the production path occurs outside the new loader before any load
attempt. Switching back is a next explicit operation/boot decision, not an automatic
second loader call after an uncertain result.

No integration enters the shipping boot image until its own boot/recovery gates
pass. Expose cancellation only where preflight can actually stop; loading UI must
not imply that terminating a CLI safely undoes a kernel operation.

### 12. Distribution and support policy

Publish an experimental version, original source inventory, reproducible build
commands/toolchain, supported profile IDs, per-artifact digests, verification report,
benchmark methodology and known limitations together. Host and Android executables
must be labeled separately; the CLI is not an APK. Keep profile schema and result
schema compatibility documented independently of executable version numbers.

Support labels are per target and strategy: offline_checked -> engineering_load_tested
-> root_tested -> boot_recovery_tested. Do not rename an engineering build stable
based on compilation or one successful load. Experimental builds remain opt-in
until evidence and an explicit later product decision support default use.

Current unresolved prerequisites: actual device access, stronger running-image/
config evidence, approved private ABI/CFI inventory, an authorized Manager for root
acceptance, a known-good restore path and an equivalent production benchmark.
Offline architecture/test work can continue while these prerequisites are pending.

### 13. Custom settings

Status: planned, not implemented in the current 0.1.0 executable. Implement this
with newly authored source; do not reuse production settings/parser code. User
preferences must remain separate from target proof and mandatory load policy.

#### Settings surface and defaults

| Group | Setting | Proposed default | Allowed scope |
| --- | --- | --- | --- |
| Entry | Experimental loader selection | Off in eventual Manager integration | Select execution path; saving/enabling never starts a load |
| Compatibility | Target profile | Explicitly selected current profile | Reviewed profiles only; device match remains mandatory |
| Strategy | Current approved strategy | Profile-supported experimental strategy | Standard/hybrid choices appear only after their respective gates; no hidden fallback |
| Output | Text or JSON | JSON for CLI; readable summary for Manager | Presentation only; stable machine reason codes retained |
| Diagnostics | Error/normal/debug verbosity | Normal | Debug adds bounded details, not verification exemptions |
| Diagnostics | Kernel-log capture | On when available | Unavailable/disabled capture must not alter the load verdict |
| Diagnostics | Capture limit | 32 KiB | 4-64 KiB, further constrained by compiled/platform caps |
| Diagnostics | Saved attempt history | Off for standalone CLI | 0-20 redacted records, each bounded; no background collection |
| I/O | Streaming buffer | 16 KiB | 8/16/32/64 KiB; phase-specific allocations stay bounded |
| Resource | Module input limit | 16 MiB | 1-16 MiB; settings may lower but cannot exceed the compiled ceiling |
| Resource | Metadata cache | Off initially | Enable only after immutable-cache correctness tests; no runtime-address persistence |
| Resource | Metadata cache budget | 1 MiB when enabled | 64-1024 KiB; intersect with validated platform budget |
| Operation | Preflight deadline | 5 seconds initially | 1-30 seconds for interruptible preflight work; not a kernel-syscall cancellation promise |
| Operation | Approved module parameters | Empty | Only profile-declared names/types/ranges; no arbitrary shell or unreviewed privilege parameters |

Defaults/ranges are proposals to validate in M1/M3/M4, not existing 0.1.0 flags.
The preferred profile and effective strategy are shown before execution. Disabled
or unsupported choices explain the missing device/strategy gate. An invalid
parameter or a resource budget too small for the selected input produces an
actionable rejection rather than an automatic increase or reduced checking.

#### Presets

Offer three named presets, applied only after the user selects one:

- Balanced: 16 KiB streaming buffers, normal diagnostics, 32 KiB log cap, no
  persistent history; metadata cache stays disabled until its gate passes.
- Lean: 8 KiB buffers, error-focused diagnostics, 8 KiB log cap, no cache/history.
  It changes allocation/diagnosis preferences, not hash/CRC/symbol verification.
- Diagnostic: 32 KiB buffers, debug diagnostics, 64 KiB log cap, up to five redacted
  attempt records when persistence is available. It does not enable address export,
  experimental loading or weaker compatibility rules.

Presets must not claim a measured performance advantage without device evidence.
Changing a preset shows its effective differences. Users can save their own named
preferences within the same schema and limits. Exported preferences do not contain
raw addresses, proof contents, signing keys, privileged command strings or an
automatically executable load action.

Applying a preset in the settings UI previews replacement of preset-governed
overrides and persists it only through the explicit save action. A one-shot CLI
preset replaces those fields in memory only; unrelated saved choices remain
effective. Neither form silently reselects a target or invokes load. A subsequent
manual edit is an explicit custom override.

#### Fixed verification requirements

Do not expose switches for skipping SHA-256/CRC coverage, accepting a mismatched
release/page size, stripping signatures, disabling CFI/GKI protection, approving
unknown private imports, binding ambiguous/zero addresses, forcing load, retrying
an uncertain syscall or calling another loader after failure. Target pins are
edited through reviewed profile tooling, not the preferences screen.

Keep explicit experimental execution separate from a saved preference. A profile
or preset import cannot authorize root execution or make the experiment the boot
default. Effective module parameters and their effects are included in the normal
operation review; unsupported parameters remain disabled. Automatic boot activation
is a later M6 integration decision requiring its own validated snapshot and device
gate, not a first-stage preference.

#### Independent configuration contract

Use a versioned typed settings record, initially represented by bounded key=value
text. Proposed limits: 16 KiB file, 64 fields and 1 KiB per line. Reject unknown
major versions, unknown/duplicate keys, overflow, invalid enum/boolean/numeric
values and values outside their declared limits. Keep settings types distinct
from target proof types; preference keys cannot replace proof fields.

Saved records contain a base preset plus explicit overrides. Normal resolution:
compiled defaults -> saved base preset -> saved overrides -> allowed CLI --set.
With an explicit one-shot --preset: defaults -> chosen preset -> saved overrides
for unrelated fields only -> allowed CLI --set. The one-shot path never writes
saved settings. Test both paths, including a saved resource override conflicting
with the chosen preset. Target policy and hard ceilings constrain each step. Report the effective
value and origin (default/preset/saved/one-shot), schema/version and a configuration
snapshot digest. Freeze one snapshot for an operation; saving settings during a
load affects the next operation, not the in-flight payload or policy.

Planned CLI additions: `settings show`, `settings validate`, `settings diff`,
`settings export`, `settings import`, `settings reset`; `--settings FILE`,
`--preset NAME` and typed `--set key=value` one-shot overrides. Preserve the current
`--config` meaning of kernel config input. Show defaults without creating files;
export/import/reset are explicit preference actions and never invoke load.

Proposed privileged store: a private root-owned settings file under
`/data/adb/husky-loader/`. Validate directory/file ownership, type, links and mode;
write a temporary file, fsync and atomically replace while retaining one last-good
snapshot. Unprivileged inspect/check may use a user-selected read-only settings
file. Do not create privileged storage merely to read defaults.

Serialize settings writers separately from loading; identify the saved generation
and reject stale writes. fsync the temporary file and containing directory after
rename, using checked directory-relative operations. A pre-rename failure keeps
the active file intact. A post-rename durability failure is commit_uncertain;
re-read/validate visible generations and report that state rather than promising
the previous file is necessarily still active. Last-good and active snapshots
are not an atomic multi-file transaction. Saving preferences must not hold the
load lock or alter a load's already-frozen snapshot.

Missing settings use defaults with experimental loading still off. Invalid saved
settings are reported; loading must not silently adopt a different configuration.
An explicit reset/recovery action can restore defaults or the last-good snapshot.
Early-init must account for unavailable encrypted /data: use a separately validated
minimal packaged test snapshot during M5a/M5c and a product snapshot after M6,
or remain on the selected existing boot path;
do not silently activate an experiment because preferences could not be read.

#### Manager experience

Authoritative visual direction:
[Official-native Expressive design](../specs/2026-10-12-manager-material3-expressive-native.md).
Use its native component/state/typography/motion rules for the settings screen;
the following requirements describe information and behavior, not an iOS skin.

Create a standard Material 3 grouped settings screen with Basic, Diagnostics,
Resources and Advanced groups. Use switches for booleans, radio/select controls
for discrete choices, and numeric input/sliders with units for bounded numbers.
Show the current/default value, source, effective platform limit and reason for
unavailable choices. Advanced technical details can collapse without hiding the
actual selected profile/strategy or a blocker.

Provide preset selection, an effective-settings preview, restore defaults and
redacted import/export. Keep a clearly labeled temporary diagnostic preset for
troubleshooting; it does not turn into persistent elevated permissions. Use the
same independently authored validation contract through a new adapter rather
than copying production loader settings logic. No Manager UI change is included
in this planning update.

#### Tests and milestone placement

- M1: schema/types/defaults, immutable-policy separation, validation and snapshot
  tests; malformed/oversized/unknown/duplicate input corpus.
- M3: commands/presets/one-shot precedence, help and effective-value reporting;
  import/export round trips and explicit reset behavior.
- M4: measure each resource preset, cache bounds and deadline behavior; confirm
  the same verification verdict for inputs within every compared preset's caps,
  and explicit resource_limit results when a chosen cap is exceeded.
- M6: Manager controls, authorized atomic persistence, early-init availability,
  crash/partial-write recovery and in-flight snapshot isolation.
- M7: ship schema/default/preset documentation and reviewed migration rules;
  refuse unsupported versions without dropping verification requirements.

Acceptance: every adjustable value is typed and bounded; presets cannot change
load permission or verification policy; persistence distinguishes pre-commit
failure from uncertain durability and retains a validated recovery snapshot;
settings never trigger loading; invalid settings never silently change an attempt.
Deadline/cancellation budgets apply only at interruptible preflight checkpoints,
not blocked reads or in-kernel work. Resource correctness comparisons use inputs
within each chosen cap; deliberately lowering a cap below input size produces a
documented resource_limit result, not a claim of identical acceptance across all
settings. Lean/debug log differences must not change successful load validation.
