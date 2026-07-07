package msb.com.vn.integration.adapter.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;

/**
 * SFTP configuration — only loaded when sftp.enabled=true.
 */
@Configuration
@ConditionalOnProperty(prefix = "integration.sftp", name = "enabled", havingValue = "true")
public class SftpConfig {

    @Value("${integration.sftp.host:localhost}")
    private String host;

    @Value("${integration.sftp.port:22}")
    private int port;

    @Value("${integration.sftp.username:}")
    private String username;

    @Value("${integration.sftp.password:}")
    private String password;

    @Value("${integration.sftp.private-key:}")
    private String privateKeyPath;

    @Bean
    public DefaultSftpSessionFactory sftpSessionFactory() {
        DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUser(username);
        factory.setAllowUnknownKeys(true);

        if (privateKeyPath != null && !privateKeyPath.isBlank()) {
            factory.setPrivateKey(new org.springframework.core.io.FileSystemResource(privateKeyPath));
        } else if (password != null && !password.isBlank()) {
            factory.setPassword(password);
        }

        return factory;
    }

    @Bean
    public SftpRemoteFileTemplate sftpRemoteFileTemplate(DefaultSftpSessionFactory factory) {
        return new SftpRemoteFileTemplate(factory);
    }
}
