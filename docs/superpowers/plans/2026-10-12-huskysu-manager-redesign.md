# HuskySU Manager redesign, repositories and LKM validation

Date: 2026-10-12
Status updated 2026-10-13: Manager redesign, source adapters, shared module sheets
and experimental settings implemented and locally built/tested. Earlier loader/
auditor hardening retained. No device test or verified-support promotion. See
[execution record](2026-10-13-implementation-status.md) and
[PC Codex handoff](2026-10-13-pc-codex-handoff.md).

## Design direction

User revision: use official Material 3 Expressive with native Compose Material 3
components. Implementation has resumed under user authorization. The authoritative
page/component spec is
[Official-native Expressive design](../specs/2026-10-12-manager-material3-expressive-native.md).
Dopamine/palera1n/Sileo/Saily may inform operation flow; they no longer define the
visual style. MMRL remains a repository architecture reference.

This supersedes the restrained color/row direction previously written here and
the older custom-helper-first Expressive policy. It complements the Canary and
independent-loader plans without changing their support claims.

References:

- https://github.com/opa334/Dopamine
- https://github.com/palera1n/loader
- https://github.com/Sileo/Sileo
- https://github.com/Lakr233/Saily (currently redirects to Lakr233/Irisin)
- https://play.google.com/store/apps/details?id=com.dergoogler.mmrl
- https://github.com/MMRLApp/MMRL

## Pages and presentation

| Destination | Planned contents |
| --- | --- |
| Status | Root state, compatibility summary, one contextual main action, updates, device details and maintenance |
| Discover | Search, source/category filters, repository management; curated content only when supplied |
| Modules | Installed modules, versions/authors, enabled state, updates, pending restart, local ZIP install |
| Authorization | App identity, granted policy, available request history and per-app profile |
| Settings | Appearance, update channels, notifications, logs, backup/recovery and grouped advanced operations |

Use phone bottom navigation and a rail on wider layouts. Preserve Android back
behavior and navigation state; essential destinations must not require swiping.
Reuse Navigation3, existing ViewModels and privileged operation contracts.

Status first answers whether root works, compatibility, and the next action.
Principal actions: install entry when absent, view update when available, restart
with a reason when needed, or diagnostics when unsupported/unauthorized/unknown.
Keep root availability, Manager authorization, compatibility and task states
separate. OTA preparation, installation, update, restore and removal are distinct
operations with prerequisites and effects shown before confirmation.

Device details retain device identity, build, kernel release, architecture, KMI,
page size, LKM/Manager versions, slot and security state when available. Unknown
values stay unknown; concise summaries link to full details.

Visual system: official Expressive theme, dynamic/role-based color, emphasized
type hierarchy, official component shapes/size variants and expressive motion.
Use native Material 3 controls directly, with domain-only composition where needed.
Follow the new spec for page hierarchy, wrapper migration and accessibility;
avoid fixed-height/one-line action wrappers and handwritten disabled-state colors.

## Repository findings and selection

Observed on 2026-10-12: the official homepage returns 200, the current Manager
`https://modules.kernelsu.org/modules.json` returns 404, and the old detail-path
sample returns 404. The new official `/api/v1/` endpoints return 403/error 1010
from this environment, so full migration data remains unvalidated here.

MMRL catalog: `https://mmrl.dev/api/repositories.json`. It returns 403 here, while
the user's phone screenshot confirms successful access. This is not evidence of
a global outage. Catalog entries provide base URLs; MMRL's `IRepoManager` resolves
`json/modules.json` against each base. The previous Alt Repo 404 probe omitted
the extra `json/` segment and was incorrect.

| Candidate source | Base URL | Verified module index | HTTP/count |
| --- | --- | --- | --- |
| Googlers | `https://gr.dergoogler.com/gmr/` | `https://gr.dergoogler.com/gmr/json/modules.json` | 200 / 40 |
| Magisk Modules Alt Repo | `https://magisk-modules-alt-repo.github.io/json-v2/` | `https://magisk-modules-alt-repo.github.io/json-v2/json/modules.json` | 200 / 117 |
| IzzyOnDroid | `https://apt.izzysoft.de/magisk/` | `https://apt.izzysoft.de/magisk/json/modules.json` | 200 / 141 |

