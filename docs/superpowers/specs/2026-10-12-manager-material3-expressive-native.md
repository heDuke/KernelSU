# HuskySU Manager: official Material 3 Expressive design

Date: 2026-10-12. Updated 2026-10-13: implementation resumed by the user;
Manager code and local build/tests completed, physical-device acceptance pending.
Scope: Android phone/tablet Manager, including experimental-loader settings and
operation screens. Wear OS components are not used for this application.

2026-10-14 review: GKI is not a mandatory UI eligibility condition. A kernel
version heuristic must not label the device unsupported, hide installation
choices or suppress the actual working mode. Missing Manager access is an
unknown installation/authorization state. Integrated mode is labelled Built-in;
LKM remains LKM. Selected image/module identity, page size, API, ABI, signature
and recovery requirements retain their own validation; this UI policy does not
claim support for arbitrary non-GKI targets.

This is the authoritative visual direction, superseding the restrained iOS-style
presentation in the earlier Manager plan and the custom-helper-first policy in
the older Expressive UI system. Repository and loader verification requirements
remain in their respective plans. iOS tools may inform operation flow only.

## Official references and implementation baseline

- Material 3 in Compose: https://developer.android.com/develop/ui/compose/designsystems/material3
- Compose Material 3 API: https://developer.android.com/reference/kotlin/androidx/compose/material3
- Compose release notes: https://developer.android.google.cn/jetpack/androidx/releases/compose-material3?hl=en
- Expressive overview: https://m3.material.io/blog/building-with-m3-expressive
- Material accessibility: https://m3.material.io/foundations/accessible-design/overview

The Expressive overview requires JavaScript in the available browsing tool; the
accessible official Compose guidance and release notes were also inspected.
Source observation: Manager pins Material 3 1.5.0-alpha26 and Compose BOM
2026.08.00, opts into the Expressive APIs and already uses MaterialExpressiveTheme
with MotionScheme.expressive(). Official release notes list 1.5.0-beta01 on
2026-10-07. Do not silently upgrade dependencies as part of a design revision;
check the pinned artifact API first, and evaluate a beta migration separately.

Use official AndroidX Compose Material 3 composables as the native components.
Version-specific signatures, opt-ins and availability must be checked before
implementation; APIs promoted to stable are distinct from a stable artifact
release. Where an Expressive variant is unavailable, use a compatible official
Material 3 component, rather than drawing a replacement control.

## Visual direction

Build a strong hierarchy through color roles, emphasized typography, component
size/shape and expressive motion. A clear focal area introduces the page; native
grouped controls and readable supporting information complete it.

- Theme: one MaterialExpressiveTheme hierarchy, official expressive MotionScheme
  and theme-provided typography/shapes. Audit explicit overrides against official
  defaults before keeping them; the theme name alone does not establish conformity.
- Color: dynamic light/dark schemes on supported Android versions; a complete
  official-role brand fallback. Choose primary/secondary/tertiary container roles
  for emphasis and surfaceContainer roles for supporting sections. Use their
  matching on-colors; avoid fixed blue/purple paints and handwritten disabled alphas.
- Typography: large page title, headline status, clearly emphasized section/action
  labels, body text for explanation and readable technical values. Use actual
  theme type styles; check emphasized-style API availability rather than faking
  the entire scale with arbitrary font sizes/weights. Test Chinese and Latin text.
- Shape: official component defaults and stateful expressive shape APIs where
  available. Avoid applying one extra-large radius to every component or replacing
  buttons with generic rounded Surfaces.
- Motion: native press/selection/shape and scroll transitions, using the theme
  motion scheme. Match any necessary layout transition to that scheme. Respect
  disabled/reduced animation settings; no bespoke global spring constants.
- Density: spacious focal content and efficient native information rows. Project
  spacing suggestions are 16dp outer gutters, 8-12dp related-element gaps and
  24dp between major sections; these are layout proposals, not universal official
  token requirements. Allow window size and font scaling to change layout.

## Native component contract

