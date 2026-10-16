# Superuser read-block diagnostics and refresh correction

Trigger: screenshot 2457 shows an unconfirmed blue overview plus a large generic
red read-error card. The user reports Home looks normal, but Manager authorization
is not confirmed. The screenshot alone does not establish the device-side cause.

Code findings and changes:
- JNI get_info previously cached the first successful result, including a mutable
  Manager-authorization flag. It now reads fresh information and clears the
  failed-modern-probe buffer before the existing legacy fallback.
- Read prerequisites distinguish missing driver interface, absent Manager
  authorization and mismatched/unknown UAPI. Root-shell, connection-timeout and
  service-query failures have separate reasons. Gates remain enforced.
- Typed failures retain observed driver version, authorization flag and both UAPI
  versions. Expandable, selectable and locally copyable diagnostics expose the
  precise condition and app identity; no report is transmitted externally.
- A first-load failure shows one native surface card with a concise reason,
  Home/check-again actions and diagnostic expansion. No empty summary or filtering
  controls precede it. An expected unavailable state uses neutral colors; actual
  errors use a localized error icon treatment. Old snapshots stay labelled.
- Native app search is hidden when there is no queryable/cached list. Other pages
  retain search by default.
- Entering/resuming the page rechecks current conditions. There is no hidden-tab
  polling or repeated automatic privileged write.
- The Root service no longer converts a package-enumeration exception into an
  apparently successful empty list; a raw empty package response also fails.
- Binder null handling and timeout reporting are explicit. Root service bootstrap
  checks its shell when a new launch is required. An existing service can
  still be bound when the cached shell is non-root.

Validation:
- Clean release build compiles JNI for arm64-v8a and x86_64.
- Existing authorization regressions plus three new cases cover prerequisite
  priority, unknown/mismatched interfaces, and diagnostic values/service failures.
- Final local results and artifact hash are in the APK manifest and build log.

Device acceptance (PC, open):
1. Install the access-fix APK with the same test signer. Compare behavior with
   screenshot 2457. If blocked, expand/copy diagnostics before altering the device.
2. driver_version <= 0: inspect interface/activation availability; do not infer
   an LKM installation failure solely from the Manager process probe.
3. manager_authorized=false with a positive driver version: inspect the installed
   Manager/package/signature against the trusted Manager expected by the driver.
   Ordinary Root access is not equivalent to KernelSU Manager authorization.
4. Mismatched/unknown UAPI remains blocked; use a matching verified build. Do not
   bypass signature, authorization, UAPI, ABI or CFI checks.
5. Restore authorization in a controlled setup; retry, change tabs and resume the
   app. Confirm that a first unauthorized probe no longer pins the flag forever.
6. RootService timeout or enumeration error must not become a zero-grant result;
   restore the service and retry. Verify reuse of an existing bound service.
   If an earlier process-wide shell fell back to
   non-root, force-stop/reopen the Manager after restoring Root as needed.
7. Re-run grant/revoke, shared UID and Save/discard from the existing Superuser
   acceptance checklist. Confirm native flags refresh without stale successes.
8. Light/dark, narrow display, 200% font and TalkBack: one readable blocked card,
   useful fixed app bar, reachable actions and selectable diagnostic details.

Limitations: device cause remains unconfirmed until the fresh diagnostic report
or PC testing is available. The older per-profile JNI getter still conflates a
missing profile with some individual read errors; no claim is made that this
separate native contract limitation has been resolved.

Final local verification: clean assembleRelease testDebugUnitTest succeeded.
40 JVM tests: 39 passed, one existing fixture skipped. JNI rebuilt for both ABIs.
Artifact: HuskySU-superuser-access-fix.apk, 9366735 bytes.
SHA-256: 7aa176ea5c924cb85a149cccbd40596da26c65be7870aac4c7fe3bef78d1bba3
APK v2 signature matches the previous Android Debug test signer.
Device-specific cause and physical acceptance remain open; no commit or push.
