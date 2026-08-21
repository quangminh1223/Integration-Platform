# msb-json-transform

Library transform JSON&rarr;JSON điều khiển bằng script khai báo cú pháp rút gọn kiểu ESQL
(IBM ACE/IIB). Thêm một transform mới = thêm một file script, không viết DTO, không build lại code.

Tách ra từ package `msb.com.vn.qrservice.mapping.declarative` của project ScanQRCode để dùng lại
độc lập.

---

## Toạ độ Maven

```xml
<dependency>
    <groupId>msb.com.vn</groupId>
    <artifactId>msb-json-transform</artifactId>
    <version>1.0.0</version>
</dependency>
```

| Thuộc tính | Giá trị |
|---|---|
| Java tối thiểu | 17 |
| Dependency | `jackson-databind` (duy nhất) |
| Không phụ thuộc | Spring, Lombok, jakarta, servlet |
| Thread-safe | Có — `JsonTransformer` và `JsonTransformDefinition` bất biến |

---

## Build

Có Maven:

```bash
mvn clean package
```

Chưa có Maven (gọi trực tiếp javac/jar của JDK, lấy Jackson từ `~/.m2`):

```powershell
powershell -ExecutionPolicy Bypass -File build-jar.ps1
```

Kết quả trong `target/`:

- `msb-json-transform-1.0.0.jar`
- `msb-json-transform-1.0.0-sources.jar`

Kiểm chứng nhanh trên JAR vừa build:

```powershell
powershell -ExecutionPolicy Bypass -File smoke-test\run-smoke-test.ps1
```

---

## Dùng

```java
JsonTransformer transformer = JsonTransformer.create();

JsonTransformDefinition def = transformer.compile("chargeCollection", """
        OutputRoot.JSON.Data.body.glAccount = TRIM(InputRoot.JSON.Data.Body.glAccount);
        OutputRoot.JSON.Data.body.name      = UPPER(InputRoot.JSON.Data.Body.customerName);
        OutputRoot.JSON.Data.body.reference  = CONCAT(InputRoot.JSON.Data.Body.branch, '-',
                                                     InputRoot.JSON.Data.Body.glAccount);
        OutputRoot.JSON.Data.body.currency  = 'VND';
        """);

ObjectNode output = transformer.execute(def, inputJsonNode);
```

Compile một lần lúc khởi động rồi tái sử dụng `def` cho mọi request — toàn bộ tokenize, parse
và validate đã xong ở bước compile.

Nạp cả thư mục script:

```java
Map<String, JsonTransformDefinition> defs =
        transformer.compileDirectory(Path.of("transform-definitions"));
```

Thêm hàm tự viết:

```java
JsonTransformer transformer = JsonTransformer.builder()
        .function(new MaskFunction())     // ghi đè được hàm mặc định cùng tên
        .build();
```

---

## Cấu trúc — 13 file, đọc theo thứ tự này

Toàn bộ lib nằm trong một package phẳng `msb.com.vn.jsontransform`. Không có subpackage,
không có tầng gián tiếp nào ngoài những gì liệt kê dưới đây.

| File | Dòng | Vai trò |
|---|---|---|
| `JsonTransformer` | 255 | **Bắt đầu đọc từ đây.** API công khai duy nhất: `compile`, `execute`, `builder` |
| `StringFunctions` | 190 | Cả 12 hàm chuỗi, mỗi hàm một dòng. Thêm hàm mới sửa đúng file này |
| `ScriptParser` | 199 | Tách script theo `;`, loại comment, nhận marker `required` |
| `ExpressionParser` | 275 | Parse vế phải thành cây biểu thức; kiểm tên hàm và số tham số |
| `TransformEngine` | 90 | Chạy từng dòng gán, gom lỗi field bắt buộc |
| `PathAccessor` | 129 | Đọc/ghi JSON theo dot-path, hỗ trợ `a.b[0].c` |
| `TransformExpression` | 34 | 3 dạng biểu thức: FieldRef, Literal, FunctionCall |
| `TransformAssignment` | 26 | Một dòng gán đã parse |
| `JsonTransformDefinition` | 55 | Script đã compile, bất biến |
| `TransformFunction` | 43 | Interface cho hàm tự viết |
| `TransformFunctionRegistry` | 50 | Bảng tra hàm theo tên |
| `JsonTransformException` | 42 | Lỗi lúc chạy: thiếu field `required` |
| `JsonTransformSyntaxException` | 25 | Lỗi lúc compile, kèm số dòng |

