# Home dashboard implementation

Confirmed 2026-10-15. Baseline: main 7f68a85a (all remote branches consolidated). Inware is a grouping/readability
reference, not an implementation or exact visual template. Keep native Material
3 Expressive controls and theme. User delegates device testing to PC Codex.

Confirmed content: status/permissions/mode, observed runtime and incident record,
todos, editable 4-6 quick actions, versions and install, module overview, existing
system information and an always-present compatibility card. Compact/standard/
expanded sizes are configurable per card in native sheets, with preview,
explicit save/cancel/reset and persistence. Compact cards adapt to full width
on narrow screens/large fonts. No fixed text heights or truncated core values.

Todos distinguish intentional disablement, safe mode, actual pending changes,
updates and observed errors. Do not infer a boot loop from disabled modules.
Runtime uses monotonic observations, boot identity and bounded deduplicated
incidents. Pauses/gaps end the observed normal interval. System uptime is
separate. No historical zero, background surveillance or root-uptime claim.

Advanced native sheets lead to existing image/LKM/OTA/backup workflows and
independent-loader preferences. Add bounded local input inspection, SHA-256,
recognized headers/metadata, target/config summary, copy/export and explicit
execution confirmation. Checking never writes partitions or invokes a loader.
File hash is not publisher trust; missing ABI/CFI/image evidence stays unknown.
Private image staging must prevent provider mutation between review and use.
The independent loader's immutable load/boot gate remains closed.

Validation: pure layout/incident/input tests; required clean assembleRelease and
available debug JVM tests; APK signature/hash. Physical visual, font, lifecycle,
root, notifications, actual flash/load/boot and recovery remain on PC.

## Implementation and acceptance

Implemented: ten native cards, user-selected compact/standard/expanded content,
responsive spans, live editor preview and explicit persisted save/cancel/reset,
one-to-six ordered actions (four by default), status/deferred work/module counts,
full existing device fields, always-present compatibility, native detail sheets,
and light/dark/200-percent-font Compose previews. Inware artwork/source is not
included. Foreground observations are boot-aware, gap-aware and deduplicated;
history is bounded to 40 events and counts saturate without overflow. No claim of
continuous background kernel monitoring is made.

Native module mutations share a process-wide gate. Bulk restoration only changes
recorded module IDs with the same version; failed/interrupted batches retain
records for manual review. The advanced UI exposes existing production image,
LKM, OTA, verified backup export/restore and independent preference workflows.
Online LKM/OTA entry points now lead to installation review. No new kernel,
userspace contract or independent-loader loading code was changed.

Read-only inspection has a separate route which cannot enable execution. Files
are bounded, staged privately, hashed, inspected and rehashed before execution;
boot-image alignment is never presented as kernel VM page size. Direct LKM
metadata conflicts block confirmation; unknown authenticity/private ABI/CFI
remains unknown. Local IMG defaults to output, with an optional native document picker. An explicit
patch-and-write choice additionally requires an identifiable ARM64 kernel target,
a matching reviewed partition and renewed Root checks; unknown companion/init_boot
targets remain inspection/output only. The
existing backend still performs its own target/backup/slot checks. Restoring
uses the backup linked to the actual boot image, not an arbitrary selected file.
GKI online updates remain unavailable without a verified GKI update source;
existing LKM releases are labeled honestly. The experimental load gate stays shut.

Local JVM regressions cover preference corruption/order, fault episode deduping,
unknown boot identity, pauses/gaps/reboots, bounded history/counter overflow,
header alignment and the real Canary module including wrong-architecture cases.
Mandatory final clean Release build and tests are recorded with delivered
artifacts. JNI payloads include freshly built static production ksuinit, not the
stale payload from older local APKs. App-private preferences do not select boot
or disable verification. No physical flash/load/reboot was performed here.

## PC Codex follow-up

Use the delivered source commit/artifact manifest. Preserve the existing trusted
production Manager. A locally Debug-signed engineering APK does not replace the
production signer; use the existing production signing workflow for trusted root
acceptance. First compare portrait/landscape/wide layout, dark/light, long labels,
font scale 2.0 and TalkBack against the confirmed grouping and card density.
Test each card size, edit/save/cancel/defaults, rotation and app restart, shortcut
selection/order, ordinary/safe/denied/unknown states, and byte-accurate details.

Then test normal-interval pauses/reboots and deduped faults; failed module fetches
must remain unknown. Verify bulk enable/disable vs install/uninstall exclusion,
interruption records and restoration after version/removal changes. Confirm safe
mode is not labeled a proven reboot loop. Validate offline/403/NoMatchingLkm
without breaking Manager-only updates or labeling build counters incompatible.

For checks-only route, exercise both entry buttons: no boot-patch/load invocation
or partition write may occur. Verify changed/deleted SAF inputs, oversized or
malformed ELF/boot headers, target mismatches, cancel/staging cleanup and export
failures. Only after exact recovery preparation test actual production output,
custom destination, local/online/OTA review and verified backup restore. Snapshot
confirmation rechecks fingerprint, running release and the actual target slot;
exercise process/boot restoration and a changed slot before confirmation. Record
payload hashes, device identity, selected target and actual effects per case.
Independent loading, CFI/ABI proof and boot/recovery campaigns remain gated.
