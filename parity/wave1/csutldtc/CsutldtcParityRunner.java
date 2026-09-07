import com.carddemo.service.DateValidationRequest;
import com.carddemo.service.DateValidationResult;
import com.carddemo.service.DateValidationService;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Independent CSUTLDTC parity runner (playbook !mf_program_parity_test, wave 1).
 * Reads cases.csv (expectations derived from app/cbl/CSUTLDTC.cbl + the FR doc, never from the Java),
 * calls DateValidationService.validate, writes actual outputs verbatim and a PASS/FAIL per assertion.
 *
 * Normalization rule (applied to BOTH sides): none beyond exact byte comparison of the 80-char result,
 * because LS-RESULT is a fixed X(80) field (CSUTLDTC.cbl:86) -- trailing blanks are significant.
 *
 * Usage: java -cp backend/target/classes CsutldtcParityRunner.java cases.csv actual.csv results.md
 */
public class CsutldtcParityRunner {

    // CSUTLDTC.cbl:128-149 EVALUATE arms: message number -> 15-char reason text
    static final Map<String, String> CATALOGUE = Map.of(
            "0000", "Date is valid  ",
            "2507", "Insufficient   ",
            "2508", "Datevalue error",
            "2509", "Invalid Era    ",
            "2513", "Unsupp. Range  ",
            "2517", "Invalid month  ",
            "2518", "Bad Pic String ",
            "2520", "Nonnumeric data",
            "2521", "YearInEra is 0 ");

    record Row(String id, String req, String date, String mask, int sev, String msg, String reason, String f80,
               String source, String confidence, String cite) {}

