# Superuser authorization design and implementation

Approved direction: Material 3 Expressive, native controls, compact overview,
UID-aware application list, application Bottom Sheet and an explicit-save profile
editor. Default to Granted and persist the last selected scope.

- [x] Keep All / Granted / Custom scopes independent from sort and user/system filters.
      Search uses the full app list within user/system filters, with a visible scope label.
- [x] Count authorization groups separately from packages. Preserve every shared-UID
      member when filtering, searching and confirming an authorization change.
- [x] Native list rows and grant switches; confirm every enable with identity and
      affected packages. Revoke directly, disable concurrent writes, re-read results.
- [x] Application sheet: identity, real authorization/configuration, version, UID,
      user, shared members, module-unmount policy and full-profile action.
- [x] Preserve existing root/non-root profile fields when toggling authorization.
      Reject a stale identity/UID or changed authorization before writing.
- [x] Full profile editor stages changes and offers Save / discard; confirmation
      for enabling root includes every affected application.
- [x] Distinct loading, read failure, filtered-empty and no-app states. Never turn
      a failed query into an apparently successful empty authorization list.
- [x] Build release and run relevant policy regression checks; document physical
      tests for PC Codex. No kernel, daemon or experimental-loader changes.

Colors follow the approved dynamic-color roles: primaryContainer for overview,
surfaceContainer for rows, neutral surface for ungranted/unknown, errorContainer
only for real errors. State always has text and controls, not color alone.

Physical acceptance: root grant/revoke/readback and failures; shared UID and
secondary user; save/discard; narrow screen, 200% font, TalkBack; light/dark and
multiple dynamic palettes. These checks require a device and remain open.

## Implementation notes

- Revalidated baseline: main and origin/main both c6b49268. Earlier uncommitted
  home/sheet work is preserved.
- Authorization changes compare a complete reviewed package/UID/install/version
  snapshot against a fresh RootService query, serialize writes and verify the
  active native profile branch after writing. Read failures stay visible.
- Shared-UID resource-only siblings are included whenever that UID has a code
  package. User/system filters operate on groups rather than removing members.
- JNI returns a default profile for a missing entry or a failed individual get.
  Manager authorization, driver/UAPI checks and write/readback checks reduce false
  success, but distinguishing every native read error needs a future JNI contract.
- Template selection only stages fields/rules. Applying SELinux rules is a live
  global effect and cannot be transactionally undone; partial failure is reported
  explicitly, even after best-effort restoration of the persisted rules file.
- No new one-time grants, expiry policies or fabricated request timestamps.

## Validation

Final clean assembleRelease + testDebugUnitTest passed (1m 51s). Five new policy
regressions passed; aggregate 37 tests: 36 passed, one existing fixture skipped.
APK, signer, digests, full source patch and logs are in
/workspace/artifacts/HuskySU-superuser-expressive-PC-handoff.zip.
Device acceptance remains open; see 2026-10-16-superuser-acceptance.md.

## Follow-up: read-block screenshot 2457

See [access refresh and diagnostics correction](2026-10-16-superuser-access-fix.md).
The device-side cause remains pending a fresh diagnostic report or PC testing.
