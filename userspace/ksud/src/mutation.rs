//! One cooperating owner for boot/LKM changes across Manager and CLI processes.
use anyhow::{Context, Result, ensure};
use std::fs::{DirBuilder, File, OpenOptions};
use std::os::unix::fs::{DirBuilderExt, MetadataExt, OpenOptionsExt};

pub struct MutationLock {
    _file: File,
}

impl MutationLock {
    pub fn acquire() -> Result<Self> {
        ensure!(
            rustix::process::geteuid().as_raw() == 0,
            "boot/LKM mutation requires root"
        );
        match DirBuilder::new().mode(0o700).create("/data/adb") {
            Ok(()) => {}
            Err(error) if error.kind() == std::io::ErrorKind::AlreadyExists => {}
            Err(error) => return Err(error).context("prepare root task directory"),
        }
        let parent = std::fs::symlink_metadata("/data/adb")?;
        ensure!(
            parent.is_dir() && parent.uid() == 0,
            "root task directory must be a root-owned directory"
        );
        let file = OpenOptions::new()
            .create(true)
            .truncate(false)
            .read(true)
            .write(true)
            .mode(0o600)
            .custom_flags(libc::O_NOFOLLOW)
            .open("/data/adb/.ksu-boot.lock")
            .context("open device-wide boot mutation lock")?;
        ensure!(
            file.metadata()?.uid() == 0 && file.metadata()?.is_file(),
            "boot mutation lock must be root-owned"
        );
        file.try_lock()
            .context("another boot/LKM operation is active (TASK_BUSY)")?;
        Ok(Self { _file: file })
    }
}
