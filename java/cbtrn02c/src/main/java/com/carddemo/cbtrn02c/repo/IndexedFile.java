package com.carddemo.cbtrn02c.repo;

import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

public class IndexedFile<K, V> {
    private final TreeMap<K, V> records;

    public IndexedFile() {
        this(new TreeMap<>());
    }

    public IndexedFile(Map<K, V> records) {
        this.records = new TreeMap<>(records);
    }

    public Optional<V> read(K key) {
        return Optional.ofNullable(records.get(key));
    }

    public void write(K key, V record) {
        if (records.containsKey(key)) {
            throw new IllegalStateException("Indexed record already exists: " + key);
        }
        records.put(key, record);
    }

    public void rewrite(K key, V record) {
        if (!records.containsKey(key)) {
            throw new IllegalStateException("Indexed record does not exist: " + key);
        }
        records.put(key, record);
    }

    public int size() {
        return records.size();
    }

    public NavigableMap<K, V> records() {
        return Collections.unmodifiableNavigableMap(new TreeMap<>(records));
    }
}
