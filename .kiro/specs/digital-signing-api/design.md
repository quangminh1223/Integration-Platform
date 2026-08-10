# Design Document: Digital Signing API (D-Sign)

## Overview

The Digital Signing API (D-Sign) is a new Maven module providing a unified REST API for digital document/data signing, signature verification, and certificate lifecycle management. It integrates with Hardware Security Modules (HSM) via PKCS#11 for secure key operations and external Public Certificate Authorities (CA) through a provider-agnostic interface. The module leverages the existing `msb-crypto` library and plugs into the integration-platform's FlowStep-based orchestration engine.

## Architecture

The D-Sign module is a new Maven sub-module (`d-sign/`) that plugs into the existing integration-platform flow engine. It exposes REST APIs for signing, verification, and certificate management while delegating cryptographic operations to HSM hardware via PKCS#11 and certificate lifecycle to external CA providers.

```
┌─────────────────────────────────────────────────────────────────────┐
│                       D-Sign REST Controllers                        │
│   SigningController  │  CertificateController  │  VerifyController   │
└──────────┬───────────────────────┬──────────────────────┬───────────┘
           │                       │                      │
┌──────────▼───────────────────────▼──────────────────────▼───────────┐
│                         Service Layer                                 │
│   SigningService  │  CertificateService  │  VerificationService      │
└──────────┬───────────────────────┬──────────────────────┬───────────┘
           │                       │                      │
┌──────────▼───────────────────────▼──────────────────────▼───────────┐
│                   Flow Engine (FlowStep chain)                        │
│  Validate → ResolveCert → RetrieveKey → Hash → HsmSign → Assemble   │
└──────────┬───────────────────────┬──────────────────────────────────┘
           │                       │
     ┌─────▼─────┐          ┌─────▼──────┐
     │ HSM Adapter│          │ CA Provider │
     │ (PKCS#11)  │          │ (OCSP/CRL) │
     └────────────┘          └────────────┘
```

## Module Structure

```
d-sign/
├── pom.xml
└── src/main/java/msb/com/vn/dsign/
    ├── DSignAutoConfiguration.java
    ├── controller/
    │   ├── SigningController.java
    │   ├── CertificateController.java
    │   └── VerificationController.java
    ├── dto/
    │   ├── request/
    │   │   ├── SigningRequest.java
    │   │   ├── VerifyRequest.java
    │   │   ├── CertificateRegistrationRequest.java
    │   │   └── CertificateQueryRequest.java
    │   └── response/
    │       ├── SigningResponse.java
    │       ├── VerifyResponse.java
    │       └── CertificateResponse.java
    ├── service/
    │   ├── SigningService.java
    │   ├── CertificateService.java
    │   └── VerificationService.java
    ├── domain/
    │   ├── entity/
    │   │   ├── Certificate.java
    │   │   └── SigningAuditLog.java
    │   └── repository/
    │       ├── CertificateRepository.java
    │       └── SigningAuditLogRepository.java
    ├── hsm/
    │   ├── HsmAdapter.java              (interface)
    │   ├── Pkcs11HsmAdapter.java        (implementation)
    │   ├── HsmConnectionPool.java
    │   └── HsmConfig.java
    ├── ca/
    │   ├── CaProvider.java              (interface)
    │   ├── CaProviderRegistry.java
    │   ├── OcspValidator.java
    │   ├── CrlValidator.java
    │   └── impl/                        (per-CA implementations)
    ├── flow/
    │   ├── SigningFlowOrchestrator.java
    │   ├── steps/
    │   │   ├── RequestValidationStep.java
    │   │   ├── CertificateResolutionStep.java
    │   │   ├── KeyRetrievalStep.java
    │   │   ├── DataHashingStep.java
    │   │   ├── HsmSigningStep.java
    │   │   └── ResponseAssemblyStep.java
    │   └── SigningFlowContext.java
    ├── audit/
    │   ├── AuditService.java
    │   └── AuditKafkaProducer.java
    ├── mapping/
    │   └── CertificateMapper.java
    ├── config/
    │   ├── DSignConfig.java
    │   └── DSignResilienceConfig.java
    └── exception/
        ├── DSignException.java
        ├── CertificateNotFoundException.java
        ├── HsmUnavailableException.java
        └── UnsupportedAlgorithmException.java
```

