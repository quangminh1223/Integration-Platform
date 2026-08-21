import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import msb.com.vn.jsontransform.JsonTransformDefinition;
import msb.com.vn.jsontransform.JsonTransformException;
import msb.com.vn.jsontransform.JsonTransformSyntaxException;
import msb.com.vn.jsontransform.JsonTransformer;

/**
 * Smoke test chạy trực tiếp trên JAR đã build — không dùng JUnit để khỏi thêm dependency.
 * Mỗi assert in ra PASS/FAIL; có FAIL thì exit code khác 0.
 */
public class SmokeTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        JsonTransformer t = JsonTransformer.create();
        ObjectMapper mapper = new ObjectMapper();

        check("12 ham mac dinh duoc dang ky", t.availableFunctions().size() == 12,
                "actual=" + t.availableFunctions());

        // ── 1. Gan truc tiep + cac ham chuoi long nhau ─────────────────────────
        String script = String.join("\n",
                "OutputRoot.JSON.Data.body.glAccount    = InputRoot.JSON.Data.Body.glAccount;",
                "OutputRoot.JSON.Data.body.trimmed      = TRIM(InputRoot.JSON.Data.Body.glAccount);",
                "OutputRoot.JSON.Data.body.nameUpper    = UPPER(TRIM(InputRoot.JSON.Data.Body.customerName));",
                "OutputRoot.JSON.Data.body.padded       = LPAD(InputRoot.JSON.Data.Body.branchCode, 8, '0');",
                "OutputRoot.JSON.Data.body.reference    = CONCAT(InputRoot.JSON.Data.Body.branchCode, '-', InputRoot.JSON.Data.Body.glAccount);",
                "OutputRoot.JSON.Data.body.prefix       = SUBSTRING(InputRoot.JSON.Data.Body.branchCode, 1, 2);",
                "OutputRoot.JSON.Data.body.note         = COALESCE(InputRoot.JSON.Data.Body.note, 'Khong co ghi chu');",
                "OutputRoot.JSON.Data.body.len          = LENGTH(InputRoot.JSON.Data.Body.branchCode);",
                "OutputRoot.JSON.Data.body.currency     = 'VND';",
                "OutputRoot.JSON.Data.body.missingField = InputRoot.JSON.Data.Body.notThere;");

        String input = """
                {"Body": {
                   "glAccount": "  1217-000123  ",
                   "customerName": " nguyen van an ",
                   "branchCode": "HN01",
                   "note": ""
                }}""";

        JsonTransformDefinition def = t.compile("chargeCollection", script);
        ObjectNode out = t.execute(def, input);
        JsonNode body = out.path("body");

        check("copy nguyen van", "  1217-000123  ".equals(body.path("glAccount").asText()),
                body.path("glAccount").asText());
        check("TRIM", "1217-000123".equals(body.path("trimmed").asText()),
                body.path("trimmed").asText());
        check("UPPER(TRIM(..)) long ham", "NGUYEN VAN AN".equals(body.path("nameUpper").asText()),
                body.path("nameUpper").asText());
        check("LPAD den 8 ky tu", "0000HN01".equals(body.path("padded").asText()),
                body.path("padded").asText());
        check("CONCAT 3 tham so", "HN01-  1217-000123  ".equals(body.path("reference").asText()),
                body.path("reference").asText());
        check("SUBSTRING 1-based", "HN".equals(body.path("prefix").asText()),
                body.path("prefix").asText());
        check("COALESCE coi '' la rong", "Khong co ghi chu".equals(body.path("note").asText()),
                body.path("note").asText());
        check("LENGTH tra ve so", body.path("len").asInt() == 4, body.path("len").toString());
        check("literal 'VND'", "VND".equals(body.path("currency").asText()),
                body.path("currency").asText());
        check("field thieu bi BO QUA, khong ghi null", !body.has("missingField"),
                "output=" + body);

        // ── 2. Marker required: comment doc lap KHONG duoc danh dau statement ──
        //    Day la bug cua ban goc trong qrservice: dong comment chua chu "required"
        //    lam statement ke tiep bi coi la bat buoc.
        String proseScript = String.join("\n",
                "-- CHINH SACH REQUIRED: khong field nao bat buoc.",
                "-- Khong danh required o transform nay.",
                "OutputRoot.JSON.Data.body.a = InputRoot.JSON.Data.absent;");
        ObjectNode proseOut = t.execute(t.compile("prose", proseScript),
                mapper.readTree("{}"));
        check("comment van xuoi chua chu 'required' KHONG lam field thanh bat buoc",
                proseOut.isEmpty(), "output=" + proseOut);

        // ── 3. Marker required that su: comment cung dong voi code ─────────────
        String reqScript = "OutputRoot.JSON.Data.body.ref = InputRoot.JSON.Data.ref;  -- required";
        JsonTransformDefinition reqDef = t.compile("req", reqScript);
        boolean threw = false;
        try {
            t.execute(reqDef, mapper.readTree("{}"));
        } catch (JsonTransformException e) {
            threw = true;
        }
        check("marker '-- required' cung dong voi code -> nem JsonTransformException", threw, "");
        check("co gia tri thi khong nem",
                "R1".equals(t.execute(reqDef, "{\"ref\":\"R1\"}").path("body").path("ref").asText()),
                "");

        // ── 4. '--' trong chuoi literal khong bi coi la comment ───────────────
        String dashScript =
                "OutputRoot.JSON.Data.body.clean = REPLACE(InputRoot.JSON.Data.acc, '--', '');";
        ObjectNode dashOut = t.execute(t.compile("dash", dashScript),
                "{\"acc\":\"12--34\"}");
        check("REPLACE voi chuoi literal '--' hoat dong",
                "1234".equals(dashOut.path("body").path("clean").asText()),
                dashOut.toString());

        // ── 5. Loi cu phap lo ra ngay luc COMPILE ─────────────────────────────
        check("ham khong ton tai -> loi luc compile",
                syntaxErrors("OutputRoot.JSON.Data.a = NOSUCHFN(InputRoot.JSON.Data.x);"), "");
        check("sai so tham so -> loi luc compile",
                syntaxErrors("OutputRoot.JSON.Data.a = TRIM(InputRoot.JSON.Data.x, 2);"), "");
        check("thieu dau ';' -> loi luc compile",
                syntaxErrors("OutputRoot.JSON.Data.a = InputRoot.JSON.Data.x"), "");
        check("ve trai sai prefix -> loi luc compile",
                syntaxErrors("Output.JSON.Data.a = InputRoot.JSON.Data.x;"), "");
        check("thieu nhay don dong -> loi luc compile",
                syntaxErrors("OutputRoot.JSON.Data.a = CONCAT('abc, InputRoot.JSON.Data.x);"), "");

        // ── 6. Ghi vao path long nhau va mang ─────────────────────────────────
        ObjectNode nested = t.execute(t.compile("nested",
                "OutputRoot.JSON.Data.body.paymentDetails[0].detail = InputRoot.JSON.Data.d;"),
                "{\"d\":\"noi dung\"}");
        check("ghi vao mang body.paymentDetails[0].detail",
                "noi dung".equals(nested.path("body").path("paymentDetails").path(0).path("detail").asText()),
                nested.toString());

        System.out.println();
        System.out.println("PASSED=" + passed + "  FAILED=" + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static boolean syntaxErrors(String script) {
        try {
            JsonTransformer.create().compile("x", script);
            return false;
        } catch (JsonTransformSyntaxException e) {
            return true;
        }
    }

    private static void check(String label, boolean ok, String detail) {
        if (ok) {
            passed++;
            System.out.println("  PASS  " + label);
        } else {
            failed++;
            System.out.println("  FAIL  " + label + "   -> " + detail);
        }
    }
}
