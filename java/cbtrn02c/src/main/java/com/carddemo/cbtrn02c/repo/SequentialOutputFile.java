package com.carddemo.cbtrn02c.repo;

import java.util.ArrayList;
import java.util.List;

public class SequentialOutputFile<T> {
    private final List<T> records;

    public SequentialOutputFile() {
        this(new ArrayList<>());
    }

    public SequentialOutputFile(List<T> records) {
        this.records = records;
    }

    public void write(T record) {
        records.add(record);
    }

    public List<T> records() {
        return List.copyOf(records);
    }

    public int size() {
        return records.size();
    }
}
