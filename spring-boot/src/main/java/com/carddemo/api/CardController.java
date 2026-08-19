package com.carddemo.api;

import com.carddemo.service.CardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping("/update")
    public CardResponse update(@RequestBody CardUpdateRequest request) {
        return service.update(request);
    }

    @PutMapping("/{cardNumber}")
    public CardResponse update(@PathVariable String cardNumber,
                               @RequestBody CardUpdateRequest request) {
        return service.update(new CardUpdateRequest(request.accountId(), cardNumber,
                request.embossedName(), request.activeStatus(), request.expiryMonth(),
                request.expiryYear(), request.originalEmbossedName(),
                request.originalActiveStatus(), request.originalExpiryMonth(),
                request.originalExpiryYear()));
    }
}
