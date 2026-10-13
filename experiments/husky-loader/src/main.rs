//! Standalone experimental loader. No imports from production project code.
mod image;
mod settings;
mod sha;
mod store;

use image::{Image, Outcome};
use std::collections::{BTreeMap, BTreeSet};
use std::ffi::CString;
use std::fs::{File, OpenOptions};
use std::io::{BufRead, BufReader, Read, Seek, SeekFrom};
use std::os::fd::AsRawFd;
use std::os::unix::fs::{MetadataExt, OpenOptionsExt};
use std::time::Instant;

type Report = BTreeMap<String, String>;

fn quoted(value: &str) -> String {
    let mut out = String::from("\"");
    for character in value.chars() {
        match character {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            c if c < ' ' => out.push_str(&format!("\\u{:04x}", c as u32)),
            c => out.push(c),
        }
    }
    out.push('"');
    out
}
fn label(report: &mut Report, key: &str, value: &str) {
    report.insert(key.into(), quoted(value));
}
fn number(report: &mut Report, key: &str, value: usize) {
    report.insert(key.into(), value.to_string());
}

fn read_file(path: &str, limit: u64) -> Outcome<Vec<u8>> {
    let file = File::open(path).map_err(|e| format!("Open {path}: {e}"))?;
    let size = file.metadata().map_err(|e| e.to_string())?;
    if !size.is_file() || size.len() > limit {
        return Err(format!(
            "Expected regular file no larger than {limit} bytes: {path}"
        ));
    }
    let mut bytes = Vec::with_capacity(size.len() as usize + 1);
    file.take(limit + 1)
        .read_to_end(&mut bytes)
        .map_err(|e| e.to_string())?;
    if bytes.len() as u64 > limit || bytes.len() as u64 != size.len() {
        return Err("File length changed or exceeded limit".into());
    }
    Ok(bytes)
}

fn proof(path: &str) -> Outcome<BTreeMap<String, String>> {
    let bytes = read_file(path, 8192)?;
    let source = std::str::from_utf8(&bytes).map_err(|_| "Target proof is not UTF-8")?;
    let mut fields: BTreeMap<String, String> = BTreeMap::new();
    let allowed = [
        "schema",
        "release",
        "machine",
        "page_size",
        "module_sha256",
        "symvers_sha256",
        "config_sha256",
        "source_commit",
        "vermagic",
        "kmi",
        "private_imports",
    ];
    for line in source
        .lines()
        .map(str::trim)
        .filter(|l| !l.is_empty() && !l.starts_with('#'))
    {
        let (key, value) = line
            .split_once('=')
            .ok_or("Target proof requires key=value records")?;
        if !allowed.contains(&key)
            || value.is_empty()
            || fields.insert(key.into(), value.into()).is_some()
        {
            return Err(format!(
                "Unknown, empty or duplicate target proof field: {key}"
            ));
        }
    }
    for key in allowed {
        if !fields.contains_key(key) {
            return Err(format!("Missing target proof field: {key}"));
        }
    }
    if fields["schema"] != "1" || fields["machine"] != "183" || fields["page_size"] != "4096" {
        return Err("First prototype supports schema 1 and ARM64 4K targets only".into());
    }
    for (key, length) in [
        ("module_sha256", 64),
        ("symvers_sha256", 64),
        ("config_sha256", 64),
        ("source_commit", 40),
    ] {
        if fields[key].len() != length || !fields[key].bytes().all(|b| b.is_ascii_hexdigit()) {
            return Err(format!("Invalid target proof digest: {key}"));
        }
    }
    if fields["private_imports"].parse::<usize>().is_err() {
        return Err("Invalid private import count".into());
    }
    Ok(fields)
}

