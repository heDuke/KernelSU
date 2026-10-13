//! Newly authored bounded ELF64 inspection; no production parser dependencies.
use std::collections::BTreeMap;

pub type Outcome<T> = Result<T, String>;

pub fn window(bytes: &[u8], offset: u64, length: u64) -> Outcome<&[u8]> {
    let end = offset.checked_add(length).ok_or("ELF range overflows")?;
    let start = usize::try_from(offset).map_err(|_| "ELF offset is too large")?;
    let end = usize::try_from(end).map_err(|_| "ELF end is too large")?;
    bytes
        .get(start..end)
        .ok_or_else(|| "ELF range exceeds file".into())
}

fn small(bytes: &[u8], offset: u64) -> Outcome<u16> {
    Ok(u16::from_le_bytes(
        window(bytes, offset, 2)?.try_into().unwrap(),
    ))
}
fn word(bytes: &[u8], offset: u64) -> Outcome<u32> {
    Ok(u32::from_le_bytes(
        window(bytes, offset, 4)?.try_into().unwrap(),
    ))
}
fn wide(bytes: &[u8], offset: u64) -> Outcome<u64> {
    Ok(u64::from_le_bytes(
        window(bytes, offset, 8)?.try_into().unwrap(),
    ))
}
fn text(bytes: &[u8], offset: u64) -> Outcome<&str> {
    let tail = window(
        bytes,
        offset,
        bytes.len() as u64 - offset.min(bytes.len() as u64),
    )?;
    let length = tail
        .iter()
        .take(4097)
        .position(|byte| *byte == 0)
        .ok_or("ELF string is unterminated or too long")?;
    let value = std::str::from_utf8(&tail[..length]).map_err(|_| "ELF string is not UTF-8")?;
    if !value.is_ascii() {
        return Err("ELF identifier is not ASCII".into());
    }
    Ok(value)
}

struct Part {
    label: u32,
    kind: u32,
    offset: u64,
    size: u64,
    link: u32,
    stride: u64,
}
pub struct Import {
    pub name: String,
    pub position: usize,
}
pub struct Image {
    pub machine: u16,
    pub info: BTreeMap<String, String>,
    pub imports: Vec<Import>,
    pub versions: BTreeMap<String, u64>,
    pub signed: bool,
}

impl Image {
    pub fn parse(bytes: &[u8]) -> Outcome<Self> {
        if window(bytes, 0, 7)? != b"\x7fELF\x02\x01\x01" || small(bytes, 16)? != 1 {
            return Err("Expected a little-endian ELF64 relocatable module".into());
        }
        let table = wide(bytes, 40)?;
        let count = small(bytes, 60)? as usize;
        let names = small(bytes, 62)? as usize;
        if small(bytes, 52)? != 64
            || small(bytes, 58)? != 64
            || count == 0
            || count > 4096
            || names >= count
        {
            return Err("Unsupported or malformed ELF section table".into());
        }
        window(bytes, table, count as u64 * 64)?;
        let mut parts = Vec::with_capacity(count);
        for i in 0..count {
            let entry = table + i as u64 * 64;
            let part = Part {
                label: word(bytes, entry)?,
                kind: word(bytes, entry + 4)?,
                offset: wide(bytes, entry + 24)?,
                size: wide(bytes, entry + 32)?,
                link: word(bytes, entry + 40)?,
                stride: wide(bytes, entry + 56)?,
            };
            if part.kind != 8 {
                window(bytes, part.offset, part.size)?;
            }
            parts.push(part);
        }
        if parts[names].kind != 3 {
            return Err("ELF section names are not a string table".into());
        }
        let name_bytes = window(bytes, parts[names].offset, parts[names].size)?;
        let mut named = BTreeMap::new();
        for (i, part) in parts.iter().enumerate() {
            let label = text(name_bytes, part.label as u64)?;
            if !label.is_empty() && named.insert(label.to_owned(), i).is_some() {
                return Err("Duplicate ELF section name".into());
            }
        }
        let get = |name: &str| -> Outcome<&Part> {
            named
                .get(name)
                .map(|index| &parts[*index])
                .ok_or_else(|| format!("Missing ELF section {name}"))
        };
        let metadata = get(".modinfo")?;
        if metadata.size > 65536 {
            return Err("Module metadata exceeds 64 KiB limit".into());
        }
        let mut info = BTreeMap::new();
        for raw in window(bytes, metadata.offset, metadata.size)?
            .split(|b| *b == 0)
            .filter(|raw| !raw.is_empty())
        {
            let line = std::str::from_utf8(raw).map_err(|_| "Invalid module metadata encoding")?;
            if let Some((key, value)) = line.split_once('=')
                && [
                    "name",
                    "vermagic",
                    "ksu_kmi",
                    "ksu_page_shift",
                    "ksu_kernel_release",
                ]
                .contains(&key)
                && info.insert(key.to_owned(), value.to_owned()).is_some()
            {
                return Err(format!("Repeated identity field {key}"));
            }
        }
        let symbols = get(".symtab")?;
        if symbols.kind != 2
            || symbols.stride != 24
            || symbols.size % 24 != 0
            || symbols.size / 24 > 65536
        {
            return Err("Malformed or oversized ELF symbol table".into());
        }
        let strings = parts
            .get(symbols.link as usize)
            .filter(|part| part.kind == 3)
            .ok_or("Invalid ELF symbol string table")?;
        let string_bytes = window(bytes, strings.offset, strings.size)?;
        let mut imports = Vec::new();
        for slot in 1..symbols.size / 24 {
            let position = symbols.offset + slot * 24;
            if small(bytes, position + 6)? == 0 {
                let name = text(string_bytes, word(bytes, position)? as u64)?;
                if name.is_empty() || name.len() > 256 {
                    return Err("Invalid imported symbol name".into());
                }
                imports.push(Import {
                    name: name.to_owned(),
                    position: position as usize,
                });
            }
        }
        let version_part = get("__versions")?;
        if version_part.size == 0 || version_part.size % 64 != 0 || version_part.size / 64 > 65536 {
            return Err("Malformed or missing symbol versions".into());
        }
        let mut versions = BTreeMap::new();
        for slot in 0..version_part.size / 64 {
            let position = version_part.offset + slot * 64;
            let name = text(window(bytes, position + 8, 56)?, 0)?.to_owned();
            if name.is_empty() || versions.insert(name, wide(bytes, position)?).is_some() {
                return Err("Invalid or duplicate symbol version".into());
            }
        }
        if !versions.contains_key("module_layout") {
            return Err("Missing module_layout CRC".into());
        }
        let btf = get(".BTF")?;
        let data = window(bytes, btf.offset, btf.size)?;
        if small(data, 0)? != 0xeb9f || data.get(2) != Some(&1) {
            return Err("Invalid BTF header".into());
        }
        let header = word(data, 4)? as u64;
        if header < 24 {
            return Err("Invalid BTF header size".into());
        }
        window(data, header + word(data, 8)? as u64, word(data, 12)? as u64)?;
        window(
            data,
            header + word(data, 16)? as u64,
            word(data, 20)? as u64,
        )?;
        Ok(Self {
            machine: small(bytes, 18)?,
            info,
            imports,
            versions,
            signed: bytes.ends_with(b"~Module signature appended~\n"),
        })
    }