Counts are snapshots; checks cover index retrieval/structure, not installation
or HuskySU compatibility. Offer these sources for selection rather than silently
enabling all catalog sources. Directory discovery must not block known sources.
Official v1 and KernelSU Next manifests need separate adapters; the Next lists
are repository references and need release-asset and module-ID resolution.

Source references:

- https://github.com/MMRLApp/MMRL/blob/master/app/src/main/kotlin/com/dergoogler/mmrl/stub/IRepoManager.kt
- https://github.com/KernelSU-Modules/tools/blob/main/CONTRACTS.md
- https://github.com/KernelSU-Next/KernelSU-Next-Modules-Repo

Persist stable source identity, base/index URL, adapter, enabled state and last
success. Support add, enable/disable, refresh and remove. Resolve URLs properly;
validate HTTPS, redirects, relative paths, schema versions, response shape and
field bounds. Unsupported data must produce an error, not a successful empty list.

Refresh independently with bounded concurrency/timeouts. Validate before replacing
each cache; retain last good data on failure and label it stale. Use conditional
requests when supported. One failed source must not hide healthy sources.

Key listings by source identity plus module ID. Keep source metadata and avoid
name-based merging. Remember installation provenance; legacy/local installs have
unknown provenance until explicitly resolved. Never switch update sources
automatically. Source removal/disable must not uninstall modules or cause an
automatic replacement from a different source.

## Details and operation flow

Online module preview and module installation confirmation use the official
Material 3 ModalBottomSheet. Only the complete module detail opens a full-screen
page in this browsing/install flow. Online list entries open preview first;
View details opens the full detail. Local ZIP selection and Install/Update from
preview or detail open the shared confirmation sheet. Replace preview content
with confirmation inside one sheet instead of stacking overlays. Preserve the
underlying list position, support scroll/expanded sheet states for long content
and large fonts, and use official drag handle, scrim, focus and back behavior.

Both sheets include native Close and Share IconButtons in the header. Sharing uses
the Android Sharesheet for the module name/version and canonical link when present,
with plain-text basic information as the fallback. Online preview shows module
icon/name/author, compact version/source/size/compatibility information, supplied
screenshots in a horizontal row and a short introduction, followed by Install/Update
and View details. Missing screenshots do not create an empty gallery; missing size
or compatibility stays explicit. Expand images/introduction within the same sheet;
keep full README/changelog in the full-screen detail. Closing dismisses the sheet
without confirming installation or cancelling an active task.

Details show icon/name, ID, authors, source, installed/available versions, summary,
README, releases/changelogs, available size, license, links, dependencies and
compatibility metadata. Missing size is unknown, not zero. Place Download ZIP and
Install near the header as distinct operations. Constrain repository HTML and
external links; repository descriptions must not inherit module WebUI privileges.

Compatibility has compatible/incompatible/unknown states grounded in actual
constraints and device/root information. Magisk repository membership is not
KernelSU compatibility. Source verification flags, download integrity and device
validation are separate. Preserve the single-metamodule rule and show mounting
requirements. Incompatibility explanations precede any applicable confirmation;
hard technical or integrity failures cannot be overridden by a UI confirmation.

First implement single-module selection, review, execution and result. Review
shows source, version, asset, integrity data, compatibility/unknowns, dependencies
and restart needs. Confirmation uses native action buttons;
hard verification failures block installation with a visible reason. Dismissing
before confirmation starts no task; dismissing after confirmation does not cancel
an active operation. Keep operation feedback in the sheet or underlying task area.
Use real stages: queued, downloading, validating, installing,
success/failure and restart required. Show bytes, known totals, speed and logs;
do not fabricate script progress. Cancel/pause only when genuinely supported and
safe. Durable background tasks, notifications, resumability and batch queues are
later backend capabilities, not promises implied by new controls. Batch privileged
installs must be serial and account for dependency order and metamodule conflicts.

## Confirmed LKM checks and limits