fn validate_files(
    image: &Image,
    module_digest: &str,
    proof: &BTreeMap<String, String>,
    symvers: &str,
    config: &str,
) -> Outcome<usize> {
    if image.machine != 183
        || image.field("ksu_page_shift")? != "12"
        || image.field("ksu_kernel_release")? != proof["release"]
        || image.field("ksu_kmi")? != proof["kmi"]
        || image.field("vermagic")? != proof["vermagic"]
        || image.field("vermagic")?.split_whitespace().next() != Some(proof["release"].as_str())
    {
        return Err("Module identity does not match exact target proof".into());
    }
    if module_digest != proof["module_sha256"].to_ascii_lowercase() {
        return Err("Module SHA-256 does not match target proof".into());
    }
    let configuration = read_file(config, 1_048_576)?;
    if sha::bytes(&configuration) != proof["config_sha256"].to_ascii_lowercase() {
        return Err("Kernel config SHA-256 mismatch".into());
    }
    let config_text =
        std::str::from_utf8(&configuration).map_err(|_| "Kernel config is not UTF-8")?;
    for needed in [
        "CONFIG_ARM64_4K_PAGES=y",
        "CONFIG_MODVERSIONS=y",
        "CONFIG_CFI_CLANG=y",
        "# CONFIG_CFI_PERMISSIVE is not set",
        "CONFIG_MODULE_SIG_PROTECT=y",
        "# CONFIG_MODULE_SIG_FORCE is not set",
    ] {
        if !config_text.lines().any(|line| line == needed) {
            return Err(format!("Unsupported config: expected {needed}"));
        }
    }
    let version_bytes = read_file(symvers, 8_388_608)?;
    if sha::reader(version_bytes.as_slice()).map_err(|e| e.to_string())?
        != proof["symvers_sha256"].to_ascii_lowercase()
    {
        return Err("Module.symvers SHA-256 mismatch".into());
    }
    let version_text =
        std::str::from_utf8(&version_bytes).map_err(|_| "Module.symvers is not UTF-8")?;
    let mut expected = BTreeMap::new();
    let wanted: BTreeSet<_> = image
        .imports
        .iter()
        .map(|entry| entry.name.as_str())
        .collect();
    for line in version_text.lines() {
        let mut fields = line.split_whitespace();
        let crc = fields.next().ok_or("Invalid symvers CRC field")?;
        let name = fields.next().ok_or("Invalid symvers name field")?;
        let owner = fields.next().ok_or("Invalid symvers owner field")?;
        let value = u64::from_str_radix(crc.trim_start_matches("0x"), 16)
            .map_err(|_| "Invalid symvers CRC")?;
        if owner == "vmlinux"
            && (name == "module_layout" || wanted.contains(name))
            && expected.insert(name.to_owned(), value).is_some()
        {
            return Err(format!("Ambiguous symvers record {name}"));
        }
    }
    if !expected.contains_key("module_layout") {
        return Err("Target symvers lacks module_layout".into());
    }
    for (name, value) in &expected {
        if image.versions.get(name) != Some(value) {
            return Err(format!("Missing or mismatched CRC: {name}"));
        }
    }
    for (name, value) in &image.versions {
        if expected.get(name) != Some(value) {
            return Err(format!("Unexpected or mismatched version record: {name}"));
        }
    }
    let private = image
        .imports
        .iter()
        .filter(|item| !expected.contains_key(&item.name))
        .count();
    if private != proof["private_imports"].parse::<usize>().unwrap() {
        return Err("Private import count differs from target proof".into());
    }
    Ok(private)
}

fn addresses(image: &Image, input: impl BufRead) -> Outcome<(BTreeMap<String, u64>, usize)> {
    let mut found: BTreeMap<String, Option<u64>> = image
        .imports
        .iter()
        .map(|i| (i.name.clone(), None))
        .collect();
    let mut input = input;
    let mut record = String::with_capacity(256);
    let mut count = 0;
    loop {
        record.clear();
        let got = (&mut input)
            .take(8193)
            .read_line(&mut record)
            .map_err(|e| format!("Read kernel symbol table: {e}"))?;
        if got == 0 {
            break;
        }
        if got > 8192 {
            return Err("Kernel symbol record exceeds resource limit".into());
        }
        count += 1;
        if count > 2_000_000 {
            return Err("Kernel symbol table exceeds resource limit".into());
        }
        let mut columns = record.split_whitespace();
        let raw_address = columns.next().ok_or("Missing symbol address")?;
        let kind = columns.next().ok_or("Missing symbol type")?;
        let raw = columns.next().ok_or("Missing symbol name")?;
        let address =
            u64::from_str_radix(raw_address, 16).map_err(|_| "Invalid kernel symbol address")?;
        if kind.len() != 1 {
            return Err(format!("Malformed kernel symbol type {count}"));
        }
        if let Some(owner) = columns.next() {
            if owner.starts_with('[') && owner.ends_with(']') && columns.next().is_none() {
                continue;
            }
            return Err(format!("Malformed kernel symbol owner {count}"));
        }
        let cut = [raw.find('$'), raw.find(".llvm.")]
            .into_iter()
            .flatten()
            .min()
            .unwrap_or(raw.len());
        if let Some(value) = found.get_mut(&raw[..cut]) {
            if address == 0 {
                return Err(format!(
                    "Kernel hides address of {}; use an appropriate privileged execution context",
                    &raw[..cut]
                ));
            }
            if value.is_some_and(|old| old != address) {
                return Err(format!("Multiple addresses for {}", &raw[..cut]));
            }
            *value = Some(address);
        }
    }
    let missing: Vec<_> = found
        .iter()
        .filter(|(_, v)| v.is_none())
        .map(|(k, _)| k.as_str())
        .take(9)
        .collect();
    if !missing.is_empty() {
        return Err(format!(
            "Unresolved kernel symbols (first nine): {}",
            missing.join(", ")
        ));
    }
    Ok((
        found.into_iter().map(|(k, v)| (k, v.unwrap())).collect(),
        count,
    ))
}

