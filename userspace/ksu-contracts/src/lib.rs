//! Host-compatible target inspection and module compatibility checks.
//! These checks establish necessary ABI conditions, not device support.

use std::fmt;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct KernelTarget {
    pub release: String,
    pub kmi: String,
    pub page_size: u32,
    pub machine: u16,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CompatibilityError(pub String);

impl fmt::Display for CompatibilityError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        self.0.fmt(f)
    }
}

impl std::error::Error for CompatibilityError {}

type Result<T> = std::result::Result<T, CompatibilityError>;

/// Decode the kernel's 64-bit little-endian auxv, unaffected by libc page emulation.
pub fn page_size_from_auxv(bytes: &[u8]) -> Result<u32> {
    if bytes.len() > 4096 || !bytes.len().is_multiple_of(16) {
        return Err(invalid("invalid kernel auxv size"));
    }
    let mut page_size = None;
    for entry in bytes.as_chunks::<16>().0 {
        let key = u64::from_le_bytes(entry[..8].try_into().unwrap());
        let value = u64::from_le_bytes(entry[8..].try_into().unwrap());
        if key == 0 {
            return page_size.ok_or_else(|| invalid("kernel auxv has no AT_PAGESZ"));
        }
        if key == 6 {
            if page_size.is_some() || !matches!(value, 4096 | 16384 | 65536) {
                return Err(invalid("ambiguous/unsupported AT_PAGESZ"));
            }
            page_size = Some(u32::try_from(value).unwrap());
        }
    }
    Err(invalid("kernel auxv is unterminated"))
}

fn invalid(message: &str) -> CompatibilityError {
    CompatibilityError(message.into())
}

pub fn parse_kmi(release: &str) -> Result<String> {
    let version = release
        .split('-')
        .next()
        .ok_or_else(|| invalid("missing version"))?;
    let mut parts = version.split('.');
    let major: u32 = parts
        .next()
        .and_then(|v| v.parse().ok())
        .ok_or_else(|| invalid("invalid major"))?;
    let minor: u32 = parts
        .next()
        .and_then(|v| v.parse().ok())
        .ok_or_else(|| invalid("invalid minor"))?;
    let patch = parts.next().ok_or_else(|| invalid("missing patch"))?;
    if !patch.chars().take_while(char::is_ascii_digit).any(|_| true)
        || !((major == 5 && minor >= 10) || major >= 6)
    {
        return Err(invalid("unsupported kernel family"));
    }
    let android = release
        .split('-')
        .find(|part| {
            part.strip_prefix("android")
                .is_some_and(|v| !v.is_empty() && v.bytes().all(|b| b.is_ascii_digit()))
        })
        .ok_or_else(|| invalid("kernel has no Android KMI identity"))?;
    Ok(format!("{android}-{major}.{minor}"))
}

impl KernelTarget {
    pub fn new(release: &str, page_size: u32, machine: u16) -> Result<Self> {
        if !matches!(
            (machine, page_size),
            (183, 4096 | 16384 | 65536) | (62, 4096)
        ) {
            return Err(invalid("unknown kernel architecture/page-size combination"));
        }
        Ok(Self {
            release: release.into(),
            kmi: parse_kmi(release)?,
            page_size,
            machine,
        })
    }
}

/// Inspect a fully decoded kernel, never a boot-header alignment or compressed banner.
pub fn inspect_kernel(data: &[u8], machine: u16) -> Result<KernelTarget> {
    let page_size = if machine == 183 {
        if data.len() < 64 || data.get(56..60) != Some(b"ARM\x64") {
            return Err(invalid("missing ARM64 Image header"));
        }
        let flags = u64::from_le_bytes(data[24..32].try_into().unwrap());
        match (flags >> 1) & 3 {
            1 => 4096,
            2 => 16384,
            3 => 65536,
            _ => return Err(invalid("ARM64 kernel page size is unspecified")),
        }
    } else if machine == 62 {
        4096
    } else {
        return Err(invalid("unsupported architecture"));
    };
    let prefix = b"Linux version ";
    let offset = data
        .windows(prefix.len())
        .position(|v| v == prefix)
        .ok_or_else(|| invalid("kernel release banner missing"))?
        + prefix.len();
    let bytes = &data[offset..];
    let end = bytes
        .iter()
        .position(|v| v.is_ascii_whitespace() || *v == 0)
        .ok_or_else(|| invalid("truncated kernel release banner"))?;
    if end > 256 {
        return Err(invalid("kernel release exceeds bound"));
    }
    let release =
        std::str::from_utf8(&bytes[..end]).map_err(|_| invalid("invalid kernel release"))?;
    KernelTarget::new(release, page_size, machine)
}

