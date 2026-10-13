# Manager Expressive review

User direction: audit Material 3 Expressive, prefer native components; GKI is
not a mandatory eligibility label. Preserve exact target/module validation.

## Implementation plan

- [x] Remove kernel-version-based GKI gating from home/install/environment UI;
  report absent/unrecognized KernelSU without claiming unsupported hardware.
- [x] Use native Expressive theme defaults, paired color roles, accessible native
  toggle semantics and labelled search actions; retain native navigation/sheets.
- [x] Check changed behavior with existing tests and required Manager build.
- [x] Record findings, validation outcomes and physical visual-review limits.
- [x] Restore unreachable Rust dependency sources with unchanged commit pins;
  complete the full CI chain before producing the APK, as selected by the user.

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

## Rust build-chain repair

First PR CI run 38460711816 failed before Manager compilation: the fixed
Kernel-SU/adb_client source was unreachable. All four Kernel-SU dependency
repositories return 404 through authenticated API reads. User chose to repair
the full build chain rather than use an old daemon merely for UI compilation.

Recovered the exact existing commit objects from public repositories:

| Dependency | Reachable source | Unchanged commit |
| --- | --- | --- |
| adb_client | 5ec1cff/adb_client | d97a966435bebaa55017834869dec08150826aa7 |
| java-properties | MySU-org/java-properties | 42a4aa941b70ded2dd3be9e9f892471023e70229 |
| prop-rs / prop-rs-android | MySU-org/ksu_props | 6f5723105d8d4cacad31d83d343defbf032c7b33 |
| ksuinit rustix | MySU-org/rustix | 4a53fbc7cb7a07cabe87125cc21dbc27db316259 |

Only source URLs and explicit full rev selectors change in manifests/lockfile;
crate versions, checksums, features and dependency edges are preserved. Source
commit objects were found by exact SHA, not selected from a moving default branch.
CI Rust builds now use --locked, ARM64 checks run cargo ndk check, clippy and fmt
in sequence, and loader/contract host regressions run before publishing artifacts.

Local cargo ndk check/clippy were attempted but cargo-ndk and NDK are absent;
cargo fmt succeeded outside the sandbox after access denial inside it. Android
compilation/lint evidence must come from the full CI run, not plain host checks.

## Final validation at 1617d547

PR: https://github.com/heDuke/KernelSU/pull/1 (draft; not merged).
Full pipeline: https://github.com/heDuke/KernelSU/actions/runs/38462306548.
Build Manager, Clippy and Rustfmt workflows all completed successfully.
ARM64 cargo ndk compilation/Clippy/format and all ksud targets passed.
Manager clean assembleRelease + testDebugUnitTest passed: 13 tests passed,
1 real-image-fixture test skipped. Loader/contracts: 13 passed, 1 real-candidate
fixture test ignored. These fixture omissions are retained as limits, not passes.

Final manager artifact ID 11847806738, ZIP SHA-256
f58f1e3b9cf5359cf86d475889ec1958c3e41ea0a7a80155688290a0d0085393.
Extracted APK SHA-256
48102d723e743310c1df0ecee44714b8f14652aeda6053d1b204317a69e4ad70.
Saved locally under the device evidence directory with PR in the file name.
The PR package/temporary signer is not trusted by the production Canary LKM.
No APK was installed and no further boot operation was performed in this review.
Physical visual/font/TalkBack/rotation/reduced-motion validation remains pending.

Context7 is unavailable in this session. Primary references inspected:
https://developer.android.com/develop/ui/compose/designsystems/material3
https://developer.android.com/develop/ui/compose/accessibility/api-defaults
https://developer.android.com/jetpack/androidx/releases/compose-material3
https://m3.material.io/blog/building-with-m3-expressive (JS-only response).
Keep Material 3 1.5.0-alpha26 pinned. Native components already in use include
LargeFlexibleTopAppBar, ShortNavigationBar, WideNavigationRail, SearchBar,
SegmentedListItem, Switch, OutlinedTextField, AlertDialog and ModalBottomSheet.
