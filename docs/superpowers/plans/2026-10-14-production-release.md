# Production-signed HuskySU release

User explicitly requested a production release after installing the PR APK.

- [x] Prepare production workflow: require ksud JNI binaries before Gradle,
  run Manager tests, verify final package/certificate/version and record hashes.
- [ ] Commit/push the reviewed release source, with device evidence excluded.
- [ ] Dispatch Husky LKM Release using existing repository signing secrets.
  Bind the release tag to the exact built commit; never use Debug fallback.
- [ ] Verify successful jobs and published assets; return the Release URL.

The existing production flow builds generic android14-6.1/android16-6.12 modules.
These are not the exact 6.1.172 Canary candidate installed on the test phone.
Release metadata must state that boundary; no new device-support claim, automatic
flash, independent experimental-loader enablement or device replacement occurs.
Publish from the committed release branch so the validated tag identifies its
source, without implicitly triggering the main-branch Telegram upload workflow.

Local production identity tests passed (3 tests, including wrong signer/package,
version, multiple signers and Debug rejection). Sandbox temp-directory cleanup
was denied; the same tests passed outside the sandbox. Full Android/Rust checks
will run in the production workflow; no local Android SDK is available.

First production run 38466425466 passed LKM, ksuinit, Android ksud checks and
Manager clean assembleRelease/tests, then failed the final signer comparison.
No Release was published. The Gradle APK independently verifies using Android's
official apksig 9.3.1 library, with the trusted b37ef4db... certificate. The final
verification now uses the official ApkVerifier certificate API directly, rather
than parsing apksigner prose, and reports observed digests on mismatch.
