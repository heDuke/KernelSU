# Production Manager delivery

User requested the production path after identifying an older PR installation.
Keep package `me.weishu.kernelsu.husky.fork` and the existing trusted production
certificate. Do not use temporary PR signing for this delivery.

- [x] Embed the checked-out source commit and version in the production APK.
- [x] Require a unique input APK and verify Manager code/resources/assets are
  unchanged by ksud replacement and signing; retain hashes in the release manifest.
- [ ] Run publication regressions and the complete production workflow.
- [ ] Download public release assets and independently verify identity, signature,
  source metadata, UI payload hashes and checksums before delivery.

The workflow rebuilds native payloads. Generic KMI artifacts retain exact-target
gates and do not establish new Canary device acceptance. A test-signed installation
of the production package may still require a deliberate migration on the PC side;
do not remove user data or flash the device as part of this publication.

Local publication regressions: 9 passed with a real LKM fixture. UI payload hashes
are stored as one compact aggregate so manifest size remains within the Manager
update parser limit. YAML structure validation passed.
