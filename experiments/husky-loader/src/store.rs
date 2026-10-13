//! Explicit preference writes using a pinned private directory and a separate lock.
use crate::image::Outcome;
use crate::settings::Saved;
use std::fs::{File, OpenOptions};
use std::io::{Read, Write};
use std::os::fd::AsRawFd;
use std::os::unix::fs::{MetadataExt, OpenOptionsExt};
use std::path::Path;

const NOFOLLOW: i32 = 0x20000;
fn private(file: &File) -> Outcome<()> {
    let metadata = file.metadata().map_err(|e| e.to_string())?;
    // SAFETY: geteuid has no arguments or side effects.
    if !metadata.is_file()
        || metadata.uid() != unsafe { crate::geteuid() }
        || metadata.nlink() != 1
        || metadata.mode() & 0o077 != 0
    {
        return Err(
            "Preference file must be private, singly linked and owned by the current UID".into(),
        );
    }
    Ok(())
}
fn read(path: &Path) -> Outcome<Option<Saved>> {
    let file = match OpenOptions::new()
        .read(true)
        .custom_flags(NOFOLLOW)
        .open(path)
    {
        Ok(file) => file,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(None),
        Err(error) => return Err(error.to_string()),
    };
    private(&file)?;
    let mut bytes = Vec::new();
    file.take(16385)
        .read_to_end(&mut bytes)
        .map_err(|e| e.to_string())?;
    Saved::parse(&bytes).map(Some)
}

pub fn save(path: &str, expected_generation: u64, mut next: Saved) -> Outcome<u64> {
    let path = Path::new(path);
    let parent = path
        .parent()
        .filter(|p| !p.as_os_str().is_empty())
        .ok_or("Store needs an explicit parent directory")?;
    let name = path
        .file_name()
        .and_then(|n| n.to_str())
        .ok_or("Invalid preference filename")?;
    if name.len() > 120
        || !name
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b'_' || b == b'.')
    {
        return Err("Invalid preference filename".into());
    }
    let dir = OpenOptions::new()
        .read(true)
        .custom_flags(NOFOLLOW | 0x10000)
        .open(parent)
        .map_err(|e| format!("Open existing private settings directory: {e}"))?;
    let meta = dir.metadata().map_err(|e| e.to_string())?;
    // SAFETY: geteuid has no arguments or side effects.
    if !meta.is_dir() || meta.uid() != unsafe { crate::geteuid() } || meta.mode() & 0o077 != 0 {
        return Err(
            "Preference store directory must be private and owned by the current UID".into(),
        );
    }
    // Every operation below resolves relative to this live directory FD, even if its path is renamed.
    let pinned = std::path::PathBuf::from(format!("/proc/self/fd/{}", dir.as_raw_fd()));
    let lock = OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .truncate(false)
        .custom_flags(NOFOLLOW)
        .mode(0o600)
        .open(pinned.join(format!(".{name}.lock")))
        .map_err(|e| e.to_string())?;
    private(&lock)?;
    // SAFETY: lock owns this live FD. LOCK_EX|LOCK_NB contains no pointer arguments.
    if unsafe { crate::flock(lock.as_raw_fd(), 2 | 4) } != 0 {
        return Err("Settings writer busy".into());
    }
    let active = pinned.join(name);
    let old = read(&active)?;
    let generation = old.as_ref().map_or(0, |saved| saved.generation);
    if generation != expected_generation {
        return Err("Stale settings generation; re-read before saving".into());
    }
    next.generation = generation
        .checked_add(1)
        .ok_or("Settings generation exhausted")?;
    let content = next.serialize();
    Saved::parse(content.as_bytes())?;
    // Last-good and active are separately committed; they are not a multi-file transaction.
    if let Some(previous) = &old {
        let backup = pinned.join(format!("{name}.last-good"));
        let temporary_backup = pinned.join(format!(".{name}.{}.backup-part", std::process::id()));
        let mut file = OpenOptions::new()
            .write(true)
            .create_new(true)
            .custom_flags(NOFOLLOW)
            .mode(0o600)
            .open(&temporary_backup)
            .map_err(|e| e.to_string())?;
        let result = (|| -> Outcome<()> {
            file.write_all(previous.serialize().as_bytes())
                .map_err(|e| e.to_string())?;
            file.sync_all().map_err(|e| e.to_string())?;
            std::fs::rename(&temporary_backup, backup).map_err(|e| e.to_string())?;
            dir.sync_all()
                .map_err(|e| format!("Backup durability failed before active commit: {e}"))?;
            Ok(())
        })();
        if result.is_err() {
            let _ = std::fs::remove_file(&temporary_backup);
        }
        result?;
    }
    let temporary = pinned.join(format!(".{name}.{}.part", std::process::id()));
    let mut created = false;
    let mut write = || -> Outcome<()> {
        let mut file = OpenOptions::new()
            .write(true)
            .create_new(true)
            .custom_flags(NOFOLLOW)
            .mode(0o600)
            .open(&temporary)
            .map_err(|e| e.to_string())?;
        created = true;
        file.write_all(content.as_bytes())
            .map_err(|e| e.to_string())?;
        file.sync_all().map_err(|e| e.to_string())?;
        std::fs::rename(&temporary, &active).map_err(|e| e.to_string())?;
        dir.sync_all().map_err(|e| {
            format!(
                "commit_uncertain: active generation {} is visible but directory sync failed: {e}",
                next.generation
            )
        })?;
        Ok(())
    };
    let result = write();
    // Never remove a pre-existing .part file owned by another interrupted writer.
    if result.is_ok() || !created || !temporary.exists() {
        return result.map(|_| next.generation);
    }
    // The separate lock excludes another conforming writer; stale temp files are not followed.
    let _ = std::fs::remove_file(&temporary);
    result.map(|_| next.generation)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::os::unix::fs::{PermissionsExt, symlink};
    #[test]
    fn saves_atomically_and_rejects_stale_or_symlink_records() {
        let root = std::env::temp_dir().join(format!("husky-settings-{}", std::process::id()));
        std::fs::create_dir(&root).unwrap();
        std::fs::set_permissions(&root, std::fs::Permissions::from_mode(0o700)).unwrap();
        let path = root.join("settings.conf");
        let target = path.to_str().unwrap();
        assert_eq!(save(target, 0, Saved::defaults()).unwrap(), 1);
        assert!(
            save(target, 0, Saved::defaults())
                .unwrap_err()
                .contains("Stale")
        );
        assert_eq!(save(target, 1, Saved::defaults()).unwrap(), 2);
        assert_eq!(
            read(&root.join("settings.conf.last-good"))
                .unwrap()
                .unwrap()
                .generation,
            1
        );
        std::fs::remove_file(&path).unwrap();
        symlink("/dev/null", &path).unwrap();
        assert!(save(target, 0, Saved::defaults()).is_err());
        std::fs::remove_dir_all(root).unwrap();
    }
}