| Purpose | Preferred official component | Usage rule |
| --- | --- | --- |
| Page structure | Scaffold, LargeFlexibleTopAppBar/compatible native app bar | Official scroll behavior, insets and collapse; no custom app-bar physics |
| Primary action | Button with official expressive size/shape defaults | Highest emphasis; natural/wrapping label; never fixed-height clipping |
| Supporting action | FilledTonalButton, OutlinedButton, TextButton | Hierarchy follows actual importance |
| Related choices | ButtonGroup with appropriate native toggle/button children, or SingleChoiceSegmentedButtonRow | Real selection semantics; native vertical radio alternative on narrow/large-font layouts |
| Navigation | NavigationBar/NavigationBarItem; NavigationRail on wide layouts | Native active indicator, labels, badges and interaction feedback |
| Status/focal surface | Card/CardDefaults with thematic container role | Informational card is not clickable unless it has a real separate destination |
| Information/settings rows | ListItem, HorizontalDivider | Native text/icon layout; genuine actions only; no empty click ripples |
| Boolean/single/multiple selection | Switch, RadioButton, Checkbox | Official colors, disabled state, semantics and touch targets |
| Search/filter | SearchBar and FilterChip | Selection/action chips represent real interaction; read-only metadata uses text |
| Menus/dialogs | Official menu APIs, AlertDialog, ModalBottomSheet | Correct anchors, focus, dismissal and back handling |
| Ongoing unknown-duration stage | LoadingIndicator, native pull-to-refresh indicator | Indeterminate activity; no invented overall percentage |
| Known download progress | LinearWavyProgressIndicator/CircularWavyProgressIndicator when supported; native linear/circular alternative | Driven by actual bytes; percentage also available as readable text |
| Numeric settings | Slider for appropriate bounded values, OutlinedTextField for precise input | Units, allowed range and validation visible |
| Transient feedback | SnackbarHost/Snackbar | Persistent failures remain visible in page content |
| Local ZIP action | ExtendedFloatingActionButton when it fits the page hierarchy | Scaffold position/insets; no custom floating capsule/navigation bar |

Native component availability is a compile-time/design gate, not permission to
emulate missing Expressive controls. LoadingIndicator remains version-sensitive;
retain the required opt-in where applicable. ButtonGroup is a grouping/layout API,
not itself a complete radio-selection policy.

## Page designs

### Status

Top to bottom:

1. Native large flexible app bar: HuskySU title and a small number of real actions.
2. One native status Card with a prominent status icon/headline, short explanation
   and compact root/loader/compatibility summaries. Use primary or secondary
   container emphasis for the focal area, error roles for an actual blocking error.
   Unknown support remains explicitly unknown, rather than using a success color.
3. One prominent native Button near that summary: Install, View update, Restart
   when explicitly applicable, or Run diagnostics. Select from the real state.
4. Native update/OTA sections with a clear supporting action; keep operation
   meanings and target-slot details distinct.
5. Device information as native ListItem rows with a Details destination for full
   build/kernel/KMI/page-size/version/security values and optional copy actions.
6. Maintenance/documentation grouped rows with lower-emphasis actions.

The status Card does not need its own whole-card click when the actual action is
a Button. Avoid nesting competing main actions. Use color, heading scale and the
button's official shape interaction to create emphasis; device metadata stays
readable and selectable. Font scaling may stack sections and wrap labels.

### Discover and repository management

- Native app bar and SearchBar; source/category FilterChips below the search input.
- Native cards for supported curated sections, when data exists; standard module
  ListItem rows for the browsable results. Do not invent recommendations or counts.
- Icons have consistent native layout space and a fallback when artwork is absent.
- Tapping an online module opens the native preview ModalBottomSheet described
  below. Only its explicit View details action opens the full-screen module detail.
- Repository management uses ListItem + Switch, status text and a trailing menu.
  Each source displays last successful sync/cached/error state, not a blank list
  presented as successful when one source fails.
- Native LoadingIndicator for the initial fetch, official pull-to-refresh feedback,
  and a visible retry action within the failed source section.
- No custom iOS-style grouped table, search capsule or third-party navigation skin.

### Installed modules

- Native app bar, search, optional native chips/tabs for meaningful list subsets.
- Module body opens details; the enable Switch is a separate native target with
  correct semantics and event handling. One tap must not both navigate and toggle.
- Name/version/author/status and pending-restart/update information remain visible.
  Use text/icons for state and official theme roles for emphasis.
- Native ExtendedFloatingActionButton for local ZIP install if appropriate;
  multi-selection uses Checkbox and a native contextual app bar/toolbar.
- Batch install action exists only after backend capability and verification gates.

### Module previews and installation confirmation

Both online module preview and installation confirmation use the official
ModalBottomSheet. They are overlays on the current destination, not full-screen
navigation destinations or AlertDialog replacements styled to look like sheets.
Within the module browsing/install flow, only the complete module detail is a
full-screen page; operation feedback stays in the sheet or an inline task area.

