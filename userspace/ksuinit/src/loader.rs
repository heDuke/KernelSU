//! Pure preflight; never changes kernel state or invokes module-loading syscalls.
use anyhow::{Context, Result, bail, ensure};
use goblin::elf::{Elf, section_header, sym::Sym};
use scroll::{Pwrite, ctx::SizeWith};
use std::collections::{BTreeMap, BTreeSet};

pub(crate) fn is_signed(data: &[u8]) -> bool {
    data.ends_with(b"~Module signature appended~\n")
}

pub(crate) fn parse_symbol(line: &str) -> Result<Option<(String, u64)>> {
    let mut fields = line.split_whitespace();
    let address = fields.next().context("Missing kallsyms address")?;
    let kind = fields.next().context("Missing kallsyms type")?;
    let name = fields.next().context("Missing kallsyms name")?;
    ensure!(kind.len() == 1, "Invalid kallsyms type");
    let address = u64::from_str_radix(address, 16).context("Invalid kallsyms address")?;
    if let Some(owner) = fields.next() {
        ensure!(
            owner.starts_with('[') && owner.ends_with(']') && fields.next().is_none(),
            "Invalid kallsyms module owner"
        );
        return Ok(None);
    }
    let end = name
        .find('$')
        .or_else(|| name.find(".llvm."))
        .unwrap_or(name.len());
    ensure!(end > 0, "Empty normalized kallsyms name");
    Ok(Some((name[..end].to_owned(), address)))
}

fn resolve(
    wanted: &BTreeSet<String>,
    symbols: impl Iterator<Item = Result<(String, u64)>>,
) -> Result<BTreeMap<String, u64>> {
    let mut resolved = BTreeMap::new();
    // Read the entire built-in table: a later duplicate must not be missed.
    for entry in symbols {
        let (name, address) = entry?;
        if !wanted.contains(&name) {
            continue;
        }
        ensure!(
            address != 0,
            "Kernel symbol {name} has a hidden/zero address"
        );
        if let Some(previous) = resolved.insert(name.clone(), address) {
            ensure!(
                previous == address,
                "Kernel symbol {name} has ambiguous addresses"
            );
        }
    }
    let missing: Vec<_> = wanted
        .iter()
        .filter(|name| !resolved.contains_key(*name))
        .collect();
    if !missing.is_empty() {
        bail!(
            "Missing {} kernel symbol(s): {}{}; use an LKM built for this exact kernel",
            missing.len(),
            missing
                .iter()
                .take(8)
                .map(|name| name.as_str())
                .collect::<Vec<_>>()
                .join(", "),
            if missing.len() > 8 { ", ..." } else { "" }
        );
    }
    Ok(resolved)
}

