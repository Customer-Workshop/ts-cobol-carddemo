package com.carddemo.service;

import com.carddemo.api.CobolMessages;
import com.carddemo.service.AccountUpdateScreen.Flag;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 1200-EDIT-MAP-INPUTS (COACTUPC.cbl:1469-1677) — the ordered edit ladder plus
 * the 1205-COMPARE-OLD-NEW change gate and the CSUTLDPY/CCSLKPCDY tables.
 * First failing message wins (WS-RETURN-MSG is written only while OFF);
 * flags keep accumulating so every invalid field renders red.
 */
@Component
public class AccountUpdateEditRules {

    private static final Set<String> AREA_CODES = load("us-area-codes.txt");
    private static final Set<String> STATE_CODES = load("us-state-codes.txt");
    private static final Set<String> STATE_ZIP_COMBOS = load("us-state-zip-combos.txt");
    private static final Set<String> MONTHS_31 = Set.of(
            "01", "03", "05", "07", "08", "10", "12");

    private static Set<String> load(String file) {
        try (InputStream in = AccountUpdateEditRules.class
                .getResourceAsStream("/cslkpcdy/" + file)) {
            if (in == null) {
                throw new IllegalStateException("missing /cslkpcdy/" + file);
            }
            List<String> lines = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .lines().filter(line -> !line.isBlank()).toList();
            return Set.copyOf(new TreeSet<>(lines));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public record Result(Map<String, Flag> flags, String firstMessage) {
        public boolean valid() {
            return flags.isEmpty();
        }

        public String firstInvalidField() {
            return flags.isEmpty() ? null : flags.keySet().iterator().next();
        }
    }

    private static final class Ctx {
        private final Map<String, Flag> flags = new LinkedHashMap<>();
        private String firstMessage;

        void flag(String field, Flag flag) {
            flags.put(field, flag);
        }

        void message(String message) {
            if (firstMessage == null) {
                firstMessage = message;
            }
        }

        boolean ok(String field) {
            return !flags.containsKey(field);
        }

        Result result() {
            return new Result(flags, firstMessage);
        }
    }

    /** 1100-RECEIVE-MAP: `field = '*'` (padded compare) OR `= SPACES`. */
    static boolean blank(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return value.stripTrailing().equals("*");
    }

    /** Blank-normalized raw input (the LOW-VALUES receive result). */
    static String norm(String value) {
        return blank(value) ? "" : value;
    }

    /** UPPER-CASE(TRIM(x)) = UPPER-CASE(TRIM(y)) from 1205-COMPARE-OLD-NEW. */
    private static boolean upEq(String typed, String stored) {
        return norm(typed).trim().equalsIgnoreCase(stored == null ? "" : stored.trim());
    }

    /** Plain PIC X compare — equal iff the right-trimmed strings match. */
    private static boolean partEq(String typed, String stored) {
        return rtrim(norm(typed)).equals(rtrim(stored == null ? "" : stored));
    }

    private static String rtrim(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == ' ') {
            end--;
        }
        return value.substring(0, end);
    }

    private static String take(String value, int len) {
        String v = norm(value);
        return v.length() > len ? v.substring(0, len) : v;
    }

    private static boolean isLeapYear(String yyyy) {
        int year = Integer.parseInt(yyyy);
        int yy = year % 100;
        int divisor = yy == 0 ? 400 : 4;
        return year % divisor == 0;
    }

    /**
     * TEST-NUMVAL-C: leading/trailing spaces, an optional sign, `$`, `,`
     * separators, a decimal point, and trailing CR/DB credit markers.
     * Returns null when the input would not parse.
     */
    static BigDecimal numvalC(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        boolean negative = false;
        if (s.endsWith("CR") || s.endsWith("DB")) {
            negative = true;
            s = s.substring(0, s.length() - 2).trim();
        }
        s = s.replace("$", "").replace(",", "");
        if (s.startsWith("+")) {
            s = s.substring(1);
        } else if (s.startsWith("-")) {
            negative = true;
            s = s.substring(1);
        }
        s = s.trim();
        if (!s.matches("\\d+(\\.\\d*)?|\\.\\d+")) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(s);
            return negative ? value.negate() : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 1205-COMPARE-OLD-NEW: true when any ACUP-NEW-* field differs from the snapshot. */
    public boolean changed(AccountUpdateSnapshot o, AccountUpdateForm n) {
        if (!partEq(n.acctId(), o.accountId() == null ? "" : "%011d".formatted(o.accountId()))
                || !upEq(n.activeStatus(), o.activeStatus())
                || moneyChanged(n.currentBalance(), o.currentBalance())
                || moneyChanged(n.creditLimit(), o.creditLimit())
                || moneyChanged(n.cashCreditLimit(), o.cashCreditLimit())
                || !partsEq(n.openYear(), n.openMon(), n.openDay(), o.openDate())
                || !partsEq(n.expYear(), n.expMon(), n.expDay(), o.expirationDate())
                || !partsEq(n.risYear(), n.risMon(), n.risDay(), o.reissueDate())
                || moneyChanged(n.currentCycleCredit(), o.currentCycleCredit())
                || moneyChanged(n.currentCycleDebit(), o.currentCycleDebit())
                || !upEq(n.accountGroup(), o.accountGroup())) {
            return true;
        }
        if (!upEq(n.custId(), o.customerId() == null ? "" : "%09d".formatted(o.customerId()))
                || !upEq(n.firstName(), o.firstName())
                || !upEq(n.middleName(), o.middleName())
                || !upEq(n.lastName(), o.lastName())
                || !upEq(n.addrLine1(), o.addressLine1())
                || !upEq(n.addrLine2(), o.addressLine2())
                || !upEq(n.city(), o.addressLine3())
                || !upEq(n.state(), o.stateCode())
                || !upEq(n.country(), o.countryCode())
                || !upEq(n.zip(), o.zip())) {
            return true;
        }
        String[] p1 = AccountUpdateSnapshot.phoneParts(o.phoneNumber1());
        String[] p2 = AccountUpdateSnapshot.phoneParts(o.phoneNumber2());
        String ssn = o.ssn() == null ? "" : "%09d".formatted(o.ssn());
        if (!partEq(n.phone1a(), p1[0]) || !partEq(n.phone1b(), p1[1])
                || !partEq(n.phone1c(), p1[2])
                || !partEq(n.phone2a(), p2[0]) || !partEq(n.phone2b(), p2[1])
                || !partEq(n.phone2c(), p2[2])
                || !partEq(n.ssn1(), ssn.isEmpty() ? "" : ssn.substring(0, 3))
                || !partEq(n.ssn2(), ssn.isEmpty() ? "" : ssn.substring(3, 5))
                || !partEq(n.ssn3(), ssn.isEmpty() ? "" : ssn.substring(5))
                || !upEq(n.governmentId(), o.governmentIssuedId())
                || !partsEq(n.dobYear(), n.dobMon(), n.dobDay(), o.dateOfBirth())
                || !partEq(n.eftAccountId(), o.eftAccountId())
                || !upEq(n.priCardHolder(), o.primaryCardHolderIndicator())
                || !partEq(n.ficoScore(),
                        o.ficoScore() == null ? "" : "%03d".formatted(o.ficoScore()))) {
            return true;
        }
        return false;
    }

    private static boolean moneyChanged(String typed, BigDecimal stored) {
        String v = norm(typed);
        if (v.isEmpty()) {
            return stored != null;
        }
        BigDecimal parsed = numvalC(v);
        if (parsed == null || stored == null) {
            return true;
        }
        return parsed.compareTo(stored) != 0;
    }

    private static boolean partsEq(String y, String m, String d, LocalDate date) {
        if (date == null) {
            return partEq(y, "") && partEq(m, "") && partEq(d, "");
        }
        return partEq(y, "%04d".formatted(date.getYear()))
                && partEq(m, "%02d".formatted(date.getMonthValue()))
                && partEq(d, "%02d".formatted(date.getDayOfMonth()));
    }

    /** The ordered edit ladder (cbl:1469-1677). */
    public Result validate(AccountUpdateForm f) {
        Ctx ctx = new Ctx();
        editYesNo(ctx, "Account Status", f.activeStatus(), "acsttus");
        editDate(ctx, "Open Date", f.openYear(), f.openMon(), f.openDay(),
                "opnyear", "opnmon", "opnday");
        editMoney(ctx, "Credit Limit", f.creditLimit(), "acrdlim");
        editDate(ctx, "Expiry Date", f.expYear(), f.expMon(), f.expDay(),
                "expyear", "expmon", "expday");
        editMoney(ctx, "Cash Credit Limit", f.cashCreditLimit(), "acshlim");
        editDate(ctx, "Reissue Date", f.risYear(), f.risMon(), f.risDay(),
                "risyear", "rismon", "risday");
        editMoney(ctx, "Current Balance", f.currentBalance(), "acurbal");
        editMoney(ctx, "Current Cycle Credit Limit", f.currentCycleCredit(), "acrcycr");
        editMoney(ctx, "Current Cycle Debit Limit", f.currentCycleDebit(), "acrcydb");
        editSsn(ctx, f);
        editDob(ctx, f);
        editFico(ctx, f);
        editAlphaRequired(ctx, "First Name", f.firstName(), 25, "acsfnam");
        editAlphaOptional(ctx, "Middle Name", f.middleName(), 25, "acsmnam");
        editAlphaRequired(ctx, "Last Name", f.lastName(), 25, "acslnam");
        editMandatory(ctx, "Address Line 1", f.addrLine1(), 50, "acsadl1");
        editAlphaRequired(ctx, "State", f.state(), 2, "acsstte");
        if (ctx.ok("acsstte")) {
            editUsState(ctx, f.state(), "acsstte");
        }
        editNumRequired(ctx, "Zip", f.zip(), 5, "acszipc");
        // Address Line 2 is optional and carries no edit (cbl:1614-1616).
        editAlphaRequired(ctx, "City", f.city(), 50, "acscity");
        editAlphaRequired(ctx, "Country", f.country(), 3, "acsctry");
        editPhone(ctx, "Phone Number 1", f.phone1a(), f.phone1b(), f.phone1c(),
                "acsph1a", "acsph1b", "acsph1c");
        editPhone(ctx, "Phone Number 2", f.phone2a(), f.phone2b(), f.phone2c(),
                "acsph2a", "acsph2b", "acsph2c");
        editNumRequired(ctx, "EFT Account Id", f.eftAccountId(), 10, "acseftc");
        editYesNo(ctx, "Primary Card Holder", f.priCardHolder(), "acspflg");
        if (ctx.ok("acsstte") && ctx.ok("acszipc")) {
            editStateZip(ctx, f.state(), f.zip());
        }
        return ctx.result();
    }

    private void editMandatory(Ctx ctx, String name, String raw, int len, String field) {
        if (blank(take(raw, len))) {
            ctx.flag(field, Flag.BLANK);
            ctx.message(name + CobolMessages.FIELD_REQUIRED_SUFFIX);
        }
    }

    private boolean editAlphaRequired(Ctx ctx, String name, String raw, int len,
                                      String field) {
        String v = take(raw, len);
        if (blank(v)) {
            ctx.flag(field, Flag.BLANK);
            ctx.message(name + CobolMessages.FIELD_REQUIRED_SUFFIX);
            return false;
        }
        if (!v.matches("[A-Za-z ]+")) {
            ctx.flag(field, Flag.NOT_OK);
            ctx.message(name + CobolMessages.FIELD_ALPHA_SUFFIX);
            return false;
        }
        return true;
    }

    private void editAlphaOptional(Ctx ctx, String name, String raw, int len,
                                   String field) {
        String v = take(raw, len);
        if (!blank(v) && !v.matches("[A-Za-z ]+")) {
            ctx.flag(field, Flag.NOT_OK);
            ctx.message(name + CobolMessages.FIELD_ALPHA_SUFFIX);
        }
    }

    private boolean editNumRequired(Ctx ctx, String name, String raw, int len,
                                    String field) {
        String v = take(raw, len);
        if (blank(v)) {
            ctx.flag(field, Flag.BLANK);
            ctx.message(name + CobolMessages.FIELD_REQUIRED_SUFFIX);
            return false;
        }
        if (!v.matches("\\d{" + len + "}")) {
            ctx.flag(field, Flag.NOT_OK);
            ctx.message(name + " must be all numeric.");
            return false;
        }
        if (Long.parseLong(v) == 0) {
            ctx.flag(field, Flag.NOT_OK);
            ctx.message(name + " must not be zero.");
            return false;
        }
        return true;
    }

    private void editYesNo(Ctx ctx, String name, String raw, String field) {
        String v = take(raw, 1);
        // 1220-EDIT-YESNO treats ZEROS as blank too.
        if (blank(v) || v.equals("0")) {
            ctx.flag(field, Flag.BLANK);
            ctx.message(name + CobolMessages.FIELD_REQUIRED_SUFFIX);
            return;
        }
        if (!v.equals("Y") && !v.equals("N")) {
            ctx.flag(field, Flag.NOT_OK);
            ctx.message(name + " must be Y or N.");
        }
    }

    private void editMoney(Ctx ctx, String name, String raw, String field) {
        String v = take(raw, 15);
        if (blank(v)) {
            ctx.flag(field, Flag.BLANK);
            ctx.message(name + CobolMessages.FIELD_REQUIRED_SUFFIX);
            return;
        }
        if (numvalC(v) == null) {
            ctx.flag(field, Flag.NOT_OK);
            ctx.message(name + CobolMessages.FIELD_NOT_VALID_SUFFIX);
        }
    }

    private void editDate(Ctx ctx, String name, String year, String month,
                          String day, String fy, String fm, String fd) {
        String y = take(year, 4);
        String m = take(month, 2);
        String d = take(day, 2);
        if (blank(y)) {
            ctx.flag(fy, Flag.BLANK);
            ctx.message(name + " : Year must be supplied.");
        } else if (!y.matches("\\d{4}")) {
            ctx.flag(fy, Flag.NOT_OK);
            ctx.message(name + " must be 4 digit number.");
        } else if (!y.startsWith("19") && !y.startsWith("20")) {
            ctx.flag(fy, Flag.NOT_OK);
            ctx.message(name + " : Century is not valid.");
        }
        if (blank(m)) {
            ctx.flag(fm, Flag.BLANK);
            ctx.message(name + " : Month must be supplied.");
        } else if (!validMonth(m)) {
            ctx.flag(fm, Flag.NOT_OK);
            ctx.message(name + ": Month must be a number between 1 and 12.");
        }
        if (blank(d)) {
            ctx.flag(fd, Flag.BLANK);
            ctx.message(name + " : Day must be supplied.");
        } else if (!d.matches("\\d{2}") || Integer.parseInt(d) < 1
                || Integer.parseInt(d) > 31) {
            ctx.flag(fd, Flag.NOT_OK);
            ctx.message(name + ":day must be a number between 1 and 31.");
        }
        // Cross-field edits need the raw 88 conditions to hold: a numeric
        // month and a numeric day.
        if (!validMonth(m) || !d.matches("\\d{2}")) {
            return;
        }
        int dd = Integer.parseInt(d);
        if (!MONTHS_31.contains(m) && dd == 31) {
            ctx.flag(fd, Flag.NOT_OK);
            ctx.flag(fm, Flag.NOT_OK);
            ctx.message(name + ":Cannot have 31 days in this month.");
            return;
        }
        if (m.equals("02") && dd == 30) {
            ctx.flag(fd, Flag.NOT_OK);
            ctx.flag(fm, Flag.NOT_OK);
            ctx.message(name + ":Cannot have 30 days in this month.");
            return;
        }
        if (m.equals("02") && dd == 29 && y.matches("\\d{4}") && !isLeapYear(y)) {
            ctx.flag(fd, Flag.NOT_OK);
            ctx.flag(fm, Flag.NOT_OK);
            ctx.flag(fy, Flag.NOT_OK);
            ctx.message(name + ":Not a leap year.Cannot have 29 days in this month.");
        }
    }

    private static boolean validMonth(String m) {
        return m.matches("\\d{2}") && Integer.parseInt(m) >= 1
                && Integer.parseInt(m) <= 12;
    }

    private void editSsn(Ctx ctx, AccountUpdateForm f) {
        if (editNumRequired(ctx, "SSN: First 3 chars", f.ssn1(), 3, "actssn1")) {
            int part1 = Integer.parseInt(take(f.ssn1(), 3));
            if (part1 == 666 || (part1 >= 900 && part1 <= 999)) {
                ctx.flag("actssn1", Flag.NOT_OK);
                ctx.message("SSN: First 3 chars: should not be 000, 666,"
                        + " or between 900 and 999");
            }
        }
        editNumRequired(ctx, "SSN 4th & 5th chars", f.ssn2(), 2, "actssn2");
        editNumRequired(ctx, "SSN Last 4 chars", f.ssn3(), 4, "actssn3");
    }

    private void editDob(Ctx ctx, AccountUpdateForm f) {
        editDate(ctx, "Date of Birth", f.dobYear(), f.dobMon(), f.dobDay(),
                "dobyear", "dobmon", "dobday");
        if (!ctx.ok("dobyear") || !ctx.ok("dobmon") || !ctx.ok("dobday")) {
            return;
        }
        LocalDate dob = LocalDate.of(Integer.parseInt(take(f.dobYear(), 4)),
                Integer.parseInt(take(f.dobMon(), 2)),
                Integer.parseInt(take(f.dobDay(), 2)));
        if (!dob.isBefore(LocalDate.now())) {
            ctx.flag("dobyear", Flag.NOT_OK);
            ctx.flag("dobmon", Flag.NOT_OK);
            ctx.flag("dobday", Flag.NOT_OK);
            ctx.message("Date of Birth:cannot be in the future ");
        }
    }

    private void editFico(Ctx ctx, AccountUpdateForm f) {
        if (!editNumRequired(ctx, "FICO Score", f.ficoScore(), 3, "acstfco")) {
            return;
        }
        int score = Integer.parseInt(take(f.ficoScore(), 3));
        if (score < 300 || score > 850) {
            ctx.flag("acstfco", Flag.NOT_OK);
            ctx.message("FICO Score: should be between 300 and 850");
        }
    }

    private void editUsState(Ctx ctx, String state, String field) {
        if (!STATE_CODES.contains(take(state, 2))) {
            ctx.flag(field, Flag.NOT_OK);
            ctx.message("State: is not a valid state code");
        }
    }

    private void editPhone(Ctx ctx, String name, String rawA, String rawB,
                           String rawC, String fa, String fb, String fc) {
        String a = take(rawA, 3);
        String b = take(rawB, 3);
        String c = take(rawC, 4);
        // cbl:2236-2239 — the third clause reads NUMA (literal spaces) or
        // NUMC (low-values): blanked area+prefix with a typed line part does
        // not pass the all-blank test and runs the per-part edits below.
        // Tested on the raw receives — norm() collapses literal spaces to ""
        // before the space/low-value distinction could be seen.
        if (blank(rawA) && blank(rawB) && (literalSpaces(rawA) || lowValues(rawC))) {
            return;
        }
        if (blank(a)) {
            ctx.flag(fa, Flag.BLANK);
            ctx.message(name + ": Area code must be supplied.");
        } else if (!a.matches("\\d{3}")) {
            ctx.flag(fa, Flag.NOT_OK);
            ctx.message(name + ": Area code must be A 3 digit number.");
        } else if (Integer.parseInt(a) == 0) {
            ctx.flag(fa, Flag.NOT_OK);
            ctx.message(name + ": Area code cannot be zero");
        } else if (!AREA_CODES.contains(a)) {
            ctx.flag(fa, Flag.NOT_OK);
            ctx.message(name + ": Not valid North America general purpose area code");
        }
        if (blank(b)) {
            ctx.flag(fb, Flag.BLANK);
            ctx.message(name + ": Prefix code must be supplied.");
        } else if (!b.matches("\\d{3}")) {
            ctx.flag(fb, Flag.NOT_OK);
            ctx.message(name + ": Prefix code must be A 3 digit number.");
        } else if (Integer.parseInt(b) == 0) {
            ctx.flag(fb, Flag.NOT_OK);
            ctx.message(name + ": Prefix code cannot be zero");
        }
        if (blank(c)) {
            ctx.flag(fc, Flag.BLANK);
            ctx.message(name + ": Line number code must be supplied.");
        } else if (!c.matches("\\d{4}")) {
            ctx.flag(fc, Flag.NOT_OK);
            ctx.message(name + ": Line number code must be A 4 digit number.");
        } else if (Integer.parseInt(c) == 0) {
            ctx.flag(fc, Flag.NOT_OK);
            ctx.message(name + ": Line number code cannot be zero");
        }
    }

    /** PIC X `= SPACES`: literal space fill, distinct from blanked input. */
    private static boolean literalSpaces(String value) {
        return value != null && !value.isEmpty() && value.isBlank();
    }

    /** Blanked input arriving as LOW-VALUES: empty, or the '*' sentinel. */
    private static boolean lowValues(String value) {
        return value == null || value.isEmpty()
                || value.stripTrailing().equals("*");
    }

    private void editStateZip(Ctx ctx, String state, String zip) {
        String combo = take(state, 2) + take(zip, 2);
        if (!STATE_ZIP_COMBOS.contains(combo)) {
            ctx.flag("acsstte", Flag.NOT_OK);
            ctx.flag("acszipc", Flag.NOT_OK);
            ctx.message("Invalid zip code for state");
        }
    }
}
