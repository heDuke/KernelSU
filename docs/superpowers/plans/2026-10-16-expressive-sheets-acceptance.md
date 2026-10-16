# Expressive sheets implementation and PC acceptance

Date: 2026-10-16. Source base: c6b49268 plus the delivered working-tree snapshot.
The snapshot includes the earlier dashboard prototype and its review corrections.
Changes are not committed or pushed by this implementation task.

## Implemented scope

- Native sheet structure with a fixed header/close/report menu, independently
  scrolling content and relevant fixed primary action; panel identity resets
  scroll state. Native expansion, insets, type and color roles are preserved.
- Status/value focal cards and grouped copyable facts for activation, access,
  runtime, incidents, versions, modules, device, compatibility and advanced use.
  Technical details expand separately; important operation risks stay visible.
- Summary-first information and paired dynamic/brand light/dark roles. Unknown
  activation is neutral and cannot be inferred from release lookup failure.
  Observed interface compatibility is distinct from online/environment findings
  and from unverified input/load/boot/recovery stages.
- Todo completeness, authorization entries, update scope and result feedback,
  operation-specific prerequisite messages and persistent pending-reboot records.
  Pending records require a confirmed boot-identity change to clear; switching
  identity sources does not itself prove a reboot.
- In-place card selection, native selected semantics, add-card feedback with a
  locate action, compact editor controls and explicit remove-from-overview text.
  Quick-action edits use a local draft: apply to an existing home draft, or save
  only quick actions when opened independently. Closing discards local edits.
- Installation preflight uses a cancellable native busy dialog. Choices remain
  frozen during inspection/review; staging cleanup also covers cancellation on
  dispatch back to the UI. Final review presents an operation/target/file/backup
  summary, technical details, visible risks and an accessible execution button.
- Bulk module actions review actual targets and skipped IDs. Execution rejects
  a changed review and retains version-bound restoration and interruption records.

## Recovery inspection and native payload

New read-only CLI: `ksud boot-info recovery-check`. It uses the same current-image
selection and recovery marker as production restore, checks that the current
image is KernelSU patched, finds the exact stock backup in the backend directory,
checks file type/size and SHA-1, and emits a bounded report including the current
image SHA-256. It does not patch, create output, load modules or flash partitions.
Legacy app-private backups remain exportable but do not establish readiness.

The new reviewed restore operation passes `--expected-image-sha256` to ksud.
The production restore path validates that digest under its mutation lock before
parsing/restoring/writing. Existing legacy restore/uninstall entry points retain
their behavior. This adds no kernel API or independent-loader execution path.

Both arm64-v8a and x86_64 ksud payloads must be rebuilt and copied before building
Manager. Bundled static ksuinit assets are unchanged from the prior refreshed
production payload. Do not rebuild only the Kotlin layer with an older daemon.

## Local validation

See the delivered manifest and logs for the final build outcome and exact hashes.
Validation includes Android ARM64 cargo-ndk check, Clippy with warnings denied,
formatting, the host changed-image recovery regression, both release daemon
builds, Manager clean Release assembly and available debug JVM tests. JVM
coverage includes pending reboot identity changes, unknown/authorization states,
prerequisite ordering, build-counter/UAPI separation and batch recovery selection.
The actual Canary LKM fixture is supplied to the existing metadata test.

Compose design samples cover healthy light/dark activation, unauthorized state,
200-percent fonts, checking, version lookup HTTP 403 and device information.
They are source previews; compiling them does not certify rendered appearance.
No device Root, module load, flash, boot or recovery was executed in this task.

## PC visual and state acceptance

1. Use the source snapshot/manifest, inspect the local diff, and rebuild both ksud
   architectures before Manager when creating a trusted production-signed APK.
   Preserve the existing trusted Manager. The provided APK has the previous test
   Android Debug certificate; it is not a production-authorized Manager signer.
2. Check light/dark, multiple wallpaper palettes and Husky brand colors. Verify
   paired foreground roles, 4.5:1 ordinary text and 3:1 large text/meaningful
   non-text contrast. Status must remain understandable without color.
3. Exercise every sheet in portrait, landscape, narrow/wide windows and 200%
   fonts. Confirm close/primary actions remain reachable, content scrolls and
   long values are complete/copyable. Check actual row/intrinsic measurement.
4. Open activation when unauthorized, while probing, and with a known fault.
   A 403 must stay a local update error; it must not establish absent activation.
   Verify absolute observation times and the separation of observed runtime from
   system uptime, including background gaps and restart.
5. Switch versions -> advanced -> backups. Each sheet starts at its beginning;
   no stale header or covered navigation destination should remain.
6. Add, locate, resize, reorder and remove cards. Confirm the selection announced
   by TalkBack matches the fixed editor controls. Repeated additions must not
   force unnecessary jumps out of the library. Compatibility cannot be removed.
7. Test cancel/back/save/reset, rotation and process restoration of home drafts.
   Quick-action Close cancels its local draft; applying to a home draft must not
   commit card changes, and standalone save must change only quick actions.
8. Check unloaded, partial, failed and genuinely empty todo states. Toggle a
   module, restart the app and verify the reboot reminder persists. Confirm it
   clears only after a known reboot. Unknown boot identity must not clear it.
9. Exercise checking/latest/failure/no-target update results in place. Distinguish
   release checks from module update checks and keep Manager-only updates usable.

## PC privileged-operation acceptance

Use the existing device-specific recovery preparation before any real writes.

1. Review disable/restore targets, including a metamodule, removed/changed IDs,
   already disabled modules and an interrupted previous batch. Change a target
   after preview; execution must reject the stale review. Verify real mutations
   record pending reboot and restore does not enable unrecorded modules.
2. Cancel input inspection early and during file transfer; inspect private cache
   cleanup. Check malformed/oversized files, changed/deleted SAF inputs, custom
   output names, slot/target changes and unavailable Root. While inspecting,
   controls must not silently change the reviewed operation.
3. Exercise both check-only entry points; no boot patch, loader call or partition
   write may occur. Passing inspection must not imply signature trust, ABI/CFI
   compatibility or successful device loading.
4. Probe recovery with no marker, only unrelated backups, only app-private legacy
   backups, a missing exact backup, wrong digest, symlink or oversized backup.
   The new restore button remains disabled; readable files may still export.
5. With an exact valid backup, verify the displayed target/digest/size and image
   hash. Change the current image after review; the backend must reject the
   reviewed restore before writing. Test actual restore only after the previous
   cases and recovery preparation are confirmed.

Independent loading/boot gates remain closed. Canary support and ABI/CFI claims
require their separate matching-device evidence; UI and host tests do not promote
those claims. Physical contrast, touch, Root and recovery acceptance remains open.
