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

### 4b. Expressive temporary Root terminal

The user requested a stronger Material 3 Expressive direction for the terminal
screenshot. Update the presentation and reading/input interactions while retaining
the bounded temporary-session lifecycle and process cleanup behavior.

- [ ] Use a native Material app bar with a clear title and a compact, prominent
  rounded status capsule. Display only observed session states (connecting, ready,
  ended, failed); an active shell is not evidence that a command is still running.
  Use icon/text as well as color. Keep extra actions behind one More button.
- [ ] Give the output a spacious, softly rounded surfaceContainer panel, with
  consistent inner padding, readable monospace typography and theme-aware contrast.
  Distinguish submitted commands using a restrained primaryContainer treatment;
  keep output continuous, without a separate card for every line or JSON object.
  Preserve original content, whitespace and existing ANSI handling.
- [ ] Make the bottom composer a single expressive rounded tonal surface combining
  the command field and a clearly emphasized filled execute control. Replace the
  detached outlined rectangle/small pill combination. Keep the input legible,
  preserve drafts and correctly follow IME/navigation insets and large text.
- [ ] Replace raw long quick-command chips with concise labels such as Modules,
  Kernel logs and Device info using the actual existing command mappings. Show a
  compact subset; expose the full presets and exact commands in a Bottom Sheet.
  Selecting a preset fills the composer; execution requires pressing Execute.
- [ ] Put Clear output, Share log, font size and soft-wrap options in the More
  Bottom Sheet. Keep Interrupt distinct from End session and do not label signal
  delivery as proof that a command stopped. Do not imply full PTY or job-control
  support absent from the underlying session implementation.
- [ ] Follow new output only while the user is at the bottom. When reading older
  output, retain scroll position and offer a compact Jump to latest control.
  Keep the existing buffer bounds, batched updates and selectable output.
- [ ] Use dynamic primary/secondary and surface color roles, generous outer shapes
  and restrained state transitions; support light/dark and reduced-motion settings.
  Prefer native components; keep text contrast, 48dp touch targets and accessible
  control labels. Avoid decorative loading animations or color-heavy log content.
- [ ] Review empty/connecting/ready/ended/error states, long JSON/URLs, text selection,
  keyboard-open layout, preset insertion without execution and preserved scroll
  position through code review, previews or automated checks. No PC acceptance step.

### 4c. Managed background commands and command favorites

The user additionally requested background task management and frequently used
command favorites. This extends the terminal plan with an explicit managed job
mode; it does not silently extend every temporary Root session's lifetime.

- [ ] Add discoverable Tasks and Favorites entries near the composer, using native
  icon buttons with labels/tooltips and an active-task count where applicable.
  Open each collection in an expressive Bottom Sheet with clear empty states.
- [ ] Offer foreground or background execution before submitting a command. Keep
  ordinary temporary sessions and their existing exit/background-timeout cleanup;
  background commands run in separate owned task processes. Do not rerun a command
  to simulate transferring an already running interactive session to the background.
- [ ] Give managed jobs an owner independent of TerminalViewModel and screen
  lifetime. Define Android foreground-service eligibility/type, notification and
  start restrictions against the supported SDK before implementation; do not
  incorrectly label arbitrary Root commands as download/data-sync work.
- [ ] Display queued, starting, running, completed, failed, canceled and interrupted
  states from observed process events. Include task name, submitted command,
  start time, elapsed time and exit code when available. Never infer a command's
  percentage or successful completion merely from an active shell or closed pipe.
- [ ] Allow reopening a task's live output, copying/sharing its log, canceling a
  queued task and requesting stop of a running owned process group. A re-run is a
  new explicit execution. Do not offer pause/resume without real job-control support.
- [ ] While jobs are active, provide an ongoing notification with a task summary
  and Open tasks action; stop actions must identify their exact owned task.
  Remove the active-service state when all tasks finish. Reopening the page must
  attach to the existing task rather than submit it again.
- [ ] Persist bounded task metadata and logs, with configurable retention and
  conservative concurrency/queue/output limits. Do not rely on WorkManager retries
  for arbitrary commands or automatically restart commands after process death,
  force-stop or reboot. Reconcile unknown survivors as interrupted/unconfirmed,
  rather than pretending completion or sending signals to a reused PID.
- [ ] Retain the Root identity handshake and private process-group ownership checks
  for managed jobs. Specify worker-death cleanup/reconciliation before enabling
  background execution; a service alone does not guarantee process survival.
- [ ] Let users favorite the current input with a name, exact command and optional
  group. Support search, rename, edit, reorder, remove and pinning selected favorites
  above the composer. Keep built-in presets distinct from editable personal items.
- [ ] Store favorites locally in app-private storage; preserve whitespace and shell
  quoting, enforce command/count limits and update atomically. Selecting a favorite
  fills the composer for review, never auto-executes or grants background execution.
  Saving, editing, opening or deleting a favorite must not launch a Root command.
- [ ] Add regressions for screen recreation and task reattachment, queue limits,
  output bounds, exit status, owned-group cancellation, stale/reused PID rejection,
  service/worker death and no automatic replay. Cover favorite persistence, ordering,
  exact quoting, empty/oversized input and selection without execution. Use automated
  process/lifecycle checks and code review; do not add PC acceptance requirements.

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
