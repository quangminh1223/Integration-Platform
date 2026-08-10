package msb.com.vn.qrservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("QR Service API")
                        .description("API tạo mã QR và phân tách mã QR theo chuẩn VietQR (NAPAS)")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("MSB QR Service Team")
                                .email("support@msb.com.vn"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
