package msb.com.vn.dsign.hsm;

import java.util.List;

/**
 * Interface for Hardware Security Module (HSM) operations.
 * Implementations communicate with the HSM via PKCS#11 to perform
 * signing operations without exposing private key material.
 *
 * <p>The private key never leaves the HSM boundary — only the data hash
 * is sent to the HSM, and the signed result is returned.</p>
 */
public interface HsmAdapter {

    /**
     * Sign a data hash using the private key identified by keyReference.
     * The private key never leaves the HSM boundary.
     *
     * @param dataHash     the pre-computed hash of the data to sign
     * @param keyReference identifier for the private key stored in the HSM
     * @param algorithm    signing algorithm (e.g., "SHA256withRSA", "SHA512withRSA")
     * @return the digital signature bytes
     */
    byte[] sign(byte[] dataHash, String keyReference, String algorithm);

    /**
     * Check HSM connectivity and slot availability.
     *
     * @return true if the HSM is reachable and at least one slot is available
     */
    boolean isHealthy();

    /**
     * List available HSM slots with their current availability status.
     *
     * @return list of HSM slot information
     */
    List<HsmSlotInfo> listSlots();
}