Muốn sửa hành vi thì gần như luôn chỉ cần chạm một trong ba file: `StringFunctions`
(thêm/đổi hàm), `ScriptParser` (đổi cú pháp dòng), `ExpressionParser` (đổi cú pháp biểu thức).

---

## Cú pháp script

```sql
-- Comment độc lập, bị bỏ qua hoàn toàn
OutputRoot.JSON.Data.<đích> = <biểu thức>;
```

Vế trái bắt buộc bắt đầu `OutputRoot.JSON.Data.`, vế phải là một trong ba dạng:

| Dạng | Ví dụ |
|---|---|
| Field | `InputRoot.JSON.Data.Body.glAccount`, `InputRoot.JSON.Data.items[0].code` |
| Literal | `'VND'` &middot; `123` &middot; `TRUE` &middot; `FALSE` &middot; `NULL` |
| Gọi hàm | `UPPER(TRIM(InputRoot.JSON.Data.Body.name))` — lồng tuỳ ý độ sâu |

Path đích hỗ trợ object lồng nhau và mảng: `body.paymentDetails[0].paymentDetail`.

Trong chuỗi literal, `''` là một dấu nháy đơn thật (chuẩn SQL/ESQL).

**Không hỗ trợ**: `IF/THEN/ELSE`, vòng lặp, toán tử số học, domain `XMLNSC`/`MRM`/`BLOB`.
Đây là tập con có chủ đích cho field-to-field mapping, không phải ESQL interpreter đầy đủ.

---

## 12 hàm có sẵn

| Hàm | Tham số | Ghi chú |
|---|---|---|
| `TRIM(s)` | 1 | null vào null ra |
| `UPPER(s)` / `LOWER(s)` | 1 | null vào null ra |
| `LENGTH(s)` | 1 | null vào trả `0` (kết quả là số) |
| `LEFT(s, n)` / `RIGHT(s, n)` | 2 | chuỗi ngắn hơn `n` thì trả nguyên chuỗi |
| `LPAD(s, len, pad)` / `RPAD(s, len, pad)` | 3 | đã đủ hoặc vượt `len` thì giữ nguyên, **không cắt** |
| `SUBSTRING(s, start, len)` | 3 | `start` đánh số **từ 1** theo chuẩn SQL/ESQL |
| `REPLACE(s, search, repl)` | 3 | so khớp chuỗi thuần, không phải regex |
| `CONCAT(a, b, ...)` | &ge;2 | null coi như chuỗi rỗng |
| `COALESCE(a, b, ...)` | &ge;2 | lấy giá trị đầu khác null **và** khác `""` |

Hai quy ước null khác nhau là có chủ ý: hàm một tham số lan truyền null, còn `CONCAT`/`COALESCE`
xử lý null tường minh — nếu không, chỉ vì một field tuỳ chọn rỗng mà cả chuỗi kết quả biến mất
khỏi output.

Cả 12 hàm nằm trong `StringFunctions.java` dưới dạng enum, mỗi hàm một dòng:

```java
TRIM(1, 1, a -> text(apply(str(a, 0), String::trim))),
LEFT(2, 2, a -> text(apply(str(a, 0), s -> s.substring(0, Math.min(nonNeg(a, 1), s.length()))))),
```

Thêm hàm mới cho mọi script dùng chung = thêm một hằng số `TEN(min, max, args -> ...)`.
Dùng `-1` cho max nếu không giới hạn số tham số. Không phải sửa parser hay engine.
Nếu chỉ cần cho một project riêng thì implement `TransformFunction` rồi truyền qua `builder()`.