- Shared sheet header: official drag handle, a concise title, and native Close
  and Share IconButtons with accessible labels and minimum touch targets. Keep
  header actions reachable while content scrolls. Close dismisses the entire
  sheet; Back retains the preview/confirmation behavior below. Neither closes by
  cancelling an operation. Share opens the Android Sharesheet with module name,
  selected version and the canonical module/source link when available; otherwise
  share those basic details as text without inventing a URL. Sharing does not
  confirm installation or dismiss the module sheet when the chooser returns.
- Preview content order: module icon/name and author; concise version/source/size
  and compatibility/status information; supplied screenshots; short introduction;
  native Install/Update and View details actions. Use official Typography, Text,
  ListItem and buttons with standard Compose image/layout primitives. Keep this
  a quick, readable overview; full README, changelog and technical information
  belong to the full-screen detail. Download ZIP is a distinct optional action.
- Screenshots use a horizontally scrollable LazyRow, preserve image proportions,
  and provide descriptive accessibility labels. An image can expand within the
  current sheet with a return-to-overview action; do not add a full-screen preview
  destination. Show screenshots only when supplied by the source; failed images
  get a compact retry state, and missing artwork gets a neutral module icon.
- Keep overview metadata short and the primary action readily reachable. Long
  introductions have an inline expand/collapse action; large fonts wrap and scroll
  naturally. Missing size/compatibility stays explicit; do not fabricate screenshots
  or metadata to fill space.
- Installation confirmation: module identity, source or local ZIP filename,
  selected version/asset, available size, integrity results, compatibility reasons
  and unknowns, dependencies, mounting/conflict information and restart needs.
  Required warnings remain visible before the native Confirm install button.
  Hard verification failures disable confirmation and show the reason.
- Local ZIP selection and Install/Update from either preview or detail open this
  same confirmation sheet. Selecting a version/source updates the reviewed asset;
  confirmation is invalidated when that asset changes.
- Transition from preview to confirmation by replacing the content of one sheet;
  do not stack sheets. Back returns to preview when entered there, then dismisses
  it; standalone confirmation returns to its underlying destination. Opening
  View details dismisses the preview and preserves the browsing position.
- Keep the official drag handle, scrim, shape, insets, focus and dismiss semantics.
  Use native scrolling content and naturally sized action buttons. Support the
  official expanded sheet state for long content and large fonts without routing
  preview/confirmation to a separate full-screen page.
- Dismissing before confirmation starts no task. After confirmation, dismissal
  must not cancel an active operation; show its state in the underlying task area.
  Offer cancellation only at stages where the backend supports it safely.

### Module detail (full-screen)

Use a native app bar, module icon/name and emphasized identity section. Place the
primary Install/Update Button and supporting Download ZIP action near the header.
Install/Update always opens the confirmation ModalBottomSheet before execution.
Use a native ButtonGroup only where related actions fit and their roles remain
clear; stack native buttons at large font scale or narrow width.

Below: installed/available version, author/source, size or unknown, compatibility
and reasons, then description/changelog/release details. Use Card/ListItem sections
and native tabs only when genuine content groups warrant them. Keep release/source
selection a native menu or sheet. Technical integrity and target-match detail can
expand into a dedicated view; a single success badge must not collapse different
verification states. Render supplied README in a constrained content area rather
than a homemade control library.

### Authorization

Use native app bar/search, optional real FilterChips, and app ListItem rows. Show
app icon/name, authorization state and available request information. The profile
screen uses native radios/switches/sliders for supported policies, with explicit
save/apply behavior and readable defaults. Failed authorization remains an
actionable explanation, not an animated success state.

### Settings and experimental-loader settings

Use a native large flexible app bar and ListItem-based sections: Appearance,
Updates, Diagnostics, Experimental loader and Advanced. A leading icon, title,
supporting explanation and native trailing control provide clear hierarchy.

Loader-specific screen:

1. Native informational Card: Experimental status, supported profile, verification
   stage and the fact that selecting settings does not execute a load.
2. Balanced/Lean/Diagnostic preset choice using official ButtonGroup/toggles when
   supported, or a native segmented/radio choice. Selection state is announced.
3. Basic, Diagnostics, Resources and Advanced ListItem groups with native Switch,
   bounded input/Slider or a sheet-based single choice. Show current/default values
   and units; settings outside platform/profile limits are disabled with a reason.
4. Effective-settings preview as native information rows, including changed fields,
   origin and next-operation scope. Profile/verification policy is distinct from
   user preferences.
5. Explicit native Save/apply action when there are edits. Reset uses AlertDialog;
   import/export use platform file/share interfaces and preserve the loader plan's
   validation and redaction requirements.