    pub fn field(&self, name: &str) -> Outcome<&str> {
        self.info
            .get(name)
            .map(String::as_str)
            .ok_or_else(|| format!("Missing module identity {name}"))
    }

    pub fn module_name(&self) -> Outcome<&str> {
        let name = self.field("name")?;
        if name.is_empty()
            || name.len() > 64
            || !name.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'_')
        {
            return Err("Invalid module name".into());
        }
        Ok(name)
    }

    pub fn private_image(&self, public: &std::collections::BTreeSet<String>) -> Self {
        Self {
            machine: self.machine,
            info: self.info.clone(),
            imports: self
                .imports
                .iter()
                .filter(|entry| !public.contains(&entry.name))
                .map(|entry| Import {
                    name: entry.name.clone(),
                    position: entry.position,
                })
                .collect(),
            versions: self.versions.clone(),
            signed: self.signed,
        }
    }
    pub fn patch_hybrid(
        &self,
        bytes: &mut [u8],
        addresses: &BTreeMap<String, u64>,
        public: &std::collections::BTreeSet<String>,
    ) -> Outcome<()> {
        let selected = self.private_image(public);
        selected.patch(bytes, addresses)
    }

    pub fn patch(&self, bytes: &mut [u8], addresses: &BTreeMap<String, u64>) -> Outcome<()> {
        if self.signed {
            return Err("Private-symbol strategy cannot mutate a signed module".into());
        }
        for item in &self.imports {
            window(bytes, item.position as u64, 24)?;
            if !addresses
                .get(&item.name)
                .is_some_and(|address| *address != 0)
            {
                return Err(format!("No verified address for {}", item.name));
            }
        }
        for item in &self.imports {
            let start = item.position;
            bytes[start + 6..start + 8].copy_from_slice(&0xfff1u16.to_le_bytes());
            bytes[start + 8..start + 16].copy_from_slice(&addresses[&item.name].to_le_bytes());
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn rejects_range_overflow_and_invalid_strings() {
        assert!(window(&[0; 8], u64::MAX, 2).is_err());
        assert!(window(&[0; 8], 7, 2).is_err());
        assert!(text(b"unfinished", 0).is_err());
        assert!(text(b"a\0", 3).is_err());
        assert!(Image::parse(&[0; 64]).is_err());
    }
}
