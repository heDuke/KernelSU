#![allow(clippy::ref_option, clippy::needless_pass_by_value)]

use std::fs::File;
use std::io::{Cursor, Seek, SeekFrom};
use std::path::Path;
use std::path::PathBuf;

use android_bootimg::cpio::{Cpio, CpioEntry};
use android_bootimg::parser::{BootImage, BootImageVersion, RamdiskImage};
use android_bootimg::patcher::BootImagePatchOption;
use anyhow::Context;
use anyhow::Result;
use anyhow::anyhow;
use anyhow::bail;
use anyhow::ensure;
use ksu_contracts::{KernelTarget, inspect_kernel, validate_module};
use memmap2::{Mmap, MmapOptions};
use regex_lite::Regex;

use crate::assets;
use crate::feature_id::{FeatureId, parse_feature_id};

#[cfg(target_os = "android")]
mod android {
    use super::Result;
    pub(super) use crate::defs::{BACKUP_FILENAME, KSU_BACKUP_DIR, KSU_BACKUP_FILE_PREFIX};
    use crate::defs::{DEFAULT_PACKAGE_NAME, KSU_TEMP_BACKUP_DIR_NAME};
    use android_bootimg::cpio::{Cpio, CpioEntry};
    use anyhow::{Context, anyhow, bail, ensure};
    use regex_lite::Regex;
    use rustix::process::getuid;
    use std::fs::{File, OpenOptions};
    use std::io::{Read, Seek, SeekFrom, Write};
    use std::os::fd::AsRawFd;
    use std::path::{Path, PathBuf};
    use std::process::Command;

    use crate::utils;

    pub(super) fn ensure_gki_kernel() -> Result<()> {
        ksu_contracts::parse_kmi(&rustix::system::uname().release().to_string_lossy())?;
        Ok(())
    }

    fn parse_kmi(version: &str) -> Result<String> {
        let re = Regex::new(r"(.* )?(\d+\.\d+)(\S+)?(android\d+)(.*)")?;
        let cap = re
            .captures(version)
            .ok_or_else(|| anyhow::anyhow!("Failed to get KMI from boot/modules"))?;
        let android_version = cap.get(4).map_or("", |m| m.as_str());
        let kernel_version = cap.get(2).map_or("", |m| m.as_str());
        Ok(format!("{android_version}-{kernel_version}"))
    }

    fn parse_kmi_from_uname() -> Result<String> {
        let uname = rustix::system::uname();
        let version = uname.release().to_string_lossy();
        parse_kmi(&version)
    }

    fn parse_kmi_from_modules() -> Result<String> {
        use std::io::BufRead;
        // find a *.ko in /vendor/lib/modules
        let modfile = std::fs::read_dir("/vendor/lib/modules")?
            .filter_map(Result::ok)
            .find(|entry| entry.path().extension().is_some_and(|ext| ext == "ko"))
            .map(|entry| entry.path())
            .ok_or_else(|| anyhow!("No kernel module found"))?;
        let output = Command::new("modinfo").arg(modfile).output()?;
        for line in output.stdout.lines().map_while(Result::ok) {
            if line.starts_with("vermagic") {
                return parse_kmi(&line);
            }
        }
        bail!("Parse KMI from modules failed")
    }

    pub fn get_current_kmi() -> Result<String> {
        parse_kmi_from_uname().or_else(|_| parse_kmi_from_modules())
    }

    pub(super) fn calculate_sha1(file_path: impl AsRef<Path>) -> Result<String> {
        use sha1::Digest;
        use std::io::Read;
        let mut file = std::fs::File::open(file_path.as_ref())?;
        let mut hasher = sha1::Sha1::new();
        let mut buffer = [0; 1024];

        loop {
            let n = file.read(&mut buffer)?;
            if n == 0 {
                break;
            }
            hasher.update(&buffer[..n]);
        }

        let result = hasher.finalize();
        Ok(base16ct::lower::encode_string(&result))
    }

    fn find_backup_location(sha1: &String) -> Result<(tempfile::NamedTempFile, String)> {
        let filename = format!("{KSU_BACKUP_FILE_PREFIX}{sha1}");
        let target = format!("{KSU_BACKUP_DIR}{filename}");
        if let Ok(target_file) = tempfile::NamedTempFile::new_in(KSU_BACKUP_DIR) {
            return Ok((target_file, target));
        }

        // We have no permission to access /data/adb
        // Save it to /data/user_de/$USER/$PKG/boot_backup
        let user_id = getuid().as_raw() / 100_000;

        let backup_dir =
            format!("/data/user_de/{user_id}/{DEFAULT_PACKAGE_NAME}/{KSU_TEMP_BACKUP_DIR_NAME}");
        std::fs::create_dir_all(&backup_dir)?;
        let backup_file = format!("{backup_dir}/{filename}");
        if let Ok(file) = tempfile::NamedTempFile::new_in(&backup_dir) {
            return Ok((file, backup_file));
        }

        bail!("Both /data/adb/ksu and {backup_dir} are not accessible!")
    }

    pub(super) fn do_backup(cpio: &mut Cpio, image: &Path) -> Result<()> {
        let sha1 = calculate_sha1(image)?;
        let (mut target_file, target) = find_backup_location(&sha1)?;
        println!("- Backup stock boot image");
        let mut source = OpenOptions::new()
            .create(false)
            .truncate(false)
            .read(true)
            .write(false)
            .open(image)?;

        // Use io::copy instead of fs::copy to allow copy block device
        std::io::copy(&mut source, target_file.as_file_mut())
            .with_context(|| format!("failed to backup to {target}"))?;
        target_file
            .as_file()
            .sync_all()
            .context("sync required backup")?;
        ensure!(
            calculate_sha1(target_file.path())? == sha1,
            "backup verification failed"
        );
        target_file
            .persist(&target)
            .context("publish verified backup")?;
        File::open(
            Path::new(&target)
                .parent()
                .context("backup directory missing")?,
        )?
        .sync_all()?;

        let backup_file = CpioEntry::regular(0o755, Box::new(sha1));
        cpio.add(BACKUP_FILENAME, backup_file)?;
        println!("- Stock image has been backup to");
        println!("- {target}");
        Ok(())
    }