## Components and Interfaces

### 1. REST Controllers (Thin Layer)

Controllers handle HTTP binding, Jakarta Validation delegation, and response mapping. All business logic lives in the service layer.

```java
@RestController
@RequestMapping("/api/v1/d-sign")
@RequiredArgsConstructor
@Slf4j
public class SigningController {

    private final SigningService signingService;

    @PostMapping("/sign")
    public ResponseEntity<IntegrationResponse<SigningResponse>> sign(
            @Valid @RequestBody SigningRequest request) {
        SigningResponse response = signingService.sign(request);
        return ResponseEntity.ok(
            IntegrationResponse.success(response.getCorrelationId(), response));
    }
}
```

### 2. Service Layer

The `SigningService` delegates to the `SigningFlowOrchestrator` which executes the FlowStep chain. The service layer handles correlation ID generation and idempotency checks.

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class SigningService {

    private final SigningFlowOrchestrator flowOrchestrator;
    private final IdempotentStore idempotentStore;
    private final AuditService auditService;

    public SigningResponse sign(SigningRequest request) {
        String requestId = request.getRequestId();
        if (idempotentStore.isDuplicate(requestId)) {
            return getCachedResult(requestId);
        }
        idempotentStore.markProcessing(requestId);
        try {
            SigningResponse result = flowOrchestrator.execute(request);
            idempotentStore.markCompleted(requestId);
            auditService.logSigningSuccess(result);
            return result;
        } catch (Exception e) {
            idempotentStore.markFailed(requestId);
            auditService.logSigningFailure(request, e);
            throw e;
        }
    }
}
```

### 3. Flow Engine Integration

The signing pipeline is implemented as a chain of `FlowStep` implementations registered with the integration-platform's flow engine. Each step receives an `IntegrationMessage` and a `FlowContext`.

```java
@Component
@RequiredArgsConstructor
public class HsmSigningStep implements FlowStep {

    private final HsmAdapter hsmAdapter;

    @Override
    public String getStepName() { return "hsm-signing"; }

    @Override
    public int getOrder() { return 500; }

    @Override
    public IntegrationMessage execute(IntegrationMessage message, FlowContext context) {
        byte[] dataHash = context.getVariable("dataHash");
        String keyReference = context.getVariable("keyReference");
        String algorithm = context.getVariable("signingAlgorithm");

        byte[] signature = hsmAdapter.sign(dataHash, keyReference, algorithm);
        context.addVariable("signatureBytes", signature);

        return message;
    }
}
```

**Flow Step Order:**

| Order | Step | Responsibility |
|-------|------|----------------|
| 100 | RequestValidationStep | Validate mandatory fields, algorithm support |
| 200 | CertificateResolutionStep | Lookup certificate by reference, check validity |
| 300 | KeyRetrievalStep | Resolve HSM key reference from certificate |
| 400 | DataHashingStep | Hash document data with specified algorithm |
| 500 | HsmSigningStep | Send hash to HSM, receive signature bytes |
| 600 | ResponseAssemblyStep | Build SigningResponse with Base64 signature |

### 4. HSM Adapter

```java
public interface HsmAdapter {

    /**
     * Sign a data hash using the private key identified by keyReference.
     * The private key never leaves the HSM boundary.
     */
    byte[] sign(byte[] dataHash, String keyReference, String algorithm);

    /**
     * Check HSM connectivity and slot availability.
     */
    boolean isHealthy();

