package com.carddemo.api;

import java.util.List;

/**
 * The initial SEND MAP of COMEN1A (COMEN01C.cbl:208-220): the POPULATE-HEADER-INFO fields
 * (:238-257) and the 11 option labels BUILD-MENU-OPTIONS writes into OPTN001..OPTN011 (:262-303).
 */
public record MenuResponse(ScreenHeaderResponse header, List<MenuOption> options) {
}
