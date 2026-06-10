package com.carddemo.controller;

import com.carddemo.model.Account;
import com.carddemo.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces COACTUPC.cbl — PUT account updates with validation.
 * Uses JPA @Version optimistic locking instead of CICS record locking.
 */
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Tag(name = "Account Update", description = "Account update — migrated from COACTUPC / CICS transaction CAUP")
public class AccountUpdateController {

    private final AccountService accountService;

    @PutMapping("/{acctId}")
    @Operation(summary = "Update account fields (validates numeric/date constraints)")
    public ResponseEntity<Account> updateAccount(
            @PathVariable Long acctId,
            @RequestBody Account updates) {
        return ResponseEntity.ok(accountService.updateAccount(acctId, updates));
    }
}
