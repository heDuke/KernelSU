# Repair the UAPI 2 to 6 upgrade path

The device screenshot shows authorized Manager access, LKM mode, kernel UAPI 2
and Manager UAPI 6. Read-only module behavior follows the intended interface
gate. The new Manager and the running old LKM have not been upgraded together.

Two integration omissions were found:

1. Reviewed direct boot-image writes required the current kernel interface to
   already match. That blocked the Root-shell path used to install its replacement.
2. The pinned Canary build script still supplied `KSU_MANAGER_PACKAGE`, whereas
   upstream now reads `KSU_PACKAGE_NAME`. Rebuilding without fixing this could
   compile the wrong Manager package restriction despite the correct certificate.

## Implementation and verification

- [x] Add a narrow, pure reviewed-boot-write policy: matching positive interfaces,
  or the explicitly reviewed 2 -> 6 transition; authorization, working Root and
  safe-mode restrictions remain mandatory. Unknown/future mismatches stay blocked.
- [x] Apply it only after staged file digests, device/build identity and target slot
  have been rechecked. Preserve target release/KMI/page-size validation. Module,
  profile and ordinary driver mutation gates are unchanged.
- [x] Correct the Canary build flag. Require the production Manager package and
  certificate to be present in the audited ELF and reject mutated identities.
- [ ] Build and audit a new UAPI-6 candidate against the pinned official 4K source,
  Image, vmlinux, config and Module.symvers. Retain its actual source/digest record.
- [x] Run Manager regression/release packaging and real-fixture audit tests.
  Local Manager: 189 passed, no skips; Python: 14 passed, including identity
  rejection. Android Rust check/strict Clippy/fmt and all seven loader regressions
  passed. The real-import fixture verifies each rewritten address and no longer
  assumes that every kernel build imports exactly 206 symbols.
- [ ] Commit/push the repair and publish/verify the production APK from that commit.

Working target is the earlier researched Pixel 8 Pro ZP11.260918.007 4K kernel
`6.1.172-android14-11-g3fc92ed75e44-ab16169542`. The new screenshot does not itself
prove the current device build or page size; current confirmation was requested.
Build this target independently and let actual target inspection reject a mismatch.
Do not relax the gate or silently substitute generic production KMI modules.

The candidate remains offline-checked engineering output. Producing it does not
establish real loading, preinit/SELinux/hook behavior, cold boot or recovery. No
device operation, automatic flashing or PC/device acceptance gate is introduced.
Updating the APK alone leaves the running UAPI-2 kernel unchanged; activation
requires installing a matching replacement and rebooting into it.

## Previous production delivery closed

husky-v32762, source `0ccbc175591616fddc812a5f7fd2536b816ca080`, completed all eight
production jobs in [run 39020561386](https://github.com/heDuke/KernelSU/actions/runs/39020561386).
The downloaded public APK, both ABI workers, both KMI modules, sole trusted signer,
source identity, UI payload fingerprint and published checksums were independently
verified. Regenerated manifest/checksums matched published bytes exactly.

APK SHA-256: `e43874483d580c1c2df84d34c224531f4654a3648a578d3ced5b3038435a6255`.
That production release contains generic KMI modules, not the pinned Canary target.
Its successful packaging does not resolve the interface mismatch in the screenshot.