    pub(super) fn flash_partition(partition: &str, data: &[u8]) -> Result<()> {
        let mut blk = std::fs::OpenOptions::new()
            .read(true)
            .write(true)
            .truncate(false)
            .create(false)
            .open(partition)
            .with_context(|| format!("open {partition}"))?;
        let capacity = blk
            .seek(SeekFrom::End(0))
            .context("read partition capacity")?;
        ensure!(
            !data.is_empty() && u64::try_from(data.len())? <= capacity,
            "image is empty or exceeds partition capacity; nothing was written"
        );
        blk.seek(SeekFrom::Start(0))?;
        unsafe {
            const BLKROSET: i32 = libc::_IO(0x12, 93);
            let mut val: libc::c_int = 0;
            if libc::ioctl(blk.as_raw_fd(), BLKROSET, &raw mut val) != 0 {
                bail!("Failed to set rw for {partition}: {}", *libc::__errno());
            }
        }
        blk.write_all(data).context("flash boot failed")?;
        blk.sync_all().context("sync boot failed")?;
        blk.seek(SeekFrom::Start(0))?;
        let mut buffer = [0u8; 8192];
        for chunk in data.chunks(buffer.len()) {
            blk.read_exact(&mut buffer[..chunk.len()])
                .context("partition readback failed; do not automatically retry")?;
            ensure!(
                &buffer[..chunk.len()] == chunk,
                "partition readback mismatch; recovery may be required"
            );
        }
        Ok(())
    }

    pub fn choose_boot_partition(
        kmi: &str,
        is_replace_kernel: bool,
        partition: &Option<String>,
    ) -> String {
        let slot_suffix = get_slot_suffix(false);
        let skip_init_boot = kmi.starts_with("android12-");
        let init_boot_exist =
            Path::new(&format!("/dev/block/by-name/init_boot{slot_suffix}")).exists();

        // if specific partition is specified, use it
        if let Some(part) = partition {
            return match part.as_str() {
                "boot" | "init_boot" | "vendor_boot" => part.clone(),
                _ => "boot".to_string(),
            };
        }

        // if init_boot exists and not skipping it, use it
        if !is_replace_kernel && init_boot_exist && !skip_init_boot {
            return "init_boot".to_string();
        }

        "boot".to_string()
    }

    pub fn get_slot_suffix(ota: bool) -> String {
        let mut slot_suffix = utils::getprop("ro.boot.slot_suffix").unwrap_or_default();
        if !slot_suffix.is_empty() && ota {
            if slot_suffix == "_a" {
                slot_suffix = "_b".to_string();
            } else {
                slot_suffix = "_a".to_string();
            }
        }
        slot_suffix
    }

    pub fn list_available_partitions() -> Vec<String> {
        let slot_suffix = get_slot_suffix(false);
        let candidates = vec!["boot", "init_boot", "vendor_boot"];
        candidates
            .into_iter()
            .filter(|name| Path::new(&format!("/dev/block/by-name/{name}{slot_suffix}")).exists())
            .map(ToString::to_string)
            .collect()
    }

    pub(super) fn auto_boot_partition_path(
        kmi: &str,
        ota: bool,
        is_replace_kernel: bool,
        partition: &Option<String>,
    ) -> PathBuf {
        let slot_suffix = get_slot_suffix(ota);
        let name = choose_boot_partition(kmi, is_replace_kernel, partition);
        PathBuf::from(format!("/dev/block/by-name/{name}{slot_suffix}"))
    }

    pub(super) struct PreparedOta {
        current_slot: String,
        target_slot: i32,
    }

    pub(super) fn prepare_ota() -> Result<PreparedOta> {
        use crate::assets::BOOTCTL_PATH;
        use crate::defs::ADB_DIR;

        println!("- Checking BootControl before OTA flash");

        let hal_info = Command::new(BOOTCTL_PATH)
            .arg("hal-info")
            .output()
            .with_context(|| format!("run {BOOTCTL_PATH} hal-info"))?;
        if !hal_info.status.success() {
            let stderr = String::from_utf8_lossy(&hal_info.stderr);
            let stdout = String::from_utf8_lossy(&hal_info.stdout);
            bail!(
                "bootctl hal-info failed (slot switch aborted): stdout={stdout:?} stderr={stderr:?}"
            );
        }

        let current_slot_out = Command::new(BOOTCTL_PATH)
            .arg("get-current-slot")
            .output()
            .with_context(|| format!("run {BOOTCTL_PATH} get-current-slot"))?;
        if !current_slot_out.status.success() {
            let stderr = String::from_utf8_lossy(&current_slot_out.stderr);
            let stdout = String::from_utf8_lossy(&current_slot_out.stdout);
            bail!("bootctl get-current-slot failed: stdout={stdout:?} stderr={stderr:?}");
        }
        let current_slot = String::from_utf8(current_slot_out.stdout)
            .context("parse bootctl get-current-slot stdout")?
            .trim()
            .to_string();
        ensure!(
            current_slot == "0" || current_slot == "1",
            "unexpected bootctl get-current-slot output: {current_slot:?}"
        );
        let target_slot = i32::from(current_slot == "0");
        let expected_suffix = if target_slot == 0 { "_a" } else { "_b" };
        ensure!(
            get_slot_suffix(true) == expected_suffix,
            "bootctl and system slot mapping disagree"
        );
        ensure!(
            !Path::new(ADB_DIR)
                .join("post-fs-data.d/post_ota.sh")
                .exists(),
            "legacy post_ota.sh exists; resolve it before flashing (early boot-success marking is unsafe)"
        );
        Ok(PreparedOta {
            current_slot,
            target_slot,
        })
    }

    pub(super) fn post_ota(prepared: PreparedOta) -> Result<()> {
        use crate::assets::BOOTCTL_PATH;
        let PreparedOta {
            current_slot,
            target_slot,
        } = prepared;
        let observed = Command::new(BOOTCTL_PATH)
            .arg("get-current-slot")
            .output()?;
        ensure!(
            observed.status.success()
                && String::from_utf8_lossy(&observed.stdout).trim() == current_slot,
            "slot changed after preflight; target partition was patched but activation was refused"
        );
        println!("- Current slot {current_slot} -> active slot {target_slot}");

        // bootctl expects two argv entries: set-active-boot-slot <0|1>
        let set_active = Command::new(BOOTCTL_PATH)
            .args(["set-active-boot-slot", &target_slot.to_string()])
            .output()
            .with_context(|| format!("run {BOOTCTL_PATH} set-active-boot-slot {target_slot}"))?;
        if !set_active.status.success() {
            let stderr = String::from_utf8_lossy(&set_active.stderr);
            let stdout = String::from_utf8_lossy(&set_active.stdout);
            bail!(
                "bootctl set-active-boot-slot {target_slot} failed (partition may already be patched): stdout={stdout:?} stderr={stderr:?}"
            );
        }

        // Android owns successful-boot marking after its normal health checks.
        println!("- Active boot slot switch complete");
        println!("- Next: open System Update and tap Restart (Pixel Virtual A/B)");

        Ok(())
    }
}

