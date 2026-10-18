# Native app bar and list scrolling repair

The 2738/2739/2740/2741 device screenshots report stuck lists and inconsistent
headers after production v32768. This is separate from the native Activity title
bar: retain the splash-to-NoActionBar repair from b418c228.

## Cause and scope

The shared custom ExpressivePageHeader introduced in 25d4ac96 replaced the native
app bar with a plain row but its callers retained exitUntilCollapsedScrollBehavior
and nested-scroll connections. The custom row did not measure the collapse range
or accept the behavior. With the default negative-Float.MAX_VALUE height limit,
the orphaned connection could keep consuming upward gestures before the list.
The custom row also placed action-bearing titles at 4dp versus 16dp for titles
without actions and changed the native title typography.

## Repair

- Replace that row with the official Material 3 LargeFlexibleTopAppBar, preserving
  colors, safe insets, navigation/actions and native expanded/collapsed typography.
- Forward the same TopAppBarScrollBehavior used by each content connection into
  the measuring app bar. SearchAppBar covers Superuser, modules and Root logs;
  wire settings, repositories/detail, install, profiles/templates/editor, scripts,
  environment diagnostics and About directly as well.
- Check both forwarding layers: the screen must pass its behavior to its private
  TopBar, and that wrapper must pass it to ExpressivePageHeader. Install, profiles,
  scripts and both template pages had omissions at both layers. Make those private
  wrapper parameters required; require an explicit behavior choice in the shared
  header and SearchAppBar (explicit null only for the static preferences header).
- Leave the standalone experimental preferences page without a scroll connection;
  its native header is static. No privilege, kernel or module-write gate changes.

## Verification and delivery

Local clean assembleRelease passed for the production fork package. All 197 JVM
tests passed with no failures or skips, using the real Canary images and UAPI-6
candidate fixtures. Production packaging follows. No emulator/device gesture or screenshot
instrumentation is available in this environment; report that limit explicitly.

A standalone JVM reproduction used the actual resolved Material 3 dependency's
ExitUntilCollapsedScrollBehavior and nested-scroll connection. With its unmeasured
default range, all 40 upward deltas were consumed before the list. After supplying
a finite collapse range and completing collapse, all 40 subsequent deltas were
left for the list. This isolates the orphaned-connection failure; it does not
replace an Android layout/gesture test. Probe source/output are retained in the
workspace artifacts as HuskyScrollProbe.java / husky-scroll-behavior-probe.log.

The initial production run 39087473410 at 8a3c676e was cancelled after the outer
wrapper audit found five still-disconnected secondary pages. Do not deliver that
intermediate commit as the complete repair. The follow-up containing both layers
passed a combined clean assembleRelease / testDebugUnitTest run (197 tests, zero
failures/errors/skips), with the production package and real Canary fixtures.
