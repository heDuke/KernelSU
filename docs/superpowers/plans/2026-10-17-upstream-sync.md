# HuskySU complete integration and Manager design plan

Consolidated scope: upstream synchronization, module management and metadata,
module card design, Superuser list styling, Expressive terminal, command favorites
and managed background commands. This document is the current execution plan;
older plans remain historical evidence where this document supersedes them.
Implementation and validation status: [execution record](2026-10-17-integration-execution.md).
The verified husky-v32650 release is the recovery source/reference baseline.

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
- Superuser visual work is limited to the application list. Preserve its title,
  overview statistics, search, filters, toolbar and existing permission semantics.
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
- [x] Create an isolated integration branch/worktree from the then-current main;
  record its SHA and re-evaluate any changes since this comparison.
- [x] Build a commit disposition list: port, retain local equivalent, UI adaptation,
  or defer with a reason. Check dependencies before any selective cherry-pick.
- [x] Target the pinned upstream main for integration testing. Stable v3.3.1 alone
  excludes newer fixes/features; do not silently substitute it for this target.

### 1. Stability review and dependency mapping first

- [x] Review/backport broken-ZIP handling (60662ac4), module config length checks
  (f866d782), kernel version parsing (88cfd4ce), and empty/long kallsyms token
  support (9f5966b4), with their required prerequisites and upstream regressions.
- [x] Reconcile SELinux/RCU, manager scanning, deadlock and service-stage fixes.
  Review security-sensitive changes even where Git merges without a conflict.
- [x] Preserve local supported compression methods, diagnostics and terminal
  process-group cleanup; check for semantic overlap rather than dropping either fix.
- [x] Classify these fixes before the ancestry merge; only backport separately
  when the prerequisite set is complete. Do not cherry-pick dependent fragments
  merely to satisfy batch order. Reconcile any backports in the later merge and
  test each buildable integration point. Do not publish this batch automatically.

### 2. Coupled kernel/userspace and loader integration

- [x] Merge the pinned upstream ancestry on the integration branch, resolve by
  subsystem, and retain the disposition record. Avoid whole-file ours/theirs.
- [x] Move kernel UAPI, Rust wrappers, Manager JNI and Kotlin capabilities together
  from 2 to 6, covering bundled LKM, service-stage result and preinit/symbol lookup.
- [x] Define behavior for old kernels before rollout: expose observed version and
  mismatch, disable unsupported writes, and keep diagnostics available. Do not
  relabel an incompatible interface as an inactive installation or empty allowlist.
- [x] Reconcile upstream production resolver changes with local loader validation,
  signed-byte preservation and error reporting. Never import the upstream vermagic
  retry path over the local rejection policy without explicit design review.
- [x] Review pinned package checks (6b5f55bd), APK signing block policy (aa08b390),
  production certificate propagation and install/upgrade/uninstall recognition.
- [x] Integrate preinit (0faec0cc) as a complete boot/install/recovery contract,
  including script placement, SELinux timing, fallback and failure observability.
  Do not move current boot hooks into preinit implicitly.
- [x] Review DDK/toolchain changes separately. Keep exact kernel release/page size,
  modversions/private symbols and CFI checks; do not expand the production matrix
  merely because upstream adds newer KMI targets.

### 3. Module infrastructure and new capabilities

- [x] Add a provider adapter for https://modules.kernelsu.org/api/v1/index.json
  (f7bfa28d). Verify the live API/schema, pagination/cache/error behavior and download
  integrity before making it a default; preserve supported custom/MMRL repositories.
- [x] Map provider metadata into the existing details model, including available
  versions/assets, README media, changelog, author, source, dates and dependencies.
  Apply the agreed compact version layout; never manufacture unknown compatibility.
- [x] Integrate one-shot script support (013b0448) with an explicit script preview
  and execution confirmation. Opening a repository/preview must not execute code.
- [x] Integrate hot install (200818ca) only for eligible modules; retain normal
  reboot-required fallback and surface the actual installation result. Never assume
  all modules or metamodules can be activated without reboot.
