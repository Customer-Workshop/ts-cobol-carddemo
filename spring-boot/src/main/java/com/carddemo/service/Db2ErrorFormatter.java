package com.carddemo.service;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.UncategorizedSQLException;

/**
 * S21-B7 — CSDB2RPY.cpy 9999-FORMAT-DB2-MESSAGE port (:2057-2077):
 * FUNCTION TRIM(WS-DB2-CURRENT-ACTION) + ' SQLCODE:' + WS-DISP-SQLCODE
 * (PIC ----9) + ' ' + the formatted DSNTIAC text. SQLCODEs are mapped from
 * the Spring Data exception family: +100 row-miss, -911 lock timeout, -532
 * child-restrict, -803 duplicate key.
 */
public final class Db2ErrorFormatter {

    private Db2ErrorFormatter() {
    }

    public static String format(String action, int sqlcode, String detail) {
        String text = detail == null ? "" : detail.trim();
        return action.trim() + " SQLCODE:" + sqlcodeDisplay(sqlcode) + " " + text;
    }

    /** CSDB2RWY.cpy WS-DISP-SQLCODE PIC ----9: 5-wide, sign only on negative. */
    public static String sqlcodeDisplay(int sqlcode) {
        return "%5d".formatted(sqlcode);
    }

    public static int sqlcodeFor(DataAccessException exception) {
        if (exception instanceof CannotAcquireLockException
                || exception instanceof PessimisticLockingFailureException) {
            return -911;
        }
        if (exception instanceof DuplicateKeyException) {
            return -803;
        }
        if (exception instanceof DataIntegrityViolationException) {
            return -532;
        }
        if (exception instanceof UncategorizedSQLException uncategorized
                && uncategorized.getSQLException() != null) {
            return -Math.abs(uncategorized.getSQLException().getErrorCode());
        }
        return -1;
    }

    public static String forException(String action, DataAccessException exception) {
        return format(action, sqlcodeFor(exception),
                exception.getMostSpecificCause() == null
                        ? exception.getMessage()
                        : exception.getMostSpecificCause().getMessage());
    }
}
