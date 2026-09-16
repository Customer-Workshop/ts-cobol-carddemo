package com.carddemo.api;

import com.carddemo.model.PendingAuthDetail;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * The PAUT9CTS composite key on the wire: {@code date9c:time9c} — the two
 * 9's-complement key fields, URL-safe (S19-B3). Also derives the real
 * (uncomplemented) timestamp AUTHFRDS uses: date from PA-AUTH-ORIG-DATE
 * YYMMDD, time from 999999999 - PA-AUTH-TIME-9C (COPAUS2C.cbl:101-110).
 */
public record PendingAuthKey(int date9c, int time9c) {

    public static PendingAuthKey of(PendingAuthDetail detail) {
        return new PendingAuthKey(detail.getId().getAuthDate9c(), detail.getId().getAuthTime9c());
    }

    public static PendingAuthKey parse(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        String[] parts = encoded.split(":", -1);
        if (parts.length != 2) {
            return null;
        }
        try {
            return new PendingAuthKey(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public String encoded() {
        return date9c + ":" + time9c;
    }

    /** Real auth timestamp: 20YY-MM-DD orig date + HH:MM:SS.mmm real time. */
    public static LocalDateTime realTimestamp(PendingAuthDetail detail) {
        String orig = detail.getAuthOrigDate();
        if (orig == null || orig.length() < 6 || detail.getId().getAuthTime9c() == null) {
            return null;
        }
        int year = 2000 + Integer.parseInt(orig.substring(0, 2));
        int month = Integer.parseInt(orig.substring(2, 4));
        int day = Integer.parseInt(orig.substring(4, 6));
        int time = 999_999_999 - detail.getId().getAuthTime9c();
        String digits = "%09d".formatted(time);
        int hour = Integer.parseInt(digits.substring(0, 2));
        int minute = Integer.parseInt(digits.substring(2, 4));
        int second = Integer.parseInt(digits.substring(4, 6));
        int millis = Integer.parseInt(digits.substring(6, 9));
        return LocalDateTime.of(LocalDate.of(year, month, day),
                LocalTime.of(hour, minute, second, millis * 1_000_000));
    }
}
