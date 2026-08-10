package msb.com.vn.dsign.hsm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wrapper for a PKCS#11 session object.
 * In production, {@code nativeSession} would hold the actual
 * {@code sun.security.pkcs11.Session} or equivalent handle.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HsmSession {

    /**
     * HSM slot identifier this session belongs to.
     */
    private String slotId;

    /**
     * Timestamp (epoch millis) when this session was created.
     */
    private long createdAt;

    /**
     * Placeholder for the native PKCS#11 session handle.
     */
    private Object nativeSession;
}
