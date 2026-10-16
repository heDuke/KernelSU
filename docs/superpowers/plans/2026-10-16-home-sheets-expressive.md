# Home dashboard and Expressive information sheets

Date: 2026-10-16. Status: design direction and color policy accepted and merged
into the plan. The implementation is now in the working tree; final local
validation and physical acceptance are tracked separately below.

This supplements the [Manager plan](2026-10-12-huskysu-manager-redesign.md),
[native component spec](../specs/2026-10-12-manager-material3-expressive-native.md)
and [home dashboard plan](2026-10-15-home-dashboard.md). It supersedes their
single-card sheet editor and flat information-sheet presentation. Other module,
repository, loader and device-verification requirements remain applicable.

Baseline: main c6b49268. The current uncommitted Inware-inspired home prototype
has a local Release APK and 26 passing JVM tests, with one older fixture skipped.
These results do not validate the planned changes below or physical rendering.
User screenshots 2437-2439 and 2443 establish the interaction and visual concerns.

## Accepted direction

- Inware informs overview density, grouping and the card library; use original
  composition and native Material 3 Expressive components.
- Overview cards precede category entries containing complete information.
  Compatibility stays present. Card size, selection and order are configurable.
- Edit in the actual home layout. Keep save/cancel accessible, explain the draft
  scope, provide visible selection and a way to locate newly added cards.
- Information sheets use summary-first presentation: a focal status/value,
  key indicators, grouped information and a relevant action. Full information
  remains reachable; material risks and blocking reasons stay directly visible.
- Keep native sheet headers/close controls reachable. Use a fixed footer only
  when there is a meaningful primary action. Reset scrolling on panel changes.
- Preserve native drag/expanded states and system insets. Content grows for
  larger fonts; narrow windows use one column. Avoid a universal fixed height.
- Module preview and installation confirmation remain native bottom sheets;
  module details remain full-screen. Preserve their explicit Close and Share
  controls and the existing Android Sharesheet behavior.

## Accepted color policy

Keep the existing dynamic-color preference and optional Husky brand theme.
The brand seed remains #1A73E8; generate complete light/dark Material palettes.
No extra color settings are required for these sheets.

| Content | Color roles | Meaning |
| --- | --- | --- |
| Sheet background | surfaceContainerLow / onSurface | Separate the sheet from the native scrim |
| Normal focal status/summary | primaryContainer / onPrimaryContainer | One dominant focal block per sheet |
| Secondary metrics/context | secondaryContainer / onSecondaryContainer | Supporting emphasis, with explicit labels |
| Occasional category accent | tertiaryContainer / onTertiaryContainer | Category emphasis, not a warning/success code |
| Ordinary grouped information | surfaceContainer / onSurface | Supporting text uses onSurfaceVariant |
| Unknown/not checked/not authorized | surfaceContainerHigh / onSurface | State the observation limit without implying disabled text |
| Confirmed error/danger confirmation | errorContainer / onErrorContainer | Apply only to the affected operation or result |
| Primary action/selection | primary / onPrimary | Use native enabled, pressed, selected and disabled behavior |

Pair each container with its matching on-color. Status always has text and an
appropriate icon. Do not assume dynamic tertiary is amber or primary is green.
An HTTP 403 on release lookup belongs in a local retryable update notice; it
does not turn unknown driver activation into a confirmed failure. Lack of Manager
authorization cannot establish that the driver is absent or unloaded.

Color acceptance still requires light/dark, brand and multiple dynamic palettes.
Target at least 4.5:1 for ordinary text, 3:1 for large text and meaningful
non-text controls. Retain native disabled behavior and test actual enabled-state
contrast; screenshots alone cannot certify all dynamic-color combinations.

## Sheet content plan

| Sheet | Focal content | Supporting information and action |
| --- | --- | --- |
| Activation | Active / unknown / confirmed failure plus one reason | Authorization, driver observability, actual Root probe, check time; recheck/report |
| Access and mode | Manager access and observed driver mode | Root availability, UAPI, late-load limits; meaningful next steps |
| Healthy observation | Foreground observed healthy interval | System uptime and observation scope separately; incident history/report |
| Incidents | Observed incident total and latest event | Chronological history with full reasons, without duplicated recent events |
| Todos | Pending count and most actionable item | Updates, authorization, module issues and pending reboot; explicit checking/unknown states |
| Versions/install | Manager and driver versions | UAPI vs build-counter distinction, update results and supported install actions |
| Modules | Installed/configured/pending counts | Updates, metamodule and batch records; safe mode is not proof of active effects |
| Device/system | Model, Android and current kernel | Build, ABI, page size, slot and security fields; full copyable long values |
| Compatibility | Known match / explicit conflict / unverified | Separate environment, selected-input and device-stage evidence; network errors stay separate |
| Backups/recovery | Whether a matching backup is available | Identity, target, digest and provenance when known; unknown legacy fields stay unknown |
| Advanced | Current installation and loading strategy | Group installation, inspection, loaders and backups; retain experimental boundaries |