    /**
     * List available slots.
     */
    List<HsmSlotInfo> listSlots();
}
```

The `Pkcs11HsmAdapter` implementation manages a connection pool (`HsmConnectionPool`) with configurable:
- `dsign.hsm.pool-size` (default: 10)
- `dsign.hsm.connection-timeout-ms` (default: 5000)
- `dsign.hsm.idle-timeout-ms` (default: 60000)
- `dsign.hsm.max-queue-depth` (default: 50)
- `dsign.hsm.slots[].id` and `dsign.hsm.slots[].pin`

When the pool is exhausted, requests queue up to `max-queue-depth`. Beyond that, `HsmUnavailableException` (HTTP 503) is thrown.

### 5. CA Provider Interface

```java
public interface CaProvider {

    String getProviderName();

    /**
     * Issue a new certificate via the CA.
     */
    CertificateIssuanceResult issueCertificate(CertificateIssuanceRequest request);

    /**
     * Validate certificate status via OCSP or CRL.
     */
    CertificateValidationResult validateCertificate(X509Certificate certificate);

    /**
     * Check provider connectivity.
     */
    boolean isAvailable();
}
```

The `CaProviderRegistry` holds all configured `CaProvider` beans (auto-discovered via Spring DI). The active provider per environment is selected via `dsign.ca.active-provider` property.

### 6. Certificate Entity

```java
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
```

## Data Models

### Request DTOs

```java
@Data
@Builder
public class SigningRequest {
    @NotBlank
    private String requestId;          // Client-provided idempotency key

    @NotBlank
    private String documentData;       // Base64-encoded document data or pre-computed hash

    @NotBlank
    private String signerId;           // Signer identity

    @NotBlank
    private String certificateRef;     // Reference to registered certificate

    @NotBlank
    private String algorithm;          // e.g., "SHA256withRSA", "SHA512withRSA"

    private boolean includeTimestamp;  // Optional: include signing timestamp
}

@Data
@Builder
public class VerifyRequest {
    @NotBlank
    private String documentData;       // Original document data (Base64)

    @NotBlank
    private String signatureValue;     // Base64-encoded signature to verify

    @NotBlank
    private String certificateRef;     // Certificate used for signing

    @NotBlank
    private String algorithm;          // Algorithm used for signing
}

@Data
@Builder
public class CertificateRegistrationRequest {
    @NotBlank
    private String certificateData;    // PEM or Base64-encoded X.509 certificate

    @NotBlank
    private String ownerId;            // Certificate owner identity

    @NotBlank
    private String caProvider;         // CA provider identifier

    @NotBlank
    private String keyReference;       // HSM key reference for the private key
}
```

### Response DTOs

```java
@Data
@Builder
public class SigningResponse {
    private String correlationId;
    private String signatureValue;     // Base64-encoded signature
    private String certificateRef;     // Certificate used
    private String algorithm;          // Algorithm applied
    private Instant signingTimestamp;  // When signing occurred
}

@Data
@Builder
public class VerifyResponse {
    private String correlationId;
    private boolean valid;             // Cryptographic verification result
    private List<String> warnings;     // e.g., "Certificate expired"
}

@Data
@Builder
public class CertificateResponse {
    private String certRefId;
    private String subject;
    private String issuer;
    private String serialNumber;
    private Instant notBefore;
    private Instant notAfter;
    private String publicKeyBase64;
    private String keyReference;
    private String ownerId;
    private String caProvider;
    private CertificateStatus status;
}
```

## Interfaces

### Signing API

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/d-sign/sign` | Create digital signature |
| POST | `/api/v1/d-sign/verify` | Verify digital signature |