---

## Xử lý field thiếu

| Trường hợp | Hành vi |
|---|---|
| Field không có giá trị, không đánh marker | **Bỏ qua**, không ghi `null` vào output |
| Field có marker `-- required`, không có giá trị | Ném `JsonTransformException` lúc `execute` |
| Sai cú pháp, hàm lạ, sai số tham số | Ném `JsonTransformSyntaxException` lúc `compile`, kèm số dòng |

Output chỉ chứa field thực sự có dữ liệu, nên bên nhận không phải phân biệt "field vắng mặt"
với "field có nhưng null".

Marker `required` chỉ có hiệu lực khi comment nằm **cùng dòng với code**:

```sql
OutputRoot.JSON.Data.body.ref = InputRoot.JSON.Data.ref;  -- required
OutputRoot.JSON.Data.body.ref = InputRoot.JSON.Data.ref;  -- required: mã đối soát
```

Comment độc lập không bao giờ đánh dấu statement nào, kể cả khi nội dung chứa chữ "required".

---

## Khác biệt so với bản trong ScanQRCode

Bản gốc ở `msb.com.vn.qrservice.mapping.declarative` gắn với Spring (`@Component`,
`@RestController`, `@Value`), Lombok và `QrException`. Bản library bỏ hết, kèm bốn thay đổi hành vi:

1. **Sửa lỗi marker `required`.** Bản gốc kiểm tra `physicalLine.toLowerCase().contains("required")`
   trên toàn bộ dòng và không giới hạn ở dòng có code, nên một comment mô tả như
   `-- CHÍNH SÁCH REQUIRED: không field nào bắt buộc` sẽ bật cờ và làm **statement kế tiếp**
   trở thành bắt buộc. Chính file `chargeCollection.esql` mắc lỗi này ở hai chỗ. Bản library yêu
   cầu comment phải cùng dòng với code và phải *bắt đầu* bằng từ `required`.

2. **`--` trong chuỗi literal không còn bị cắt thành comment.** `REPLACE(x, '--', '')` giờ hoạt động;
   bản gốc cắt tại `--` đầu tiên bất kể có nằm trong nháy đơn hay không.

3. **Field reference hỗ trợ chỉ số mảng.** `InputRoot.JSON.Data.items[0].code` parse được;
   bản gốc tokenize được `[` `]` nhưng parser không xử lý nên báo "Dư token".

4. **Target `release 17`** thay vì 21, nên `switch` pattern matching được viết lại bằng
   `instanceof` pattern. Hành vi không đổi.

5. **Gọn hơn đáng kể.** Bản trong app là 34 file / 2 305 dòng, lib này 13 file / 1 413 dòng
   cho cùng phần transform. Hai thay đổi lớn nhất:
   - 12 class hàm (14 file, ~540 dòng) &rarr; một enum `StringFunctions` (190 dòng). Phần khác
     nhau giữa các hàm chỉ là một biểu thức, không đáng một file riêng mỗi hàm.
   - Bỏ `Lexer` và tầng danh sách token; `ExpressionParser` đọc trực tiếp trên ký tự. Với ngữ
     pháp chỉ có ba dạng biểu thức, token list là một tầng dữ liệu dựng ra rồi duyệt lại.

   Hành vi giữ nguyên: smoke test 21/21 pass trước và sau khi gọn.

---

## Tích hợp vào Spring Boot

Library không phụ thuộc Spring; khai hai bean là xong:

```java
@Configuration
public class JsonTransformConfig {

    @Bean
    JsonTransformer jsonTransformer() {
        return JsonTransformer.create();
    }

    @Bean
    Map<String, JsonTransformDefinition> transformDefinitions(
            JsonTransformer transformer,
            @Value("${transform.definitions.path}") Path dir) {
        return transformer.compileDirectory(dir);   // sai cú pháp -> app không start
    }
}
```

Cho `JsonTransformException` trả HTTP 400 và `JsonTransformSyntaxException` trả 500 trong
`@RestControllerAdvice` của bạn.
