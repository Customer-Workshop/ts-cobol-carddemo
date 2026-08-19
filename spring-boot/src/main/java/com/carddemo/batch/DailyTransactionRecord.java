package com.carddemo.batch;

import com.carddemo.data.CobolFieldReader;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
                parseTimestamp(origin),
                parseTimestamp(process),
                raw);
    }

    private static LocalDateTime parseTimestamp(String value) {
        if (value == null || value.length() < 19) {
            return null;
        }
        return LocalDateTime.parse(value.substring(0, 19).replace(' ', 'T'));
    }
}