Target: Pixel 8 Pro, ZP11.260918.007, kernel
`6.1.172-android14-11-g3fc92ed75e44-ab16169542`; prepared official configuration
and matching source were inspected. Findings apply to this target only.

| Layer | Existing behavior / evidence |
| --- | --- |
| Download integrity | `VerifiedArtifact.write` requires expected size and SHA-256, writes privately, checks digest, fsyncs and atomically promotes; this is not a publisher signature |
| User-space compatibility | `ksu-contracts::validate_module` checks ELF architecture, page-size metadata, KMI and exact release; retry validation forbids changing release/feature tokens |
| Kernel ABI | `CONFIG_MODVERSIONS=y`; kernel performs version checks including `module_layout`. Offline candidate audit matches 136 recorded symbol CRCs against official Module.symvers |
| Kernel signatures | `CONFIG_MODULE_SIG=y`, `CONFIG_MODULE_SIG_ALL=y`, `CONFIG_MODULE_SIG_PROTECT=y`; `CONFIG_MODULE_SIG_FORCE` is not set |
| GKI protection | Matching source allows unsigned modules subject to GKI symbol/export restrictions; malformed or invalid signatures can still be fatal |
| Control-flow checks | `CONFIG_CFI_CLANG=y`; audit compares one shared CFI type ID, which is not a complete CFI/runtime proof |
| Manager authorization | Kernel recognizes the Manager by configured package identity and APK signing-certificate size/hash; this is independent of .ko signature/integrity |

Candidate ZIP inspection: the `.ko` lacks the Linux appended-signature marker;
its audit records 206 resolved symbols, 136 checked CRCs, one CFI ID comparison,
`offline_checked_candidate`, and `device_tested=false`. Only 4K is covered.

Important loader limit: `ksuinit::load_module` rewrites unresolved ELF symbols to
absolute addresses using kallsyms before `init_module` for unsigned modules.
Signed modules now retain their bytes and use kernel symbol resolution without
the mutation/retry path. Original-file hashes and
offline CRC comparisons do not prove all references were validated by the kernel
loader. The separate `module_layout` check still matters. ELF mutation also means
an original appended signature cannot simply be assumed valid on submitted bytes.
Do not add signing or advertise trusted signed loading without resolving that
load-path contract. No forced loading or removal of CRC/signature protection is
part of this plan.

Existing code evidence:

- `manager/app/src/main/java/me/weishu/kernelsu/core/utils/VerifiedArtifact.kt`
- `userspace/ksu-contracts/src/lib.rs`, `userspace/ksuinit/src/lib.rs`
- `kernel/manager/apk_sign.c`, `scripts/audit_canary_lkm.py`
- Matching kernel `kernel/module/signing.c`, `main.c`, `Kconfig`
- `/workspace/toolchain/ksu-canary-kdir/.config`
- `/workspace/artifacts/HuskySU-ZP11.260918.007-4k-candidate.zip` audit report

Plan UI labels independently: file integrity, target match, signature presence,
trusted-signature verification (unknown until actually checked), offline audit,
and device verification. Never collapse them into a single Verified badge. A
test-signed APK cannot be presented as a production-authorized root Manager.

Before claiming installable/verified LKM support, review signature handling across
ELF mutation, actual GKI restrictions, loader errors and complete runtime CFI
behavior, then test boot/load/root access/restart/recovery on the matching device.
No such device verification was performed during this planning update.

### Follow-up verification and required LKM gate

See [LKM follow-up report](2026-10-12-husky-lkm-validation.md) and
[evidence JSON](2026-10-12-husky-lkm-validation-evidence.json).
Three existing real-artifact audit tests passed. The offline loader model rewrites
all 206 imports to SHN_ABS: normal per-import CRC/namespace/access resolution is
not entered for those symbols, while the separate module-layout check remains.
The shipped candidate covers its 135 imports with published CRCs plus module_layout;
71 other imports have no published CRC and require exact-target/runtime evidence.

