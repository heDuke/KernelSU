use anyhow::{Context, Result, bail};
use goblin::elf::Elf;
use rustix::system::init_module;
use std::ffi::CStr;
use std::fs::{self, File, OpenOptions};
use std::io::{BufRead, BufReader, ErrorKind, Read, Seek, SeekFrom};
use std::os::unix::fs::OpenOptionsExt;

mod loader;

/// Kernel page size from proc auxv, rather than bionic's compatibility sysconf.
pub fn kernel_page_size() -> Result<u32> {
    let mut bytes = Vec::new();
    File::open("/proc/self/auxv")?
        .take(4097)
        .read_to_end(&mut bytes)?;
    Ok(ksu_contracts::page_size_from_auxv(&bytes)?)
}

struct Kptr {
    value: Option<String>,
}

impl Kptr {
    pub fn new() -> Result<Self> {
        let value = fs::read_to_string("/proc/sys/kernel/kptr_restrict")?;
        let value = if value.trim() == "2" {
            fs::write("/proc/sys/kernel/kptr_restrict", "1")?;
            Some(value)
        } else {
            None
        };
        Ok(Kptr { value })
    }
}

impl Drop for Kptr {
    fn drop(&mut self) {
        if let Some(value) = &self.value
            && let Err(error) = fs::write("/proc/sys/kernel/kptr_restrict", value.as_bytes())
        {
            log::error!("Cannot restore kptr_restrict: {error}");
        }
    }
}

pub struct KptrOwnedIter<I> {
    _kptr: Kptr,
    iter: I,
}

impl<I: Iterator> Iterator for KptrOwnedIter<I> {
    type Item = I::Item;

    fn next(&mut self) -> Option<Self::Item> {
        self.iter.next()
    }
}

pub fn kernel_symbols_iter() -> Result<impl Iterator<Item = (String, u64)>> {
    Ok(checked_kernel_symbols_iter()?.map_while(|item| match item {
        Ok(symbol) => Some(symbol),
        Err(error) => {
            log::error!("Cannot read kernel symbols: {error:#}");
            None
        }
    }))
}

fn checked_kernel_symbols_iter() -> Result<impl Iterator<Item = Result<(String, u64)>>> {
    let kptr = Kptr::new()?;

    let iter = BufReader::new(File::open("/proc/kallsyms")?)
        .lines()
        .enumerate()
        .filter_map(|(index, line)| {
            match line
                .context("Cannot read kallsyms line")
                .and_then(|line| loader::parse_symbol(&line))
                .with_context(|| format!("kallsyms line {}", index + 1))
            {
                Ok(Some(symbol)) => Some(Ok(symbol)),
                Ok(None) => None,
                Err(error) => Some(Err(error)),
            }
        });

    Ok(KptrOwnedIter { _kptr: kptr, iter })
}

pub fn for_each_kernel_symbols<F: FnMut(&(String, u64)) -> Result<bool>>(mut f: F) -> Result<()> {
    for item in checked_kernel_symbols_iter()? {
        if !f(&item?)? {
            break;
        }
    }
    Ok(())
}

const O_NONBLOCK: i32 = 0x800;

fn open_kmsg_at_end() -> Result<File> {
    let mut last_error = None;

    for path in ["/dev/kmsg", "/kmsg"] {
        match OpenOptions::new()
            .read(true)
            .custom_flags(O_NONBLOCK)
            .open(path)
        {
            Ok(mut file) => {
                file.seek(SeekFrom::End(0))
                    .with_context(|| format!("Cannot seek {path} to end"))?;

                log::info!("Reading kernel log from {path}");
                return Ok(file);
            }
            Err(error) => {
                last_error = Some((path, error));
            }
        }
    }

    match last_error {
        Some((path, error)) => {
            Err(error).with_context(|| format!("Cannot open kernel log device, last tried {path}"))
        }
        None => bail!("No kernel log device candidate"),
    }
}

fn read_new_kmsg(file: &mut File) -> Result<String> {
    read_kernel_log(file)
}

fn read_kernel_log(reader: &mut impl Read) -> Result<String> {
    const LIMIT: usize = 64 * 1024;
    let mut output = Vec::new();
    let mut record = [0u8; 8192];

    for _ in 0..256 {
        let remaining = LIMIT - output.len();
        if remaining <= 1 {
            break;
        }
        let capacity = record.len().min(remaining - 1);
        match reader.read(&mut record[..capacity]) {
            Ok(0) => break,
            Ok(length) => {
                output.extend_from_slice(&record[..length]);
                output.push(b'\n');
            }
            Err(error) if error.kind() == ErrorKind::WouldBlock => break,
            Err(error)
                if error.kind() == ErrorKind::Interrupted || error.raw_os_error() == Some(32) =>
            {
                continue;
            }
            Err(error) => return Err(error).context("Cannot read /dev/kmsg"),
        }
    }

    Ok(String::from_utf8_lossy(&output).into_owned())
}

