# Phase 2 — Dual APK implementation checklist

**Parent spec:** [2026-09-06-loader-pm-split.md](../specs/2026-09-06-loader-pm-split.md)  
**Prerequisite:** Device smoke of `husky-v32625` slot switch (recommended before merging large UI moves)  
**UI rule:** Material 3 / M3 Expressive only — no new custom widgets

---

## Goal

Ship two production-signed apps:

| App | ID | Owns |
|-----|-----|------|
| **HuskySU** (Loader) | `me.weishu.kernelsu.husky.fork` | Root, LKM/align, inactive-slot, SuperUser, AppProfile, EnvCheck, Settings (root), deep-link out to Packages |
| **HuskySU Packages** | `me.weishu.kernelsu.husky.packages` | Module list, repo, pending batch, WebUI, module action, metamodule install UX |

Trust **B1**: Packages never talks supercall; only `ksud module …` via root/`libksud.so`.

Transition: Loader keeps an **optional built-in Modules tab** for one major version (`settings` flag, default **on** → next tag default **off**).

---

## Screen ownership

| Surface | Loader | Packages |
|---------|--------|----------|
| Home (status, align, OTA slot) | yes | no |
| Install / Flash boot / uninstall KSU | yes | no |
| SuperUser / AppProfile / Templates / Sulog | yes | no |
| EnvCheck / Appearance / About (branding) | yes | thin About |
| Module list / enable / uninstall ZIP | optional embedded | **primary** |
| ModuleRepo / pending ≤5 | optional / deep-link | **primary** |
| ExecuteModuleAction / WebUI | deep-link or embedded | **primary** |

---

## PR DAG (recommended)

```text
PR-P2-0  Checklist freeze (this doc) + strings stubs for deep link / “Open Packages”
    │
    ├─ PR-P2-1  Gradle: :common + :loader-ui + :packages-ui feature modules
    │            (still one :app assembling everything — no behavior change)
    │
    ├─ PR-P2-2  Move/wire Module* / Repo / WebUI / pending into :packages-ui
    │            Loader routes call PackageClient only via that module
    │
    └─ PR-P2-3  New :packages applicationId + Manifest + M3-only shell
             │   Deep links huskysu://packages/...
             │   Loader Settings “Open Packages” + optional hide Modules tab
             │
             └─ PR-P2-4  husky-release.yml: build+repack both APKs; Release assets
                      docs/husky.md + README install order
```

Each PR must stay mergeable alone; **P2-1/P2-2 = zero user-visible change** preferred.

---

### PR-P2-0 — Docs / strings (½ day)

- [x] This plan file
- [ ] String resources: `open_packages`, `packages_not_installed`, `builtin_modules_toggle` (en + zh-CN minimum)
- [ ] Intent / deep-link scheme documented in husky.md draft section (land in P2-4)

**Done when:** copy exists; no code behavior change required.

---

### PR-P2-1 — Gradle feature modules (1–2 days)

**New modules** (names flexible):

| Module | Responsibility |
|--------|----------------|
| `:common` | Theme (`MaterialExpressiveTheme`, husky colors), `LoaderClient` / `PackageClient`, downloader helpers, serialization used by both |
| `:loader-ui` | Home, Install, Flash, SuperUser, Profile, EnvCheck, Settings root, Sulog, About |
| `:packages-ui` | Module, ModuleRepo, pending, ExecuteModuleAction, WebUI |
| `:app` | Application class, nav host, still packages **both** UIs (single APK) |

**Steps**

1. `settings.gradle.kts`: `include(":common", ":loader-ui", ":packages-ui", ":app")`
2. Move code with package rename only as needed; keep `namespace` compatible with existing R / BuildConfig where painful — prefer `api`/`implementation` on `:common` first
3. `:app` depends on `:loader-ui` + `:packages-ui`
4. JNI / `jniLibs` / CMake stay on `:app` (or `:common` if cleaner) — **do not** duplicate `libksud.so` packaging twice yet

**Done when:** `assembleDebug` / `assembleRelease` (local or CI) green; app identical to pre-split.

**Risk:** AGP multi-module R class / navigation3; mitigate by moving ViewModels with screens, keep Routes in `:app` or `:common` initially.

---

### PR-P2-2 — Boundary enforcement (1 day)