Before the fix, the audit accepted a temporary copy missing the kobject_del CRC.
The completeness check and missing-record regression are now implemented.
Retain an L0 gate:
require expected CRC coverage, test missing records, define pre-rewrite import and
signature contracts, and then perform matching-device loading/CFI/recovery tests.
L0 precedes stronger LKM installation assurances or verified-support claims; UI
and repository work can proceed independently without promoting support status.
CFI is non-permissive; BTF mismatch is allowed by this target. Neither a single
compared CFI ID nor BTF presence establishes full compatibility.

### Initial loader hardening implemented

- Collect every unsigned-module import, scan the complete built-in symbol table,
  reject missing/zero/ambiguous addresses and read/parse failures, then mutate a
  copy. Same-address aliases and duplicate module symbol entries are handled.
- Leave signed modules unchanged for the kernel; do not manually resolve their
  imports or retry by changing signed metadata. Do not claim signature trust from
  the trailer marker.
- Retry unsigned vermagic formatting only after ENOEXEC and a diagnostic matching
  this module's name and original magic; preserve the existing token-equality gate.
- Bound kernel diagnostic reads to 64 KiB/256 attempts, handle interrupted/overrun
  reads, report errno-oriented next steps, and avoid unnecessary kptr_restrict
  writes. Log failures restoring a temporary symbol-visibility change.
- Require CRC coverage for all imports present in pinned Module.symvers, in
  addition to the existing value checks and module_layout requirement.

Validation: ARM64 cargo-ndk check and Clippy with warnings denied (ksuinit and its
ksud consumer), formatting, seven host loader tests including the real 206-import
candidate, and four real-artifact Python audit tests passed. No module-loading
syscall ran in these tests. Source inspection confirms the boot loader logs a
module-load failure and continues preparing the system init; actual boot recovery
has not been tested. Remaining L0 items: runtime ABI/CRC guarantees for mutated
imports, trusted-signature paths, relocation/CFI and matching-device recovery.

## Implementation order and gates

Execution resumed. P0-P3 application code is implemented and locally built;
end-to-end device acceptance remains open. P4 includes sequential batch review
and existing foreground downloads, but durable process-death install recovery
and additional v1/Next adapters remain future work.

The independently authored experimental CLI is tracked in
[Independent experimental loader](2026-10-12-independent-experimental-loader.md).
It is separate from production loader hardening and is not a default Manager or
boot path. Integration requires the experimental device/comparison gates.

| Phase | Work | Gate |
| --- | --- | --- |
| L0 | Audit coverage, pre-rewrite import/signature contracts, matching-device LKM tests | No missing applicable CRCs; actual load/CFI/recovery evidence before stronger support claims |
| P0 | Source model, MMRL-format adapter, three selectable sources, per-source cache/errors | Real snapshots parse; bad source preserves working sources and last good cache |
| P1 | Shared visual tokens, navigation, Status and installed Modules | Existing actions remain reachable and correctly gated; LKM validation distinctions visible |
| P2 | Catalog discovery, source management, details and source-bound updates | Missing data explicit; no cross-source automatic replacement |
| P3 | Single-module operation review, actual stages/results/logs | Review matches executed asset; privileged safeguards preserved |
| P4 | Optional durable tasks, notifications/resume/batch and official v1/Next adapters | Backend and device checks pass before controls are enabled |

Primary touchpoints: `ModuleRepoRepositoryImpl.kt`, `RepoModule.kt`,
`ModuleRepoApi.kt`, `ModuleRepoViewModel.kt`, `ui/screen/modulerepo/`,
`ui/screen/home/`, `module/`, `superuser/`, `settings/`, `ui/MainActivity.kt`
and `ui/theme/` under the current Manager source tree.

Use meaningful parser/cache/provenance fixtures and repository-required component
checks when implementation begins. Validate light/dark, large fonts, offline,
empty data, partial failure, unknown compatibility, authorization denial, pending
restart and operation interruption. LKM claims require separate matching-device
tests with an authorized Manager; UI tests cannot establish kernel compatibility.

Manager UI implementation and artifact distribution remain separate tasks. The
initial loader/auditor implementation above is the only runtime change included;
it does not alter the kernel or establish device-tested compatibility.
