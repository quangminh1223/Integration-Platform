# Implementation Plan: Digital Signing API (D-Sign)

## Overview

Implement the D-Sign Maven module as a new sub-module under the ScanQRCode project. The module provides REST APIs for digital signing, signature verification, and certificate management, integrating with HSM via PKCS#11 and external CA providers. Implementation follows the existing integration-platform FlowStep pattern with Resilience4j, Redis idempotency, and Kafka audit events.

## Tasks

- [ ] 1. Set up D-Sign module structure and core interfaces
  - [ ] 1.1 Create Maven module and base configuration
    - Create `d-sign/pom.xml` with dependencies (Spring Boot Starter Web, Spring Data JPA, Spring Data Redis, Spring Kafka, Resilience4j, MapStruct, Lombok, jqwik, Jakarta Validation)
    - Register `d-sign` as a module in the root `pom.xml`
    - Create `DSignAutoConfiguration.java` with component scan and conditional configuration
    - Create `DSignConfig.java` with `@ConfigurationProperties(prefix = "dsign")` binding all configuration properties (algorithms, HSM, CA, audit, resilience)
    - Create `HsmConfig.java` for HSM-specific pool configuration
    - Add `dsign` section to `application.yml` with default values
    - _Requirements: 1.5, 3.4, 3.6, 4.6_

  - [ ] 1.2 Define exception hierarchy and global error handler
    - Create `DSignException.java` as base exception extending `RuntimeException`
    - Create `CertificateNotFoundException.java`, `HsmUnavailableException.java`, `UnsupportedAlgorithmException.java`, `CertificateInvalidException.java`, `UnknownCaProviderException.java`, `CaProviderException.java`
    - Create `DSignExceptionHandler.java` with `@ControllerAdvice` mapping each exception to the correct HTTP status code (400, 404, 502, 503, 500)
    - _Requirements: 1.3, 1.4, 2.3, 2.4, 3.3, 4.5_

  - [ ] 1.3 Define DTOs (request and response)
    - Create `SigningRequest.java` with Jakarta Validation annotations (`@NotBlank` on documentData, signerId, certificateRef, algorithm; `requestId`)
    - Create `VerifyRequest.java` with validation annotations
    - Create `CertificateRegistrationRequest.java` with validation annotations
    - Create `CertificateQueryRequest.java`
    - Create `SigningResponse.java`, `VerifyResponse.java`, `CertificateResponse.java`
    - Create `SigningAuditEvent.java` for Kafka event payload
    - _Requirements: 1.2, 1.6, 2.5, 6.1_

  - [ ] 1.4 Define domain entities and repositories
    - Create `Certificate.java` JPA entity mapped to `DSIGN_CERTIFICATE` table with all columns (certRefId, subject, issuer, serialNumber, notBefore, notAfter, publicKeyBase64, keyReference, ownerId, caProvider, status, createdAt, updatedAt)
    - Create `CertificateStatus` enum (ACTIVE, EXPIRED, REVOKED, SUSPENDED)
    - Create `SigningAuditLog.java` JPA entity for persistent audit storage
    - Create `CertificateRepository.java` (Spring Data JPA)
    - Create `SigningAuditLogRepository.java` (Spring Data JPA)
    - Create database migration script for `DSIGN_CERTIFICATE` and `DSIGN_AUDIT_LOG` tables
    - _Requirements: 2.1, 2.5, 7.1, 7.4_

  - [ ] 1.5 Define core interfaces (HsmAdapter, CaProvider)
    - Create `HsmAdapter.java` interface with methods: `sign(byte[] dataHash, String keyReference, String algorithm)`, `isHealthy()`, `listSlots()`
    - Create `CaProvider.java` interface with methods: `getProviderName()`, `issueCertificate(...)`, `validateCertificate(...)`, `isAvailable()`
    - Create `CaProviderRegistry.java` with registry pattern to hold and resolve `CaProvider` beans by name
    - Create `CertificateMapper.java` MapStruct interface for entity ↔ DTO conversion
    - _Requirements: 3.1, 3.2, 4.1, 4.4_

