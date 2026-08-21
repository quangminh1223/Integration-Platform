package msb.com.vn.jsontransform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Điểm vào duy nhất của lib: compile script transform rồi chạy trên input JSON.
 *
 * <p>Đối tượng này bất biến và an toàn khi dùng đồng thời nhiều thread — tạo một lần rồi
 * dùng chung cho toàn ứng dụng.</p>
 *
 * <h3>Dùng nhanh</h3>
 * <pre>{@code
 * JsonTransformer transformer = JsonTransformer.create();
 *
 * JsonTransformDefinition def = transformer.compile("chargeCollection", """
 *     OutputRoot.JSON.Data.body.glAccount = TRIM(InputRoot.JSON.Data.Body.glAccount);
 *     OutputRoot.JSON.Data.body.name      = UPPER(InputRoot.JSON.Data.Body.customerName);
 *     OutputRoot.JSON.Data.body.currency  = 'VND';
 *     """);
 *
 * ObjectNode output = transformer.execute(def, inputJsonNode);
 * }</pre>
 *
 * <h3>Nạp từ thư mục, thêm hàm tự viết</h3>
 * <pre>{@code
 * JsonTransformer transformer = JsonTransformer.builder()
 *         .function(new MaskFunction())
 *         .build();
 *
 * Map<String, JsonTransformDefinition> defs =
 *         transformer.compileDirectory(Path.of("transform-definitions"));
 *
 * ObjectNode out = transformer.execute(defs.get("chargeCollection"), input);
 * }</pre>
 *
 * <h3>Nguyên tắc xử lý lỗi</h3>
 * <ul>
 *   <li>Sai cú pháp, hàm không tồn tại, sai số tham số &rarr;
 *       {@link JsonTransformSyntaxException} lúc {@code compile}, kèm số dòng</li>
 *   <li>Field {@code -- required} không có giá trị &rarr; {@link JsonTransformException}
 *       lúc {@code execute}</li>
 *   <li>Field không required mà thiếu &rarr; bỏ qua, không xuất hiện trong output</li>
 * </ul>
 */
public final class JsonTransformer {

    /** Phần mở rộng mặc định của file script. */
    public static final String SCRIPT_EXTENSION = ".esql";

    private final TransformFunctionRegistry functionRegistry;
    private final ScriptParser scriptParser;
    private final TransformEngine engine;
    private final ObjectMapper objectMapper;

    private JsonTransformer(TransformFunctionRegistry functionRegistry, ObjectMapper objectMapper) {
        this.functionRegistry = functionRegistry;
        this.scriptParser = new ScriptParser(new ExpressionParser(functionRegistry));
        this.engine = new TransformEngine(functionRegistry);
        this.objectMapper = objectMapper;
    }

    /** Transformer với 12 hàm chuỗi mặc định. */
    public static JsonTransformer create() {
        return new JsonTransformer(TransformFunctionRegistry.withBuiltIn(), new ObjectMapper());
    }

    public static Builder builder() {
        return new Builder();
    }

    // ─── Compile ────────────────────────────────────────────────────────────

