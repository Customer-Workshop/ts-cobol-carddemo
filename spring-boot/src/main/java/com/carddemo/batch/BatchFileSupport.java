package com.carddemo.batch;

final class BatchFileSupport {
    private BatchFileSupport() {
    }

    static String pad(String value, int width) {
        String text = value == null ? "" : value;
        if (text.length() >= width) {
            return text.substring(0, width);
        }
        return text + " ".repeat(width - text.length());
    }
}
