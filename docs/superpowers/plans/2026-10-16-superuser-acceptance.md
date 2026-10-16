# Superuser page acceptance and PC handoff

Base: main c6b49268. Contains prior uncommitted home/sheet work. No commit or push
is implied by this design implementation.

Implemented:
- Native Material 3 list, compact dynamic-color overview, Granted/All/Custom
  FilterChips; last scope persisted. Sorting and user/system filters retained.
- Search covers all applications within the user/system scope, even in Granted.
- Independent row navigation and grant switches; enable requires identity/range
  confirmation; revoke applies immediately, then re-reads the active policy.
- UID-group filtering preserves all sibling packages, including resource-only
  packages alongside code packages. Counts distinguish groups and applications.
- Native application sheet with real identity, current grant/configuration,
  effective unmount behavior, shared members and a full-profile action.
- Full editor stages changes and templates, offers Save/discard, confirms new
  grants, rejects changed identities/profiles, serializes and checks writes.
- RootService connection timeout, cancellation cleanup and shell argument quoting
  for policy writes. No kernel, daemon, loader, expiry or temporary-grant changes.

Local validation:
- New SuperUserPolicyTest: five tests passed (shared membership, reinstall/update/
  UID reuse, restricted UID ranges, corrupt preference fallback, active-policy
  union readback). Full suite: 37 tests, 36 passed, one existing fixture skipped.
- Release build and final artifact recorded alongside the APK manifest.

PC physical acceptance (open):
1. Install test-signed APK with the existing trusted test signer where allowed.
   Verify activation/Manager authorization before testing grants.
2. Fresh preferences default to Granted; All/Custom and sort/user/system settings
   persist. Find a previously ungranted app using Search while Granted is selected.
3. Cancel grant confirmation: no grant occurs. Confirm: native state is read back.
   Revoke directly, refresh, reopen the page and verify real authorization.
4. Shared UID: confirmation and sheet list every sibling. Switching affects the
   whole group. An app update/removal/changed member invalidates the old review.
5. Work/secondary user: exact UID/user is visible. User/system filters preserve
   group membership; restricted system app IDs stay restricted for every user.
6. Profile: editing switches, custom fields or templates does not write before
   Save. Back offers discard. Save applies/checks. Test rotation/navigation while
   a draft exists, and a rapid second save after success.
7. Force RootService/driver/read or set failure: no false empty/success, state
   remains labelled unconfirmed or old snapshot; retry has a clear result.
8. SELinux updates have live global effects. They are not a transactional rollback:
   partial failure is reported explicitly; inspect state/reboot as appropriate.
9. TalkBack: application identity, independent switch name/state and fixed close/
   full-profile actions; narrow layout and 200% font must scroll without cropping.
10. Light/dark, dynamic palettes and Husky seed: verify role contrast with device
    captures (4.5:1 ordinary text; 3:1 large text and meaningful non-text elements).

Native limitation: the existing JNI get method uses a default profile both for
missing profiles and individual get errors. Availability/UAPI checks and write
readback are implemented; finer error differentiation requires a JNI contract
change and is not represented as verified in this build.

Final local verification: clean assembleRelease testDebugUnitTest succeeded in
1m 51s. APK is HuskySU-superuser-expressive-preview.apk (9,353,167 bytes), signed
with the existing Android Debug test certificate, not a production release key.
Both bundled ksud payloads match the previously verified home-sheet build.
The handoff archive also includes the earlier home-sheet physical checklist.

## Follow-up: read-block screenshot 2457

The newer access-fix APK includes refreshed JNI flags and precise diagnostics.
See [follow-up PC checks](2026-10-16-superuser-access-fix.md) before the rest of
the grant/revoke checklist. The earlier 37-test result above is historical; the
access-fix suite has 40 tests (39 passed, one existing fixture skipped).