- [ ] 2. Implement HSM adapter and connection pool
  - [ ] 2.1 Implement HsmConnectionPool
    - Create `HsmConnectionPool.java` managing PKCS#11 session objects
    - Implement configurable pool size, connection timeout, idle timeout, and max queue depth from `HsmConfig`
    - Implement `borrowSession()` that queues when pool is exhausted and throws `HsmUnavailableException` when queue exceeds `max-queue-depth`
    - Implement `returnSession()` for session recycling
    - _Requirements: 3.4, 3.5_

  - [ ] 2.2 Implement Pkcs11HsmAdapter
    - Create `Pkcs11HsmAdapter.java` implementing `HsmAdapter`
    - Implement `sign()` method: borrow session from pool, execute PKCS#11 sign operation, return session
    - Implement `isHealthy()` checking pool availability and slot reachability
    - Implement `listSlots()` returning configured HSM slot information
    - Register as `IntegrationAdapter` in PluginRegistry for health monitoring
    - _Requirements: 3.1, 3.2, 3.3, 3.6_

  - [ ]* 2.3 Write property test for HSM pool exhaustion behavior
    - **Property 15: HSM adapter resilience under pool exhaustion**
    - Verify that when pool is full and queue is at max depth, next request gets HTTP 503 without blocking
    - Use jqwik to generate random concurrent request counts exceeding pool capacity
    - **Validates: Requirements 3.3, 3.5**

- [ ] 3. Implement CA provider integration
  - [ ] 3.1 Implement CaProviderRegistry and base CA provider
    - Implement `CaProviderRegistry.java` auto-discovering `CaProvider` beans via Spring DI
    - Implement `getProvider(String name)` returning the named provider or throwing `UnknownCaProviderException`
    - Implement `getActiveProvider()` resolving from `dsign.ca.active-provider` config
    - _Requirements: 4.1, 4.4, 4.6_

  - [ ] 3.2 Implement OCSP and CRL validators
    - Create `OcspValidator.java` implementing OCSP certificate status check
    - Create `CrlValidator.java` implementing CRL-based certificate status check
    - Map external status (good/revoked/unknown) to internal `CertificateValidationResult` (VALID, REVOKED, UNKNOWN)
    - _Requirements: 4.3_

  - [ ]* 3.3 Write property test for OCSP/CRL status mapping
    - **Property 7: OCSP/CRL validation result mapping**
    - Verify that for any OCSP/CRL response status, the correct internal status is produced
    - Use jqwik to generate all possible status combinations
    - **Validates: Requirements 4.3**