fn extract_required_vermagic(kmsg: &str, module_name: &str, original: &str) -> Option<String> {
    const PREFIX: &str = "version magic '";
    const SEPARATOR: &str = "' should be '";

    for record in kmsg.lines().rev() {
        let message = record
            .split_once(';')
            .map(|(_, message)| message)
            .unwrap_or(record);

        let Some(prefix_position) = message.find(PREFIX) else {
            continue;
        };
        if module_name.is_empty()
            || !message[..prefix_position].ends_with(&format!("{module_name}: "))
        {
            continue;
        }
        let after_prefix = &message[prefix_position + PREFIX.len()..];

        let Some(separator_position) = after_prefix.find(SEPARATOR) else {
            continue;
        };
        if &after_prefix[..separator_position] != original {
            continue;
        }
        let required = &after_prefix[separator_position + SEPARATOR.len()..];

        let Some(end_quote) = required.find('\'') else {
            continue;
        };
        let required = &required[..end_quote];

        if !required.is_empty() {
            return Some(required.to_owned());
        }
    }

    None
}

fn align_up(value: usize, alignment: usize) -> Result<usize> {
    let alignment = alignment.max(1);

    if !alignment.is_power_of_two() {
        bail!("Invalid ELF alignment: {alignment}");
    }

    value
        .checked_add(alignment - 1)
        .map(|value| value & !(alignment - 1))
        .context("ELF alignment overflow")
}

fn write_elf64_word(
    buffer: &mut [u8],
    offset: usize,
    value: u64,
    little_endian: bool,
) -> Result<()> {
    let end = offset.checked_add(8).context("ELF write overflow")?;
    let destination = buffer
        .get_mut(offset..end)
        .context("ELF write outside module buffer")?;

    let bytes = if little_endian {
        value.to_le_bytes()
    } else {
        value.to_be_bytes()
    };
    destination.copy_from_slice(&bytes);
    Ok(())
}

fn replace_module_vermagic(buffer: &mut Vec<u8>, required_vermagic: &str) -> Result<()> {
    struct ModinfoLocation {
        offset: usize,
        size: usize,
        section_header_offset: usize,
        alignment: usize,
        little_endian: bool,
    }

    let location = {
        let elf = Elf::parse(buffer)?;

        if !elf.is_64 {
            bail!("Only ELF64 modules are supported");
        }

        let section_table_offset =
            usize::try_from(elf.header.e_shoff).context("Section table offset overflow")?;
        let section_entry_size = usize::from(elf.header.e_shentsize);
        let mut location = None;

        for (index, section) in elf.section_headers.iter().enumerate() {
            let Some(name) = elf.shdr_strtab.get_at(section.sh_name) else {
                continue;
            };
            if name != ".modinfo" {
                continue;
            }

            let offset = usize::try_from(section.sh_offset).context(".modinfo offset overflow")?;
            let size = usize::try_from(section.sh_size).context(".modinfo size overflow")?;
            let end = offset
                .checked_add(size)
                .context(".modinfo range overflow")?;

            if end > buffer.len() {
                bail!(".modinfo is outside module buffer");
            }

            let section_header_offset = section_table_offset
                .checked_add(
                    index
                        .checked_mul(section_entry_size)
                        .context("Section index overflow")?,
                )
                .context("Section header offset overflow")?;

            location = Some(ModinfoLocation {
                offset,
                size,
                section_header_offset,
                alignment: usize::try_from(section.sh_addralign).unwrap_or(1).max(1),
                little_endian: elf.little_endian,
            });
            break;
        }

        location.context("Module has no .modinfo section")?
    };

    let old_modinfo = &buffer[location.offset..location.offset + location.size];
    let replacement = format!("vermagic={required_vermagic}");
    let mut new_modinfo = Vec::with_capacity(old_modinfo.len().max(replacement.len() + 1));
    let mut replaced = false;

    for entry in old_modinfo.split(|byte| *byte == 0) {
        if entry.is_empty() {
            continue;
        }

        if entry.starts_with(b"vermagic=") {
            if !replaced {
                new_modinfo.extend_from_slice(replacement.as_bytes());
                new_modinfo.push(0);
                replaced = true;
            }
        } else {
            new_modinfo.extend_from_slice(entry);
            new_modinfo.push(0);
        }
    }

    if !replaced {
        new_modinfo.extend_from_slice(replacement.as_bytes());
        new_modinfo.push(0);
    }

    let new_offset = align_up(buffer.len(), location.alignment)?;
    buffer.resize(new_offset, 0);
    buffer.extend_from_slice(&new_modinfo);

    // Elf64_Shdr: sh_offset at +0x18, sh_size at +0x20.
    write_elf64_word(
        buffer,
        location.section_header_offset + 0x18,
        new_offset as u64,
        location.little_endian,
    )?;
    write_elf64_word(
        buffer,
        location.section_header_offset + 0x20,
        new_modinfo.len() as u64,
        location.little_endian,
    )?;

    log::warn!(
        "Replaced module vermagic with kernel-required value: {:?}",
        required_vermagic
    );
    Ok(())
}

