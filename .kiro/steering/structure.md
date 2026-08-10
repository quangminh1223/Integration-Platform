---
inclusion: always
---

# Project Structure

This is a Java 21 / Spring Boot 3.2 multi-module Maven project providing QR code generation, parsing, ISO 8583 messaging, and dynamic API routing for banking transfers.

```text
ScanQRCode/
│
├── pom.xml                              # Root Maven POM (Spring Boot 3.2.5, Java 21)
├── docker-compose.yml                   # Local infrastructure (Redis, Oracle, Kafka)
├── README.md
│
├── libs/                                # Internal library modules
│   └── msb-crypto/                      # Encryption/decryption utilities (separate POM)
│       └── src/main/java/msb/com/vn/crypto/
│
├── src/
│   ├── main/
│   │   ├── java/msb/com/vn/qrservice/
│   │   │   ├── QrServiceApplication.java       # Spring Boot entry point
│   │   │   │
│   │   │   ├── config/                  # Spring configuration classes
│   │   │   ├── controller/              # REST controllers (thin, delegate to services)
│   │   │   ├── service/                 # Business logic layer
│   │   │   ├── domain/                  # JPA entities and Spring Data repositories
│   │   │   │   ├── entity/
│   │   │   │   └── repository/
│   │   │   ├── dto/                     # Request/response DTOs
│   │   │   │   ├── request/
│   │   │   │   └── response/
│   │   │   ├── mapping/                 # MapStruct mappers
│   │   │   ├── common/                  # Cross-cutting concerns
│   │   │   │   ├── crypto/
│   │   │   │   ├── enums/
│   │   │   │   ├── exception/
│   │   │   │   ├── http/
│   │   │   │   └── response/
│   │   │   ├── cache/                   # Redis-backed caching layer
│   │   │   ├── kafka/                   # Kafka producers, consumers, events
│   │   │   │   ├── producer/
│   │   │   │   ├── consumer/
│   │   │   │   └── event/
│   │   │   ├── queue/                   # In-memory managed queue + DLQ handling
│   │   │   ├── logging/                 # Structured transaction/exception logging
│   │   │   ├── qrformat/               # VietQR format generation/parsing (ZXing)
│   │   │   │   ├── generator/
│   │   │   │   ├── model/
│   │   │   │   └── util/
│   │   │   └── swagger/                # Dynamic API routing from Swagger definitions
│   │   │       ├── loader/
│   │   │       ├── model/
│   │   │       ├── registry/
│   │   │       └── service/
│   │   │
│   │   └── resources/
│   │       ├── application.yml          # Main configuration
│   │       ├── backend-endpoints.yml    # Backend endpoint registry
│   │       ├── logback-spring.xml       # Logging configuration
│   │       ├── db/migration/            # Database migration scripts
│   │       ├── iso8583/                 # ISO 8583 message format definitions
│   │       └── swagger-definitions/     # Swagger specs for dynamic routing
│   │
│   └── test/
│       ├── java/                        # Unit and integration tests
│       └── resources/
│           └── application.yml          # Test-specific configuration
│
├── docs/                                # Project documentation
├── logs/                                # Runtime logs (gitignored)
└── .kiro/
    ├── specs/                           # Kiro spec documents
    └── steering/                        # Steering documents
```

---

# Package Conventions

- **Controllers** are thin — validate input, delegate to services, return DTOs.
- **Services** contain business logic. One service class per cohesive capability.
- **Domain** holds JPA entities and Spring Data repository interfaces. Entities use Lombok annotations.
- **DTOs** are separated into `request/` and `response/` sub-packages. Use Java records or Lombok `@Data` classes.
- **Mappers** use MapStruct interfaces for entity ↔ DTO conversion.
- **Common** holds cross-cutting utilities: enums, exception classes, standard HTTP response wrappers.
- **Cache** wraps Redis operations. Each logical cache extends `AbstractTableCache`.
- **Kafka** separates producers, consumers, and event models into sub-packages.
- **Queue** provides in-memory managed queues with dead-letter handling and health indicators.

---

# Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| Package | lowercase, domain-grouped | `msb.com.vn.qrservice.service` |
| Class | PascalCase, role suffix | `VietQrTransferService`, `Iso8583Controller` |
| DTO | PascalCase, suffixed `Request`/`Response` | `TransferIso8583Response` |
| Entity | PascalCase, no suffix | `TransactionLog` |
| Repository | PascalCase, suffixed `Repository` | `TransactionLogRepository` |
| Mapper | PascalCase, suffixed `Mapper` | `BackendApiMapper` |
| Config | PascalCase, suffixed `Config` | `RedisConfig`, `KafkaConfig` |
| Constants | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |

---

# Key Technologies

| Concern | Technology |
|---------|-----------|
| Language | Java 21 (virtual threads enabled) |
| Framework | Spring Boot 3.2.5 (Undertow server) |
| Database | Oracle (JPA/Hibernate) |
| Cache | Redis (Spring Data Redis) |
| Messaging | Apache Kafka |
| QR Code | ZXing 3.5.3 |
| ISO 8583 | jPOS 2.1.9 |
| Resilience | Resilience4j (circuit breaker, rate limiter) |
| Mapping | MapStruct 1.5.5 |
| Boilerplate | Lombok |
| API Docs | SpringDoc OpenAPI 2.5 |
| Metrics | Micrometer + Prometheus |
| Build | Maven |
| Container | Docker Compose (local dev) |

---

# Architecture Patterns

- **Layered architecture**: Controller → Service → Repository. No skipping layers.
- **Event-driven messaging**: Use Kafka for async operations (transfer events, audit logs).
- **Cache-aside pattern**: Redis caches DB table lookups; invalidate on write.
- **Dynamic API routing**: Swagger definitions drive runtime route registration and backend call delegation.
- **Resilient HTTP calls**: All outbound HTTP uses Resilience4j circuit breakers and rate limiters.
- **Structured logging**: JSON-formatted logs with correlation IDs via `TransactionLogService`.
- **Dead-letter handling**: Failed queue messages routed to `DeadLetterHandler` for retry or inspection.

---

# Code Style Rules

- Use Lombok `@Slf4j`, `@Data`, `@Builder`, `@RequiredArgsConstructor` to reduce boilerplate.
- Prefer constructor injection (via `@RequiredArgsConstructor`) over field injection.
- All public service methods must have Jakarta Validation on input DTOs.
- Use `Optional` for nullable return values — never return `null` from repository/service methods.
- Exceptions must be domain-specific (extend a base `ServiceException`) and handled by a global `@ControllerAdvice`.
- Keep controller methods under 10 lines; extract logic into services.
- Configuration values come from `application.yml` via `@ConfigurationProperties` or `@Value`.

---

# Testing Conventions

- Tests live under `src/test/java` mirroring the main source package structure.
- Use `@SpringBootTest` for integration tests, plain JUnit 5 + Mockito for unit tests.
- Test config in `src/test/resources/application.yml` (overrides main config).
- Name test classes with `Test` suffix: `VietQrTransferServiceTest`.
- Run tests via: `mvn test`

---

# Build & Run

- Build: `mvn clean package -DskipTests`
- Run locally: `mvn spring-boot:run` (requires Docker Compose for Redis, Oracle, Kafka)
- Run tests: `mvn test`
- Local infra: `docker-compose up -d`

---
