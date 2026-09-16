package com.carddemo.service;

import com.carddemo.api.CobolApiException;
import com.carddemo.api.MenuOption;
import com.carddemo.api.MenuSelectRequest;
import com.carddemo.model.SecurityUser;
import com.carddemo.repository.SecurityUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MenuServiceTest {

    @Test
    void cataloguePinsComen02yAndCoadm02yVerbatim() {
        // Derived from app/cpy/COMEN02Y.cpy (11 rows) and COADM02Y.cpy (6 rows):
        // name, order, program and required user type must match the route
        // catalogues exactly.
        MenuService service = new MenuService(mock(SecurityUserRepository.class));

        assertThat(service.mainMenu(null).options()).containsExactly(
                new MenuOption(1, "Account View", "COACTVWC",
                        "/api/accounts/{acctId}", "U", true, true),
                new MenuOption(2, "Account Update", "COACTUPC",
                        "/api/accounts/{accountId}", "U", true, true),
                new MenuOption(3, "Credit Card List", "COCRDLIC",
                        "/api/cards", "U", true, true),
                new MenuOption(4, "Credit Card View", "COCRDSLC",
                        "/api/cards/{cardNumber}", "U", true, true),
                new MenuOption(5, "Credit Card Update", "COCRDUPC",
                        "/api/cards/{cardNumber}", "U", true, true),
                new MenuOption(6, "Transaction List", "COTRN00C",
                        "/api/transactions", "U", true, true),
                new MenuOption(7, "Transaction View", "COTRN01C",
                        "/api/transactions/{transactionId}", "U", true, true),
                new MenuOption(8, "Transaction Add", "COTRN02C",
                        "/api/transactions", "U", true, true),
                new MenuOption(9, "Transaction Reports", "CORPT00C",
                        "/api/reports", "U", true, true),
                new MenuOption(10, "Bill Payment", "COBIL00C",
                        "/api/billing/payments", "U", true, true),
                new MenuOption(11, "Pending Authorization View", "COPAUS0C",
                        "/api/programs/COPAUS0C", "U", false, true));

        assertThat(service.adminMenu().options()).containsExactly(
                new MenuOption(1, "User List (Security)", "COUSR00C",
                        "/api/admin/users", "A", true, true),
                new MenuOption(2, "User Add (Security)", "COUSR01C",
                        "/api/admin/users", "A", true, true),
                new MenuOption(3, "User Update (Security)", "COUSR02C",
                        "/api/admin/users/{userId}", "A", true, true),
                new MenuOption(4, "User Delete (Security)", "COUSR03C",
                        "/api/admin/users/{userId}", "A", true, true),
                new MenuOption(5, "Transaction Type List/Update (Db2)", "COTRTLIC",
                        "/api/programs/COTRTLIC", "A", false, true),
                new MenuOption(6, "Transaction Type Maintenance (Db2)", "COTRTUPC",
                        "/api/programs/COTRTUPC", "A", false, true));
    }

    @Test
    void placeholderOptionSelectionShowsComingSoon() {
        SecurityUserRepository repository = mock(SecurityUserRepository.class);
        SecurityUser user = new SecurityUser();
        user.setUserId("USER0001");
        user.setUserType("U");
        when(repository.findById("USER0001")).thenReturn(Optional.of(user));
        MenuOption placeholder = new MenuOption(1, "Future Feature", "DUMMY001",
                "/api/future", "U", true, false);
        MenuService service = new MenuService(repository, List.of(placeholder), List.of());
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "USER0001", null, List.of());

        var selection = service.selectMain(new MenuSelectRequest("1"), authentication);

        // COMEN01C.cbl:172-176 emits the name DELIMITED BY SPACE: only the
        // first word reaches the message.
        assertEquals("This option Futureis coming soon ...", selection.message());
        assertNull(service.uiRoute(selection));
    }

    @Test
    void uiRouteResolvesOnlyBrowsableEndpoints() {
        SecurityUserRepository repository = mock(SecurityUserRepository.class);
        SecurityUser admin = new SecurityUser();
        admin.setUserId("ADMIN001");
        admin.setUserType("A");
        when(repository.findById("ADMIN001")).thenReturn(Optional.of(admin));
        MenuService service = new MenuService(repository);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "ADMIN001", null, List.of());

        assertEquals("/cards/list", service.uiRoute(
                service.selectMain(new MenuSelectRequest("3"), authentication)));
        assertEquals("/transactions/list", service.uiRoute(
                service.selectMain(new MenuSelectRequest("6"), authentication)));
        assertEquals("/accounts/view", service.uiRoute(
                service.selectMain(new MenuSelectRequest("1"), authentication)));
        // POST-only or unimplemented endpoints have no browsable route today.
        assertNull(service.uiRoute(
                service.selectMain(new MenuSelectRequest("2"), authentication)));
        assertEquals("/bill-payment", service.uiRoute(
                service.selectMain(new MenuSelectRequest("10"), authentication)));
        assertNull(service.uiRoute(
                service.selectMain(new MenuSelectRequest("11"), authentication)));
        assertEquals("/api/admin/users", service.uiRoute(
                service.selectAdmin(new MenuSelectRequest("1"))));
        assertNull(service.uiRoute(
                service.selectAdmin(new MenuSelectRequest("5"))));
    }

    @Test
    void regularUserIsDeniedConfiguredAdminOnlyOption() {
        SecurityUserRepository repository = mock(SecurityUserRepository.class);
        SecurityUser user = new SecurityUser();
        user.setUserId("USER0001");
        user.setUserType("U");
        when(repository.findById("USER0001")).thenReturn(Optional.of(user));
        MenuOption adminOnly = new MenuOption(1, "Admin task", "COADMXXC",
                "/api/admin/task", "A", false, true);
        MenuService service = new MenuService(repository, List.of(adminOnly), List.of());
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "USER0001", null, List.of());

        CobolApiException exception = assertThrows(CobolApiException.class,
                () -> service.selectMain(new MenuSelectRequest("1"), authentication));

        assertEquals("No access - Admin Only option... ", exception.getMessage());
    }

    @Test
    void adminSelectionUsesInjectedOptions() {
        SecurityUserRepository repository = mock(SecurityUserRepository.class);
        MenuOption configured = new MenuOption(1, "Configured admin task", "CUSTOMADM",
                "/api/custom-admin", "A", true, true);
        MenuService service = new MenuService(repository, List.of(), List.of(configured));

        var selection = service.selectAdmin(new MenuSelectRequest("1"));

        assertEquals("CUSTOMADM", selection.program());
        assertEquals("/api/custom-admin", selection.endpoint());
    }
}
