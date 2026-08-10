# Hướng dẫn gọi API Backend qua Swagger

## Tổng quan

Project sử dụng `SwaggerBackendCaller` để gọi API backend. Chỉ cần:
1. Bỏ file swagger vào thư mục `src/main/resources/swagger-definitions/`
2. Inject `SwaggerBackendCaller` vào service
3. Gọi bằng `operationId` — không cần viết URL, method thủ công

---

## Bước 1: Thêm file swagger

Bỏ file swagger (.json / .yaml) vào:
```
src/main/resources/swagger-definitions/
├── updateMsbCustomer-v1.0.0-swagger.json    ← file hiện có
├── payment-service-v2.json                   ← thêm mới
└── account-service.yaml                      ← thêm mới
```

> **Lưu ý:** Tên file = swaggerId, dùng để quản lý sau này.

---

## Bước 2: Inject SwaggerBackendCaller

```java
import msb.com.vn.qrservice.swagger.service.SwaggerBackendCaller;

@Service
@RequiredArgsConstructor
public class MyService {

    private final SwaggerBackendCaller backendCaller;
}
```

---

## Bước 3: Gọi API

### 3.1 — Gọi đơn giản nhất (chỉ cần operationId + body)

```java
// operationId lấy từ file swagger → field "operationId" trong mỗi endpoint
RestResponse<JsonNode> response = backendCaller.call(
    "updateMsbCustomer",          // operationId
    Map.of("id", "123"),          // path params (thay {id} trong URL)
    requestBody,                  // body (Map, POJO, hoặc JsonNode)
    null                          // headers (null = mặc định)
);

// Kiểm tra kết quả
if (response.isSuccess()) {
    JsonNode data = response.getBody();
    // xử lý data...
} else {
    log.error("Lỗi: {}", response.getErrorMessage());
}
```

### 3.2 — Gọi với custom timeout

```java
RestResponse<JsonNode> response = backendCaller.call(
    "updateMsbCustomer",
    Map.of("id", "123"),
    requestBody,
    null,
    5000,     // connectTimeoutMs (5 giây)
    60000     // readTimeoutMs (60 giây)
);
```

### 3.3 — Gọi với response type cụ thể (tự map sang DTO)

```java
RestResponse<CustomerDto> response = backendCaller.call(
    "updateMsbCustomer",
    Map.of("id", "123"),
    requestBody,
    null,
    CustomerDto.class             // tự deserialize response sang DTO
);

CustomerDto customer = response.getBody();
```

### 3.4 — Gọi với headers bổ sung

```java
RestResponse<JsonNode> response = backendCaller.call(
    "updateMsbCustomer",
    Map.of("id", "123"),
    requestBody,
    Map.of(
        "Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9...",
        "X-Request-Id", UUID.randomUUID().toString(),
        "X-Channel", "MOBILE"
    )
);
```

### 3.5 — Gọi API không có path params (POST đơn giản)

```java
RestResponse<JsonNode> response = backendCaller.call(
    "createPayment",              // operationId
    Map.of(),                     // không có path params
    paymentRequest,               // body
    null
);
```

### 3.6 — Gọi API GET (không có body)

```java
RestResponse<JsonNode> response = backendCaller.call(
    "getCustomerById",
    Map.of("id", "456"),
    null,                         // GET không cần body
    null
);
```

### 3.7 — Gọi bằng method + path (thay vì operationId)

```java
RestResponse<JsonNode> response = backendCaller.callByPath(
    HttpMethod.PUT,
    "/product/msb/customer/maintenance/amend/{id}",
    Map.of("id", "789"),
    requestBody,
    null
);
```

---

## Bước 4: Xem danh sách operations có sẵn

```java
Map<String, String> ops = backendCaller.listAvailableOperations();
// Kết quả:
// {
//   "updateMsbCustomer": "PUT http://api.server.com/api/v1.0.0/product/msb/customer/maintenance/amend/{id}",
//   "createPayment": "POST http://payment.server.com/api/v1/payments",
//   ...
// }
```

---

## Template code — Copy & Paste

### Template 1: Service gọi 1 API backend

