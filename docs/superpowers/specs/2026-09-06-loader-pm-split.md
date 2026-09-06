# Loader / Package Manager split (2026-09-06)

## Decision lock

| Item | Choice |
|------|--------|
| Product shape | Dual APK (Phase 1 code split → Phase 2 second APK) |
| Trust model | **B1** — Packages talks to `ksud module …` via root shell; only Loader is kernel-bound manager |
| Transition | Built-in PM remains in Loader APK for one major version (toggle later) |
| Naming | **HuskySU** (Loader) + **HuskySU Packages** |
| UI | Material 3 / M3 Expressive **only** — no new custom component library |
| Slot / OTA | `post_ota` / inactive-slot ∈ **Loader** only |

## Package IDs

| App | applicationId |
|-----|----------------|
| Loader (existing) | `me.weishu.kernelsu.husky.fork` |
| Packages (new) | `me.weishu.kernelsu.husky.packages` |

## API boundary (`ksud` CLI)

**Loader**

- `LateLoad`, `Insmod`, `BootPatch` / `BootPatchV2`, `BootRestore`, `Unload`, `Install`, `Uninstall`
- `Profile`, `Feature`, `Kernel`, `Sepolicy`, `Resetprop`, `Initrc`
- Init events: `PostFsData`, `Services`, `BootCompleted`
- `Su` / debug su, sulogd

**Package**

- `Module *` (install / uninstall / enable / disable / list / action / …)
- `module_config` persistence used by modules
- Metamodule **install orchestration** (symlink + hooks still consumed by Loader boot)

**Shared contracts (unchanged paths)**

- `/data/adb/ksu/` — Loader
- `/data/adb/modules/`, `modules_update/` — Package writes; Loader boot executes
- `/data/adb/metamodule` — Package installs; Loader invokes hooks
- Supercall IOCTL — **Loader only**

## Deep link (Phase 2)

- `huskysu://packages/` — open Packages home
- `huskysu://packages/module/{id}` — module detail / action

## Non-goals

- deb/APT package format
- Rewriting metamodule / overlayfs
- Third-party PM marketplace (CLI docs only, later)
- New custom UI widget set

## Phased delivery

1. **Phase 0** — this spec (done)
2. **Phase 1** — `LoaderClient` / `PackageClient` façade split; keep single APK
3. **Phase 2** — second `applicationId` + CI assets + deep link
4. **Phase 3** — optional independent versioning

## Relation to slot hot-fix

`post_ota` bootctl argv fix ships in Loader/`ksud` independently of dual-APK. Split must not regress inactive-slot.