#[cfg(target_os = "android")]
pub use android::*;

fn map_file(file: &Path) -> Result<Mmap> {
    let mut f = File::open(file).with_context(|| format!("open {}", file.display()))?;
    let len = f
        .seek(SeekFrom::End(0))
        .with_context(|| format!("seek end of {}", file.display()))? as usize;
    let mmap = unsafe { MmapOptions::new().len(len).map(&f)? };
    Ok(mmap)
}

pub fn parse_kmi(buffer: &[u8]) -> Result<String> {
    let re = Regex::new(r"(\d+\.\d+)(?:\S+)?(android\d+)").context("Failed to compile regex")?;
    buffer
        .windows(4)
        .enumerate()
        .filter(|(_, x)| {
            x[1] == b'.'
                && x[2].is_ascii_digit()
                && match x[0] {
                    b'5' => x[3].is_ascii_digit(),
                    b'6'..=b'9' => true,
                    _ => false,
                }
        })
        .find_map(|(i, _)| {
            let a = &buffer[i..buffer.len().min(i + 100)];
            if let Some(e) = a.iter().position(|c| *c == 0)
                && let Ok(s) = std::str::from_utf8(&a[..e])
                && let Some(caps) = re.captures(s)
                && let (Some(kernel_version), Some(android_version)) = (caps.get(1), caps.get(2))
            {
                Some(format!(
                    "{}-{}",
                    android_version.as_str(),
                    kernel_version.as_str()
                ))
            } else {
                None
            }
        })
        .ok_or_else(|| {
            println!("- Failed to get KMI version");
            anyhow!("Try to choose LKM manually")
        })
}

fn parse_kmi_from_kernel(kernel: &Path) -> Result<String> {
    let data = std::fs::read(kernel).context("Failed to read kernel file")?;
    parse_kmi(&data)
}

fn parse_kmi_from_boot(image: &Path) -> Result<String> {
    let data = map_file(image)?;
    let boot = BootImage::parse(&data)?;
    if let Some(kernel) = boot.get_blocks().get_kernel() {
        let mut output = Vec::<u8>::new();
        kernel.dump(&mut output, false)?;
        parse_kmi(&output)
    } else {
        bail!("no kernel found in boot image")
    }
}

const MAX_KERNEL_SIZE: usize = 64 * 1024 * 1024;

struct BoundedKernel(Vec<u8>);

impl std::io::Write for BoundedKernel {
    fn write(&mut self, bytes: &[u8]) -> std::io::Result<usize> {
        if bytes.len() > MAX_KERNEL_SIZE.saturating_sub(self.0.len()) {
            return Err(std::io::Error::other(
                "decoded kernel exceeds 64 MiB inspection limit",
            ));
        }
        self.0.extend_from_slice(bytes);
        Ok(bytes.len())
    }
    fn flush(&mut self) -> std::io::Result<()> {
        Ok(())
    }
}

fn inspect_boot_target(image: &Path, machine: u16) -> Result<Option<KernelTarget>> {
    let data = map_file(image)?;
    let boot = BootImage::parse(&data)?;
    let Some(kernel) = boot.get_blocks().get_kernel() else {
        return Ok(None);
    };
    let mut output = BoundedKernel(Vec::new());
    kernel
        .dump(&mut output, false)
        .context("decode target kernel")?;
    Ok(Some(inspect_kernel(&output.0, machine)?))
}

/// For vendor boot, prefer the `init_boot` ramdisk entry over the one with empty name,
/// matching the original magiskboot lookup order (init_boot.cpio before ramdisk.cpio).
fn extract_ramdisk(ramdisk_image: &RamdiskImage) -> Result<(Cpio, Option<usize>)> {
    if ramdisk_image.is_vendor_ramdisk() {
        let (pos, target) = ramdisk_image
            .iter_vendor_ramdisk()
            .enumerate()
            .find(|e| e.1.get_name_raw() == b"init_boot")
            .or_else(|| {
                ramdisk_image
                    .iter_vendor_ramdisk()
                    .enumerate()
                    .find(|e| e.1.get_name_raw() == b"")
            })
            .ok_or_else(|| anyhow!("No suitable vendor ramdisk entry found"))?;
        let mut buf = Vec::<u8>::new();
        target.dump(&mut buf, false)?;
        Ok((Cpio::load_from_data(&buf)?, Some(pos)))
    } else {
        let mut buf = Vec::<u8>::new();
        ramdisk_image.dump(&mut buf, false)?;
        Ok((Cpio::load_from_data(&buf)?, None))
    }
}

fn enforce_bootimage_version(boot: &BootImage<'_>) -> Result<()> {
    if let BootImageVersion::Android(ver) = boot.get_header().get_version()
        && ver < 3
    {
        bail!("bootimage version {ver} is not supported!")
    }
    Ok(())
}

#[allow(clippy::struct_excessive_bools)]
#[derive(clap::Args, Debug)]
pub struct BootPatchArgs {
    /// boot image path, if not specified, will try to find the boot image automatically
    #[arg(short, long)]
    pub boot: Option<PathBuf>,

    /// kernel image path to replace
    #[arg(short, long)]
    pub kernel: Option<PathBuf>,

    /// LKM module path to replace, if not specified, will use the builtin one
    #[arg(short, long)]
    pub module: Option<PathBuf>,

    /// init to be replaced
    #[arg(short, long)]
    pub init: Option<PathBuf>,

    /// will use another slot when boot image is not specified
    #[cfg(target_os = "android")]
    #[arg(short = 'u', long, default_value = "false")]
    pub ota: bool,

    /// Flash it to boot partition after patch
    #[cfg(target_os = "android")]
    #[arg(short, long, default_value = "false")]
    pub flash: bool,

    /// Force backup source image as stock image
    #[cfg(target_os = "android")]
    #[arg(long, default_value = "false")]
    pub backup: bool,

    /// Output path. If not specified, will use current directory.
    /// If specified, the boot image will be written to the directory
    /// even if --flash is specified.
    #[cfg(target_os = "android")]
    #[arg(short, long, default_value = None)]
    pub out: Option<PathBuf>,

    /// Output path. If not specified, will use current directory.
    #[cfg(not(target_os = "android"))]
    #[arg(short, long, default_value = None)]
    pub out: Option<PathBuf>,

    /// KMI version, if specified, will use the specified KMI
    #[arg(long, default_value = None)]
    pub kmi: Option<String>,

    /// Matching companion boot for a local init_boot or raw ramdisk target
    #[arg(long)]
    pub target_boot: Option<PathBuf>,

