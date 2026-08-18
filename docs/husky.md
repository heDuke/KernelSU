# HuskySU — Pixel 8 Pro (`husky`)

Device branch: **`husky-lkm`**.

| | |
|--|--|
| App | **HuskySU** |
| Package | `me.weishu.kernelsu.husky.fork` |
| KMI | `android14-6.1` |
| Release assets | `HuskySU.apk`, `android14-6.1_kernelsu.ko` |
| Latest tags | [`husky-v*`](https://github.com/heDuke/KernelSU/releases) |

Project overview: [README.md](../README.md) / [README_CN.md](../README_CN.md).

## Build / publish

GitHub → Actions → **Husky LKM Release** → **Run workflow** with **`create_release` checked**.

Requires repository Secrets: `KEYSTORE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

| Trigger | Builds | Creates GitHub Release |
|---------|--------|-------------------------|
| `push` to `husky-lkm` | Yes | **No** |
| `workflow_dispatch` + `create_release` | Yes | **Yes** (`husky-v*`) |

## First-time flash (PC)

Use stock `init_boot.img` from the **same** factory build as the phone.

```bash
ksud boot-patch \
  -b init_boot.img \
  -m android14-6.1_kernelsu.ko \
  --kmi android14-6.1 \
  --allow-shell \
  -o . --out-name husky_patched.img

adb reboot bootloader
fastboot flash init_boot_a husky_patched.img
fastboot flash init_boot_b husky_patched.img
fastboot reboot

adb install -r HuskySU.apk
adb shell su -c id   # expect uid=0
```

Remove older KernelSU / `.pr` managers first (different package id).

## Later updates

Prefer **in-app**:

1. Home → husky LKM card / **Align** (when an update is available)
2. Or Environment check → open details → align
3. Keep manager and driver `versionCode` matched after installing a new APK

## In-app features (fork)

- **Environment check** (Settings + home summary): BL / AVB, Root/LKM, manager↔driver, Zygisk/LSPosed, SSL-unpin module scan, copy report
- **Module pending**: multi-select batch download → flash (recommended / updatable)
- **Module repository**: Settings and Module tab entry
- **OTA inactive slot**: Home husky card section (confirm before flash)
- **Appearance**: theme mode + dynamic color only

## Appearance

Settings → Appearance:

- Theme: system / light / dark
- Dynamic color: off = husky seed `#1A73E8`; on = wallpaper colors

## OTA

After system OTA, use “install to inactive slot” on the Home husky card (when shown), then reboot — or re-patch a new factory `init_boot`.

Align / env-check **does not** auto-flash the inactive slot.

## Rollback

Flash stock `init_boot` from the matching factory image.

## Production signing note

Husky LKM builds bind the **primary** manager certificate (`KSU_EXPECTED_SIZE` / `KSU_EXPECTED_HASH`) to the HuskySU keystore and set `KSU_MANAGER_PACKAGE=me.weishu.kernelsu.husky.fork`.

Do **not** pass the production cert as `KSU_EXPECTED_SIZE2` / `HASH2` — that enables PR dual-sign support and triggers the in-app warning that the kernel is not a production build.

## Credits

Based on [KernelSU](https://github.com/tiann/KernelSU). Full upstream thanks: [README.md](README.md#credits) / [README_CN.md](README_CN.md#鸣谢).
