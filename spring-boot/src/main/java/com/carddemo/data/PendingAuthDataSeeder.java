package com.carddemo.data;

import com.carddemo.model.CardXref;
import com.carddemo.model.PendingAuthDetail;
import com.carddemo.model.PendingAuthSummary;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.PendingAuthDetailRepository;
import com.carddemo.repository.PendingAuthSummaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * S-19 fixture: a handful of PAUTSUM0/PAUTDTL1 rows so the pending-auth
 * screens have content before the S-20 producer path exists. Runs after
 * {@link DataSeeder} (ApplicationReadyEvent fires once the runners finish)
 * and hangs the fixture on whichever accounts the ASCII seed actually
 * loaded — dev fixture accts 2/20, plus acct 1 which exists in both the dev
 * and test fixtures. Cards come from the seeded XREF so the detail rows
 * carry the account's real card number.
 *
 * Key fields carry the real 9's-complement values (date9c = 99999 - YYDDD,
 * time9c = 999999999 - HHMMSSmmm) so ascending rows display newest-first
 * (COPAUA0C.cbl:868-875).
 */
@Component
@ConditionalOnProperty(name = "carddemo.seed.enabled", havingValue = "true", matchIfMissing = true)
public class PendingAuthDataSeeder {
    private static final Logger log = LoggerFactory.getLogger(PendingAuthDataSeeder.class);

    private final PendingAuthSummaryRepository summaryRepository;
    private final PendingAuthDetailRepository detailRepository;
    private final CardXrefRepository cardXrefRepository;

    public PendingAuthDataSeeder(PendingAuthSummaryRepository summaryRepository,
                                 PendingAuthDetailRepository detailRepository,
                                 CardXrefRepository cardXrefRepository) {
        this.summaryRepository = summaryRepository;
        this.detailRepository = detailRepository;
        this.cardXrefRepository = cardXrefRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (summaryRepository.count() > 0) {
            log.info("Skipping pending-auth seed because summary data is already present");
            return;
        }
        List<PendingAuthSummary> summaries = new ArrayList<>();
        List<PendingAuthDetail> details = new ArrayList<>();

        // Acct 1 — seven rows with the two oldest pre-flagged for fraud:
        // enough to exercise PF7/PF8 paging and the F5 remove path.
        account(summaries, details, 1L, "11000.00", "2500.00", "5200.50", "0.00",
                5, 2, "620.10", "95.75", new Object[][] {
                        {2024, 60, 143512340, "240229", "143512", "0500", "00", "0000", "P",
                                "175.50", "AMAZON MKTPLACE", "SEATTLE", "WA", "98101",
                                "T00000000000401", " ", "        "},
                        {2024, 58, 91545123, "240227", "091545", "0500", "00", "0000", "P",
                                "82.15", "WHOLEFDS SEA 102", "SEATTLE", "WA", "98103",
                                "T00000000000392", " ", "        "},
                        {2024, 55, 164020500, "240224", "164020", "0600", "85", "4100", "D",
                                "210.00", "DELTA AIR LINES", "ATLANTA", "GA", "30301",
                                "T00000000000377", " ", "        "},
                        {2024, 52, 110310800, "240221", "110310", "0500", "00", "0000", "P",
                                "22.60", "CORNER CAFE", "PORTLAND", "OR", "97201",
                                "T00000000000360", " ", "        "},
                        {2024, 50, 184505000, "240219", "184505", "0500", "00", "0000", "P",
                                "145.00", "HOTEL SEASIDE", "SAN DIEGO", "CA", "92101",
                                "T00000000000345", " ", "        "},
                        {2024, 48, 81715000, "240217", "081715", "0500", "85", "4400", "D",
                                "530.50", "ELECTRONICS WORLD", "PHOENIX", "AZ", "85001",
                                "T00000000000330", " ", "        "},
                        {2024, 45, 122330750, "240214", "122330", "0500", "00", "0000", "P",
                                "98.75", "GAS N GO", "TACOMA", "WA", "98401",
                                "T00000000000312", "F", "02/20/24"},
                });

        // Acct 2 — seven rows, one pre-flagged (dev fixture only).
        account(summaries, details, 2L, "10000.00", "2000.00", "4500.75", "0.00",
                4, 3, "380.25", "740.50", new Object[][] {
                        {2024, 61, 143512340, "240301", "143512", "0500", "00", "0000", "P",
                                "175.50", "AMAZON MKTPLACE", "SEATTLE", "WA", "98101",
                                "T00000000000410", " ", "        "},
                        {2024, 58, 91545123, "240227", "091545", "0500", "00", "0000", "P",
                                "82.15", "WHOLEFDS SEA 102", "SEATTLE", "WA", "98103",
                                "T00000000000392", " ", "        "},
                        {2024, 55, 164020500, "240224", "164020", "0600", "85", "4100", "D",
                                "210.00", "DELTA AIR LINES", "ATLANTA", "GA", "30301",
                                "T00000000000377", " ", "        "},
                        {2024, 52, 110310800, "240221", "110310", "0500", "00", "0000", "P",
                                "22.60", "CORNER CAFE", "PORTLAND", "OR", "97201",
                                "T00000000000360", " ", "        "},
                        {2024, 50, 184505000, "240219", "184505", "0500", "00", "0000", "P",
                                "145.00", "HOTEL SEASIDE", "SAN DIEGO", "CA", "92101",
                                "T00000000000345", " ", "        "},
                        {2024, 48, 81715000, "240217", "081715", "0500", "85", "4400", "D",
                                "530.50", "ELECTRONICS WORLD", "PHOENIX", "AZ", "85001",
                                "T00000000000330", " ", "        "},
                        {2024, 45, 122330750, "240214", "122330", "0500", "00", "0000", "P",
                                "98.75", "GAS N GO", "TACOMA", "WA", "98401",
                                "T00000000000312", "F", "02/20/24"},
                });

        // Acct 20 — three approved rows (dev fixture only).
        account(summaries, details, 20L, "7500.00", "1500.00", "1200.00", "0.00",
                3, 0, "195.40", "0.00", new Object[][] {
                        {2024, 59, 103015000, "240228", "103015", "0500", "00", "0000", "P",
                                "65.40", "CITY BAKERY", "BOISE", "ID", "83701",
                                "T00000000000388", " ", "        "},
                        {2024, 54, 133020250, "240223", "133020", "0500", "00", "0000", "P",
                                "75.00", "SHOE PALACE", "BOISE", "ID", "83702",
                                "T00000000000370", " ", "        "},
                        {2024, 47, 195540900, "240216", "195540", "0600", "00", "0000", "P",
                                "55.00", "MOVIE HOUSE", "MERIDIAN", "ID", "83642",
                                "T00000000000341", " ", "        "},
                });

        summaryRepository.saveAll(summaries);
        detailRepository.saveAll(details);
        log.info("Seeded {} pending-auth summaries and {} details",
                summaries.size(), details.size());
    }

