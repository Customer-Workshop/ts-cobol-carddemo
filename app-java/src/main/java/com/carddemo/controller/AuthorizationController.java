package com.carddemo.controller;

import com.carddemo.service.AuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Replaces COPAUA0C.cbl authorization decision — POST authorize endpoint.
 */
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Tag(name = "Authorization", description = "Card authorization — migrated from COPAUA0C / IMS-DB2-MQ")
public class AuthorizationController {

    private final AuthorizationService authorizationService;

    @PostMapping("/{acctId}/authorize")
    @Operation(summary = "Authorize a transaction against available credit")
    public ResponseEntity<Map<String, Object>> authorize(
            @PathVariable Long acctId,
            @RequestBody Map<String, BigDecimal> request) {
        BigDecimal amount = request.get("transactionAmount");
        if (amount == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "transactionAmount is required"));
        }
        return ResponseEntity.ok(authorizationService.authorize(acctId, amount));
    }
}
