package msb.com.vn.dsign.hsm;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.dsign.config.HsmConfig;
import msb.com.vn.dsign.exception.HsmUnavailableException;
import msb.com.vn.integration.common.enums.ProtocolType;
import msb.com.vn.integration.common.model.IntegrationMessage;
import msb.com.vn.integration.core.adapter.IntegrationAdapter;
import msb.com.vn.integration.core.plugin.PluginRegistry;
import org.springframework.stereotype.Component;

import java.security.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * PKCS#11-based HSM adapter that communicates with the HSM through
 * a connection pool of PKCS#11 sessions.
 *
 * <p>Implements both {@link HsmAdapter} for signing operations and
 * {@link IntegrationAdapter} for health monitoring via the {@link PluginRegistry}.</p>
 *
 * <p>Since real PKCS#11 hardware is not available in development,
 * the sign operation is simulated using Java's {@link java.security.Signature} class.
 * In production, this would delegate to the native PKCS#11 provider.</p>
 */
@Slf4j
@Component
public class Pkcs11HsmAdapter implements HsmAdapter, IntegrationAdapter {

    private static final String ADAPTER_NAME = "pkcs11-hsm-adapter";

    private final HsmConnectionPool connectionPool;
    private final HsmConfig hsmConfig;
    private final PluginRegistry pluginRegistry;

    public Pkcs11HsmAdapter(HsmConnectionPool connectionPool,
                            HsmConfig hsmConfig,
                            PluginRegistry pluginRegistry) {
        this.connectionPool = connectionPool;
        this.hsmConfig = hsmConfig;
        this.pluginRegistry = pluginRegistry;
    }

    /**
     * Register this adapter with the PluginRegistry on startup for health monitoring.
     */
    @PostConstruct
    public void register() {
        pluginRegistry.registerAdapter(this);
        log.info("Pkcs11HsmAdapter registered with PluginRegistry as '{}'", ADAPTER_NAME);
    }

    // ===== HsmAdapter methods =====

    /**
     * {@inheritDoc}
     *
     * <p>Borrows a session from the pool, performs the signing operation,
     * and returns the session back to the pool. The borrow/return pattern
     * ensures sessions are properly recycled.</p>
     *
     * <p>In this development implementation, signing is simulated using
     * Java's {@link Signature} class with an in-memory key pair.
     * In production, the native PKCS#11 session would be used.</p>
     */
    @Override
    public byte[] sign(byte[] dataHash, String keyReference, String algorithm) {
        HsmSession session = null;
        try {
            session = connectionPool.borrowSession();
            log.debug("Borrowed HSM session for slot={}, keyRef={}, algorithm={}",
                    session.getSlotId(), keyReference, algorithm);

            byte[] signature = performSignOperation(session, dataHash, keyReference, algorithm);

            log.debug("Signing completed successfully on slot={}", session.getSlotId());
            return signature;
        } finally {
            if (session != null) {
                connectionPool.returnSession(session);
                log.debug("Returned HSM session for slot={}", session.getSlotId());
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Checks that the connection pool has at least one available session,
     * indicating the HSM is reachable and operational.</p>
     */
    @Override
    public boolean isHealthy() {
        try {
            int available = connectionPool.getAvailableCount();
            boolean healthy = available > 0;
            if (!healthy) {
                log.warn("HSM health check failed: no available sessions in pool");
            }
            return healthy;
        } catch (Exception e) {
            log.error("HSM health check encountered an error", e);
            return false;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Maps from the configured HSM slots in {@link HsmConfig} to
     * a list of {@link HsmSlotInfo} DTOs with current availability status.</p>
     */
    @Override
    public List<HsmSlotInfo> listSlots() {
        if (hsmConfig.getSlots() == null || hsmConfig.getSlots().isEmpty()) {
            return List.of();
        }

        return hsmConfig.getSlots().stream()
                .map(slotConfig -> HsmSlotInfo.builder()
                        .slotId(slotConfig.getId())
                        .available(true) // slots from config are considered available
                        .build())
                .collect(Collectors.toList());
    }

    // ===== IntegrationAdapter methods =====

    @Override
    public String getName() {
        return ADAPTER_NAME;
    }

    @Override
    public ProtocolType getProtocol() {
        return ProtocolType.CUSTOM;
    }

    /**
     * HSM adapter does not support message routing.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public IntegrationMessage send(IntegrationMessage message) {
        throw new UnsupportedOperationException(
                "Pkcs11HsmAdapter does not support message routing. Use sign() for HSM operations.");
    }

    /**
     * HSM adapter does not handle integration messages.
     *
     * @return always false
     */
    @Override
    public boolean supports(IntegrationMessage message) {
        return false;
    }

    // ===== Private helpers =====

    /**
     * Simulates the PKCS#11 sign operation.
     * In production, this would use the native session handle to invoke
     * C_SignInit / C_Sign on the HSM hardware.
     *
     * <p>For development/testing, we use Java's {@link Signature} class
     * with an in-memory RSA key pair as a placeholder.</p>
     */
    private byte[] performSignOperation(HsmSession session, byte[] dataHash,
                                        String keyReference, String algorithm) {
        try {
            // Simulate HSM signing using Java security APIs as placeholder.
            // In production: use session.getNativeSession() to call PKCS#11 C_Sign
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();

            Signature sig = Signature.getInstance(algorithm);
            sig.initSign(keyPair.getPrivate());
            sig.update(dataHash);

            return sig.sign();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Unsupported signing algorithm: " + algorithm, e);
        } catch (InvalidKeyException | SignatureException e) {
            throw new HsmUnavailableException(
                    "HSM signing operation failed on slot " + session.getSlotId(), session.getSlotId(), e);
        }
    }
}