unsafe extern "C" {
    fn syscall(number: isize, ...) -> isize;
    fn flock(fd: i32, operation: i32) -> i32;
    fn geteuid() -> u32;
}

fn device_matches(proof: &BTreeMap<String, String>) -> Outcome<()> {
    if !cfg!(target_arch = "aarch64") {
        return Err(
            "Experimental loading requires an ARM64 executable on the target device".into(),
        );
    }
    let release =
        std::fs::read_to_string("/proc/sys/kernel/osrelease").map_err(|e| e.to_string())?;
    if release.trim() != proof["release"] {
        return Err("Running kernel release differs from target proof; no load attempted".into());
    }
    let auxv = std::fs::read("/proc/self/auxv").map_err(|e| e.to_string())?;
    if auxv.len() > 4096 || auxv.len() % 16 != 0 {
        return Err("Invalid device auxv".into());
    }
    let page = auxv
        .as_chunks::<16>()
        .0
        .iter()
        .find(|entry| u64::from_ne_bytes(entry[..8].try_into().unwrap()) == 6)
        .map(|entry| u64::from_ne_bytes(entry[8..].try_into().unwrap()));
    if page != Some(4096) {
        return Err("Running kernel is not a confirmed 4K target".into());
    }
    // SAFETY: geteuid has no pointer arguments or application-side side effects.
    if unsafe { geteuid() } != 0 {
        return Err("Load needs root or an authorized early-init context".into());
    }
    Ok(())
}

fn lock_load() -> Outcome<File> {
    let file = OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .truncate(false)
        .custom_flags(0x20000)
        .mode(0o600)
        .open("/dev/.husky-loader.lock")
        .map_err(|e| format!("Open loader lock: {e}"))?;
    let state = file.metadata().map_err(|e| e.to_string())?;
    if !state.is_file() || state.uid() != 0 || state.nlink() != 1 || state.mode() & 0o077 != 0 {
        return Err("Loader lock must be a private root-owned regular file".into());
    }
    // SAFETY: fd belongs to this live File; LOCK_EX | LOCK_NB has no pointers.
    if unsafe { flock(file.as_raw_fd(), 2 | 4) } != 0 {
        return Err(format!(
            "Another experimental load may be running: {}",
            std::io::Error::last_os_error()
        ));
    }
    Ok(file)
}

fn log_cursor() -> Option<File> {
    let mut file = OpenOptions::new()
        .read(true)
        .custom_flags(0x800)
        .open("/dev/kmsg")
        .ok()?;
    file.seek(SeekFrom::End(0)).ok()?;
    Some(file)
}
fn log_tail(file: &mut File, limit: usize) -> String {
    let mut output = Vec::new();
    let mut scratch = [0u8; 4096];
    for _ in 0..128 {
        let remaining = limit - output.len();
        if remaining == 0 {
            break;
        }
        let space = remaining.min(scratch.len());
        match file.read(&mut scratch[..space]) {
            Ok(0) => break,
            Ok(count) => output.extend_from_slice(&scratch[..count]),
            Err(e)
                if e.kind() == std::io::ErrorKind::Interrupted || e.raw_os_error() == Some(32) =>
            {
                continue;
            }
            Err(_) => break,
        }
    }
    String::from_utf8_lossy(&output).into_owned()
}

fn options(args: &[String]) -> Outcome<BTreeMap<String, String>> {
    let mut result = BTreeMap::new();
    let mut position = 2;
    while position < args.len() {
        let key = args[position].as_str();
        if key == "--experimental" {
            if result.insert(key.into(), "yes".into()).is_some() {
                return Err("Repeated experimental option".into());
            }
            position += 1;
        } else if [
            "--proof",
            "--symvers",
            "--config",
            "--settings",
            "--preset",
            "--set",
            "--store",
            "--expect-generation",
            "--output",
        ]
        .contains(&key)
        {
            let value = args
                .get(position + 1)
                .filter(|v| !v.starts_with("--"))
                .ok_or("Option needs a file path")?;
            if key == "--set" {
                let entry = result.entry(key.to_owned()).or_insert_with(String::new);
                if !entry.is_empty() {
                    entry.push('\n');
                }
                entry.push_str(value);
            } else if result.insert(key.into(), value.clone()).is_some() {
                return Err("Repeated option".into());
            }
            position += 2;
        } else {
            return Err(format!("Unknown option {key}"));
        }
    }
    Ok(result)
}

