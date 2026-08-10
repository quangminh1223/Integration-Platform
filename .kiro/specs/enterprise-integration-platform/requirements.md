# Requirements Document

## Introduction

The Enterprise Integration Platform (EIP) is a Spring Boot based integration backbone for MSB that receives requests over multiple protocols, validates and transforms them, executes configurable business flows, and routes them to external systems through pluggable protocol adapters. The platform processes every message through a fixed pipeline:

```
API → Validation → Transformation → Business Flow → Routing → Adapter → External Systems
```

The platform is organized into ten modules (gateway, common, core, adapter, router, transformer, security, config, scheduler, monitoring) and supports seven external protocols (REST, SOAP, Kafka, RabbitMQ, IBM MQ, File, SFTP). It applies enterprise integration patterns (plugin architecture, adapter, strategy, factory, chain of responsibility) and cross-cutting reliability concerns (retry, circuit breaker, timeout, idempotency, correlation ID, audit log, distributed tracing, health check, metrics).

The platform builds on existing MSB QR Service capabilities and conventions: Java 21 virtual threads, Spring Boot 3.2.5, Undertow, Resilience4j (already used in `ResilientBackendCaller`), Redis-backed hot configuration (`AbstractTableCache` / `SystemCacheStore`), runtime route registration (`DynamicRouteRegistry`), and async audit logging (`TransactionLogService`). All code lives under the base package `msb.com.vn.qrservice` and targets ~100 TPS on OpenShift.

## Glossary

- **EIP / Platform**: The Enterprise Integration Platform — the system being specified.
- **Gateway**: The module that terminates inbound protocol connections and admits messages into the pipeline.
- **Common_Module**: The module providing shared types, the common adapter interface, envelopes, enums, and exceptions.
- **Core_Module**: The module that orchestrates the processing pipeline and owns the chain of responsibility.
- **Adapter_Module**: The module containing protocol adapter implementations and the adapter factory.
- **Router_Module**: The module that selects the destination adapter and operation for a message.
- **Transformer_Module**: The module that transforms messages between source and target representations.
- **Security_Module**: The module that authenticates, authorizes, encrypts, and signs messages.
- **Config_Module**: The module that loads, validates, and hot-reloads platform configuration.
- **Scheduler_Module**: The module that executes time-triggered and polling-based integration jobs.
- **Monitoring_Module**: The module that exposes health, metrics, and tracing telemetry.
- **Pipeline**: The ordered sequence of processing stages: Validation, Transformation, Business Flow, Routing, Adapter dispatch.
- **Protocol_Adapter**: A plugin that connects the platform to one external protocol (REST, SOAP, Kafka, RabbitMQ, IBM MQ, File, SFTP).
- **Adapter_Interface**: The common Java interface that every Protocol_Adapter implements.
- **Message_Envelope**: The internal canonical representation carrying payload, headers, correlation ID, and routing metadata through the Pipeline.
- **Flow**: A named, configuration-defined sequence of pipeline steps applied to a class of messages.
- **Flow_Definition**: The externalized configuration describing a Flow's stages, transformations, routing target, and resilience settings.
- **Route**: A configuration entry mapping an inbound message to a target adapter and operation.
- **Correlation_ID**: A unique identifier assigned per inbound message and propagated through all stages, adapters, logs, and traces.
- **Idempotency_Key**: A client-supplied or derived key used to detect and suppress duplicate processing of the same logical request.
- **Audit_Log**: A durable record of message processing events written asynchronously for compliance and troubleshooting.
- **Distributed_Trace**: A linked set of spans across pipeline stages and external calls, correlated by Correlation_ID and trace context.
- **Circuit_Breaker**: A Resilience4j component that stops calls to a failing external system.
- **Hot_Configuration**: Configuration that the Config_Module applies at runtime without restarting the Platform.
- **Plugin**: A self-contained, independently registrable component (adapter, transformer, or flow step) discovered at runtime.
- **External_System**: A downstream or upstream system reached through a Protocol_Adapter.
- **Operator**: A human administrator who configures, deploys, and monitors the Platform.
- **Client**: An external caller or system that sends a request into the Platform.

## Requirements

### Requirement 1: Multi-Protocol Inbound Gateway

**User Story:** As an integration engineer, I want the platform to accept requests over multiple protocols through a single gateway, so that diverse client systems can connect without protocol-specific application code.

#### Acceptance Criteria

