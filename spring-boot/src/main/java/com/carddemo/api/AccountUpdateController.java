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

    @PutMapping
    public AccountViewResponse update(@RequestBody AccountUpdateRequest request) {
        return service.update(request);
    }

    @PutMapping("/{accountId}")
    public AccountViewResponse update(@PathVariable String accountId,
                                      @RequestBody AccountUpdateRequest request) {
        return service.update(new AccountUpdateRequest(accountId, request.activeStatus(),
                request.currentBalance(), request.creditLimit(), request.cashCreditLimit(),
                request.openDate(), request.expirationDate(), request.reissueDate(),
                request.currentCycleCredit(), request.currentCycleDebit(), request.accountGroup(),
                request.customerId(), request.ssn(), request.dateOfBirth(), request.ficoScore(),
                request.firstName(), request.middleName(), request.lastName(), request.addressLine1(),
                request.addressLine2(), request.addressLine3(), request.stateCode(), request.zip(),
                request.countryCode(), request.phoneNumber1(), request.phoneNumber2(),
                request.governmentIssuedId(), request.eftAccountId(),
                request.primaryCardHolderIndicator(), request.expectedCurrentBalance(),
                request.expectedFirstName()));
    }
}
