//! Optional real-image regression: synthetic tests remain runnable without firmware.
use ksu_contracts::{KernelTarget, inspect_kernel, validate_module};

#[test]
fn researched_canary_default_kernel() {
    let Ok(directory) = std::env::var("KSU_CANARY_FIXTURE_DIR") else {
        return;
    };
    let image = std::fs::read(std::path::Path::new(&directory).join("kernel-image.bin")).unwrap();
    let target = inspect_kernel(&image, 183).unwrap();
    assert_eq!(
        target.release,
        "6.1.172-android14-11-g3fc92ed75e44-ab16169542"
    );
    assert_eq!(target.kmi, "android14-6.1");
    assert_eq!(target.page_size, 4096);
}

#[test]
fn built_ddk_module_is_not_a_canary_candidate() {
    let Ok(path) = std::env::var("KSU_DDK_MODULE_FIXTURE") else {
        return;
    };
    let module = std::fs::read(path).unwrap();
    let build_target = KernelTarget::new("6.1.166-dirty", 4096, 183).unwrap_err();
    assert!(build_target.to_string().contains("KMI"));
    // DDK uname omits the Android tag; the build's explicit KMI supplies it.
    let build_target = KernelTarget {
        release: "6.1.166-dirty".into(),
        kmi: "android14-6.1".into(),
        page_size: 4096,
        machine: 183,
    };
    validate_module(&module, &build_target).unwrap();
    let canary =
        KernelTarget::new("6.1.172-android14-11-g3fc92ed75e44-ab16169542", 4096, 183).unwrap();
    assert!(validate_module(&module, &canary).is_err());
    let sixteen_k = KernelTarget {
        page_size: 16384,
        ..build_target
    };
    assert!(validate_module(&module, &sixteen_k).is_err());
}
