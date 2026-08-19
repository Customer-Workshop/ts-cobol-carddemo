package com.carddemo.batch;

import com.carddemo.data.CobolFieldReader;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record DailyTransactionRecord(
        String id, String typeCode, int categoryCode, String source, String description,
        BigDecimal amount, Long merchantId, String merchantName, String merchantCity,
        String merchantZip, String cardNumber, LocalDateTime originTimestamp,
        LocalDateTime processTimestamp, String raw) {

    public static DailyTransactionRecord parse(String raw) {
        String origin = CobolFieldReader.text(raw, 278, 26);
        String process = CobolFieldReader.text(raw, 304, 26);
        return new DailyTransactionRecord(
                CobolFieldReader.requiredText(raw, 0, 16, "dailytran", 0),
                CobolFieldReader.requiredText(raw, 16, 2, "dailytran", 0),
                Integer.parseInt(CobolFieldReader.requiredText(raw, 18, 4, "dailytran", 0)),
                CobolFieldReader.text(raw, 22, 10),
                CobolFieldReader.text(raw, 32, 100),
                CobolFieldReader.signedDecimal(raw, 132, 9, 2),
                CobolFieldReader.optionalUnsignedLong(raw, 143, 9),
                CobolFieldReader.text(raw, 152, 50),
                CobolFieldReader.text(raw, 202, 50),
                CobolFieldReader.text(raw, 252, 10),
                CobolFieldReader.requiredText(raw, 262, 16, "dailytran", 0),
                parseTimestamp(origin, raw),
                parseTimestamp(process),
                raw);
    }

    private static LocalDateTime parseTimestamp(String value) {
        return parseTimestamp(value, value);
    }

    private static LocalDateTime parseTimestamp(String value, String source) {
        if (value == null || value.length() < 19) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.substring(0, 19).replace(' ', 'T'));
        } catch (RuntimeException ignored) {
            Matcher matcher = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})[ T](\\d{2}:\\d{2}:\\d{2})")
                    .matcher(source);
            return matcher.find()
                    ? LocalDateTime.parse(matcher.group(1) + "T" + matcher.group(2))
                    : null;
        }
    }
}
