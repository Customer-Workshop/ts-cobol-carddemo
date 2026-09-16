package com.carddemo.api;

import com.carddemo.service.TransactionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @GetMapping
    public TransactionListResponse list(
            @RequestParam(required = false) String transactionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "forward") String direction) {
        return service.list(transactionId, page, direction);
    }

    @GetMapping("/{transactionId}")
    public TransactionResponse detail(@PathVariable String transactionId) {
        return service.detail(transactionId);
    }

    /**
     * ENTER on COTRN02A. Business rejections stay HTTP 200 with the COBOL
     * message in the screen state — only structurally impossible input
     * (over-width, S09-B6) maps to 400.
     */
    @PostMapping
    public TransactionAddScreen add(@RequestBody TransactionCreateRequest request) {
        return service.enter(request);
    }

    /** PF5 on COTRN02A — COPY-LAST-TRAN-DATA then the ENTER tail. */
    @PostMapping("/copy-last")
    public TransactionAddScreen copyLast(@RequestBody TransactionCreateRequest request) {
        return service.copyLast(request);
    }
}
