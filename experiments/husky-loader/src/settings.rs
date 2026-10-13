//! Independent bounded preferences. They cannot modify target proof or authorize loading.
use crate::image::Outcome;
use std::collections::BTreeMap;

#[derive(Clone, Debug, Default)]
pub struct Saved {
    pub generation: u64,
    pub preset: String,
    pub overrides: BTreeMap<String, String>,
}
#[derive(Clone, Debug)]
pub struct Settings {
    pub values: BTreeMap<String, String>,
    pub origins: BTreeMap<String, String>,
}
const KEYS: [&str; 9] = [
    "output",
    "verbosity",
    "kernel_log",
    "log_limit_kib",
    "history_count",
    "io_buffer_kib",
    "module_limit_mib",
    "cache_enabled",
    "deadline_seconds",
];
const PRESET_KEYS: [&str; 4] = [
    "verbosity",
    "log_limit_kib",
    "history_count",
    "io_buffer_kib",
];

fn preset(name: &str) -> Outcome<BTreeMap<String, String>> {
    let (verbosity, log, history, buffer) = match name {
        "balanced" => ("normal", "32", "0", "16"),
        "lean" => ("error", "8", "0", "8"),
        "diagnostic" => ("debug", "64", "0", "32"),
        _ => return Err("Unknown preset; use balanced, lean or diagnostic".into()),
    };
    Ok(BTreeMap::from([
        ("verbosity".into(), verbosity.into()),
        ("log_limit_kib".into(), log.into()),
        ("history_count".into(), history.into()),
        ("io_buffer_kib".into(), buffer.into()),
    ]))
}
fn validate(key: &str, value: &str) -> Outcome<()> {
    let integer = |min: u32, max: u32| {
        value.parse::<u32>().is_ok_and(|n| n >= min && n <= max)
            && value.bytes().all(|b| b.is_ascii_digit())
    };
    let valid = match key {
        "output" => ["json", "text"].contains(&value),
        "verbosity" => ["error", "normal", "debug"].contains(&value),
        "kernel_log" => ["true", "false"].contains(&value),
        "log_limit_kib" => integer(4, 64),
        "history_count" => value == "0", // Persistent attempt records require the device campaign gate.
        "io_buffer_kib" => ["8", "16", "32", "64"].contains(&value),
        "module_limit_mib" => integer(1, 16),
        "cache_enabled" => value == "false", // Address caching is never accepted.
        "deadline_seconds" => integer(1, 30),
        _ => false,
    };
    if valid {
        Ok(())
    } else {
        Err(format!(
            "Invalid, unsupported or immutable preference: {key}"
        ))
    }
}

