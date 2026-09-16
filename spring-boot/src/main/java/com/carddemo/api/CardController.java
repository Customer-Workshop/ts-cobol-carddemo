package com.carddemo.api;

import com.carddemo.service.CardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cards")
public class CardController {
    private final CardService service;

    public CardController(CardService service) {
        this.service = service;
    }

    @GetMapping
    public CardListResponse list(@RequestParam(required = false) String accountId,
                                 @RequestParam(required = false) String cardNumber,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "forward") String direction) {
        return service.list(accountId, cardNumber, page, direction);
    }

    @GetMapping("/{cardNumber}")
    public CardResponse detail(@PathVariable String cardNumber,
                               @RequestParam(required = false) String accountId) {
        return service.detail(accountId, cardNumber);
    }

    @PutMapping("/{cardNumber}")
    public CardResponse update(@PathVariable String cardNumber,
                               @RequestParam String accountId,
                               @RequestBody CardUpdateRequest request) {
        return service.update(accountId, cardNumber, request);
    }
}
