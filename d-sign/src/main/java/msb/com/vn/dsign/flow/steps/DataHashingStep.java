package msb.com.vn.dsign.flow.steps;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.crypto.CryptoService;
import msb.com.vn.crypto.RsaHashAlgorithm;
import msb.com.vn.dsign.exception.DSignException;
import msb.com.vn.integration.common.model.IntegrationMessage;
import msb.com.vn.integration.core.flow.FlowContext;
import msb.com.vn.integration.core.flow.FlowStep;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;

/**
 * Flow step that decodes the Base64 document data from the signing request
 * and hashes it using the algorithm's hash component (SHA-256 or SHA-512).
 * The resulting hash bytes are placed in the FlowContext for the HSM signing step.
 *
 * <p>Order: 400 (after KeyRetrievalStep, before HsmSigningStep)</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataHashingStep implements FlowStep {

    private static final String STEP_NAME = "data-hashing";
    private static final int ORDER = 400;

    /**
     * Maps signing algorithm names to their hash digest algorithm.
     */
    private static final Map<String, String> ALGORITHM_TO_DIGEST = Map.of(
            "SHA256withRSA", "SHA-256",
            "SHA512withRSA", "SHA-512"
    );

    @Override
    public String getStepName() {
        return STEP_NAME;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public IntegrationMessage execute(IntegrationMessage message, FlowContext context) {
        String signingAlgorithm = context.getVariable("signingAlgorithm");
        String documentData = context.getVariable("documentData");

        log.debug("DataHashingStep: decoding document data and hashing with algorithm [{}]", signingAlgorithm);

        // Decode Base64 document data
        byte[] decodedData = decodeBase64(documentData);

        // Resolve the digest algorithm from the signing algorithm
        String digestAlgorithm = resolveDigestAlgorithm(signingAlgorithm);

        // Compute hash
        byte[] hash = computeHash(decodedData, digestAlgorithm);

        // Store hash in context for HsmSigningStep
        context.addVariable("dataHash", hash);

        log.debug("DataHashingStep: successfully computed {} hash ({} bytes)", digestAlgorithm, hash.length);

        return message;
    }

    /**
     * Decodes Base64-encoded document data.
     *
     * @param documentData the Base64-encoded string
     * @return decoded byte array
     * @throws DSignException if the data is not valid Base64
     */
    private byte[] decodeBase64(String documentData) {
        if (documentData == null || documentData.isBlank()) {
            throw new DSignException("Document data is empty or null");
        }
        try {
            return Base64.getDecoder().decode(documentData);
        } catch (IllegalArgumentException e) {
            throw new DSignException("Invalid Base64 document data: " + e.getMessage(), e);
        }
    }

    /**
     * Resolves the hash digest algorithm name from the signing algorithm.
     * For example, "SHA256withRSA" → "SHA-256", "SHA512withRSA" → "SHA-512".
     *
     * @param signingAlgorithm the full signing algorithm name
     * @return the MessageDigest algorithm name
     * @throws DSignException if the algorithm is not recognized
     */
    private String resolveDigestAlgorithm(String signingAlgorithm) {
        String digestAlgorithm = ALGORITHM_TO_DIGEST.get(signingAlgorithm);
        if (digestAlgorithm == null) {
            throw new DSignException(
                    "Cannot resolve hash algorithm from signing algorithm: " + signingAlgorithm);
        }
        return digestAlgorithm;
    }

    /**
     * Computes the hash of the given data using the specified digest algorithm.
     *
     * @param data the raw byte data to hash
     * @param digestAlgorithm the digest algorithm name (e.g., "SHA-256")
     * @return the hash bytes
     * @throws DSignException if the algorithm is not available
     */
    private byte[] computeHash(byte[] data, String digestAlgorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(digestAlgorithm);
            return digest.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new DSignException(
                    "Hash algorithm not available: " + digestAlgorithm + " - " + e.getMessage(), e);
        }
    }
}
