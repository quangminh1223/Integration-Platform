package msb.com.vn.qrservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableKafka
@EnableScheduling   // cần cho job đối soát giao dịch ISO8583 bị timeout
public class QrServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(QrServiceApplication.class, args);
    }
}
