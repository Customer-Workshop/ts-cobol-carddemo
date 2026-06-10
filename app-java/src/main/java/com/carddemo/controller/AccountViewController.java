package com.carddemo.controller;

import com.carddemo.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Replaces COACTVWC.cbl — GET account details (account + customer + cards via xref).
 */
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Tag(name = "Account View", description = "Account inquiry — migrated from COACTVWC / CICS transaction CAVW")
public class AccountViewController {

    private final AccountService accountService;

    @GetMapping("/{acctId}")
    @Operation(summary = "View account details with associated customer and card info")
    public ResponseEntity<Map<String, Object>> viewAccount(@PathVariable Long acctId) {
        return ResponseEntity.ok(accountService.getAccountDetails(acctId));
    }
}
