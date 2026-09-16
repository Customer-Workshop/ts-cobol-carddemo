package com.carddemo.service;

import com.carddemo.api.CobolMessages;
import com.carddemo.api.PendingAuthKey;
import com.carddemo.api.PendingAuthNavigation;
import com.carddemo.api.PendingAuthPageState;
import com.carddemo.api.PendingAuthRequest;
import com.carddemo.api.PendingAuthResponse;
import com.carddemo.api.PendingAuthRowView;
import com.carddemo.api.PendingAuthScreenView;
import com.carddemo.model.Account;
import com.carddemo.model.CardXref;
import com.carddemo.model.Customer;
import com.carddemo.model.PendingAuthDetail;
import com.carddemo.model.PendingAuthSummary;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.CustomerRepository;
import com.carddemo.repository.PendingAuthDetailRepository;
import com.carddemo.repository.PendingAuthSummaryRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * COPAUS0C (tran CPVS): the pending-authorization list. IMS PAUTSUM0/PAUTDTL1
 * reads become repository calls: GU = findById, unqualified GNP = ordered
 * first page, qualified GNP reposition = keyset fetch (S19-B3/S19-B8).
 * One call per AID press; {@code request.pageState} is the echoed
 * CDEMO-CPVS-INFO COMMAREA.
 */
@Service
public class PendingAuthService {
    public static final int PAGE_SIZE = PendingAuthPageState.ROW_COUNT;
    private static final String PROGRAM_DETAIL = "COPAUS1C";

    private final PendingAuthSummaryRepository summaryRepository;
    private final PendingAuthDetailRepository detailRepository;
    private final CardXrefRepository cardXrefRepository;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;

    public PendingAuthService(PendingAuthSummaryRepository summaryRepository,
                              PendingAuthDetailRepository detailRepository,
                              CardXrefRepository cardXrefRepository,
                              AccountRepository accountRepository,
                              CustomerRepository customerRepository) {
        this.summaryRepository = summaryRepository;
        this.detailRepository = detailRepository;
        this.cardXrefRepository = cardXrefRepository;
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
    }

    public PendingAuthResponse browse(PendingAuthRequest request) {
        if (request.pageState() == null) {
            // Fresh entry (COPAUS0C.cbl:195-217): a numeric CDEMO-ACCT-ID
            // echoes and gathers at once; anything else shows the empty map.
            String acctInput = trim(request.acctId());
            PendingAuthPageState state = PendingAuthPageState.fresh(
                    isNumeric(acctInput) ? Long.parseLong(acctInput) : null);
            if (state.acctId() == null) {
                return page(state, blankScreen(acctInput, null));
            }
            return gather(state, acctInput, null);
        }

        PendingAuthPageState state = request.pageState();
        String aid = normalizeAid(request.aid());
        return switch (aid) {
            case "PF3" ->
                    // RETURN-TO-PREV-SCREEN (:660-678) — XCTL back to the menu.
                    new PendingAuthResponse("exit", null, null, state);
            case "ENTER" -> enter(request, state);
            case "PF7" -> pageBackward(state);
            case "PF8" -> pageForward(state);
            default -> {
                // WHEN OTHER (:245-252): invalid AID redisplays with the
                // invalid-key message.
                PendingAuthScreenView screen =
                        blankScreen("", CobolMessages.INVALID_KEY_PRESSED);
                yield page(state, screen);
            }
        };
    }

    // PROCESS-ENTER-KEY (:258-342): input edit, SEL scan, then GATHER-DETAILS.
    private PendingAuthResponse enter(PendingAuthRequest request, PendingAuthPageState state) {
        String acctInput = trim(request.acctId());
        if (acctInput.isEmpty()) {
            return page(state, blankScreen("", CobolMessages.PENDING_AUTH_ACCT_REQUIRED));
        }
        if (!isNumeric(acctInput)) {
            return page(state, blankScreen("", CobolMessages.PENDING_AUTH_ACCT_NUMERIC));
        }
        Long acct = Long.parseLong(acctInput);

        // SEL scan (:289-335): first non-blank SEL on a populated row wins;
        // a non-blank SEL on an empty row (AUTH-KEYS(n) LOW-VALUES) falls
        // through to GATHER-DETAILS untouched.
        for (int i = 0; i < PAGE_SIZE; i++) {
            String sel = trim(selection(request, i));
            if (sel.isEmpty()) {
                continue;
            }
            String key = i < state.authKeys().size() ? state.authKeys().get(i) : null;
            if (key == null) {
                break;
            }
            if ("S".equalsIgnoreCase(sel)) {
                return new PendingAuthResponse("navigate",
                        new PendingAuthNavigation(PROGRAM_DETAIL, acct, key), null, state);
            }
            return gather(PendingAuthPageState.fresh(acct), acctInput,
                    CobolMessages.TRANSACTION_SELECTION_INVALID);
        }
        return gather(PendingAuthPageState.fresh(acct), acctInput, null);
    }

