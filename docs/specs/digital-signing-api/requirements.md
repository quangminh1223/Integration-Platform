# Requirements Document

## Introduction

The Digital Signing API (D-Sign) provides a unified REST API for digital document/data signing and digital certificate registration. The system integrates with external Public Certificate Authorities (CA) and Hardware Security Modules (HSM) via a provider-agnostic abstraction layer. It leverages the existing `msb-crypto` library for base cryptographic operations and plugs into the integration-platform's flow engine for end-to-end orchestration of signing workflows.

## Glossary

- **D-Sign_System**: The digital signing module providing APIs for signing operations and certificate management
- **Signing_API**: The unified REST endpoint that accepts all required input data to perform a digital signature operation
- **Certificate_Registration_API**: The REST endpoint for registering and managing digital certificates with external CA providers
- **HSM_Adapter**: The component that interfaces with Hardware Security Modules via PKCS#11 for secure key storage and cryptographic operations
- **CA_Provider**: An external Public Certificate Authority that issues and manages digital certificates
- **Signing_Request**: The input payload containing document data, signer identity, certificate reference, and signing parameters
- **Signing_Flow**: The end-to-end processing pipeline from request reception through validation, key retrieval, signing, and response delivery
- **Certificate**: A digital certificate (X.509) binding a public key to an identity, issued by a CA_Provider
- **Key_Reference**: An identifier pointing to a private key stored in the HSM, used to perform signing without exposing key material
- **Flow_Engine**: The existing integration-platform Chain of Responsibility engine that orchestrates processing steps

## Requirements

### Requirement 1: Unified Digital Signing API

**User Story:** As an integrating application, I want a single API endpoint with all required input data for signing, so that I can digitally sign documents or data without managing multiple calls or external dependencies directly.

**Mô tả:** API ký số thống nhất cho phép ứng dụng tích hợp gửi một yêu cầu duy nhất chứa đầy đủ dữ liệu đầu vào (tài liệu, định danh người ký, tham chiếu chứng thư số, thuật toán ký) để thực hiện ký số tài liệu hoặc dữ liệu. Hệ thống sẽ tính toán chữ ký số bằng khóa riêng được tham chiếu và trả về chữ ký mã hóa Base64 trong phản hồi, giúp đơn giản hóa quy trình tích hợp mà không cần quản lý nhiều lệnh gọi API hoặc phụ thuộc bên ngoài.

#### Acceptance Criteria

1. WHEN a valid Signing_Request is received, THE Signing_API SHALL compute a digital signature over the provided data using the referenced private key and return the Base64-encoded signature in the response.
2. THE Signing_API SHALL accept the following input fields in a single request: document data (or hash), signer identifier, certificate reference, signing algorithm, and an optional timestamp flag.
3. WHEN a Signing_Request is missing any mandatory field, THE Signing_API SHALL return an HTTP 400 response with a validation error detailing each missing field.
4. WHEN a Signing_Request specifies an unsupported signing algorithm, THE Signing_API SHALL return an HTTP 400 response indicating the algorithm is not supported and list available algorithms.
5. THE Signing_API SHALL support SHA-256 with RSA (SHA256withRSA) and SHA-512 with RSA (SHA512withRSA) signing algorithms.
6. WHEN a Signing_Request is processed successfully, THE Signing_API SHALL return the signature value, the certificate used, the algorithm applied, and a signing timestamp in the response.

#### BIAN API Examples

**Service Domain:** DigitalSigning/Initiate

**Request:**
```json
{
  "serviceDomain": "DigitalSigning",
  "serviceOperation": "Initiate",
  "header": {
    "correlationId": "ds-20240115-001",
    "serviceProviderId": "MSB-DSIGN-001",
    "serviceDateTime": "2024-01-15T10:30:00+07:00",
    "initiatorId": "APP-MOBILE-BANKING"
  },
  "payload": {
    "digitalSigningInitiateRequest": {
      "signingRequestIdentifier": "req-abc-123",
      "documentData": "dGFpIGxpZXUga3kgc28gZGllbiB0dQ==",
      "signerReference": "USR-001-NGUYEN-VAN-A",
      "certificateReference": "CERT-REF-2024-0042",
      "signingAlgorithm": "SHA256withRSA",
      "includeTimestamp": true
    }
  }
}
```

