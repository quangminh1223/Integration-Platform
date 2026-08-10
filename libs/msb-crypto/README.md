# msb-crypto

Thư viện crypto tái sử dụng cho mọi project: RSA encrypt/decrypt, SHA256withRSA sign/verify, SHA-256 hash, và hybrid RSA+AES-128-GCM cho payload lớn.

## Build & cài vào local Maven repo

```bash
cd libs/msb-crypto
mvn clean install
```

Lệnh này build + chạy test + cài jar vào `~/.m2/repository/msb/com/vn/msb-crypto/1.0.0/`.

## Dùng ở project khác

Thêm vào `pom.xml`:

```xml
<dependency>
    <groupId>msb.com.vn</groupId>
    <artifactId>msb-crypto</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Cách dùng

### Project Spring Boot (tự động có bean)

```java
@Service
@RequiredArgsConstructor
public class MyService {
    private final CryptoService cryptoService;          // auto-injected
    private final HybridCryptoService hybridCryptoService; // auto-injected

    public void demo() {
        String hash = cryptoService.hashSha256("data");
        String enc  = cryptoService.encryptRsa("secret", publicKey);
        String dec  = cryptoService.decryptRsa(enc, privateKey);
        String sig  = cryptoService.sign("msg", privateKey);
        boolean ok  = cryptoService.verify("msg", sig, publicKey);

        // Payload lớn
        String envelope = hybridCryptoService.encrypt(bigPayload, publicKey);
        String back     = hybridCryptoService.decrypt(envelope, privateKey);
    }
}
```

### Project plain Java (không Spring)

```java
CryptoService crypto = new CryptoService();
HybridCryptoService hybrid = new HybridCryptoService();

String hash = crypto.hashSha256("data");
String envelope = hybrid.encrypt(payload, publicKey);
```

## API

| Method | Mô tả |
|--------|-------|
| `hashSha256(data)` | SHA-256 → hex |
| `hashSha256Base64(data)` | SHA-256 → Base64 |
| `encryptRsa(plain, pubKey)` | RSA-OAEP-SHA256 encrypt (payload nhỏ) |
| `decryptRsa(cipher, privKey)` | RSA decrypt |
| `sign(data, privKey)` | Ký SHA256withRSA |
| `verify(data, sig, pubKey)` | Verify chữ ký |
| `generateKeyPair(size)` | Sinh cặp key test |
| `HybridCryptoService.encrypt(payload, pubKey)` | RSA+AES-128-GCM (payload lớn) |
| `HybridCryptoService.decrypt(envelope, privKey)` | Giải mã hybrid |
| `encryptAndSign(...)` / `verifyAndDecrypt(...)` | Mã hóa + ký kết hợp |

## Thiết kế

- **Core** (`CryptoService`, `HybridCryptoService`) — framework-agnostic, chỉ phụ thuộc Jackson
- **Spring auto-config** (`CryptoAutoConfiguration`) — optional, chỉ active khi có Spring Boot
- Dùng `@ConditionalOnMissingBean` → project có thể override bean riêng
```
