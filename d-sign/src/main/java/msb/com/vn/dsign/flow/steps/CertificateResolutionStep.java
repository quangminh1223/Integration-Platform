package msb.com.vn.dsign.flow.steps;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.dsign.domain.entity.Certificate;
import msb.com.vn.dsign.domain.entity.CertificateStatus;
import msb.com.vn.dsign.domain.repository.CertificateRepository;
import msb.com.vn.dsign.exception.CertificateInvalidException;
import msb.com.vn.dsign.exception.CertificateNotFoundException;
import msb.com.vn.integration.common.model.IntegrationMessage;
import msb.com.vn.integration.core.flow.FlowContext;
import msb.com.vn.integration.core.flow.FlowStep;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Flow step that resolves and validates a certificate by reference.
 * <p>
 * Looks up the certificate in the repository, validates it is neither expired nor revoked,
 * and places the certificate details and key reference into the FlowContext for downstream steps.
 * </p>
 * <p>Order: 200</p>
 *
 * @see FlowStep
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CertificateResolutionStep implements FlowStep {

    private static final String STEP_NAME = "certificate-resolution";
    private static final int STEP_ORDER = 200;

    private final CertificateRepository certificateRepository;

    @Override
    public String getStepName() {
        return STEP_NAME;
    }

    @Override
    public int getOrder() {
        return STEP_ORDER;
    }

    @Override
    public IntegrationMessage execute(IntegrationMessage message, FlowContext context) {
        String certificateRef = context.getVariable("certificateRef");
        log.debug("Resolving certificate reference: {}", certificateRef);

        Certificate certificate = certificateRepository.findByCertRefId(certificateRef)
                .orElseThrow(() -> {
                    log.warn("Certificate not found: {}", certificateRef);
                    return new CertificateNotFoundException(certificateRef);
                });

        validateCertificate(certificate);

        // Place certificate details in context for downstream steps
        context.addVariable("keyReference", certificate.getKeyReference());
        context.addVariable("certificateSubject", certificate.getSubject());
        context.addVariable("certificateIssuer", certificate.getIssuer());
        context.addVariable("certificateSerialNumber", certificate.getSerialNumber());
        context.addVariable("certificatePublicKey", certificate.getPublicKeyBase64());
        context.addVariable("certificateStatus", certificate.getStatus());

        log.debug("Certificate resolved successfully: ref={}, keyRef={}", certificateRef, certificate.getKeyReference());
        return message;
    }

    /**
     * Validates that the certificate is in a usable state — not expired and not revoked.
     *
     * @param certificate the certificate entity to validate
     * @throws CertificateInvalidException if certificate is expired, revoked, or suspended
     */
    private void validateCertificate(Certificate certificate) {
        CertificateStatus status = certificate.getStatus();

        if (status == CertificateStatus.REVOKED) {
            log.warn("Certificate is revoked: {}", certificate.getCertRefId());
            throw new CertificateInvalidException(
                    CertificateInvalidException.Reason.REVOKED,
                    "Certificate " + certificate.getCertRefId() + " has been revoked");
        }

        if (status == CertificateStatus.EXPIRED || isExpired(certificate)) {
            log.warn("Certificate is expired: {}", certificate.getCertRefId());
            throw new CertificateInvalidException(
                    CertificateInvalidException.Reason.EXPIRED,
                    "Certificate " + certificate.getCertRefId() + " has expired (notAfter: " + certificate.getNotAfter() + ")");
        }

        if (status == CertificateStatus.SUSPENDED) {
            log.warn("Certificate is suspended: {}", certificate.getCertRefId());
            throw new CertificateInvalidException(
                    CertificateInvalidException.Reason.REVOKED,
                    "Certificate " + certificate.getCertRefId() + " is suspended");
        }
    }

    /**
     * Checks if the certificate's notAfter date has passed.
     */
    private boolean isExpired(Certificate certificate) {
        return certificate.getNotAfter() != null && Instant.now().isAfter(certificate.getNotAfter());
    }
}