- [ ] 4. Checkpoint - Ensure core infrastructure compiles and tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 5. Implement signing flow steps
  - [ ] 5.1 Implement RequestValidationStep
    - Create `RequestValidationStep.java` implementing `FlowStep` with order 100
    - Validate mandatory fields present in `IntegrationMessage` payload
    - Validate algorithm is in the configured supported set
    - On failure, throw appropriate exception with details of missing/invalid fields
    - _Requirements: 1.3, 1.4, 5.1_

  - [ ]* 5.2 Write property test for mandatory field rejection
    - **Property 3: Missing mandatory field rejection**
    - Use jqwik to generate signing requests with random combinations of blank/missing fields
    - Verify HTTP 400 with validation error listing exactly the missing fields
    - **Validates: Requirements 1.3**

  - [ ]* 5.3 Write property test for unsupported algorithm rejection
    - **Property 4: Unsupported algorithm rejection**
    - Use jqwik to generate random algorithm strings not in the supported set
    - Verify HTTP 400 indicating unsupported algorithm and listing available algorithms
    - **Validates: Requirements 1.4**

  - [ ] 5.4 Implement CertificateResolutionStep
    - Create `CertificateResolutionStep.java` implementing `FlowStep` with order 200
    - Look up certificate by reference from `CertificateRepository`
    - Throw `CertificateNotFoundException` if not found
    - Validate certificate is not expired or revoked; throw `CertificateInvalidException` if invalid
    - Place certificate details and key reference in `FlowContext`
    - _Requirements: 2.6, 5.1, 6.3_

  - [ ] 5.5 Implement KeyRetrievalStep
    - Create `KeyRetrievalStep.java` implementing `FlowStep` with order 300
    - Extract key reference from `FlowContext` (set by CertificateResolutionStep)
    - Validate key reference format and place in context for HSM step
    - _Requirements: 3.2, 5.1_

  - [ ] 5.6 Implement DataHashingStep
    - Create `DataHashingStep.java` implementing `FlowStep` with order 400
    - Decode Base64 document data from request
    - Hash using the algorithm's hash component (SHA-256 or SHA-512) via `CryptoService`
    - Place hash bytes in `FlowContext`
    - _Requirements: 1.1, 5.1_

  - [ ] 5.7 Implement HsmSigningStep
    - Create `HsmSigningStep.java` implementing `FlowStep` with order 500
    - Retrieve data hash and key reference from `FlowContext`
    - Call `HsmAdapter.sign()` with Resilience4j circuit breaker and timeout
    - Place signature bytes in `FlowContext`
    - _Requirements: 1.1, 3.2, 5.1, 5.6_

  - [ ] 5.8 Implement ResponseAssemblyStep
    - Create `ResponseAssemblyStep.java` implementing `FlowStep` with order 600
    - Build `SigningResponse` from `FlowContext` data: Base64-encode signature, set certificate ref, algorithm, and timestamp
    - Place response in `FlowContext` for service layer retrieval
    - _Requirements: 1.6, 5.1_

  - [ ]* 5.9 Write property test for signing response completeness
    - **Property 2: Signing response completeness**
    - Use jqwik to generate random valid signing requests
    - Verify response always contains non-empty signature, certificate ref, algorithm, and non-null timestamp
    - **Validates: Requirements 1.6**

- [ ] 6. Implement SigningFlowOrchestrator and SigningFlowContext
  - [ ] 6.1 Create SigningFlowOrchestrator
    - Create `SigningFlowOrchestrator.java` orchestrating the six flow steps in order
    - Generate correlation ID at flow start and propagate through `FlowContext`
    - Wrap execution with try-catch: on failure, mark context as aborted and capture failed step name
    - Log step entry/exit for audit trail
    - _Requirements: 5.1, 5.2, 5.3_

  - [ ] 6.2 Create SigningFlowContext
    - Create `SigningFlowContext.java` extending or wrapping `FlowContext`
    - Hold correlation ID, step audit entries, abort status, and error details
    - Provide `addStepAudit(stepName, status)` for flow integrity tracking
    - _Requirements: 5.2, 5.3_

  - [ ]* 6.3 Write property test for flow execution integrity
    - **Property 9: Flow execution integrity**
    - Verify that successful flows have audit entries for all 6 steps in correct order with same correlation ID
    - Use jqwik to generate random valid request payloads
    - **Validates: Requirements 5.1, 5.2**

  - [ ]* 6.4 Write property test for flow failure abort behavior
    - **Property 10: Flow failure aborts remaining steps**
    - Verify that when step N fails, only steps 1..N appear in audit trail, context is aborted, and error identifies step N
    - Use jqwik to generate random failure points across the 6 steps
    - **Validates: Requirements 5.3**

