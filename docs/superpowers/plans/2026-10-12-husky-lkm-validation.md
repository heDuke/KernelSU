# Husky Canary LKM follow-up verification

Date: 2026-10-12. Scope: matching-source inspection, real-artifact audit and an
offline ELF model. No kernel loading, flashing or device testing was performed.

Target: ZP11.260918.007, Pixel 8 Pro, 4K kernel
`6.1.172-android14-11-g3fc92ed75e44-ab16169542`.

Evidence: [machine-readable results](2026-10-12-husky-lkm-validation-evidence.json).
Candidate SHA-256:
`eeb1dcca4239ce6be846c304b3d57214b0d4cccca811f6f17746d86ab719bc55`.
The candidate and distributed ZIP were not modified.

## What was verified

1. Re-ran all three real-artifact audit regression tests. The original candidate
   passes; wrong page size, a corrupted CRC and truncated ELF are rejected.
   The test names group multiple mutations into three tests.
2. Compared all candidate imports with the matching official vmlinux and
   Module.symvers. All 206 imports have unique non-zero normalized addresses in
   vmlinux; 135 are in official Module.symvers, and 71 lack published symbol CRCs.
3. The original candidate covers all 135 imports with published CRCs and has a
   separate `module_layout` entry: 136 version records total. There are no missing
   published import-version entries in the shipped candidate.
4. Modeled the current loader's ELF symbol mutation using matching vmlinux
   addresses. All 206 imports become SHN_ABS, leaving zero unresolved symbols.
   This changes 2,051 bytes and the whole-file hash, while preserving `.modinfo`
   and `__versions`. The model is not actual device kallsyms or relocated code.
5. No candidate `__ksymtab_*` exports were found, so no protected-export-list
   overlap was found. This does not prove all runtime GKI conditions pass.
6. The candidate has no appended Linux module-signature marker.

Reproduction of the audit regression run:

```bash
KSU_CANARY_AUDIT_INPUTS=/workspace/kernel-lkm-review/canary-build-16169542 \
KSU_CANARY_MODULE_FIXTURE=/workspace/artifacts/husky-canary-4k-candidate/android14-6.1_4k_ZP11.260918.007_candidate.ko \
KSU_CANARY_CONFIG_FIXTURE=/workspace/toolchain/ksu-canary-kdir/.config \
python3 -m unittest discover -s scripts/tests -p test_canary_lkm_audit.py -v
```

The additional probe is `/tmp/huskysu-lkm-validation/probe.py` in this workspace.
It reads the real ELF and matching inputs, mutates copies only, and never invokes
`init_module`. Its transient location is not a permanent regression-test suite.

## Findings affecting the verification contract

### Import CRC checks are not all executed by the kernel loader

`userspace/ksuinit/src/lib.rs::load_module` transforms imports before calling
`init_module`. Matching `kernel/module/main.c::simplify_symbols` leaves SHN_ABS
unchanged; only SHN_UNDEF enters `resolve_symbol_wait`. That normal resolution
path performs import-version, namespace and relevant GKI symbol-access checks.
Consequently, these 206 rewritten imports do not pass through that path.

`check_modstruct_version` separately checks `module_layout` before allocation and
still executes. ELF checks, signature checks, protected export checks, relocation
and runtime control-flow checks are not generally eliminated by this mutation.

The 71 imports lacking published CRCs require exact-source/config/toolchain
evidence and runtime verification; finding an address does not establish an ABI.
Do not extrapolate to another release or KMI based on a successful load alone.

### Pre-fix offline audit had a reproducible completeness gap

Removed only the `kobject_del` version record from a temporary ELF copy and
adjusted the version-section length. Before hardening, `audit_canary_lkm.py`
accepted the copy with 135 records. The old checker validated present records and required `module_layout`,
but does not require a version record for every import in Module.symvers.

This was an audit limitation, not evidence that the original candidate is missing
a CRC. The fix now derives expected coverage from actual imports and pinned
Module.symvers and requires all applicable records plus `module_layout`. A new
real-artifact test rejects a missing import CRC. A minimum record-count
check alone is insufficient because a different symbol can occupy a record.

### Signature presence and signature validity are separate

The target enables MODULE_SIG, MODULE_SIG_ALL and MODULE_SIG_PROTECT, with
MODULE_SIG_FORCE disabled. Matching `kernel/module/signing.c` permits missing
signatures or unavailable keys under this protection mode but rejects other
fatal signature errors. MODULE_SIG_ALL means automatic signing during the
configured kernel build; it is not a universal requirement for external modules.

A signature authenticates its signed bytes. The loader's symbol rewrite changes
those bytes, so the original signature cannot be advertised as validating the
submitted module. Actual error behavior depends on the key and verification
path; this model does not prove every signed module is always rejected.
Define supported signature handling before adding signing or trusted-signature
labels. Publisher artifact authentication is a separate pre-mutation mechanism;
SHA-256 metadata alone is not a publisher signature.

### Vermagic, CFI and BTF need precise labels

Matching `kernel/module/version.c::same_magic` ignores the release substring
when the module has CRCs. HuskySU's current exact-release preflight is stricter;
retain it while the loader uses private symbols and mutable imports.

CFI_CLANG is enabled and CFI_PERMISSIVE is disabled. The audit compares one shared
CFI type ID; that does not cover all indirect calls or runtime paths. BTF_MODULES
and MODULE_ALLOW_BTF_MISMATCH are enabled. The audit checks BTF section presence,
not full semantic correctness; BTF presence or tolerated mismatch must not become
a compatibility guarantee.

## Required follow-up gates

- Fix audit completeness and add real mutation regression cases before expanding
  the audit's assurances or publishing a verified-support claim.
- Define and test loader preflight for all imports, including missing/ambiguous
  addresses and expected version coverage. Preserve exact target pinning for
  imports without public CRCs. Kernel checks skipped by mutation must be explicit.
- Define unsigned/signed/trusted signature states and test the submitted-byte
  contract. Do not disable kernel protection or force mismatched loading.
- Test actual relocation, runtime CFI and kernel diagnostics on the matching
  device; include load/boot, Manager authorization, root use, restart and recovery.
- Record load failures by category: metadata, missing/ambiguous symbol, CRC/layout,
  signature, GKI/namespace, relocation, and runtime failure when identifiable.
- Keep offline-audited, load-tested and boot/recovery-tested states separate.
  Current support remains an offline-checked 4K candidate; 16K is unverified.

## Subsequent implementation on 2026-10-12

The initial probe above describes the pre-fix state. Loader hardening now performs
complete unsigned import preflight before mutation, rejects missing/hidden/
ambiguous symbols and scanner failures, preserves signed bytes for direct kernel
resolution, narrows retry to module-specific ENOEXEC diagnostics, and bounds log
reading. The audit now rejects missing applicable CRC records.

Seven host loader tests (including the real candidate ELF), four real-artifact
Python audit tests, ARM64 Android check and Clippy with warnings denied passed.
No test invoked a module-loading syscall. Kernel-enforced import checks for the
unsigned mutation path, trusted-signature behavior and runtime/boot/recovery
verification remain separate, pending gates. Existing candidate binaries and the
distributed ZIP have not been rebuilt or modified by this source update.
