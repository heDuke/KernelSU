import com.android.apksig.ApkVerifier;
import java.io.File;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Verify integrity and expose signer identities without parsing CLI prose. */
class VerifyApkSigner {
    public static void main(String[] args) throws Exception {
        var result = new ApkVerifier.Builder(new File(args[0])).build().verify();
        if (!result.isVerified()) {
            throw new IllegalArgumentException("APK signature invalid: " + result.getErrors());
        }
        if (result.getSignerCertificates().size() != 1) {
            throw new IllegalArgumentException("Production APK requires exactly one signer");
        }
        for (var certificate : result.getSignerCertificates()) {
            System.out.println(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded())));
        }
    }
}