- [x] Add supported shell-root/ADB and kernel-module blocking settings using native
  M3 controls. Preserve opt-in behavior for boot-time module blocking (aaa14a34).
  Explain capability availability and migration without silently changing defaults.

### 4. Manager and build reconciliation

- [x] Port upstream functional changes into the current dashboard, Superuser,
  module screens and Bottom Sheets; do not restore old layouts from merge conflicts.
- [x] Upgrade AGP/Kotlin/Compose/Gradle/Rust dependencies in reviewable groups after
  interfaces stabilize; prerequisites needed to compile upstream APIs move into
  the coupled merge. Keep JDK 21 unless a verified build requirement changes it,
  and retain both libksud.so ABI payloads.
- [x] Preserve production-only package/signature checks, unique-input APK selection,
  embedded source identity and unchanged DEX/resources/manifest/assets verification.
- [x] Verify version-code monotonicity after ancestry integration and align APK,
  ksud, LKM, release tags and manifests. Merged commit count is not a UAPI guarantee.
- [x] Bring useful upstream tests/translations/docs along without resurrecting
  removed UI dependencies. Keep scripts/tests and experimental-loader checks intact.

### 4a. Module card presentation (confirmed design)

This specification replaces the earlier connected action-bar proposal. Apply it
to both enabled and disabled installed-module cards during Manager reconciliation.

- [x] Use native Material 3 cards, switches, buttons, icon buttons and status
  components with consistent spacing and corner treatment. Prefer theme surface
  roles and restrained emphasis over random per-module colors.
- [x] Give enabled modules a soft theme-colored container and a clear hierarchy:
  real icon or consistent fallback, module name, version/author, short description,
  then the primary action. Keep descriptions expandable rather than crowding the
  controls or duplicating full detail-page content.
- [x] Give disabled modules a compact neutral container with a visible disabled
  label and readable text; do not dim the entire card with reduced opacity.
  Keep the description expandable and show only currently supported actions.
- [x] Keep the enable switch independent. Place the three-dot more-actions button
  at the right of the card header, with distinct accessible labels and at least
  48dp touch targets; neither control may overlap the name or the other control.
- [x] Retain at most one primary action at the bottom: Open WebUI when available,
  otherwise Execute when available; omit the action area when neither applies.
  Do not add connected toolbars, multiple button rows or duplicate overflow buttons.
- [x] Open a Bottom Sheet from the card's three-dot button. Put all other available
  actions there, including Update, secondary execution entries, WebUI/action
  shortcut creation and Uninstall. Keep an update-available status on the card,
  but keep its update action inside More. Place Uninstall last with error-color
  text and retain its confirmation flow.
- [x] Show pending reboot, pending uninstall and abnormal states using concise
  icon/text status regions. An enabled switch alone must not imply successful
  runtime loading. Avoid generic unknown metadata or unsupported action placeholders.
- [x] Preserve natural height growth for long names and large fonts; keep action
  placement predictable. Check light/dark themes, both module states, no WebUI/action,
  both WebUI/action, update available and pending-state combinations through code
  review, previews or automated checks, without adding a PC acceptance prerequisite.

### 4a2. Superuser application list only

- [x] Replace separate heavy row cards with a grouped, virtualized native Material
  list. Use one visual group with rounded outer boundaries and subtle internal
  separators; do not compose an unbounded Column of all installed applications.
- [x] Use a consistent 44-48dp app icon, prominent application name, subdued package
  name and independent trailing authorization switch. Keep the existing narrow/
  large-text separate-control layout where needed rather than shrinking targets.
- [x] Remove the repeated ordinary grant/deny overline from visible rows. Preserve
  checked/state descriptions for accessibility and expose saving, stale/unconfirmed,
  shared UID, custom configuration, secondary user and error states when applicable.