1. WHEN a Client sends a request over REST, SOAP, Kafka, RabbitMQ, IBM MQ, File, or SFTP and an enabled inbound adapter exists for that protocol, THE Gateway SHALL accept the request and construct a Message_Envelope from the request payload and metadata.
2. WHEN the Gateway constructs a Message_Envelope, THE Gateway SHALL record exactly one source protocol identifier, drawn from the set {REST, SOAP, Kafka, RabbitMQ, IBM MQ, File, SFTP}, in the Message_Envelope.
3. IF a request arrives over a protocol that has no enabled inbound adapter, THEN THE Gateway SHALL reject the request without constructing a Message_Envelope and return a protocol-appropriate error indicating the protocol is not supported.
4. IF the request payload cannot be read or parsed into a Message_Envelope, THEN THE Gateway SHALL reject the request and return a protocol-appropriate error indicating the request is malformed.
5. WHEN the Gateway admits a Message_Envelope, THE Gateway SHALL submit the Message_Envelope to the Core_Module Pipeline.
6. THE Gateway SHALL assign one Correlation_ID, unique across all admitted Message_Envelopes, to each admitted Message_Envelope before submitting it to the Pipeline.

### Requirement 2: Common Adapter Interface and Plugin Registration

**User Story:** As a platform developer, I want every adapter to implement one common interface and register as a plugin, so that new protocols can be added without modifying core code.

#### Acceptance Criteria

1. THE Common_Module SHALL define one Adapter_Interface that declares the operations required to send and receive a Message_Envelope.
2. THE Adapter_Module SHALL provide one Protocol_Adapter implementing the Adapter_Interface for each of the following seven protocols: REST, SOAP, Kafka, RabbitMQ, IBM MQ, File, and SFTP.
3. WHEN the Platform starts, THE Adapter_Module SHALL discover and register every available Protocol_Adapter that implements the Adapter_Interface before the Platform accepts routing requests.
4. WHERE a Plugin that implements the Adapter_Interface declares a non-empty protocol identifier that is not already registered, THE Adapter_Module SHALL register the Plugin without requiring changes to the Core_Module.
5. IF a Protocol_Adapter declares an empty or absent protocol identifier, THEN THE Adapter_Module SHALL reject the registration and record an error indicating the protocol identifier is missing.
6. IF two registered Protocol_Adapters declare the same protocol identifier, THEN THE Adapter_Module SHALL retain the first registered Protocol_Adapter, reject the later registration, and record an error identifying the conflicting protocol identifier.
7. WHEN the Router_Module requests an adapter by a registered protocol identifier, THE Adapter_Module SHALL return the registered Protocol_Adapter for that identifier through a factory.
8. IF the Router_Module requests an adapter by a protocol identifier that is not registered, THEN THE Adapter_Module SHALL return an error indicating no adapter is registered for that protocol identifier.

### Requirement 3: Pipeline Orchestration via Chain of Responsibility

**User Story:** As an integration engineer, I want each message processed through an ordered chain of pipeline stages, so that processing is predictable, observable, and extensible.

#### Acceptance Criteria

1. WHEN the Core_Module receives a Message_Envelope, THE Core_Module SHALL process it through the stages in the order Validation, Transformation, Business Flow, Routing, Adapter dispatch, without skipping or reordering the mandatory stages.
2. WHEN a pipeline stage completes successfully, THE Core_Module SHALL pass the stage output Message_Envelope to the next stage in the chain.
3. IF a pipeline stage returns a failure, THEN THE Core_Module SHALL stop the Pipeline, discard the partial results of any subsequent stages, and return an error response that includes the Correlation_ID and the failing stage name.
4. THE Core_Module SHALL keep the Correlation_ID value unchanged in the Message_Envelope at the entry and exit of every stage.
5. IF a Message_Envelope reaches the Core_Module with a missing or empty Correlation_ID, THEN THE Core_Module SHALL reject the Message_Envelope before the Validation stage and return an error indicating the Correlation_ID is missing.
6. WHERE a Flow_Definition adds an optional pipeline step, THE Core_Module SHALL execute that step at the position declared in the Flow_Definition relative to the mandatory stages.

### Requirement 4: Request Validation

**User Story:** As an integration engineer, I want inbound messages validated before processing, so that malformed or unauthorized messages are rejected early.

#### Acceptance Criteria

