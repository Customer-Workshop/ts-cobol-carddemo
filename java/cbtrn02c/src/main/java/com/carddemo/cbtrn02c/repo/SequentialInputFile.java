package com.carddemo.cbtrn02c.repo;

import java.util.List;

public class SequentialInputFile<T> {
    private final List<T> records;
    private int nextIndex;

    public SequentialInputFile(List<T> records) {
        this.records = records;
    }

    public T readNext() {
        if (nextIndex >= records.size()) {
            return null;
        }
        return records.get(nextIndex++);
    }

    public boolean eof() {
        return nextIndex >= records.size();
    }

    public int size() {
        return records.size();
    }
}
