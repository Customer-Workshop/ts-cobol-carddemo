package com.carddemo.service;

import com.carddemo.api.CobolApiException;
import com.carddemo.api.CobolMessages;
import com.carddemo.api.TransactionCreateRequest;
import com.carddemo.api.TransactionListResponse;
import com.carddemo.api.TransactionListRow;
import com.carddemo.api.TransactionResponse;
import com.carddemo.model.Card;
import com.carddemo.model.Transaction;
import com.carddemo.model.TransactionCategory;
import com.carddemo.model.TransactionType;
import com.carddemo.repository.CardRepository;
import com.carddemo.repository.TransactionCategoryRepository;
import com.carddemo.repository.TransactionRepository;
import com.carddemo.repository.TransactionTypeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

@Service
public class TransactionService {
    public static final int COBOL_PAGE_SIZE = 10;

    private final TransactionRepository transactionRepository;
    private final CardRepository cardRepository;
    private final TransactionTypeRepository typeRepository;
    private final TransactionCategoryRepository categoryRepository;
    private final TransactionIdGenerator idGenerator;

    public TransactionService(TransactionRepository transactionRepository,
                              CardRepository cardRepository,
                              TransactionTypeRepository typeRepository,
                              TransactionCategoryRepository categoryRepository,
                              TransactionIdGenerator idGenerator) {
        this.transactionRepository = transactionRepository;
        this.cardRepository = cardRepository;
        this.typeRepository = typeRepository;
        this.categoryRepository = categoryRepository;
        this.idGenerator = idGenerator;
    }

    public TransactionListResponse list(String filter, int page, String direction) {
        if (page < 0) throw bad(CobolMessages.TRANSACTION_TOP);
        boolean backward = "backward".equalsIgnoreCase(direction);
        if (!backward && direction != null && !direction.isBlank()
                && !"forward".equalsIgnoreCase(direction)) {
            throw bad(CobolMessages.INVALID_KEY);
        }
        if (filter != null && !filter.isBlank()
                && (!filter.matches("\\d{1,16}") || filter.chars().allMatch(c -> c == '0'))) {
            throw bad(CobolMessages.TRANSACTION_ID_INVALID);
        }
        Pageable pageable = PageRequest.of(page, COBOL_PAGE_SIZE,
                Sort.by(backward ? Sort.Direction.DESC : Sort.Direction.ASC, "tranId"));
        String start = filter == null || filter.isBlank()
                ? null
                : "%016d".formatted(Long.parseLong(filter));
        Page<Transaction> values;
        if (start == null) {
            values = transactionRepository.findAll(pageable);
        } else if (backward) {
            values = transactionRepository.findByTranIdLessThanEqual(start, pageable);
        } else {
            values = transactionRepository.findByTranIdGreaterThanEqual(start, pageable);
        }
        if (values.isEmpty()) {
            throw notFound(backward ? CobolMessages.TRANSACTION_TOP : CobolMessages.TRANSACTION_BOTTOM);
        }
        List<TransactionListRow> rows = values.getContent().stream().map(this::row).toList();
        return new TransactionListResponse(page, COBOL_PAGE_SIZE, values.hasNext(),
                page > 0, rows);
    }

    public TransactionResponse detail(String transactionId) {
        String id = requireTransactionId(transactionId);
        return transactionRepository.findById(id).map(this::response)
                .orElseThrow(() -> notFound(CobolMessages.TRANSACTION_NOT_FOUND));
    }