fn word(data: &[u8], offset: usize, size: usize) -> Result<u64> {
    let bytes = data
        .get(
            offset
                ..offset
                    .checked_add(size)
                    .ok_or_else(|| invalid("ELF offset overflow"))?,
        )
        .ok_or_else(|| invalid("truncated ELF"))?;
    let mut value = [0; 8];
    value[..size].copy_from_slice(bytes);
    Ok(u64::from_le_bytes(value))
}

fn index(value: u64) -> Result<usize> {
    usize::try_from(value).map_err(|_| invalid("ELF range overflow"))
}

fn section(data: &[u8], table: usize, stride: usize, number: usize) -> Result<usize> {
    let position = table
        .checked_add(
            stride
                .checked_mul(number)
                .ok_or_else(|| invalid("ELF section overflow"))?,
        )
        .ok_or_else(|| invalid("ELF section overflow"))?;
    data.get(
        position
            ..position
                .checked_add(64)
                .ok_or_else(|| invalid("ELF section overflow"))?,
    )
    .ok_or_else(|| invalid("truncated ELF section"))?;
    Ok(position)
}

fn section_data(data: &[u8], header: usize) -> Result<&[u8]> {
    let start = index(word(data, header + 24, 8)?)?;
    let size = index(word(data, header + 32, 8)?)?;
    data.get(
        start
            ..start
                .checked_add(size)
                .ok_or_else(|| invalid("ELF data overflow"))?,
    )
    .ok_or_else(|| invalid("truncated ELF section data"))
}

pub fn module_info(data: &[u8]) -> Result<(u16, &[u8])> {
    if data.get(..6) != Some(b"\x7fELF\x02\x01") || word(data, 16, 2)? != 1 {
        return Err(invalid("module must be little-endian ELF64 ET_REL"));
    }
    let machine = u16::try_from(word(data, 18, 2)?).unwrap();
    let table = index(word(data, 40, 8)?)?;
    let stride = index(word(data, 58, 2)?)?;
    let count = index(word(data, 60, 2)?)?;
    let strings = index(word(data, 62, 2)?)?;
    if stride != 64 || count == 0 || strings >= count {
        return Err(invalid("invalid ELF section table"));
    }
    let names = section_data(data, section(data, table, stride, strings)?)?;
    let mut found = None;
    for i in 0..count {
        let header = section(data, table, stride, i)?;
        let name = index(word(data, header, 4)?)?;
        let bytes = names
            .get(name..)
            .ok_or_else(|| invalid("invalid ELF section name"))?;
        let end = bytes
            .iter()
            .position(|b| *b == 0)
            .ok_or_else(|| invalid("unterminated ELF section name"))?;
        if &bytes[..end] == b".modinfo" {
            if found.is_some() {
                return Err(invalid("duplicate .modinfo"));
            }
            found = Some(section_data(data, header)?);
        }
    }
    Ok((
        machine,
        found.ok_or_else(|| invalid("module has no .modinfo"))?,
    ))
}

