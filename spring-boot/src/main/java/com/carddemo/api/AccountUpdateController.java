package com.carddemo.api;

import com.carddemo.service.AccountUpdateService;
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

    @PutMapping("/{accountId}")
    public AccountViewResponse update(@PathVariable String accountId,
                                      @RequestBody AccountUpdateRequest request) {
        return service.update(accountId, request);
    }
}
