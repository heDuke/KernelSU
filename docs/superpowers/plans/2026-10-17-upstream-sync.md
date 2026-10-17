# HuskySU upstream synchronization plan

User requested a plan to follow the updated upstream. This document records a
read-only comparison and merge simulation, not a completed code integration.
The verified husky-v32650 production release remains the rollback baseline.

## Pinned comparison (2026-10-17 UTC)

- Fork source: bb1cf974aac44aa18368ad32b3fb9d56f3a3326d.
- Upstream: https://github.com/tiann/KernelSU, branch main.
- Upstream target: 0faec0cc7b5f655170d89bf9c4d28bb5ca271fa2,
  module preinit support, committed 2026-10-16 21:31:40 +08:00.
- Latest upstream stable release at inspection: v3.3.1 (2026-10-12).
- Common ancestor: a33dbca339ad4201072f035f187288a0df9a9321 (2026-08-17).
- Divergence: 62 fork-only and 104 upstream-only commits. These are the complete
  divergence, not 104 commits added today. Git patch-ID comparison reports no
  equivalent commits; independent implementations still require semantic review.
- Upstream changes: 410 files; 146 files modified on both sides.
- `git merge-tree --write-tree HEAD upstream/main` reports 78 conflict files,
  including 21 modify/delete conflicts, without changing the working tree/index.
- Current UAPI is 2; target UAPI is 6. Treat this as a coupled platform update.

## Invariants

- Preserve HuskySU branding, package me.weishu.kernelsu.husky.fork, the trusted
  production certificate and increasing Android version codes.
- Retain the M3 Expressive dashboard/editor, expressive information sheets,
  Superuser access fixes and native Material navigation/components. Keep removed
  Miuix/liquid UI removed; port functional fixes into the retained UI.
- Preserve ZIP compression validation/logging, bounded temporary terminal sessions,
  update-failure classification, exact LKM target gates and release provenance.
- Module previews and installation review remain Bottom Sheets with close/share,
  available icon/screenshots/description; only module detail uses a full page.
- Keep one repository entry in Discover and Remote/Local installation choices.
  Hide absent metadata; do not infer compatibility from missing declarations.
- Production loader changes must preserve signed module bytes, reject unresolved
  private symbols and keep existing no-force/no-blind-retry safeguards.
- The independent experimental loader stays a separate self-authored codebase.
  Do not copy upstream resolver/loader implementations into experiments/husky-loader.
  Preserve its existing target-proof checks and runtime enablement gates.
- Upstream synchronization does not establish ZP11.260918.007 Canary compatibility.
  Generic KMI builds are not substitutes for the pinned target's ABI/CFI checks.

## Execution batches

### 0. Freeze evidence and integration policy

- [x] Fetch upstream main, pin both sources and simulate conflicts.
- [x] Finish and independently verify the existing production publication.
- [ ] Create an isolated integration branch/worktree from the then-current main;
  record its SHA and re-evaluate any changes since this comparison.
- [ ] Build a commit disposition list: port, retain local equivalent, UI adaptation,
  or defer with a reason. Check dependencies before any selective cherry-pick.
- [ ] Target the pinned upstream main for integration testing. Stable v3.3.1 alone
  excludes newer fixes/features; do not silently substitute it for this target.

### 1. Stability fixes first

- [ ] Review/backport broken-ZIP handling (60662ac4), module config length checks
  (f866d782), kernel version parsing (88cfd4ce), and empty/long kallsyms token
  support (9f5966b4), with their required prerequisites and upstream regressions.
- [ ] Reconcile SELinux/RCU, manager scanning, deadlock and service-stage fixes.
  Review security-sensitive changes even where Git merges without a conflict.
- [ ] Preserve local supported compression methods, diagnostics and terminal
  process-group cleanup; check for semantic overlap rather than dropping either fix.
- [ ] Keep each dependency-complete fix reviewable and test the affected component
  before adding feature work. Do not publish this batch automatically.

### 2. Coupled kernel/userspace and loader integration

- [ ] Merge the pinned upstream ancestry on the integration branch, resolve by
  subsystem, and retain the disposition record. Avoid whole-file ours/theirs.
- [ ] Move kernel UAPI, Rust wrappers, Manager JNI and Kotlin capabilities together
  from 2 to 6, covering bundled LKM, service-stage result and preinit/symbol lookup.
- [ ] Define behavior for old kernels before rollout: expose observed version and
  mismatch, disable unsupported writes, and keep diagnostics available. Do not
  relabel an incompatible interface as an inactive installation or empty allowlist.
- [ ] Reconcile upstream production resolver changes with local loader validation,
  signed-byte preservation and error reporting. Never import the upstream vermagic
  retry path over the local rejection policy without explicit design review.
- [ ] Review pinned package checks (6b5f55bd), APK signing block policy (aa08b390),
  production certificate propagation and install/upgrade/uninstall recognition.
- [ ] Integrate preinit (0faec0cc) as a complete boot/install/recovery contract,
  including script placement, SELinux timing, fallback and failure observability.
  Do not move current boot hooks into preinit implicitly.
- [ ] Review DDK/toolchain changes separately. Keep exact kernel release/page size,
  modversions/private symbols and CFI checks; do not expand the production matrix
  merely because upstream adds newer KMI targets.

