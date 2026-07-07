# MSB Enterprise Integration Platform

Enterprise-grade integration platform built on Spring Boot 3.2 / Java 21.
Plugin-based architecture supporting multiple protocols with resilient, configurable message flows.

---

## Architecture Overview

```
                    ┌─────────────────────────────────────────────────┐
                    │              API GATEWAY (gateway)               │
                    │  Request Reception → Validation → Rate Limiting  │
                    └──────────────────────┬──────────────────────────┘
                                           │
                    ┌──────────────────────▼──────────────────────────┐
                    │            SECURITY (security)                   │
                    │      API Key Auth → Authorization                │
                    └──────────────────────┬──────────────────────────┘
                                           │
                    ┌──────────────────────▼──────────────────────────┐
                    │          FLOW ENGINE (core)                      │
                    │   Idempotent → Chain of Responsibility           │
                    │   Plugin Registry → Flow Orchestration           │
                    └───┬──────────┬──────────┬──────────┬───────────┘
                        │          │          │          │
              ┌─────────▼──┐ ┌────▼─────┐ ┌──▼───────┐ ┌▼──────────┐
              │ VALIDATION │ │TRANSFORM │ │ ROUTING  │ │  ADAPTER  │
              │            │ │          │ │          │ │           │
              │ Schema     │ │ JSON↔XML │ │ Content  │ │ REST      │
              │ Jakarta    │ │ JSON↔CSV │ │ Header   │ │ SOAP      │
              │ Custom     │ │ ISO8583  │ │ Rule     │ │ Kafka     │
              └────────────┘ └──────────┘ │ Dynamic  │ │ RabbitMQ  │
                                          └──────────┘ │ IBM MQ    │
                                                       │ File      │
                                                       │ SFTP      │
                                                       └─────┬─────┘
                                                             │
                                          ┌──────────────────▼──────────────────┐
                                          │       EXTERNAL SYSTEMS              │
                                          │  Core Banking │ Payment │ NAPAS     │
                                          │  Notification │ Reports │ Partners  │
                                          └─────────────────────────────────────┘

    ┌──────────────────┐  ┌──────────────────┐  ┌────────────────────┐
    │  CONFIG (config) │  │ SCHEDULER (sched)│  │ MONITORING (mon)   │
    │                  │  │                  │  │                    │
    │ Hot Reload       │  │ Retry Jobs       │  │ Prometheus Metrics │
    │ Redis-backed     │  │ Health Checks    │  │ Distributed Trace  │
    │ Version Control  │  │ Cron Flows       │  │ Audit Logging      │
    └──────────────────┘  └──────────────────┘  └────────────────────┘
```

---

## Project Structure

```
integration-platform/
├── pom.xml                          # Parent POM (module aggregator)
│
├── integration-common/              # Shared models, exceptions, utilities
│   └── src/main/java/.../common/
│       ├── model/                   # IntegrationMessage, IntegrationResponse
│       ├── enums/                   # ProtocolType, ContentType
│       └── exception/               # IntegrationException hierarchy
│
├── integration-core/                # Engine: flow orchestration, plugins
│   └── src/main/java/.../core/
│       ├── flow/                    # FlowEngine, FlowStep, FlowContext
│       ├── adapter/                 # IntegrationAdapter interface
│       ├── transformer/             # MessageTransformer interface
│       ├── plugin/                  # PluginRegistry
│       └── idempotent/              # IdempotentStore (Redis)
│
├── integration-transformer/         # Data transformation strategies
│   └── src/main/java/.../transformer/
│       ├── JsonToXmlTransformer
│       ├── XmlToJsonTransformer
│       └── config/TransformerConfig
│
├── integration-router/              # Message routing logic
│   └── src/main/java/.../router/
│       ├── MessageRouter            # Router interface
│       ├── ContentBasedRouter       # Content/header-based routing
│       ├── RouteDefinition          # Route model
│       └── RoutingConfig            # Config model
│
├── integration-adapter/             # Protocol adapter implementations
│   └── src/main/java/.../adapter/
│       ├── rest/RestAdapter
│       ├── kafka/KafkaAdapter
│       ├── file/FileAdapter
│       └── config/AdapterConfig
│
├── integration-gateway/             # API entry point
│   └── src/main/java/.../gateway/
│       ├── IntegrationGatewayController
│       ├── GatewayService
│       ├── GlobalExceptionHandler
│       └── dto/IntegrationRequest
│
├── integration-security/            # Auth & authorization
│   └── src/main/java/.../security/
│       ├── SecurityConfig
│       └── ApiKeyAuthFilter
│
├── integration-config/              # Hot configuration
│   └── src/main/java/.../config/
│       └── HotConfigService
│
├── integration-scheduler/           # Job scheduling
│   └── src/main/java/.../scheduler/
│       └── RetryScheduler
│
├── integration-monitoring/          # Observability
│   └── src/main/java/.../monitoring/
│       ├── AuditLogService
│       └── MetricsService
│
└── integration-app/                 # Boot application (assembly)
    └── src/main/
        ├── java/.../IntegrationPlatformApplication.java
        └── resources/
            ├── application.yml      # Main config
            ├── routing-config.yml   # Route definitions
            └── flow-config.yml      # Flow definitions
```

