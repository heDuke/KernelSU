# Production publication of the Expressive Manager update

User explicitly requested a production release after the Superuser access-fix APK.
Publish all completed local Manager/home/recovery changes using the existing
repository-managed production signing workflow. Keep existing support boundaries.

- [x] Fetch main/tags and compare source baseline (c6b49268, no remote divergence).
- [x] Locate the existing Husky LKM Release workflow and trusted production signer.
      The latest prior production release is husky-v32640. Local files have no
      production keystore. Secret listing is unavailable to the current integration;
      the workflow itself validates the required secrets without exporting them.
- [x] Commit completed userspace, Manager and release documentation changes.
- [x] Push main and dispatch the manual production workflow for the exact commit.
- [x] Verify all workflow jobs, production APK identity/certificate, manifest and
      published asset checksums. Download a local copy of the official APK.

Use a final [skip ci] commit marker on the main push to avoid the separate automatic
Manager workflow and its Telegram upload. The explicitly dispatched production
workflow still runs all its build/verification jobs. No messaging is requested.

Expected production package: me.weishu.kernelsu.husky.fork.
Trusted signer SHA-256:
b37ef4db671d6d1dd44d1b42efdd63effece3265649734a9d665ff5939c59553.

Local source validation already passed: clean Manager release build, both JNI
architectures, 40 JVM tests (39 passed and one existing fixture skipped). Earlier
userspace changes passed Android cargo check/Clippy/format and native builds; the
production workflow reruns Android checks with its own signed payloads. New release
identity verification tests run before submission.

Physical acceptance remains with PC Codex. The cause of the reported read block
is not yet confirmed by a fresh device diagnostic. Generic LKM assets are not the
exact pinned ZP11.260918.007 Canary candidate. Independent loading remains gated.
The test signer differs from production; replacing a test-signed installation
requires uninstalling it and clears the Manager-local settings.

## Published and independently verified (2026-10-17)

Release: https://github.com/heDuke/KernelSU/releases/tag/husky-v32643
Source: 01403fdaa02db67521021466cc09d1cb1bfe79cd.
Workflow: https://github.com/heDuke/KernelSU/actions/runs/38891094119 (all eight jobs successful).
Package/version: me.weishu.kernelsu.husky.fork / 32643.
Production signer matches the trusted b37ef4db... certificate above.
Downloaded APK SHA-256:
dc916fd84ae3e5073844df17e3ff52c7d53432217c91a9e7bca783d4bb524334.

All published checksum entries passed. The downloaded APK independently passed
Android apksig identity/signature validation; the regenerated release manifest
matches the published file byte-for-byte. Published assets are retained under
/workspace/artifacts/HuskySU-production-32643. Detailed CI test-report download
was blocked by the artifact storage endpoint; individual remote test counts are
not asserted here. Local source tests were 39 passed / one existing fixture skipped.

Repository Actions disablement was resolved by the user. The first attempted
publication was blocked; the run above is the completed publication. Later
Superuser layout refinements remain separate local work beyond this source.