    public static void main(String[] args) throws Exception {
        List<String> lines = Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8);
        DateValidationService svc = new DateValidationService();
        List<String> actualCsv = new ArrayList<>();
        actualCsv.add("id,date,mask,act_severity,act_msg,act_reason,act_formatted80,act_len");
        StringBuilder md = new StringBuilder();
        md.append("| id | req | input date | mask | expected (sev/msg/reason) | actual (sev/msg/reason) | f80 byte-exact | verdict | conf |\n");
        md.append("|---|---|---|---|---|---|---|---|---|\n");
        int pass = 0, fail = 0, run = 0;
        List<String> failures = new ArrayList<>();

        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] c = line.split(",", -1);
            Row r = new Row(c[0], c[1], c[2], c[3], Integer.parseInt(c[4]), c[5], c[6], c[7], c[8], c[9], c[10]);
            DateValidationResult a = svc.validate(new DateValidationRequest(r.date, r.mask));
            run++;
            actualCsv.add(String.join(",", r.id, q(r.date), q(r.mask), String.valueOf(a.severity()), a.messageNumber(),
                    q(a.reasonText()), q(a.formatted80()), String.valueOf(a.formatted80().length())));

            boolean codeInferred = r.confidence.contains("INFERRED") && r.sev == 3;
            boolean sevOk = a.severity() == r.sev;
            boolean msgOk = a.messageNumber().equals(r.msg);
            boolean reasonOk = a.reasonText().equals(r.reason);
            boolean f80Ok = a.formatted80().equals(r.f80);
            // FACT assertions that hold regardless of CEEDAYS mapping (CSUTLDTC.cbl:42-57, :97-98, :122-149):
            boolean len80 = a.formatted80().length() == 80;
            boolean sevBytes = a.formatted80().substring(0, 4).equals(String.format("%04d", a.severity()));
            boolean labels = a.formatted80().substring(4, 15).equals("Mesg Code: ")
                    && a.formatted80().substring(15, 19).equals(a.messageNumber())
                    && a.formatted80().charAt(19) == ' '
                    && a.formatted80().substring(20, 35).equals(a.reasonText())
                    && a.formatted80().charAt(35) == ' '
                    && a.formatted80().substring(36, 45).equals("TstDate: ")
                    && a.formatted80().charAt(55) == ' '
                    && a.formatted80().substring(56, 66).equals("Mask used:")
                    && a.formatted80().substring(66, 76).equals(pad10(r.mask))
                    && a.formatted80().substring(76).equals("    ");
            boolean dv04 = a.formatted80().substring(45, 55).equals(pad10(r.date));
            boolean pairing = a.reasonText().equals(CATALOGUE.getOrDefault(a.messageNumber(), "Date is invalid"));
            boolean pure = svc.validate(new DateValidationRequest(r.date, r.mask)).equals(a);

            String verdict;
            if (sevOk && msgOk && reasonOk && f80Ok && len80 && sevBytes && labels && dv04 && pairing && pure) {
                verdict = "PASS";
            } else if (sevOk && len80 && sevBytes && labels && dv04 && pairing && pure && codeInferred && !msgOk) {
                verdict = "PASS-WITH-RISK (INFERRED code differs)";
            } else {
                verdict = "FAIL";
            }
            if (verdict.startsWith("PASS")) pass++; else fail++;
            if (!verdict.equals("PASS")) {
                failures.add(String.format("%s: expected sev=%d msg=%s reason='%s' f80='%s' | actual sev=%d msg=%s reason='%s' f80='%s' | len80=%b sevBytes=%b labels=%b dv04=%b pairing=%b pure=%b | cite %s",
                        r.id, r.sev, r.msg, r.reason, r.f80, a.severity(), a.messageNumber(), a.reasonText(), a.formatted80(),
                        len80, sevBytes, labels, dv04, pairing, pure, r.cite));
            }
            md.append(String.format("| %s | %s | `%s` | `%s` | %04d/%s/`%s` | %04d/%s/`%s` | %s | %s | %s |\n",
                    r.id, r.req, r.date, r.mask, r.sev, r.msg, r.reason, a.severity(), a.messageNumber(), a.reasonText(),
                    f80Ok ? "yes" : "NO", verdict, r.confidence));
        }

        // Structural / contract cases not expressible as a CSV row
        StringBuilder extra = new StringBuilder();
        // DTC-S01 B-0014: null arguments -> IllegalArgumentException (plan §3.3; FR §7)
        extra.append(structural("DTC-S01", "B-0014 null date -> IllegalArgumentException", () -> {
            try { svc.validate(new DateValidationRequest(null, "YYYYMMDD")); return "no exception"; }
            catch (IllegalArgumentException e) { return "PASS"; } catch (RuntimeException e) { return e.getClass().getName(); }
        }));
        extra.append(structural("DTC-S02", "B-0014 null mask -> IllegalArgumentException", () -> {
            try { svc.validate(new DateValidationRequest("20240229", null)); return "no exception"; }
            catch (IllegalArgumentException e) { return "PASS"; } catch (RuntimeException e) { return e.getClass().getName(); }
        }));
        // DTC-S03 A-DTC-4: > 10 chars is impossible in COBOL (LS-DATE X(10), :84); FR recommends reject like null (INFERRED policy)
        extra.append(structural("DTC-S03", "A-DTC-4 11-char date -> IllegalArgumentException (INFERRED policy)", () -> {
            try { DateValidationResult x = svc.validate(new DateValidationRequest("20240229XYZ", "YYYYMMDD")); return "no exception: " + x; }
            catch (IllegalArgumentException e) { return "PASS"; } catch (RuntimeException e) { return e.getClass().getName(); }
        }));
        // DTC-S04 R-6 never throws for any non-null <=10 input: fuzz a grid of inputs; WHEN OTHER fallback must keep sev 3 + 'Date is invalid'
        extra.append(structural("DTC-S04", "R-6 never throws over 1,000 mixed inputs; unmapped -> sev 3 'Date is invalid'; catalogue pairing holds", () -> {
            String[] masks = {"YYYYMMDD", "YYYY-MM-DD", "MMDDYYYY", "ZZZZ", "", "YYYY/MM/DD", "DDMMYYYY", "YYMMDD"};
            String alphabet = "0123456789 -/AB";
            java.util.Random rnd = new java.util.Random(20240229);
            int fallback = 0;
            for (int i = 0; i < 1000; i++) {
                int len = rnd.nextInt(11);
                StringBuilder sb = new StringBuilder();
                for (int k = 0; k < len; k++) sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
                String m = masks[rnd.nextInt(masks.length)];
                DateValidationResult x;
                try { x = svc.validate(new DateValidationRequest(sb.toString(), m)); }
                catch (RuntimeException e) { return "THREW " + e + " for date='" + sb + "' mask='" + m + "'"; }
                if (x.formatted80().length() != 80) return "len!=80 for '" + sb + "'";
                if (!x.reasonText().equals(CATALOGUE.getOrDefault(x.messageNumber(), "Date is invalid")))
                    return "pairing broken: " + x;
                if (x.reasonText().equals("Date is invalid")) { fallback++; if (x.severity() != 3) return "fallback sev != 3: " + x; }
                if (x.severity() == 0 && !x.messageNumber().equals("0000")) return "sev 0 with msg != 0000: " + x;
                if (x.severity() != 0 && x.severity() != 3) return "unexpected severity: " + x;
            }
            return "PASS (fallback 'Date is invalid' observed " + fallback + " times)";
        }));

        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Path.of(args[1]), StandardCharsets.UTF_8))) {
            actualCsv.forEach(w::println);
        }
        StringBuilder out = new StringBuilder();
        out.append("### CSUTLDTC case results (cases derived: ").append(run).append(", run: ").append(run)
           .append(", passed: ").append(pass).append(", failed: ").append(fail).append(")\n\n");
        out.append(md).append("\n### Structural / contract cases\n\n").append(extra);
        if (!failures.isEmpty()) {
            out.append("\n### Non-PASS detail (expected vs actual, verbatim)\n\n");
            failures.forEach(f -> out.append("- ").append(f).append("\n"));
        }
        Files.writeString(Path.of(args[2]), out.toString(), StandardCharsets.UTF_8);
        System.out.print(out);
    }

    interface Check { String run(); }

    static String structural(String id, String title, Check c) {
        String res = c.run();
        return String.format("- %s %s -> **%s**\n", id, title, res.startsWith("PASS") ? res : "FAIL: " + res);
    }

    static String pad10(String s) {
        return s.length() >= 10 ? s.substring(0, 10) : String.format("%-10s", s);
    }

    static String q(String s) { return "\"" + s.replace("\"", "\"\"") + "\""; }
}