### Certificate API

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/d-sign/certificates` | Register a certificate |
| GET | `/api/v1/d-sign/certificates/{certRefId}` | Get certificate details |
| POST | `/api/v1/d-sign/certificates/{certRefId}/validate` | Validate cert via CA |

### Supported Algorithms

| Algorithm ID | JCA Name | Description |
|--------------|----------|-------------|
| `SHA256withRSA` | SHA256withRSA | SHA-256 hash + RSA signature |
| `SHA512withRSA` | SHA512withRSA | SHA-512 hash + RSA signature |

## Error Handling

All errors extend `DSignException` and are handled by a `@ControllerAdvice`:

| Exception | HTTP Code | Scenario |
|-----------|-----------|----------|
| `ValidationException` (Jakarta) | 400 | Missing/invalid request fields |
| `UnsupportedAlgorithmException` | 400 | Algorithm not in supported set |
| `CertificateNotFoundException` | 404 | Certificate reference not found |
| `CertificateInvalidException` | 400 | Expired, revoked, or malformed cert |
| `UnknownCaProviderException` | 400 | CA provider not configured |
| `HsmUnavailableException` | 503 | HSM unreachable or pool exhausted |
| `CaProviderException` | 502 | CA provider returned an error |
| `DSignException` | 500 | Unexpected internal error |

## Resilience Configuration

```java
@Configuration
public class DSignResilienceConfig {

    @Bean
    public CircuitBreakerConfig hsmCircuitBreaker() {
        return CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .slidingWindowSize(10)
            .build();
    }

    @Bean
    public TimeLimiterConfig hsmTimeLimiter() {
        return TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(5))
            .build();
    }

    @Bean
    public CircuitBreakerConfig caCircuitBreaker() {
        return CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .slidingWindowSize(5)
            .build();
    }
}
```

## Kafka Audit Events

On successful signing, a Kafka event is emitted to the `dsign.audit.events` topic:

```java
@Data
@Builder
public class SigningAuditEvent {
    private String correlationId;
    private String signerId;
    private String certificateRef;
    private String algorithm;
    private Instant timestamp;
    private String result;           // "SUCCESS" or "FAILURE"
    private String errorCode;        // null on success
    private String errorMessage;     // null on success
    private String failedStep;       // null on success
}
```

## Idempotency Strategy

Uses the existing `RedisIdempotentStore` with key format: `idem:dsign:{requestId}`. The client-provided `requestId` in `SigningRequest` serves as the idempotency key. Duplicate submissions within the 24-hour TTL return the cached `SigningResponse` without re-executing the flow.

## Configuration Properties

```yaml
dsign:
  algorithms:
    supported:
      - SHA256withRSA
      - SHA512withRSA
  hsm:
    pool-size: 10
    connection-timeout-ms: 5000
    idle-timeout-ms: 60000
    max-queue-depth: 50
    slots:
      - id: "slot-0"
        pin: "${HSM_SLOT_0_PIN}"
      - id: "slot-1"
        pin: "${HSM_SLOT_1_PIN}"
  ca:
    active-provider: "vnca"
    providers:
      vnca:
        base-url: "https://ca.vnca.vn"
        api-key: "${VNCA_API_KEY}"
        validation-method: "OCSP"  # OCSP or CRL
      other-ca:
        base-url: "https://other-ca.example.com"
        api-key: "${OTHER_CA_API_KEY}"
        validation-method: "CRL"
  audit:
    kafka-topic: "dsign.audit.events"
    retention-days: 365
  resilience:
    hsm:
      circuit-breaker-failure-rate: 50
      timeout-seconds: 5
    ca:
      circuit-breaker-failure-rate: 50
      timeout-seconds: 10
