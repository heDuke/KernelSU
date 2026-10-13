# Manager Expressive review

User direction: audit Material 3 Expressive, prefer native components; GKI is
not a mandatory eligibility label. Preserve exact target/module validation.

## Implementation plan

- [x] Remove kernel-version-based GKI gating from home/install/environment UI;
  report absent/unrecognized KernelSU without claiming unsupported hardware.
- [x] Use native Expressive theme defaults, paired color roles, accessible native
  toggle semantics and labelled search actions; retain native navigation/sheets.
- [ ] Check changed behavior with focused tests and required Manager build.
- [ ] Record findings, validation outcomes and physical visual-review limits.

## Findings and validation

Corrected: non-GKI version heuristic incorrectly produced an unsupported hero,
failed environment verdict and hidden direct install; native working mode was
suppressed. Missing KernelSU/Manager access now remains unknown; Built-in and
LKM describe actual mode. Direct install availability follows root availability;
the patcher's exact image/module contracts still decide compatibility.

Corrected: Switch rows lacked switch semantics; preset rows had independent
row and radio click actions. Native toggleable/selectable roles now provide one
logical action per row; radio groups expose group semantics. Search close/clear
actions have localized descriptions. Native Expressive typography defaults
replace the theme override; theme colors no longer animate through arbitrary
global spring values. The update card uses its matching on-container color.
Module preview identity text uses remaining width and wraps at larger fonts.

Existing native scaffold/app bars, navigation, search, dialogs, segmented controls,
text fields and ModalBottomSheet are retained; no replacement control library,
dependency upgrade, public API, IOCTL, kernel/loader code or policy bypass added.

Local checks: git diff --check passes; edited English/Chinese resources parse
and have no duplicate string names. Manager Gradle 9.7.0 downloaded successfully,
but clean assembleRelease testDebugUnitTest stopped during configuration because
Android SDK is absent. No local compilation/test success is claimed.
User requested GitHub Actions. Existing Build Manager now waits for ksud and
places both JNI binaries before Gradle, runs existing unit tests, and uploads
test reports. PR workflow builds use a separate test package/signer and do not
prove production Manager authorization on the installed Canary candidate.

Visual limits: captured installed-app frame was partly covered by a user video
overlay and does not represent the modified build. No full visual/font/TalkBack/
rotation/reduced-motion acceptance is claimed. Device evidence stays local.

Context7 is unavailable in this session. Primary references inspected:
https://developer.android.com/develop/ui/compose/designsystems/material3
https://developer.android.com/develop/ui/compose/accessibility/api-defaults
https://developer.android.com/jetpack/androidx/releases/compose-material3
https://m3.material.io/blog/building-with-m3-expressive (JS-only response).
Keep Material 3 1.5.0-alpha26 pinned. Native components already in use include
LargeFlexibleTopAppBar, ShortNavigationBar, WideNavigationRail, SearchBar,
SegmentedListItem, Switch, OutlinedTextField, AlertDialog and ModalBottomSheet.
