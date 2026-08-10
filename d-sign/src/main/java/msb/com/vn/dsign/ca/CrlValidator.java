package msb.com.vn.dsign.ca;

import lombok.extern.slf4j.Slf4j;
import msb.com.vn.dsign.ca.model.CertificateValidationResult;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates certificate revocation status via CRL (Certificate Revocation List).
 *
 * <p>Extracts CRL Distribution Point URLs from the certificate's CRL Distribution Points
 * extension, downloads the CRL, and checks if the certificate's serial number appears
 * in the revocation list.</p>
 *
 * <p>Status mapping:
 * <ul>
 *     <li>Serial not in CRL → VALID</li>
 *     <li>Serial in CRL → REVOKED</li>
 *     <li>CRL unavailable → UNKNOWN</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class CrlValidator {

    private static final String VALIDATION_METHOD = "CRL";
    private static final String CRL_DISTRIBUTION_POINTS_OID = "2.5.29.31";

    /**
     * Validate certificate revocation status via CRL.
     *
     * @param cert the certificate to validate
     * @return validation result with status VALID, REVOKED, or UNKNOWN
     */
    public CertificateValidationResult validate(X509Certificate cert) {
        try {
            List<String> crlUrls = extractCrlDistributionPoints(cert);
            if (crlUrls.isEmpty()) {
                log.warn("No CRL Distribution Point URLs found in certificate: {}",
                        cert.getSubjectX500Principal());
                return buildResult(CertificateValidationResult.Status.UNKNOWN,
                        "No CRL Distribution Point URLs found in certificate");
            }

            // Try each CRL URL until one succeeds
            for (String crlUrl : crlUrls) {
                try {
                    X509CRL crl = fetchCrl(new URL(crlUrl));
                    if (crl == null) {
                        continue;
                    }

                    X509CRLEntry revokedEntry = crl.getRevokedCertificate(cert.getSerialNumber());
                    if (revokedEntry != null) {
                        log.info("Certificate serial {} found in CRL (revoked on {})",
                                cert.getSerialNumber(), revokedEntry.getRevocationDate());
                        return buildResult(CertificateValidationResult.Status.REVOKED, null);
                    }

                    log.info("Certificate serial {} not found in CRL - certificate is valid",
                            cert.getSerialNumber());
                    return buildResult(CertificateValidationResult.Status.VALID, null);

                } catch (Exception e) {
                    log.warn("Failed to fetch/parse CRL from {}: {}", crlUrl, e.getMessage());
                    // Try next URL
                }
            }

            // All CRL URLs failed
            log.error("All CRL Distribution Point URLs failed for certificate: {}",
                    cert.getSubjectX500Principal());
            return buildResult(CertificateValidationResult.Status.UNKNOWN,
                    "Unable to download CRL from any distribution point");

        } catch (Exception e) {
            log.error("CRL validation failed for certificate: {}", cert.getSubjectX500Principal(), e);
            return buildResult(CertificateValidationResult.Status.UNKNOWN,
                    "CRL validation error: " + e.getMessage());
        }
    }

    /**
     * Extract CRL Distribution Point URLs from the certificate.
     *
     * @param cert the certificate
     * @return list of CRL distribution point URLs
     */
    List<String> extractCrlDistributionPoints(X509Certificate cert) {
        List<String> urls = new ArrayList<>();
        byte[] crlDpExtension = cert.getExtensionValue(CRL_DISTRIBUTION_POINTS_OID);
        if (crlDpExtension == null) {
            return urls;
        }

        try {
            // Unwrap the outer OCTET STRING
            byte[] dpValue = unwrapOctetString(crlDpExtension);
            if (dpValue == null) {
                return urls;
            }

            // Parse the CRLDistributionPoints SEQUENCE
            parseCrlDistributionPoints(dpValue, urls);
        } catch (Exception e) {
            log.debug("Failed to parse CRL Distribution Points extension", e);
        }

        return urls;
    }

    /**
     * Fetch and parse a CRL from the given URL.
     * This method is protected to allow overriding in tests.
     *
     * @param url the CRL distribution point URL
     * @return the parsed X509CRL, or null if download/parsing fails
     * @throws IOException if the HTTP call fails
     */
    protected X509CRL fetchCrl(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setRequestProperty("Accept", "application/pkix-crl, application/x-pkcs7-crl");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("CRL server returned HTTP " + responseCode);
            }

            try (InputStream in = connection.getInputStream()) {
                byte[] crlBytes = in.readAllBytes();
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                return (X509CRL) cf.generateCRL(new ByteArrayInputStream(crlBytes));
            } catch (Exception e) {
                throw new IOException("Failed to parse CRL from " + url, e);
            }
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Parse CRLDistributionPoints ASN.1 structure to extract URLs.
     *
     * <pre>
     * CRLDistributionPoints ::= SEQUENCE SIZE (1..MAX) OF DistributionPoint
     * DistributionPoint ::= SEQUENCE {
     *     distributionPoint [0] DistributionPointName OPTIONAL,
     *     ...
     * }
     * DistributionPointName ::= CHOICE {
     *     fullName [0] GeneralNames,
     *     ...
     * }
     * GeneralNames ::= SEQUENCE SIZE (1..MAX) OF GeneralName
     * GeneralName ::= CHOICE {
     *     uniformResourceIdentifier [6] IA5String,
     *     ...
     * }
     * </pre>
     */
    private void parseCrlDistributionPoints(byte[] dpValue, List<String> urls) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(dpValue)) {
            int tag = bais.read();
            if (tag != 0x30) { // SEQUENCE
                return;
            }
            int seqLength = readLength(bais);

            // Iterate through DistributionPoint entries
            int bytesRead = 0;
            while (bytesRead < seqLength && bais.available() > 0) {
                int dpTag = bais.read();
                bytesRead++;
                if (dpTag == -1) break;

                int dpLength = readLength(bais);
                bytesRead += getLengthBytes(dpLength);

                if (dpTag == 0x30) { // DistributionPoint SEQUENCE
                    byte[] dpBytes = new byte[dpLength];
                    int read = bais.read(dpBytes);
                    bytesRead += read;
                    extractUrlFromDistributionPoint(dpBytes, urls);
                } else {
                    // Skip unknown tag
                    bais.skip(dpLength);
                    bytesRead += dpLength;
                }
            }
        } catch (IOException e) {
            log.debug("Error parsing CRL Distribution Points", e);
        }
    }

    /**
     * Extract URL from a single DistributionPoint ASN.1 structure.
     */
    private void extractUrlFromDistributionPoint(byte[] dpBytes, List<String> urls) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(dpBytes)) {
            int tag = bais.read() & 0xFF;

            // distributionPoint [0] - context-specific constructed
            if (tag == 0xA0) {
                int length = readLength(bais);
                byte[] dpNameBytes = new byte[length];
                bais.read(dpNameBytes);
                extractUrlFromDistributionPointName(dpNameBytes, urls);
            }
        } catch (IOException e) {
            log.debug("Error parsing DistributionPoint", e);
        }
    }

    /**
     * Extract URL from DistributionPointName (fullName [0]).
     */
    private void extractUrlFromDistributionPointName(byte[] dpNameBytes, List<String> urls) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(dpNameBytes)) {
            int tag = bais.read() & 0xFF;

            // fullName [0] - context-specific constructed containing GeneralNames
            if (tag == 0xA0) {
                int length = readLength(bais);

                // Parse GeneralNames - looking for uniformResourceIdentifier [6]
                int bytesRead = 0;
                while (bytesRead < length && bais.available() > 0) {
                    int gnTag = bais.read() & 0xFF;
                    bytesRead++;
                    if (gnTag == 0xFF) break;

                    int gnLength = readLength(bais);
                    bytesRead += getLengthBytes(gnLength);

                    if (gnTag == 0x86) { // uniformResourceIdentifier [6] implicit IA5String
                        byte[] uriBytes = new byte[gnLength];
                        bais.read(uriBytes);
                        bytesRead += gnLength;
                        String url = new String(uriBytes, java.nio.charset.StandardCharsets.US_ASCII);
                        urls.add(url);
                    } else {
                        bais.skip(gnLength);
                        bytesRead += gnLength;
                    }
                }
            }
        } catch (IOException e) {
            log.debug("Error parsing DistributionPointName", e);
        }
    }

    /**
     * Unwrap the outer OCTET STRING wrapping an extension value.
     */
    private byte[] unwrapOctetString(byte[] extensionValue) {
        if (extensionValue == null || extensionValue.length < 2) {
            return null;
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(extensionValue)) {
            int tag = bais.read();
            if (tag != 0x04) { // OCTET STRING
                return null;
            }
            int length = readLength(bais);
            byte[] value = new byte[length];
            int read = bais.read(value);
            return read == length ? value : null;
        } catch (IOException e) {
            return null;
        }
    }

    private int readLength(InputStream in) throws IOException {
        int firstByte = in.read();
        if (firstByte < 128) {
            return firstByte;
        }
        int numBytes = firstByte & 0x7F;
        int length = 0;
        for (int i = 0; i < numBytes; i++) {
            length = (length << 8) | in.read();
        }
        return length;
    }

    private int getLengthBytes(int length) {
        if (length < 128) return 1;
        if (length < 256) return 2;
        return 3;
    }

    private CertificateValidationResult buildResult(CertificateValidationResult.Status status, String errorMessage) {
        return CertificateValidationResult.builder()
                .status(status)
                .validationMethod(VALIDATION_METHOD)
                .validatedAt(Instant.now())
                .errorMessage(errorMessage)
                .build();
    }
}