    // GATHER-DETAILS (:344-354): CXACAIX -> ACCTDAT -> CUSTDAT context reads,
    // PAUTSUM0 GU, then the page-1 fill when a summary exists.
    private PendingAuthResponse gather(PendingAuthPageState state, String acctInput,
                                       String message) {
        String acct = trim(acctInput);
        Long acctId = state.acctId();
        List<CardXref> xrefs;
        try {
            xrefs = cardXrefRepository.findByXrefAcctId(acctId);
        } catch (DataAccessException exception) {
            return page(state, blankScreen(acct, CobolMessages.pendingAuthXrefError(acct)));
        }
        if (xrefs.isEmpty()) {
            return page(state, blankScreen(acct, CobolMessages.pendingAuthXrefNotFound(acct)));
        }
        CardXref xref = xrefs.get(0);

        Account account;
        try {
            account = accountRepository.findById(xref.getXrefAcctId()).orElse(null);
        } catch (DataAccessException exception) {
            return page(state, blankScreen(acct,
                    CobolMessages.pendingAuthAcctError(padded(xref.getXrefAcctId(), 11))));
        }
        if (account == null) {
            return page(state, blankScreen(acct,
                    CobolMessages.pendingAuthAcctNotFound(padded(xref.getXrefAcctId(), 11))));
        }

        Customer customer;
        try {
            customer = customerRepository.findById(xref.getXrefCustId()).orElse(null);
        } catch (DataAccessException exception) {
            return page(state, blankScreen(acct,
                    CobolMessages.pendingAuthCustError(padded(xref.getXrefCustId(), 9))));
        }
        if (customer == null) {
            return page(state, blankScreen(acct,
                    CobolMessages.pendingAuthCustNotFound(padded(xref.getXrefCustId(), 9))));
        }

        PendingAuthSummary summary;
        try {
            summary = summaryRepository.findById(acctId).orElse(null);
        } catch (DataAccessException exception) {
            return page(state, contextScreen(acct, customer, account, null,
                    emptyRows(), CobolMessages.pendingAuthSummaryError("EX")));
        }

        List<PendingAuthRowView> rows = emptyRows();
        PendingAuthPageState next = PendingAuthPageState.fresh(acctId);
        if (summary != null) {
            PageFill fill = fillForward(acctId, null);
            if (fill.error() != null) {
                return page(state, contextScreen(acct, customer, account, summary,
                        rows, fill.error()));
            }
            next = fill.state(PendingAuthPageState.fresh(acctId), 0);
            rows = fill.rows();
        }
        return page(next, contextScreen(acct, customer, account, summary, rows,
                message, next.pageNum()));
    }

    // PROCESS-PF7-KEY (:357-376): pop the page-start key and re-read the
    // current page inclusively (qualified GNP reposition).
    private PendingAuthResponse pageBackward(PendingAuthPageState state) {
        if (state.pageNum() <= 1 || state.acctId() == null) {
            return page(state, redisplay(state, CobolMessages.TRANSACTION_ALREADY_TOP));
        }
        int pageNum = state.pageNum() - 1;
        PendingAuthKey startKey = PendingAuthKey.parse(state.prevPageKeys().get(pageNum - 1));
        PageFill fill = fillFrom(state.acctId(), startKey);
        if (fill.error() != null) {
            return page(state, redisplay(state, fill.error()));
        }
        // The reposition consumed the page-start key as row 1, so the page
        // number and stack land back exactly where the forward pass left them.
        return page(fill.state(state, pageNum),
                render(state.acctId(), fill, null, pageNum));
    }

