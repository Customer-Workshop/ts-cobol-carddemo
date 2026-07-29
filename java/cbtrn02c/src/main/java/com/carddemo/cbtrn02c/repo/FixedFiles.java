package com.carddemo.cbtrn02c.repo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

public final class FixedFiles {
    private FixedFiles() {
    }

    public static <T> List<T> readSequential(
            Path path,
            Function<String, T> parser) throws IOException {
        if (!Files.exists(path)) {
            return new ArrayList<>();
        }
        List<T> records = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    records.add(parser.apply(line));
                }
            }
        }
        return records;
    }

    public static <T> TreeMap<String, T> readMap(
            Path path,
            Function<String, T> parser,
            Function<T, String> keyExtractor) throws IOException {
        TreeMap<String, T> records = new TreeMap<>();
        for (T record : readSequential(path, parser)) {
            records.put(keyExtractor.apply(record), record);
        }
        return records;
    }

    public static <T> void writeSequential(
            Path path,
            List<T> records,
            Function<T, String> formatter) throws IOException {
        createParent(path);
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            for (T record : records) {
                writer.write(formatter.apply(record));
                writer.newLine();
            }
        }
    }

    public static <T> void writeMap(
            Path path,
            Map<String, T> records,
            Function<T, String> formatter) throws IOException {
        writeSequential(path, new ArrayList<>(records.values()), formatter);
    }

    private static void createParent(Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
    }
}