```java
package msb.com.vn.qrservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.common.http.RestResponse;
import msb.com.vn.qrservice.swagger.service.SwaggerBackendCaller;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerBackendService {

    private final SwaggerBackendCaller backendCaller;

    /**
     * Gọi API updateMsbCustomer trên backend.
     */
    public JsonNode updateCustomer(String customerId, Object payload) {
        RestResponse<JsonNode> response = backendCaller.call(
            "updateMsbCustomer",
            Map.of("id", customerId),
            payload,
            null
        );

        if (!response.isSuccess()) {
            throw new RuntimeException("Backend lỗi: " + response.getErrorMessage());
        }

        return response.getBody();
    }
}
```

### Template 2: Service gọi nhiều API backend

```java
package msb.com.vn.qrservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.common.http.RestResponse;
import msb.com.vn.qrservice.swagger.service.SwaggerBackendCaller;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final SwaggerBackendCaller backendCaller;

    public JsonNode createPayment(Object payload) {
        return callBackend("createPayment", Map.of(), payload);
    }

    public JsonNode getPayment(String paymentId) {
        return callBackend("getPaymentById", Map.of("id", paymentId), null);
    }

    public JsonNode cancelPayment(String paymentId) {
        return callBackend("cancelPayment", Map.of("id", paymentId), null);
    }

    // ── Helper chung ──────────────────────────────────────────────────────
    private JsonNode callBackend(String operationId, Map<String, Object> pathParams, Object body) {
        RestResponse<JsonNode> response = backendCaller.call(operationId, pathParams, body, null);

        if (!response.isSuccess()) {
            log.error("Backend [{}] lỗi: {}", operationId, response.getErrorMessage());
            throw new RuntimeException("Lỗi gọi " + operationId + ": " + response.getErrorMessage());
        }

        log.info("Backend [{}] thành công", operationId);
        return response.getBody();
    }
}
```

### Template 3: Controller expose API → gọi backend

```java
package msb.com.vn.qrservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import msb.com.vn.qrservice.common.http.RestResponse;
import msb.com.vn.qrservice.common.response.ApiResponse;
import msb.com.vn.qrservice.swagger.service.SwaggerBackendCaller;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final SwaggerBackendCaller backendCaller;

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<JsonNode>> updateCustomer(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {

        RestResponse<JsonNode> response = backendCaller.call(
            "updateMsbCustomer",
            Map.of("id", id),
            body,
            null
        );

        if (response.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success("Cập nhật thành công", response.getBody()));
        }
        return ResponseEntity.status(response.getStatusCode())
                .body(ApiResponse.error("BACKEND_ERROR", response.getErrorMessage()));
    }
}
```

---

## Cách thêm API backend mới (checklist)

| # | Việc cần làm | Chi tiết |
|---|---|---|
| 1 | Lấy file swagger | Xin từ team backend hoặc export từ Swagger UI của họ |
| 2 | Bỏ vào thư mục | `src/main/resources/swagger-definitions/ten-service.json` |
| 3 | Restart app | Xem log: `✓ Loaded: ten-service.json → X routes` |
| 4 | Xem operationId | Mở file swagger → tìm field `"operationId"` trong mỗi endpoint |
| 5 | Viết code gọi | Copy template ở trên, thay `operationId` và params |

---

## Cấu trúc file swagger cần có

```json
{
  "swagger": "2.0",
  "info": { "title": "Tên service", "version": "v1.0.0" },
  "host": "api.server.com",           ← URL backend target
  "basePath": "/api/v1.0.0/",         ← base path
  "paths": {
    "/endpoint/{id}": {
      "put": {
        "operationId": "tenOperation", ← QUAN TRỌNG: dùng cái này để gọi
        "parameters": [...],
        ...
      }
    }
  }
}
```

> **Quan trọng:** Mỗi endpoint trong swagger PHẢI có `operationId` — đây là key để gọi.

---

## Xử lý lỗi

```java
RestResponse<JsonNode> response = backendCaller.call("updateMsbCustomer", ...);

// Kiểm tra
response.isSuccess()        // true nếu HTTP 2xx
response.getStatusCode()    // HttpStatusCode (200, 400, 500...)
response.getBody()          // response body (null nếu lỗi)
response.getErrorMessage()  // message lỗi (null nếu thành công)
```

---

## Config (application.yml)

```yaml
# Thư mục chứa file swagger
swagger:
  definitions:
    path: classpath:swagger-definitions/
    auto-load: true

# Timeout mặc định khi gọi backend
rest-client:
  connect-timeout-ms: 5000
  read-timeout-ms: 30000
```
