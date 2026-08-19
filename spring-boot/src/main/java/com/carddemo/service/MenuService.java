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
            option(2, "Account Update", "COACTUPC", "/api/programs/COACTUPC", "U", false),
            option(3, "Credit Card List", "COCRDLIC", "/api/programs/COCRDLIC", "U", false),
            option(4, "Credit Card View", "COCRDSLC", "/api/programs/COCRDSLC", "U", false),
            option(5, "Credit Card Update", "COCRDUPC", "/api/programs/COCRDUPC", "U", false),
            option(6, "Transaction List", "COTRN00C", "/api/programs/COTRN00C", "U", false),
            option(7, "Transaction View", "COTRN01C", "/api/programs/COTRN01C", "U", false),
            option(8, "Transaction Add", "COTRN02C", "/api/programs/COTRN02C", "U", false),
            option(9, "Transaction Reports", "CORPT00C", "/api/programs/CORPT00C", "U", false),
            option(10, "Bill Payment", "COBIL00C", "/api/programs/COBIL00C", "U", false),
            option(11, "Pending Authorization View", "COPAUS0C", "/api/programs/COPAUS0C", "U", false));

    private static final List<MenuOption> ADMIN = List.of(
            option(1, "User List (Security)", "COUSR00C", "/api/programs/COUSR00C", "A", false),
            option(2, "User Add (Security)", "COUSR01C", "/api/programs/COUSR01C", "A", false),
            option(3, "User Update (Security)", "COUSR02C", "/api/programs/COUSR02C", "A", false),
            option(4, "User Delete (Security)", "COUSR03C", "/api/programs/COUSR03C", "A", false),
            option(5, "Transaction Type List/Update (Db2)", "COTRTLIC", "/api/programs/COTRTLIC", "A", false),
            option(6, "Transaction Type Maintenance (Db2)", "COTRTUPC", "/api/programs/COTRTUPC", "A", false));

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