1. WHEN the Validation stage receives a Message_Envelope, THE Core_Module SHALL validate the payload against every validation rule declared in the applicable Flow_Definition.
2. IF a Message_Envelope fails validation, THEN THE Core_Module SHALL stop the Pipeline, leave the Message_Envelope unchanged without passing it to the Transformation stage, and return a validation error that lists each violated rule.
3. WHEN validation succeeds, THE Core_Module SHALL pass the Message_Envelope to the Transformation stage with its payload byte-for-byte unchanged.
4. WHERE a Flow_Definition declares no validation rules, THE Core_Module SHALL treat the Validation stage as passed and pass the Message_Envelope to the Transformation stage with its payload byte-for-byte unchanged.
5. IF the payload of a received Message_Envelope cannot be parsed and therefore the validation rules cannot be evaluated, THEN THE Core_Module SHALL stop the Pipeline, leave the Message_Envelope unchanged, and return a validation error indicating that the payload is malformed.

### Requirement 5: Message Transformation (Strategy Pattern)

**User Story:** As an integration engineer, I want configurable transformation between source and target message formats, so that systems with different data contracts can interoperate.

#### Acceptance Criteria

1. WHEN the Transformation stage receives a Message_Envelope, THE Transformer_Module SHALL apply the transformation strategy named in the applicable Flow_Definition.
2. THE Transformer_Module SHALL select the transformation strategy at runtime based on a case-sensitive exact match of the strategy name declared in the Flow_Definition.
3. IF the Flow_Definition names a transformation strategy that is not registered, THEN THE Transformer_Module SHALL stop the Pipeline, leave the Message_Envelope payload unchanged, and return an error identifying the missing strategy name.
4. IF a registered transformation strategy fails to execute on the payload, THEN THE Transformer_Module SHALL stop the Pipeline, leave the Message_Envelope payload unchanged, and return an error identifying the strategy name and the failure cause.
5. WHERE a Flow_Definition declares no transformation, THE Transformer_Module SHALL pass the Message_Envelope to the Routing stage with its payload bytes identical to the input payload bytes.
6. FOR ALL transformation strategies that declare an inverse, applying the strategy and then its inverse to a payload SHALL produce a payload byte-for-byte identical to the original input payload (round-trip property).

### Requirement 6: Configurable Business Flows

**User Story:** As an Operator, I want business flows defined entirely in configuration, so that I can change integration behavior without code changes or redeployment.

#### Acceptance Criteria

1. THE Config_Module SHALL load every Flow_Definition from externalized configuration rather than from compiled code.
2. WHEN the Business Flow stage receives a Message_Envelope, THE Core_Module SHALL execute the Flow whose selection criteria match the Message_Envelope.
3. IF no Flow_Definition matches a Message_Envelope, THEN THE Core_Module SHALL stop the Pipeline and return an error indicating no matching Flow was found.
4. IF more than one Flow_Definition matches a Message_Envelope, THEN THE Core_Module SHALL select the Flow_Definition with the highest declared priority value.
5. THE Core_Module SHALL execute Flow steps in the order declared in the Flow_Definition.

### Requirement 7: Message Routing

**User Story:** As an integration engineer, I want messages routed to the correct external system based on configuration, so that destinations can change without modifying application logic.

#### Acceptance Criteria

1. WHEN the Routing stage receives a Message_Envelope, THE Router_Module SHALL select the target adapter and operation defined by the Route that matches the Message_Envelope.
2. THE Router_Module SHALL resolve target adapters by protocol identifier through the Adapter_Module factory.
3. IF no Route matches a Message_Envelope, THEN THE Router_Module SHALL stop the Pipeline and return an error indicating no matching Route was found.
4. WHEN a Route is matched, THE Router_Module SHALL record the selected protocol identifier and operation in the Message_Envelope.
5. WHERE a Route declares a fallback target, THE Router_Module SHALL select the fallback target when the primary target adapter is unavailable.

### Requirement 8: Adapter Dispatch to External Systems

**User Story:** As an integration engineer, I want the platform to dispatch messages to external systems through the selected adapter, so that downstream systems receive correctly formatted requests.

#### Acceptance Criteria

1. WHEN the Adapter dispatch stage receives a routed Message_Envelope, THE Adapter_Module SHALL invoke the selected Protocol_Adapter through the Adapter_Interface.
2. WHEN a Protocol_Adapter receives a response from an External_System, THE Adapter_Module SHALL place the response payload into the Message_Envelope.
3. IF a Protocol_Adapter cannot reach its External_System, THEN THE Adapter_Module SHALL return an error that includes the Correlation_ID and the target protocol identifier.
4. THE Adapter_Module SHALL propagate the Correlation_ID to the External_System in the protocol's metadata when the protocol supports metadata.

### Requirement 9: Resilient External Calls (Retry, Circuit Breaker, Timeout)