    /// target partition override (init_boot | boot | vendor_boot)
    #[cfg(target_os = "android")]
    #[arg(long, default_value = None)]
    pub partition: Option<String>,

    /// File name of the output. If specified, the boot image will be
    /// written to the output directory even if --flash is specified.
    #[cfg(target_os = "android")]
    #[arg(long, default_value = None)]
    pub out_name: Option<String>,

    /// File name of the output.
    #[cfg(not(target_os = "android"))]
    #[arg(long, default_value = None)]
    pub out_name: Option<String>,

    /// Extra cmdline to append to boot image header
    #[arg(long, default_value = None)]
    pub cmdline: Option<String>,

    #[command(flatten)]
    config: KsuConfigArgs,

    /// Force enable adbd and disable adbd auth
    #[arg(long, default_value = "false")]
    enable_adbd: bool,

    /// Add more adb_debug prop
    #[arg(long, required = false)]
    adb_debug_prop: Option<String>,

    /// Do not (re-)install kernelsu, only modify configs (allow_shell, etc.)
    #[arg(long, default_value = "false")]
    no_install: bool,

    #[cfg(not(target_os = "android"))]
    #[arg(long, default_value = "aarch64")]
    arch: String,

    /// Patching ramdisk instead of boot image. This is used for AVD ramdisk
    #[arg(long, default_value = "false")]
    ramdisk: bool,
}

/// KernelSU module params, stored in ramdisk `/ksu_config` (boot-patch) or the
/// injected capsule (boot-patch-v2).
#[derive(clap::Args, Debug, Default)]
pub struct KsuConfigArgs {
    /// Always allow shell to get root permission
    #[arg(long, default_value = "false")]
    allow_shell: bool,

    /// Do not load custom rc
    #[arg(long, default_value = "false")]
    no_custom_rc: bool,

    /// Do not run module preinit actions
    #[arg(long, default_value = "false")]
    no_preinit: bool,

    /// Force a kernel feature to a value at boot, e.g. `su_compat=0`.
    /// Forced features can not be changed or loaded by userspace. Can be repeated.
    #[arg(long, value_name = "FEATURE=VALUE", value_parser = parse_force_feature)]
    force_feature: Vec<(FeatureId, u64)>,

    /// Block a kernel module from loading at boot by name, e.g.
    /// `oplus_secure_guard`. Comma-separated and/or repeated. Omitting clears it.
    #[arg(long = "block-module", value_name = "NAME", value_delimiter = ',', value_parser = parse_block_module)]
    block_module: Vec<String>,
}

/// Max length of the `block_modules=` value. The kernel copies it into a
/// 256-byte buffer, leaving 255 bytes for the value; anything longer is
/// silently truncated there, so reject it on the host instead.
const MAX_BLOCK_MODULES_LEN: usize = 255;

impl KsuConfigArgs {
    /// Validate options whose full value is only known after joining. Call
    /// before `apply` so the user gets an error instead of the kernel silently
    /// truncating an over-long list (which would leave some modules unblocked).
    pub fn validate(&self) -> Result<()> {
        if !self.block_module.is_empty() {
            let len = self.block_module.join(",").len();
            ensure!(
                len <= MAX_BLOCK_MODULES_LEN,
                "block-module list is too long ({len} bytes, max {MAX_BLOCK_MODULES_LEN}); block fewer modules"
            );
        }
        Ok(())
    }

    /// Set the full state of these options in `config`, other entries are kept.
    pub fn apply(&self, config: &mut Vec<String>) {
        set_config_flag(config, "no custom rc", "norc=1", self.no_custom_rc);
        set_config_flag(config, "allow shell", "allow_shell=1", self.allow_shell);
        set_config_flag(config, "no preinit", "preinit=0", self.no_preinit);

        config.retain(|v| !v.starts_with("force_feature="));
        if !self.force_feature.is_empty() {
            let mut entries = Vec::with_capacity(self.force_feature.len());
            for (feature_id, value) in &self.force_feature {
                println!("- Forcing feature {} to {value}", feature_id.name());
                entries.push(format!("{}:{value}", feature_id.name()));
            }
            config.push(format!("force_feature={}", entries.join(",")));
        }

        config.retain(|v| !v.starts_with("block_modules="));
        if !self.block_module.is_empty() {
            let list = self.block_module.join(",");
            println!("- Blocking modules: {list}");
            config.push(format!("block_modules={list}"));
        }
    }
}

/// Validate a single module name for the `--block-module` option. The kernel
/// `block_modules` buffer holds 255 bytes, so keep names well within that.
fn parse_block_module(s: &str) -> Result<String> {
    ensure!(!s.is_empty(), "module name must not be empty");
    ensure!(
        s.len() <= 64,
        "module name '{s}' is too long (max 64 bytes)"
    );
    ensure!(
        s.bytes().all(|ch| ch.is_ascii_alphanumeric() || ch == b'_'),
        "invalid module name '{s}': only letters, digits and '_' are allowed"
    );
    Ok(s.to_owned())
}

fn set_config_flag(config: &mut Vec<String>, name: &str, value: &str, add: bool) {
    let has_value = config.iter().any(|v| v == value);

    if add {
        println!("- Adding {name} config");
        if !has_value {
            config.push(value.to_owned());
        }
    } else if has_value {
        println!("- Removing {name} config");
        config.retain(|v| v != value);
    }
}

fn parse_force_feature(s: &str) -> Result<(FeatureId, u64)> {
    let (name, value) = s
        .split_once('=')
        .context("expected FEATURE=VALUE, e.g. su_compat=0")?;
    let value = value
        .parse()
        .with_context(|| format!("invalid value for feature {name}: {value}"))?;
    Ok((parse_feature_id(name)?, value))
}

