package com.carddemo.data;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Q-10 date gate (D-0035): every PIC X(10) date value of the extracts must be a real YYYY-MM-DD
 * calendar date for the {@code DATE}/{@code LocalDate} mapping to hold. The importer records every
 * value it sees; a single offender is reported (and fails the import) instead of being silently nulled.
 */
public final class DateColumnGate {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);

    public record Offender(String column, int recordNumber, String value) {
    }

    private int valuesChecked;
    private final List<Offender> offenders = new ArrayList<>();

    /** Parses one date column value; {@code null} when the field is blank. */
    public LocalDate parse(String column, int recordNumber, String rawValue) {
        String value = rawValue == null ? null : rawValue.trim();
        valuesChecked++;
        if (value == null || value.isEmpty()) {
            offenders.add(new Offender(column, recordNumber, rawValue));
            return null;
        }
        try {
            return LocalDate.parse(value, ISO);
        } catch (DateTimeParseException notIso) {
            offenders.add(new Offender(column, recordNumber, rawValue));
            return null;
        }
    }

    public int valuesChecked() {
        return valuesChecked;
    }

    public List<Offender> offenders() {
        return Collections.unmodifiableList(offenders);
    }

    public boolean passed() {
        return offenders.isEmpty();
    }
}