**User Story:** As an Operator, I want external calls protected by retry, circuit breaker, and timeout, so that failures in one external system do not cascade across the platform.

#### Acceptance Criteria

1. WHEN a Protocol_Adapter calls an External_System, THE Adapter_Module SHALL apply the timeout configured for that Route.
2. IF a call to an External_System fails with a retryable error, THEN THE Adapter_Module SHALL retry the call up to the retry count configured for that Route.
3. WHILE the Circuit_Breaker for an External_System is open, THE Adapter_Module SHALL reject calls to that External_System and return a service-unavailable error without invoking the External_System.
4. WHEN the failure rate for an External_System exceeds the configured threshold, THE Adapter_Module SHALL open the Circuit_Breaker for that External_System.
5. IF a call to an External_System exceeds the configured timeout, THEN THE Adapter_Module SHALL cancel the call and return a timeout error that includes the Correlation_ID.
6. THE Config_Module SHALL load retry count, circuit breaker threshold, and timeout values from externalized configuration for each Route.

### Requirement 10: Idempotent Request Processing

**User Story:** As an integration engineer, I want duplicate requests detected and suppressed, so that retried or replayed messages do not cause duplicate side effects in external systems.

#### Acceptance Criteria

1. WHEN a Message_Envelope carries an Idempotency_Key that has not been seen within the configured retention window, THE Core_Module SHALL process the Message_Envelope normally and store the Idempotency_Key with its result.
2. WHEN a Message_Envelope carries an Idempotency_Key that matches a completed request within the configured retention window, THE Core_Module SHALL return the stored result without re-dispatching to the External_System.
3. WHERE a Flow_Definition marks a Flow as idempotent and the Message_Envelope carries no Idempotency_Key, THE Core_Module SHALL derive an Idempotency_Key from the configured payload fields.
4. THE Config_Module SHALL load the idempotency retention window from externalized configuration.

### Requirement 11: Correlation ID Propagation

**User Story:** As an Operator, I want a correlation ID assigned and propagated end to end, so that I can trace a single request across all stages, logs, and external calls.

#### Acceptance Criteria

1. WHEN a Client request includes a correlation identifier in protocol metadata, THE Gateway SHALL use that identifier as the Correlation_ID for the Message_Envelope.
2. WHEN a Client request includes no correlation identifier, THE Gateway SHALL generate a unique Correlation_ID for the Message_Envelope.
3. THE Core_Module SHALL include the Correlation_ID in every Audit_Log record produced for a Message_Envelope.
4. THE Monitoring_Module SHALL include the Correlation_ID in every Distributed_Trace span produced for a Message_Envelope.
5. WHEN the Platform returns a response to a Client, THE Gateway SHALL include the Correlation_ID in the response metadata.

### Requirement 12: Asynchronous Audit Logging

**User Story:** As a compliance officer, I want every message processing event recorded in an audit log without slowing request processing, so that the platform meets audit requirements at high throughput.

#### Acceptance Criteria

1. WHEN a Message_Envelope enters and exits the Pipeline, THE Monitoring_Module SHALL write an Audit_Log record containing the Correlation_ID, source protocol, matched Flow, target protocol, and outcome status.
2. THE Monitoring_Module SHALL write Audit_Log records asynchronously so that audit writing does not block the processing thread.
3. IF the audit log queue is full, THEN THE Monitoring_Module SHALL route the Audit_Log record to a dead-letter handler and record a dropped-record metric.
4. THE Monitoring_Module SHALL write Audit_Log records using the existing async logging mechanism of the QR Service.

### Requirement 13: Distributed Tracing

**User Story:** As an Operator, I want distributed traces across pipeline stages and external calls, so that I can diagnose latency and failures across system boundaries.

#### Acceptance Criteria

1. WHEN the Core_Module processes a Message_Envelope, THE Monitoring_Module SHALL create a Distributed_Trace span for each pipeline stage.
2. WHEN a Protocol_Adapter calls an External_System, THE Monitoring_Module SHALL create a child Distributed_Trace span for that external call.
3. THE Monitoring_Module SHALL link every Distributed_Trace span for a Message_Envelope to the Message_Envelope Correlation_ID.
4. WHERE trace context metadata is present on an inbound request, THE Gateway SHALL continue the existing Distributed_Trace rather than starting a new trace.

### Requirement 14: Health Checks

**User Story:** As an Operator, I want health checks for the platform and its external dependencies, so that orchestration platforms can manage readiness and liveness.