pub fn info_value<'a>(info: &'a [u8], key: &str) -> Result<Option<&'a str>> {
    let mut found = None;
    if !info.ends_with(&[0]) {
        return Err(invalid("unterminated .modinfo"));
    }
    for entry in info.split(|b| *b == 0).filter(|v| !v.is_empty()) {
        let value = std::str::from_utf8(entry).map_err(|_| invalid("non-UTF8 .modinfo"))?;
        if let Some((name, value)) = value.split_once('=')
            && name == key
        {
            if found.is_some() {
                return Err(invalid("duplicate module identity field"));
            }
            found = Some(value);
        }
    }
    Ok(found)
}

pub fn validate_module(data: &[u8], target: &KernelTarget) -> Result<()> {
    let (machine, info) = module_info(data)?;
    if machine != target.machine {
        return Err(invalid("module architecture mismatch"));
    }
    let shift: u32 = info_value(info, "ksu_page_shift")?
        .and_then(|v| v.parse().ok())
        .ok_or_else(|| invalid("module lacks page-size metadata; rebuild the LKM"))?;
    if 1u32.checked_shl(shift) != Some(target.page_size) {
        return Err(invalid("module kernel page-size mismatch"));
    }
    let kmi = info_value(info, "ksu_kmi")?
        .map(str::to_owned)
        .or_else(|| {
            info_value(info, "ksu_kernel_release")
                .ok()
                .flatten()
                .and_then(|v| parse_kmi(v).ok())
        })
        .ok_or_else(|| invalid("module lacks Android KMI metadata; rebuild with KSU_KMI"))?;
    if kmi != target.kmi {
        return Err(invalid("module Android KMI mismatch"));
    }
    let vermagic =
        info_value(info, "vermagic")?.ok_or_else(|| invalid("module vermagic missing"))?;
    if vermagic.split_whitespace().next() != Some(target.release.as_str()) {
        return Err(invalid(
            "module kernel release differs; rebuild for the target instead of forcing vermagic",
        ));
    }
    Ok(())
}