/// Resolve requested built-in symbols without accepting ambiguous or hidden addresses.
pub fn resolve_kallsyms(names: &[&str]) -> Result<Vec<Option<u64>>> {
    let mut found = std::collections::BTreeMap::new();
    for entry in checked_kernel_symbols_iter()? {
        let (name, address) = entry?;
        if names.contains(&name.as_str()) {
            anyhow::ensure!(address != 0, "Hidden symbol {name}");
            if let Some(previous) = found.insert(name.clone(), address) {
                anyhow::ensure!(previous == address, "Ambiguous symbol {name}");
            }
        }
    }
    Ok(names.iter().map(|name| found.get(*name).copied()).collect())
}

pub fn load_module(data: &[u8], params: &CStr) -> Result<()> {
    load_preflighted(data, params, checked_kernel_symbols_iter)
}

/// Preserve Husky target/signature preflight for the upstream resolver entry point.
pub fn load_module_with_resolver<F>(data: &[u8], params: &CStr, resolver: F) -> Result<()>
where
    F: FnOnce(&[&str]) -> Result<Vec<Option<u64>>>,
{
    load_preflighted(data, params, || {
        let elf = Elf::parse(data)?;
        let names: std::collections::BTreeSet<_> = elf
            .syms
            .iter()
            .filter(|sym| sym.st_shndx == goblin::elf::section_header::SHN_UNDEF as usize)
            .filter_map(|sym| elf.strtab.get_at(sym.st_name))
            .filter(|name| !name.is_empty())
            .collect();
        let names: Vec<_> = names.into_iter().collect();
        let addresses = resolver(&names)?;
        anyhow::ensure!(
            addresses.len() == names.len(),
            "Resolver result count mismatch"
        );
        Ok(names
            .into_iter()
            .zip(addresses)
            .filter_map(|(name, address)| address.map(|address| Ok((name.to_owned(), address))))
            .collect::<Vec<_>>()
            .into_iter())
    })
}

/// Relocate undefined symbols in an ELF kernel module buffer using /proc/kallsyms,
/// then load it via init_module syscall.
fn load_preflighted<I: Iterator<Item = Result<(String, u64)>>>(
    data: &[u8],
    params: &CStr,
    symbols: impl FnOnce() -> Result<I>,
) -> Result<()> {
    let machine = if cfg!(target_arch = "aarch64") {
        183
    } else {
        62
    };
    let target = ksu_contracts::KernelTarget::new(
        &rustix::system::uname().release().to_string_lossy(),
        kernel_page_size()?,
        machine,
    )?;
    ksu_contracts::validate_module(data, &target).context("module compatibility check")?;
    let signed = loader::is_signed(data);
    let mut buffer = if signed {
        log::info!("Signed module: preserving bytes for kernel signature and symbol checks");
        data.to_vec()
    } else {
        loader::prepare(data, symbols).context("Module symbol preflight failed")?
    };

    let mut kmsg = match open_kmsg_at_end() {
        Ok(file) => Some(file),
        Err(error) => {
            log::warn!("Cannot prepare kmsg fallback: {error:#}");
            None
        }
    };

    match init_module(&buffer, params) {
        Ok(()) => Ok(()),
        Err(first_error) => {
            let logs = match kmsg.as_mut() {
                Some(file) => read_new_kmsg(file).unwrap_or_else(|error| {
                    log::warn!("Cannot read module diagnostics: {error:#}");
                    String::new()
                }),
                None => String::new(),
            };

            let (_, info) = ksu_contracts::module_info(data)?;
            let original = info
                .split(|byte| *byte == 0)
                .find_map(|entry| entry.strip_prefix(b"vermagic="))
                .and_then(|value| std::str::from_utf8(value).ok())
                .unwrap_or_default();
            let name = info
                .split(|byte| *byte == 0)
                .find_map(|entry| entry.strip_prefix(b"name="))
                .and_then(|value| std::str::from_utf8(value).ok())
                .unwrap_or_default();
            let required = if !signed && first_error == rustix::io::Errno::NOEXEC {
                extract_required_vermagic(&logs, name, original)
            } else {
                None
            };
            let Some(required_vermagic) = required else {
                log::error!("Kernel module loading log:\n{}", logs);
                return Err(first_error).with_context(|| {
                    format!(
                        "init_module failed: {}; no forced loading was attempted",
                        load_error_hint(first_error)
                    )
                });
            };
            ksu_contracts::validate_vermagic_retry(info, &required_vermagic)
                .context("unsafe vermagic retry refused")?;

            log::warn!(
                "Kernel requires vermagic {:?}; replacing and retrying",
                required_vermagic
            );

            replace_module_vermagic(&mut buffer, &required_vermagic)
                .context("Cannot replace module vermagic")?;

            init_module(&buffer, params).context("init_module failed after replacing vermagic")?;
            Ok(())
        }
    }
}