**Success Response:**
```json
{
  "serviceDomain": "DigitalSigning",
  "serviceOperation": "Initiate",
  "header": {
    "correlationId": "ds-20240115-001",
    "serviceProviderId": "MSB-DSIGN-001",
    "serviceDateTime": "2024-01-15T10:30:01+07:00",
    "statusCode": "SUCCESS"
  },
  "payload": {
    "digitalSigningInitiateResponse": {
      "signingRequestIdentifier": "req-abc-123",
      "signatureValue": "MEUCIQD7x2l...base64EncodedSignature...==",
      "certificateReference": "CERT-REF-2024-0042",
      "signingAlgorithm": "SHA256withRSA",
      "signingTimestamp": "2024-01-15T10:30:01+07:00"
    }
  }
}
```

**Error Response (Missing Fields):**
```json
{
  "serviceDomain": "DigitalSigning",
  "serviceOperation": "Initiate",
  "header": {
    "correlationId": "ds-20240115-002",
    "serviceProviderId": "MSB-DSIGN-001",
    "serviceDateTime": "2024-01-15T10:31:00+07:00",
    "statusCode": "REJECTED"
  },
  "payload": {
    "digitalSigningInitiateError": {
      "httpStatusCode": 400,
      "errorType": "VALIDATION_ERROR",
      "errorDescription": "Request validation failed",
      "validationErrors": [
        {"field": "documentData", "message": "Field is mandatory"},
        {"field": "signerReference", "message": "Field is mandatory"}
      ],
      "supportedAlgorithms": ["SHA256withRSA", "SHA512withRSA"]
    }
  }
}
```

---

### Requirement 2: Certificate Registration API

**User Story:** As a system administrator, I want an API to register digital certificates with the D-Sign system, so that certificates from external CA providers can be managed and referenced during signing operations.

**Mô tả:** API đăng ký chứng thư số cho phép quản trị viên hệ thống đăng ký chứng thư số từ các nhà cung cấp CA bên ngoài vào hệ thống D-Sign. Hệ thống sẽ xác thực định dạng X.509 và chuỗi tin cậy, lưu trữ thông tin metadata của chứng thư (chủ thể, tổ chức phát hành, số serial, thời hạn hiệu lực, khóa công khai), liên kết với chủ sở hữu và trả về mã tham chiếu chứng thư duy nhất để sử dụng trong các thao tác ký số sau này.

#### Acceptance Criteria

1. WHEN a valid certificate registration request is received, THE Certificate_Registration_API SHALL store the certificate metadata and public key material, associate the certificate with the specified owner, and return a unique certificate reference identifier.
2. THE Certificate_Registration_API SHALL validate the X.509 certificate format and chain of trust before accepting registration.
3. IF a certificate has expired or has been revoked, THEN THE Certificate_Registration_API SHALL reject the registration with an HTTP 400 response indicating the certificate is not valid.
4. WHEN a certificate registration request references a CA_Provider not configured in the system, THE Certificate_Registration_API SHALL return an HTTP 400 response indicating the CA provider is unknown.
5. THE Certificate_Registration_API SHALL store certificate subject, issuer, serial number, validity period, public key, and associated Key_Reference for each registered certificate.
6. WHEN a certificate query request is received with a valid certificate reference, THE Certificate_Registration_API SHALL return the certificate details and its current validity status.

#### BIAN API Examples

**Service Domain:** CertificateManagement/Register

**Request:**
```json
{
  "serviceDomain": "CertificateManagement",
  "serviceOperation": "Register",
  "header": {
    "correlationId": "cm-20240115-001",
    "serviceProviderId": "MSB-DSIGN-001",
    "serviceDateTime": "2024-01-15T09:00:00+07:00",
    "initiatorId": "ADMIN-SYSTEM"
  },
  "payload": {
    "certificateRegistrationRequest": {
      "certificateData": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A...PEM-encoded-X509...",
      "ownerReference": "USR-001-NGUYEN-VAN-A",
      "certificateAuthorityReference": "VNCA",
      "keyReference": "HSM-SLOT0-KEY-001"
    }
  }
}
```

**Success Response:**
```json
{
  "serviceDomain": "CertificateManagement",
  "serviceOperation": "Register",
  "header": {
    "correlationId": "cm-20240115-001",
    "serviceProviderId": "MSB-DSIGN-001",
    "serviceDateTime": "2024-01-15T09:00:01+07:00",
    "statusCode": "SUCCESS"
  },
  "payload": {
    "certificateRegistrationResponse": {
      "certificateReferenceIdentifier": "CERT-REF-2024-0042",
      "subject": "CN=Nguyen Van A, O=MSB, C=VN",
      "issuer": "CN=VNCA Root CA, O=VNCA, C=VN",
      "serialNumber": "1A2B3C4D5E6F",
      "validityPeriod": {
        "notBefore": "2024-01-01T00:00:00+07:00",
        "notAfter": "2025-01-01T00:00:00+07:00"
      },
      "ownerReference": "USR-001-NGUYEN-VAN-A",
      "certificateStatus": "ACTIVE"
    }
  }
}
```

