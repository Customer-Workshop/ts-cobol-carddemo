package com.carddemo.cbact04c.io;

public final class FixedWidth {

    private FixedWidth() {
    }

    public static String pad(String value, int length) {
        String safeValue = value == null ? "" : value;
        if (safeValue.length() >= length) {
            return safeValue.substring(0, length);
        }
        return safeValue + " ".repeat(length - safeValue.length());
    }

    public static String at(String value, int start, int length) {
        return pad(value, start + length).substring(start, start + length);
    }
}
