package msb.com.vn.integration.common.enums;

/**
 * Supported communication protocols.
 * Each adapter implementation handles one protocol.
 */
public enum ProtocolType {
    REST,
    SOAP,
    KAFKA,
    RABBITMQ,
    IBM_MQ,
    FILE,
    SFTP
}
