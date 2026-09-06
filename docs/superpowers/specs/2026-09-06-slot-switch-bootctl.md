# Slot switch / bootctl fix (2026-09-06)

## Context

Husky device on Android 17 QPR2 Beta 4 adaptation path. Device may **not** have applied a system OTA yet. The user-facing bug is **active boot slot switch** during Manager “install to inactive slot” (`boot-patch -u` → `post_ota()`).

## Root causes

### S1 — argv (fixed in husky-v32625)

`post_ota()` previously called:

```rust
Command::new(BOOTCTL_PATH)
    .arg(format!("set-active-boot-slot {target_slot}"))
```

That passes a **single** argv containing a space. `bootctl` expects two argv entries.

Also: `hal-info` failure no longer silently skips; exit codes are checked.

### S4 — HIDL-only bootctl on Android 17 (Pixel husky beta)

Device exposes AIDL `android.hardware.boot.IBootControl/default`.  
Embedded `userspace/ksud/bin/*/bootctl` was an old HIDL `bootctrl@1.0` client and prints:

```text
Error getting bootctrl v1.0 module.
```

So `post_ota` fails after partition write. **Fix:** replace embedded `bootctl` with an AIDL-capable binary (Magisk v31 `libbootctl.so` / AOSP BootControlClient). On-device check:

```text
HAL Version: android.hardware.boot@aidl::IBootControl
```

## Verification (no system OTA required)

```bash
getprop ro.boot.slot_suffix
/data/adb/ksu/bin/bootctl hal-info
/data/adb/ksu/bin/bootctl get-current-slot
# wrong (should fail):
/data/adb/ksu/bin/bootctl "set-active-boot-slot 1"
# right:
/data/adb/ksu/bin/bootctl set-active-boot-slot 1
# switch back immediately if the opposite slot is not prepared
/data/adb/ksu/bin/bootctl set-active-boot-slot 0
```

After installing a build with the fix, `ksud boot-patch -f -u ...` should log `- Switching active boot slot` and `- Active boot slot switch complete`.

## Out of scope here

Full post-OTA inactive write + reboot root (OTA-later). Virtual A/B / KMI asset issues deferred until a real OTA.