    // PROCESS-PF8-KEY (:379-410): strict continuation after PAUKEY-LAST —
    // only when the last fill's look-ahead saw another row.
    private PendingAuthResponse pageForward(PendingAuthPageState state) {
        if (!state.nextPage() || state.acctId() == null || state.lastKey() == null) {
            return page(state, redisplay(state, CobolMessages.TRANSACTION_ALREADY_BOTTOM));
        }
        PageFill fill = fillForward(state.acctId(), PendingAuthKey.parse(state.lastKey()));
        if (fill.error() != null) {
            return page(state, redisplay(state, fill.error()));
        }
        PendingAuthPageState next = fill.state(state, state.pageNum());
        return page(next, render(state.acctId(), fill, null, next.pageNum()));
    }

    // Unqualified GNP from the start, or strictly-after repositioned GNP.
    private PageFill fillForward(Long acctId, PendingAuthKey afterKey) {
        try {
            List<PendingAuthDetail> fetched = afterKey == null
                    ? detailRepository.findByIdAcctIdOrderByIdAuthDate9cAscIdAuthTime9cAsc(
                            acctId, PageRequest.ofSize(PAGE_SIZE + 1))
                    : detailRepository.findByAcctIdAfterKey(acctId, afterKey.date9c(),
                            afterKey.time9c(), PageRequest.ofSize(PAGE_SIZE + 1));
            return PageFill.of(fetched);
        } catch (DataAccessException exception) {
            return PageFill.error(CobolMessages.pendingAuthDetailsError("EX"));
        }
    }

    // Inclusive reposition at a page-start key (PF7's qualified GNP).
    private PageFill fillFrom(Long acctId, PendingAuthKey startKey) {
        try {
            List<PendingAuthDetail> fetched = detailRepository.findByAcctIdFromKey(
                    acctId, startKey.date9c(), startKey.time9c(),
                    PageRequest.ofSize(PAGE_SIZE + 1));
            return PageFill.of(fetched);
        } catch (DataAccessException exception) {
            return PageFill.error(CobolMessages.pendingAuthDetailsError("EX"));
        }
    }

    // Non-erase redisplay (SEND-ERASE-NO): context, summary and the current
    // page re-render with the message overlaid.
    private PendingAuthScreenView redisplay(PendingAuthPageState state, String message) {
        if (state.acctId() == null) {
            return blankScreen("", message);
        }
        PageFill current = PageFill.empty();
        if (state.pageNum() >= 1 && state.pageNum() - 1 < state.prevPageKeys().size()
                && state.prevPageKeys().get(state.pageNum() - 1) != null) {
            current = fillFrom(state.acctId(),
                    PendingAuthKey.parse(state.prevPageKeys().get(state.pageNum() - 1)));
            if (current.error() != null) {
                message = current.error();
                current = PageFill.empty();
            }
        }
        return render(state.acctId(), current, message, state.pageNum());
    }

    private PendingAuthScreenView render(Long acctId, PageFill fill, String message,
                                         int pageNum) {
        Context context = readContext(acctId);
        if (context.error() != null) {
            return blankScreen(padded(acctId, 11), context.error());
        }
        return contextScreen(padded(acctId, 11), context.customer(), context.account(),
                context.summary(), fill.rows(), message, pageNum);
    }

    private PendingAuthResponse page(PendingAuthPageState state, PendingAuthScreenView screen) {
        return new PendingAuthResponse("page", null, screen, state);
    }

    // GATHER-ACCOUNT-DETAILS (:742-760) minus the row fill: the context and
    // summary reads PF7/PF8 rely on having already run (non-erase BMS leaves
    // those fields on screen; the web render re-derives them).
    private Context readContext(Long acctId) {
        if (acctId == null) {
            return Context.error(CobolMessages.PENDING_AUTH_ACCT_REQUIRED);
        }
        String acct = padded(acctId, 11);
        List<CardXref> xrefs;
        try {
            xrefs = cardXrefRepository.findByXrefAcctId(acctId);
        } catch (DataAccessException exception) {
            return Context.error(CobolMessages.pendingAuthXrefError(acct));
        }
        if (xrefs.isEmpty()) {
            return Context.error(CobolMessages.pendingAuthXrefNotFound(acct));
        }
        CardXref xref = xrefs.get(0);
        Account account;
        try {
            account = accountRepository.findById(xref.getXrefAcctId()).orElse(null);
        } catch (DataAccessException exception) {
            return Context.error(CobolMessages.pendingAuthAcctError(
                    padded(xref.getXrefAcctId(), 11)));
        }
        if (account == null) {
            return Context.error(CobolMessages.pendingAuthAcctNotFound(
                    padded(xref.getXrefAcctId(), 11)));
        }
        Customer customer;
        try {
            customer = customerRepository.findById(xref.getXrefCustId()).orElse(null);
        } catch (DataAccessException exception) {
            return Context.error(CobolMessages.pendingAuthCustError(
                    padded(xref.getXrefCustId(), 9)));
        }
        if (customer == null) {
            return Context.error(CobolMessages.pendingAuthCustNotFound(
                    padded(xref.getXrefCustId(), 9)));
        }
        PendingAuthSummary summary;
        try {
            summary = summaryRepository.findById(acctId).orElse(null);
        } catch (DataAccessException exception) {
            return Context.error(CobolMessages.pendingAuthSummaryError("EX"));
        }
        return new Context(null, customer, account, summary);
    }