### 3. Module infrastructure and new capabilities

- [ ] Add a provider adapter for https://modules.kernelsu.org/api/v1/index.json
  (f7bfa28d). Verify the live API/schema, pagination/cache/error behavior and download
  integrity before making it a default; preserve supported custom/MMRL repositories.
- [ ] Map provider metadata into the existing details model, including available
  versions/assets, README media, changelog, author, source, dates and dependencies.
  Apply the agreed compact version layout; never manufacture unknown compatibility.
- [ ] Integrate one-shot script support (013b0448) with an explicit script preview
  and execution confirmation. Opening a repository/preview must not execute code.
- [ ] Integrate hot install (200818ca) only for eligible modules; retain normal
  reboot-required fallback and surface the actual installation result. Never assume
  all modules or metamodules can be activated without reboot.
- [ ] Add supported shell-root/ADB and kernel-module blocking settings using native
  M3 controls. Preserve opt-in behavior for boot-time module blocking (aaa14a34).
  Explain capability availability and migration without silently changing defaults.

### 4. Manager and build reconciliation

- [ ] Port upstream functional changes into the current dashboard, Superuser,
  module screens and Bottom Sheets; do not restore old layouts from merge conflicts.
- [ ] Upgrade AGP/Kotlin/Compose/Gradle/Rust dependencies in reviewable groups after
  interfaces stabilize. Keep required JDK 21 and both libksud.so ABI payloads.
- [ ] Preserve production-only package/signature checks, unique-input APK selection,
  embedded source identity and unchanged DEX/resources/manifest/assets verification.
- [ ] Verify version-code monotonicity after ancestry integration and align APK,
  ksud, LKM, release tags and manifests. Merged commit count is not a UAPI guarantee.
- [ ] Bring useful upstream tests/translations/docs along without resurrecting
  removed UI dependencies. Keep scripts/tests and experimental-loader checks intact.

### 4a. Module card presentation (confirmed design)

This specification replaces the earlier connected action-bar proposal. Apply it
to both enabled and disabled installed-module cards during Manager reconciliation.

- [ ] Use native Material 3 cards, switches, buttons, icon buttons and status
  components with consistent spacing and corner treatment. Prefer theme surface
  roles and restrained emphasis over random per-module colors.
- [ ] Give enabled modules a soft theme-colored container and a clear hierarchy:
  real icon or consistent fallback, module name, version/author, short description,
  then the primary action. Keep descriptions expandable rather than crowding the
  controls or duplicating full detail-page content.
- [ ] Give disabled modules a compact neutral container with a visible disabled
  label and readable text; do not dim the entire card with reduced opacity.
  Keep the description expandable and show only currently supported actions.
- [ ] Keep the enable switch independent. Place the three-dot more-actions button
  at the right of the card header, with distinct accessible labels and at least
  48dp touch targets; neither control may overlap the name or the other control.
- [ ] Retain at most one primary action at the bottom: Open WebUI when available,
  otherwise Execute when available; omit the action area when neither applies.
  Do not add connected toolbars, multiple button rows or duplicate overflow buttons.
- [ ] Open a Bottom Sheet from the card's three-dot button. Put all other available
  actions there, including Update, secondary execution entries, WebUI/action
  shortcut creation and Uninstall. Keep an update-available status on the card,
  but keep its update action inside More. Place Uninstall last with error-color
  text and retain its confirmation flow.
- [ ] Show pending reboot, pending uninstall and abnormal states using concise
  icon/text status regions. An enabled switch alone must not imply successful
  runtime loading. Avoid generic unknown metadata or unsupported action placeholders.
- [ ] Preserve natural height growth for long names and large fonts; keep action
  placement predictable. Check light/dark themes, both module states, no WebUI/action,
  both WebUI/action, update available and pending-state combinations through code
  review, previews or automated checks, without adding a PC acceptance prerequisite.

## Verification and rollout

Per user request, PC Codex handoff and physical-device acceptance are removed
from this synchronization plan and are not prerequisites for merging or publishing.
Record unverified device behavior accurately; automated checks alone do not prove
cold-boot, recovery or Canary private-ABI/CFI compatibility.

- [ ] Kernel: build supported production KMI/architectures and run module identity
  audits; exercise package/signature rejection and exact-target failures.
- [ ] Rust: run Android cargo ndk check, clippy, then fmt for changed workspaces;
  run loader, kallsyms, archive/config, module lifecycle and host process regressions.
- [ ] Manager: clean assembleRelease and JVM tests with both ksud ABI binaries;
  cover old/new UAPI, missing root, authorization failures, ZIP errors, repository
  parsing/integrity, script confirmation and hot-install fallback.
- [ ] Review light/dark, large font, keyboard, navigation and Bottom Sheet flows.
  Report which checks used code review, previews or automated tests.
- [ ] Merge into main after the required automated checks and code review pass;
  retain ancestry and a reviewed record of intentional deviations.
- [ ] Publish a new production release only after the candidate passes. Independently
  verify its downloaded APK/certificate/provenance/checksums as for husky-v32650.
  Retain the previous APK and matching native assets for a coherent rollback.

No upstream application code was merged when preparing this plan. Merge conflict
counts are a textual preflight result, not a complete estimate of semantic risks.