- [x] Keep full UID grouping, all affected applications in confirmation, explicit
  profile save/readback and authorization gates. A row tap opens its existing detail
  sheet; switching must not also trigger the row. Apply the row design to search
  results without changing search, counts, sorting or filters.
- [x] Limit edits to list presentation and the supporting previews. Do not replace
  the approved overview with a compact counter strip or redesign the rest of the page.

### 4b. Expressive temporary Root terminal

The user requested a stronger Material 3 Expressive direction for the terminal
screenshot. Update the presentation and reading/input interactions while retaining
the bounded temporary-session lifecycle and process cleanup behavior.

- [x] Use a native Material app bar with a clear title and a compact, prominent
  rounded status capsule. Display only observed session states (connecting, ready,
  ended, failed); an active shell is not evidence that a command is still running.
  Use icon/text as well as color. Keep extra actions behind one More button.
- [x] Give the output a spacious, softly rounded surfaceContainer panel, with
  consistent inner padding, readable monospace typography and theme-aware contrast.
  Distinguish submitted commands using a restrained primaryContainer treatment;
  keep output continuous, without a separate card for every line or JSON object.
  Preserve original content, whitespace and existing ANSI handling.
- [x] Make the bottom composer a single expressive rounded tonal surface combining
  the command field and a clearly emphasized filled execute control. Replace the
  detached outlined rectangle/small pill combination. Keep the input legible,
  preserve drafts and correctly follow IME/navigation insets and large text.
- [x] Replace raw long quick-command chips with concise labels such as Modules,
  Kernel logs and Device info using the actual existing command mappings. Show a
  compact subset; expose the full presets and exact commands in a Bottom Sheet.
  Selecting a preset fills the composer; execution requires pressing Execute.
- [x] Put Clear output, Share log, font size and soft-wrap options in the More
  Bottom Sheet. Keep Interrupt distinct from End session and do not label signal
  delivery as proof that a command stopped. Do not imply full PTY or job-control
  support absent from the underlying session implementation.
- [x] Follow new output only while the user is at the bottom. When reading older
  output, retain scroll position and offer a compact Jump to latest control.
  Keep the existing buffer bounds, batched updates and selectable output.
- [x] Use dynamic primary/secondary and surface color roles, generous outer shapes
  and restrained state transitions; support light/dark and reduced-motion settings.
  Prefer native components; keep text contrast, 48dp touch targets and accessible
  control labels. Avoid decorative loading animations or color-heavy log content.
- [x] Review empty/connecting/ready/ended/error states, long JSON/URLs, text selection,
  keyboard-open layout, preset insertion without execution and preserved scroll
  position through code review, previews or automated checks. No PC acceptance step.

### 4c. Managed background commands and command favorites

The user additionally requested background task management and frequently used
command favorites. This extends the terminal plan with an explicit managed job
mode; it does not silently extend every temporary Root session's lifetime.

- [x] Add discoverable Tasks and Favorites entries near the composer, using native
  icon buttons with labels/tooltips and an active-task count where applicable.
  Open each collection in an expressive Bottom Sheet with clear empty states.
- [x] Offer foreground or background execution before submitting a command. Keep
  ordinary temporary sessions and their existing exit/background-timeout cleanup;
  background commands run in separate owned task processes. Do not rerun a command
  to simulate transferring an already running interactive session to the background.
- [x] Give managed jobs an owner independent of TerminalViewModel and screen
  lifetime. Define Android foreground-service eligibility/type, notification and
  start restrictions against the supported SDK before implementation; do not
  incorrectly label arbitrary Root commands as download/data-sync work.
- [x] Display queued, starting, running, completed, failed, canceled and interrupted
  states from observed process events. Include task name, submitted command,
  start time, elapsed time and exit code when available. Never infer a command's
  percentage or successful completion merely from an active shell or closed pipe.
- [x] Allow reopening a task's live output, copying/sharing its log, canceling a
  queued task and requesting stop of a running owned process group. A re-run is a
  new explicit execution. Do not offer pause/resume without real job-control support.
