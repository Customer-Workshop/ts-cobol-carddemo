package com.carddemo.api;

import com.carddemo.service.MenuService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class MenuController {
    private final MenuService service;

    public MenuController(MenuService service) {
        this.service = service;
    }

    @GetMapping("/menu")
    public MenuResponse menu(Authentication authentication) {
        return service.mainMenu(authentication);
    }

    @PostMapping("/menu/select")
    public MenuSelectionResponse select(@Valid @RequestBody MenuSelectRequest request,
                                        Authentication authentication) {
        return service.selectMain(request, authentication);
    }

    @GetMapping("/admin/menu")
    public MenuResponse adminMenu() {
        return service.adminMenu();
    }

    @PostMapping("/admin/menu/select")
    public MenuSelectionResponse selectAdmin(@Valid @RequestBody MenuSelectRequest request) {
        return service.selectAdmin(request);
    }
}
