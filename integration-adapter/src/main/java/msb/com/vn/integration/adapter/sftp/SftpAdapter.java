package msb.com.vn.integration.adapter.sftp;

import lombok.extern.slf4j.Slf4j;
import msb.com.vn.integration.common.enums.ProtocolType;
import msb.com.vn.integration.common.exception.AdapterException;
import msb.com.vn.integration.common.model.IntegrationMessage;
import msb.com.vn.integration.common.model.MessageStatus;
import msb.com.vn.integration.core.adapter.IntegrationAdapter;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * SFTP adapter — uploads/downloads files via SFTP protocol.
 * Used for batch file transfers, report uploads, partner integrations.
 */
@Slf4j
@Component
public class SftpAdapter implements IntegrationAdapter {

    private final SftpRemoteFileTemplate sftpTemplate;

    public SftpAdapter(SftpRemoteFileTemplate sftpTemplate) {
        this.sftpTemplate = sftpTemplate;
    }

    @Override
    public String getName() {
        return "sftp-adapter";
    }

    @Override
    public ProtocolType getProtocol() {
        return ProtocolType.SFTP;
    }

    @Override
    public IntegrationMessage send(IntegrationMessage message) {
        String remoteDirectory = message.getHeader("X-SFTP-Directory");
        String fileName = message.getHeader("X-SFTP-FileName");

        if (remoteDirectory == null) {
            throw new AdapterException(getName(), "Missing X-SFTP-Directory header",
                    message.getCorrelationId(), false);
        }

        if (fileName == null) {
            fileName = message.getCorrelationId() + "_"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                    + ".dat";
        }

        String remotePath = remoteDirectory.endsWith("/")
                ? remoteDirectory + fileName
                : remoteDirectory + "/" + fileName;

        log.info("SFTP upload: remotePath={}, correlationId={}", remotePath, message.getCorrelationId());

        try {
            String content = message.getPayload() != null ? message.getPayload().toString() : "";
            InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

            String finalFileName = fileName;
            sftpTemplate.execute(session -> {
                if (!session.exists(remoteDirectory)) {
                    session.mkdir(remoteDirectory);
                }
                session.write(inputStream, remotePath);
                return null;
            });

            return IntegrationMessage.builder()
                    .messageId(message.getMessageId() + "-uploaded")
                    .correlationId(message.getCorrelationId())
                    .source("sftp:" + remotePath)
                    .payload(Map.of(
                            "remotePath", remotePath,
                            "fileName", finalFileName,
                            "size", content.length(),
                            "status", "UPLOADED"
                    ))
                    .status(MessageStatus.DELIVERED)
                    .build();

        } catch (Exception e) {
            throw new AdapterException(getName(),
                    "SFTP upload failed: " + e.getMessage(),
                    message.getCorrelationId(), true, e);
        }
    }

    @Override
    public boolean supports(IntegrationMessage message) {
        return "SFTP".equalsIgnoreCase(message.getHeader("X-Protocol"));
    }

    @Override
    public boolean isHealthy() {
        try {
            return sftpTemplate.execute(session -> session.isOpen());
        } catch (Exception e) {
            log.warn("SFTP health check failed: {}", e.getMessage());
            return false;
        }
    }
}
