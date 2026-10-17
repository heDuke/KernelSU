# Complete integration plan review

Date: 2026-10-17 UTC. Reviewed against main 49d48909, upstream
0faec0cc7b5f655170d89bf9c4d28bb5ca271fa2, the verified husky-v32650 release,
current Manager/kernel/userspace sources and retrieved official Android references.

Scope: review and improve the execution plan, not implement its pending features.
No code tests, device tests or new production release were performed for this
documentation revision. Earlier production and integration evidence remains
attributed to its original source commit.

## Result

The revised [complete plan](2026-10-17-upstream-sync.md) is actionable in milestones
M0-M5. Planning inconsistencies below are resolved in the document. Kernel/UAPI
compatibility, managed worker ownership and platform service behavior remain
implementation exit conditions, not claims established by this review. No PC
handoff or physical-device acceptance has been reintroduced as a publication gate.

## Findings and disposition

### R1 - High: accepted Superuser scope was missing

The previous plan preserved existing Superuser work but did not include the latest
request to restyle only application rows. Earlier discussion suggested shrinking
the overview, which the user explicitly declined.

Disposition: add section 4a2 with a virtualized grouped Material list, consistent
icons/text, independent controls and conditional metadata. Freeze title, overview,
search, filters, toolbar, UID grouping and authorization semantics. Removing the
ordinary visible grant overline must retain accessible checked/unknown/save state.

### R2 - High: background ownership could not be inferred from temporary shells

TerminalViewModel owns TempShellSession and ends it on clearing; its background
timeout is five minutes. ShellProcessHandle has no child wait status API. Sending
a command or seeing closed output does not prove task completion or an exit code.

Disposition: introduce a screen-independent task service/repository and supervised
Root worker with a separate control/status protocol, exact command submission,
Root identity and process ownership. Background jobs use independent noninteractive
shells; no claim of transferring a live interactive shell by rerunning its command.
Real wait status, stop_requested and worker/controller-death tests precede enablement.

### R3 - High: foreground-service type and notification behavior were unresolved

The manifest currently declares only DownloadService/dataSync. Reusing it for
arbitrary Root work would apply the wrong platform contract. shortService cannot
provide arbitrary-duration execution. The target SDK is 37, min SDK 31.

Disposition: specify a separate non-exported specialUse service with permission and
subtype declaration on API 34+, visible user-initiated starts, appropriate API 31-33
behavior and START_NOT_STICKY. Request notification permission contextually, handle
refusal without falsely declaring FGS prohibited, and test start failures and SDK 37
behavior. Official Android pages support this platform design; store approval and
OEM survival are not guaranteed by selecting a service type.

### R4 - High: stop success and restart behavior were underspecified

SIGINT delivery is not termination. Android lifecycle callbacks alone cannot
guarantee cleanup of a Root child. Saved PIDs or state=running can outlive their
process or refer to a later process. A service restart could duplicate side effects.

Disposition: explicit worker ownership, EOF cleanup, observed wait results,
idempotent task IDs, private task-scoped notification actions, stop_requested and
bounded TERM/KILL escalation. No command replay on boot, service/app restart or
WorkManager retry. Unknown ownership becomes interrupted/unknown. Unkillable or
detached descendants are not represented as successfully stopped.

### R5 - High: local-only favorites contradicted current backup configuration

AndroidManifest sets allowBackup=true. Both backup XML files contain effectively
default rules. Merely writing favorites/commands/logs to app-private files would
not establish that they remain local.

Disposition: explicitly exclude favorites, command metadata and logs from legacy
backup and cloud/device transfer, keep diagnostic sharing opt-in, and use versioned
atomic/transactional stores. Preserve exact command quoting and newlines; selecting
or editing a favorite never executes it. Existing 16384-character command limit
also applies to favorites and a multiline-capable composer.

### R6 - High: module state and arbitrary Root task concurrency were ambiguous

Module.update is a pending installed mutation, while remote update availability
comes from separate repository information. ModuleMutationGate serializes module
mutations, not arbitrary external Root commands. The enabled flag is not proof of
successful loading. RepoModule managerRequirements is currently a raw string.

Disposition: keep separate pending/update/diagnostic states, normalize sourced
metadata, retain readback and mutation serialization, and coordinate managed jobs
using an app-owned exclusive operation lease. Do not claim a shell parser or this
lease controls external/unmanaged Root operations. Pending uninstall exposes Undo
and blocks conflicting card actions.

### R7 - Medium: sheet transitions and action priority lacked a complete contract

Existing ModuleSheetHost handles preview/install state; adding More sheets without
transition rules could stack modals, duplicate operations or retain deleted module
selection. A connected toolbar proposal also remained in conversation history.

Disposition: the current plan explicitly supersedes that toolbar. Keep one primary
WebUI-or-Execute action and right-side More; Update and other actions go into More.
Use sequential sheet transitions, guarded mutations and explicit Back/focus rules.
Preserve full-page module detail, compact version rows and Remote/Local entry paths.

### R8 - Medium: dependency order and rollback wording were misleading

Selective stability backports may depend on older upstream commits not yet merged;
optional dependency upgrades cannot be delayed if required to compile the merge.
The older production APK cannot generally overwrite a newer Android version code.

Disposition: make the first batch dependency review, require complete backport sets
and reconcile them in the ancestry merge. Move required toolchain updates into M1.
Define old-UAPI bootstrap/diagnostic behavior before publication, preserve increasing
version codes and the production signer, and prefer a known-good-source rebuild
with a newer version code over promising a drop-in downgrade. Native assets remain
paired to source and exact target.

### R9 - Medium: resource and evidence boundaries needed measurable defaults

The former plan asked for bounded storage but set no initial limits. UI previews
can compile without being rendered; process mocks cannot establish Android service
lifecycle behavior. Public release provenance does not establish Canary boot safety.

Disposition: specify one running job, 16 queued, 2 MiB retained output per task,
20 MiB aggregate logs, 100 retained terminal records/seven-day retention; preserve
existing temporary-buffer bounds. Add quota/truncation behavior and a state/visual
matrix. Separate host process, JVM, Android instrumentation/emulation and publication
evidence; report unavailable checks honestly. No physical-device sign-off is required.

## Remaining implementation decisions and exit evidence

These do not require another design approval, but must be recorded in implementation
commits rather than guessed:

- The exact platform/library APIs used by the pinned SDK/Material versions and the
  minimal build prerequisites for the upstream merge.
- Version-specific old-UAPI bootstrap behavior, ABI layouts and feature gates.
- Worker protocol framing, controller ownership, process wait/signal handling and
  service-death behavior demonstrated by real process/lifecycle regressions.
- Live registry schema/provider provenance and migration of cached metadata.
- Final theme token application backed by previews/automated checks; unavailable
  visual or platform checks are explicit limitations, not passed tests.

The updated plan contains implementation paths, dependencies, initial defaults,
test expectations, release identity checks and recovery behavior for these items.
No upstream source integration or new-feature implementation is marked complete.
