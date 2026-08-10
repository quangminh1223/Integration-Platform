package msb.com.vn.crypto.spring;

import msb.com.vn.crypto.CryptoException;
import msb.com.vn.crypto.CryptoService;
import msb.com.vn.crypto.HybridCryptoService;
import msb.com.vn.crypto.JsonMessageSigner;
import msb.com.vn.crypto.KeyStoreService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;

/**
 * Spring Boot auto-configuration cho msb-crypto.
 *
 * Khi project Spring add dependency msb-crypto, các bean CryptoService,
 * HybridCryptoService, JsonMessageSigner tự động được tạo.
 *
 * Nếu cấu hình {@code crypto.keystore.path}, thêm bean {@link KeyStoreService}
 * load sẵn keystore JKS (chứa private key của mình + public key đối tác).
 *
 * Project KHÔNG dùng Spring vẫn dùng được core class (new CryptoService()).
 *
 * @ConditionalOnMissingBean: nếu project tự định nghĩa bean riêng thì lib nhường.
 */
@Configuration
@EnableConfigurationProperties(CryptoKeyStoreProperties.class)
public class CryptoAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CryptoService cryptoService() {
        return new CryptoService();
    }

    @Bean
    @ConditionalOnMissingBean
    public HybridCryptoService hybridCryptoService(CryptoService cryptoService) {
        return new HybridCryptoService(cryptoService);
    }

    @Bean
    @ConditionalOnMissingBean
    public JsonMessageSigner jsonMessageSigner(CryptoService cryptoService) {
        return new JsonMessageSigner(cryptoService);
    }

    /**
     * Load keystore JKS từ {@code crypto.keystore.path}. Chỉ tạo bean khi có cấu hình path.
     * Hỗ trợ classpath: / file: / đường dẫn tuyệt đối qua {@link ResourceLoader}.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "crypto.keystore", name = "path")
    public KeyStoreService keyStoreService(CryptoKeyStoreProperties props, ResourceLoader resourceLoader) {
        Resource resource = resourceLoader.getResource(props.getPath());
        if (!resource.exists()) {
            throw new CryptoException("Không tìm thấy keystore tại: " + props.getPath());
        }
        try (InputStream in = resource.getInputStream()) {
            return KeyStoreService.load(in, props.storePasswordChars(), props.getType());
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("Lỗi mở keystore '" + props.getPath() + "': " + e.getMessage(), e);
        }
    }
}
