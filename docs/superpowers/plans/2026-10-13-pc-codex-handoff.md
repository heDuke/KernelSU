# PC Codex handoff: HuskySU Manager and independent loader

Date: 2026-10-13. User delegates physical-device tests to PC Codex.
Read [local implementation record](2026-10-13-implementation-status.md) first.
This is an engineering package, not a verified-support release.

## Package and source

HuskySU-PC-Codex-handoff-20261013.zip contains manifest.json and SHA256SUMS with
payload paths, sizes and SHA-256 hashes: Manager APK, independent Android ARM64
loader, candidate .ko/proof/symvers/config, probe tool, validation reports and
source patch. No credentials, signing key, personal data or boot image included.

Source patch includes this round and earlier pending hardening, based on
heDuke/KernelSU commit 8115aef9ff17e726083d3580bc8edffc219fc1ab.
The bundle is a pre-commit snapshot. Prefer the repository's updated main branch
for committed source. Apply the bundled patch only to its clean base checkout,
not to a checkout that already contains these changes. In a separate worktree:

```sh
git checkout -b review/huskysu-20261013 8115aef9ff17e726083d3580bc8edffc219fc1ab
git apply --check /path/to/package/HuskySU-20261013-source.patch
git apply /path/to/package/HuskySU-20261013-source.patch
git diff --check
```

Preserve unrelated local changes. For rebuilding, follow AGENTS.md, including
existing ksud JNI binaries, and matching Rust/JDK/SDK/NDK. Material 3 is pinned
to 1.5.0-alpha26; do not silently upgrade dependencies during acceptance.

## A. Baseline and recovery

Expected Pixel 8 Pro / husky, ZP11.260918.007,
6.1.172-android14-11-g3fc92ed75e44-ab16169542, ARM64, real 4096-byte pages.
A model name alone is insufficient; do not promote a mismatched device.
Select one device and save baseline outputs:

```sh
adb devices -l
adb -s SERIAL shell getprop ro.product.device
adb -s SERIAL shell getprop ro.build.id
adb -s SERIAL shell getprop ro.build.fingerprint
adb -s SERIAL shell uname -a
adb -s SERIAL shell getconf PAGESIZE
adb -s SERIAL shell su -c id
adb -s SERIAL shell su -c 'cat /proc/sys/kernel/modules_disabled'
adb -s SERIAL shell su -c 'cat /proc/self/attr/current'
adb -s SERIAL shell su -c 'cat /proc/modules'
```

Obtain exact matching official boot/init_boot and current patched artifacts,
record hashes/active slot, and confirm user-controlled bootloader/recovery access
and known-good restoration. Verify image identity/restore method before any
controlled load or boot integration. Preserve existing root. No generic flash,
bootloader unlock or data wipe is prescribed.

## B. Manager signing and acceptance

Supplied APK Android Debug certificate SHA-256:
36d1a805dd1ba9032efcd5075c9625a497986ec634176ab62b38144684389dcb.
Check installed Manager signer and kernel Manager authorization first. A different
signer cannot update the current Manager in place. If not trusted, rebuild with
the already authorized project signing workflow. Do not bypass certificate checks
or uninstall the working Manager just to make installation succeed.
After signer compatibility is established: adb -s SERIAL install -r APK.
Record installed version, signer and APK hash. Test these cases:

1. Status/Modules/Discover/Authorization/Settings, root granted/denied, portrait/
   landscape/wide layout; light/dark/dynamic/fixed theme, enlarged fonts including
   200%, TalkBack, reduced motion, back navigation, touch targets and long labels.
2. Search/source/category/sort, source enable/disable, refresh/offline cache, one
   broken source, missing/invalid data/images, README retry and release actions.
   Same module ID from different sources must not merge or switch silently.
3. Online cards open preview Bottom Sheet; close/share/icon/screenshots/intro/
   metadata work. Explicit details alone opens full module page. Install changes
   to review in the sheet; back returns to preview; scrim/drag dismiss naturally.
4. Local ZIP, online install/update, installed update, batches and external intents
   require shared review. Selected version/source must match executed asset.
   Unknown compatibility stays unknown; API mismatch needs consent; declared
   KernelSU unsupported stays blocked.
5. Controlled negative fixtures: wrong ID, metadata CRC corruption, incomplete
   download, no space/dependency, Magisk conflict, root denied/safe mode and
   metamodule collision. No script after failed preflight. Use only known test
   packages for privileged script execution after recovery preparation.
6. Real bytes/speed/stages/results/logs; rotate/background/dismiss/reopen without
   duplicate tasks/scripts. Browse another preview/details during install; new
   install disabled until completion. Partial batch success preserves restart
   indication. Reboot remains user initiated.
7. Notifications granted/denied, foreground-service startup denial, network drop,
   duplicate waiter, cancel, filename collision and partial cleanup. Notification
   progress matches bytes. Durable process-death installer recovery is absent.
8. Settings preset/override/origin, duplicate/invalid import, export round-trip,
   draft rotation, stale generation/reset/restart. Saving cannot load a module or
   change boot. Cache/history and verification-bypass controls remain unavailable.

Save screenshots and bounded logs with artifact/device/test case identity.
Record expected/observed results and exact reproduction for failures.

## C. Independent read-only device probe

Python 3 and adb, from bundle root (replace SERIAL):

```sh
python3 tools/device_probe.py --serial SERIAL --loader HuskyLoader-experimental-0.2.0-arm64 --module candidate/module.ko --proof-dir candidate --output pc-device-probe.json
```

Pushes into its own temporary /data/local/tmp directory, runs doctor/check-device
through su, removes only that directory. No load/boot/sysctl/reboot operations.
Rejections are recorded with exit code 2. Check temporary_directory_removed; if
false inspect the exact reported owned path, not broader directories. Preserve
rejected reports. Without working root, continue host/UI work; no invented
privileged bootstrap.

A successful probe proves only bounded post-boot environment/identity checks,
not early-init, running image identity, private ABI/CFI, root health or loading.
Expected 206 imports = 135 public + 71 private; 136 CRC records. Candidate SHA:
eeb1dcca4239ce6be846c304b3d57214b0d4cccca811f6f17746d86ab719bc55.
Unsigned input/provenance pins do not imply publisher authentication.

## D. Gates before loading

0.2.0 intentionally rejects load: campaign_not_approved, load_attempted=false.
No settings/CLI override. Classify all 71 private imports, exact kernel types,
relocation/CFI implications, all applicable public CRC checks, and running image/
config identity. Report public/private/unknown/rejected/proven properties clearly.

The binary needs /system/bin/linker64. Prove early-init dependencies before
creating a boot adapter. Review exact campaign/profile/recovery evidence before
issuing a separately enabled test build; never replace the gate with a preference.
No automatic retry/fallback, signature stripping, CRC removal, CFI/SELinux
disabling or kptr_restrict mutation is authorized by these tests.

After those gates, follow M5b/M5c in the
[full plan](2026-10-12-independent-experimental-loader.md): controlled single load,
actual root operations, kernel/CFI diagnostics, equivalent resource measurements,
cold boots, intentional failure handoff and recovery. If the same module is
loaded, record conflict; do not unload a working root module to evade the guard.
Automatic boot activation/default replacement remains closed until acceptance.

## Return report

Device/build/kernel/page size, artifact hashes, Manager signer/trust; UI/task
matrix; read-only probe JSON; private ABI/CFI evidence; load_attempted and
device_tested per stage; bounded/redacted logs; root/boot/recovery observations;
equivalent resource measurement method; failures and omitted tests. Host timings
and this handoff are not verified device support or superiority to production.
