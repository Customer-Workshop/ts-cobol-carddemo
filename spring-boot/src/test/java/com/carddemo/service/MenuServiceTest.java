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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MenuServiceTest {
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
}
