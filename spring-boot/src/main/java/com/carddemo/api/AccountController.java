package com.carddemo.api;

import com.carddemo.service.AccountViewService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountViewService service;

    public AccountController(AccountViewService service) {
        this.service = service;
    }

    @GetMapping("/{acctId}")
    public AccountViewResponse view(@PathVariable String acctId) {
        return service.view(acctId);
    }
}
