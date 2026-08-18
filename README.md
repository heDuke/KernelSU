# HuskySU

**HuskySU** is a Pixel 8 Pro (`husky`) maintenance build of [KernelSU](https://github.com/tiann/KernelSU): LKM root (`android14-6.1`), Material 3 Expressive manager, and GitHub Releases for `.ko` + APK.

| | |
|--|--|
| **App** | HuskySU |
| **Package** | `me.weishu.kernelsu.husky.fork` |
| **Branch** | [`husky-lkm`](https://github.com/heDuke/KernelSU/tree/husky-lkm) |
| **CI** | [Husky LKM Release](https://github.com/heDuke/KernelSU/actions/workflows/husky-release.yml) (single KMI) |
| **Releases** | [github.com/heDuke/KernelSU/releases](https://github.com/heDuke/KernelSU/releases) (tags `husky-v*`) |

## Install

1. Open a **`husky-v*`** Release and download:
   - `HuskySU.apk`
   - `android14-6.1_kernelsu.ko` (first-time PC flash; later updates can be in-app)
2. First-time root (PC): see [docs/husky.md](docs/husky.md) — patch stock `init_boot` with the `.ko` and `fastboot flash init_boot`.
3. Install `HuskySU.apk`. Later: use the app to check husky Releases and update the LKM.

Uninstall any previous `me.weishu.kernelsu` / `.pr` manager before installing HuskySU (new package id).

## Features (husky fork)

- One-shot CI: `android14-6.1` LKM + signed HuskySU matching the same signing cert
- In-app update from husky GitHub Releases (planned / rolling)
- OTA inactive-slot install path
- Slim recommended modules (not a full module market)
- Appearance: light / dark / system + optional dynamic color only
- Full Material 3 Expressive UI direction

## Build

```text
Branch: husky-lkm
Action: Husky LKM Release (workflow_dispatch, create_release=true)
Secrets: KEYSTORE, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD
```

## Credits

This fork is **based on KernelSU**. Upstream project and thanks:

- [tiann/KernelSU](https://github.com/tiann/KernelSU) — the KernelSU project
- See also [docs/README.md Credits](docs/README.md#credits) for the full upstream acknowledgement list (Magisk, genuine, Diamorphine, etc.)

## License

Same as upstream KernelSU:

- `kernel/` — GPL-2.0-only
- elsewhere — GPL-3.0-or-later

---

Upstream documentation mirrors live under [`docs/`](docs/) (English / 简体中文 / …). Device-specific steps: [`docs/husky.md`](docs/husky.md).
