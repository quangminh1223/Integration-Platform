# QR Service

Service tạo và phân tách mã QR theo chuẩn VietQR (NAPAS EMVCo).

## Tech Stack

| Thành phần | Phiên bản |
|---|---|
| Java | 21 |
| Spring Boot | 3.2.5 |
| Oracle DB | XE 21c |
| Redis | 7.2 |
| Apache Kafka | 7.6.0 (Confluent) |
| ZXing | 3.5.3 |

## Cấu trúc project

```
src/
├── main/java/com/qrservice/
│   ├── QrServiceApplication.java
│   ├── config/
│   │   ├── KafkaConfig.java        # Cấu hình Kafka topics
│   │   ├── OpenApiConfig.java      # Swagger UI
│   │   └── RedisConfig.java        # Redis cache manager
│   ├── common/
│   │   ├── enums/                  # QrType, QrStatus
│   │   ├── exception/              # Exception handling
│   │   └── response/               # ApiResponse wrapper
│   ├── controller/
│   │   ├── QrGenerateController    # POST /v1/qr/generate
│   │   ├── QrParseController       # POST /v1/qr/parse
│   │   └── QrHistoryController     # GET  /v1/qr/history
│   ├── domain/
│   │   ├── entity/                 # JPA entities
│   │   └── repository/             # Spring Data repositories
│   ├── dto/
│   │   ├── request/                # Request DTOs
│   │   └── response/               # Response DTOs
│   ├── kafka/
│   │   ├── consumer/               # Kafka consumers
│   │   ├── event/                  # Event POJOs
│   │   └── producer/               # Kafka producers
│   └── service/
│       ├── QrGenerateService       # Logic tạo QR
│       ├── QrImageService          # ZXing image processing
│       ├── QrParseService          # Logic phân tách QR
│       └── VietQrBuilderService    # VietQR EMVCo builder/parser
└── resources/
    ├── application.yml
    └── db/migration/               # SQL scripts
```

## REST APIs

### Tạo QR

| Method | Endpoint | Mô tả |
|---|---|---|
| POST | `/api/v1/qr/generate` | Tạo QR text/url/custom |
| POST | `/api/v1/qr/generate/vietqr` | Tạo QR theo chuẩn VietQR |
| GET  | `/api/v1/qr/generate/{id}` | Lấy QR theo ID |

### Phân tách QR

| Method | Endpoint | Mô tả |
|---|---|---|
| POST | `/api/v1/qr/parse` | Phân tách chuỗi QR |
| POST | `/api/v1/qr/parse/image` | Phân tách QR từ ảnh base64 |
| GET  | `/api/v1/qr/parse/{id}` | Lấy lịch sử parse theo ID |

### Lịch sử

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/api/v1/qr/history/generated` | Lịch sử tạo QR |
| GET | `/api/v1/qr/history/parsed` | Lịch sử phân tách QR |

## Chạy với Docker

```bash
# Khởi động tất cả services (Oracle, Redis, Kafka)
docker-compose up -d

# Chờ Oracle khởi động (~2 phút), sau đó chạy app
mvn spring-boot:run
```

## Swagger UI

Sau khi chạy, truy cập: http://localhost:8080/api/swagger-ui.html

## Ví dụ request

### Tạo VietQR

```json
POST /api/v1/qr/generate/vietqr
{
  "bankBin": "970436",
  "bankAccount": "1234567890",
  "accountName": "NGUYEN VAN A",
  "amount": 100000,
  "description": "Thanh toan hoa don",
  "transactionRef": "TXN20240101001"
}
```

### Phân tách QR

```json
POST /api/v1/qr/parse
{
  "rawContent": "000201010212..."
}
```

### Phân tách QR từ ảnh

```json
POST /api/v1/qr/parse/image
{
  "imageBase64": "iVBORw0KGgoAAAANSUhEUgAA..."
}
```

## Kafka Topics

| Topic | Mô tả |
|---|---|
| `qr.generated` | Sự kiện khi tạo QR thành công |
| `qr.parsed` | Sự kiện khi phân tách QR |

## Cấu hình môi trường

Chỉnh sửa `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@<host>:1521/<service>
    username: <user>
    password: <password>
  data:
    redis:
      host: <redis-host>
      port: 6379
  kafka:
    bootstrap-servers: <kafka-host>:9092
```
