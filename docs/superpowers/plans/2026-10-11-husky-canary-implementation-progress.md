# HuskySU Canary implementation progress

Baseline: `4252df8cf47da5ffe2f2163d3d1226c372f4e8b7`.
Target: Pixel 8 Pro, `ZP11.260918.007`, default 4 KB mode.
Update: a separate matching 6.1.172 / 4K engineering candidate has now been
built and checked offline; see the [candidate report](2026-10-11-husky-canary-candidate.md).
The artifact descriptions and test counts below describe the foundation milestone.
This is the first implementation milestone of the
[reviewed plan](2026-10-10-husky-canary-zp11.260918.007.md).
No device was flashed, rebooted or used to load a module. The user has requested
committing and pushing this foundation milestone; no production release is
published and no device-support declaration is made by that source push.

## Implemented

- Shared Rust kernel target and bounded ELF module inspection. Check actual
  architecture, Android KMI, page size, module identity and exact kernel release
  before packaging and loading. Reject unknown page metadata and cross-release
  vermagic adaptation. The boot loader retains its existing stock-init fallback.
- Embed `ksu_page_shift`, `ksu_kernel_release` and build-provided `ksu_kmi` in new
  LKMs; pass the KMI through the DDK workflow. These are necessary conditions,
  not signed provenance or an ABI/device support record.
- Probe running kernel page size from `/proc/self/auxv` AT_PAGESZ rather than
  libc sysconf. Manager reads the same ksud result. Inspect selected boot images
  using decompressed ARM64 Image flags; boot header alignment is not page size.
- Bounded legacy-LZ4 parsing in Manager, covering the researched Canary kernels.
  Backend inspection uses the existing boot-image decoder with bounded output.
- Replace the fixed 6.1 release URL with indexed KMI/page candidates. Require
  release asset size and SHA-256, verify private temporary downloads, fsync and
  atomically promote completed files. Failed or cancelled downloads preserve
  the prior destination. Hash metadata from GitHub is not a signed manifest.
- Require `--target-boot` for local init_boot/raw-ramdisk patching. The remote
  stock-image download flow supplies the companion boot from the same package.
  An entered KMI cannot bypass target checks. The local picker companion UI is
  still pending; unsupported local input fails with an actionable CLI error.
- Hold a root-owned advisory lock outside the removable KernelSU directory
  for cooperating flash/OTA, restore, uninstall, install, late-load and unload
  processes. The executor retains the lock across partition write/readback.
- Preserve backups, stage and verify them before atomic publication, and abort
  required backup failures before flashing. Validate recovery marker syntax and
  backup digest before restoration. Uninstall restores before deleting files.
- Check partition capacity before writing, sync writes and compare readback.
  Failures do not trigger an automatic flash retry.
- Validate BootControl HAL/slot mapping before OTA writes, recheck the active
  slot before activation, and refuse the old unsafe post-OTA success script.
  Stop creating a script that marks a boot healthy before Android does so.
  Reject OTA combined with local boot/kernel/raw-ramdisk/config-only overrides,
  and reject 16 KB mode in both the running kernel and inspected target.
- Serialize package observer init/exit, preserve allocation/mark errors and
  clear released state. Callers report observer initialization failure.
- Report unload/delete_module and service restart failures rather than success.
  Required helper/userspace installation failures propagate to Manager.

## Compatibility and unfinished gates

The tested DDK is `android14-6.1` / `6.1.166-dirty`; it is not the target
`6.1.172-android14-11-g3fc92ed75e44-ab16169542`. Its rebuilt module is a compile
regression artifact and is rejected for the Canary target. Existing release
modules lack the new page metadata and are also rejected. This deliberately
restricts manual and automatic loading until rebuilt, matching assets are
available; it does not preserve legacy installation compatibility yet.

Still required before publishing Canary support:

- Matching 6.1.172 generated headers, symbol versions, CFI/layout review and
  separately validated 4 KB and 16 KB builds. No forced vermagic workaround.
- A validated migration policy for existing kernels/modules and 6.12 build and
  device regression coverage. Asset-selection unit coverage is not a 6.12 build.
- Signed manifests, provenance, offline bundles and per-build support records.
- A complete read-only preview API, private plan digest and stale-plan checks.
- Durable worker/task journal, detached execution, interrupted-job reconciliation
  and Manager task/progress/recovery UI. An advisory lock alone is not a journal.
