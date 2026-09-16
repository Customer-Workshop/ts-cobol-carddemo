package com.carddemo.batch;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-step counters feeding the CBEXPORT/CBIMPORT finalize DISPLAY lines
 * (CBEXPORT.cbl:138-144 WS-EXPORT-STATISTICS, CBIMPORT.cbl:139-147 WS-IMPORT-STATISTICS).
 */
public class ImportExportStats {
    private final Map<String, Long> perType = new LinkedHashMap<>();
    private long total;
    private long errors;
    private long unknownTypes;

    public void countType(String type) {
        perType.merge(type, 1L, Long::sum);
    }

    public void countRecord() {
        total++;
    }

    public void countError(boolean unknownType) {
        errors++;
        if (unknownType) {
            unknownTypes++;
        }
    }

    public long of(String type) {
        return perType.getOrDefault(type, 0L);
    }

    public long total() {
        return total;
    }

    public long errors() {
        return errors;
    }

    public long unknownTypes() {
        return unknownTypes;
    }
}