const REVIEWED_INPUT_PROFILE: &str =
    "394ae4978afe6654153be10826f4a54787d5629e27fac0d43440e32def376b71";
fn require_campaign_approval() -> Outcome<()> {
    Err("Private ABI/CFI classification and a device recovery campaign are not approved; no module-loading syscall is enabled in this build".into())
}
fn resolve_settings(
    flags: &BTreeMap<String, String>,
    report: &mut Report,
) -> Outcome<settings::Settings> {
    let saved = if let Some(path) = flags.get("--settings") {
        settings::Saved::parse(&read_file(path, 16384)?)?
    } else {
        settings::Saved::defaults()
    };
    let overrides = flags
        .get("--set")
        .map(|value| value.lines().map(str::to_owned).collect::<Vec<_>>())
        .unwrap_or_default();
    let effective = settings::Settings::resolve(
        &saved,
        flags.get("--preset").map(String::as_str),
        &overrides,
    )?;
    label(report, "output", &effective.values["output"]);
    label(
        report,
        "settings_sha256",
        &sha::bytes(effective.snapshot().as_bytes()),
    );
    report.insert("settings_generation".into(), saved.generation.to_string());
    for (key, value) in &effective.values {
        label(report, &format!("settings.{key}"), value);
    }
    for (key, value) in &effective.origins {
        label(report, &format!("settings_origin.{key}"), value);
    }
    Ok(effective)
}
fn settings_command(args: &[String], report: &mut Report) -> Outcome<()> {
    let action = args
        .get(1)
        .map(String::as_str)
        .ok_or("Missing settings action")?;
    let has_input = action == "import";
    let mut option_args = vec!["settings".into(), action.into()];
    option_args.extend_from_slice(
        args.get(if has_input { 3 } else { 2 }..)
            .unwrap_or_default(),
    );
    let flags = options(&option_args)?;
    label(report, "operation", &format!("settings.{action}"));
    let allowed: &[&str] = match action {
        "show" | "validate" | "diff" => &["--settings", "--preset", "--set"],
        "export" => &["--settings", "--preset", "--set", "--output"],
        "import" | "reset" => &["--store", "--expect-generation"],
        _ => return Err("Unknown settings action".into()),
    };
    if flags.keys().any(|key| !allowed.contains(&key.as_str())) {
        return Err("Unsupported flag for this settings operation".into());
    }
    match action {
        "show" | "validate" | "diff" => {
            if [
                "--experimental",
                "--proof",
                "--symvers",
                "--config",
                "--store",
                "--expect-generation",
                "--output",
            ]
            .iter()
            .any(|key| flags.contains_key(*key))
            {
                return Err("Invalid flags for read-only preferences".into());
            }
            if action == "validate" && !flags.contains_key("--settings") {
                return Err("validate requires --settings FILE".into());
            }
            let effective = resolve_settings(&flags, report)?;
            if action == "diff" {
                let defaults =
                    settings::Settings::resolve(&settings::Saved::defaults(), None, &[])?;
                for (key, value) in &effective.values {
                    if defaults.values[key] != *value {
                        label(report, &format!("changed.{key}"), value);
                    }
                }
            }
        }
        "export" => {
            let output = flags
                .get("--output")
                .ok_or("export requires --output FILE")?;
            let effective = resolve_settings(&flags, report)?;
            let saved = settings::Saved {
                generation: 0,
                preset: "balanced".into(),
                overrides: effective.values,
            };
            let mut file = OpenOptions::new()
                .write(true)
                .create_new(true)
                .custom_flags(0x20000)
                .mode(0o600)
                .open(output)
                .map_err(|e| e.to_string())?;
            use std::io::Write;
            file.write_all(saved.serialize().as_bytes())
                .map_err(|e| e.to_string())?;
            file.sync_all().map_err(|e| e.to_string())?;
            label(
                report,
                "export",
                "created_without_overwriting_existing_file",
            );
        }
        "import" | "reset" => {
            let path = flags
                .get("--store")
                .ok_or("write requires --store FILE in an existing private directory")?;
            let generation = flags
                .get("--expect-generation")
                .ok_or("write requires --expect-generation NUMBER")?
                .parse::<u64>()
                .map_err(|_| "Invalid expected generation")?;
            let next = if has_input {
                settings::Saved::parse(&read_file(
                    args.get(2).ok_or("Missing import file")?,
                    16384,
                )?)?
            } else {
                settings::Saved::defaults()
            };
            let generation = store::save(path, generation, next)?;
            report.insert("saved_generation".into(), generation.to_string());
        }
        _ => return Err("Unknown settings action".into()),
    }
    label(report, "stage", "preferences_only_no_load");
    Ok(())
}
fn doctor(report: &mut Report) -> Outcome<()> {
    label(report, "operation", "doctor");
    label(report, "architecture", std::env::consts::ARCH);
    // SAFETY: geteuid has no arguments or side effects.
    number(report, "effective_uid", unsafe { geteuid() } as usize);
    for (key, path) in [
        ("running_release", "/proc/sys/kernel/osrelease"),
        ("modules_disabled", "/proc/sys/kernel/modules_disabled"),
        ("selinux_context", "/proc/self/attr/current"),
    ] {
        let mut bytes = Vec::new();
        let content = File::open(path).and_then(|file| file.take(4097).read_to_end(&mut bytes));
        match content {
            Ok(_) if bytes.len() <= 4096 => {
                label(report, key, String::from_utf8_lossy(&bytes).trim())
            }
            _ => label(report, key, "unavailable"),
        }
    }
    // procfs pseudo-files have a zero stat length; read their bounded content directly.
    let file = File::open("/proc/self/status").map_err(|e| e.to_string())?;
    let mut status = String::new();
    file.take(16384)
        .read_to_string(&mut status)
        .map_err(|e| e.to_string())?;
    let cap = status
        .lines()
        .find_map(|line| line.strip_prefix("CapEff:"))
        .and_then(|value| u64::from_str_radix(value.trim(), 16).ok());
    report.insert(
        "cap_sys_module".into(),
        cap.is_some_and(|bits| bits & (1 << 16) != 0).to_string(),
    );
    report.insert("load_permitted".into(), "false".into());
    label(
        report,
        "campaign_gate",
        "private_abi_cfi_and_boot_recovery_pending",
    );
    label(report, "stage", "read_only_device_diagnostics");
    Ok(())
}
fn redact(input: &str) -> String {
    input
        .split_whitespace()
        .map(|word| {
            if word
                .split(|character: char| !character.is_ascii_hexdigit())
                .any(|part| part.len() >= 12)
            {
                "[address-redacted]"
            } else {
                word
            }
        })
        .collect::<Vec<_>>()
        .join(" ")
}

