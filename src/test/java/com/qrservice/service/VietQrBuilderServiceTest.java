package com.qrservice.service;

import com.qrservice.dto.request.GenerateVietQrRequest;
import com.qrservice.dto.response.VietQrData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class VietQrBuilderServiceTest {

    private VietQrBuilderService service;

    @BeforeEach
    void setUp() {
        service = new VietQrBuilderService();
    }

    @Test
    @DisplayName("Tạo VietQR content không có số tiền")
    void buildVietQrContent_withoutAmount() {
        GenerateVietQrRequest request = new GenerateVietQrRequest();
        request.setBankBin("970436");
        request.setBankAccount("1234567890");
        request.setAccountName("NGUYEN VAN A");

        String content = service.buildVietQrContent(request);

        assertThat(content).isNotBlank();
        assertThat(content).startsWith("000201");
        assertThat(content).contains("A000000727");
        assertThat(content).contains("970436");
        assertThat(content).contains("1234567890");
    }

    @Test
    @DisplayName("Tạo VietQR content có số tiền")
    void buildVietQrContent_withAmount() {
        GenerateVietQrRequest request = new GenerateVietQrRequest();
        request.setBankBin("970436");
        request.setBankAccount("1234567890");
        request.setAccountName("NGUYEN VAN A");
        request.setAmount(new BigDecimal("100000"));
        request.setDescription("Thanh toan hoa don");

        String content = service.buildVietQrContent(request);

        assertThat(content).contains("100000");
        assertThat(content).contains("Thanh toan");
    }

    @Test
    @DisplayName("Phân tách VietQR content")
    void parseVietQrContent() {
        GenerateVietQrRequest request = new GenerateVietQrRequest();
        request.setBankBin("970436");
        request.setBankAccount("1234567890");
        request.setAccountName("NGUYEN VAN A");
        request.setAmount(new BigDecimal("50000"));
        request.setDescription("Test payment");

        String content = service.buildVietQrContent(request);
        VietQrData parsed = service.parseVietQrContent(content);

        assertThat(parsed.getBankBin()).isEqualTo("970436");
        assertThat(parsed.getBankAccount()).isEqualTo("1234567890");
        assertThat(parsed.getAmount()).isEqualByComparingTo(new BigDecimal("50000"));
    }

    @Test
    @DisplayName("Kiểm tra CRC hợp lệ")
    void verifyCrc_valid() {
        GenerateVietQrRequest request = new GenerateVietQrRequest();
        request.setBankBin("970436");
        request.setBankAccount("1234567890");

        String content = service.buildVietQrContent(request);
        boolean valid = service.verifyCrc(content);

        assertThat(valid).isTrue();
    }
}