The UI does not offer skip-CRC/signature/target controls. A saved preference does
not activate boot integration or initiate loading. At large text size, three-way
preset choice becomes a vertical native radio list instead of shrinking labels.

### Operation and diagnostics

- Native stage headline and concise task identity; known byte progress uses a
  native determinate progress indicator with bytes/speed/percentage labels.
- Unknown preflight/kernel phases use official LoadingIndicator and truthful text.
  Kernel initialization has no fabricated countdown or guaranteed cancel action.
- Native Cancel is available only where the operation can actually cooperate;
  cancellation_requested differs from completed cancellation.
- Success/failure uses icon, text and applicable native actions. Module presence,
  initialized state and authorized root health remain distinct.
- Native diagnostic sheet/detail rows expose reason codes and redacted log export;
  extended logs are content, not an alternative custom dialog/control system.

## State and accessibility acceptance

Create preview/reference states for each page: loading, data, empty, offline,
partial-source failure, actual error, unsupported/unknown target, authorization
denied, busy, pending restart and operation result.

Use role-correct paired colors and check contrast: normal text at least 4.5:1,
large text and meaningful non-text controls at least 3:1. Verify dynamic colors,
light/dark and selected/disabled/error states. Maintain 48dp minimum touch targets,
logical TalkBack order, labels and selected/busy state announcements. Decorative
icons do not duplicate adjacent spoken text.

Check font scales 1.0/1.3/2.0, compact width around 320dp, the Pixel 8 Pro phone
layout and wider windows. Primary/critical labels must not be ellipsized because
a wrapper imposes a 56dp height or one-line limit. Use content-driven height,
native minimum sizes and adaptive stacking. Retain predictable Android back,
keyboard/focus, system insets and RTL handling. Verify animation scale disabled
and reduced-motion behavior; official components do not remove the need to test it.

## Existing wrapper migration policy

The current Expressive* helpers often wrap actual native components. Audit what
each helper changes instead of treating its name as proof of compliance or of a
custom control. Do not remove necessary domain state/semantics merely to inline it.

| Existing pattern | Planned disposition |
| --- | --- |
| ExpressivePrimaryBar fixed 56dp height/one-line ellipsis | Replace with direct native expressive Button defaults and adaptive text/layout |
| ExpressiveSwitch handwritten color/alpha overrides | Prefer direct Switch/SwitchDefaults unless a measured semantic need justifies an override |
| ExpressiveDialog generic shape wrapper | Prefer AlertDialog/ModalBottomSheet defaults; retain domain confirmation content only |
| ExpressiveScaffold | Keep only if it supplies necessary shared insets/state without changing native appearance/behavior |
| ExpressiveHeroCard/TonalCard | Compose native Card plus domain content; review forced extra-large shape/icon/spacing per use |
| Custom navigation/pager motion | Prefer native navigation/adaptive layouts; retain gesture requirements only with accessibility evidence |
| Search/menu helpers | Use official APIs/position providers; remove duplicate interaction/shape systems |

All interactive controls should originate from official Material 3 components
unless an exception is documented with a functional reason, API availability,
semantics and tests. Domain layouts, metadata formatting and state models are
legitimate application code; they do not require a second visual component kit.

## Design and implementation sequence

The user resumed Manager and independent-loader implementation. D0 is complete;
D1-D4 native migration and application code compile. Standalone preview catalog
and physical accessibility/performance assessment remain incomplete. See the
[execution record](../plans/2026-10-13-implementation-status.md) and
[PC handoff](../plans/2026-10-13-pc-codex-handoff.md).

| Step | Deliverable | Gate |
| --- | --- | --- |
| D0 | This official-native direction, component map and page/state definitions | Supersedes conflicting visual plans; distinguishes proposals from available APIs |
| D1 | Native component catalog and Compose previews for theme, actions, cards, rows, choices and progress | Pinned-version APIs compile; light/dark/large-font/reduced-motion states assessed |
| D2 | Status and installed-module reference screens | Focal hierarchy visible; native interactions and full information remain usable |
| D3 | Discover/details/authorization/settings and operation references | Same tokens/components; real state/permission/cancellation semantics |
| D4 | Wrapper migration and accessibility/performance validation | Native defaults preserved; exceptions justified; component-specific build/checks pass |

Implementation preserves the pinned Material 3 dependency and uses its checked
native APIs. Manager release build and debug JVM tests pass. No physical device
or privileged load was exercised. Visual acceptance remains with PC Codex.
