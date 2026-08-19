package com.carddemo.service;

import com.carddemo.api.CardListResponse;
import com.carddemo.api.CardListRow;
import com.carddemo.api.CardResponse;
import com.carddemo.api.CardUpdateRequest;
import com.carddemo.api.CobolApiException;
import com.carddemo.api.CobolMessages;
import com.carddemo.model.Card;
import com.carddemo.repository.CardRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class CardService {
    public static final int COBOL_PAGE_SIZE = 7;

    private final CardRepository cardRepository;

    public CardService(CardRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    public CardListResponse list(String rawAccountId, String rawCardNumber,
                                 int page, String direction) {
        Long accountId = validateAccount(rawAccountId, false);
        String cardNumber = validateCard(rawCardNumber, false);
        if (page < 0) {
            throw invalid(CobolMessages.INVALID_OPTION);
        }
        List<Card> cards = cardRepository.findAll().stream()
                .filter(card -> accountId == null || accountId.equals(card.getCardAcctId()))
                .filter(card -> cardNumber == null || cardNumber.equals(card.getCardNumber()))
                .sorted(Comparator.comparing(Card::getCardNumber))
                .toList();
        if ("backward".equalsIgnoreCase(direction)) {
            cards = cards.reversed();
        } else if (direction != null && !"forward".equalsIgnoreCase(direction)
                && !direction.isBlank()) {
            throw invalid(CobolMessages.INVALID_OPTION);
        }
        int from = page * COBOL_PAGE_SIZE;
        if (from > cards.size()) {
            throw new CobolApiException(HttpStatus.NOT_FOUND, CobolMessages.CARD_COMBINATION_NOT_FOUND);
        }
        int to = Math.min(from + COBOL_PAGE_SIZE, cards.size());
        List<CardListRow> rows = cards.subList(from, to).stream()
                .map(card -> new CardListRow("S", "U", card.getCardAcctId(), card.getCardNumber(),
                        card.getCardActiveStatus(), "/api/cards/" + card.getCardNumber(),
                        "/api/cards/" + card.getCardNumber()))
                .toList();
        return new CardListResponse(page, COBOL_PAGE_SIZE, to < cards.size(), page > 0, rows);
    }

    public CardResponse detail(String rawAccountId, String rawCardNumber) {
        Long accountId = validateAccount(rawAccountId, false);
        String cardNumber = validateCard(rawCardNumber, false);
        if (accountId == null && cardNumber == null) {
            throw new CobolApiException(HttpStatus.BAD_REQUEST, CobolMessages.NO_CHANGES_DETECTED);
        }
        Card card;
        if (cardNumber != null) {
            card = cardRepository.findById(cardNumber).orElseThrow(
                    () -> new CobolApiException(HttpStatus.NOT_FOUND,
                            CobolMessages.CARD_COMBINATION_NOT_FOUND));
            if (accountId != null && !accountId.equals(card.getCardAcctId())) {
                throw new CobolApiException(HttpStatus.NOT_FOUND,
                        CobolMessages.CARD_COMBINATION_NOT_FOUND);
            }
        } else {
            card = cardRepository.findByCardAcctId(accountId).stream().findFirst()
                    .orElseThrow(() -> new CobolApiException(HttpStatus.NOT_FOUND,
                            CobolMessages.CARD_ACCOUNT_NOT_FOUND));
        }
        return response(card);
    }

    @Transactional
    public CardResponse update(CardUpdateRequest request) {
        Long accountId = validateAccount(request.accountId(), true);
        String cardNumber = validateCard(request.cardNumber(), true);
        Card card = cardRepository.findById(cardNumber).orElseThrow(
                () -> new CobolApiException(HttpStatus.NOT_FOUND,
                        CobolMessages.CARD_COMBINATION_NOT_FOUND));
        if (!accountId.equals(card.getCardAcctId())) {
            throw new CobolApiException(HttpStatus.NOT_FOUND,
                    CobolMessages.CARD_COMBINATION_NOT_FOUND);
        }
        requireName(request.embossedName());
        String active = requireStatus(request.activeStatus());
        requireExpiryMonth(request.expiryMonth());
        requireExpiryYear(request.expiryYear());

        if (request.originalEmbossedName() != null
                && !same(request.originalEmbossedName(), card.getCardEmbossedName())) {
            throw changed();
        }
        if (request.originalActiveStatus() != null
                && !same(request.originalActiveStatus(), card.getCardActiveStatus())) {
            throw changed();
        }
        if (request.originalExpiryMonth() != null
                && !request.originalExpiryMonth().equals(card.getCardExpirationDate().getMonthValue())) {
            throw changed();
        }
        if (request.originalExpiryYear() != null
                && !request.originalExpiryYear().equals(card.getCardExpirationDate().getYear())) {
            throw changed();
        }
        if (same(request.embossedName(), card.getCardEmbossedName())
                && same(active, card.getCardActiveStatus())
                && request.expiryMonth().equals(card.getCardExpirationDate().getMonthValue())
                && request.expiryYear().equals(card.getCardExpirationDate().getYear())) {
            throw new CobolApiException(HttpStatus.BAD_REQUEST, CobolMessages.NO_CHANGES_DETECTED);
        }
        card.setCardEmbossedName(request.embossedName().trim());
        card.setCardActiveStatus(active);
        card.setCardExpirationDate(LocalDate.of(request.expiryYear(), request.expiryMonth(),
                card.getCardExpirationDate().getDayOfMonth()));
        cardRepository.save(card);
        return response(card);
    }

    private CardResponse response(Card card) {
        return new CardResponse(card.getCardNumber(), card.getCardAcctId(), card.getCardCvvCode(),
                card.getCardEmbossedName(), card.getCardExpirationDate(), card.getCardActiveStatus());
    }

    private Long validateAccount(String raw, boolean required) {
        if (raw == null || raw.isBlank()) {
            if (required) {
                throw invalid(CobolMessages.ACCOUNT_NUMBER_INVALID);
            }
            return null;
        }
        if (!raw.matches("\\d{1,11}") || raw.chars().allMatch(c -> c == '0')) {
            throw invalid(CobolMessages.CARD_ACCOUNT_FILTER_INVALID);
        }
        return Long.parseLong(raw);
    }

    private String validateCard(String raw, boolean required) {
        if (raw == null || raw.isBlank()) {
            if (required) {
                throw invalid(CobolMessages.CARD_FILTER_INVALID);
            }
            return null;
        }
        if (!raw.matches("\\d{16}") || raw.chars().allMatch(c -> c == '0')) {
            throw invalid(CobolMessages.CARD_FILTER_INVALID);
        }
        return raw;
    }

    private void requireName(String value) {
        if (value == null || value.isBlank()) {
            throw invalid(CobolMessages.CARD_NAME_REQUIRED);
        }
        if (!value.matches("[A-Za-z ]+")) {
            throw invalid(CobolMessages.CARD_NAME_ALPHA);
        }
    }

    private String requireStatus(String value) {
        if (value == null || value.isBlank()) {
            throw invalid(CobolMessages.CARD_STATUS_INVALID);
        }
        String normalized = value.trim().toUpperCase();
        if (!normalized.equals("Y") && !normalized.equals("N")) {
            throw invalid(CobolMessages.CARD_STATUS_INVALID);
        }
        return normalized;
    }

    private void requireExpiryMonth(Integer value) {
        if (value == null || value < 1 || value > 12) {
            throw invalid(CobolMessages.CARD_EXPIRY_MONTH_INVALID);
        }
    }

    private void requireExpiryYear(Integer value) {
        if (value == null || value < 1950 || value > 2099) {
            throw invalid(CobolMessages.CARD_EXPIRY_YEAR_INVALID);
        }
    }

    private boolean same(String left, String right) {
        return left == null ? right == null : left.trim().equalsIgnoreCase(right == null ? null : right.trim());
    }

    private CobolApiException changed() {
        return new CobolApiException(HttpStatus.CONFLICT, CobolMessages.RECORD_CHANGED);
    }

    private CobolApiException invalid(String message) {
        return new CobolApiException(HttpStatus.BAD_REQUEST, message);
    }
}
