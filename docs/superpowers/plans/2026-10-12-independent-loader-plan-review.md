# Independent loader plan review

Date: 2026-10-12. Scope: plan consistency and feasibility checked against the
prototype source, current ARM64 artifact and matching kernel module entry checks.
This review updates documentation only; no device test or new executable build.

## Findings and disposition

### R1 - High: device testing depended on integration that depended on device testing

The former M5 required boot/recovery testing, while the required early-init adapter
was postponed to M6 after M5. The current artifact also requires
`/system/bin/linker64`, so it cannot simply run at a pre-system-mounted boot stage.
The prototype rejects an already present module, so root provided by that same
already-loaded KernelSU module does not create a clean first-load environment.
UID 0 is not sufficient: matching kernel `may_init_module` checks CAP_SYS_MODULE
and modules_disabled; security policy can independently reject loading.

Disposition: split M5a (privileged harness/dependencies/profile/restore preparation),
M5b (runtime campaign) and M5c (test adapter/boot campaign). Define runtime stage,
linker/libraries, mounts, descriptors, capabilities, external root runtime and
authorized Manager before loading. Product integration remains M6. Implementation
and real environment verification are pending.

### R2 - High: profile self-consistency did not establish approval or private ABI

The prototype accepts user-supplied pins and only probes release/page size. The
plan used reviewed/approved profiles without defining their trust anchor. Missing
public CRC metadata alone cannot classify an import as approved private access.
The prototype currently mutates all imports and does not prove the hybrid path.

Disposition: require a reviewed campaign profile digest packaged with the build/
test harness, separate generation from approval, and emit per-import provenance
using actual exports, owners/namespaces and type evidence. Unclassified imports
block the campaign build. More running-target evidence precedes any broader
installation/support claim. These are planned gates, not current code guarantees.

### R3 - Medium: preset precedence and persistence guarantees contradicted behavior

Saved resource overrides could defeat a selected preset; applying a preset did
not distinguish persistent UI selection from one-shot CLI use. The old promise
that any failed save leaves the previous file active was too strong after rename.

Disposition: define saved-base/override and one-shot paths separately, freeze
per-operation snapshots, serialize versioned settings saves, fsync the containing
directory and report commit_uncertain after a post-rename durability failure.
Do not model active/last-good files as an atomic multi-file transaction.

### R4 - Medium: cancellation and interrupted status lacked an exact contract

Pre-syscall cancellation cannot automatically interrupt a blocked OS read.
Optional history cannot reliably reconstruct a crashed load attempt, and module
presence alone may not distinguish initialization from a fully live/root-working
state. Resource caps can deliberately reject inputs, contradicting a blanket
promise of identical verdicts under all settings.

Disposition: cooperative checkpoints and requested/completed cancellation states;
bounded ephemeral attempt coordination for integrated loads; reconcile lifecycle
and root health; unknown state without evidence. Keep history optional. Test
correctness within configured caps and classify cap failures explicitly.

### R5 - Medium: performance targets needed comparable measurement and uncertainty

The current seven-run host snapshot measures read-only work, not Android loading.
VmHWM captured before final JSON excludes later allocations. Different validation
work or production load versus experimental check is not a fair comparison.

Disposition: define equivalent scopes, cold/warm conditions, sample distributions
and uncertainty. Thirty samples are pilot data; proposed superiority evidence
requires a larger repeatable campaign with confidence intervals. Keep current
numbers labeled as snapshots, and do not promote them to device/whole-lifetime
or stability guarantees.

## Result

The revised plan can guide M1 offline implementation. The first privileged device
campaign remains blocked on the R1/R2 environment, trust and target gates. No
production integration or superior-stability claim is approved by this review.

See [revised full plan](2026-10-12-independent-experimental-loader.md).
