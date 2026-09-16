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

    // COCRDLIC REST surface (S-04): the keyed browse is one call per AID
    // press; GET is the fresh entry and POST carries the echoed COMMAREA
    // pageState for every later key.
    @GetMapping
    public CardListResponse list(@RequestParam(required = false) String accountId,
                                 @RequestParam(required = false) String cardNumber) {
        return service.browse(new CardListRequest("ENTER", accountId, cardNumber, null, null));
    }

    @PostMapping("/list")
    public CardListResponse listAid(@RequestBody CardListRequest request) {
        return service.browse(request);
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

    // COCRDUPC REST surface (S-06): one call per AID press of the
    // lookup-then-update screen, commarea echoed verbatim. lookup covers
    // the search/fetch turns; validate covers the edit/confirm turns. The
    // write itself is still the PUT above.
    @PostMapping("/lookup")
    public CardUpdateScreen lookup(@RequestBody CardUpdateForm request) {
        return service.cardUpdate(request);
    }

    @PostMapping("/validate")
    public CardUpdateScreen validate(@RequestBody CardUpdateForm request) {
        return service.cardUpdate(request);
    }
}