**Error Response (Expired Certificate):**
```json
{
  "serviceDomain": "CertificateManagement",
  "serviceOperation": "Register",
  "header": {
    "correlationId": "cm-20240115-002",
    "serviceProviderId": "MSB-DSIGN-001",
    "serviceDateTime": "2024-01-15T09:05:00+07:00",
    "statusCode": "REJECTED"
  },
  "payload": {
    "certificateRegistrationError": {
      "httpStatusCode": 400,
      "errorType": "CERTIFICATE_INVALID",
      "errorDescription": "Certificate is not valid for registration",
      "rejectionReason": "EXPIRED",
      "certificateExpiredAt": "2023-12-31T23:59:59+07:00"
    }
  }
}
```

**Query Request:**

**Service Domain:** CertificateManagement/Retrieve

```json
{
  "serviceDomain": "CertificateManagement",
  "serviceOperation": "Retrieve",
  "header": {
    "correlationId": "cm-20240115-003",
    "serviceProviderId": "MSB-DSIGN-001",
    "serviceDateTime": "2024-01-15T11:00:00+07:00",
    "initiatorId": "APP-MOBILE-BANKING"
  },
  "payload": {
    "certificateRetrieveRequest": {
      "certificateReferenceIdentifier": "CERT-REF-2024-0042"
    }
  }
}
```

**Query Response:**
```json
{
  "serviceDomain": "CertificateManagement",
  "serviceOperation": "Retrieve",
  "header": {
    "correlationId": "cm-20240115-003",
    "serviceProviderId": "MSB-DSIGN-001",
    "serviceDateTime": "2024-01-15T11:00:00+07:00",
    "statusCode": "SUCCESS"
  },
  "payload": {
    "certificateRetrieveResponse": {
      "certificateReferenceIdentifier": "CERT-REF-2024-0042",
      "subject": "CN=Nguyen Van A, O=MSB, C=VN",
      "issuer": "CN=VNCA Root CA, O=VNCA, C=VN",
      "serialNumber": "1A2B3C4D5E6F",
      "validityPeriod": {
        "notBefore": "2024-01-01T00:00:00+07:00",
        "notAfter": "2025-01-01T00:00:00+07:00"
      },
      "publicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A...",
      "keyReference": "HSM-SLOT0-KEY-001",
      "ownerReference": "USR-001-NGUYEN-VAN-A",
      "certificateAuthorityReference": "VNCA",
      "certificateStatus": "ACTIVE"
    }
  }
}
```

---

### Requirement 3: HSM Integration

**User Story:** As a security architect, I want the signing operations to use private keys stored in a Hardware Security Module, so that key material is never exposed outside the secure hardware boundary.

**Mô tả:** Tích hợp HSM (Hardware Security Module) đảm bảo tất cả thao tác ký số sử dụng khóa riêng được lưu trữ trong thiết bị phần cứng bảo mật. Hệ thống giao tiếp với HSM qua giao thức chuẩn PKCS#11, gửi hash dữ liệu đến HSM để ký mà không bao giờ trích xuất khóa riêng ra ngoài. HSM Adapter duy trì connection pool có thể cấu hình, hỗ trợ nhiều slot, và xử lý graceful khi connection pool cạn kiệt bằng cách xếp hàng yêu cầu đến giới hạn tối đa.

#### Acceptance Criteria

1. THE HSM_Adapter SHALL interface with the HSM via the PKCS#11 standard protocol.
2. WHEN a signing operation requires a private key, THE HSM_Adapter SHALL send the data hash to the HSM and receive the signed result without extracting the private key.
3. IF the HSM is unreachable or returns a connection error, THEN THE HSM_Adapter SHALL return a service unavailable error with a descriptive message and log the failure details.
4. THE HSM_Adapter SHALL maintain a connection pool to the HSM with configurable pool size, connection timeout, and idle timeout parameters.
5. WHILE the HSM connection pool is exhausted, THE HSM_Adapter SHALL queue incoming signing requests up to a configurable maximum queue depth and reject further requests with an HTTP 503 response.
6. THE HSM_Adapter SHALL support multiple HSM slots, each identified by a slot identifier and PIN, configurable via application properties.

