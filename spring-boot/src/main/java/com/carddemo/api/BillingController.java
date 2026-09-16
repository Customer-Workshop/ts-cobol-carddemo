package com.carddemo.api;

import com.carddemo.service.BillingService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/billing")
public class BillingController {
    private final BillingService service;

    public BillingController(BillingService service) {
        this.service = service;
    }

    @PostMapping("/payments")
    public BillPaymentResponse pay(@RequestBody BillPaymentRequest request) {
        return service.pay(request);
    }
}
