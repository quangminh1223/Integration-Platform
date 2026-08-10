# 📁 Swagger Definitions

Thư mục này chứa các file Swagger 2.0 / OpenAPI 3.x.
Khi app khởi động, tất cả file ở đây sẽ được tự động load và đăng ký thành endpoint.

## Cách sử dụng

1. **Bỏ file swagger vào đây** (JSON hoặc YAML)
2. **Restart app** → routes tự động được đăng ký
3. **Gọi API** qua prefix: `http://localhost:8080/api/v1/dynamic/{path-trong-swagger}`

## Quy tắc đặt tên file

- Tên file sẽ trở thành `swaggerId` (dùng để quản lý, xóa route)
- Ví dụ: `updateMsbCustomer-v1.0.0-swagger.json` → swaggerId = `updateMsbCustomer-v1.0.0-swagger`

## Format hỗ trợ

| Extension | Format |
|-----------|--------|
| `.json`   | Swagger 2.0 JSON hoặc OpenAPI 3.x JSON |
| `.yaml`   | Swagger 2.0 YAML hoặc OpenAPI 3.x YAML |
| `.yml`    | Swagger 2.0 YAML hoặc OpenAPI 3.x YAML |

## Ví dụ

File `updateMsbCustomer-v1.0.0-swagger.json` định nghĩa:
```
PUT /product/msb/customer/maintenance/amend/{id}
host: api.server.com
basePath: /api/v1.0.0/
```

Sau khi load, gọi:
```
PUT http://localhost:8080/api/v1/dynamic/product/msb/customer/maintenance/amend/123
Body: { ... }
```

→ Service sẽ forward tới:
```
PUT http://api.server.com/api/v1.0.0/product/msb/customer/maintenance/amend/123
```

## Override target URL

Nếu swagger không có `host` hoặc muốn đổi target, thêm header:
```
X-Target-Url: http://10.0.1.50:9090/api/v2
```

## Quản lý routes

- Xem routes: `GET /api/v1/swagger/routes`
- Xóa routes: `DELETE /api/v1/swagger/routes/{swaggerId}`
- Re-import: `POST /api/v1/swagger/import/file`

## Config (application.yml)

```yaml
swagger:
  definitions:
    path: classpath:swagger-definitions/   # thư mục scan
    auto-load: true                         # true = tự load khi khởi động
```