/// An ABI retry is only safe without changing release or feature flags.
/// Cross-release adaptation needs a separately validated compatibility policy.
pub fn validate_vermagic_retry(info: &[u8], required: &str) -> Result<()> {
    let current =
        info_value(info, "vermagic")?.ok_or_else(|| invalid("module vermagic missing"))?;
    if current.split_whitespace().ne(required.split_whitespace()) {
        return Err(invalid(
            "automatic cross-release/feature vermagic adaptation is disabled",
        ));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn kernel_auxv_distinguishes_pages_without_libc() {
        for pages in [4096u64, 16384, 65536] {
            let auxv = [
                6u64.to_le_bytes(),
                pages.to_le_bytes(),
                0u64.to_le_bytes(),
                0u64.to_le_bytes(),
            ]
            .concat();
            assert_eq!(page_size_from_auxv(&auxv).unwrap(), pages as u32);
            assert!(page_size_from_auxv(&auxv[..31]).is_err());
            assert!(page_size_from_auxv(&auxv[..16]).is_err());
        }
        let duplicated = [6u64, 4096, 6, 16384, 0, 0]
            .into_iter()
            .flat_map(u64::to_le_bytes)
            .collect::<Vec<_>>();
        assert!(page_size_from_auxv(&duplicated).is_err());
        assert!(page_size_from_auxv(&[0; 32]).is_err());
    }

    fn module(info: &[u8], machine: u16) -> Vec<u8> {
        let mut data = vec![0; 256];
        data[..6].copy_from_slice(b"\x7fELF\x02\x01");
        data[16..18].copy_from_slice(&1u16.to_le_bytes());
        data[18..20].copy_from_slice(&machine.to_le_bytes());
        data[40..48].copy_from_slice(&64u64.to_le_bytes());
        data[58..60].copy_from_slice(&64u16.to_le_bytes());
        data[60..62].copy_from_slice(&3u16.to_le_bytes());
        data[62..64].copy_from_slice(&1u16.to_le_bytes());
        let names = b"\0.shstrtab\0.modinfo\0";
        data[128 + 24..128 + 32].copy_from_slice(&256u64.to_le_bytes());
        data[128 + 32..128 + 40].copy_from_slice(&(names.len() as u64).to_le_bytes());
        data[192..196].copy_from_slice(&11u32.to_le_bytes());
        data[192 + 24..192 + 32].copy_from_slice(&(256u64 + names.len() as u64).to_le_bytes());
        data[192 + 32..192 + 40].copy_from_slice(&(info.len() as u64).to_le_bytes());
        data.extend_from_slice(names);
        data.extend_from_slice(info);
        data
    }

    #[test]
    fn supported_family_is_not_patch_number() {
        assert_eq!(parse_kmi("6.1.0-android14-11"), Ok("android14-6.1".into()));
        assert_eq!(parse_kmi("5.10.0-android12-9"), Ok("android12-5.10".into()));
        assert!(parse_kmi("4.19.300-android11").is_err());
        assert!(parse_kmi("5.4.200-android12").is_err());
        assert!(parse_kmi("6.1.172-dirty").is_err());
    }

    #[test]
    fn same_release_opposite_pages() {
        let mut image = vec![0; 64];
        image[56..60].copy_from_slice(b"ARM\x64");
        image.extend_from_slice(b"Linux version 6.1.172-android14-11-test (clang)\0");
        image[24..32].copy_from_slice(&10u64.to_le_bytes());
        let four = inspect_kernel(&image, 183).unwrap();
        image[24..32].copy_from_slice(&12u64.to_le_bytes());
        let sixteen = inspect_kernel(&image, 183).unwrap();
        assert_eq!(four.release, sixteen.release);
        assert_eq!(four.kmi, sixteen.kmi);
        assert_ne!(four.page_size, sixteen.page_size);
        let lkm = module(
            b"ksu_page_shift=12\0ksu_kmi=android14-6.1\0vermagic=6.1.172-android14-11-test SMP\0",
            183,
        );
        assert!(validate_module(&lkm, &four).is_ok());
        assert!(validate_module(&lkm, &sixteen).is_err());
        image[24..32].fill(0);
        assert!(inspect_kernel(&image, 183).is_err());
    }

    #[test]
    fn wrong_or_missing_module_identity_is_rejected() {
        let target = KernelTarget::new("6.1.172-android14-11", 4096, 183).unwrap();
        for info in [
            b"vermagic=6.1.166\0".as_slice(),
            b"ksu_page_shift=12\0ksu_kmi=android16-6.12\0",
            b"ksu_page_shift=32\0ksu_kmi=android14-6.1\0",
            b"ksu_page_shift=12\0ksu_page_shift=14\0ksu_kmi=android14-6.1\0",
        ] {
            assert!(validate_module(&module(info, 183), &target).is_err());
        }
        assert!(
            validate_module(
                &module(b"ksu_page_shift=12\0ksu_kmi=android14-6.1\0", 62),
                &target
            )
            .is_err()
        );
    }

    #[test]
    fn truncated_elf_never_panics_or_passes() {
        let lkm = module(b"ksu_page_shift=12\0ksu_kmi=android14-6.1\0", 183);
        for end in 0..lkm.len() {
            assert!(module_info(&lkm[..end]).is_err());
        }
    }

    #[test]
    fn same_kmi_different_release_requires_rebuild() {
        let target = KernelTarget::new("6.1.172-android14-11", 4096, 183).unwrap();
        let lkm = module(
            b"ksu_page_shift=12\0ksu_kmi=android14-6.1\0vermagic=6.1.166-dirty SMP\0",
            183,
        );
        assert!(
            validate_module(&lkm, &target)
                .unwrap_err()
                .0
                .contains("rebuild")
        );
    }

    #[test]
    fn vermagic_does_not_cross_release_or_features() {
        let info = b"vermagic=6.1.166 SMP preempt modversions aarch64 \0";
        assert!(validate_vermagic_retry(info, "6.1.166 SMP preempt modversions aarch64").is_ok());
        assert!(validate_vermagic_retry(info, "6.1.172 SMP preempt modversions aarch64").is_err());
        assert!(validate_vermagic_retry(info, "6.1.166 SMP modversions aarch64").is_err());
    }
}
