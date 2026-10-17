# Superuser layout refinement

User requested continued Superuser design optimization after production publication.
Baseline: main 01403fda / production husky-v32643. Material 3 Expressive and native
components remain the approved direction.

- [x] Verify the published 32643 assets/signature and retain the exact release source.
- [x] Replace button-like overview statistics with clearly labelled native metric
      cards: authorization groups and affected applications stay distinct. Stack
      cards on narrow layouts/large font, and label cached/unconfirmed data.
- [x] Separate application name, package, authorization and shared/custom/user
      metadata. Native switches remain independent targets. Move the control below
      the identity at narrow widths/large fonts. Full package names remain in sheets.
- [x] Merge repeated grant summary and switch in the application-sheet identity
      card. Keep shared-UID scope visible and save/unknown feedback explicit.
- [x] Group profile/mount policy and app identity. Preview three shared members with
      an explicit expand-all action; the grant confirmation always lists all members.
- [x] Add native Compose previews for ordinary/shared/long-name/stale-data states,
      narrow layouts, dark mode and 200% text. Do not claim rendered/device approval
      from compilation alone.
- [x] Build release and execute appropriate existing regressions. Keep physical
      visual/Root acceptance with PC Codex.

Scope: presentation and information hierarchy. Existing identity validation,
confirmation, explicit-save behavior, readback and authorization/UAPI gates retain
consistent semantics. No new fabricated request time or unsupported grant policy.

## Local verification

clean assembleRelease testDebugUnitTest succeeded. All 40 JVM tests ran with
39 passed and one existing fixture skipped; no failures/errors. Native Compose
preview variants compile. Actual preview rendering, device contrast, TalkBack,
Root writes and 200% text acceptance are not claimed and remain with PC Codex.

The 32643 production source/signature/download was independently verified. The
layout changes in this plan are a later revision and are not contained in that
published APK. Source publication follows successful local verification; any
local APK uses the existing test signer and is not a production-signed update.
