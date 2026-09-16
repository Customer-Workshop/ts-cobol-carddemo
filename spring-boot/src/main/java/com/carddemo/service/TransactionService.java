package com.carddemo.service;

import com.carddemo.api.CobolApiException;
import com.carddemo.api.CobolMessages;
import com.carddemo.api.TransactionAddScreen;
import com.carddemo.api.TransactionCreateRequest;
import com.carddemo.api.TransactionListResponse;
import com.carddemo.api.TransactionListRow;
import com.carddemo.api.TransactionResponse;
import com.carddemo.model.CardXref;
import com.carddemo.model.Transaction;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.TransactionRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class TransactionService {
    public static final int COBOL_PAGE_SIZE = 10;

    private static final String DATE_MASK = "YYYY-MM-DD";

    private final TransactionRepository transactionRepository;
    private final CardXrefRepository cardXrefRepository;
    private final DateValidationService dateValidationService;
    private final TransactionIdGenerator idGenerator;

    public TransactionService(TransactionRepository transactionRepository,
                              CardXrefRepository cardXrefRepository,
                              DateValidationService dateValidationService,
                              TransactionIdGenerator idGenerator) {
        this.transactionRepository = transactionRepository;
        this.cardXrefRepository = cardXrefRepository;
        this.dateValidationService = dateValidationService;
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

    /**
     * ENTER on the add-transaction map (PROCESS-ENTER-KEY +
     * VALIDATE-INPUT-KEY-FIELDS + VALIDATE-INPUT-DATA-FIELDS +
     * ADD-TRANSACTION, COTRN02C.cbl:164-466). Returns the post-edit screen
     * state; a rejection leaves the typed values in place and carries the
     * COBOL error text and the field the cursor lands on.
     */
    public TransactionAddScreen enter(TransactionCreateRequest request) {
        Fields fields = fields(request);
        Rejection error = validateKeyFields(fields);
        if (error != null) {
            return rejectScreen(fields, error);
        }
        return processEnter(fields);
    }

    /**
     * PF5 on the map: key fields resolve first, COPY-LAST-TRAN-DATA fills
     * the data fields from the highest tran-id, then the same ENTER tail
     * runs (COTRN02C.cbl:471-495).
     */
    public TransactionAddScreen copyLast(TransactionCreateRequest request) {
        Fields fields = fields(request);
        Rejection error = validateKeyFields(fields);
        if (error != null) {
            return rejectScreen(fields, error);
        }
        error = copyLastInto(fields);
        if (error != null) {
            return rejectScreen(fields, error);
        }
        return processEnter(fields);
    }

    private TransactionAddScreen processEnter(Fields fields) {
        Rejection error = validateDataFields(fields);
        if (error != null) {
            return rejectScreen(fields, error);
        }
        String confirm = fields.confirmation.trim();
        if (confirm.equalsIgnoreCase("Y")) {
            return writeTransaction(fields);
        }
        if (confirm.isEmpty() || confirm.equalsIgnoreCase("N")) {
            return rejectScreen(fields,
                    new Rejection(CobolMessages.TRANSACTION_CONFIRM, "confirmation"));
        }
        return rejectScreen(fields,
                new Rejection(CobolMessages.TRANSACTION_CONFIRM_INVALID, "confirmation"));
    }

    // VALIDATE-INPUT-KEY-FIELDS, COTRN02C.cbl:193-230 — account first,
    // card second, "entered both" is unreachable.
    private Rejection validateKeyFields(Fields fields) {
        if (!blank(fields.accountId)) {
            if (!allDigits(fields.accountId)) {
                return new Rejection(CobolMessages.TRANSACTION_ACCOUNT_NUMERIC, "accountId");
            }
            CardXref xref;
            try {
                xref = cardXrefRepository
                        .findByXrefAcctId(Long.parseLong(fields.accountId))
                        .stream().findFirst().orElse(null);
            } catch (DataAccessException e) {
                return new Rejection(CobolMessages.TRANSACTION_ACCOUNT_LOOKUP_FAILED, "accountId");
            }
            if (xref == null) {
                return new Rejection(CobolMessages.TRANSACTION_ACCOUNT_NOT_FOUND, "accountId");
            }
            fields.cardNumber = pad(xref.getXrefCardNumber(), Fields.CARD_WIDTH);
            return null;
        }
        if (!blank(fields.cardNumber)) {
            if (!allDigits(fields.cardNumber)) {
                return new Rejection(CobolMessages.TRANSACTION_CARD_INVALID, "cardNumber");
            }
            CardXref xref;
            try {
                xref = cardXrefRepository.findById(fields.cardNumber).orElse(null);
            } catch (DataAccessException e) {
                return new Rejection(CobolMessages.TRANSACTION_CARD_LOOKUP_FAILED, "cardNumber");
            }
            if (xref == null) {
                return new Rejection(CobolMessages.TRANSACTION_CARD_NOT_FOUND, "cardNumber");
            }
            fields.accountId = pad("%011d".formatted(xref.getXrefAcctId()), Fields.ACCT_WIDTH);
            return null;
        }
        return new Rejection(CobolMessages.TRANSACTION_ACCOUNT_OR_CARD_REQUIRED, "accountId");
    }

    // VALIDATE-INPUT-DATA-FIELDS, COTRN02C.cbl:235-437 — mandatory fields in
    // screen order, then the per-field class/layout edits, then amount
    // renormalisation, then the two CSUTLDTC calls, then the merchant-id
    // class test. First failure wins.
    private Rejection validateDataFields(Fields fields) {
        if (blank(fields.typeCode)) {
            return new Rejection(CobolMessages.TRANSACTION_TYPE_REQUIRED, "transactionTypeCode");
        }
        if (blank(fields.categoryCode)) {
            return new Rejection(CobolMessages.TRANSACTION_CATEGORY_REQUIRED,
                    "transactionCategoryCode");
        }
        if (blank(fields.source)) {
            return new Rejection(CobolMessages.TRANSACTION_SOURCE_REQUIRED, "source");
        }
        if (blank(fields.description)) {
            return new Rejection(CobolMessages.TRANSACTION_DESCRIPTION_REQUIRED, "description");
        }
        if (blank(fields.amount)) {
            return new Rejection(CobolMessages.TRANSACTION_AMOUNT_REQUIRED, "amount");
        }
        if (blank(fields.originDate)) {
            return new Rejection(CobolMessages.TRANSACTION_ORIG_DATE_REQUIRED, "originDate");
        }
        if (blank(fields.processDate)) {
            return new Rejection(CobolMessages.TRANSACTION_PROC_DATE_REQUIRED, "processDate");
        }
        if (blank(fields.merchantId)) {
            return new Rejection(CobolMessages.TRANSACTION_MERCHANT_ID_REQUIRED, "merchantId");
        }
        if (blank(fields.merchantName)) {
            return new Rejection(CobolMessages.TRANSACTION_MERCHANT_NAME_REQUIRED, "merchantName");
        }
        if (blank(fields.merchantCity)) {
            return new Rejection(CobolMessages.TRANSACTION_MERCHANT_CITY_REQUIRED, "merchantCity");
        }
        if (blank(fields.merchantZip)) {
            return new Rejection(CobolMessages.TRANSACTION_MERCHANT_ZIP_REQUIRED, "merchantZip");
        }
        if (!allDigits(fields.typeCode)) {
            return new Rejection(CobolMessages.TRANSACTION_TYPE_INVALID, "transactionTypeCode");
        }
        if (!allDigits(fields.categoryCode)) {
            return new Rejection(CobolMessages.TRANSACTION_CATEGORY_NUMERIC,
                    "transactionCategoryCode");
        }
        if (!amountLayout(fields.amount)) {
            return new Rejection(CobolMessages.TRANSACTION_AMOUNT_FORMAT, "amount");
        }
        if (!dateLayout(fields.originDate)) {
            return new Rejection(CobolMessages.TRANSACTION_ORIG_DATE_INVALID, "originDate");
        }
        if (!dateLayout(fields.processDate)) {
            return new Rejection(CobolMessages.TRANSACTION_PROC_DATE_INVALID, "processDate");
        }
        // WS-EDIT-AMT moves the signed text into PIC +99999999.99 for the
        // redisplay (:329-335).
        fields.amount = echoAmount(new BigDecimal(fields.amount.trim()));
        Rejection error = validateDate(fields.originDate, CobolMessages.TRANSACTION_ORIG_DATE_NOT_VALID,
                "originDate", fields);
        if (error != null) {
            return error;
        }
        error = validateDate(fields.processDate, CobolMessages.TRANSACTION_PROC_DATE_NOT_VALID,
                "processDate", fields);
        if (error != null) {
            return error;
        }
        if (!allDigits(fields.merchantId)) {
            return new Rejection(CobolMessages.TRANSACTION_MERCHANT_ID_NUMERIC, "merchantId");
        }
        return null;
    }

    // COTRN02C.cbl:360-379 — the caller accepts the date when CSUTLDTC
    // severity is 0000 or the message number is 2513 (out-of-CEEDAYS-range
    // but structurally valid). parsed == null covers the year-0000 quirk
    // (D-2), which clears the edits but cannot be stored.
    private Rejection validateDate(String field, String message, String cursor, Fields fields) {
        DateValidationResult result =
                dateValidationService.validate(field.trim(), DATE_MASK);
        boolean accepted = "0000".equals(result.severity())
                || ("2513".equals(result.messageNumber()) && result.parsed() != null);
        if (!accepted) {
            return new Rejection(message, cursor);
        }
        if ("originDate".equals(cursor)) {
            fields.originLocalDate = result.parsed();
        } else {
            fields.processLocalDate = result.parsed();
        }
        return null;
    }

    // COPY-LAST-TRAN-DATA, COTRN02C.cbl:471-495 — every move truncates to
    // the screen field width.
    private Rejection copyLastInto(Fields fields) {
        Transaction last;
        try {
            last = transactionRepository.findTopByOrderByTranIdDesc();
        } catch (DataAccessException e) {
            return new Rejection(CobolMessages.TRANSACTION_ADD_LOOKUP_FAILED, "accountId");
        }
        if (last != null) {
            fields.typeCode = pad(first(last.getTranTypeCode(), 2), Fields.TYPE_WIDTH);
            fields.categoryCode = pad(last.getTranCategoryCode() == null ? ""
                    : "%04d".formatted(last.getTranCategoryCode()), Fields.CATEGORY_WIDTH);
            fields.source = pad(first(last.getTranSource(), 10), Fields.SOURCE_WIDTH);
            fields.description = pad(first(last.getTranDescription(), 60), Fields.DESC_WIDTH);
            fields.amount = pad(last.getTranAmount() == null ? ""
                    : echoAmount(last.getTranAmount()), Fields.AMOUNT_WIDTH);
            fields.originDate = pad(dateText(last.getTranOriginTimestamp()), Fields.DATE_WIDTH);
            fields.processDate = pad(dateText(last.getTranProcessTimestamp()), Fields.DATE_WIDTH);
            fields.merchantId = pad(last.getTranMerchantId() == null ? ""
                    : "%09d".formatted(last.getTranMerchantId()), Fields.MERCHANT_ID_WIDTH);
            fields.merchantName = pad(first(last.getTranMerchantName(), 30), Fields.MERCHANT_NAME_WIDTH);
            fields.merchantCity = pad(first(last.getTranMerchantCity(), 25), Fields.MERCHANT_CITY_WIDTH);
            fields.merchantZip = pad(first(last.getTranMerchantZip(), 10), Fields.MERCHANT_ZIP_WIDTH);
        }
        return null;
    }

    // ADD-TRANSACTION, COTRN02C.cbl:442-466 + WRITE-TRANSACT-FILE
    // (:711-749): next id off the highest existing key, then the WRITE —
    // DUPREC and generic failures map to distinct messages.
    private TransactionAddScreen writeTransaction(Fields fields) {
        String tranId;
        try {
            tranId = idGenerator.nextId();
        } catch (DataAccessException e) {
            return rejectScreen(fields,
                    new Rejection(CobolMessages.TRANSACTION_ADD_LOOKUP_FAILED, "accountId"));
        }
        Transaction value = new Transaction();
        value.setTranId(tranId);
        value.setTranCardNumber(fields.cardNumber.trim());
        value.setTranTypeCode(fields.typeCode.trim());
        value.setTranCategoryCode(Integer.parseInt(fields.categoryCode.trim()));
        value.setTranSource(fields.source.trim());
        value.setTranDescription(fields.description.trim());
        value.setTranAmount(new BigDecimal(fields.amount.trim()));
        value.setTranOriginTimestamp(fields.originLocalDate.atStartOfDay());
        value.setTranProcessTimestamp(fields.processLocalDate.atStartOfDay());
        value.setTranMerchantId(Long.parseLong(fields.merchantId.trim()));
        value.setTranMerchantName(fields.merchantName.trim());
        value.setTranMerchantCity(fields.merchantCity.trim());
        value.setTranMerchantZip(fields.merchantZip.trim());
        try {
            transactionRepository.saveAndFlush(value);
        } catch (DataIntegrityViolationException e) {
            return rejectScreen(fields,
                    new Rejection(CobolMessages.TRANSACTION_DUPLICATE, "accountId"));
        } catch (DataAccessException e) {
            return rejectScreen(fields,
                    new Rejection(CobolMessages.TRANSACTION_ADD_FAILED, "accountId"));
        }
        // Successful write: all input fields initialise before the success
        // message shows (INITIALIZE-ALL-FIELDS, :755-779).
        return new TransactionAddScreen("", "", "", "", "", "", "", "", "", "",
                "", "", "", "", CobolMessages.transactionAdded(tranId), "info",
                "accountId", tranId);
    }

    private TransactionAddScreen rejectScreen(Fields fields, Rejection rejection) {
        return new TransactionAddScreen(fields.accountId.trim(), fields.cardNumber.trim(),
                fields.typeCode.trim(), fields.categoryCode.trim(), fields.source.trim(),
                fields.description.trim(), fields.amount.trim(), fields.originDate.trim(),
                fields.processDate.trim(), fields.merchantId.trim(), fields.merchantName.trim(),
                fields.merchantCity.trim(), fields.merchantZip.trim(),
                fields.confirmation.trim(), rejection.message(), null,
                rejection.cursorField(), null);
    }

    // The fourteen unprotected map fields, each padded to its PIC X width —
    // the COBOL class tests ran against the whole padded field.
    private Fields fields(TransactionCreateRequest request) {
        Fields f = new Fields();
        f.accountId = padAtApi(request.accountId(), Fields.ACCT_WIDTH, "accountId");
        f.cardNumber = padAtApi(request.cardNumber(), Fields.CARD_WIDTH, "cardNumber");
        f.typeCode = padAtApi(request.transactionTypeCode(), Fields.TYPE_WIDTH, "transactionTypeCode");
        f.categoryCode = padAtApi(request.transactionCategoryCode(), Fields.CATEGORY_WIDTH,
                "transactionCategoryCode");
        f.source = padAtApi(request.source(), Fields.SOURCE_WIDTH, "source");
        f.description = padAtApi(request.description(), Fields.DESC_WIDTH, "description");
        f.amount = padAtApi(request.amount(), Fields.AMOUNT_WIDTH, "amount");
        f.originDate = padAtApi(request.originDate(), Fields.DATE_WIDTH, "originDate");
        f.processDate = padAtApi(request.processDate(), Fields.DATE_WIDTH, "processDate");
        f.merchantId = padAtApi(request.merchantId(), Fields.MERCHANT_ID_WIDTH, "merchantId");
        f.merchantName = padAtApi(request.merchantName(), Fields.MERCHANT_NAME_WIDTH, "merchantName");
        f.merchantCity = padAtApi(request.merchantCity(), Fields.MERCHANT_CITY_WIDTH, "merchantCity");
        f.merchantZip = padAtApi(request.merchantZip(), Fields.MERCHANT_ZIP_WIDTH, "merchantZip");
        f.confirmation = padAtApi(request.confirmation(), 1, "confirmation");
        return f;
    }

    private String padAtApi(String value, int width, String name) {
        String text = value == null ? "" : value.replace('\u0000', ' ');
        if (text.length() > width) {
            // A 3270 cannot return an over-width field (D-3); anything wider
            // here is a malformed non-screen caller, not a screen redisplay.
            throw new CobolApiException(HttpStatus.BAD_REQUEST,
                    name + " exceeds the " + width + "-character field width");
        }
        return pad(text, width);
    }

    private static String pad(String text, int width) {
        String trimmed = text.trim();
        StringBuilder sb = new StringBuilder(trimmed);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static String first(String text, int width) {
        String trimmed = text == null ? "" : text.trim();
        return trimmed.length() > width ? trimmed.substring(0, width) : trimmed;
    }

    private static String dateText(java.time.LocalDateTime timestamp) {
        return timestamp == null ? "" : timestamp.toLocalDate().toString();
    }

    private static boolean allDigits(String field) {
        return field.chars().allMatch(Character::isDigit);
    }

    // PIC +99999999.99 — the amount edit/display mask the 3270 forces.
    private static String echoAmount(BigDecimal amount) {
        return "%+012.2f".formatted(amount);
    }

    private boolean amountLayout(String amount) {
        return amount.length() == Fields.AMOUNT_WIDTH
                && (amount.charAt(0) == '-' || amount.charAt(0) == '+')
                && amount.substring(1, 9).chars().allMatch(Character::isDigit)
                && amount.charAt(9) == '.'
                && amount.substring(10, 12).chars().allMatch(Character::isDigit);
    }

    private boolean dateLayout(String date) {
        return date.length() == Fields.DATE_WIDTH
                && date.substring(0, 4).chars().allMatch(Character::isDigit)
                && date.charAt(4) == '-'
                && date.substring(5, 7).chars().allMatch(Character::isDigit)
                && date.charAt(7) == '-'
                && date.substring(8, 10).chars().allMatch(Character::isDigit);
    }

    private record Rejection(String message, String cursorField) {
    }

    private static final class Fields {
        static final int ACCT_WIDTH = 11;
        static final int CARD_WIDTH = 16;
        static final int TYPE_WIDTH = 2;
        static final int CATEGORY_WIDTH = 4;
        static final int SOURCE_WIDTH = 10;
        static final int DESC_WIDTH = 60;
        static final int AMOUNT_WIDTH = 12;
        static final int DATE_WIDTH = 10;
        static final int MERCHANT_ID_WIDTH = 9;
        static final int MERCHANT_NAME_WIDTH = 30;
        static final int MERCHANT_CITY_WIDTH = 25;
        static final int MERCHANT_ZIP_WIDTH = 10;

        String accountId;
        String cardNumber;
        String typeCode;
        String categoryCode;
        String source;
        String description;
        String amount;
        String originDate;
        String processDate;
        String merchantId;
        String merchantName;
        String merchantCity;
        String merchantZip;
        String confirmation;
        java.time.LocalDate originLocalDate;
        java.time.LocalDate processLocalDate;
    }

    private String requireTransactionId(String value) {
        if (blank(value) || !value.matches("\\d{1,16}")) throw bad(CobolMessages.TRANSACTION_ID_INVALID);
        return "%016d".formatted(Long.parseLong(value));
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
