# Paired UAPI repair delivery

Production code source: `2d916ab754975ade73036dca3233b9b6584fd1a1`.
This merges the local repair and remote PR #3/#4 without replacing their UI/CI
changes. A single RootGate controls privileged operations; an older loaded driver
can use an authorized working Root shell for reviewed boot-image repair while
module mutations remain blocked until the interface matches. Unknown interfaces
never unlock module writes. File hashes, device/build, slot and target checks remain.

## APK

- [husky-v32766](https://github.com/heDuke/KernelSU/releases/tag/husky-v32766)
- [HuskySU.apk](https://github.com/heDuke/KernelSU/releases/download/husky-v32766/HuskySU.apk)
- [Production run 39082715640](https://github.com/heDuke/KernelSU/actions/runs/39082715640): all eight jobs passed.
- Package: `me.weishu.kernelsu.husky.fork`; version code 32766.
- Sole trusted signer: `b37ef4db671d6d1dd44d1b42efdd63effece3265649734a9d665ff5939c59553`.
- APK SHA-256: `57cd9350d5b34ed93ebe00bd00441a229117ada452452f54e139889091f5761d`.
- Manager payload fingerprint: `ac12de08d56f7e87320fed8e129ee3bc97b04b81642c1b634a1db8f85e70d42b`.

The public APK/core assets were downloaded independently. Signer, source identity,
both native ABI payloads including the new task worker, both generic KMI module
identities and published checksums passed. Regenerated manifest/checksum files
matched the published bytes. These generic LKMs are not the Canary engineering
file below and must not be substituted for its exact target.

## Canary engineering file

- [Immutable engineering files and instructions](https://github.com/heDuke/KernelSU/tree/cc7fb38ec20c15cf3db46929dd275d3c8d8a1a7f)
- [HuskySU-UAPI6-v32766-ZP11.260918.007-4K.ko](https://raw.githubusercontent.com/heDuke/KernelSU/cc7fb38ec20c15cf3db46929dd275d3c8d8a1a7f/HuskySU-UAPI6-v32766-ZP11.260918.007-4K.ko)
- Exact target: Pixel 8 Pro, ZP11.260918.007,
  `6.1.172-android14-11-g3fc92ed75e44-ab16169542`, android14-6.1, 4096-byte pages.
- Module UAPI 6, version 32766, same source and Manager identity as the APK.
- SHA-256: `1b8aceff680c951cd5cbefd826775340c201f8a9a7db78bd6c3b13b09825cbd8`.
- Offline audit: 230 symbol references, 149 symbol versions, BTF, expected package
  and certificate. One common CFI type was compared; this is limited evidence.
- Final file passed all seven loader tests and 14 real-fixture Python regressions.
  Merged Manager clean release build and 197 JVM tests passed locally with no skips.

Release asset upload from the cloud CLI returned HTTP 401 on uploads.github.com,
although repository API writes and git push worked. Engineering delivery therefore
uses an isolated artifact-only orphan branch, not binary files on main. Its commit
`cc7fb38ec20c15cf3db46929dd275d3c8d8a1a7f` is a transport record, not application
source. The source remains 2d916ab7 and is recorded inside the candidate manifest.
All five public files were downloaded and compared byte-for-byte with local
audited output; CANARY_SHA256SUMS and paired APK/source/version/identity checks passed.

The engineering filename is intentionally outside the production repository's
LKM-name matcher. It is not in automatic update metadata. No alternate tag was
introduced that could change future Manager git-describe version names.

## Device use and limits

The user confirmed the device still runs CANARY ZP11.260918.007. That confirmation
does not establish page size: inspect the actual complete kernel target before
selecting the 4K candidate. The App's reviewed local-LKM flow is under
Install/Update -> Use local LKM file. Confirm actual target, partition/slot and
backup information before writing. Successfully installing the replacement and
rebooting is required to replace the running UAPI-2 LKM; updating only the APK
does not do that.

This is offline_checked_candidate, not device_verified support. Actual module
loading, private ABI/CFI, preinit, Root/SELinux/hooks, cold boot, suspend, OTA and
recovery remain unverified. No device operation was executed. Independent
experimental loading remains disabled. Per the user, no PC/device acceptance gate
blocks automated publication; this does not convert missing runtime evidence into
a successful device test.

## Native title-bar follow-up

The later 2735.jpg device screenshot still shows a blank native title bar above
the in-content HuskySU heading. Version 32766 removed the Compose duplicate but
did not resolve this separate window-decor problem. Clearing the Activity title
only hid its text.

MainActivity declares Theme.KernelSU.Starting but omitted installSplashScreen().
On API 31+, the resolved splash theme inherits the platform DayNight theme;
postSplashScreenTheme is applied by the AndroidX installer, not automatically by
the manifest. Call the installer before super.onCreate so the existing normal
NoActionBar theme is applied before window decor creation. Keep the page heading,
refresh/edit actions and status-bar safe insets.

Local clean assembleRelease passed. Compiled MainActivity bytecode confirms the
installer runs before ComponentActivity.onCreate. No emulator/device visual
verification was available; this correction is newer than production v32766.

History inspection attributes the mismatch to integration cec8cc4e: it brought in
the upstream starting theme while retaining the fork Activity implementation
without its splash installer. The correction is source
`b418c2288df492df7e1018f5e9dc2f1ffb459dd1`, published as
[husky-v32768](https://github.com/heDuke/KernelSU/releases/tag/husky-v32768).
[Production run 39085671451](https://github.com/heDuke/KernelSU/actions/runs/39085671451)
passed all eight jobs. Public assets were independently downloaded: the sole
production signer, fork package/version, embedded source, both userspace ABIs,
generic module identities and checksums passed; regenerated manifest/checksums
matched the published bytes exactly. APK SHA-256:
`94485e033fb3e51ad4814f0141fa150cd659f5544c808d1dfcdb667ebd11d244`.
Existing matching UAPI-6 Canary installations only need the APK update for this
window fix; no replacement LKM or new Canary candidate is needed. Device visual
confirmation remains outstanding.