#### BIAN API Examples

**Service Domain:** DigitalSigning/Execute (HSM signing is an internal step, surfaced in error responses)

**Error Response (HSM Unavailable):**
```json
{
  "serviceDomain": "DigitalSigning",
  "serviceOperation": "Execute",
  "header": {
    "correlationId": "ds-20240115-003",
    "serviceProviderId": "MSB-DSIGN-001",
    "serviceDateTime": "2024-01-15T10:35:00+07:00",
    "statusCode": "FAILED"
  },
  "payload": {
    "digitalSigningExecuteError": {
      "httpStatusCode": 503,
      "errorType": "HSM_UNAVAILABLE",
      "errorDescription": "Hardware Security Module is unreachable",
      "failedStep": "hsm-signing",
      "retryAfterSeconds": 30,
      "hsmDetails": {
        "slotIdentifier": "slot-0",
        "connectionPoolStatus": "EXHAUSTED",
        "currentQueueDepth": 50,
        "maxQueueDepth": 50
      }
    }
  }
}
```

**Error Response (Pool Exhausted - HTTP 503):**
```json
{
  "serviceDomain": "DigitalSigning",
  "serviceOperation": "Execute",
  "header": {
    "correlationId": "ds-20240115-004",
    "serviceProviderId": "MSB-DSIGN-001",
    "serviceDateTime": "2024-01-15T10:36:00+07:00",
    "statusCode": "FAILED"
  },
  "payload": {
    "digitalSigningExecuteError": {
      "httpStatusCode": 503,
      "errorType": "SERVICE_CAPACITY_EXCEEDED",
      "errorDescription": "HSM connection pool exhausted and queue depth reached maximum",
      "failedStep": "hsm-signing",
      "retryAfterSeconds": 10,
      "capacityDetails": {
        "poolSize": 10,
        "activeConnections": 10,
        "queuedRequests": 50,
        "maxQueueDepth": 50
      }
    }
  }
}
```

---

### Requirement 4: Public CA Provider Integration

**User Story:** As a system operator, I want the D-Sign system to integrate with multiple Public CA providers through a provider-agnostic interface, so that the system can issue, renew, and validate certificates from different CAs without code changes.

**Mô tả:** Tích hợp nhà cung cấp CA công cộng cho phép hệ thống D-Sign kết nối với nhiều nhà cung cấp Chứng thư số (Certificate Authority) khác nhau thông qua giao diện trừu tượng không phụ thuộc nhà cung cấp. Hệ thống hỗ trợ phát hành, gia hạn và xác thực chứng thư số từ các CA khác nhau (sử dụng OCSP hoặc CRL) mà không cần thay đổi mã nguồn. Việc thêm CA mới chỉ yêu cầu một lớp triển khai mới và cấu hình tương ứng.

#### Acceptance Criteria

1. THE D-Sign_System SHALL define a provider-agnostic CA integration interface that all CA_Provider implementations conform to.
2. WHEN a certificate issuance request is submitted, THE D-Sign_System SHALL delegate the request to the configured CA_Provider and return the issued certificate upon success.
3. WHEN a certificate validation request is received, THE D-Sign_System SHALL check the certificate status with the issuing CA_Provider using OCSP or CRL and return the validation result.
4. WHERE a new CA_Provider is added, THE D-Sign_System SHALL require only a new implementation class and configuration entry without modifying existing code.
5. IF a CA_Provider returns an error during certificate issuance, THEN THE D-Sign_System SHALL return the CA error details in the response and log the full error context for troubleshooting.
6. THE D-Sign_System SHALL support configuring the active CA_Provider per environment via application properties.

#### BIAN API Examples

**Service Domain:** CertificateManagement/Initiate (Certificate Issuance)

**Request:**
```json
{
  "serviceDomain": "CertificateManagement",
  "serviceOperation": "Initiate",
  "header": {
    "correlationId": "cm-20240115-010",
    "serviceProviderId": "MSB-DSIGN-001",
    "serviceDateTime": "2024-01-15T14:00:00+07:00",
    "initiatorId": "ADMIN-SYSTEM"
  },
  "payload": {
    "certificateIssuanceRequest": {
      "subjectDistinguishedName": "CN=Nguyen Van B, O=MSB, C=VN",
      "certificateAuthorityReference": "VNCA",
      "keyAlgorithm": "RSA",
      "keyLength": 2048,
      "validityDays": 365,
      "ownerReference": "USR-002-NGUYEN-VAN-B"
    }
  }
}
```