pub fn patch(args: BootPatchArgs) -> Result<()> {
    let inner = move || {
        let BootPatchArgs {
            boot: image,
            init,
            kernel,
            module: kmod,
            out,
            kmi,
            target_boot,
            out_name,
            cmdline,
            config,
            enable_adbd,
            adb_debug_prop,
            no_install,
            #[cfg(target_os = "android")]
            ota,
            #[cfg(target_os = "android")]
            flash,
            #[cfg(target_os = "android")]
            backup,
            #[cfg(target_os = "android")]
            partition,
            #[cfg(not(target_os = "android"))]
            arch,
            ramdisk,
        } = args;

        println!(include_str!("banner"));

        #[cfg(target_os = "android")]
        let _lock = if flash || ota {
            Some(crate::mutation::MutationLock::acquire()?)
        } else {
            None
        };

        #[cfg(target_os = "android")]
        if let Some(partition) = &partition {
            ensure!(
                matches!(partition.as_str(), "boot" | "init_boot" | "vendor_boot"),
                "invalid target partition"
            );
        }

        #[cfg(target_os = "android")]
        let patch_file = image.is_some();

        #[cfg(target_os = "android")]
        if ota {
            ensure!(
                image.is_none() && kernel.is_none() && !ramdisk && !no_install,
                "OTA requires automatic inactive-slot inspection and module installation; local image/kernel/config overrides are unsupported"
            );
        }

        #[cfg(target_os = "android")]
        if !patch_file {
            ensure_gki_kernel()?;
        }

        let is_replace_kernel = kernel.is_some();

        if ramdisk && is_replace_kernel {
            bail!("incompatiable option: --ramdisk and --kernel")
        }

        #[cfg(target_os = "android")]
        if ramdisk && flash {
            bail!("incompatiable option: --ramdisk and --flash")
        }

        if is_replace_kernel {
            ensure!(
                init.is_none() && kmod.is_none(),
                "init and module must not be specified."
            );
        }

        let requested_kmi = kmi;
        let machine = {
            #[cfg(target_os = "android")]
            {
                if cfg!(target_arch = "aarch64") {
                    183
                } else {
                    62
                }
            }
            #[cfg(not(target_os = "android"))]
            {
                match arch.as_str() {
                    "aarch64" => 183,
                    "x86_64" => 62,
                    _ => bail!("unsupported target architecture"),
                }
            }
        };
        let target = if no_install {
            None
        } else if let Some(kernel_path) = &kernel {
            let kernel_data = map_file(kernel_path)?;
            ensure!(
                kernel_data.len() <= MAX_KERNEL_SIZE,
                "replacement kernel exceeds inspection limit"
            );
            Some(inspect_kernel(&kernel_data, machine)?)
        } else if let Some(image_path) = &image {
            let local = if ramdisk {
                None
            } else {
                inspect_boot_target(image_path, machine)?
            };
            Some(match local {
                Some(target) => target,
                None => inspect_boot_target(target_boot.as_deref().context("init_boot/raw ramdisk requires --target-boot; --kmi alone cannot identify page size")?, machine)?
                    .context("companion boot contains no kernel")?,
            })
        } else {
            #[cfg(target_os = "android")]
            {
                if ota {
                    ensure!(
                        ksuinit::kernel_page_size()? == 4096,
                        "16 KiB OTA mode requires independent target validation"
                    );
                    Some(
                        inspect_boot_target(
                            Path::new(&format!("/dev/block/by-name/boot{}", get_slot_suffix(true))),
                            machine,
                        )?
                        .context("inactive boot contains no kernel")?,
                    )
                } else {
                    Some(KernelTarget::new(
                        &rustix::system::uname().release().to_string_lossy(),
                        ksuinit::kernel_page_size()?,
                        machine,
                    )?)
                }
            }
            #[cfg(not(target_os = "android"))]
            {
                bail!("Please specify a boot image");
            }
        };
        if let (Some(requested), Some(target)) = (&requested_kmi, &target) {
            ensure!(
                requested == &target.kmi,
                "explicit KMI disagrees with target kernel"
            );
        }
        #[cfg(target_os = "android")]
        if ota {
            ensure!(
                target.as_ref().is_some_and(|value| value.page_size == 4096),
                "16 KiB OTA target requires independent page-mode validation"
            );
        }
        let kmi = target
            .as_ref()
            .map(|v| v.kmi.clone())
            .or(requested_kmi)
            .map_or_else(
                || -> Result<_> {
                    #[cfg(target_os = "android")]
                    if ota {
                        let slot_suffix = get_slot_suffix(true);
                        println!("- Trying to auto detect KMI version from boot");
                        return parse_kmi_from_boot(Path::new(&format!(
                            "/dev/block/by-name/boot{slot_suffix}"
                        )));
                    }
                    Ok(if ramdisk {
                        bail!("please specify kmi manually")
                    } else if let Some(image_path) = &image {
                        println!(
                            "- Trying to auto detect KMI version for {}",
                            image_path.display()
                        );
                        parse_kmi_from_boot(image_path)?
                    } else if let Some(kernel_path) = &kernel {
                        println!(
                            "- Trying to auto detect KMI version for {}",
                            kernel_path.display()
                        );
                        parse_kmi_from_kernel(kernel_path)?
                    } else {
                        String::new()
                    })
                },
                Ok,
            )?;

        let boot_image_file = if let Some(image) = image {
            ensure!(image.exists(), "boot image not found");
            std::fs::canonicalize(image)?
        } else {
            #[cfg(target_os = "android")]
            {
                auto_boot_partition_path(&kmi, ota, is_replace_kernel, &partition)
            }
            #[cfg(not(target_os = "android"))]
            {
                bail!("Please specify a boot image");
            }
        };

        #[cfg(target_os = "android")]
        println!("- Bootdevice: {}", boot_image_file.display());

        // try extract bootctl/busybox
        #[cfg(target_os = "android")]
        if flash || ota {
            assets::ensure_binaries(false).context("prepare required userspace helpers")?;
        }

        #[cfg(target_os = "android")]
        let prepared_ota = if flash && ota {
            Some(prepare_ota()?)
        } else {
            None
        };

        println!("- Preparing assets");
        println!("- Unpacking boot image");
        let boot_image_data = map_file(&boot_image_file)?;
        let boot_image = if ramdisk {
            BootImage::parse_raw_ramdisk(&boot_image_data)?
        } else {
            BootImage::parse(&boot_image_data)?
        };
        enforce_bootimage_version(&boot_image)?;

        let mut patcher = BootImagePatchOption::new(&boot_image);

        if let Some(cmdline_value) = &cmdline {
            patcher.override_cmdline(cmdline_value.as_bytes());
            println!("- Set cmdline to: {cmdline_value}");
        }

        if let Some(kernel_path) = kernel {
            println!("- Adding Kernel");
            let kernel_data = map_file(&kernel_path)?;
            patcher.replace_kernel(Box::new(Cursor::new(kernel_data)), false);
        }

        let bundled_lkm = (!no_install).then_some(kmod.is_none());
        let kernelsu_ko: Box<dyn AsRef<[u8]>> = if no_install {
            Box::new(Vec::<u8>::new())
        } else if let Some(kmod_path) = kmod {
            Box::new(map_file(&kmod_path)?)
        } else {
            #[cfg(target_os = "android")]
            {
                println!("- KMI: {kmi}");
                let name = format!("{kmi}_kernelsu.ko");
                assets::get_asset(&name).with_context(|| format!("Failed to load {name}"))?
            }
            #[cfg(not(target_os = "android"))]
            {
                println!("- KMI: {kmi}");
                println!("- Arch: {arch}");
                let name = format!("{arch}/{kmi}_kernelsu.ko");
                assets::get_asset(&name).with_context(|| format!("Failed to load {name}"))?
            }
        };

        if let Some(target) = &target {
            validate_module(kernelsu_ko.as_ref().as_ref(), target)
                .context("target/LKM compatibility check failed before patching")?;
            println!(
                "- Target: {} / {} byte kernel pages",
                target.kmi, target.page_size
            );
        }

        let ksu_init: Box<dyn AsRef<[u8]>> = if no_install {
            Box::new(Vec::<u8>::new())
        } else if let Some(init_path) = init {
            Box::new(map_file(&init_path)?)
        } else {
            #[cfg(not(target_os = "android"))]
            {
                assets::get_asset(&format!("{arch}/ksuinit")).context("Failed to load ksuinit")?
            }
            #[cfg(target_os = "android")]
            {
                assets::get_asset("ksuinit").context("Failed to load ksuinit")?
            }
        };

        let (mut cpio, vendor_ramdisk_idx) =
            if let Some(ramdisk_image) = boot_image.get_blocks().get_ramdisk() {
                extract_ramdisk(ramdisk_image)?
            } else {
                println!("- No ramdisk, create by default");
                (Cpio::new(), None)
            };

        if !no_install {
            ensure!(
                !cpio.is_magisk_patched(),
                "Cannot work with Magisk patched image"
            );

            println!("- Adding KernelSU LKM");
            let is_kernelsu_patched = cpio.exists("kernelsu.ko");

            #[cfg(target_os = "android")]
            ensure!(
                !(backup && is_kernelsu_patched),
                "patched input cannot be labeled a stock backup; use a matching stock image"
            );

            if !is_kernelsu_patched && cpio.exists("init") {
                cpio.mv("init", "init.real")?;
            }

            cpio.add("init", CpioEntry::regular(0o755, ksu_init))?;
            cpio.add("kernelsu.ko", CpioEntry::regular(0o755, kernelsu_ko))?;

            #[cfg(target_os = "android")]
            if backup || (!is_kernelsu_patched && flash) {
                do_backup(&mut cpio, &boot_image_file)
                    .context("required backup failed; installation aborted")?;
            }
        }

        let mut ksu_config: Vec<String> = cpio
            .entry_by_name("ksu_config")
            .and_then(CpioEntry::data)
            .and_then(|v| str::from_utf8(v).ok())
            .map(|v| v.split(' ').map(std::borrow::ToOwned::to_owned).collect())
            .unwrap_or_default();

        config.validate()?;
        config.apply(&mut ksu_config);
        if let Some(bundled) = bundled_lkm {
            set_config_flag(&mut ksu_config, "bundled LKM", "bundled=1", bundled);
        }

        if ksu_config.is_empty() {
            cpio.rm("ksu_config", false);
        } else {
            let data = ksu_config.join(" ").into_bytes();
            cpio.add("ksu_config", CpioEntry::regular(0o644, Box::new(data)))?;
        }

        // remove legacy config file
        cpio.rm("allow_shell", false);

        if enable_adbd || adb_debug_prop.is_some() {
            println!("- Adding adb_debug props");
            cpio.add(
                "force_debuggable",
                CpioEntry::regular(0o644, Box::new(Vec::<u8>::new())),
            )?;

            let mut prop = Vec::<u8>::new();
            if enable_adbd {
                println!("- Adding props to enable adbd");
                prop.extend_from_slice(
                    b"ro.debuggable=1\nro.force.debuggable=1\nro.adb.secure=0\n",
                );
            }
            if let Some(extra) = adb_debug_prop {
                println!("- Adding custom props");
                prop.extend_from_slice(extra.as_bytes());
            }
            cpio.add("adb_debug.prop", CpioEntry::regular(0o644, Box::new(prop)))?;
        } else {
            if cpio.exists("force_debuggable") {
                println!("- Removing /force_debuggable");
                cpio.rm("force_debuggable", false);
            }
            if cpio.exists("adb_debug.prop") {
                println!("- Removing /adb_debug.prop");
                cpio.rm("adb_debug.prop", false);
            }
        }

        let mut new_cpio = Vec::<u8>::new();
        cpio.dump(&mut new_cpio)?;

        if let Some(idx) = vendor_ramdisk_idx {
            patcher.replace_vendor_ramdisk(idx, Box::new(Cursor::new(new_cpio)), false);
        } else {
            patcher.replace_ramdisk(Box::new(Cursor::new(new_cpio)), false);
        }

        println!("- Repacking boot image");
        let mut new_boot_buf = Cursor::new(Vec::<u8>::with_capacity(boot_image.get_size()));
        patcher.patch(&mut new_boot_buf)?;
        let new_boot_bytes = new_boot_buf.into_inner();

        // Free the source mmap so the boot partition is no longer mapped read-only,
        // otherwise some kernels reject the subsequent write.
        drop(boot_image);
        drop(boot_image_data);

        #[cfg(target_os = "android")]
        if flash {
            println!("- Flashing new boot image");
            let bootdevice = boot_image_file.display().to_string();
            flash_partition(&bootdevice, &new_boot_bytes)?;
            if let Some(prepared) = prepared_ota {
                post_ota(prepared)?;
            }
        }

        #[cfg(target_os = "android")]
        let should_write_output = patch_file || !flash || out_name.is_some() || out.is_some();
        #[cfg(not(target_os = "android"))]
        let should_write_output = true;

        if should_write_output {
            let output_dir = out.unwrap_or(std::env::current_dir()?);
            let name = out_name.unwrap_or_else(|| {
                let now = chrono::Utc::now();
                format!("kernelsu_patched_{}.img", now.format("%Y%m%d_%H%M%S"))
            });
            let output_image = output_dir.join(name);
            std::fs::write(&output_image, &new_boot_bytes).context("write out new boot failed")?;
            println!("- Output file is written to");
            println!("- {}", output_image.display().to_string().trim_matches('"'));
        }

        println!("- Done!");
        Ok(())
    };

    let result = inner();
    if let Err(ref e) = result {
        println!("- Patch Error: {e}");
    }
    result
}