- [ ] 7. Implement service layer (SigningService, CertificateService, VerificationService)
  - [ ] 7.1 Implement SigningService
    - Create `SigningService.java` with `sign(SigningRequest)` method
    - Check idempotency via `RedisIdempotentStore` with key `idem:dsign:{requestId}`
    - If duplicate, return cached result
    - Otherwise mark processing, delegate to `SigningFlowOrchestrator`, mark completed on success or failed on error
    - Call `AuditService` on success/failure
    - _Requirements: 1.1, 5.4_

  - [ ]* 7.2 Write property test for idempotent signing
    - **Property 11: Idempotent signing**
    - Verify that duplicate requests return identical responses without re-executing flow steps
    - Use jqwik to generate random request IDs and submit twice
    - **Validates: Requirements 5.4**

  - [ ] 7.3 Implement CertificateService
    - Create `CertificateService.java` with `register(CertificateRegistrationRequest)`, `getByRef(String certRefId)`, `validate(String certRefId)` methods
    - Parse X.509 certificate, validate format and expiration
    - Check CA provider exists in `CaProviderRegistry`
    - Persist via `CertificateRepository`
    - Delegate validation to CA provider's OCSP/CRL validators
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 4.3_

  - [ ]* 7.4 Write property test for certificate registration round trip
    - **Property 5: Certificate registration round trip**
    - Verify registered certificate can be queried back with all fields matching
    - Use jqwik to generate random valid certificate metadata
    - **Validates: Requirements 2.1, 2.5, 2.6**

  - [ ]* 7.5 Write property test for invalid certificate registration rejection
    - **Property 6: Invalid certificate registration rejection**
    - Verify expired certificates or unknown CA providers are rejected with HTTP 400 and correct reason
    - Use jqwik to generate expired dates and unknown provider names
    - **Validates: Requirements 2.3, 2.4**

  - [ ] 7.6 Implement VerificationService
    - Create `VerificationService.java` with `verify(VerifyRequest)` method
    - Look up certificate by reference (throw 404 if not found)
    - Decode document data and signature from Base64
    - Verify signature using public key from certificate via `CryptoService`
    - If certificate expired, add warning to response but still return cryptographic result
    - _Requirements: 6.1, 6.2, 6.3, 6.4_

  - [ ]* 7.7 Write property test for sign-then-verify round trip
    - **Property 1: Sign-then-verify round trip**
    - Verify that signing then verifying with the same data and certificate always returns valid=true
    - Use jqwik to generate random document data and supported algorithms
    - **Validates: Requirements 1.1, 6.1**

  - [ ]* 7.8 Write property test for expired certificate verification warning
    - **Property 13: Expired certificate verification includes warning**
    - Verify that verification with an expired cert includes a warning but still returns the boolean result
    - Use jqwik to generate certificates with past notAfter dates
    - **Validates: Requirements 6.2**

  - [ ]* 7.9 Write property test for unknown certificate reference 404
    - **Property 14: Unknown certificate reference returns 404**
    - Verify that requests with non-existent certificate refs return HTTP 404
    - Use jqwik to generate random non-existent certificate ref IDs
    - **Validates: Requirements 6.3**

- [ ] 8. Checkpoint - Ensure signing flow and services compile and unit tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 9. Implement audit and Kafka integration
  - [ ] 9.1 Implement AuditService and AuditKafkaProducer
    - Create `AuditService.java` logging signing/certificate operations to `SigningAuditLog` entity
    - Create `AuditKafkaProducer.java` emitting `SigningAuditEvent` to `dsign.audit.events` topic
    - Log success events with correlationId, signerId, certificateRef, algorithm, timestamp, result
    - Log failure events with additional failedStep, errorCode, errorMessage
    - _Requirements: 7.1, 7.2, 7.3, 7.4_

  - [ ]* 9.2 Write property test for Kafka audit event emission
    - **Property 12: Kafka audit event emission**
    - Verify that successful signing emits exactly one Kafka event with all required fields and status "SUCCESS"
    - Use jqwik to generate random successful signing scenarios
    - **Validates: Requirements 5.5**

  - [ ]* 9.3 Write property test for audit log completeness
    - **Property 16: Audit log completeness**
    - Verify that every operation (success or failure) produces audit entry with required fields
    - For failures, verify additional fields (failedStep, errorCode, errorMessage) are present
    - Use jqwik to generate random operations with random outcomes
    - **Validates: Requirements 7.1, 7.2, 7.3**

