package com.carddemo.service;

import com.carddemo.api.CobolApiException;
import com.carddemo.api.CobolMessages;
import com.carddemo.api.MenuOption;
import com.carddemo.api.MenuResponse;
import com.carddemo.api.MenuSelectRequest;
import com.carddemo.api.MenuSelectionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MenuService {
    private static final List<MenuOption> MAIN = List.of(
            option(1, "Account View", "COACTVWC", "/api/accounts/{acctId}", true),
            option(2, "Account Update", "COACTUPC", "/api/programs/COACTUPC", false),
            option(3, "Credit Card List", "COCRDLIC", "/api/programs/COCRDLIC", false),
            option(4, "Credit Card View", "COCRDSLC", "/api/programs/COCRDSLC", false),
            option(5, "Credit Card Update", "COCRDUPC", "/api/programs/COCRDUPC", false),
            option(6, "Transaction List", "COTRN00C", "/api/programs/COTRN00C", false),
            option(7, "Transaction View", "COTRN01C", "/api/programs/COTRN01C", false),
            option(8, "Transaction Add", "COTRN02C", "/api/programs/COTRN02C", false),
            option(9, "Transaction Reports", "CORPT00C", "/api/programs/CORPT00C", false),
            option(10, "Bill Payment", "COBIL00C", "/api/programs/COBIL00C", false),
            option(11, "Pending Authorization View", "COPAUS0C", "/api/programs/COPAUS0C", false));

    private static final List<MenuOption> ADMIN = List.of(
            option(1, "User List (Security)", "COUSR00C", "/api/programs/COUSR00C", false),
            option(2, "User Add (Security)", "COUSR01C", "/api/programs/COUSR01C", false),
            option(3, "User Update (Security)", "COUSR02C", "/api/programs/COUSR02C", false),
            option(4, "User Delete (Security)", "COUSR03C", "/api/programs/COUSR03C", false),
            option(5, "Transaction Type List/Update (Db2)", "COTRTLIC", "/api/programs/COTRTLIC", false),
            option(6, "Transaction Type Maintenance (Db2)", "COTRTUPC", "/api/programs/COTRTUPC", false));

    public MenuResponse mainMenu(Authentication authentication) {
        return new MenuResponse("COMEN01C", MAIN);
    }

    public MenuResponse adminMenu() {
        return new MenuResponse("COADM01C", ADMIN);
    }

    public MenuSelectionResponse selectMain(MenuSelectRequest request, Authentication authentication) {
        MenuOption option = select(request.option(), MAIN);
        return selection(option);
    }

    public MenuSelectionResponse selectAdmin(MenuSelectRequest request) {
        return selection(select(request.option(), ADMIN));
    }

    private MenuOption select(String rawOption, List<MenuOption> menu) {
        if (rawOption == null || !rawOption.trim().matches("\\d+")) {
            throw new CobolApiException(HttpStatus.BAD_REQUEST, CobolMessages.INVALID_OPTION);
        }
        int number;
        try {
            number = Integer.parseInt(rawOption.trim());
        } catch (NumberFormatException exception) {
            throw new CobolApiException(HttpStatus.BAD_REQUEST, CobolMessages.INVALID_OPTION);
        }
        if (number <= 0 || number > menu.size()) {
            throw new CobolApiException(HttpStatus.BAD_REQUEST, CobolMessages.INVALID_OPTION);
        }
        return menu.get(number - 1);
    }

    private MenuSelectionResponse selection(MenuOption option) {
        String message = option.implemented()
                ? null
                : ("COPAUS0C".equals(option.program())
                ? CobolMessages.optionNotInstalled(option.name())
                : CobolMessages.optionComingSoon(option.name()));
        return new MenuSelectionResponse(option.number(), option.name(), option.program(),
                option.endpoint(), option.implemented(), message);
    }

    private static MenuOption option(int number, String name, String program, String endpoint,
                                     boolean implemented) {
        return new MenuOption(number, name, program, endpoint, implemented, true);
    }
}