fn execute(args: &[String], report: &mut Report) -> Outcome<()> {
    let mode = args
        .first()
        .map(String::as_str)
        .ok_or("Use inspect, check or load followed by an LKM path")?;
    if mode == "settings" {
        return settings_command(args, report);
    }
    if mode == "help" || mode == "--help" {
        label(
            report,
            "usage",
            "inspect|check|plan|check-device MODULE; doctor; status; settings show|validate|diff|export|import|reset; --settings FILE --preset NAME --set key=value; load is gated pending private ABI/device campaign approval",
        );
        return Ok(());
    }
    if ["doctor", "status"].contains(&mode) {
        if args.len() != 1 {
            return Err("doctor/status take no load options".into());
        }
        doctor(report)?;
        label(report, "operation", mode);
        label(report, "module_presence", "not_probed");
        label(report, "root_health", "not_probed");
        return Ok(());
    }
    if !["inspect", "check", "plan", "check-device", "load"].contains(&mode) {
        return Err("Unknown operation; use help".into());
    }
    let path = args.get(1).ok_or("Missing LKM path")?;
    let flags = options(args)?;
    if ["--store", "--expect-generation", "--output"]
        .iter()
        .any(|key| flags.contains_key(*key))
    {
        return Err("Preference write flags belong to settings commands".into());
    }
    let preferences = resolve_settings(&flags, report)?;
    let deadline = Instant::now()
        + std::time::Duration::from_secs(preferences.number("deadline_seconds") as u64);
    label(report, "operation", mode);
    label(report, "stage", "read");
    let mut bytes = read_file(
        path,
        (preferences.number("module_limit_mib") * 1_048_576) as u64,
    )?;
    label(report, "stage", "elf");
    let image = Image::parse(&bytes)?;
    label(report, "name", image.module_name()?);
    number(report, "module_bytes", bytes.len());
    number(report, "machine", image.machine as usize);
    number(report, "imports", image.imports.len());
    number(report, "version_records", image.versions.len());
    report.insert("signature_present".into(), image.signed.to_string());
    let original_digest = sha::bytes(&bytes);
    label(report, "original_sha256", &original_digest);
    label(report, "kernel_release", image.field("ksu_kernel_release")?);
    label(report, "signature_trust", "not_verified");
    if mode == "inspect" {
        if ["--proof", "--symvers", "--config", "--experimental"]
            .iter()
            .any(|key| flags.contains_key(*key))
        {
            return Err("inspect takes no proof/load options".into());
        }
        label(report, "stage", "inspected");
        return Ok(());
    }
    let required = |key: &str| -> Outcome<&str> {
        flags
            .get(key)
            .map(String::as_str)
            .ok_or_else(|| format!("Missing {key}"))
    };
    let proof_path = required("--proof")?;
    let target = proof(proof_path)?;
    let profile_pins_match = sha::bytes(&read_file(proof_path, 8192)?) == REVIEWED_INPUT_PROFILE;
    report.insert(
        "reviewed_input_profile".into(),
        profile_pins_match.to_string(),
    );
    report.insert("private_abi_approved".into(), "false".into());
    label(report, "stage", "proof");
    let private = validate_files(
        &image,
        &original_digest,
        &target,
        required("--symvers")?,
        required("--config")?,
    )?;
    number(report, "private_imports", private);
    label(report, "source_commit_claim", &target["source_commit"]);
    label(
        report,
        "proof_trust",
        "user_supplied_pins_not_publisher_signature",
    );
    if Instant::now() > deadline {
        return Err("Cooperative preflight deadline exceeded; no syscall attempted".into());
    }
    if mode == "plan" {
        number(
            report,
            "kernel_resolved_imports",
            image.imports.len() - private,
        );
        number(report, "private_bindings_pending_approval", private);
        let inventory = image
            .imports
            .iter()
            .map(|entry| {
                let public = image.versions.contains_key(&entry.name);
                format!(
                    "{{\"name\":{},\"kind\":{},\"private_type_evidence\":\"unclassified\"}}",
                    quoted(&entry.name),
                    quoted(if public {
                        "kernel_export_with_crc"
                    } else {
                        "private_pending_evidence"
                    })
                )
            })
            .collect::<Vec<_>>()
            .join(",");
        label(
            report,
            "inventory_sha256",
            &sha::bytes(inventory.as_bytes()),
        );
        report.insert("import_inventory".into(), format!("[{inventory}]"));
        label(
            report,
            "planned_strategy",
            "hybrid_keep_public_imports_undefined",
        );
        report.insert("load_permitted".into(), "false".into());
        label(report, "stage", "offline_plan_private_abi_campaign_pending");
        return Ok(());
    }
    if mode == "check-device" {
        label(report, "stage", "device_identity_partial");
        device_matches(&target)?;
        report.insert("runtime_abi_verified".into(), "false".into());
        return Ok(());
    }
    if mode == "check" {
        if flags.contains_key("--experimental") {
            return Err("Experimental load flag is not accepted for check".into());
        }
        label(report, "stage", "offline_checked");
        return Ok(());
    }
    if !flags.contains_key("--experimental") {
        return Err("Loading requires explicit --experimental; inspect/check are read-only".into());
    }
    if image.signed {
        return Err(
            "Signed LKM is unsupported by private-symbol strategy; it will not be altered".into(),
        );
    }
    label(report, "stage", "campaign_gate");
    if !profile_pins_match {
        return Err("Load profile is not the reviewed input snapshot".into());
    }
    // Matching input pins do not approve unclassified private ABI/CFI or a runtime campaign.
    require_campaign_approval()?;
    label(report, "stage", "device");
    device_matches(&target)?;
    label(
        report,
        "device_identity_evidence",
        "running_release_and_page_size_only",
    );
    report.insert("runtime_abi_verified".into(), "false".into());
    let _lock = lock_load()?;
    if std::path::Path::new(&format!("/sys/module/{}", image.module_name()?)).exists() {
        return Err("Module is already present; unload is not performed automatically".into());
    }
    label(report, "stage", "symbols");
    let table =
        File::open("/proc/kallsyms").map_err(|e| format!("Read privileged kernel symbols: {e}"))?;
    let public = image
        .versions
        .keys()
        .filter(|name| name.as_str() != "module_layout")
        .cloned()
        .collect();
    let private_image = image.private_image(&public);
    let (resolved, scanned) = addresses(
        &private_image,
        BufReader::with_capacity(preferences.number("io_buffer_kib") * 1024, table),
    )?;
    number(report, "symbol_rows_scanned", scanned);
    image.patch_hybrid(&mut bytes, &resolved, &public)?;
    label(report, "submitted_sha256", &sha::bytes(&bytes));
    label(report, "strategy", "experimental_private_symbols");
    label(report, "stage", "kernel_load");
    let mut diagnostics = if preferences.enabled("kernel_log") {
        log_cursor()
    } else {
        None
    };
    let params = CString::new("").unwrap();
    // SAFETY: ARM64 is checked above. Linux ARM64 init_module syscall number is
    // 105; the allocation and terminated parameter string remain live throughout.
    // No ignore-vermagic/modversions flags or signature stripping are used.
    report.insert("load_attempted".into(), "true".into());
    let result = unsafe { syscall(105, bytes.as_ptr(), bytes.len(), params.as_ptr()) };
    if result != 0 {
        let error = std::io::Error::last_os_error();
        report.insert(
            "kernel_errno".into(),
            error.raw_os_error().unwrap_or(0).to_string(),
        );
        if let Some(cursor) = diagnostics.as_mut() {
            label(
                report,
                "kernel_messages",
                &redact(&log_tail(
                    cursor,
                    preferences.number("log_limit_kib") * 1024,
                )),
            );
        }
        return Err(format!(
            "Kernel rejected experimental module: {error}; no retry or force-load performed"
        ));
    }
    if !std::path::Path::new(&format!("/sys/module/{}", image.module_name()?)).exists() {
        return Err("Kernel returned success but module presence could not be confirmed".into());
    }
    label(report, "stage", "module_present_root_function_not_verified");
    Ok(())
}