**Success Response:**
```json
{
  "serviceDomain": "CertificateManagement",
  "serviceOperation": "Initiate",
  "header": {
    "correlationId": "cm-20240115-010",
    "serviceProviderId": "MSB-DSIGN-001",
    "serviceDateTime": "2024-01-15T14:00:05+07:00",
    "statusCode": "SUCCESS"
  },
  "payload": {
    "certificateIssuanceResponse": {
      "certificateReferenceIdentifier": "CERT-REF-2024-0099",
      "certificateData": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A...issued-cert...",
      "subject": "CN=Nguyen Van B, O=MSB, C=VN",
      "issuer": "CN=VNCA Root CA, O=VNCA, C=VN",
      "serialNumber": "7F8A9B0C1D2E",
      "validityPeriod": {
        "notBefore": "2024-01-15T00:00:00+07:00",
        "notAfter": "2025-01-15T00:00:00+07:00"
      },
      "certificateAuthorityReference": "VNCA"
    }
  }
}
```

**Error Response (CA Provider Error):**
```json
{
  "serviceDomain": "CertificateManagement",
  "serviceOperation": "Initiate",
  "header": {
    "correlationId": "cm-20240115-011",
    "serviceProviderId": "MSB-DSIGN-001",
    "serviceDateTime": "2024-01-15T14:01:00+07:00",
    "statusCode": "FAILED"
  },
  "payload": {
    "certificateIssuanceError": {
      "httpStatusCode": 502,
      "errorType": "CA_PROVIDER_ERROR",
      "errorDescription": "Certificate Authority returned an error during issuance",
      "caProviderDetails": {
        "providerReference": "VNCA",
        "caErrorCode": "CSR_INVALID",
        "caErrorDescription": "Certificate Signing Request format is invalid",
        "caRequestId": "VNCA-REQ-2024-5678"
      }
    }
  }
}
```

**Certificate Validation Request:**

**Service Domain:** CertificateManagement/Execute (Validate)

```json
{
  "serviceDomain": "CertificateManagement",
  "serviceOperation": "Execute",
  "header": {
    "correlationId": "cm-20240115-012",
    "serviceProviderId": "MSB-DSIGN-001",
    "serviceDateTime": "2024-01-15T15:00:00+07:00",
    "initiatorId": "APP-MOBILE-BANKING"
  },
  "payload": {
    "certificateValidationRequest": {
      "certificateReferenceIdentifier": "CERT-REF-2024-0042",
      "validationMethod": "OCSP"
    }
  }
}
```

**Validation Response:**
```json
{
  "serviceDomain": "CertificateManagement",
  "serviceOperation": "Execute",
  "header": {
    "correlationId": "cm-20240115-012",
    "serviceProviderId": "MSB-DSIGN-001",
    "serviceDateTime": "2024-01-15T15:00:01+07:00",
    "statusCode": "SUCCESS"
  },
  "payload": {
    "certificateValidationResponse": {
      "certificateReferenceIdentifier": "CERT-REF-2024-0042",
      "validationResult": "VALID",
      "validationMethod": "OCSP",
      "ocspResponseStatus": "GOOD",
      "validatedAt": "2024-01-15T15:00:01+07:00",
      "nextUpdateExpected": "2024-01-16T15:00:00+07:00"
    }
  }
}
```

---

### Requirement 5: End-to-End Signing Flow Orchestration

**User Story:** As a platform engineer, I want the signing process to follow the existing integration-platform flow engine pattern, so that the signing pipeline is observable, retryable, and consistent with other platform flows.

**Mô tả:** Luồng ký số end-to-end được điều phối thông qua Flow Engine hiện có của integration-platform, thực thi tuần tự các bước: xác thực yêu cầu, phân giải chứng thư, truy xuất khóa, hash dữ liệu, ký HSM, và lắp ráp phản hồi. Mỗi luồng được gán một correlation ID duy nhất để truy vết. Hệ thống đảm bảo idempotency qua Redis, phát sự kiện Kafka cho kiểm toán, và áp dụng circuit breaker/timeout cho các bước gọi HSM và CA Provider.

#### Acceptance Criteria