#[derive(clap::Args, Debug)]
pub struct BootRestoreArgs {
    /// Bind a reviewed restore to the exact current image before any output/write.
    #[cfg(target_os = "android")]
    #[arg(long)]
    pub expected_image_sha256: Option<String>,
    /// boot image path, if not specified, will try to find the boot image automatically
    #[arg(short, long)]
    pub boot: Option<PathBuf>,

    /// Flash it to boot partition after restore
    #[cfg(target_os = "android")]
    #[arg(short, long, default_value = "false")]
    pub flash: bool,

    /// Output path. If not specified, will use current directory.
    /// If specified, the boot image will be written to the directory
    /// even if --flash is specified.
    #[cfg(target_os = "android")]
    #[arg(short, long, default_value = None)]
    pub out: Option<PathBuf>,

    /// Output path. If not specified, will use current directory.
    #[cfg(not(target_os = "android"))]
    #[arg(short, long, default_value = None)]
    pub out: Option<PathBuf>,

    /// File name of the output. If specified, the boot image will be
    /// written to the output directory even if --flash is specified.
    #[cfg(target_os = "android")]
    #[arg(long, default_value = None)]
    pub out_name: Option<String>,

    /// File name of the output.
    #[cfg(not(target_os = "android"))]
    #[arg(long, default_value = None)]
    pub out_name: Option<String>,
}