    private PendingAuthScreenView contextScreen(String acctInput, Customer customer,
                                                Account account, PendingAuthSummary summary,
                                                List<PendingAuthRowView> rows,
                                                String message) {
        return contextScreen(acctInput, customer, account, summary, rows, message, 0);
    }

    // MOVE-SUMMARY (:783-807): all counts and amounts come from the
    // PAUTSUM0 segment; a missing summary leaves the MOVE ZERO targets —
    // zero-edited values.
    private PendingAuthScreenView contextScreen(String acctInput, Customer customer,
                                                Account account, PendingAuthSummary summary,
                                                List<PendingAuthRowView> rows,
                                                String message, int pageNum) {
        return new PendingAuthScreenView(acctInput,
                custName(customer), padded(customer.getCustId(), 9),
                addr1(customer), addr2(customer),
                "",
                nullToEmpty(customer.getCustPhoneNum1()),
                count(summary == null ? null : summary.getApprovedAuthCnt()),
                count(summary == null ? null : summary.getDeclinedAuthCnt()),
                summaryAmount(summary == null ? null : summary.getCreditLimit()),
                summaryAmount(summary == null ? null : summary.getCashLimit()),
                summaryAmount(summary == null ? null : summary.getApprovedAuthAmt()),
                summaryAmount(summary == null ? null : summary.getCreditBalance()),
                summaryAmount(summary == null ? null : summary.getCashBalance()),
                summaryAmount(summary == null ? null : summary.getDeclinedAuthAmt()),
                rows, pageNum, message);
    }

    // POPULATE-AUTH-LIST (:519-526) row projection.
    private static PendingAuthRowView toRow(PendingAuthDetail detail) {
        return new PendingAuthRowView("",
                nullToEmpty(detail.getTransactionId()),
                editDate(detail.getAuthOrigDate()),
                editTime(detail.getAuthOrigTime()),
                nullToEmpty(detail.getAuthType()),
                "00".equals(detail.getAuthRespCode()) ? "A" : "D",
                nullToEmpty(detail.getMatchStatus()),
                CobolFormat.editSuppressedAmount(detail.getApprovedAmt()),
                PendingAuthKey.of(detail).encoded());
    }

    // YYMMDD -> MM/DD/YY (COPAUS0C.cbl:531-534).
    static String editDate(String yymmdd) {
        if (yymmdd == null || yymmdd.length() < 6) {
            return "";
        }
        return yymmdd.substring(2, 4) + "/" + yymmdd.substring(4, 6) + "/" + yymmdd.substring(0, 2);
    }

    // HHMMSS -> HH:MM:SS (COPAUS0C.cbl:527-529).
    static String editTime(String hhmmss) {
        if (hhmmss == null || hhmmss.length() < 6) {
            return "";
        }
        return hhmmss.substring(0, 2) + ":" + hhmmss.substring(2, 4) + ":" + hhmmss.substring(4, 6);
    }

    private static String custName(Customer customer) {
        String first = firstWord(customer.getCustFirstName());
        String middle = nullToEmpty(customer.getCustMiddleName());
        String last = firstWord(customer.getCustLastName());
        return first + " " + (middle.isEmpty() ? "" : middle.substring(0, 1)) + " " + last;
    }

