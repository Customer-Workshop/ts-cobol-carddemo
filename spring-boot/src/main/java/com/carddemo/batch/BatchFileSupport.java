package com.carddemo.batch;

import com.carddemo.data.CobolFieldReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class BatchFileSupport {
    private BatchFileSupport() {
    }

    static List<DailyTransactionRecord> dailyTransactions(Path path) throws IOException {
        return CobolFieldReader.splitRecords(Files.readString(path), 350).stream()
                .filter(record -> !record.isBlank())
                .map(DailyTransactionRecord::parse)
                .toList();
    }

    static String pad(String value, int width) {
        String text = value == null ? "" : value;
        if (text.length() >= width) {
            return text.substring(0, width);
        }
        return text + " ".repeat(width - text.length());
    }
}
