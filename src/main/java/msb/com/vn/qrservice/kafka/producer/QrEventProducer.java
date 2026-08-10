package msb.com.vn.qrservice.kafka.producer;

import msb.com.vn.qrservice.kafka.event.QrGeneratedEvent;
import msb.com.vn.qrservice.kafka.event.QrParsedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class QrEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.qr-generated}")
    private String qrGeneratedTopic;

    @Value("${kafka.topics.qr-parsed}")
    private String qrParsedTopic;

    public void publishQrGenerated(QrGeneratedEvent event) {
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(qrGeneratedTopic, event.getQrCodeId(), event);
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Không thể gửi sự kiện QR_GENERATED: qrCodeId={}, error={}",
                        event.getQrCodeId(), ex.getMessage());
            } else {
                log.debug("Đã gửi sự kiện QR_GENERATED: qrCodeId={}, partition={}, offset={}",
                        event.getQrCodeId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    public void publishQrParsed(QrParsedEvent event) {
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(qrParsedTopic, event.getParseId(), event);
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Không thể gửi sự kiện QR_PARSED: parseId={}, error={}",
                        event.getParseId(), ex.getMessage());
            } else {
                log.debug("Đã gửi sự kiện QR_PARSED: parseId={}, partition={}, offset={}",
                        event.getParseId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