- [ ] 10. Implement REST controllers
  - [ ] 10.1 Implement SigningController
    - Create `SigningController.java` with `POST /api/v1/d-sign/sign` endpoint
    - Accept `@Valid @RequestBody SigningRequest`, delegate to `SigningService`
    - Return `IntegrationResponse<SigningResponse>` wrapper
    - _Requirements: 1.1, 1.2, 1.6_

  - [ ] 10.2 Implement VerificationController
    - Create `VerificationController.java` with `POST /api/v1/d-sign/verify` endpoint
    - Accept `@Valid @RequestBody VerifyRequest`, delegate to `VerificationService`
    - Return `IntegrationResponse<VerifyResponse>` wrapper
    - _Requirements: 6.1, 6.4_

  - [ ] 10.3 Implement CertificateController
    - Create `CertificateController.java` with endpoints:
      - `POST /api/v1/d-sign/certificates` — register certificate
      - `GET /api/v1/d-sign/certificates/{certRefId}` — query certificate
      - `POST /api/v1/d-sign/certificates/{certRefId}/validate` — validate via CA
    - Delegate to `CertificateService`
    - _Requirements: 2.1, 2.6, 4.3_

- [ ] 11. Implement resilience configuration
  - [ ] 11.1 Create DSignResilienceConfig
    - Create `DSignResilienceConfig.java` defining circuit breaker and time limiter beans
    - HSM circuit breaker: 50% failure rate, 30s open state, sliding window 10
    - HSM time limiter: 5s timeout
    - CA circuit breaker: 50% failure rate, 60s open state, sliding window 5
    - CA time limiter: 10s timeout
    - Apply decorators to `HsmSigningStep` and CA validation calls
    - _Requirements: 5.6, 3.3_

  - [ ]* 11.2 Write property test for CA error propagation
    - **Property 8: CA error propagation**
    - Verify that CA provider errors are returned in the API response without loss of detail
    - Use jqwik to generate random CA error codes and descriptions
    - **Validates: Requirements 4.5**

- [ ] 12. Wire everything together and final integration
  - [ ] 12.1 Wire auto-configuration and Spring DI
    - Ensure `DSignAutoConfiguration` registers all beans (controllers, services, flow steps, adapters, audit)
    - Add `spring.factories` or `@AutoConfiguration` entry for module auto-discovery
    - Verify component scan picks up all D-Sign packages
    - _Requirements: 4.4, 5.1_

  - [ ] 12.2 Add test configuration for D-Sign module
    - Create `src/test/resources/application.yml` with test-specific D-Sign config (embedded Redis, in-memory DB, mock HSM)
    - Create base test class with common mocks for `HsmAdapter` and `CaProvider`
    - _Requirements: All_

  - [ ]* 12.3 Write integration tests for full signing flow
    - Test end-to-end: register cert → sign document → verify signature
    - Use mocked HSM adapter and CA provider
    - Verify Redis idempotency and Kafka event emission
    - _Requirements: 1.1, 5.4, 5.5, 6.1_

- [ ] 13. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document (16 properties covered)
- Unit tests validate specific examples and edge cases
- The D-Sign module follows existing project conventions: Lombok, MapStruct, layered architecture, Resilience4j
- HSM adapter uses a mock/simulator in test environment — real PKCS#11 integration requires hardware access
- jqwik is used for property-based testing with minimum 100 iterations per property

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3"] },
    { "id": 1, "tasks": ["1.4", "1.5"] },
    { "id": 2, "tasks": ["2.1", "3.1", "3.2"] },
    { "id": 3, "tasks": ["2.2", "2.3", "3.3"] },
    { "id": 4, "tasks": ["5.1", "5.4", "5.5", "5.6"] },
    { "id": 5, "tasks": ["5.2", "5.3", "5.7", "5.8"] },
    { "id": 6, "tasks": ["5.9", "6.1", "6.2"] },
    { "id": 7, "tasks": ["6.3", "6.4", "7.1", "7.3"] },
    { "id": 8, "tasks": ["7.2", "7.4", "7.5", "7.6"] },
    { "id": 9, "tasks": ["7.7", "7.8", "7.9", "9.1"] },
    { "id": 10, "tasks": ["9.2", "9.3", "10.1", "10.2", "10.3"] },
    { "id": 11, "tasks": ["11.1", "11.2"] },
    { "id": 12, "tasks": ["12.1", "12.2"] },
    { "id": 13, "tasks": ["12.3"] }
  ]
}
```