- [x] While jobs are active, provide an ongoing notification with a task summary
  and Open tasks action; stop actions must identify their exact owned task.
  Remove the active-service state when all tasks finish. Reopening the page must
  attach to the existing task rather than submit it again.
- [x] Persist bounded task metadata and logs, with configurable retention and
  conservative concurrency/queue/output limits. Do not rely on WorkManager retries
  for arbitrary commands or automatically restart commands after process death,
  force-stop or reboot. Reconcile unknown survivors as interrupted/unconfirmed,
  rather than pretending completion or sending signals to a reused PID.
- [x] Retain the Root identity handshake and private process-group ownership checks
  for managed jobs. Specify worker-death cleanup/reconciliation before enabling
  background execution; a service alone does not guarantee process survival.
- [x] Let users favorite the current input with a name, exact command and optional
  group. Support search, rename, edit, reorder, remove and pinning selected favorites
  above the composer. Keep built-in presets distinct from editable personal items.
- [x] Store favorites locally in app-private storage; preserve whitespace and shell
  quoting, enforce command/count limits and update atomically. Selecting a favorite
  fills the composer for review, never auto-executes or grants background execution.
  Saving, editing, opening or deleting a favorite must not launch a Root command.
- [ ] Add regressions for screen recreation and task reattachment, queue limits,
  output bounds, exit status, owned-group cancellation, stale/reused PID rejection,
  service/worker death and no automatic replay. Cover favorite persistence, ordering,
  exact quoting, empty/oversized input and selection without execution. Use automated
  process/lifecycle checks and code review; do not add PC acceptance requirements.

## Implementation contracts established by plan review

### Scope and milestone order

| Milestone | Buildable result | Dependency / exit condition |
| --- | --- | --- |
| M0 | Source snapshot, commit disposition and conflict map | Refresh main SHA; keep upstream fixed unless deliberately revised |
| M1 | Coupled upstream kernel/UAPI/ksud/ksuinit/JNI integration | Required toolchain changes, stable signer/package and old-UAPI diagnostics checked together |
| M2 | Repository adapters, metadata and Remote/Local installation | Parser/download integrity and existing ZIP/install contracts pass |
| M3 | Module cards, Superuser list and Expressive terminal | Approved layout boundaries and existing actions preserved |
| M4 | Command favorites and background runner | Favorites pass independently; worker protocol/lifecycle tests precede task UI enablement |
| M5 | Full candidate, review and production delivery | Required automated checks, rendered/emulated checks where available and independent public-asset verification |

Keep each milestone reviewable. No unrelated dashboard redesign, automatic upstream
polling, true PTY support, scheduler, automatic retries, reboot persistence or forced
experimental-loader activation is added by this plan. Existing experimental loader
settings/target checks remain supported; copying upstream loader code into it is
out of scope. No PC handoff or device acceptance is a merge/publication gate.

### State, data and interaction rules

- Separate update-available from pending-update: Module.update is an installed
  mutation/reboot marker, not proof of a newer remote release. Use the repository's
  version evidence for Update and the reboot store for pending state. Only show
  abnormal status with a real diagnostic. Keep stable module ID keys.
- For pending uninstall, show Undo uninstall in More and suppress conflicting
  actions. Serialize mutations through ModuleMutationGate, disable repeat taps
  while saving and reconcile with repository readback; display failure explicitly.
- Preserve provenance when mapping registry/MMRL/local metadata. Identify entries
  by provider plus module ID; do not silently merge unrelated modules with the
  same display name or switch an installed module to another provider for updates.
  Changing provider base URL invalidates its cache. Show cached/offline/error states.
- Retain repository URL, direct ZIP URL and local content-URI flows inside Remote/
  Local installation. Repository lookup only discovers candidates; opening a URL
  never downloads, executes or installs automatically. Use existing validation,
  download verification and review before installation.
