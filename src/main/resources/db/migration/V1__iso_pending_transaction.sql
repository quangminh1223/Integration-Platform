-- ═══════════════════════════════════════════════════════════════════════════════
-- ISO_PENDING_TRANSACTION
--
-- Giao dịch ISO8583 đã trả RC 68 (timeout) cho bên gọi nhưng CHƯA BIẾT CORE có
-- hạch toán hay không.
--
-- Vì sao cần bảng này: CORE không hỗ trợ bản tin đảo 0400 Reversal, nên khi timeout
-- ta không thể tự động hoàn tác. Mỗi bản ghi ở đây là một khoản có nguy cơ lệch quỹ,
-- phải được theo dõi đến khi xác định được kết quả thật.
--
-- Lưu ý: PAN được lưu dạng đã che (6 số đầu + 4 số cuối) theo PCI DSS.
-- ═══════════════════════════════════════════════════════════════════════════════

CREATE TABLE ISO_PENDING_TRANSACTION (
    ID                  VARCHAR2(36)   NOT NULL,
    CORRELATION_ID      VARCHAR2(64)   NOT NULL,

    -- Định danh giao dịch theo chuẩn ISO8583
    MTI                 VARCHAR2(4)    NOT NULL,
    STAN                VARCHAR2(12),
    TERMINAL_ID         VARCHAR2(16),
    RRN                 VARCHAR2(24),
    LOCAL_TXN_DATE      VARCHAR2(8),
    PAN_MASKED          VARCHAR2(24),
    AMOUNT              VARCHAR2(20),
    CURRENCY_CODE       VARCHAR2(3),
    PROCESSING_CODE     VARCHAR2(6),
    REMOTE_ADDRESS      VARCHAR2(64),

    -- Trạng thái đối soát
    -- TIMEOUT_UNKNOWN | CORE_CONFIRMED | CORE_DECLINED | RECONCILED | NEEDS_MANUAL
    STATUS              VARCHAR2(24)   NOT NULL,
    RESPONSE_SENT       VARCHAR2(4),
    CORE_RESPONSE_CODE  VARCHAR2(4),
    CORE_RESPONDED_AT   TIMESTAMP,
    DEADLINE_MS         NUMBER(19),
    CORE_ELAPSED_MS     NUMBER(19),
    RECONCILE_ATTEMPTS  NUMBER(10)     DEFAULT 0,
    RECONCILE_NOTE      CLOB,

    CREATED_AT          TIMESTAMP      NOT NULL,
    UPDATED_AT          TIMESTAMP      NOT NULL,

    CONSTRAINT PK_ISO_PENDING_TRANSACTION PRIMARY KEY (ID),
    CONSTRAINT CK_ISO_PENDING_STATUS CHECK (
        STATUS IN ('TIMEOUT_UNKNOWN', 'CORE_CONFIRMED', 'CORE_DECLINED',
                   'RECONCILED', 'NEEDS_MANUAL')
    )
);

-- Job đối soát quét theo trạng thái
CREATE INDEX IDX_ISO_PENDING_STATUS
    ON ISO_PENDING_TRANSACTION (STATUS);

-- Tìm bản ghi khi CORE trả về muộn: STAN duy nhất theo terminal trong ngày
CREATE INDEX IDX_ISO_PENDING_STAN
    ON ISO_PENDING_TRANSACTION (STAN, TERMINAL_ID, LOCAL_TXN_DATE);

-- Job leo thang quét theo thời gian tạo
CREATE INDEX IDX_ISO_PENDING_CREATED
    ON ISO_PENDING_TRANSACTION (CREATED_AT);

-- Tra cứu theo correlation ID khi điều tra sự cố
CREATE INDEX IDX_ISO_PENDING_CORRELATION
    ON ISO_PENDING_TRANSACTION (CORRELATION_ID);

COMMENT ON TABLE ISO_PENDING_TRANSACTION IS
    'Giao dịch ISO8583 timeout chờ đối soát với CORE. CORE không hỗ trợ 0400 Reversal nên phải xử lý thủ công.';
COMMENT ON COLUMN ISO_PENDING_TRANSACTION.STATUS IS
    'CORE_CONFIRMED = lệch quỹ thật: CORE đã hạch toán nhưng bên gọi nhận RC 68';
COMMENT ON COLUMN ISO_PENDING_TRANSACTION.PAN_MASKED IS
    'PAN đã che theo PCI DSS: giữ 6 số đầu và 4 số cuối';