```

## Integration with Existing Platform

The D-Sign module integrates with the existing integration-platform by:

1. **FlowStep contract**: All signing steps implement `FlowStep` and are discoverable by the flow engine via Spring DI.
2. **PluginRegistry**: The `Pkcs11HsmAdapter` registers as an `IntegrationAdapter` for HSM protocol, allowing monitoring and health checks via the existing plugin infrastructure.
3. **IdempotentStore**: Reuses `RedisIdempotentStore` from integration-core with a D-Sign-specific key prefix.
4. **IntegrationMessage**: The signing pipeline wraps `SigningRequest` data into an `IntegrationMessage.payload` for flow engine compatibility.
5. **msb-crypto library**: The `DataHashingStep` and `VerificationService` delegate to `CryptoService` for hashing and signature verification using software keys (for verification when HSM is not needed).

## Sequence Diagram: Signing Flow

```
Client          Controller       Service         FlowOrchestrator    Steps...         HSM        Redis      Kafka
  │                 │               │                  │                │              │           │          │
  │ POST /sign      │               │                  │                │              │           │          │
  │────────────────>│               │                  │                │              │           │          │
  │                 │ sign(req)     │                  │                │              │           │          │
  │                 │──────────────>│                  │                │              │           │          │
  │                 │               │ isDuplicate?     │                │              │           │          │
  │                 │               │─────────────────────────────────────────────────────────────>│          │
  │                 │               │ false            │                │              │           │          │
  │                 │               │<─────────────────────────────────────────────────────────────│          │
  │                 │               │ markProcessing   │                │              │           │          │
  │                 │               │─────────────────────────────────────────────────────────────>│          │
  │                 │               │ execute(req)     │                │              │           │          │
  │                 │               │─────────────────>│                │              │           │          │
  │                 │               │                  │ validate       │              │           │          │
  │                 │               │                  │───────────────>│              │           │          │
  │                 │               │                  │ resolveCert    │              │           │          │
  │                 │               │                  │───────────────>│              │           │          │
  │                 │               │                  │ retrieveKey    │              │           │          │
  │                 │               │                  │───────────────>│              │           │          │
  │                 │               │                  │ hashData       │              │           │          │
  │                 │               │                  │───────────────>│              │           │          │
  │                 │               │                  │ hsmSign        │              │           │          │
  │                 │               │                  │───────────────>│──sign(hash)─>│           │          │
  │                 │               │                  │               │<─signature───│           │          │
  │                 │               │                  │ assemble       │              │           │          │
  │                 │               │                  │───────────────>│              │           │          │
  │                 │               │  SigningResponse  │                │              │           │          │
  │                 │               │<─────────────────│                │              │           │          │
  │                 │               │ markCompleted    │                │              │           │          │
  │                 │               │─────────────────────────────────────────────────────────────>│          │
  │                 │               │ emit audit event │                │              │           │          │
  │                 │               │────────────────────────────────────────────────────────────────────────>│
  │                 │  response     │                  │                │              │           │          │
  │                 │<──────────────│                  │                │              │           │          │
  │  200 OK         │               │                  │                │              │           │          │
  │<────────────────│               │                  │                │              │           │          │
