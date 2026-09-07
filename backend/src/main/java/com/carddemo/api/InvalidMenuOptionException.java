package com.carddemo.api;

import org.springframework.http.HttpStatus;

/**
 * E-08 branch of PROCESS-ENTER-KEY (COMEN01C.cbl:127-134): the option is blank/zero, non-numeric or
 * greater than CDEMO-MENU-OPT-COUNT. Carries the normalised echo (:125) alongside the verbatim text.
 */
public class InvalidMenuOptionException extends CobolApiException {

    private final String normalizedOption;

    public InvalidMenuOptionException(String normalizedOption) {
        super(HttpStatus.BAD_REQUEST, CobolMessages.INVALID_OPTION);
        this.normalizedOption = normalizedOption;
    }

    public String normalizedOption() {
        return normalizedOption;
    }
}
