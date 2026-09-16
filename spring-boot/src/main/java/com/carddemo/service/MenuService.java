package com.carddemo.service;

import com.carddemo.api.CobolApiException;
import com.carddemo.api.CobolMessages;
import com.carddemo.api.MenuOption;
import com.carddemo.api.MenuResponse;
import com.carddemo.api.MenuSelectRequest;
import com.carddemo.api.MenuSelectionResponse;
import com.carddemo.model.SecurityUser;
import com.carddemo.repository.SecurityUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class MenuService {
    private final SecurityUserRepository userRepository;
    private final List<MenuOption> mainOptions;
    private final List<MenuOption> adminOptions;

    @Autowired
    public MenuService(SecurityUserRepository userRepository) {
        this(userRepository, MAIN, ADMIN);
    }

    MenuService(SecurityUserRepository userRepository, List<MenuOption> mainOptions,
                List<MenuOption> adminOptions) {
        this.userRepository = userRepository;
        this.mainOptions = mainOptions;
        this.adminOptions = adminOptions;
    }

    private static final List<MenuOption> MAIN = List.of(
            option(1, "Account View", "COACTVWC", "/api/accounts/{acctId}", "U", true),
            option(2, "Account Update", "COACTUPC", "/api/accounts/{accountId}", "U", true),
            option(3, "Credit Card List", "COCRDLIC", "/api/cards", "U", true),
            option(4, "Credit Card View", "COCRDSLC", "/api/cards/{cardNumber}", "U", true),
            option(5, "Credit Card Update", "COCRDUPC", "/api/cards/{cardNumber}", "U", true),
            option(6, "Transaction List", "COTRN00C", "/api/transactions", "U", true),
            option(7, "Transaction View", "COTRN01C", "/api/transactions/{transactionId}", "U", true),
            option(8, "Transaction Add", "COTRN02C", "/api/transactions", "U", true),
            option(9, "Transaction Reports", "CORPT00C", "/api/reports", "U", true),
            option(10, "Bill Payment", "COBIL00C", "/api/billing/payments", "U", true),
            option(11, "Pending Authorization View", "COPAUS0C", "/api/pending-auth/{acctId}", "U", true));

    private static final List<MenuOption> ADMIN = List.of(
            option(1, "User List (Security)", "COUSR00C", "/api/admin/users", "A", true),
            option(2, "User Add (Security)", "COUSR01C", "/api/admin/users", "A", true),
            option(3, "User Update (Security)", "COUSR02C", "/api/admin/users/{userId}", "A", true),
            option(4, "User Delete (Security)", "COUSR03C", "/api/admin/users/{userId}", "A", true),
            option(5, "Transaction Type List/Update (Db2)", "COTRTLIC", "/api/programs/COTRTLIC", "A", true),
            option(6, "Transaction Type Maintenance (Db2)", "COTRTUPC", "/api/programs/COTRTUPC", "A", true));

    public MenuResponse mainMenu(Authentication authentication) {
        return new MenuResponse("COMEN01C", authorize(mainOptions, authentication));
    }

    public MenuResponse adminMenu() {
        return new MenuResponse("COADM01C", adminOptions);
    }

    public MenuSelectionResponse selectMain(MenuSelectRequest request, Authentication authentication) {
        MenuOption option = select(request.option(), mainOptions);
        requireAccess(option, authentication);
        return selection(option);
    }

    public MenuSelectionResponse selectAdmin(MenuSelectRequest request) {
        return selection(select(request.option(), adminOptions));
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
        String message;
        if (!option.implemented()) {
            message = CobolMessages.optionNotInstalled(option.name());
        } else if (!option.available()) {
            message = CobolMessages.optionComingSoon(option.name());
        } else {
            message = null;
        }
        return new MenuSelectionResponse(option.number(), option.name(), option.program(),
                option.endpoint(), option.implemented(), option.available(), message);
    }

    // S01-B1 route registry for the web surface: a UI route exists only where
    // the option's target answers GET on a concrete (non-templated) path
    // today. Everything else falls back to the not-installed idiom instead of
    // a dead link.
    private static final Map<String, String> UI_ROUTES = Map.ofEntries(
            Map.entry("COCRDLIC", "/cards/list"),
            Map.entry("COTRN00C", "/transactions/list"),
            Map.entry("COTRN01C", "/transactions/view"),
            Map.entry("COTRN02C", "/transactions/add"),
            Map.entry("COUSR00C", "/admin/users"),
            Map.entry("COUSR01C", "/admin/users/add"),
            Map.entry("COUSR02C", "/admin/users/update"),
            Map.entry("COUSR03C", "/admin/users/delete"),
            Map.entry("COCRDSLC", "/cards/view"),
            Map.entry("COCRDUPC", "/cards/update"),
            Map.entry("COBIL00C", "/bill-payment"),
            Map.entry("CORPT00C", "/reports"),
            Map.entry("COPAUS0C", "/ui/pending-auth"),
            Map.entry("COACTUPC", "/accounts/update"),
            Map.entry("COACTVWC", "/accounts/view"),
            Map.entry("COTRTLIC", "/ui/tran-types"),
            Map.entry("COTRTUPC", "/ui/tran-types/maint"));

    public String uiRoute(MenuSelectionResponse selection) {
        if (!selection.implemented() || !selection.available()) {
            return null;
        }
        return UI_ROUTES.get(selection.program());
    }

    // S04-B1/S07-B1 — cross-program hand-offs (XCTL, row selection) resolve
    // a target's UI route and catalogue name through this registry; a
    // missing route is the disabled target, handled by the caller with the
    // coming-soon idiom instead of a dead link.
    public String uiRouteForProgram(String program) {
        return UI_ROUTES.get(program);
    }

    public String optionNameForProgram(String program) {
        return mainOptions.stream()
                .filter(option -> option.program().equals(program))
                .findFirst()
                .map(MenuOption::name)
                .orElse(program);
    }

    public String programName(String program) {
        return java.util.stream.Stream.concat(mainOptions.stream(), adminOptions.stream())
                .filter(option -> program.equals(option.program()))
                .map(MenuOption::name)
                .findFirst()
                .orElse(program);
    }

    private List<MenuOption> authorize(List<MenuOption> options, Authentication authentication) {
        String userType = userType(authentication);
        return options.stream()
                .map(option -> new MenuOption(option.number(), option.name(), option.program(),
                        option.endpoint(), option.requiredUserType(), option.implemented(),
                        canAccess(option, userType)))
                .toList();
    }

    private void requireAccess(MenuOption option, Authentication authentication) {
        if (!canAccess(option, userType(authentication))) {
            throw new CobolApiException(HttpStatus.FORBIDDEN, CobolMessages.ADMIN_ONLY);
        }
    }

    private String userType(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return "";
        }
        return userRepository.findById(authentication.getName())
                .map(SecurityUser::getUserType)
                .orElse("");
    }

    private boolean canAccess(MenuOption option, String userType) {
        return !"A".equals(option.requiredUserType()) || "A".equals(userType)
                || "U".equals(option.requiredUserType()) && "U".equals(userType);
    }

    private static MenuOption option(int number, String name, String program, String endpoint,
                                     String requiredUserType, boolean implemented) {
        return new MenuOption(number, name, program, endpoint, requiredUserType, implemented, true);
    }
}