pub fn restore(args: BootRestoreArgs) -> Result<()> {
    #[cfg(target_os = "android")]
    let _lock = if args.flash {
        Some(crate::mutation::MutationLock::acquire()?)
    } else {
        None
    };
    restore_inner(args)
}

// Internal callers must already hold MutationLock when args.flash is set.
#[cfg(any(target_os = "android", test))]
fn validate_reviewed_image(data: &[u8], expected: Option<&str>) -> Result<()> {
    if let Some(expected) = expected {
        ensure!(
            expected.len() == 64 && expected.bytes().all(|v| v.is_ascii_hexdigit()),
            "Invalid reviewed image hash"
        );
        ensure!(
            sha256::digest(data) == expected,
            "Current image changed; review recovery again"
        );
    }
    Ok(())
}

#[cfg(test)]
#[test]
fn recovery_review_rejects_a_changed_image() {
    let original = b"reviewed boot bytes";
    let expected = sha256::digest(original);
    assert!(validate_reviewed_image(original, Some(&expected)).is_ok());
    assert!(validate_reviewed_image(b"different boot bytes", Some(&expected)).is_err());
    assert!(validate_reviewed_image(original, Some("bad")).is_err());
    assert!(validate_reviewed_image(original, None).is_ok());
}

/// Inspect the exact backup path used by restore, without creating output or flashing.
#[cfg(target_os = "android")]
pub fn recovery_check() -> Result<()> {
    let kmi = get_current_kmi().unwrap_or_default();
    let image = auto_boot_partition_path(&kmi, false, false, &None);
    let data = map_file(&image)?;
    let boot = BootImage::parse(&data)?;
    enforce_bootimage_version(&boot)?;
    let ramdisk = boot
        .get_blocks()
        .get_ramdisk()
        .context("No compatible ramdisk")?;
    let (cpio, _) = extract_ramdisk(ramdisk)?;
    ensure!(
        cpio.exists("kernelsu.ko"),
        "Current image is not KernelSU patched"
    );
    let marker = cpio
        .entry_by_name(BACKUP_FILENAME)
        .context("No stock backup marker")?;
    let bytes = marker.data().unwrap_or_default();
    let digest = std::str::from_utf8(bytes)?.trim();
    ensure!(
        digest.len() == 40 && digest.bytes().all(|v| v.is_ascii_hexdigit()),
        "Invalid recovery marker"
    );
    let backup = PathBuf::from(KSU_BACKUP_DIR).join(format!("{KSU_BACKUP_FILE_PREFIX}{digest}"));
    let metadata = std::fs::symlink_metadata(&backup).context("Matching backup is unavailable")?;
    ensure!(
        metadata.is_file() && metadata.len() > 0 && metadata.len() <= 512 * 1024 * 1024,
        "Unreadable or oversized backup"
    );
    ensure!(calculate_sha1(&backup)? == digest, "Backup digest mismatch");
    println!(
        "{}",
        serde_json::json!({
            "image": image, "image_sha256": sha256::digest(&data[..]),
            "backup": backup, "backup_sha1": digest, "bytes": metadata.len(),
            "slot": get_slot_suffix(false)
        })
    );
    Ok(())
}

pub fn restore_inner(args: BootRestoreArgs) -> Result<()> {
    let BootRestoreArgs {
        #[cfg(target_os = "android")]
        expected_image_sha256,
        boot: image,
        out_name,
        out,
        #[cfg(target_os = "android")]
        flash,
    } = args;

    #[cfg(target_os = "android")]
    let kmi = get_current_kmi().unwrap_or_default();

    #[cfg(target_os = "android")]
    let image_supplied = image.is_some();

    let boot_image_file = if let Some(image) = image {
        ensure!(image.exists(), "boot image not found");
        std::fs::canonicalize(image)?
    } else {
        #[cfg(target_os = "android")]
        {
            auto_boot_partition_path(&kmi, false, false, &None)
        }
        #[cfg(not(target_os = "android"))]
        {
            bail!("Please specify a boot image");
        }
    };

    #[cfg(target_os = "android")]
    println!("- Bootdevice: {}", boot_image_file.display());

    println!("- Unpacking boot image");
    let bootimage_data = map_file(&boot_image_file)?;
    #[cfg(target_os = "android")]
    validate_reviewed_image(&bootimage_data, expected_image_sha256.as_deref())?;
    let boot_image = BootImage::parse(&bootimage_data)?;
    enforce_bootimage_version(&boot_image)?;

    let (mut cpio, vendor_ramdisk_idx) =
        if let Some(ramdisk_image) = boot_image.get_blocks().get_ramdisk() {
            extract_ramdisk(ramdisk_image)?
        } else {
            bail!("No compatible ramdisk found.")
        };

    ensure!(
        cpio.exists("kernelsu.ko"),
        "boot image is not patched by KernelSU"
    );

    #[cfg(target_os = "android")]
    let mut stock_boot: Option<PathBuf> = None;

    #[cfg(target_os = "android")]
    if let Some(backup_file) = cpio.entry_by_name(BACKUP_FILENAME) {
        let sha = String::from_utf8(backup_file.data().unwrap_or_default().to_vec())?;
        let sha = sha.trim();
        ensure!(
            sha.len() == 40 && sha.bytes().all(|v| v.is_ascii_hexdigit()),
            "invalid recovery marker"
        );
        let backup_path =
            PathBuf::from(KSU_BACKUP_DIR).join(format!("{KSU_BACKUP_FILE_PREFIX}{sha}"));
        if backup_path.is_file() {
            println!("- Using backup file {}", backup_path.display());
            ensure!(
                calculate_sha1(&backup_path)? == sha,
                "backup digest mismatch; restore aborted before writing"
            );
            stock_boot = Some(backup_path);
        } else {
            println!("- Warning: no backup {} found!", backup_path.display());
        }
    } else {
        println!("- Backup info is absent!");
    }

    #[cfg(target_os = "android")]
    let mut stock_source: Option<PathBuf> = None;

    let new_boot_bytes: Vec<u8> = {
        #[cfg(target_os = "android")]
        {
            if let Some(stock_path) = stock_boot {
                let bytes = std::fs::read(&stock_path)
                    .with_context(|| format!("read stock boot {}", stock_path.display()))?;
                stock_source = Some(stock_path);
                bytes
            } else {
                rebuild_without_ksu(&boot_image, &mut cpio, vendor_ramdisk_idx)?
            }
        }
        #[cfg(not(target_os = "android"))]
        {
            rebuild_without_ksu(&boot_image, &mut cpio, vendor_ramdisk_idx)?
        }
    };

    drop(boot_image);
    drop(bootimage_data);

    #[cfg(target_os = "android")]
    if flash {
        if let Some(ref source) = stock_source {
            println!("- Flashing new boot image from {}", source.display());
        } else {
            println!("- Flashing new boot image");
        }
        let bootdevice = boot_image_file.display().to_string();
        flash_partition(&bootdevice, &new_boot_bytes)?;
    }

    #[cfg(target_os = "android")]
    let should_write_output = image_supplied || !flash || out_name.is_some() || out.is_some();
    #[cfg(not(target_os = "android"))]
    let should_write_output = true;

    if should_write_output {
        let output_dir = out.unwrap_or(std::env::current_dir()?);
        let name = out_name.unwrap_or_else(|| {
            let now = chrono::Utc::now();
            format!("kernelsu_restore_{}.img", now.format("%Y%m%d_%H%M%S"))
        });
        let output_image = output_dir.join(name);
        std::fs::write(&output_image, &new_boot_bytes).context("copy out new boot failed")?;
        println!("- Output file is written to");
        println!("- {}", output_image.display().to_string().trim_matches('"'));
    }

    println!("- Done!");
    Ok(())
}