    /**
     * Compile nội dung script thành {@link JsonTransformDefinition}.
     *
     * @param operationId định danh gắn vào definition, dùng trong log và thông báo lỗi
     * @param script      nội dung script
     * @throws JsonTransformSyntaxException nếu script sai cú pháp
     */
    public JsonTransformDefinition compile(String operationId, String script) {
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("operationId không được rỗng");
        }
        if (script == null) {
            throw new IllegalArgumentException("script không được null");
        }
        List<TransformAssignment> assignments = scriptParser.parse(script);
        return new JsonTransformDefinition(operationId, assignments, script);
    }

    /**
     * Compile một file script. {@code operationId} lấy từ tên file, bỏ phần mở rộng.
     */
    public JsonTransformDefinition compileFile(Path scriptFile) {
        String filename = scriptFile.getFileName().toString();
        String operationId = stripExtension(filename);
        try {
            return compile(operationId, Files.readString(scriptFile, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được file script: " + scriptFile, e);
        }
    }

    /**
     * Compile mọi file {@code *.esql} trong một thư mục (không đệ quy).
     *
     * <p>Trả về map giữ nguyên thứ tự tên file. Một file sai cú pháp sẽ làm hàm này ném lỗi
     * ngay — cố ý như vậy để cấu hình sai không lọt vào runtime. Nếu bạn muốn bỏ qua file
     * lỗi và chạy tiếp, hãy tự lặp và gọi {@link #compileFile(Path)} trong try/catch.</p>
     *
     * @throws JsonTransformSyntaxException nếu bất kỳ file nào sai cú pháp
     */
    public Map<String, JsonTransformDefinition> compileDirectory(Path directory) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Không phải thư mục: " + directory);
        }
        Map<String, JsonTransformDefinition> result = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> scripts = files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(SCRIPT_EXTENSION))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            for (Path script : scripts) {
                JsonTransformDefinition definition = compileFile(script);
                result.put(definition.operationId(), definition);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được thư mục script: " + directory, e);
        }
        return result;
    }

    // ─── Execute ────────────────────────────────────────────────────────────

    /**
     * Chạy transform trên input JSON.
     *
     * @param input node đóng vai {@code InputRoot.JSON.Data} trong script
     * @return output JSON tương ứng {@code OutputRoot.JSON.Data}
     * @throws JsonTransformException nếu thiếu field {@code -- required}
     */
    public ObjectNode execute(JsonTransformDefinition definition, JsonNode input) {
        if (definition == null) {
            throw new IllegalArgumentException("definition không được null");
        }
        return engine.execute(definition, input);
    }

    /** Như {@link #execute(JsonTransformDefinition, JsonNode)} nhưng input là chuỗi JSON. */
    public ObjectNode execute(JsonTransformDefinition definition, String inputJson) {
        try {
            return execute(definition, objectMapper.readTree(inputJson));
        } catch (IOException e) {
            throw new IllegalArgumentException("Input không phải JSON hợp lệ: " + e.getMessage(), e);
        }
    }

    /** Chạy transform và trả về chuỗi JSON. */
    public String executeToString(JsonTransformDefinition definition, JsonNode input) {
        return execute(definition, input).toString();
    }

    /** Compile rồi chạy một lần — tiện cho test, KHÔNG nên dùng trong hot path. */
    public ObjectNode transform(String operationId, String script, JsonNode input) {
        return execute(compile(operationId, script), input);
    }

    // ─── Introspection ──────────────────────────────────────────────────────

    /** Tên các hàm đang khả dụng trong script. */
    public List<String> availableFunctions() {
        return functionRegistry.registeredNames();
    }

    private static String stripExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(0, lastDot) : filename;
    }

    // ─── Builder ────────────────────────────────────────────────────────────

    /**
     * Dựng {@link JsonTransformer} với hàm tự viết và/hoặc {@link ObjectMapper} riêng.
     *
     * <p>Hàm thêm qua {@link #function(TransformFunction)} được đăng ký TRƯỚC hàm mặc định,
     * nên có thể ghi đè hành vi của hàm cùng tên.</p>
     */
    public static final class Builder {

        private final List<TransformFunction> customFunctions = new ArrayList<>();
        private boolean includeBuiltIn = true;
        private ObjectMapper objectMapper;

        private Builder() {
        }

        /** Thêm một hàm tự viết. */
        public Builder function(TransformFunction function) {
            if (function == null) {
                throw new IllegalArgumentException("function không được null");
            }
            customFunctions.add(function);
            return this;
        }

        /** Thêm nhiều hàm tự viết. */
        public Builder functions(Collection<? extends TransformFunction> functions) {
            functions.forEach(this::function);
            return this;
        }

        /**
         * Bỏ toàn bộ 12 hàm mặc định, chỉ dùng hàm tự đăng ký. Dùng khi bạn muốn kiểm soát
         * chặt tập hàm mà script được phép gọi.
         */
        public Builder withoutBuiltIn() {
            this.includeBuiltIn = false;
            return this;
        }

        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        public JsonTransformer build() {
            List<TransformFunction> all = new ArrayList<>(customFunctions);
            if (includeBuiltIn) {
                all.addAll(List.of(StringFunctions.values()));
            }
            if (all.isEmpty()) {
                throw new IllegalStateException(
                        "Không có hàm nào được đăng ký — gọi function() hoặc bỏ withoutBuiltIn()");
            }
            return new JsonTransformer(new TransformFunctionRegistry(all),
                    objectMapper != null ? objectMapper : new ObjectMapper());
        }
    }
}
