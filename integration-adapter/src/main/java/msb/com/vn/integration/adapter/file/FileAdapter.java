package msb.com.vn.integration.adapter.file;

import lombok.extern.slf4j.Slf4j;
import msb.com.vn.integration.common.enums.ProtocolType;
import msb.com.vn.integration.common.exception.AdapterException;
import msb.com.vn.integration.common.model.IntegrationMessage;
import msb.com.vn.integration.common.model.MessageStatus;
import msb.com.vn.integration.core.adapter.IntegrationAdapter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * File adapter — writes messages to local file system.
 * Useful for batch processing, report generation, and audit trails.
 */
@Slf4j
@Component
public class FileAdapter implements IntegrationAdapter {

    @Override
    public String getName() {
        return "file-adapter";
    }

    @Override
    public ProtocolType getProtocol() {
        return ProtocolType.FILE;
    }

    @Override
    public IntegrationMessage send(IntegrationMessage message) {
        String directory = message.getHeader("X-File-Directory");
        String filename = message.getHeader("X-File-Name");

        if (directory == null) {
            throw new AdapterException(getName(), "Missing X-File-Directory header",
                    message.getCorrelationId(), false);
        }

        if (filename == null) {
            filename = message.getCorrelationId() + "_"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                    + ".dat";
        }

        Path filePath = Path.of(directory, filename);
        log.info("File write: path={}, correlationId={}", filePath, message.getCorrelationId());

        try {
            Files.createDirectories(filePath.getParent());
            String content = message.getPayload() != null ? message.getPayload().toString() : "";
            Files.writeString(filePath, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            return IntegrationMessage.builder()
                    .messageId(message.getMessageId() + "-written")
                    .correlationId(message.getCorrelationId())
                    .source("file:" + filePath)
                    .payload(java.util.Map.of("path", filePath.toString(), "size", content.length()))
                    .status(MessageStatus.DELIVERED)
                    .build();

        } catch (IOException e) {
            throw new AdapterException(getName(),
                    "File write failed: " + e.getMessage(),
                    message.getCorrelationId(), true, e);
        }
    }

    @Override
    public boolean supports(IntegrationMessage message) {
        return "FILE".equalsIgnoreCase(message.getHeader("X-Protocol"));
    }

    @Override
    public boolean isHealthy() {
        return true;
    }
}