1. THE Signing_Flow SHALL execute as a sequence of FlowStep implementations within the existing Flow_Engine: request validation, certificate resolution, key retrieval, data hashing, HSM signing, and response assembly.
2. WHEN a Signing_Flow is initiated, THE D-Sign_System SHALL generate a unique correlation ID and propagate the correlation ID through all flow steps for traceability.
3. IF any FlowStep in the Signing_Flow fails, THEN THE D-Sign_System SHALL abort the remaining steps, log the failure with correlation ID and step name, and return an error response indicating which step failed.
4. THE Signing_Flow SHALL enforce idempotency using the existing Redis-based idempotent store, keyed by a client-provided request identifier, so that duplicate submissions return the original result.
5. WHEN the Signing_Flow completes successfully, THE D-Sign_System SHALL emit a Kafka event containing the signing result, correlation ID, and timestamp for audit purposes.
6. THE Signing_Flow SHALL apply Resilience4j circuit breaker and timeout patterns on the HSM signing step and the CA_Provider validation step.

#### BIAN API Examples

**Service Domain:** DigitalSigning/Initiate (Flow with Correlation Tracking)

**Success Response (with flow metadata):**
```json
{
  "serviceDomain": "DigitalSigning",
  "serviceOperation": "Initiate",
  "header": {
    "correlationId": "ds-20240115-005",
    "serviceProviderId": "MSB-DSIGN-001",
    "serviceDateTime": "2024-01-15T11:00:02+07:00",
    "statusCode": "SUCCESS"
  },
  "payload": {
    "digitalSigningInitiateResponse": {
      "signingRequestIdentifier": "req-xyz-789",
      "signatureValue": "MGYCMQCn9r...base64Signature...==",
      "certificateReference": "CERT-REF-2024-0042",
      "signingAlgorithm": "SHA512withRSA",
      "signingTimestamp": "2024-01-15T11:00:02+07:00",
      "flowExecutionMetadata": {
        "correlationId": "ds-20240115-005",
        "stepsExecuted": [
          "request-validation",
          "certificate-resolution",
          "key-retrieval",
          "data-hashing",
          "hsm-signing",
          "response-assembly"
        ],
        "totalExecutionTimeMs": 245,
        "idempotencyKey": "req-xyz-789"
      }
    }
  }
}
```

**Error Response (Flow Step Failure):**
```json
{
  "serviceDomain": "DigitalSigning",
  "serviceOperation": "Initiate",
  "header": {
    "correlationId": "ds-20240115-006",
    "serviceProviderId": "MSB-DSIGN-001",
    "serviceDateTime": "2024-01-15T11:05:00+07:00",
    "statusCode": "FAILED"
  },
  "payload": {
    "digitalSigningInitiateError": {
      "httpStatusCode": 500,
      "errorType": "FLOW_STEP_FAILURE",
      "errorDescription": "Signing flow aborted due to step failure",
      "flowFailureDetails": {
        "correlationId": "ds-20240115-006",
        "failedStep": "certificate-resolution",
        "failedStepOrder": 200,
        "errorCode": "CERT_NOT_FOUND",
        "errorMessage": "Certificate reference CERT-REF-INVALID-001 not found in registry",
        "completedSteps": ["request-validation"],
        "abortedSteps": ["key-retrieval", "data-hashing", "hsm-signing", "response-assembly"]
      }
    }
  }
}
```

**Idempotent Duplicate Response:**
```json
{
  "serviceDomain": "DigitalSigning",
  "serviceOperation": "Initiate",
  "header": {
    "correlationId": "ds-20240115-005",
    "serviceProviderId": "MSB-DSIGN-001",
    "serviceDateTime": "2024-01-15T11:10:00+07:00",
    "statusCode": "SUCCESS",
    "duplicateRequest": true
  },
  "payload": {
    "digitalSigningInitiateResponse": {
      "signingRequestIdentifier": "req-xyz-789",
      "signatureValue": "MGYCMQCn9r...base64Signature...==",
      "certificateReference": "CERT-REF-2024-0042",
      "signingAlgorithm": "SHA512withRSA",
      "signingTimestamp": "2024-01-15T11:00:02+07:00",
      "idempotencyNote": "Result retrieved from cache - original request processed at 2024-01-15T11:00:02+07:00"
    }
  }
}
```

---

### Requirement 6: Signature Verification API

**User Story:** As an integrating application, I want an API to verify a digital signature against the original data and certificate, so that I can confirm document integrity and authenticity.

