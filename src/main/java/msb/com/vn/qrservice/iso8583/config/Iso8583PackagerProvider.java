package msb.com.vn.qrservice.iso8583.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * Cung cấp {@link GenericPackager} dùng chung cho luồng socket inbound/outbound.
 *
 * <p>GenericPackager của jPOS là thread-safe khi chỉ dùng để pack/unpack,
 * nên một instance dùng chung cho toàn bộ connection.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Iso8583PackagerProvider {

    private static final String FALLBACK_PACKAGER = "/org/jpos/iso/packager/iso87ascii.xml";

    private final Iso8583SocketProperties properties;

    private GenericPackager packager;

    @PostConstruct
    void init() {
        String configPath = properties.getPackagerConfig();
        try {
            ClassPathResource resource = new ClassPathResource(configPath);
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    packager = new GenericPackager(is);
                    log.info("ISO8583 socket packager khởi tạo từ: {}", configPath);
                }
            } else {
                try (InputStream is = getClass().getResourceAsStream(FALLBACK_PACKAGER)) {
                    packager = new GenericPackager(is);
                    log.warn("Không tìm thấy {}, dùng packager mặc định iso87ascii", configPath);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Không thể khởi tạo ISO8583 packager: " + e.getMessage(), e);
        }
    }

    public GenericPackager getPackager() {
        return packager;
    }

    /**
     * Tạo ISOMsg rỗng đã gắn packager.
     */
    public ISOMsg newMessage() {
        ISOMsg msg = new ISOMsg();
        msg.setPackager(packager);
        return msg;
    }

    /**
     * Giải mã byte thành ISOMsg.
     */
    public ISOMsg unpack(byte[] raw) throws ISOException {
        ISOMsg msg = newMessage();
        msg.unpack(raw);
        return msg;
    }

    /**
     * Đóng gói ISOMsg thành byte để đẩy lên socket.
     */
    public byte[] pack(ISOMsg msg) throws ISOException {
        if (msg.getPackager() == null) {
            msg.setPackager(packager);
        }
        return msg.pack();
    }
}