- Distinguish repository-declared verification from independent verification.
  Parse manager restrictions, API limits and dependencies as structured fields
  with sources, not raw map/string output. Missing fields are omitted. Explicit
  unsupported declarations remain visible. Never infer KernelSU compatibility
  from a generic archive, a green badge or an absent restriction.
- Use compact version rows: version/date first, size as secondary data, expandable
  changelog and one install action. Show asset selection only for genuinely multiple
  valid assets; keep missing size/date/file information hidden. README images may
  fail independently without blanking text or creating broken-image placeholders.
- Keep only Discover as the repository entry; remove duplicate module toolbar/menu
  repository links and move recommendations out of the installed-module list.
  Keep the existing batch/recovery functionality without duplicating install entry
  points. Do not add new status filters without mapping them to real data.
- Keep one active ModalBottomSheet host per feature: preview -> install review and
  module More -> shortcut/update confirmation are explicit transitions. Close the
  old sheet before opening the next, clear selection on removal, handle Back/focus
  correctly and avoid stacked dim layers. Full module detail remains a full page.
- Use 16dp card content padding, standard theme shapes/colors and at least 48dp
  interactive targets as starting tokens; adapt to narrow layouts and 200% text.
  Keep persistent status comprehensible without color; honor system motion scale.

### Background runner architecture and state

- Add a dedicated internal TerminalTaskService, task repository and immutable
  task-state model. The service owns execution while UI ViewModels only observe
  and send task-ID actions. Do not modify DownloadService into a command runner.
- On API 34+, use the specialUse foreground-service type for the explicit,
  user-started Root command use case, its permission and subtype declaration;
  use the appropriate foreground-service behavior on API 31-33. Keep the service
  non-exported, start from the visible user action, handle start/promotion failures
  and use START_NOT_STICKY. Verify SDK 37 compatibility in instrumentation/emulation.
  Neither shortService's approximately three-minute limit nor dataSync semantics
  fit arbitrary-duration commands. No system-exempt privileges are assumed.
- Request notification permission contextually. Refusal is not itself a platform
  prohibition on foreground services: keep task controls visible in-app and state
  that notification-drawer controls are unavailable. Still post the required service
  notification; do not promise permanent drawer visibility when permission is denied.
- Use a supervised Root worker (planned ksud task-runner subcommand) per running
  job, with a dedicated framed control/status channel separate from stdout/stderr.
  Submit the exact command as data to a fresh non-interactive shell; never inject
  command text into a generated control shell string or infer exit from log markers.
  Background jobs have closed/noninteractive stdin and do not inherit the temporary
  shell's cwd, environment edits or functions; display execution context explicitly.
- Worker contract: task UUID, Root UID, PID/PGID, process start identity, authenticated
  controller channel, child spawn acknowledgement and wait status. Wait/reap the
  owned command, preserve signal termination separately from exit code, bound pipe
  drain after exit, and label detached/unmanaged descendants as outside supervision.
  This is not a sandbox for arbitrary Root code.
- State transitions: queued -> starting -> running -> completed/failed; queued may
  become canceled. Running/starting stop requests enter stop_requested first.
  Interrupt sends SIGINT; Stop uses owned-group TERM then a bounded KILL escalation
  after five seconds if ownership is still established. Mark canceled only after
  termination is observed. A natural exit racing a stop retains its observed result.
  If termination remains unconfirmed, retain stop_requested with a diagnostic and
  keep the operation lease while the live controller still owns the task; a timeout
  alone is not a cancellation result. Repeated stop requests are idempotent.
  Unknown/dead controller state becomes interrupted with unknown exit code.
- Root worker detects controller-channel EOF and cleans up its owned command group;
  Android service destruction is best-effort extra cleanup, not the only mechanism.
  Test controller/worker death, PID reuse, unresponsive commands and pipe flooding.
  If lifecycle ownership tests fail, keep the feature unfinished rather than claiming
  background reliability. Do not replay commands through BOOT_COMPLETED or WorkManager.
