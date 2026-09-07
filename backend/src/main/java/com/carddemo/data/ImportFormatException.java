package com.carddemo.data;

/** A legacy extract record that does not match its copybook layout; the import stops rather than guessing. */
public class ImportFormatException extends RuntimeException {

    public ImportFormatException(String sourceName, int recordNumber, int offset, String detail) {
        super(sourceName + " record " + recordNumber + " offset " + offset + ": " + detail);
    }
}
