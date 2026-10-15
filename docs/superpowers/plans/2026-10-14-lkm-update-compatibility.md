# Manager build difference and exact-target LKM updates

Device rechecked: husky, ZP11.260918.007, 6.1.172-android14-11-g3fc92ed75e44-ab16169542,
4096-byte pages. Production Manager/ksud 32639, UAPI 2. Running driver 32631,
UAPI 2, root UID 0 and loaded kernelsu remain healthy. Release 32639's 6.1 LKM
declares 6.1.166-dirty, so the existing exact-release boot guard rejects it.

- [x] Identify device, installed Manager, running driver and real module metadata.
- [x] Fix UI so a build-number difference is distinguished from UAPI/minimum
  driver incompatibility; retain truthful Manager and driver version details.
- [x] Add real ELF target metadata and require exact kernel release, KMI,
  page size and verified digest before offering an online LKM update. Missing
  or incompatible metadata must not disable independent Manager APK updates.
- [x] Add focused selection/metadata/version-policy regression tests. Complete
  the full production Android build/tests in GitHub Actions (no local SDK).
- [x] Publish and verify a production-signed Manager fix, install on the device
  using the already authorized APK installation scope, and verify the status.

Keep the working Canary module and kernel compatibility guard. No forced
vermagic, kernel signature bypass, automatic flash or new independent load.
The generic Release modules are not rebuilt as a Canary candidate merely to
equalize UI counters. A future kernel update needs exact target build evidence.

The kernel sources are unchanged from the handoff base to the current production
release (git diff --name-only 8115aef9..HEAD -- kernel is empty). The UI build
counter difference alone therefore does not justify replacing the working LKM.
Release metadata schema 2 records real module targets without changing any kernel,
userspace IOCTL or loader-result schema. Context7 is unavailable in this session;
the change uses existing repository APIs and pinned dependencies.
Local production identity/target tests passed with the actual released module:
4 passed, including rejection after mutating its vermagic only. Manager selection
and version-policy regression tests passed in the full production CI below.

## Result

Published and installed https://github.com/heDuke/KernelSU/releases/tag/husky-v32640,
source b70656bc05b53f642cd2e870f24f6aa6e3034f09. Full production workflow
38474194714 passed; Manager tests: 17 passed, 1 real-image fixture skipped,
0 failures/errors. Production identity/actual-module target tests: 4 passed.
All five release assets, manifest, SHA256SUMS and embedded ksud hashes verified.
Downloaded APK independently verifies with the trusted production certificate.
APK SHA-256 9e627f6c2d9b3b8eda9b5bdd63a4853b05b90025a4b9e1725ad910a56fcab1d7.

adb install -r returned Success; device reports versionCode 32640. Actual Manager
accessibility text shows working and driver 32631-2, with no build-mismatch alert.
Driver/root remained live; both explicit root and traditional su -c id eventually
returned UID 0 / u:r:ksu:s0. Traditional su was temporarily unavailable during
post-install checks; its exact transient cause was not proven and is retained.
No permission/feature change, flash, boot modification, reboot or module reload.
Live online target filtering could not be completed because the phone's GitHub
release request returns HTTP 403. That error is visible; incompatible modules
are covered by regression tests and real published metadata comparison.