    private void account(List<PendingAuthSummary> summaries, List<PendingAuthDetail> details,
                         long acctId, String creditLimit, String cashLimit, String creditBal,
                         String cashBal, int apprCnt, int declCnt, String apprAmt,
                         String declAmt, Object[][] rows) {
        List<CardXref> xrefs = cardXrefRepository.findByXrefAcctId(acctId);
        if (xrefs.isEmpty()) {
            return;
        }
        long custId = xrefs.get(0).getXrefCustId();
        String cardNum = xrefs.get(0).getXrefCardNumber().trim();
        PendingAuthSummary summary = new PendingAuthSummary();
        summary.setAcctId(acctId);
        summary.setCustId(custId);
        summary.setAuthStatus(" ");
        summary.setAccountStatus("          ");
        summary.setCreditLimit(new BigDecimal(creditLimit));
        summary.setCashLimit(new BigDecimal(cashLimit));
        summary.setCreditBalance(new BigDecimal(creditBal));
        summary.setCashBalance(new BigDecimal(cashBal));
        summary.setApprovedAuthCnt(apprCnt);
        summary.setDeclinedAuthCnt(declCnt);
        summary.setApprovedAuthAmt(new BigDecimal(apprAmt));
        summary.setDeclinedAuthAmt(new BigDecimal(declAmt));
        summaries.add(summary);
        for (Object[] row : rows) {
            details.add(detail(acctId, cardNum, row));
        }
    }

    // dayOfYear + HHMMSSmmm feed the complement key the producer computes
    // (date9c = 99999 - YYDDD, time9c = 999999999 - HHMMSSmmm).
    private static PendingAuthDetail detail(long acctId, String cardNum, Object[] row) {
        int year = (int) row[0];
        int dayOfYear = (int) row[1];
        int timeMillis = (int) row[2];
        String respCode = (String) row[6];
        String amt = (String) row[9];
        PendingAuthDetail detail = new PendingAuthDetail();
        int yyddd = (year % 100) * 1000 + dayOfYear;
        detail.setId(new PendingAuthDetail.Id(acctId, 99999 - yyddd, 999999999 - timeMillis));
        detail.setAuthOrigDate((String) row[3]);
        detail.setAuthOrigTime((String) row[4]);
        detail.setCardNum(cardNum);
        detail.setAuthType((String) row[5]);
        detail.setCardExpiryDate("1226");
        detail.setMessageType("0100  ");
        detail.setMessageSource("POS   ");
        detail.setAuthIdCode("ABC123");
        detail.setAuthRespCode(respCode);
        detail.setAuthRespReason((String) row[7]);
        detail.setProcessingCode(100);
        detail.setTransactionAmt(new BigDecimal(amt));
        detail.setApprovedAmt("00".equals(respCode) ? new BigDecimal(amt) : BigDecimal.ZERO);
        detail.setMerchantCategoryCode("5411");
        detail.setAcqrCountryCode("840");
        detail.setPosEntryMode(5);
        detail.setMerchantId("M12345678901234");
        detail.setMerchantName((String) row[10]);
        detail.setMerchantCity((String) row[11]);
        detail.setMerchantState((String) row[12]);
        detail.setMerchantZip((String) row[13]);
        detail.setTransactionId((String) row[14]);
        detail.setMatchStatus((String) row[8]);
        detail.setAuthFraud((String) row[15]);
        detail.setFraudRptDate((String) row[16]);
        return detail;
    }
}