```

## Testing Strategy

**Unit Tests (JUnit 5 + Mockito):**
- Service layer logic with mocked repositories, HSM adapter, and CA providers
- Flow step logic in isolation with mocked FlowContext
- Validation logic (algorithm support, field presence)
- Certificate parsing and entity mapping

**Property-Based Tests (jqwik):**
- Sign-then-verify round trip across random document data
- Input validation across random field combinations
- Flow execution integrity across random request payloads
- Idempotency behavior across random request IDs
- Audit log completeness across operation types

**Integration Tests (@SpringBootTest):**
- HSM adapter with PKCS#11 simulator
- CA provider communication (mocked external CA)
- Redis idempotent store behavior
- Kafka audit event publishing
- Full signing flow end-to-end

**Minimum 100 iterations per property test** to ensure edge cases are covered.

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Sign-then-verify round trip

*For any* valid document data and supported signing algorithm, if the D-Sign system produces a signature using a registered certificate's private key, then verifying that signature against the same document data and the certificate's public key SHALL return `valid = true`.

**Validates: Requirements 1.1, 6.1**

### Property 2: Signing response completeness

*For any* successfully processed signing request, the response SHALL contain all four required fields: a non-empty Base64 signature value, the certificate reference used, the algorithm applied, and a non-null signing timestamp.

**Validates: Requirements 1.6**

### Property 3: Missing mandatory field rejection

*For any* signing request where one or more mandatory fields (documentData, signerId, certificateRef, algorithm) are absent or blank, the API SHALL return HTTP 400 with a validation error listing exactly the set of missing fields.

**Validates: Requirements 1.3**

### Property 4: Unsupported algorithm rejection

*For any* signing request whose algorithm value is not in the configured supported set (SHA256withRSA, SHA512withRSA), the API SHALL return HTTP 400 indicating the algorithm is unsupported and listing the available algorithms.

**Validates: Requirements 1.4**

### Property 5: Certificate registration round trip

*For any* valid X.509 certificate registered with the system, querying by the returned certificate reference SHALL return all stored fields (subject, issuer, serial number, validity period, public key, key reference, owner) matching the original registration input.

**Validates: Requirements 2.1, 2.5, 2.6**

### Property 6: Invalid certificate registration rejection

*For any* certificate registration request containing an expired certificate OR referencing a CA provider not configured in the system, the API SHALL return HTTP 400 with an error message identifying the specific rejection reason.

**Validates: Requirements 2.3, 2.4**

### Property 7: OCSP/CRL validation result mapping

*For any* OCSP or CRL response status (good, revoked, unknown) returned by a CA provider, the D-Sign system SHALL correctly map the external status to the corresponding internal `CertificateValidationResult` (VALID, REVOKED, UNKNOWN).

**Validates: Requirements 4.3**

### Property 8: CA error propagation

*For any* error response returned by a CA provider during certificate issuance, the D-Sign system SHALL include the CA's error details (error code, description) in the API response without loss of information.

**Validates: Requirements 4.5**

### Property 9: Flow execution integrity

*For any* successfully completed signing flow, the audit trail SHALL contain entries for all six steps (validation, certificate resolution, key retrieval, data hashing, HSM signing, response assembly) in the correct order, and each entry SHALL carry the same non-null correlation ID.

**Validates: Requirements 5.1, 5.2**

### Property 10: Flow failure aborts remaining steps

*For any* signing flow where step N fails, the audit trail SHALL contain entries only for steps 1 through N (inclusive), the flow context SHALL be marked as aborted, and the error response SHALL identify step N by name.

**Validates: Requirements 5.3**

### Property 11: Idempotent signing

*For any* signing request submitted twice with the same `requestId`, the second submission SHALL return a response identical to the first without re-executing any flow steps (the flow step count in audit trail remains unchanged after the second call).

**Validates: Requirements 5.4**

### Property 12: Kafka audit event emission

*For any* successfully completed signing operation, the system SHALL emit exactly one Kafka event to the audit topic containing the correlation ID, signer identity, certificate reference, algorithm, timestamp, and result status "SUCCESS".

**Validates: Requirements 5.5**

### Property 13: Expired certificate verification includes warning

*For any* signature verification request referencing a certificate whose `notAfter` date is in the past, the response SHALL include a warning string indicating certificate expiration AND still return the cryptographic verification boolean result.

**Validates: Requirements 6.2**

### Property 14: Unknown certificate reference returns 404

*For any* verification or signing request referencing a certificate ID not registered in the system, the API SHALL return HTTP 404 with a message identifying the missing certificate reference.

**Validates: Requirements 6.3**

### Property 15: HSM adapter resilience under pool exhaustion

*For any* state where the HSM connection pool is fully occupied and the request queue has reached the configured `max-queue-depth`, the next incoming signing request SHALL receive an HTTP 503 response without blocking indefinitely.

**Validates: Requirements 3.3, 3.5**

### Property 16: Audit log completeness

*For any* signing or certificate operation (success or failure), the structured audit log entry SHALL contain: correlation ID, actor identity, operation type, timestamp, and result. For failures, it SHALL additionally contain the failed step name, error code, and error message.

**Validates: Requirements 7.1, 7.2, 7.3**