**Mô tả:** API xác minh chữ ký số cho phép ứng dụng tích hợp kiểm tra tính toàn vẹn và xác thực của tài liệu bằng cách xác minh chữ ký số dựa trên dữ liệu gốc và chứng thư số. Hệ thống trả về kết quả xác minh mật mã (hợp lệ/không hợp lệ) cùng với cảnh báo nếu chứng thư đã hết hạn tại thời điểm xác minh. API hỗ trợ xác minh chữ ký được tạo bằng các thuật toán SHA256withRSA và SHA512withRSA.

#### Acceptance Criteria

1. WHEN a signature verification request is received with document data, signature value, and certificate reference, THE Signing_API SHALL verify the signature and return a boolean result indicating validity.
2. WHEN the referenced certificate has expired at the time of verification, THE Signing_API SHALL include a warning in the response indicating the certificate was expired, while still returning the cryptographic verification result.
3. IF the signature verification request references a certificate not registered in the D-Sign_System, THEN THE Signing_API SHALL return an HTTP 404 response indicating the certificate is not found.
4. THE Signing_API SHALL support verifying signatures created with SHA256withRSA and SHA512withRSA algorithms.

#### BIAN API Examples

**Service Domain:** DigitalSigning/Execute (Verify)

**Request:**
```json
{
  "serviceDomain": "DigitalSigning",
  "serviceOperation": "Execute",
  "serviceAction": "Verify",
  "header": {
    "correlationId": "ds-20240115-020",
    "serviceProviderId": "MSB-DSIGN-001",
    "serviceDateTime": "2024-01-15T16:00:00+07:00",
    "initiatorId": "APP-INTERNET-BANKING"
  },
  "payload": {
    "signatureVerificationRequest": {
      "documentData": "dGFpIGxpZXUga3kgc28gZGllbiB0dQ==",
      "signatureValue": "MEUCIQD7x2l...base64EncodedSignature...==",
      "certificateReference": "CERT-REF-2024-0042",
      "verificationAlgorithm": "SHA256withRSA"
    }
  }
}
```

**Success Response (Valid Signature):**
```json
{
  "serviceDomain": "DigitalSigning",
  "serviceOperation": "Execute",
  "serviceAction": "Verify",
  "header": {
    "correlationId": "ds-20240115-020",
    "serviceProviderId": "MSB-DSIGN-001",
    "serviceDateTime": "2024-01-15T16:00:00+07:00",
    "statusCode": "SUCCESS"
  },
  "payload": {
    "signatureVerificationResponse": {
      "verificationResult": true,
      "certificateReference": "CERT-REF-2024-0042",
      "verificationAlgorithm": "SHA256withRSA",
      "verifiedAt": "2024-01-15T16:00:00+07:00",
      "warnings": []
    }
  }
}
```

**Response (Valid Signature with Expired Certificate Warning):**
```json
{
  "serviceDomain": "DigitalSigning",
  "serviceOperation": "Execute",
  "serviceAction": "Verify",
  "header": {
    "correlationId": "ds-20240115-021",
    "serviceProviderId": "MSB-DSIGN-001",
    "serviceDateTime": "2024-01-15T16:05:00+07:00",
    "statusCode": "SUCCESS"
  },
  "payload": {
    "signatureVerificationResponse": {
      "verificationResult": true,
      "certificateReference": "CERT-REF-2023-0010",
      "verificationAlgorithm": "SHA256withRSA",
      "verifiedAt": "2024-01-15T16:05:00+07:00",
      "warnings": [
        "Certificate CERT-REF-2023-0010 expired at 2023-12-31T23:59:59+07:00. Cryptographic verification was still performed."
      ]
    }
  }
}
```

**Error Response (Certificate Not Found):**
```json
{
  "serviceDomain": "DigitalSigning",
  "serviceOperation": "Execute",
  "serviceAction": "Verify",
  "header": {
    "correlationId": "ds-20240115-022",
    "serviceProviderId": "MSB-DSIGN-001",
    "serviceDateTime": "2024-01-15T16:10:00+07:00",
    "statusCode": "REJECTED"
  },
  "payload": {
    "signatureVerificationError": {
      "httpStatusCode": 404,
      "errorType": "CERTIFICATE_NOT_FOUND",
      "errorDescription": "Referenced certificate is not registered in the D-Sign system",
      "certificateReference": "CERT-REF-UNKNOWN-999"
    }
  }
}
```

---

### Requirement 7: Audit and Logging

**User Story:** As a compliance officer, I want all signing and certificate operations to be fully auditable, so that the organization can demonstrate regulatory compliance and investigate incidents.