- Use explicit, idempotent task IDs for submission/cancellation. Notification actions
  use private immutable PendingIntents scoped to that task; stale actions cannot
  target a new task. Reopening UI attaches without resubmitting. Internal events
  must not accept forged exported intents as task state.
- Coordinate managed jobs and module mutations through an application-owned exclusive
  operation lease: wait visibly while installer/managed job holds it, and release
  on every terminal path. Do not parse arbitrary shell commands to claim they cannot
  conflict with module operations. Existing unmanaged interactive Root commands are
  outside this coordination; do not promise global device serialization.

### Persistence and initial resource defaults

- Task metadata uses app-private transactional SQLite with versioned migrations;
  logs use task-UUID filenames and bounded segments. A database transaction does not
  make log-file writes atomic: reconcile missing/truncated files and incomplete task
  states on restart. Never treat persisted running as proof of a live owned process.
- Start with one running job, at most 16 queued jobs, 100 retained terminal records
  and seven-day completed-record retention. Reserve space before starting; retain
  at most 2 MiB output per task and 20 MiB total logs. Drop/rotate retained output
  with an explicit truncation marker while continuing to drain streams, not blocking
  the child or crashing the app. Do not evict active task metadata. Report quota-full
  rather than silently starting an untracked task. User-adjustable limits remain
  within validated caps; duration limits are optional and shown before execution.
- Keep the existing temporary scrollback limit (5000 lines / 512 Ki characters)
  and batched UI updates. Changing log presentation must not create unbounded lists,
  per-character recomposition or full-log rereads on every chunk.
- Favorites use a versioned app-private AtomicFile repository with a serialized
  writer. Initial caps: 200 favorites, 20 groups, four pinned items, name up to
  64 characters, command up to the existing 16384-character limit. Preserve exact
  quoting/newlines; reject blank commands without trimming valid command content.
  The composer expands for multiline favorites; do not flatten them into one line.
- Exclude favorites, task commands/metadata and logs from both legacy Auto Backup
  and cloud/device-transfer extraction rules; app-private files alone do not imply
  local-only storage while allowBackup=true. Do not include them in crash reports
  or ordinary diagnostics automatically. Share only a user-selected log via the
  existing FileProvider flow. Versioned favorites/settings survive normal upgrades.

### Implementation map and required checks

| Work | Existing integration points | Targeted evidence |
| --- | --- | --- |
| Kernel/UAPI/production loader | kernel, uapi, userspace/ksud, userspace/ksuinit, Natives/JNI | Android compilation/lints, host loader contracts, exact-target audit, old/new UAPI fixtures |
| Repository/install | ModuleRepoRepositoryImpl, RepoModule, ModuleInstallController, ModuleSheetHost | Provider/cache/schema fixtures, download verification, ZIP regressions, no automatic execution |
| Module cards | ModuleMaterial, ModuleViewModel, ModuleRebootStore, ModuleMutationGate | State/action matrix, one main action, overflow transitions, pending uninstall/readback |
| Superuser list | SuperUserMaterial, SuperUserDesignPreviews, SuperUserPolicyTest | UID grouping, independent switch/row semantics, unknown/save/error states; unchanged overview |
| Terminal UI/favorites | TerminalScreen, TerminalViewModel, TempShellSession; new FavoritesRepository | Preset/favorite insertion without execution, quoting persistence, IME and scroll anchoring |
| Background jobs | New task service/repository/worker; AndroidManifest and backup rules | Real host process exit/cancellation/ownership tests, service/lifecycle and private notification instrumentation |
| Publication | husky-release.yml, manager_build_identity, verify_husky_release | Clean source, sole trusted signer, both ABI payloads, unchanged repack UI hashes, public checksums |

Minimum UI preview matrix: light/dark; normal/200% font; narrow phone/expanded width;
module enabled/disabled/pending uninstall/pending update/update available, with both/
one/no runnable actions; Superuser ordinary/shared/stale/saving; terminal empty/large
output/keyboard-open/selection; task running/stopping/completed/failed/unknown and
favorite empty/long/multiline states. Compile previews and render or instrument where
the environment supports it; report unavailable checks, never call compilation visual
approval. There is no PC acceptance requirement.

