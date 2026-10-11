# Husky Canary 4K engineering candidate

This follows foundation commit `15924b770a6c68cc2ad3413d1e305855a8038b24`.
Target: Pixel 8 Pro / ZP11.260918.007 / 4 KB only.
State: offline-checked candidate, not device-verified support.

## Matching build inputs

The official [android14-6.1-2026-06_r8 tag](https://android.googlesource.com/kernel/common/+/refs/tags/android14-6.1-2026-06_r8)
points to commit `3fc92ed75e44bfb2b12bc625fad7a054b939f8a6` and
[public build 16169542](https://ci.android.com/builds/submitted/16169542/kernel_aarch64/latest).
Download artifacts from that build's artifact viewer; its public page supplies
the download URL. The old `/latest/raw/` URL convention does not work here.
The Image SHA-256 is identical to the factory-extracted 4K kernel, and the
prepared `.config` is byte-identical to its embedded IKCONFIG. The matching
Clang is Android 10087095 / r487747c, not a newer NDK compiler.

| Input | SHA-256 |
| --- | --- |
| Image | e75276559886e2aba2c7d3d9d82aa0c835f4cf4b9566d179eaea9846294710ea |
| vmlinux | cb179992de8e54de5d17587dfbd661b9ee8218d6f0b2a64ab4ac78addc5f89b4 |
| kernel_aarch64_Module.symvers | 8ddcd0842039fdcb0d5421c2bf1b01d1065c0894b7487a4686d9d1b3f9d3aa71 |
| modules_prepare_outdir.tar.gz | f1c1c00f6469da56be0043d5016c2154592430fbe7cb5a8d12ba4c970476f9ad |
| kernel-headers.tar.gz | 3fdc0cb9936a3b349b733b9f57845496f5cbc50a361bd19e3238c2e9130cd28e |
| prepared .config / factory IKCONFIG | b4ba7450f12f2b6cb04f1b19b312ac448213854f7868baa7c4fa975194a8a343 |

Artifact size and MD5 were compared with the public CI listing before computing
these SHA-256 pins. That transport check is not a signed release manifest.
The whole factory ZIP hash gate remains open.

## Reproduce and audit

1. Clone the pinned official tag and verify its exact commit. Obtain the
   matching artifacts and verify the above SHA-256 values before extraction.
2. Safely extract `modules_prepare_outdir.tar.gz` into a dedicated prepared
   directory; copy vmlinux there and copy `kernel_aarch64_Module.symvers` as
   `Module.symvers`. Keep the authentic generated utsrelease and config.
3. Provide Clang r487747c, pahole with its library dependencies, libelf and a
   working resolve_btfids environment. The official host tool requires the
   `libelf.so` name; use a local library alias if only libelf.so.1 is installed.
4. Run `bash scripts/build_husky_canary.sh SOURCE PREPARED_KDIR CLANG_BIN IMAGE OUT`.
   The script relocates the prepared Makefile and regenerates SELinux headers
   from the pinned source. It never loads or flashes a module.

Unlike the patched generic DDK modpost, the official modpost checks private
unexported references. The build allows those expected warnings because the
existing ksuinit loader resolves private references using kallsyms. The subsequent
audit is mandatory: any missing/ambiguous symbol, target identity mismatch or
incorrect CRC rejects the candidate. No vermagic rewrite or empty-version-table
workaround is used; all official generated CRCs, including module_layout, remain.
The older `kernel/check_symbol` expects an empty __versions section and is not
the acceptance tool for this candidate.

The audit resolves 206 undefined references against the exact target vmlinux and
compares 136 symbol CRCs with the official Module.symvers. It confirms module BTF
and compares one shared CFI type ID. One CFI comparison is limited evidence,
not a complete callback/type-safety proof. Source/config/compiler matching and
these offline gates do not establish runtime behavior of hooks or private APIs.
The script emits `audit.json` with the actual stripped module digest.

## Validation and remaining work

- Matching module compilation including BTF completed successfully.
- Shared Rust validation accepts this 4K target and rejects 16K or another release.
- The Python audit is tested with the real candidate, wrong page metadata,
  a mutated symbol CRC and truncated ELF input.
- Rust Android check, clippy and format are rerun after the test addition.
- Final results: 10 Rust tests and 3 Python audit tests passed. The build script
  was executed successfully against the official prepared output, then its
  stripped output passed the same audit. The previous 9 Manager tests remain
  applicable because no Manager or production Rust loader code changed here.
- No hooks, UAPI or production certificate identity were changed for this build.
  No new APK is implied: the previously sent APKs have test signatures and are
  not production-root Manager builds. The candidate retains production Manager
  certificate authorization.

Still pending: real boot/root/SELinux/hook tests, unload/suspend/reboot, OTA and
recovery acceptance; 16 KB build and validation; a release asset/manifest and
validated legacy migration policy. This candidate is not embedded into the
normal APK or published as a production release. No device operation was performed.
