# HuskySU — Pixel 8 Pro (`husky`)

Device branch: **`husky-lkm`**.

| | |
|--|--|
| App | **HuskySU** |
| Package | `me.weishu.kernelsu.husky.fork` |
| KMI | `android14-6.1` |
| Release assets | `HuskySU.apk`, `android14-6.1_kernelsu.ko` |

## Build / publish

GitHub → Actions → **Husky LKM Release** → Run workflow (`create_release` checked).

Requires repository Secrets: `KEYSTORE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

Produces a GitHub Release tagged `husky-v*`.

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

Prefer **in-app** check against husky Releases (download `.ko` + flash via Manager when rooted).

## Appearance

Settings → Appearance:

- Theme: system / light / dark
- Dynamic color: off = husky seed `#1A73E8`; on = wallpaper colors

## OTA

After system OTA, use “install to inactive slot” (when available in Manager), then reboot — or re-patch a new factory `init_boot`.

## Rollback

Flash stock `init_boot` from the matching factory image.

## Production signing note

Husky LKM builds bind the **primary** manager certificate (`KSU_EXPECTED_SIZE` / `KSU_EXPECTED_HASH`) to the HuskySU keystore and set `KSU_MANAGER_PACKAGE=me.weishu.kernelsu.husky.fork`.

Do **not** pass the production cert as `KSU_EXPECTED_SIZE2` / `HASH2` — that enables PR dual-sign support and triggers the in-app warning that the kernel is not a production build.

## Credits

Based on [KernelSU](https://github.com/tiann/KernelSU). Full upstream thanks: [docs/README.md](README.md#credits) / [README_CN.md](README_CN.md#鸣谢).