pub(crate) fn prepare<I: Iterator<Item = Result<(String, u64)>>>(
    data: &[u8],
    symbols: impl FnOnce() -> Result<I>,
) -> Result<Vec<u8>> {
    // Preserve signed bytes; let the kernel resolve imports and verify trust.
    if is_signed(data) {
        return Ok(data.to_vec());
    }
    let elf = Elf::parse(data).context("Cannot parse module ELF")?;
    ensure!(elf.is_64, "Only ELF64 modules are supported");
    let ctx = *elf.syms.ctx();
    let mut patches: BTreeMap<String, Vec<(Sym, usize)>> = BTreeMap::new();
    for (index, symbol) in elf.syms.iter().enumerate().skip(1) {
        if symbol.st_shndx != section_header::SHN_UNDEF as usize {
            continue;
        }
        let name = elf
            .strtab
            .get_at(symbol.st_name)
            .filter(|name| !name.is_empty())
            .context("Undefined module symbol has an invalid name")?;
        let stride = Sym::size_with(elf.syms.ctx());
        let offset = elf
            .syms
            .offset()
            .checked_add(
                index
                    .checked_mul(stride)
                    .context("Module symbol index overflow")?,
            )
            .context("Module symbol offset overflow")?;
        ensure!(
            offset
                .checked_add(stride)
                .is_some_and(|end| end <= data.len()),
            "Module symbol is outside ELF buffer"
        );
        patches
            .entry(name.to_owned())
            .or_default()
            .push((symbol, offset));
    }
    let wanted = patches.keys().cloned().collect();
    let addresses = if patches.is_empty() {
        BTreeMap::new()
    } else {
        resolve(&wanted, symbols()?)?
    };
    // No ELF mutation until every requested address has passed preflight.
    let mut buffer = data.to_vec();
    for (name, entries) in patches {
        for (mut symbol, offset) in entries {
            symbol.st_shndx = section_header::SHN_ABS as usize;
            symbol.st_value = addresses[&name];
            buffer.pwrite_with(symbol, offset, ctx)?;
        }
    }
    log::info!(
        "Module symbol preflight passed: {} unique imports",
        addresses.len()
    );
    Ok(buffer)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn wanted() -> BTreeSet<String> {
        BTreeSet::from(["entry".to_owned()])
    }

    #[test]
    fn rejects_missing_hidden_ambiguous_and_failed_scan() {
        assert!(resolve(&wanted(), std::iter::empty()).is_err());
        assert!(resolve(&wanted(), [Ok(("entry".into(), 0))].into_iter()).is_err());
        assert!(
            resolve(
                &wanted(),
                [Ok(("entry".into(), 1)), Ok(("entry".into(), 2))].into_iter()
            )
            .is_err()
        );
        assert!(
            resolve(
                &wanted(),
                [Ok(("entry".into(), 1)), Err(anyhow::anyhow!("read failed"))].into_iter()
            )
            .is_err()
        );
    }

    #[test]
    fn accepts_same_address_aliases() {
        let result = resolve(
            &wanted(),
            [Ok(("entry".into(), 1)), Ok(("entry".into(), 1))].into_iter(),
        )
        .unwrap();
        assert_eq!(result["entry"], 1);
    }

    #[test]
    fn parses_suffixes_and_excludes_module_owned_symbols() {
        assert_eq!(
            parse_symbol("abcd T entry.llvm.123").unwrap(),
            Some(("entry".into(), 0xabcd))
        );
        assert_eq!(
            parse_symbol("abcd T entry$123").unwrap(),
            Some(("entry".into(), 0xabcd))
        );
        assert_eq!(parse_symbol("abcd T entry [other]").unwrap(), None);
        for line in [
            "",
            "xyz T entry",
            "abcd",
            "abcd TT entry",
            "abcd T entry other",
        ] {
            assert!(parse_symbol(line).is_err(), "{line}");
        }
    }

    #[test]
    fn signed_bytes_are_preserved_without_symbol_scan() {
        let data = b"signed bytes~Module signature appended~\n";
        let never_scan =
            || -> Result<std::iter::Empty<Result<(String, u64)>>> { panic!("must not scan") };
        assert_eq!(prepare(data, never_scan).unwrap(), data);
    }

    #[test]
    #[ignore = "requires KSU_CANARY_MODULE_FIXTURE"]
    fn real_candidate_rewrites_every_import_and_preserves_metadata() {
        let data = std::fs::read(std::env::var("KSU_CANARY_MODULE_FIXTURE").unwrap()).unwrap();
        let before = Elf::parse(&data).unwrap();
        let names: Vec<_> = before
            .syms
            .iter()
            .skip(1)
            .filter(|s| s.st_shndx == 0)
            .map(|s| before.strtab.get_at(s.st_name).unwrap().to_owned())
            .collect();
        assert!(
            !names.is_empty(),
            "Fixture must have real undefined imports"
        );
        let expected: BTreeMap<_, _> = names
            .iter()
            .enumerate()
            .map(|(i, name)| (name.clone(), 0xffff000000000000 + i as u64))
            .collect();
        let values = expected
            .iter()
            .map(|(name, address)| Ok((name.clone(), *address)));
        let result = prepare(&data, || Ok(values)).unwrap();
        let after = Elf::parse(&result).unwrap();
        assert!(after.syms.iter().skip(1).all(|s| s.st_shndx != 0));
        for symbol in after.syms.iter().skip(1) {
            if let Some(address) = after
                .strtab
                .get_at(symbol.st_name)
                .and_then(|name| expected.get(name))
            {
                assert_eq!(symbol.st_value, *address);
                assert_eq!(symbol.st_shndx, section_header::SHN_ABS as usize);
            }
        }
        for name in [".modinfo", "__versions", ".BTF"] {
            let section = before
                .section_headers
                .iter()
                .find(|s| before.shdr_strtab.get_at(s.sh_name) == Some(name))
                .unwrap();
            let range = section.sh_offset as usize..(section.sh_offset + section.sh_size) as usize;
            assert_eq!(data[range.clone()], result[range]);
        }
        assert!(
            prepare(&data, || Ok(std::iter::empty()))
                .unwrap_err()
                .to_string()
                .contains("Missing")
        );
        assert!(prepare(&data[..64], || Ok(std::iter::empty())).is_err());
    }
}