#### Acceptance Criteria

1. THE Monitoring_Module SHALL expose a liveness health endpoint that reports whether the Platform process is running.
2. THE Monitoring_Module SHALL expose a readiness health endpoint that reports the status of each registered Protocol_Adapter and its External_System connectivity.
3. IF a registered Protocol_Adapter cannot reach its External_System, THEN THE Monitoring_Module SHALL report that adapter as not ready in the readiness health endpoint.
4. WHEN every registered Protocol_Adapter reports healthy, THE Monitoring_Module SHALL report overall readiness as ready.

### Requirement 15: Metrics Exposure

**User Story:** As an Operator, I want platform metrics exposed in Prometheus format, so that I can monitor throughput, latency, and error rates.

#### Acceptance Criteria

1. THE Monitoring_Module SHALL expose a Prometheus-format metrics endpoint.
2. WHEN a Message_Envelope completes the Pipeline, THE Monitoring_Module SHALL record the processing latency and outcome status as metrics labeled by source protocol and matched Flow.
3. WHEN a Protocol_Adapter call completes, THE Monitoring_Module SHALL record the external call latency and outcome as metrics labeled by target protocol.
4. THE Monitoring_Module SHALL record the current state of each Circuit_Breaker as a metric.

### Requirement 16: Hot Configuration Reload

**User Story:** As an Operator, I want to change flows, routes, and resilience settings without restarting the platform, so that I can adapt integrations with zero downtime.

#### Acceptance Criteria

1. WHEN an Operator updates Hot_Configuration, THE Config_Module SHALL apply the updated Flow_Definitions and Routes without restarting the Platform.
2. IF updated Hot_Configuration fails validation, THEN THE Config_Module SHALL reject the update, retain the previously active configuration, and record an error describing the validation failure.
3. WHEN the Config_Module applies updated Hot_Configuration, THE Config_Module SHALL apply the update so that any Message_Envelope already in the Pipeline completes using the configuration active when it entered the Pipeline.
4. THE Config_Module SHALL load all configuration from externalized sources and SHALL NOT require hard-coded flow, route, endpoint, or credential values.

### Requirement 17: Security — Authentication, Authorization, and Cryptography

**User Story:** As a security engineer, I want inbound requests authenticated and authorized and sensitive payloads protected, so that only permitted clients invoke flows and data stays confidential.

#### Acceptance Criteria

1. WHEN a Message_Envelope requires authentication per its Flow_Definition, THE Security_Module SHALL authenticate the Client before the Validation stage.
2. IF authentication fails, THEN THE Security_Module SHALL stop the Pipeline and return an unauthorized error that includes the Correlation_ID.
3. WHEN an authenticated Client lacks authorization for the matched Flow, THE Security_Module SHALL stop the Pipeline and return a forbidden error.
4. WHERE a Flow_Definition declares payload encryption or signing, THE Security_Module SHALL encrypt, decrypt, sign, or verify the payload using the msb-crypto library.
5. THE Security_Module SHALL load credentials and key references from externalized configuration rather than from compiled code.

### Requirement 18: Scheduled and Polling Integrations

**User Story:** As an integration engineer, I want time-triggered and polling-based integrations, so that file pickups and periodic jobs run without an external trigger.

#### Acceptance Criteria

1. WHERE a Flow_Definition declares a schedule, THE Scheduler_Module SHALL trigger the Flow at the configured times.
2. WHEN a polling Protocol_Adapter (File, SFTP, or message queue) detects new input, THE Scheduler_Module SHALL submit a Message_Envelope for that input to the Pipeline.
3. IF a scheduled Flow is already running when its next trigger occurs, THEN THE Scheduler_Module SHALL skip the new trigger and record a skipped-execution metric.
4. THE Scheduler_Module SHALL load schedule definitions from externalized configuration.

### Requirement 19: Sample Artifacts and Documentation

**User Story:** As a new platform developer, I want sample artifacts and architectural documentation, so that I can understand and extend the platform quickly.

#### Acceptance Criteria

1. THE Platform SHALL provide a sample REST API definition that demonstrates an end-to-end Flow through the Pipeline.
2. THE Platform SHALL provide a sample routing configuration that demonstrates mapping an inbound message to an external target.
3. THE Platform SHALL provide a sequence diagram that depicts a message traversing the API → Validation → Transformation → Business Flow → Routing → Adapter → External System path.
4. THE Platform SHALL provide a documented project structure listing each of the ten modules.
5. THE Platform SHALL provide documentation stating the purpose of each of the ten modules.
