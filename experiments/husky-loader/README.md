# Independent experimental Husky loader 0.2.0

Original standalone Rust application source, zero third-party crates. It does
not copy/import/include/wrap production loader code or another loader project.
Rust std and public ELF/Linux interfaces are used. This is not a formal
clean-room claim or a new operating system implementation.

First profile: unsigned 4K HuskySU candidate for Pixel 8 Pro / husky,
ZP11.260918.007. No runtime-support claim. Not integrated into production boot.
[Full plan](../../docs/superpowers/plans/2026-10-12-independent-experimental-loader.md),
[execution record](../../docs/superpowers/plans/2026-10-13-implementation-status.md),
[PC handoff](../../docs/superpowers/plans/2026-10-13-pc-codex-handoff.md).

## Commands and load gate

```sh
husky-loader-experimental help
husky-loader-experimental inspect MODULE.ko
husky-loader-experimental check MODULE.ko --proof target.proof --symvers Module.symvers --config kernel.config
husky-loader-experimental plan MODULE.ko --proof target.proof --symvers Module.symvers --config kernel.config
husky-loader-experimental doctor
husky-loader-experimental status
husky-loader-experimental check-device MODULE.ko --proof target.proof --symvers Module.symvers --config kernel.config
```

Schema 1 JSON by default; exit 0 success, 2 rejection. Inspect/check/plan need no
root. Doctor reports bounded environment information. Check-device adds exact
running release and real page-size checks, not complete image/config identity or
private ABI/CFI. Status is a diagnostics alias: root health/module presence are
explicitly not probed.

Load is behind an unconditional campaign rejection in 0.2.0. Root,
--experimental, a different proof or settings cannot authorize it. Expected:
campaign_not_approved, load_attempted=false. Private-type/CFI classification and
reviewed matching-device recovery evidence must precede a separate enabled build.

Inventory: 135 public / 71 private imports. Tests prove hybrid mutation preserves
public undefined symbols and all 136 CRC records, binding private symbols only.
Private imports remain unclassified. Signed private-rewrite inputs are rejected;
no signature/CRC stripping, force flags, CFI/sysctl changes, retries or reboots.
Input pins/source commit are provenance claims, not publisher or device trust.
Root functionality is not inferred from module presence or syscall success.

## Preferences

```sh
husky-loader-experimental settings show
husky-loader-experimental settings diff --preset diagnostic
husky-loader-experimental settings export --preset lean --output lean.conf
husky-loader-experimental settings validate --settings lean.conf
mkdir -m 700 preferences
husky-loader-experimental settings import lean.conf --store preferences/settings.conf --expect-generation 0
husky-loader-experimental settings reset --store preferences/settings.conf --expect-generation 1
```

--settings FILE, --preset balanced|lean|diagnostic and repeated --set key=value
apply to module/read-only commands. Precedence: saved preset/overrides, one-shot
preset for its governed keys, CLI overrides. Values and origins are reported.
Writes require an existing owned private directory and matching generation;
unknown/duplicate fields, symlinks, oversize and stale writers fail. Separate
flock, owned temporary file, fsync and atomic rename; uncertain commits reported.

Bounded 16 KiB settings: JSON/text, verbosity, kernel-log capture, 4-64 KiB logs,
8/16/32/64 KiB IO buffers, 1-16 MiB module limit, cooperative 1-30 second deadline.
The deadline cannot interrupt a syscall. History=0 and cache=false are immutable
for now; unsupported values rejected. No verification-bypass preference exists.
Manager saves app-private preferences; explicit export provides CLI interchange.

## Build and evidence

```sh
cargo test
cargo clippy -- -D warnings
cargo fmt -- --check
cargo build --release
cargo ndk -t arm64-v8a check
cargo ndk -t arm64-v8a clippy -- -D warnings
cargo ndk -t arm64-v8a build --release
```

Set HUSKY_LKM_FIXTURE and HUSKY_PROOF_DIR (target.proof, Module.symvers,
kernel.config), then cargo test -- --include-ignored for the actual candidate.

Tools/benchmark.py performs read-only host checks. The 90-check pilot is not a
load benchmark; sampled post-exec VmHWM is a lower bound, not device peak RSS.
Tools/device_probe.py runs doctor/check-device through selected adb and su in an
owned temporary directory. It never calls load or changes boot, retains rejected
reports and cleans up only that directory. --dry-run contacts no device.

ARM64 binary uses /system/bin/linker64. Early-init availability is unverified.
Greater stability, lower device memory and superiority to production are goals
awaiting equivalent device measurements.
