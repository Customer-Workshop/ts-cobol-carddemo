package com.carddemo.cbact04c.util;

import java.math.BigDecimal;

public final class CobolField {

    private CobolField() {
    }

    public static String text(String value, int length) {
        String safeValue = value == null ? "" : value;
        return (safeValue + " ".repeat(length)).substring(0, length);
    }

    public static String digits(String value, int length) {
        String safeValue = value == null ? "" : value;
        if (safeValue.length() > length) {
            safeValue = safeValue.substring(safeValue.length() - length);
        }
        return "0".repeat(length - safeValue.length()) + safeValue;
    }

    public static BigDecimal decimal(String value) {
        return ZonedDecimal.parse(value);
    }
}
