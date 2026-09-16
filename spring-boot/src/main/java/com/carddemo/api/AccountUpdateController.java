package com.carddemo.api;

import com.carddemo.service.AccountUpdateScreen;
import com.carddemo.service.AccountUpdateService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class AccountUpdateController {
    private final AccountUpdateService service;

    public AccountUpdateController(AccountUpdateService service) {
        this.service = service;
    }

    @PostMapping("/lookup")
    public AccountUpdateScreen lookup(@RequestBody AccountLookupRequest request) {
        return service.lookup(request.accountId());
    }

    @PostMapping("/validate")
    public AccountUpdateScreen validate(@RequestBody AccountUpdateRequest request) {
        return service.validate(request.original(), request.updated());
    }

    @PutMapping("/{accountId}")
    public AccountUpdateScreen update(@PathVariable String accountId,
                                      @RequestBody AccountUpdateRequest request) {
        try {
            return service.update(accountId, request);
        } catch (AccountUpdateService.ScreenRollbackException e) {
            return e.screen();
        }
    }
}
