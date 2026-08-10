package msb.com.vn.crypto;

/**
 * Bộ thuật toán băm dùng kèm RSA — cho phép nâng cấp bảo mật về sau
 * mà không phải sửa logic (chỉ đổi enum).
 *
 * Mỗi giá trị gom đủ 3 chuẩn tương ứng:
 *  - {@link #digest}             : thuật toán hash thuần (MessageDigest)
 *  - {@link #signatureAlgorithm} : thuật toán ký RSA (Signature)
 *  - {@link #rsaTransform}       : transform RSA-OAEP (Cipher)
 *
 * Mặc định toàn hệ thống dùng {@link #SHA_256}. Khi cần tăng cường bảo mật,
 * chỉ việc khởi tạo {@code new CryptoService(RsaHashAlgorithm.SHA_512)} hoặc
 * truyền algorithm vào từng lời gọi.
 */
public enum RsaHashAlgorithm {

    SHA_256("SHA-256", "SHA256withRSA", "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"),
    SHA_512("SHA-512", "SHA512withRSA", "RSA/ECB/OAEPWithSHA-512AndMGF1Padding");

    private final String digest;
    private final String signatureAlgorithm;
    private final String rsaTransform;

    RsaHashAlgorithm(String digest, String signatureAlgorithm, String rsaTransform) {
        this.digest = digest;
        this.signatureAlgorithm = signatureAlgorithm;
        this.rsaTransform = rsaTransform;
    }

    /** Tên thuật toán cho {@link java.security.MessageDigest} (vd: "SHA-256"). */
    public String digest() {
        return digest;
    }

    /** Tên thuật toán cho {@link java.security.Signature} (vd: "SHA256withRSA"). */
    public String signatureAlgorithm() {
        return signatureAlgorithm;
    }

    /** Transform cho {@link javax.crypto.Cipher} RSA-OAEP. */
    public String rsaTransform() {
        return rsaTransform;
    }
}
