# Production Manager delivery

User requested the production path after identifying an older PR installation.
Keep package `me.weishu.kernelsu.husky.fork` and the existing trusted production
certificate. Do not use temporary PR signing for this delivery.

- [x] Embed the checked-out source commit and version in the production APK.
- [x] Require a unique input APK and verify Manager code/resources/assets are
  unchanged by ksud replacement and signing; retain hashes in the release manifest.
- [x] Run publication regressions and the complete production workflow.
- [x] Download public release assets and independently verify identity, signature,
  source metadata, UI payload hashes and checksums before delivery.

The workflow rebuilds native payloads. Generic KMI artifacts retain exact-target
gates and do not establish new Canary device acceptance. A test-signed installation
of the production package may still require a deliberate migration on the PC side;
do not remove user data or flash the device as part of this publication.

Local publication regressions: 9 passed with a real LKM fixture. UI payload hashes
are stored as one compact aggregate so manifest size remains within the Manager
update parser limit. YAML structure validation passed.

## Published and independently verified

- Release: https://github.com/heDuke/KernelSU/releases/tag/husky-v32650
- Source: bb1cf974aac44aa18368ad32b3fb9d56f3a3326d.
- Workflow: https://github.com/heDuke/KernelSU/actions/runs/39011427225;
  all eight jobs successful, including Manager unit tests and repack verification.
- Package: me.weishu.kernelsu.husky.fork; version code 32650;
  embedded version name husky-v32643-7-gbb1cf974.
- APK SHA-256: c1e1c88196cda3be8616bedccefd8fda9aff1e474bdb9dcfc3efa3958ab74e28.
- Trusted sole signer:
  b37ef4db671d6d1dd44d1b42efdd63effece3265649734a9d665ff5939c59553.

Public release assets were downloaded into
/workspace/artifacts/HuskySU-production-32650. All four checksum entries passed.
Independent apksig, package/version/source-asset and Manager payload fingerprint
checks passed; regenerating manifest.json and SHA256SUMS reproduced the published
files byte-for-byte. Both embedded ksud ABI payloads and both module target
identities passed. CI checked input/output Manager content equality before
publication. Device acceptance and installation-signature migration remain with
PC Codex. The separate older .pr installation is not replaced by this package.