impl Saved {
    pub fn parse(bytes: &[u8]) -> Outcome<Self> {
        if bytes.len() > 16384 {
            return Err("Settings exceed 16 KiB".into());
        }
        let source = std::str::from_utf8(bytes).map_err(|_| "Settings are not UTF-8")?;
        let mut fields = BTreeMap::new();
        for line in source
            .lines()
            .map(str::trim)
            .filter(|line| !line.is_empty() && !line.starts_with('#'))
        {
            if line.len() > 1024 || fields.len() >= 64 {
                return Err("Settings record limit exceeded".into());
            }
            let (key, value) = line
                .split_once('=')
                .ok_or("Settings require key=value records")?;
            if value.is_empty() || fields.insert(key.to_owned(), value.to_owned()).is_some() {
                return Err("Empty or duplicate settings field".into());
            }
        }
        if fields.remove("schema").as_deref() != Some("1") {
            return Err("Unsupported settings schema".into());
        }
        let raw_generation = fields.remove("generation").unwrap_or_else(|| "0".into());
        if !raw_generation.bytes().all(|byte| byte.is_ascii_digit()) {
            return Err("Invalid settings generation".into());
        }
        let generation = raw_generation
            .parse::<u64>()
            .map_err(|_| "Invalid settings generation")?;
        if generation > i64::MAX as u64 {
            return Err("Settings generation exceeds interchange limit".into());
        }
        let selected = fields.remove("preset").unwrap_or_else(|| "balanced".into());
        preset(&selected)?;
        for (key, value) in &fields {
            validate(key, value)?;
        }
        Ok(Self {
            generation,
            preset: selected,
            overrides: fields,
        })
    }
    pub fn defaults() -> Self {
        Self {
            preset: "balanced".into(),
            ..Self::default()
        }
    }
    pub fn serialize(&self) -> String {
        let mut out = format!(
            "schema=1\ngeneration={}\npreset={}\n",
            self.generation, self.preset
        );
        for (key, value) in &self.overrides {
            out.push_str(&format!("{key}={value}\n"));
        }
        out
    }
}
impl Settings {
    pub fn resolve(
        saved: &Saved,
        one_shot_preset: Option<&str>,
        overrides: &[String],
    ) -> Outcome<Self> {
        let mut values = BTreeMap::from([
            ("output".into(), "json".into()),
            ("kernel_log".into(), "true".into()),
            ("module_limit_mib".into(), "16".into()),
            ("cache_enabled".into(), "false".into()),
            ("deadline_seconds".into(), "5".into()),
        ]);
        let mut origins = values
            .keys()
            .map(|key: &String| (key.clone(), "default".into()))
            .collect::<BTreeMap<_, _>>();
        for (key, value) in preset(one_shot_preset.unwrap_or(&saved.preset))? {
            origins.insert(
                key.clone(),
                if one_shot_preset.is_some() {
                    "one-shot-preset"
                } else {
                    "preset"
                }
                .into(),
            );
            values.insert(key, value);
        }
        for (key, value) in &saved.overrides {
            validate(key, value)?;
            if one_shot_preset.is_some() && PRESET_KEYS.contains(&key.as_str()) {
                continue;
            }
            values.insert(key.clone(), value.clone());
            origins.insert(key.clone(), "saved".into());
        }
        if overrides.len() > 64 {
            return Err("Too many preference overrides".into());
        }
        let mut seen = BTreeMap::new();
        for entry in overrides {
            let (key, value) = entry.split_once('=').ok_or("--set requires key=value")?;
            validate(key, value)?;
            if seen.insert(key, value).is_some() {
                return Err("Repeated one-shot setting".into());
            }
            values.insert(key.into(), value.into());
            origins.insert(key.into(), "one-shot".into());
        }
        Ok(Self { values, origins })
    }
    pub fn number(&self, key: &str) -> usize {
        self.values[key].parse().unwrap()
    }
    pub fn enabled(&self, key: &str) -> bool {
        self.values[key] == "true"
    }
    pub fn snapshot(&self) -> String {
        KEYS.iter()
            .map(|key| format!("{key}={}\n", self.values[*key]))
            .collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn rejects_ambiguous_and_policy_overrides() {
        for data in [
            "schema=2",
            "schema=1\noutput=xml",
            "schema=1\nforce=true",
            "schema=1\nmodule_limit_mib=17",
            "schema=1\noutput=json\noutput=text",
            "schema=1\ncache_enabled=true",
            "schema=1\nhistory_count=5",
        ] {
            assert!(Saved::parse(data.as_bytes()).is_err(), "{data}");
        }
        assert!(Saved::parse(&vec![b'x'; 16385]).is_err());
    }
    #[test]
    fn one_shot_preset_discards_only_preset_governed_saved_overrides() {
        let saved =
            Saved::parse(b"schema=1\npreset=balanced\nio_buffer_kib=64\nmodule_limit_mib=4")
                .unwrap();
        assert_eq!(
            Settings::resolve(&saved, None, &[])
                .unwrap()
                .number("io_buffer_kib"),
            64
        );
        let effective =
            Settings::resolve(&saved, Some("lean"), &["verbosity=debug".into()]).unwrap();
        assert_eq!(effective.number("io_buffer_kib"), 8);
        assert_eq!(effective.number("module_limit_mib"), 4);
        assert_eq!(effective.origins["module_limit_mib"], "saved");
        assert_eq!(effective.origins["verbosity"], "one-shot");
        assert_eq!(saved.overrides["io_buffer_kib"], "64");
    }
    #[test]
    fn canonical_round_trip_and_bounds() {
        let saved =
            Saved::parse(b"schema=1\ngeneration=7\npreset=diagnostic\nkernel_log=false").unwrap();
        assert_eq!(
            Saved::parse(saved.serialize().as_bytes())
                .unwrap()
                .generation,
            7
        );
        assert!(
            !Settings::resolve(&saved, None, &[])
                .unwrap()
                .enabled("kernel_log")
        );
        assert!(Settings::resolve(&saved, None, &["log_limit_kib=3".into()]).is_err());
    }
}
