# Pixel 8 Pro (husky) — KernelSU LKM maintenance

Device-specific branch: **`husky-lkm`**.

## What this branch provides

- LKM only (`init_boot`), KMI **`android14-6.1`**
- Streamlined CI: [Husky LKM Release](../.github/workflows/husky-release.yml) — no full KMI matrix
- Self-signed Manager matching the built `.ko` certificate hash

## Build

GitHub → Actions → **Husky LKM Release** → Run workflow.

Artifacts (`husky-release`):

- `android14-6.1_kernelsu.ko`
- `KernelSU_husky_*.apk`

## Flash (PC)

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

adb install -r KernelSU_husky_*.apk
adb shell su -c id   # expect uid=0
```

## Notes

- After OTA, extract a new factory `init_boot` and re-patch.
- Do not relock the bootloader while rooted.
- Rollback: flash stock `init_boot` from the factory image.
