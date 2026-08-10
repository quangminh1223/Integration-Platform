package msb.com.vn.dsign.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA entity representing a digital certificate registered in the D-Sign system.
 * Maps to the DSIGN_CERTIFICATE table.
 */
@Entity
@Table(name = "DSIGN_CERTIFICATE")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Certificate {

    @Id
    @Column(name = "CERT_REF_ID")
    private String certRefId;

    @Column(name = "SUBJECT")
    private String subject;

    @Column(name = "ISSUER")
    private String issuer;

    @Column(name = "SERIAL_NUMBER")
    private String serialNumber;

    @Column(name = "NOT_BEFORE")
    private Instant notBefore;

    @Column(name = "NOT_AFTER")
    private Instant notAfter;

    @Column(name = "PUBLIC_KEY", columnDefinition = "CLOB")
    private String publicKeyBase64;

    @Column(name = "KEY_REFERENCE")
    private String keyReference;

    @Column(name = "OWNER_ID")
    private String ownerId;

    @Column(name = "CA_PROVIDER")
    private String caProvider;

    @Column(name = "STATUS")
    @Enumerated(EnumType.STRING)
    private CertificateStatus status;

    @Column(name = "CREATED_AT")
    private Instant createdAt;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;
}