### Upgrade, release and recovery

- At M1, specify and test the old-UAPI upgrade path before rollout. A mismatched
  driver retains observed identity and diagnostics, not fake inactive/empty state.
  Gate unsupported writes and features. Any legacy bootstrap needed to install the
  paired candidate must use a separately verified version-specific contract; do not
  require the new UAPI to install that UAPI or bypass a mismatch via an unverified
  Root fallback.
- Retain the stable production package/certificate. Never publish test certificates
  as production or imply that the separate .pr package is overwritten by an update.
  Source metadata and version code come from the exact built commit after the merge.
- Use additive/versioned local schemas where possible and test existing settings,
  authorization presentation, favorites and task-history migration. Imported task
  records are historical; they never re-create commands.
- Retaining an older APK is a recovery reference, not a normal in-place rollback:
  Android version-code downgrade rules still apply. Prefer rebuilding known-good
  source with a higher version code and the same production certificate, keeping
  data formats readable. Pair native payloads with that source and exact device
  target; do not automatically flash or uninstall user applications.
- Run targeted checks after each affected milestone, then one full candidate suite
  and manual production workflow. Publish no incomplete feature as completed. Keep
  unresolved runtime/Canary limits explicit; automated publication is allowed after
  the required checks without adding a PC/device sign-off step.

## Plan review and official references

Review result and closed planning findings:
[2026-10-17-plan-review.md](2026-10-17-plan-review.md).

- [Android foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Foreground service background-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Notification runtime permission](https://developer.android.com/develop/ui/views/notifications/notification-permission)
- [Compose Bottom Sheets](https://developer.android.com/develop/ui/compose/components/bottom-sheets)
- [Material 3 lists](https://m3.material.io/components/lists/guidelines)

Official Android pages were retrieved during review; specialUse is a platform type,
not proof of acceptance by an app store or guaranteed execution under every OEM.
Use the native components available in the pinned Material library. Verify exact
API signatures against that dependency before implementation; a newer upstream
design screenshot alone is not a reason to restore removed UI libraries.

## Verification and rollout

Per user request, PC Codex handoff and physical-device acceptance are removed
from this synchronization plan and are not prerequisites for merging or publishing.
Record unverified device behavior accurately; automated checks alone do not prove
cold-boot, recovery or Canary private-ABI/CFI compatibility.

- [x] Kernel: build supported production KMI/architectures and run module identity
  audits; exercise package/signature rejection and exact-target failures.
- [x] Rust: run Android cargo ndk check, clippy, then fmt for changed workspaces;
  run loader, kallsyms, archive/config, module lifecycle and host process regressions.
- [x] Manager: clean assembleRelease and JVM tests with both ksud ABI binaries;
  cover old/new UAPI, missing root, authorization failures, ZIP errors, repository
  parsing/integrity, script confirmation and hot-install fallback.
- [ ] Review light/dark, large font, keyboard, navigation and Bottom Sheet flows.
  Report which checks used code review, previews or automated tests.
- [x] Merge into main after the required automated checks and code review pass;
  retain ancestry and a reviewed record of intentional deviations.
- [x] Publish a new production release only after the candidate passes. Independently
  verify its downloaded APK/certificate/provenance/checksums as for husky-v32650.
  Retain known-good source, APK and matching native assets as recovery references;
  follow the version-code/data-migration recovery contract above.

Upstream application code is now integrated on the isolated branch. The execution
record distinguishes passed host/JVM/build checks from unavailable Android runtime
instrumentation; checked implementation items are not claims of device validation.

Final production delivery, subsequent remote merge, UAPI upgrade repair and
Canary engineering output: [delivery record](2026-10-18-paired-uapi-delivery.md).
Unavailable Android runtime instrumentation remains a disclosed limitation.