fn main() {
    let start = Instant::now();
    let args: Vec<String> = std::env::args().skip(1).collect();
    let mut report = Report::new();
    report.insert("schema".into(), "1".into());
    report.insert("experimental".into(), "true".into());
    report.insert("device_tested".into(), "false".into());
    report.insert("load_attempted".into(), "false".into());
    let result = execute(&args, &mut report);
    report.insert("success".into(), result.is_ok().to_string());
    if let Err(error) = &result {
        label(&mut report, "error", error);
        let reason = if error.starts_with("commit_uncertain:") {
            "commit_uncertain"
        } else {
            match report.get("stage").map(String::as_str) {
                Some("\"read\"") => "input_rejected",
                Some("\"elf\"") => "elf_rejected",
                Some("\"proof\"") => "target_proof_rejected",
                Some("\"campaign_gate\"") => "campaign_not_approved",
                Some("\"device\"") | Some("\"device_identity_partial\"") => {
                    "device_context_rejected"
                }
                Some("\"kernel_load\"") => "kernel_rejected",
                _ => "request_or_preferences_rejected",
            }
        };
        label(&mut report, "reason_code", reason);
    } else {
        label(&mut report, "reason_code", "ok");
    }
    // /proc/self/status reports this executable's post-exec memory high-water
    // mark; parent/spawn wait4 usage can include memory from before exec.
    if let Ok(file) = File::open("/proc/self/status") {
        let mut status = String::new();
        if file.take(16384).read_to_string(&mut status).is_ok()
            && let Some(peak) = status.lines().find_map(|line| {
                line.strip_prefix("VmHWM:")?
                    .split_whitespace()
                    .next()?
                    .parse::<usize>()
                    .ok()
            })
        {
            number(&mut report, "post_exec_peak_rss_kib", peak);
        }
    }
    report.insert("elapsed_us".into(), start.elapsed().as_micros().to_string());
    if report
        .get("output")
        .is_some_and(|value| value == "\"text\"")
    {
        for (key, value) in &report {
            println!("{key}: {value}");
        }
    } else {
        println!(
            "{{{}}}",
            report
                .iter()
                .map(|(key, value)| format!("{}:{value}", quoted(key)))
                .collect::<Vec<_>>()
                .join(",")
        );
    }
    if result.is_err() {
        std::process::exit(2);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn model() -> Image {
        Image {
            machine: 183,
            info: BTreeMap::new(),
            imports: vec![image::Import {
                name: "probe".into(),
                position: 0,
            }],
            versions: BTreeMap::new(),
            signed: false,
        }
    }
    #[test]
    fn symbol_resolution_rejects_hidden_missing_and_ambiguity() {
        let image = model();
        for raw in [
            "0000 T probe\n",
            "1234 T other\n",
            "1234 T probe\n5678 T probe\n",
            "broken\n",
        ] {
            assert!(addresses(&image, raw.as_bytes()).is_err());
        }
        let data = "1234 T probe.llvm.x\n1234 T probe$x\n1234 T probe [other]\n";
        assert_eq!(
            addresses(&image, data.as_bytes()).unwrap().0["probe"],
            0x1234
        );
    }
    #[test]
    fn mutation_refuses_signed_or_incomplete_inputs() {
        let mut image = model();
        let mut payload = vec![0; 24];
        assert!(image.patch(&mut payload, &BTreeMap::new()).is_err());
        assert_eq!(payload, vec![0; 24]);
        assert!(
            image
                .patch(&mut [0; 8], &BTreeMap::from([("probe".into(), 1)]))
                .is_err()
        );
        image.signed = true;
        assert!(
            image
                .patch(&mut payload, &BTreeMap::from([("probe".into(), 1)]))
                .is_err()
        );
        assert_eq!(payload, vec![0; 24]);
    }
    #[test]
    fn campaign_gate_and_preference_operations_cannot_authorize_loading() {
        assert!(require_campaign_approval().is_err());
        let mut report = Report::new();
        assert!(execute(&["settings".into(), "import".into()], &mut report).is_err());
        assert!(
            execute(
                &["settings".into(), "show".into(), "--experimental".into()],
                &mut report
            )
            .is_err()
        );
        assert!(
            execute(
                &["settings".into(), "reset".into(), "--force".into()],
                &mut report
            )
            .is_err()
        );
        assert!(redact("trace ffff8000001000 some text").contains("[address-redacted]"));
        assert_eq!(
            redact("pc=0xffff8000001000 [<ffff8000002000>]"),
            "[address-redacted] [address-redacted]"
        );
    }
    #[test]
    fn strict_options_and_json_escaping() {
        assert!(options(&["load".into(), "file".into(), "--force".into()]).is_err());
        assert!(options(&["load".into(), "file".into(), "--proof".into()]).is_err());
        assert_eq!(quoted("a\n\"\\\u{0001}"), "\"a\\n\\\"\\\\\\u0001\"");
    }
    #[test]
    #[ignore = "requires HUSKY_LKM_FIXTURE and HUSKY_PROOF_DIR"]
    fn actual_candidate_and_rejection_paths() {
        let path = std::env::var("HUSKY_LKM_FIXTURE").unwrap();
        let root = std::env::var("HUSKY_PROOF_DIR").unwrap();
        let mut data = std::fs::read(&path).unwrap();
        let image = Image::parse(&data).unwrap();
        let proof = proof(&format!("{root}/target.proof")).unwrap();
        let symvers = format!("{root}/Module.symvers");
        let config = format!("{root}/kernel.config");
        assert_eq!(
            validate_files(&image, &sha::bytes(&data), &proof, &symvers, &config).unwrap(),
            71
        );
        assert_eq!(image.imports.len(), 206);
        let mut wrong = proof.clone();
        wrong.insert("module_sha256".into(), "0".repeat(64));
        assert!(
            validate_files(&image, &sha::bytes(&data), &wrong, &symvers, &config)
                .unwrap_err()
                .contains("SHA-256")
        );
        let mut incomplete = Image::parse(&data).unwrap();
        let missing = incomplete
            .versions
            .keys()
            .find(|name| name.as_str() != "module_layout")
            .unwrap()
            .clone();
        incomplete.versions.remove(&missing);
        assert!(
            validate_files(&incomplete, &sha::bytes(&data), &proof, &symvers, &config)
                .unwrap_err()
                .contains("CRC")
        );
        wrong = proof.clone();
        wrong.insert("private_imports".into(), "72".into());
        assert!(validate_files(&image, &sha::bytes(&data), &wrong, &symvers, &config).is_err());
        let fake: BTreeMap<_, _> = image
            .imports
            .iter()
            .enumerate()
            .map(|(i, v)| (v.name.clone(), 0xffff000000001000 + i as u64))
            .collect();
        let original = data.clone();
        let public = image
            .versions
            .keys()
            .filter(|name| name.as_str() != "module_layout")
            .cloned()
            .collect();
        image.patch_hybrid(&mut data, &fake, &public).unwrap();
        let hybrid = Image::parse(&data).unwrap();
        assert_eq!(hybrid.imports.len(), 135);
        assert_eq!(hybrid.versions, image.versions);
        for entry in image
            .imports
            .iter()
            .filter(|entry| image.versions.contains_key(&entry.name))
        {
            assert_eq!(
                &data[entry.position..entry.position + 24],
                &original[entry.position..entry.position + 24]
            );
        }
        data = original;
        image.patch(&mut data, &fake).unwrap();
        assert!(Image::parse(&data).unwrap().imports.is_empty());
        assert!(validate_files(&image, &sha::bytes(&data), &proof, &symvers, &config).is_err());
        for size in [0, 63, 64, 128] {
            assert!(Image::parse(&data[..size]).is_err());
        }
        let mut malformed = data.clone();
        malformed[40..48].copy_from_slice(&u64::MAX.to_le_bytes());
        assert!(Image::parse(&malformed).is_err());
    }
}