---

## Module Explanation

| Module | Purpose | Why It Exists |
|--------|---------|---------------|
| **common** | Shared models, enums, exceptions | Single source of truth for data contracts. All modules depend on this — prevents circular dependencies and duplication. |
| **core** | Flow engine, plugin registry, interfaces | The brain of the platform. Defines contracts (IntegrationAdapter, MessageTransformer) that all plugins implement. Contains the Chain of Responsibility engine. |
| **transformer** | Data format conversion | Messages between systems use different formats (JSON, XML, ISO8583). Isolating transformers allows adding new formats without touching core logic. Strategy pattern. |
| **router** | Message routing decisions | Decouples "what to do" from "where to send". Rules can be changed at runtime. Content-based, header-based, or rule-engine routing. |
| **adapter** | Protocol implementations | Each external system speaks a different language. Adapter pattern wraps protocol specifics behind a unified interface. Adding a new protocol = adding one class. |
| **gateway** | API entry point | Single point of entry for all requests. Handles validation, rate limiting, correlation ID assignment. Thin controller layer. |
| **security** | Authentication & authorization | API key management, JWT validation, encryption. Isolated so security policies can evolve independently. |
| **config** | Hot configuration | Runtime changes without restart. Redis-backed with local caching. Crucial for production where downtime = money. |
| **scheduler** | Retry & batch jobs | Failed messages need retry. Reports need scheduling. Health checks need periodic execution. Isolated for testability. |
| **monitoring** | Observability | Metrics (Prometheus), distributed tracing, audit logs. Required for production operations and compliance. |
| **app** | Assembly & boot | Pulls all modules together. Contains Spring Boot main class and environment-specific config. Single deployable artifact. |

---

## Design Patterns Used

| Pattern | Where | Purpose |
|---------|-------|---------|
| **Plugin Architecture** | PluginRegistry | Components self-register. New adapters/transformers discovered automatically via Spring DI. |
| **Adapter Pattern** | IntegrationAdapter | Unified interface for all protocols. REST, Kafka, SFTP all look the same to the engine. |
| **Strategy Pattern** | MessageTransformer, MessageRouter | Runtime selection of transformation/routing algorithm based on config. |
| **Factory Pattern** | AdapterFactory (implicit via Spring) | Spring creates adapter instances based on config. |
| **Chain of Responsibility** | FlowEngine + FlowStep | Ordered steps process message sequentially. Each step can modify, enrich, or abort. |
| **Template Method** | IntegrationAdapter interface defaults | Base behavior with extension points. |

---

## Resilience Patterns

| Pattern | Implementation | Purpose |
|---------|---------------|---------|
| **Retry** | Resilience4j + exponential backoff | Transient failures recover automatically |
| **Circuit Breaker** | Resilience4j per adapter | Prevents cascade failures when backend is down |
| **Timeout** | TimeLimiter per adapter call | Prevents thread exhaustion from slow backends |
| **Idempotent** | Redis SET NX with TTL | Exactly-once processing guarantee |
| **Rate Limiting** | Resilience4j at gateway | Protects platform from traffic spikes |
| **Dead Letter Queue** | Failed messages → DLQ | No data loss on processing failures |
| **Bulkhead** | Thread isolation per adapter | One slow adapter doesn't block others |

---

## Sequence Diagram: Bank Transfer Flow

