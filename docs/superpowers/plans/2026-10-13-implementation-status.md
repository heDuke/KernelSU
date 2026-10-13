# HuskySU local implementation and validation

Date: 2026-10-13. Base: 8115aef9ff17e726083d3580bc8edffc219fc1ab.
User authorized Manager redesign, independent-loader resumption and earlier
hardening; subsequent physical-device tests are delegated to PC Codex.
The engineering bundle was prepared before Git submission and retains a source
patch against the base above. Use the repository's current main branch for the
subsequently committed source; do not reapply the patch to that updated checkout.

## Implemented locally

- Native Material 3 Expressive theme/actions, official dynamic color, native
  switches/fields/cards/list items, phone navigation and wide rail. Discover is
  reachable without root; existing installed-module actions remain available.
- Online preview and install confirmation use a shared native ModalBottomSheet:
  close/share, icon, screenshots, introduction, metadata and explicit details.
  Only module details use a full page within this module flow.
- Details include release selection/history, bounded README, changelog,
  source/version/size/compatibility/requirements/verification and support links.
  Unknown data stays unknown; repository verification is not a signature.
- Three source-bound JSON-v2 repositories, enable controls, category/source/sort
  filters, cache and per-source errors. Last inspected snapshots: 40 Googlers,
  117 Alternative, 141 Izzy modules; counts can change.
- Shared local/online/batch review freezes targets and requires consent. API
  mismatch needs acknowledgement; declared KernelSU incompatibility is blocked.
  Root/safe mode/Magisk, dependencies, space, ZIP CRC/identity and metamodule
  conflicts are checked before scripts. Installs run sequentially.
- Process-owned tasks continue through sheet dismissal/Activity recreation.
  Other previews/details remain usable. Actual bytes/speed/stages/logs shown;
  no fabricated install percentage. Provenance saved after online installation.
- Download filename/collision safety, cancellation cleanup, service-start failure,
  shared callbacks and active-job registration race addressed.
- Native experimental settings, strict import/export, app-private atomic save,
  generation checks, presets/overrides and draft preservation. Saving cannot
  select a boot loader.
- Independent 0.2.0 settings/store, structured diagnostics, public/private inventory,
  private-only mutation tests, pinned input profile and immutable load gate.
  Original read-only PC harness and host pilot are included.
- Earlier production ksuinit/auditor hardening is retained in the source patch.

## Validation

| Check | Result |
| --- | --- |
| Manager clean assembleRelease + testDebugUnitTest | Passed; 14 JVM tests, zero failures/errors; final build 2m 3s |
| Independent real-candidate Rust suite | 11 passed including otherwise ignored fixture |
| Independent host/Android check, clippy -D warnings, fmt | Passed |
| Independent host and Android ARM64 release builds | Passed |
| Settings export/import/generation and load-gate smoke | 7 commands matched expected exit/result; no load attempted |
| Canary auditor with actual vmlinux/Image/config/module | 4 tests passed without skips |
| Read-only device harness | Host dry-run and simulated rejection/cleanup passed; no device contacted |
| Host pilot | 90 checks across 3 presets, no validation differences; not device evidence |
| Production ksuinit | Earlier local host/Android checks/builds retained; unchanged in final Manager pass |

Evidence: [host pilot](2026-10-13-independent-loader-host-pilot.json),
[CLI smoke](2026-10-13-independent-loader-cli-smoke.json),
[probe dry-run](2026-10-13-independent-loader-probe-dry-run.json).
2026-10-12 loader evidence describes the older 0.1 prototype only.

## Remaining and boundaries

PC: visual/font/TalkBack/reduced-motion acceptance, real network/lifecycle and
notifications, privileged scripts/results, trusted Manager signing, SELinux/
capabilities/runtime checks, private ABI/CFI inventory, controlled load/root/
cold-boot/recovery and equivalent resource comparison. No physical device,
installer script or module-loading syscall was exercised here.

Ownership survives Activity recreation, not arbitrary process death. Durable
installer recovery, extra v1/Next adapters, address cache, attempt history,
independent early-init integration and signed-loading strategies remain future
milestones, not available controls. Standalone Compose preview catalog and
physical accessibility/performance assessment are also incomplete.

APK is Android Debug signed. Loader requires Android linker64 and rejects load
until a separately reviewed campaign build. Candidate is unsigned; file pins do
not establish runtime compatibility or publisher trust.
See [PC acceptance/recovery handoff](2026-10-13-pc-codex-handoff.md).