**Mô tả:** Kiểm toán và ghi nhật ký đảm bảo tất cả thao tác ký số và quản lý chứng thư được ghi lại đầy đủ để tuân thủ quy định và hỗ trợ điều tra sự cố. Hệ thống ghi nhật ký có cấu trúc cho mọi thao tác ký số (correlation ID, định danh người ký, tham chiếu chứng thư, thuật toán, dấu thời gian, kết quả) và mọi thay đổi trạng thái chứng thư. Các bản ghi kiểm toán được lưu trữ theo chính sách retention có thể cấu hình và không bị xóa tự động trước khi hết thời hạn lưu trữ.

#### Acceptance Criteria

1. THE D-Sign_System SHALL log every signing operation with correlation ID, signer identity, certificate reference, algorithm, timestamp, and operation result to the structured audit log.
2. THE D-Sign_System SHALL log every certificate registration and status change with the certificate reference, action performed, actor identity, and timestamp.
3. WHEN a signing or certificate operation fails, THE D-Sign_System SHALL log the error details including the failed step, error code, and error message alongside the correlation ID.
4. THE D-Sign_System SHALL retain audit log entries according to the configured retention policy without automatic deletion before the retention period expires.

#### BIAN API Examples

**Service Domain:** DigitalSigning/Notify (Kafka Audit Event)

**Audit Event (Signing Success):**
```json
{
  "serviceDomain": "DigitalSigning",
  "serviceOperation": "Notify",
  "serviceAction": "AuditLog",
  "header": {
    "correlationId": "ds-20240115-005",
    "serviceProviderId": "MSB-DSIGN-001",
    "serviceDateTime": "2024-01-15T11:00:02+07:00",
    "eventType": "AUDIT"
  },
  "payload": {
    "signingAuditEvent": {
      "correlationId": "ds-20240115-005",
      "operationType": "DIGITAL_SIGNING",
      "signerReference": "USR-001-NGUYEN-VAN-A",
      "certificateReference": "CERT-REF-2024-0042",
      "signingAlgorithm": "SHA512withRSA",
      "operationTimestamp": "2024-01-15T11:00:02+07:00",
      "operationResult": "SUCCESS",
      "flowStepsCompleted": 6,
      "executionTimeMs": 245
    }
  }
}
```

**Audit Event (Signing Failure):**
```json
{
  "serviceDomain": "DigitalSigning",
  "serviceOperation": "Notify",
  "serviceAction": "AuditLog",
  "header": {
    "correlationId": "ds-20240115-006",
    "serviceProviderId": "MSB-DSIGN-001",
    "serviceDateTime": "2024-01-15T11:05:00+07:00",
    "eventType": "AUDIT"
  },
  "payload": {
    "signingAuditEvent": {
      "correlationId": "ds-20240115-006",
      "operationType": "DIGITAL_SIGNING",
      "signerReference": "USR-002-NGUYEN-VAN-B",
      "certificateReference": "CERT-REF-INVALID-001",
      "signingAlgorithm": "SHA256withRSA",
      "operationTimestamp": "2024-01-15T11:05:00+07:00",
      "operationResult": "FAILURE",
      "failedStep": "certificate-resolution",
      "errorCode": "CERT_NOT_FOUND",
      "errorMessage": "Certificate reference CERT-REF-INVALID-001 not found in registry",
      "flowStepsCompleted": 1
    }
  }
}
```

**Audit Event (Certificate Registration):**
```json
{
  "serviceDomain": "CertificateManagement",
  "serviceOperation": "Notify",
  "serviceAction": "AuditLog",
  "header": {
    "correlationId": "cm-20240115-001",
    "serviceProviderId": "MSB-DSIGN-001",
    "serviceDateTime": "2024-01-15T09:00:01+07:00",
    "eventType": "AUDIT"
  },
  "payload": {
    "certificateAuditEvent": {
      "correlationId": "cm-20240115-001",
      "operationType": "CERTIFICATE_REGISTRATION",
      "certificateReference": "CERT-REF-2024-0042",
      "actionPerformed": "REGISTER",
      "actorReference": "ADMIN-SYSTEM",
      "operationTimestamp": "2024-01-15T09:00:01+07:00",
      "operationResult": "SUCCESS",
      "certificateSubject": "CN=Nguyen Van A, O=MSB, C=VN",
      "certificateAuthorityReference": "VNCA"
    }
  }
}
```
