# Slot switch / bootctl fix (2026-09-06)

## Context

Husky device on Android 17 QPR2 Beta 4 adaptation path. Device may **not** have applied a system OTA yet. The user-facing bug is **active boot slot switch** during Manager “install to inactive slot” (`boot-patch -u` → `post_ota()`).

## Root cause (S1)

`userspace/ksud/src/boot_patch.rs` `post_ota()` previously called:

```rust
Command::new(BOOTCTL_PATH)
    .arg(format!("set-active-boot-slot {target_slot}"))
```

That passes a **single** argv containing a space. `bootctl` expects:

```text
bootctl set-active-boot-slot <0|1>
```

Related issues fixed in the same change:

- S2: `hal-info` failure no longer silently skips slot switch (`Ok(())`).
- S3: `set-active-boot-slot` / `get-current-slot` exit status is checked; failures `bail!` with stderr.

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
