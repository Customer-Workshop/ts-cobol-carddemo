package com.carddemo.api;

/**
 * One row of the static menu route table (B-0033 / D-0023), ported from the COBOL constant table
 * {@code CARDDEMO-MAIN-MENU-OPTIONS} (app/cpy/COMEN02Y.cpy:19-98, 11 entries in an OCCURS 12).
 *
 * <p>{@code number} is CDEMO-MENU-OPT-NUM 9(02), {@code name} is CDEMO-MENU-OPT-NAME X(35) with
 * the trailing spaces trimmed, {@code program} is CDEMO-MENU-OPT-PGMNAME X(08) (the XCTL target of
 * COMEN01C.cbl:185), {@code userType} is CDEMO-MENU-OPT-USRTYPE X(01). {@code endpoint} and
 * {@code route} are the target-side replacements of the XCTL and are {@code null} while the option
 * is not migrated; {@code implemented} is the strangler switch each later stream flips.
 */
public record MenuOption(
        int number,
        String name,
        String program,
        String endpoint,
        String route,
        boolean implemented,
        String userType) {

    /** {@code nn. name} exactly as BUILD-MENU-OPTIONS composes it (COMEN01C.cbl:269-272). */
    public String label() {
        return String.format("%02d", number) + ". " + name;
    }
}
