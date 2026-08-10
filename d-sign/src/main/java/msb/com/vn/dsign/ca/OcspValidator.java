package msb.com.vn.dsign.ca;

import lombok.extern.slf4j.Slf4j;
import msb.com.vn.dsign.ca.model.CertificateValidationResult;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.time.Instant;

/**
 * Validates certificate revocation status via OCSP (Online Certificate Status Protocol).
 *
 * <p>Extracts the OCSP responder URL from the certificate's Authority Information Access (AIA)
 * extension, builds an OCSP request, and maps the response status to
 * {@link CertificateValidationResult}.</p>
 *
 * <p>Status mapping:
 * <ul>
 *     <li>GOOD → VALID</li>
 *     <li>REVOKED → REVOKED</li>
 *     <li>UNKNOWN → UNKNOWN</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class OcspValidator {

    private static final String VALIDATION_METHOD = "OCSP";
    private static final String AIA_OID = "1.3.6.1.5.5.7.1.1";
    private static final String OCSP_ACCESS_METHOD_OID = "1.3.6.1.5.5.7.48.1";

    /**
     * Validate certificate revocation status via OCSP.
     *
     * @param cert       the certificate to validate
     * @param issuerCert the issuer certificate (needed to build the OCSP request)
     * @return validation result with status VALID, REVOKED, or UNKNOWN
     */
    public CertificateValidationResult validate(X509Certificate cert, X509Certificate issuerCert) {
        try {
            String ocspUrl = extractOcspUrl(cert);
            if (ocspUrl == null) {
                log.warn("No OCSP responder URL found in certificate: {}", cert.getSubjectX500Principal());
                return buildResult(CertificateValidationResult.Status.UNKNOWN,
                        "No OCSP responder URL found in certificate AIA extension");
            }

            byte[] ocspRequest = buildOcspRequest(cert, issuerCert);
            byte[] ocspResponseBytes = fetchOcspResponse(new URL(ocspUrl), ocspRequest);

            CertificateValidationResult.Status status = parseOcspResponse(ocspResponseBytes);
            log.info("OCSP validation result for cert serial {}: {}", cert.getSerialNumber(), status);
            return buildResult(status, null);

        } catch (Exception e) {
            log.error("OCSP validation failed for certificate: {}", cert.getSubjectX500Principal(), e);
            return buildResult(CertificateValidationResult.Status.UNKNOWN,
                    "OCSP validation error: " + e.getMessage());
        }
    }

    /**
     * Extract the OCSP responder URL from the certificate's Authority Information Access extension.
     *
     * @param cert the certificate
     * @return the OCSP responder URL, or null if not found
     */
    String extractOcspUrl(X509Certificate cert) {
        byte[] aiaExtension = cert.getExtensionValue(AIA_OID);
        if (aiaExtension == null) {
            return null;
        }

        try {
            // Parse the ASN.1 structure of the AIA extension
            // AIA extension value is wrapped in an OCTET STRING
            byte[] aiaValue = unwrapOctetString(aiaExtension);
            if (aiaValue == null) {
                return null;
            }

            // Parse the SEQUENCE of AccessDescription entries
            return parseAiaForOcsp(aiaValue);
        } catch (Exception e) {
            log.debug("Failed to parse AIA extension for OCSP URL", e);
            return null;
        }
    }

    /**
     * Build an OCSP request for the given certificate and issuer.
     * Uses Java security APIs to construct the request bytes.
     *
     * @param cert       the certificate to check
     * @param issuerCert the issuer certificate
     * @return the DER-encoded OCSP request
     */
    byte[] buildOcspRequest(X509Certificate cert, X509Certificate issuerCert) throws Exception {
        // Build a minimal OCSP request using the certificate's serial number
        // and the issuer's name/key hash
        java.security.MessageDigest sha1 = java.security.MessageDigest.getInstance("SHA-1");

        // Hash the issuer's distinguished name
        byte[] issuerNameHash = sha1.digest(
                issuerCert.getSubjectX500Principal().getEncoded());

        // Hash the issuer's public key
        sha1.reset();
        byte[] issuerKeyHash = sha1.digest(
                issuerCert.getPublicKey().getEncoded());

        byte[] serialNumber = cert.getSerialNumber().toByteArray();

        // Construct DER-encoded OCSPRequest manually
        return buildDerOcspRequest(issuerNameHash, issuerKeyHash, serialNumber);
    }

    /**
     * Fetch the OCSP response from the responder URL.
     * This method is protected to allow overriding in tests.
     *
     * @param url     the OCSP responder URL
     * @param request the DER-encoded OCSP request
     * @return the raw OCSP response bytes
     * @throws IOException if the HTTP call fails
     */
    protected byte[] fetchOcspResponse(URL url, byte[] request) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/ocsp-request");
            connection.setRequestProperty("Accept", "application/ocsp-response");
            connection.setDoOutput(true);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);

            try (OutputStream out = connection.getOutputStream()) {
                out.write(request);
                out.flush();
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("OCSP responder returned HTTP " + responseCode);
            }

            try (InputStream in = connection.getInputStream()) {
                return in.readAllBytes();
            }
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Parse the OCSP response bytes and determine the certificate status.
     *
     * @param responseBytes the raw OCSP response
     * @return the mapped status (VALID, REVOKED, or UNKNOWN)
     */
    CertificateValidationResult.Status parseOcspResponse(byte[] responseBytes) throws Exception {
        if (responseBytes == null || responseBytes.length == 0) {
            return CertificateValidationResult.Status.UNKNOWN;
        }

        // Parse the OCSPResponse ASN.1 structure
        // OCSPResponse ::= SEQUENCE { responseStatus ENUMERATED, responseBytes [0] EXPLICIT ... }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(responseBytes)) {
            // Check the outer SEQUENCE tag
            int tag = bais.read();
            if (tag != 0x30) { // Not a SEQUENCE
                return CertificateValidationResult.Status.UNKNOWN;
            }
            readLength(bais);

            // Read responseStatus (ENUMERATED)
            int statusTag = bais.read();
            if (statusTag != 0x0A) { // Not ENUMERATED
                return CertificateValidationResult.Status.UNKNOWN;
            }
            int statusLength = readLength(bais);
            int responseStatus = 0;
            for (int i = 0; i < statusLength; i++) {
                responseStatus = (responseStatus << 8) | bais.read();
            }

            // OCSPResponseStatus: 0 = successful
            if (responseStatus != 0) {
                log.warn("OCSP response status is not successful: {}", responseStatus);
                return CertificateValidationResult.Status.UNKNOWN;
            }

            // Navigate to the SingleResponse certStatus within the response
            return extractCertStatusFromResponse(responseBytes);
        }
    }

    /**
     * Extract the certStatus from the OCSP response body.
     * certStatus can be:
     *   [0] IMPLICIT NULL  -- good
     *   [1] IMPLICIT RevokedInfo  -- revoked
     *   [2] IMPLICIT NULL  -- unknown
     */
    private CertificateValidationResult.Status extractCertStatusFromResponse(byte[] responseBytes) {
        // Search for the certStatus tag within the response
        // In a real SingleResponse, certStatus follows the CertID
        // We scan for context-specific tags [0], [1], or [2]
        for (int i = 0; i < responseBytes.length - 1; i++) {
            int b = responseBytes[i] & 0xFF;

            // Look for context-specific class (0x80 = context [0], 0xA1 = constructed context [1], 0x82 = context [2])
            // In OCSP SingleResponse, certStatus is tagged:
            //   good:    [0] IMPLICIT NULL -> tag 0x80, length 0
            //   revoked: [1] IMPLICIT ... -> tag 0xA1
            //   unknown: [2] IMPLICIT NULL -> tag 0x82, length 0

            if (b == 0x80 && i > 20) {
                // Possible good status - check if length is 0
                int nextByte = responseBytes[i + 1] & 0xFF;
                if (nextByte == 0x00) {
                    return CertificateValidationResult.Status.VALID;
                }
            } else if (b == 0xA1 && i > 20) {
                // Possible revoked status
                return CertificateValidationResult.Status.REVOKED;
            } else if (b == 0x82 && i > 20) {
                int nextByte = responseBytes[i + 1] & 0xFF;
                if (nextByte == 0x00) {
                    return CertificateValidationResult.Status.UNKNOWN;
                }
            }
        }

        // If we couldn't determine the status, return UNKNOWN
        return CertificateValidationResult.Status.UNKNOWN;
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

    /**
     * Parse the AIA extension looking for OCSP access method URL.
     */
    private String parseAiaForOcsp(byte[] aiaValue) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(aiaValue)) {
            int tag = bais.read();
            if (tag != 0x30) { // SEQUENCE
                return null;
            }
            int seqLength = readLength(bais);

            // Iterate through AccessDescription entries
            int bytesRead = 0;
            while (bytesRead < seqLength) {
                int descTag = bais.read();
                bytesRead++;
                if (descTag != 0x30) { // Each AccessDescription is a SEQUENCE
                    break;
                }
                int descLength = readLength(bais);
                bytesRead += getLengthBytes(descLength);

                byte[] descBytes = new byte[descLength];
                int read = bais.read(descBytes);
                bytesRead += read;

                String url = parseAccessDescription(descBytes);
                if (url != null) {
                    return url;
                }
            }
        } catch (IOException e) {
            log.debug("Failed to parse AIA value", e);
        }
        return null;
    }

    /**
     * Parse a single AccessDescription and return the URL if it's an OCSP access method.
     */
    private String parseAccessDescription(byte[] descBytes) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(descBytes)) {
            // accessMethod OID
            int oidTag = bais.read();
            if (oidTag != 0x06) { // OID
                return null;
            }
            int oidLength = readLength(bais);
            byte[] oidBytes = new byte[oidLength];
            bais.read(oidBytes);

            // Check if this is the OCSP access method OID (1.3.6.1.5.5.7.48.1)
            if (!isOcspOid(oidBytes)) {
                return null;
            }

            // accessLocation - context [6] for uniformResourceIdentifier
            int locTag = bais.read() & 0xFF;
            if (locTag == 0x86) { // context [6] implicit IA5String (URI)
                int uriLength = readLength(bais);
                byte[] uriBytes = new byte[uriLength];
                bais.read(uriBytes);
                return new String(uriBytes, java.nio.charset.StandardCharsets.US_ASCII);
            }
        } catch (IOException e) {
            log.debug("Failed to parse AccessDescription", e);
        }
        return null;
    }

    /**
     * Check if the OID bytes represent the OCSP access method (1.3.6.1.5.5.7.48.1).
     */
    private boolean isOcspOid(byte[] oidBytes) {
        // DER encoding of 1.3.6.1.5.5.7.48.1
        byte[] expectedOid = {0x2B, 0x06, 0x01, 0x05, 0x05, 0x07, 0x30, 0x01};
        if (oidBytes.length != expectedOid.length) {
            return false;
        }
        for (int i = 0; i < oidBytes.length; i++) {
            if (oidBytes[i] != expectedOid[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Build a minimal DER-encoded OCSP request.
     */
    private byte[] buildDerOcspRequest(byte[] issuerNameHash, byte[] issuerKeyHash, byte[] serialNumber) {
        // CertID uses SHA-1 algorithm OID: 1.3.14.3.2.26
        byte[] sha1Oid = {0x06, 0x05, 0x2B, 0x0E, 0x03, 0x02, 0x1A};
        byte[] nullParam = {0x05, 0x00};

        // AlgorithmIdentifier SEQUENCE
        byte[] algorithmId = wrapSequence(concat(sha1Oid, nullParam));

        // CertID components
        byte[] issuerNameHashWrapped = wrapOctetString(issuerNameHash);
        byte[] issuerKeyHashWrapped = wrapOctetString(issuerKeyHash);
        byte[] serialNumberWrapped = wrapInteger(serialNumber);

        // CertID SEQUENCE
        byte[] certId = wrapSequence(concat(algorithmId, issuerNameHashWrapped,
                issuerKeyHashWrapped, serialNumberWrapped));

        // Request SEQUENCE (just contains CertID)
        byte[] request = wrapSequence(certId);

        // RequestList SEQUENCE OF
        byte[] requestList = wrapSequence(request);

        // TBSRequest SEQUENCE
        byte[] tbsRequest = wrapSequence(requestList);

        // OCSPRequest SEQUENCE
        return wrapSequence(tbsRequest);
    }

    private byte[] wrapSequence(byte[] content) {
        return wrapTag((byte) 0x30, content);
    }

    private byte[] wrapOctetString(byte[] content) {
        return wrapTag((byte) 0x04, content);
    }

    private byte[] wrapInteger(byte[] content) {
        // Ensure positive integer (add leading zero if high bit set)
        if (content.length > 0 && (content[0] & 0x80) != 0) {
            byte[] padded = new byte[content.length + 1];
            padded[0] = 0x00;
            System.arraycopy(content, 0, padded, 1, content.length);
            content = padded;
        }
        return wrapTag((byte) 0x02, content);
    }

    private byte[] wrapTag(byte tag, byte[] content) {
        byte[] lengthBytes = encodeDerLength(content.length);
        byte[] result = new byte[1 + lengthBytes.length + content.length];
        result[0] = tag;
        System.arraycopy(lengthBytes, 0, result, 1, lengthBytes.length);
        System.arraycopy(content, 0, result, 1 + lengthBytes.length, content.length);
        return result;
    }

    private byte[] encodeDerLength(int length) {
        if (length < 128) {
            return new byte[]{(byte) length};
        } else if (length < 256) {
            return new byte[]{(byte) 0x81, (byte) length};
        } else {
            return new byte[]{(byte) 0x82, (byte) (length >> 8), (byte) length};
        }
    }

    private byte[] concat(byte[]... arrays) {
        int totalLength = 0;
        for (byte[] array : arrays) {
            totalLength += array.length;
        }
        byte[] result = new byte[totalLength];
        int offset = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
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