fn rebuild_without_ksu(
    boot_image: &BootImage<'_>,
    cpio: &mut Cpio,
    vendor_ramdisk_idx: Option<usize>,
) -> Result<Vec<u8>> {
    println!("- Removing KernelSU from boot image");
    cpio.rm("kernelsu.ko", false);
    if cpio.exists("init.real") {
        cpio.mv("init.real", "init")?;
    }

    let mut new_cpio = Vec::<u8>::new();
    cpio.dump(&mut new_cpio)?;

    println!("- Repacking boot image");
    let mut patcher = BootImagePatchOption::new(boot_image);
    if let Some(idx) = vendor_ramdisk_idx {
        patcher.replace_vendor_ramdisk(idx, Box::new(Cursor::new(new_cpio)), false);
    } else {
        patcher.replace_ramdisk(Box::new(Cursor::new(new_cpio)), false);
    }

    let mut buf = Cursor::new(Vec::<u8>::with_capacity(boot_image.get_size()));
    patcher.patch(&mut buf)?;
    Ok(buf.into_inner())
}

#[cfg(test)]
mod block_modules_tests {
    use super::*;

    #[test]
    fn parse_block_module_accepts_valid_names() {
        assert_eq!(
            parse_block_module("oplus_secure_guard").unwrap(),
            "oplus_secure_guard"
        );
        assert_eq!(parse_block_module("vr").unwrap(), "vr");
        assert_eq!(parse_block_module("mkp0").unwrap(), "mkp0");
    }

    #[test]
    fn parse_block_module_rejects_invalid_names() {
        assert!(parse_block_module("").is_err());
        assert!(parse_block_module("bad-name").is_err());
        assert!(parse_block_module("a b").is_err());
        assert!(parse_block_module(&"a".repeat(65)).is_err());
    }

    #[test]
    fn apply_writes_and_clears_block_modules() {
        let args = KsuConfigArgs {
            block_module: vec!["vr".to_owned(), "vklp".to_owned()],
            ..Default::default()
        };
        // Unrelated entries are preserved; apply() only owns the keys it manages.
        let mut config = vec!["bundled=1".to_owned()];
        args.apply(&mut config);
        assert!(config.contains(&"block_modules=vr,vklp".to_owned()));
        assert!(config.contains(&"bundled=1".to_owned()));

        // Omitting the option removes a previously written entry.
        let empty = KsuConfigArgs::default();
        empty.apply(&mut config);
        assert!(!config.iter().any(|v| v.starts_with("block_modules=")));
    }

    #[test]
    fn apply_replaces_existing_block_modules() {
        let args = KsuConfigArgs {
            block_module: vec!["vr".to_owned()],
            ..Default::default()
        };
        let mut config = vec!["block_modules=old,stale".to_owned()];
        args.apply(&mut config);
        assert_eq!(
            config
                .iter()
                .filter(|v| v.starts_with("block_modules="))
                .count(),
            1
        );
        assert!(config.contains(&"block_modules=vr".to_owned()));
    }

    #[test]
    fn apply_keeps_sibling_keys_with_shared_prefix() {
        // A key that merely starts with "block_modules" but is not the exact
        // "block_modules=" entry must not be dropped by retain().
        let args = KsuConfigArgs {
            block_module: vec!["vr".to_owned()],
            ..Default::default()
        };
        let mut config = vec!["block_modules_foo=1".to_owned()];
        args.apply(&mut config);
        assert!(config.contains(&"block_modules_foo=1".to_owned()));
        assert!(config.contains(&"block_modules=vr".to_owned()));
    }

    #[test]
    fn validate_rejects_overlong_list() {
        // Many valid names whose joined length exceeds the kernel's 255-byte
        // value budget must be rejected rather than silently truncated.
        let names: Vec<String> = (0..30).map(|i| format!("module_name_{i:03}")).collect();
        assert!(names.join(",").len() > MAX_BLOCK_MODULES_LEN);
        let args = KsuConfigArgs {
            block_module: names,
            ..Default::default()
        };
        assert!(args.validate().is_err());

        // A short list is accepted.
        let ok = KsuConfigArgs {
            block_module: vec!["vr".to_owned(), "mkp".to_owned()],
            ..Default::default()
        };
        assert!(ok.validate().is_ok());
        assert!(KsuConfigArgs::default().validate().is_ok());
    }
}
