# HuskySU

**HuskySU** is a Pixel 8 Pro (`husky`) maintenance build of [KernelSU](https://github.com/tiann/KernelSU): LKM root (`android14-6.1`), Material 3 Expressive manager, and GitHub Releases for `.ko` + APK.

[English](README.md) | [简体中文](README_CN.md)

| | |
|--|--|
| **App** | HuskySU |
| **Package** | `me.weishu.kernelsu.husky.fork` |
| **Branch** | [`husky-lkm`](https://github.com/heDuke/KernelSU/tree/husky-lkm) |
| **KMI** | `android14-6.1` |
| **CI** | [Husky LKM Release](https://github.com/heDuke/KernelSU/actions/workflows/husky-release.yml) |
| **Releases** | [`husky-v*` tags](https://github.com/heDuke/KernelSU/releases) — assets: `HuskySU.apk`, `android14-6.1_kernelsu.ko` |

## Install

1. Open a **`husky-v*`** Release and download `HuskySU.apk` and (for first flash) `android14-6.1_kernelsu.ko`.
2. First-time root on PC: see **[docs/husky.md](docs/husky.md)** — patch stock `init_boot` and `fastboot flash init_boot`.
3. Install the APK. Later updates: use **in-app** husky Release check / “align” to flash a matching LKM.

Uninstall any previous `me.weishu.kernelsu` / `.pr` manager first (different package id).

> **Publishing note:** `git push` builds CI artifacts but does **not** create a GitHub Release. To publish: Actions → **Husky LKM Release** → Run workflow with **`create_release` checked**.

## Features (this fork)

### Root / updates
- Single-KMI CI: `android14-6.1` LKM + APK signed with the **same** production cert
- In-app check against husky Releases; one-tap LKM update when rooted
- OTA **inactive-slot** LKM install (manual confirm; not auto-run by “align”)
- Production primary cert binding (`KSU_EXPECTED_SIZE` / `HASH` — not `SIZE2`/`HASH2`)

### Manager UI
- Material 3 Expressive only (Miuix removed)
- Appearance: system / light / dark + optional dynamic color (husky seed `#1A73E8` when off)
- Slim install surface; module repository re-enabled from Settings / Module
- Recommended modules section; **pending** card with multi-select batch install/update (≤5)
- About page with HuskySU branding and upstream credits

### Environment check
- Settings → **Environment check** (and home summary card)
- Bootloader / Verified Boot (AVB), KernelSU/LKM status, manager↔driver match
- Zygisk / LSPosed / recommended gaps; static scan for common SSL-unpin helper modules
- Play Integrity: placeholder until a Cloud project is configured
- Copy report to clipboard; “align” updates LKM only (does not flash inactive slot)

## Build

```text
Branch:  husky-lkm
Action:  Husky LKM Release
         workflow_dispatch + create_release=true
Secrets: KEYSTORE, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD
```

Device guide: [docs/husky.md](docs/husky.md)  
Feature wrap-up note: [docs/superpowers/specs/2026-08-18-feature-wrapup.md](docs/superpowers/specs/2026-08-18-feature-wrapup.md)

## Credits

Based on **[tiann/KernelSU](https://github.com/tiann/KernelSU)**. Full upstream acknowledgements (Magisk, genuine, Diamorphine, …): [docs/README.md § Credits](docs/README.md#credits) / [docs/README_CN.md § 鸣谢](docs/README_CN.md#鸣谢).

## License

Same as upstream KernelSU:

- `kernel/` — GPL-2.0-only
- elsewhere — GPL-3.0-or-later

---

Upstream documentation mirrors: [`docs/`](docs/) (English / 简体中文 / …).
