package msb.com.vn.qrservice.kafka.consumer;

import msb.com.vn.qrservice.kafka.event.QrGeneratedEvent;
import msb.com.vn.qrservice.kafka.event.QrParsedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class QrEventConsumer {

    @KafkaListener(
            topics = "${kafka.topics.qr-generated}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeQrGenerated(QrGeneratedEvent event, Acknowledgment ack) {
        try {
            log.info("[KAFKA] QR_GENERATED: qrCodeId={}, type={}, createdBy={}, thread={}",
                    event.getQrCodeId(), event.getQrType(),
                    event.getCreatedBy(), Thread.currentThread().getName());
            processQrGeneratedEvent(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[KAFKA] Lỗi xử lý QR_GENERATED: qrCodeId={}, error={}",
                    event.getQrCodeId(), e.getMessage(), e);
        }
    }

    @KafkaListener(
            topics = "${kafka.topics.qr-parsed}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeQrParsed(QrParsedEvent event, Acknowledgment ack) {
        try {
            log.info("[KAFKA] QR_PARSED: parseId={}, type={}, status={}, thread={}",
                    event.getParseId(), event.getQrType(),
                    event.getStatus(), Thread.currentThread().getName());
            processQrParsedEvent(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[KAFKA] Lỗi xử lý QR_PARSED: parseId={}, error={}",
                    event.getParseId(), e.getMessage(), e);
        }
    }

    private void processQrGeneratedEvent(QrGeneratedEvent event) {
        log.debug("Xử lý QR_GENERATED event: {}", event.getQrCodeId());
    }

    private void processQrParsedEvent(QrParsedEvent event) {
        log.debug("Xử lý QR_PARSED event: {}", event.getParseId());
    }
}