    @Transactional
    public TransactionResponse add(TransactionCreateRequest request) {
        String cardNumber = resolveCard(request);
        String type = required(request.transactionTypeCode(), CobolMessages.TRANSACTION_TYPE_REQUIRED);
        String category = required(request.transactionCategoryCode(),
                CobolMessages.TRANSACTION_CATEGORY_REQUIRED);
        if (!typeRepository.existsById(type)) throw bad(CobolMessages.TRANSACTION_TYPE_INVALID);
        int categoryNumber = parseCategory(category);
        TransactionCategory.Id categoryId = new TransactionCategory.Id();
        categoryId.setTranTypeCode(type);
        categoryId.setTranCategoryCode(categoryNumber);
        if (!categoryRepository.existsById(categoryId)) throw bad(CobolMessages.TRANSACTION_CATEGORY_INVALID);
        if (blank(request.source())) throw bad(CobolMessages.TRANSACTION_SOURCE_REQUIRED);
        if (blank(request.description())) throw bad(CobolMessages.TRANSACTION_DESCRIPTION_REQUIRED);
        if (request.amount() == null) throw bad(CobolMessages.TRANSACTION_AMOUNT_REQUIRED);
        if (blank(request.originDate())) throw bad(CobolMessages.TRANSACTION_ORIG_DATE_REQUIRED);
        if (blank(request.processDate())) throw bad(CobolMessages.TRANSACTION_PROC_DATE_REQUIRED);
        if (request.merchantId() == null) throw bad(CobolMessages.TRANSACTION_MERCHANT_ID_REQUIRED);
        if (blank(request.merchantName())) throw bad(CobolMessages.TRANSACTION_MERCHANT_NAME_REQUIRED);
        if (blank(request.merchantCity())) throw bad(CobolMessages.TRANSACTION_MERCHANT_CITY_REQUIRED);
        if (blank(request.merchantZip())) throw bad(CobolMessages.TRANSACTION_MERCHANT_ZIP_REQUIRED);
        if (!"Y".equalsIgnoreCase(request.confirmation())) throw bad(CobolMessages.TRANSACTION_CONFIRM);
        Transaction value = new Transaction();
        value.setTranId(idGenerator.nextId());
        value.setTranCardNumber(cardNumber);
        value.setTranTypeCode(type);
        value.setTranCategoryCode(categoryNumber);
        value.setTranSource(request.source().trim());
        value.setTranDescription(request.description().trim());
        value.setTranAmount(request.amount());
        value.setTranOriginTimestamp(parseDate(request.originDate(),
                CobolMessages.TRANSACTION_ORIG_DATE_INVALID));
        value.setTranProcessTimestamp(parseDate(request.processDate(),
                CobolMessages.TRANSACTION_PROC_DATE_INVALID));
        value.setTranMerchantId(request.merchantId());
        value.setTranMerchantName(request.merchantName().trim());
        value.setTranMerchantCity(request.merchantCity().trim());
        value.setTranMerchantZip(request.merchantZip().trim());
        transactionRepository.save(value);
        return response(value);
    }

    private String resolveCard(TransactionCreateRequest request) {
        if (!blank(request.accountId())) {
            if (!request.accountId().matches("\\d{1,11}")) throw bad(CobolMessages.ACCOUNT_NUMBER_INVALID);
            long accountId = Long.parseLong(request.accountId());
            Card card = cardRepository.findByCardAcctId(accountId).stream().findFirst()
                    .orElseThrow(() -> notFound(CobolMessages.TRANSACTION_ACCOUNT_NOT_FOUND));
            return card.getCardNumber();
        }
        if (!blank(request.cardNumber())) {
            if (!request.cardNumber().matches("\\d{16}")) throw bad(CobolMessages.TRANSACTION_CARD_INVALID);
            if (cardRepository.findById(request.cardNumber()).isEmpty()) {
                throw notFound(CobolMessages.TRANSACTION_CARD_NOT_FOUND);
            }
            return request.cardNumber();
        }
        throw bad(CobolMessages.TRANSACTION_ACCOUNT_OR_CARD_REQUIRED);
    }

    private int parseCategory(String value) {
        if (!value.matches("\\d{1,4}")) throw bad(CobolMessages.TRANSACTION_CATEGORY_NUMERIC);
        return Integer.parseInt(value);
    }

    private LocalDateTime parseDate(String value, String message) {
        try {
            return LocalDate.parse(value).atStartOfDay();
        } catch (DateTimeParseException exception) {
            throw bad(message);
        }
    }

    private String requireTransactionId(String value) {
        if (blank(value) || !value.matches("\\d{1,16}")) throw bad(CobolMessages.TRANSACTION_ID_INVALID);
        return "%016d".formatted(Long.parseLong(value));
    }

    private String required(String value, String message) {
        if (blank(value)) throw bad(message);
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    private TransactionListRow row(Transaction value) {
        return new TransactionListRow("S", "U", value.getTranId(), value.getTranCardNumber(),
                value.getTranTypeCode(), "%04d".formatted(value.getTranCategoryCode()),
                value.getTranDescription(), value.getTranAmount(), value.getTranOriginTimestamp(),
                "/api/transactions/" + value.getTranId());
    }

    private TransactionResponse response(Transaction value) {
        return new TransactionResponse(value.getTranId(), value.getTranCardNumber(),
                value.getTranTypeCode(), "%04d".formatted(value.getTranCategoryCode()),
                value.getTranSource(), value.getTranDescription(), value.getTranAmount(),
                value.getTranMerchantId(), value.getTranMerchantName(), value.getTranMerchantCity(),
                value.getTranMerchantZip(), value.getTranOriginTimestamp(),
                value.getTranProcessTimestamp());
    }

    private CobolApiException bad(String message) {
        return new CobolApiException(HttpStatus.BAD_REQUEST, message);
    }

    private CobolApiException notFound(String message) {
        return new CobolApiException(HttpStatus.NOT_FOUND, message);
    }

}