fn load_error_hint(error: rustix::io::Errno) -> &'static str {
    match error.raw_os_error() {
        8 | 22 => "check ELF, vermagic, module_layout/CRC and relocation diagnostics",
        1 | 13 => "check privilege, kernel security policy and protected-symbol diagnostics",
        126 | 129 => "check module signing key and signature requirements",
        17 => "a module with this name is already loaded",
        2 => "a required kernel symbol or dependency is unavailable",
        _ => "inspect kernel diagnostics for the exact failure",
    }
}

#[cfg(test)]
mod loader_diagnostics_tests {
    use super::*;

    #[test]
    fn ignores_unrelated_vermagic_errors() {
        let logs = "other: version magic 'ours ' should be 'wrong'\nkernelsu: version magic 'ours ' should be 'ours'";
        assert_eq!(
            extract_required_vermagic(logs, "kernelsu", "ours "),
            Some("ours".into())
        );
        assert_eq!(extract_required_vermagic(logs, "kernelsu", "absent"), None);
        assert_eq!(extract_required_vermagic(logs, "absent", "ours "), None);
    }

    #[test]
    fn diagnostic_reader_is_bounded_even_when_logs_never_end() {
        let mut continuous = std::io::repeat(b'x');
        let result = read_kernel_log(&mut continuous).unwrap();
        assert!(result.len() <= 64 * 1024);
        assert!(!result.is_empty());
    }
}

fn has_kernelsu_legacy() -> bool {
    use syscalls::{Sysno, syscall};
    let mut version = 0;
    const CMD_GET_VERSION: i32 = 2;
    unsafe {
        let _ = syscall!(
            Sysno::prctl,
            0xDEADBEEF,
            CMD_GET_VERSION,
            std::ptr::addr_of_mut!(version)
        );
    }

    log::info!("KernelSU version: {}", version);

    version != 0
}

fn has_kernelsu_v2() -> bool {
    use syscalls::{Sysno, syscall};
    const KSU_INSTALL_MAGIC1: u32 = 0xDEADBEEF;
    const KSU_INSTALL_MAGIC2: u32 = 0xCAFEBABE;
    const KSU_IOCTL_GET_INFO: u32 = 0x80104b02; // _IOR('K', 2, struct ksu_get_info_cmd)
    const KSU_IOCTL_GET_INFO_LEGACY: u32 = 0x80004b02; // _IOC(_IOC_READ, 'K', 2, 0)

    #[repr(C)]
    #[derive(Default)]
    struct GetInfoCmd {
        version: u32,
        flags: u32,
        features: u32,
        uapi_version: u32,
    }

    #[repr(C)]
    #[derive(Default)]
    struct GetInfoLegacyCmd {
        version: u32,
        flags: u32,
        features: u32,
    }

    // Try new method: get driver fd using reboot syscall with magic numbers
    let mut fd: i32 = -1;
    unsafe {
        let _ = syscall!(
            Sysno::reboot,
            KSU_INSTALL_MAGIC1,
            KSU_INSTALL_MAGIC2,
            0,
            std::ptr::addr_of_mut!(fd)
        );
    }

    let version = if fd >= 0 {
        // New method: try to get version info via ioctl
        let mut cmd = GetInfoCmd::default();
        let version = unsafe {
            let ret = syscall!(Sysno::ioctl, fd, KSU_IOCTL_GET_INFO, &mut cmd as *mut _);

            match ret {
                Ok(_) => cmd.version,
                Err(_) => {
                    let mut cmd = GetInfoLegacyCmd::default();
                    match syscall!(
                        Sysno::ioctl,
                        fd,
                        KSU_IOCTL_GET_INFO_LEGACY,
                        &mut cmd as *mut _
                    ) {
                        Ok(_) => cmd.version,
                        Err(_) => 0,
                    }
                }
            }
        };

        unsafe {
            let _ = syscall!(Sysno::close, fd);
        }

        version
    } else {
        0
    };

    log::info!("KernelSU version: {}", version);

    version != 0
}

pub fn has_kernelsu() -> bool {
    has_kernelsu_v2() || has_kernelsu_legacy()
}