```
┌──────┐     ┌─────────┐     ┌────────┐     ┌───────────┐     ┌────────┐     ┌──────────┐     ┌──────────────┐
│Client│     │ Gateway │     │Security│     │FlowEngine │     │Transform│     │  Router  │     │ REST Adapter │
└──┬───┘     └────┬────┘     └───┬────┘     └─────┬─────┘     └────┬────┘     └────┬─────┘     └──────┬───────┘
   │               │              │                │                │               │                  │
   │ POST /execute │              │                │                │               │                  │
   │──────────────>│              │                │                │               │                  │
   │               │              │                │                │               │                  │
   │               │ Authenticate │                │                │               │                  │
   │               │─────────────>│                │                │               │                  │
   │               │   API Key OK │                │                │               │                  │
   │               │<─────────────│                │                │               │                  │
   │               │              │                │                │               │                  │
   │               │         Execute Flow          │                │               │                  │
   │               │──────────────────────────────>│                │               │                  │
   │               │              │                │                │               │                  │
   │               │              │                │ ①Idempotent    │               │                  │
   │               │              │                │   Check(Redis) │               │                  │
   │               │              │                │────────┐       │               │                  │
   │               │              │                │        │       │               │                  │
   │               │              │                │<───────┘       │               │                  │
   │               │              │                │                │               │                  │
   │               │              │                │ ②Validate      │               │                  │
   │               │              │                │────────┐       │               │                  │
   │               │              │                │        │       │               │                  │
   │               │              │                │<───────┘       │               │                  │
   │               │              │                │                │               │                  │
   │               │              │                │ ③Transform     │               │                  │
   │               │              │                │───────────────>│               │                  │
   │               │              │                │  JSON→ISO8583  │               │                  │
   │               │              │                │<───────────────│               │                  │
   │               │              │                │                │               │                  │
   │               │              │                │ ④Resolve Route │               │                  │
   │               │              │                │───────────────────────────────>│                  │
   │               │              │                │     RouteDefinition            │                  │
   │               │              │                │<──────────────────────────────│                  │
   │               │              │                │                │               │                  │
   │               │              │                │ ⑤Dispatch                      │                  │
   │               │              │                │──────────────────────────────────────────────────>│
   │               │              │                │                │               │   HTTP POST      │
   │               │              │                │                │               │   Backend        │
   │               │              │                │                │               │                  │
   │               │              │                │   Response     │               │                  │
   │               │              │                │<──────────────────────────────────────────────────│
   │               │              │                │                │               │                  │
   │               │              │                │ ⑥Mark Complete │               │                  │
   │               │              │                │   (Redis)      │               │                  │
   │               │              │                │────────┐       │               │                  │
   │               │              │                │        │       │               │                  │
   │               │              │                │<───────┘       │               │                  │
   │               │              │                │                │               │                  │
   │               │   Result     │                │                │               │                  │
   │               │<─────────────────────────────│                │               │                  │
   │               │              │                │                │               │                  │
   │  200 OK       │              │                │                │               │                  │
   │<──────────────│              │                │                │               │                  │
   │               │              │                │                │               │                  │
```

---

## Sample API Usage

### Execute Integration Flow

```bash
curl -X POST http://localhost:8081/api/v1/integration/execute \
  -H "Content-Type: application/json" \
  -H "X-API-Key: integration-client-key" \
  -H "X-Correlation-ID: txn-20240101-001" \
  -d '{
    "flowId": "bank-transfer",
    "source": "mobile-app",
    "target": "core-banking-transfer",
    "contentType": "JSON",
    "payload": {
      "fromAccount": "0001234567890",
      "toAccount": "9876543210001",
      "amount": 500000,
      "currency": "VND",
      "description": "Transfer to savings"
    },
    "headers": {
      "X-Channel": "MOBILE",
      "X-Branch-Code": "HN01"
    }
  }'
```

### Response

```json
{
  "correlationId": "txn-20240101-001",
  "success": true,
  "code": "200",
  "message": "Success",
  "data": {
    "transactionId": "TXN20240101001",
    "status": "COMPLETED",
    "responseCode": "00"
  },
  "timestamp": "2024-01-01T10:30:00.123Z"
}
```

### List Available Flows

```bash
curl http://localhost:8081/api/v1/integration/flows \
  -H "X-API-Key: integration-admin-key"
```

---

## Build & Run

```bash
# Build all modules
cd integration-platform
mvn clean package -DskipTests

# Run
mvn spring-boot:run -pl integration-app

# Run with Docker infra
docker-compose up -d   # from parent project for Redis + Kafka
mvn spring-boot:run -pl integration-app
```

---

## Hot Configuration

Configuration changes via Redis without restart:

```bash
# Update route endpoint at runtime
redis-cli SET "integration:config:route.core-banking-transfer.endpoint" "http://new-core-banking/api/v2/transfers"

# Update timeout
redis-cli SET "integration:config:route.core-banking-transfer.timeout-ms" "20000"

# Disable a flow
redis-cli SET "integration:config:flow.bank-transfer.enabled" "false"
```

---

## Adding a New Adapter

1. Create a class implementing `IntegrationAdapter`
2. Add `@Component` annotation
3. Done — the `PluginRegistry` auto-discovers it

```java
@Component
public class MyCustomAdapter implements IntegrationAdapter {
    @Override public String getName() { return "my-adapter"; }
    @Override public ProtocolType getProtocol() { return ProtocolType.REST; }
    @Override public IntegrationMessage send(IntegrationMessage msg) { /* ... */ }
    @Override public boolean supports(IntegrationMessage msg) { /* ... */ }
    @Override public boolean isHealthy() { return true; }
}
```

---

## Key Technologies

| Concern | Technology |
|---------|-----------|
| Language | Java 21 (virtual threads) |
| Framework | Spring Boot 3.2.5 (Undertow) |
| Cache/State | Redis |
| Messaging | Kafka, RabbitMQ |
| Resilience | Resilience4j |
| Metrics | Micrometer + Prometheus |
| Mapping | MapStruct |
| Security | Spring Security (API Key) |
| Docs | SpringDoc OpenAPI |
| Build | Maven Multi-Module |
