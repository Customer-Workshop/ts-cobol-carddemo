package com.carddemo.service;

import com.carddemo.api.CardListResponse;
import com.carddemo.api.CardListRow;
import com.carddemo.api.CardResponse;
import com.carddemo.api.CardUpdateRequest;
import com.carddemo.api.CobolApiException;
import com.carddemo.api.CobolMessages;
import com.carddemo.model.Card;
import com.carddemo.repository.CardRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
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
            throw invalid(CobolMessages.CARD_NO_PREVIOUS_PAGES);
        }
        boolean backward = "backward".equalsIgnoreCase(direction);
        if (!backward && direction != null && !"forward".equalsIgnoreCase(direction)
                && !direction.isBlank()) {
            throw invalid(CobolMessages.INVALID_KEY);
        }
        Pageable pageable = PageRequest.of(page, COBOL_PAGE_SIZE,
                Sort.by(backward ? Sort.Direction.DESC : Sort.Direction.ASC, "cardNumber"));
        Page<Card> cards;
        if (accountId != null && cardNumber != null) {
            cards = backward
                    ? cardRepository.findByCardAcctIdAndCardNumberLessThanEqual(
                            accountId, cardNumber, pageable)
                    : cardRepository.findByCardAcctIdAndCardNumberGreaterThanEqual(
                            accountId, cardNumber, pageable);
        } else if (accountId != null) {
            cards = cardRepository.findByCardAcctId(accountId, pageable);
        } else if (cardNumber != null) {
            cards = backward
                    ? cardRepository.findByCardNumberLessThanEqual(cardNumber, pageable)
                    : cardRepository.findByCardNumberGreaterThanEqual(cardNumber, pageable);
        } else {
            cards = cardRepository.findAll(pageable);
        }
        if (cards.isEmpty()) {
            throw new CobolApiException(HttpStatus.NOT_FOUND,
                    backward ? CobolMessages.CARD_NO_PREVIOUS_PAGES
                            : CobolMessages.CARD_NO_MORE_RECORDS);
        }
        List<CardListRow> rows = cards.getContent().stream()
                .map(card -> new CardListRow("S", "U", card.getCardAcctId(), card.getCardNumber(),
                        card.getCardActiveStatus(), "/api/cards/" + card.getCardNumber(),
                        "/api/cards/" + card.getCardNumber()))
                .toList();
        return new CardListResponse(page, COBOL_PAGE_SIZE, cards.hasNext(), page > 0, rows);
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
    public CardResponse update(String rawAccountId, String rawCardNumber,
                               CardUpdateRequest request) {
        Long accountId = validateAccount(rawAccountId, true);
        String cardNumber = validateCard(rawCardNumber, true);
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
        LocalDate existingExpiry = card.getCardExpirationDate();

        CardUpdateRequest.CardSnapshot original = request.original();
        if (original == null || original.embossedName() == null
                || original.activeStatus() == null || original.expiryMonth() == null
                || original.expiryYear() == null) {
            throw new CobolApiException(HttpStatus.BAD_REQUEST, CobolMessages.SNAPSHOT_REQUIRED);
        }
        if (!same(original.embossedName(), card.getCardEmbossedName())) {
            throw changed();
        }
        if (!same(original.activeStatus(), card.getCardActiveStatus())) {
            throw changed();
        }
        if (existingExpiry == null
                || !original.expiryMonth().equals(existingExpiry.getMonthValue())) {
            throw changed();
        }
        if (existingExpiry == null
                || !original.expiryYear().equals(existingExpiry.getYear())) {
            throw changed();
        }
        if (same(request.embossedName(), card.getCardEmbossedName())
                && same(active, card.getCardActiveStatus())
                && existingExpiry != null
                && request.expiryMonth().equals(existingExpiry.getMonthValue())
                && request.expiryYear().equals(existingExpiry.getYear())) {
            throw new CobolApiException(HttpStatus.BAD_REQUEST, CobolMessages.NO_CHANGES_DETECTED);
        }
        card.setCardEmbossedName(request.embossedName().trim());
        card.setCardActiveStatus(active);
        YearMonth expiry = YearMonth.of(request.expiryYear(), request.expiryMonth());
        int day = existingExpiry == null
                ? 1
                : Math.min(existingExpiry.getDayOfMonth(), expiry.lengthOfMonth());
        card.setCardExpirationDate(expiry.atDay(day));
        cardRepository.save(card);
        return response(card);
    }

    private CardResponse response(Card card) {
        return new CardResponse(card.getCardNumber(), card.getCardAcctId(),
                card.getCardCvvCode() == null ? null : "%03d".formatted(card.getCardCvvCode()),
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
        return left == null ? right == null : left.trim().equals(right == null ? null : right.trim());
    }

    private CobolApiException changed() {
        return new CobolApiException(HttpStatus.CONFLICT, CobolMessages.RECORD_CHANGED);
    }

    private CobolApiException invalid(String message) {
        return new CobolApiException(HttpStatus.BAD_REQUEST, message);
    }
}