    // STRING ... DELIMITED BY '  ' + ',' + ... (COPAUS0C.cbl:766-777).
    private static String addr1(Customer customer) {
        return delimited(customer.getCustAddrLine1()) + "," + delimited(customer.getCustAddrLine2());
    }

    private static String addr2(Customer customer) {
        String zip = nullToEmpty(customer.getCustAddrZip());
        return delimited(customer.getCustAddrLine3()) + ","
                + nullToEmpty(customer.getCustAddrStateCode()) + ","
                + zip.substring(0, Math.min(5, zip.length()));
    }

    private static String firstWord(String value) {
        String v = nullToEmpty(value);
        int space = v.indexOf(' ');
        return space < 0 ? v : v.substring(0, space);
    }

    private static String delimited(String value) {
        String v = nullToEmpty(value);
        int idx = v.indexOf("  ");
        return idx < 0 ? v : v.substring(0, idx);
    }

    private static PendingAuthScreenView blankScreen(String acctInput, String message) {
        return new PendingAuthScreenView(acctInput, "", "", "", "", "", "",
                "", "", "", "", "", "", "", "", emptyRows(), 0, message);
    }

    private static List<PendingAuthRowView> emptyRows() {
        return new ArrayList<>(Collections.nCopies(PAGE_SIZE, null));
    }

    private static String normalizeAid(String aid) {
        return aid == null ? "ENTER" : aid.trim().toUpperCase();
    }

    private static boolean isNumeric(String value) {
        return value != null && !value.isEmpty() && value.chars().allMatch(Character::isDigit);
    }

    private static String selection(PendingAuthRequest request, int index) {
        List<String> selections = request.selections();
        return selections == null || index >= selections.size() ? null : selections.get(index);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String padded(Long value, int width) {
        return value == null ? "" : ("%0" + width + "d").formatted(value);
    }

    private static String count(Integer value) {
        return value == null ? "000" : "%03d".formatted(value);
    }

    // Missing-summary displays are MOVE ZERO targets (:801-807).
    private static String summaryAmount(java.math.BigDecimal value) {
        return CobolFormat.editSuppressedAmount(value == null ? java.math.BigDecimal.ZERO : value);
    }

    private record Context(String error, Customer customer, Account account,
                           PendingAuthSummary summary) {
        static Context error(String message) {
            return new Context(message, null, null, null);
        }
    }

    // PROCESS-PAGE-FORWARD (:413-455): fill up to five slots, update
    // PAUKEY-LAST, push the page-start key, and run the look-ahead GNP that
    // sets NEXT-PAGE-FLG.
    private record PageFill(List<PendingAuthRowView> rows, List<String> authKeys,
                            String lastKey, boolean nextPage, String error) {
        static PageFill of(List<PendingAuthDetail> fetched) {
            List<PendingAuthRowView> rows = new ArrayList<>(emptyRows());
            List<String> authKeys = new ArrayList<>(Collections.nCopies(PAGE_SIZE, null));
            String lastKey = null;
            int count = Math.min(PAGE_SIZE, fetched.size());
            for (int i = 0; i < count; i++) {
                PendingAuthDetail detail = fetched.get(i);
                rows.set(i, toRow(detail));
                lastKey = PendingAuthKey.of(detail).encoded();
                authKeys.set(i, lastKey);
            }
            return new PageFill(rows, authKeys, lastKey, fetched.size() > PAGE_SIZE, null);
        }

        static PageFill error(String message) {
            return new PageFill(emptyRows(), new ArrayList<>(Collections.nCopies(PAGE_SIZE, null)),
                    null, false, message);
        }

        static PageFill empty() {
            return error(null);
        }

        // WS-IDX=2 side effect (:436-443): the first filled row bumps
        // PAGE-NUM and stores its key under the new page number.
        PendingAuthPageState state(PendingAuthPageState prior, int basePageNum) {
            List<String> prevKeys = new ArrayList<>(prior.prevPageKeys());
            int pageNum = basePageNum;
            if (lastKey != null) {
                pageNum = basePageNum + 1;
                while (prevKeys.size() < pageNum) {
                    prevKeys.add(null);
                }
                if (pageNum <= PendingAuthPageState.MAX_PAGE_KEYS) {
                    prevKeys.set(pageNum - 1, authKeys.get(0));
                }
            }
            return new PendingAuthPageState(prior.acctId(), pageNum, prevKeys,
                    lastKey, nextPage, authKeys);
        }
    }
}