- [ ] New Packages code imports **only** `PackageClient` (lint or `androidx.annotation` / custom Detekt rule optional)
- [ ] New Loader code imports **only** `LoaderClient` for ksud ops
- [ ] `FlashIt.FlashModules` stays callable from Packages UI; boot flash stays Loader
- [ ] No supercall / `Natives` profile APIs from `:packages-ui` except read-only status if unavoidable (prefer none)

**Done when:** module graph + spot-check imports; still single APK.

---

### PR-P2-3 — Second application (2–3 days)

**Product**

1. New Gradle `application` **or** `productFlavors` / second module `:packages-app`:
   - `applicationId = me.weishu.kernelsu.husky.packages`
   - `KSU_NAME=HuskySU Packages`
   - Same signing secrets as Loader
   - Depends on `:packages-ui` + `:common` (+ embed `libksud.so` for `module` CLI)
2. Manifest:
   - `LAUNCHER` activity
   - Deep links: `huskysu://packages`, `huskysu://packages/module/{id}`
3. Loader:
   - Settings row → `Intent` to Packages (package name + fallback Play/GitHub / “not installed” dialog — M3 `AlertDialog`)
   - Optional: hide bottom Modules tab when Packages installed **and** builtin toggle off
4. Packages UI: **M3-only** shell (`Scaffold`, `NavigationBar`/`NavigationRail`, `ListItem`, `Card`, `Button`) — do not copy legacy `Expressive*` into the new APK if avoidable
5. WebUI / shortcuts: migrate intent filters to Packages; keep temporary redirect in Loader if needed

**Signing / kernel**

- Kernel still binds **Loader** cert only (B1)
- Packages APK signed with same keystore for update consistency, but **not** passed as `KSU_EXPECTED_SIZE2`

**Done when:** two APKs install side-by-side; Packages can list/toggle/flash modules with root; Loader inactive-slot still works; deep link opens Packages.

---

### PR-P2-4 — CI + docs (½–1 day)

`husky-release.yml`:

- [ ] Second Gradle assemble for Packages (`-PKSU_PACKAGE_NAME=me.weishu.kernelsu.husky.packages`)
- [ ] Repack both APKs with `repack_apk.py` (or extend script)
- [ ] Release assets: `HuskySU.apk`, `HuskySU-Packages.apk`, `android14-6.1_kernelsu.ko`
- [ ] Release body: install order — Loader first, then Packages

Docs:

- [ ] `docs/husky.md` / README: dual APK, deep links, builtin Modules toggle
- [ ] Note: push still does **not** auto-build (manual `workflow_dispatch` only)

**Done when:** one manual workflow run publishes three assets; smoke install from Release.

---

## Explicit non-goals (Phase 2)

- Splitting `ksud` into two binaries
- Kernel allowlist for Packages package name (B2)
- Removing embedded Modules from Loader in the **same** release as first dual-APK (do one cadence later)
- Play Integrity / deb packages

---

## Test matrix (husky)

| Case | Expect |
|------|--------|
| Loader only | Root, align, inactive-slot, SuperUser OK; Modules tab works if builtin on |
| Packages only | Shows “need HuskySU / root” empty state; no crash |
| Both | Deep link + Settings open Packages; module ZIP install/enable/WebUI OK |
| Uninstall Packages | Loader still roots; optional builtin Modules remains |
| Align / OTA slot | Unchanged vs `husky-v32625` |
| Theme | System/light/dark + dynamic color; no new custom components |

---

## Effort / sequencing estimate

| PR | Effort | Blocks |
|----|--------|--------|
| P2-0 | 0.5 d | — |
| P2-1 | 1–2 d | P2-0 |
| P2-2 | 1 d | P2-1 |
| P2-3 | 2–3 d | P2-2 |
| P2-4 | 0.5–1 d | P2-3 |
| **Total** | **~5–7.5 d** | after device slot smoke |

Suggested tags: `husky-vN` = dual APK first ship (builtin Modules default on); `husky-vN+1` = default builtin off.

---

## Open decisions (defaults if you say nothing)

| Topic | Default |
|-------|---------|
| Second module shape | Separate `:packages-app` Gradle project (clearer than flavors) |
| Builtin Modules default | **On** for first dual-APK release |
| Packages icon | Distinct monochrome / husky-blue variant of existing icon |
| Version codes | Same `versionCode` formula for both APKs in one Release |

---

## Approval checklist

- [ ] Accept PR DAG P2-0 → P2-4
- [ ] Confirm `:packages-app` vs productFlavors
- [ ] Confirm builtin Modules default **on**
- [ ] Optional: require slot-smoke on device before P2-1 code moves