- Virtual A/B snapshot/update readiness and independent OTA page-mode evidence.
  Current 16 KB running-kernel or target OTA is rejected; no 16 KB OTA support
  is claimed.
- Stock factory archive SHA-256 verification and device acceptance: first boot,
  root/SELinux behavior, module lifecycle, suspend/reboot, OTA and recovery.

F1-F17 remain the roadmap. This milestone supplies target selection, integrity
and mutation/lifecycle foundations; it does not deliver all planned features.
Kernel UAPI and production package identity are unchanged. APK verification
builds do not use the production signing key.
The local ARM64 embedded module is the 6.1 DDK regression artifact; this session
does not produce a complete x86_64 LKM bundle or any validated 16 KB LKM asset.
The two-ABI APKs therefore verify compilation and packaging, not release readiness.

## Verification

| Check | Result |
| --- | --- |
| `cargo ndk -t arm64-v8a check` | Passed |
| `cargo ndk -t arm64-v8a clippy` | Passed, no warnings |
| `cargo fmt` | Completed after check and clippy |
| Portable Rust contract tests | 9 passed: 7 unit + 2 actual artifact tests |
| Observer source fault/concurrency harness | Passed |
| ksuinit/ksud API 26 release builds | ARM64 and x86_64 passed |
| ARM64 android14-6.1 DDK module including BTF | Passed, 6.1.166 build regression only |
| Manager unit tests with Canary fixtures enabled | 9 passed, 0 failures/errors/skips |
| Manager Debug APK | Passed; expected package and both updated ksud ABIs checked |
| Manager Release APK | Passed; final package and both updated ksud ABIs checked |

The actual artifact tests inspect the extracted default Canary kernel and the
newly built DDK module. The latter passes its own declared DDK target but is
rejected for Canary 6.1.172 and 16 KB mode. The observer harness compiles the
actual observer C source with controlled kernel-operation doubles; it tests
early unload, allocation/watch failures, repeated init/exit and concurrency.
It is not a kernel runtime or ABI test. Partition write/readback, Android service
restart, process-death lock behavior and real OTA recovery remain device tests.
Manager compilation emits existing deprecated icon, redundant safe-call and
generated AIDL unchecked-operation diagnostics; Rust checks emit no warnings.
The release build also reports Gradle deprecations for the future Gradle 10
upgrade. Both APK variants verify the production package name; the Release
verification APK is signed with the Android Debug certificate, not the
production certificate, and must not be published as a production update.
Gradle strips packaged native executables, so APK library bytes are not compared
directly with unstripped Cargo outputs. Packaging is checked against Gradle's
stripped outputs and the new auxv probe is present in both ABIs.
After the final OTA argument/page guard fix, check/clippy/fmt and both ksud
release builds passed again. Both APKs were refreshed successfully and verified
against the stripped final executables, including the new OTA rejection path.
The full clean Manager test/Debug/Release build executed 118 tasks successfully;
the native refresh executed 19 tasks with 88 up to date. Unit tests were not
repeated for the native-only refresh because their Kotlin sources were unchanged.

Reproduce the portable and observer tests:

```sh
KSU_CANARY_FIXTURE_DIR=/workspace/kernel-lkm-review/canary-research \
KSU_DDK_MODULE_FIXTURE=/workspace/KernelSU/kernel/kernelsu.ko \
cargo test -p ksu-contracts
python3 scripts/tests/test_pkg_observer.py
```

The locked upstream Git repositories for some Rust dependencies were unavailable
under their original organization. This session fetched the exact locked commits
from public forks using a scoped Git URL rewrite; dependency versions, lockfile
commit IDs and repository URLs were not upgraded. Toolchain inputs and logs are
outside the repository under `/workspace/toolchain`. The DDK image layers were
digest-verified before selective extraction; this is independent of the missing
factory archive hash verification.

The fixture directory is `/workspace/kernel-lkm-review/canary-research`; tests
must set `KSU_CANARY_FIXTURE_DIR` to execute the real factory-kernel cases.
The complete 4 GB factory ZIP was not hash-verified; Range extraction evidence
and fixture tests do not replace that release gate.