Remove repeated reasons across the summary and detail list. Reuse one observation
for the same fact and expose its time/scope. Normal runtime remains a foreground
observation, not a historical/background kernel-health guarantee.

## Review corrections to implement

| ID | Correction | Acceptance |
| --- | --- | --- |
| R01 | Persist real module changes requiring reboot | A successful enable/disable remains visible until a confirmed reboot; do not invent a marker from configured counts |
| R02 | Make newly added/selected cards easy to inspect | Offer a visible preview or explicit locate action; preserve useful library position for adding multiple cards |
| R03 | Clarify quick-action and whole-home save scope | A local action must not unexpectedly commit unrelated card edits; cancel has a documented, consistent effect |
| R04 | Distinguish removal from overview and fixed sections | Use remove-from-overview wording; explain when tasks/actions remain in the fixed area |
| R05 | Add native selected-state semantics | TalkBack identifies the selected card and correct control targets; outline is not the only feedback |
| R06 | Model todo completeness and authorization | Unchecked/unknown/error cannot appear as a confirmed empty task list; no premature Root-failure claim during probing |
| R07 | Define update scope and local feedback | Name Manager/driver vs module checking explicitly, or aggregate them; show checking/result/retry in place |
| R08 | Match availability text to actual prerequisites | Distinguish authorization, Root, UAPI, safe-mode restrictions and busy operations |
| R09 | Fix sheet headers and panel navigation | Close stays accessible; a newly opened panel starts at its beginning rather than inheriting another panel's offset |
| R10 | Show preflight work and freeze reviewed inputs | Native busy feedback; changing an input cancels/restarts review or is blocked; confirmation matches the displayed choices |
| R11 | Verify backup matching before enabling recovery | Arbitrary readable backups do not imply current-target recoverability; export remains independent |
| R12 | Preview bulk module effects | Show affected count/identity and skipped items, highlighting metamodule effects before confirmation |
| R13 | Structure installation confirmation | Present operation, file, partition, slot and backup summary before expandable technical details; action stays accessible |

## Implementation sequence

- [x] P0: Prepare coherent loading/unknown/error/reboot/update/prerequisite state
  models, incorporating R01, R06-R08 and the observation limits above.
- [x] P1: Apply the native sheet structure and paired color roles to activation,
  versions/install and device/system first. Produce reviewable samples covering
  normal, unauthorized, checking and HTTP 403, plus a dark-theme sample.
- [x] P2: Reuse the reviewed structure across the remaining information sheets;
  fix duplicated content, scroll reset and fixed header/action behavior (R09).
- [x] P3: Complete editor feedback, save semantics, remove wording, selection
  accessibility and large-font/landscape toolbar adaptation (R02-R05).
- [x] P4: Complete preflight feedback/input consistency, recovery readiness,
  bulk previews and structured install confirmation (R10-R13).
- [x] P5: Run relevant state/preference/operation regressions and the required
  clean Release build; record APK hashes/signature and the exact source revision.
- [ ] P6: Hand off physical visual and privileged-operation acceptance to PC
  Codex, preserving the trusted production Manager and recovery preparation.

Plan phases may share state models/components, but prototype availability or a
passing build never marks untested design or device behavior complete.

## Acceptance matrix

- Light/dark, Husky brand and varied wallpaper palettes; Chinese/Latin, long
  values, 200-percent fonts, narrow/wide/landscape and system navigation insets.
- TalkBack selection, names, reading/focus order, touch targets, fixed close and
  accessible primary action; respect system motion settings.
- Healthy, not authorized, not observed, checking, offline/403, partial/stale
  information, missing input/backup and operation-busy states.
- Edit/add/resize/reorder/remove, cancel/back/save/reset, rotation/restart,
  matching quick-action save scope and no privileged actions from preview.
- No premature empty/success state, duplicated root cause or unsupported driver
  compatibility inference; essential risks remain visible before execution.
- Check-only never invokes loading or writes partitions. Keep the independent
  experimental load/boot gate closed. Manager rendering work does not change
  Canary support claims, trusted signing requirements or loader ABI/CFI gates.

## Execution record

The home and sheet changes, R01-R13 corrections and native recovery inspection
are implemented in the current working tree. See
[implementation and PC acceptance](2026-10-16-expressive-sheets-acceptance.md).
P5 passed: clean Release assembly, 31 passing JVM tests (one older fixture
skipped), Android ARM64 check/Clippy, formatting, both daemon release builds and
the host changed-image recovery regression. P6 physical acceptance remains
open for the user/PC Codex. Color mapping is implemented, while actual palette
contrast, rendering and touch behavior still require device acceptance.
No kernel or independent-loader execution gate was changed. No commit/push or
real device flash, load, boot or recovery is claimed here.
