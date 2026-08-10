# Hướng dẫn Cache hệ thống trên Redis

Tài liệu mô tả cách lưu/đọc cache hệ thống (dữ liệu cấu hình từ DB) lên Redis và cách
refresh cache thủ công qua API admin.

---

## 1. Tổng quan

Mục tiêu: chuyển các bảng cấu hình từ DB (Oracle) lên Redis để truy cập nhanh, giảm tải DB.

Luồng dữ liệu:

```
Oracle DB  ──reload()──►  Redis (cache)  ──getRow()/getAll()──►  Ứng dụng
```

Các thành phần chính:

| Class | Vai trò |
|-------|---------|
| `SystemCacheStore` | Helper save/load lên Redis (key/value + hash). Chỉ bật khi cấu hình. |
| `DbTableCache` | Đọc **bất kỳ bảng nào** từ DB → cache lên Redis (không cần tạo class model). |
| `CacheAdminController` | API admin để refresh cache thủ công sau khi sửa DB. |

> Không cần tạo class model cho từng bảng. Mỗi dòng DB được lưu dưới dạng
> `Map<TÊN_CỘT, giá_trị>`.

---

## 2. Cấu hình

### 2.1. Bật Redis cache (`application.yml`)

```yaml
# Kết nối Redis
spring:
  data:
    redis:
      host: <redis-host>
      port: 6379
      password: <nếu có>

# Bật system cache
system-cache:
  redis:
    enabled: true            # mặc định false — phải set true mới hoạt động
    key-prefix: "syscache:"  # tiền tố key trên Redis
    ttl-seconds: 0           # 0 = không hết hạn; >0 = TTL theo giây
```

> Khi `enabled: false`, mọi thao tác cache tự bỏ qua (ghi) / trả rỗng (đọc),
> ứng dụng vẫn chạy bình thường. Bật `true` khi Redis đã sẵn sàng.

### 2.2. Cách lưu trên Redis

Mỗi bảng = 1 Redis **hash**:

```
key   = <key-prefix> + <tên bảng>      vd: syscache:ESB_SRV_PROFILE
field = giá trị cột khóa               vd: SRV979
value = cả dòng DB dạng JSON           vd: {"SPF_ID":979,"SPF_CODE":"SRV979",...}
```

---

## 3. Dùng trong code

```java
@Autowired
DbTableCache dbTableCache;

// Nạp toàn bộ bảng từ DB lên Redis (ghi đè dữ liệu cũ)
int rows = dbTableCache.reload("ESB_SRV_PROFILE", "SPF_CODE");

// Đọc 1 dòng theo khóa -> Map<TÊN_CỘT, giá trị>
Map<String, Object> row = dbTableCache.getRow("ESB_SRV_PROFILE", "SRV979");
String url = (String) row.get("SPF_URL");
String timeout = (String) row.get("TIMEOUT");

// Đọc cả bảng -> Map<khóa, dòng>
Map<String, Map<String, Object>> all = dbTableCache.getAll("ESB_SRV_PROFILE");

// Xóa cache 1 bảng
dbTableCache.evict("ESB_SRV_PROFILE");

// Kiểm tra Redis đã bật chưa
boolean on = dbTableCache.isEnabled();
```

---

## 4. API Admin — Refresh cache thủ công

Dùng khi: vừa sửa dữ liệu trong DB và muốn Redis cập nhật **ngay**, không chờ restart.

> Lưu ý: ứng dụng có `context-path: /api`. URL đầy đủ tùy môi trường triển khai;
> các đường dẫn dưới đây là path được khai báo ở controller.

### 4.1. Reload 1 bảng

```
POST /api/v1/admin/cache/reload/{table}?keyColumn={cột_khóa}
```

```bash
curl -X POST "http://localhost:8080/api/v1/admin/cache/reload/ESB_SRV_PROFILE?keyColumn=SPF_CODE"
```

Response:
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Đã nạp 670 dòng của bảng ESB_SRV_PROFILE",
  "data": { "table": "ESB_SRV_PROFILE", "keyColumn": "SPF_CODE", "cachedRows": 670 }
}
```

### 4.2. Reload nhiều bảng cùng lúc

```
POST /api/v1/admin/cache/reload
```

```bash
curl -X POST "http://localhost:8080/api/v1/admin/cache/reload" \
  -H "Content-Type: application/json" \
  -d '[
        {"table":"ESB_SRV_PROFILE","keyColumn":"SPF_CODE"},
        {"table":"ESB_AUTHORIZED","keyColumn":"ATZ_ID"},
        {"table":"ESB_CONSUMER","keyColumn":"CSM_CODE"}
      ]'
```

Lỗi ở 1 bảng không làm hỏng các bảng khác — mỗi bảng có `success`/`error` riêng.

### 4.3. Xóa cache 1 bảng

```
DELETE /api/v1/admin/cache/{table}
```

```bash
curl -X DELETE "http://localhost:8080/api/v1/admin/cache/ESB_SRV_PROFILE"
```

### 4.4. Mã lỗi thường gặp

| HTTP | code | Ý nghĩa |
|------|------|---------|
| 503 | `REDIS_DISABLED` | Chưa bật `system-cache.redis.enabled=true` |
| 400 | `INVALID_INPUT` | Tên bảng/cột sai định dạng |
| 500 | `RELOAD_FAILED` | Lỗi khi truy vấn DB / Redis |

---

## 5. Các bảng cấu hình (DB esbuat)

| Bảng | Mô tả | Cột khóa gợi ý | Số dòng (tham khảo) |
|------|-------|----------------|---------------------|
| `ESB_SRV_PROFILE` | Service profile (SPF) | `SPF_CODE` | ~670 |
| `ESB_AUTHORIZED` | Phân quyền consumer↔service (ATZ) | `ATZ_ID` | ~5798 |
| `ESB_CONSUMER` | Consumer (CSM) | `CSM_CODE` | ~67 |

---

## 6. Chiến lược refresh

Hiện dùng **refresh thủ công** (mục 4): sửa DB xong thì gọi API reload.

Các phương án khác có thể bổ sung sau:
- **Warm-up lúc khởi động**: nạp sẵn khi app start.
- **Scheduled** (`@Scheduled`): tự reload định kỳ (vd 5 phút) làm lưới an toàn.
- **TTL**: đặt `ttl-seconds > 0` để key tự hết hạn rồi nạp lại.

---

## 7. Lưu ý bảo mật

- API admin (`/api/v1/admin/cache/**`) **chưa có xác thực**. Trước khi lên production:
  đặt sau API gateway / Spring Security, hoặc giới hạn IP nội bộ.
- Mật khẩu DB/Redis nên đưa vào biến môi trường / vault thay vì hardcode trong
  `application.yml`.
- Cột kiểu `DATE` (vd `SPF_UPDATE_DATE`) khi cache sẽ lưu dạng timestamp số — đọc lại
  vẫn đúng giá trị, chỉ không phải kiểu ngày "đẹp".
```
