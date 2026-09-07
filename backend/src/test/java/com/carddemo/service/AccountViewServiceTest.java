package com.carddemo.service;

import com.carddemo.api.AccountViewResponse;
import com.carddemo.api.CobolApiException;
import com.carddemo.api.CobolMessages;
import com.carddemo.api.CustomerNotFoundException;
import com.carddemo.api.ScreenHeaderResponse;
import com.carddemo.model.Account;
import com.carddemo.model.CardXref;
import com.carddemo.model.Customer;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.CannotCreateTransactionException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * COACTVWC 2200-EDIT-MAP-INPUTS (app/cbl/COACTVWC.cbl:628-681) and 9000-READ-ACCT (:687-870), one
 * test per branch. Expectations come from the COBOL, app/bms/COACTVW.bms and
 * functional/CardDemo/programs/COACTVWC_functional_requirement.md; the account 27 / customer 27
 * values are decoded from app/data/ASCII/acctdata.txt:27 and custdata.txt:27, never from Java.
 * Confidence HIGH unless stated.
 *
 * <p>E-09..E-12 are asserted as the 75 bytes WS-RETURN-MSG holds (PIC X(75), cbl:117), which is the
 * true ceiling of every message: E-09/E-10 lose the last six RESP2 characters, E-11 loses the last
 * three, and E-12's meaningful text fills the field exactly — all ten RESP2 digits survive and only
 * the trailing filler blanks of the STRING are cut.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountViewServiceTest {

    @Mock
    private CardXrefRepository xrefs;

    @Mock
    private AccountRepository accounts;

    @Mock
    private CustomerRepository customers;

    @Mock
    private ScreenHeaderService headerService;

    private AccountViewService service;

    @BeforeEach
    void setUp() {
        service = new AccountViewService(xrefs, accounts, customers, headerService);
        when(headerService.header(AccountViewService.TRAN_ID, AccountViewService.PROGRAM_NAME))
                .thenReturn(new ScreenHeaderResponse("CAVW", "COACTVWC", "AWS Mainframe Modernization",
                        "CardDemo", "09/07/26", "14:05:09", "CARDDEMO", "CICS"));
    }

    /** app/data/ASCII/acctdata.txt:27 — "00000000027Y00000002840{00000055720{00000020750{2012-09-302025-07-132025-07-13...A000000000". */
    private static Account account27() {
        Account account = new Account();
        account.setAcctId(27L);
        account.setAcctActiveStatus("Y");
        account.setAcctCurrBal(new BigDecimal("284.00"));
        account.setAcctCreditLimit(new BigDecimal("5572.00"));
        account.setAcctCashCreditLimit(new BigDecimal("2075.00"));
        account.setAcctOpenDate(LocalDate.of(2012, 9, 30));
        account.setAcctExpiraionDate(LocalDate.of(2025, 7, 13));
        account.setAcctReissueDate(LocalDate.of(2025, 7, 13));
        account.setAcctCurrCycCredit(new BigDecimal("0.00"));
        account.setAcctCurrCycDebit(new BigDecimal("0.00"));
        account.setAcctGroupId("A000000000");
        return account;
    }

    /** app/data/ASCII/custdata.txt:27 — Ward / Henri / Jones, ZIP 07923-8822, SSN 980161210, FICO 078. */
    private static Customer customer27() {
        Customer customer = new Customer();
        customer.setCustId(27L);
        customer.setCustFirstName("Ward");
        customer.setCustMiddleName("Henri");
        customer.setCustLastName("Jones");
        customer.setCustAddrLine1("210 Amaya Turnpike");
        customer.setCustAddrLine2("Suite 180");
        customer.setCustAddrLine3("Port Dwight");
        customer.setCustAddrStateCd("GU");
        customer.setCustAddrCountryCd("USA");
        customer.setCustAddrZip("07923-8822");
        customer.setCustPhoneNum1("(935)027-1145  ");
        customer.setCustPhoneNum2("(103)537-5007  ");
        customer.setCustSsn(980161210L);
        customer.setCustGovtIssuedId("00000000000881558757");
        customer.setCustDobYyyyMmDd(LocalDate.of(1986, 11, 8));
        customer.setCustEftAccountId("0050024139");
        customer.setCustPriCardHolderInd("Y");
        customer.setCustFicoCreditScore(78);
        return customer;
    }

    private static CardXref xref(String cardNumber, long custId, long acctId) {
        CardXref xref = new CardXref();
        xref.setXrefCardNumber(cardNumber);
        xref.setXrefCustId(custId);
        xref.setXrefAcctId(acctId);
        return xref;
    }

    private void chainFound() {
        when(xrefs.findFirstByXrefAcctIdOrderByXrefCardNumberAsc(27L))
                .thenReturn(Optional.of(xref("0683586198171516", 27L, 27L)));
        when(accounts.findById(27L)).thenReturn(Optional.of(account27()));
        when(customers.findById(27L)).thenReturn(Optional.of(customer27()));
    }

    @Test
    @DisplayName("FR-14 / E-04 — blank ACCTSID: 2210-EDIT-ACCOUNT 'not supplied' plus the cross-field overwrite (cbl:628-633, :640-645)")
    void fr14BlankInput() {
        CobolApiException error = assertThrows(CobolApiException.class, () -> service.view("   "));

        assertEquals(CobolMessages.NO_INPUT_RECEIVED, error.getMessage());
        assertEquals("No input received", error.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        verifyNoInteractions(xrefs, accounts, customers);
    }

    @Test
    @DisplayName("FR-14 / E-04 — a null filter (no path segment) is the same 'not supplied' branch (cbl:640-645)")
    void fr14NullInput() {
        CobolApiException error = assertThrows(CobolApiException.class, () -> service.view(null));

        assertEquals(CobolMessages.NO_INPUT_RECEIVED, error.getMessage());
        verifyNoInteractions(xrefs, accounts, customers);
    }

    @Test
    @DisplayName("FR-14 / E-04 — '*' is replaced by LOW-VALUES before the edit, so it is 'not supplied' too (cbl:637-641)")
    void fr14AsteriskIsNoInput() {
        CobolApiException error = assertThrows(CobolApiException.class, () -> service.view("*"));

        assertEquals(CobolMessages.NO_INPUT_RECEIVED, error.getMessage());
        verifyNoInteractions(xrefs, accounts, customers);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567890A", "0000000002x", "abcdefghijk", "1234-567890"})
    @DisplayName("FR-15 / E-05 — NOT NUMERIC (cbl:666-677): the verbatim two-space literal, no read")
    void fr15NonNumeric(String filter) {
        CobolApiException error = assertThrows(CobolApiException.class, () -> service.view(filter));

        assertEquals("Account Filter must  be a non-zero 11 digit number", error.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        verifyNoInteractions(xrefs, accounts, customers);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "27", "0000000027", "000000000271"})
    @DisplayName("Q-02 / E-05 — fewer or more than 11 digits is invalid (BMS LENGTH=11, cbl:666-668)")
    void q02WrongLength(String filter) {
        CobolApiException error = assertThrows(CobolApiException.class, () -> service.view(filter));

        assertEquals(CobolMessages.ACCOUNT_FILTER_INVALID, error.getMessage());
        verifyNoInteractions(xrefs, accounts, customers);
    }

    @Test
    @DisplayName("FR-15 / E-05 — CC-ACCT-ID EQUAL ZEROES is rejected before any read (cbl:667)")
    void fr15AllZeroes() {
        CobolApiException error = assertThrows(CobolApiException.class, () -> service.view("00000000000"));

        assertEquals(CobolMessages.ACCOUNT_FILTER_INVALID, error.getMessage());
        verifyNoInteractions(xrefs, accounts, customers);
    }

    @Test
    @DisplayName("FR-17 / E-09 — CXACAIX NOTFND (cbl:736-758): verbatim text, and neither master file is read")
    void fr17XrefNotFound() {
        when(xrefs.findFirstByXrefAcctIdOrderByXrefCardNumberAsc(1L)).thenReturn(Optional.empty());

        CobolApiException error = assertThrows(CobolApiException.class, () -> service.view("00000000001"));

        assertEquals("Account:00000000001 not found in Cross ref file.  Resp:0000000013 Reas:0000",
                error.getMessage());
        assertEquals(75, error.getMessage().length(), "WS-RETURN-MSG is PIC X(75)");
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
        verifyNoInteractions(accounts, customers);
    }

    @Test
    @DisplayName("DV-01 / E-10 — ACCTDAT NOTFND (cbl:785-807): E-10 only, no customer read and no payload")
    void dv01AccountNotFound() {
        when(xrefs.findFirstByXrefAcctIdOrderByXrefCardNumberAsc(50L))
                .thenReturn(Optional.of(xref("0500024453765740", 50L, 50L)));
        when(accounts.findById(50L)).thenReturn(Optional.empty());

        CobolApiException error = assertThrows(CobolApiException.class, () -> service.view("00000000050"));

        assertEquals("Account:00000000050 not found in Acct Master file.Resp:0000000013 Reas:0000",
                error.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
        verifyNoInteractions(customers);
    }

    @Test
    @DisplayName("FR-20 / E-11 — CUSTDAT NOTFND (cbl:836-857): verbatim text and the account block stays on the screen")
    void fr20CustomerNotFound() {
        when(xrefs.findFirstByXrefAcctIdOrderByXrefCardNumberAsc(27L))
                .thenReturn(Optional.of(xref("0683586198171516", 27L, 27L)));
        when(accounts.findById(27L)).thenReturn(Optional.of(account27()));
        when(customers.findById(27L)).thenReturn(Optional.empty());

        CustomerNotFoundException error =
                assertThrows(CustomerNotFoundException.class, () -> service.view("00000000027"));

        assertEquals("CustId:000000027 not found in customer master.Resp: 0000000013 REAS:0000000",
                error.getMessage());
        // PIC X(75) cuts the last three of the ten RESP2 characters this STRING builds (cbl:117, :836-856).
        assertEquals(75, error.getMessage().length(), "WS-RETURN-MSG is PIC X(75)");
        assertTrue(error.getMessage().endsWith("REAS:0000000"));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
        assertEquals("00000000027", error.accountNumber());
        assertEquals("+        284.00", error.account().currentBalance());
        assertEquals("A000000000", error.account().groupId());
    }

    @Test
    @DisplayName("E-12 / D-0040 — a CXACAIX read failure that is not NOTFND becomes this program's own File Error text (cbl:759-770)")
    void e12XrefReadError() {
        when(xrefs.findFirstByXrefAcctIdOrderByXrefCardNumberAsc(anyLong()))
                .thenThrow(new DataAccessResourceFailureException("down"));

        CobolApiException error = assertThrows(CobolApiException.class, () -> service.view("00000000027"));

        assertEquals("File Error: READ     on CXACAIX   returned RESP 0000000016,RESP2 0000000000",
                error.getMessage());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, error.getStatus());
    }

    @Test
    @DisplayName("E-12 — the same WHEN OTHER arm for ACCTDAT (cbl:808-819)")
    void e12AccountReadError() {
        when(xrefs.findFirstByXrefAcctIdOrderByXrefCardNumberAsc(27L))
                .thenReturn(Optional.of(xref("0683586198171516", 27L, 27L)));
        when(accounts.findById(27L)).thenThrow(new CannotCreateTransactionException("no tx"));

        CobolApiException error = assertThrows(CobolApiException.class, () -> service.view("00000000027"));

        assertEquals("File Error: READ     on ACCTDAT   returned RESP 0000000016,RESP2 0000000000",
                error.getMessage());
        // E-12 fits PIC X(75) exactly: every RESP2 digit survives and only the STRING's trailing
        // filler blanks are cut (cbl:117, :808-819).
        assertEquals(75, error.getMessage().length(), "WS-RETURN-MSG is PIC X(75)");
        assertTrue(error.getMessage().endsWith(",RESP2 0000000000"));
        verifyNoInteractions(customers);
    }

    @Test
    @DisplayName("E-12 — the same WHEN OTHER arm for CUSTDAT (cbl:858-868)")
    void e12CustomerReadError() {
        when(xrefs.findFirstByXrefAcctIdOrderByXrefCardNumberAsc(27L))
                .thenReturn(Optional.of(xref("0683586198171516", 27L, 27L)));
        when(accounts.findById(27L)).thenReturn(Optional.of(account27()));
        when(customers.findById(27L)).thenThrow(new DataAccessResourceFailureException("down"));

        CobolApiException error = assertThrows(CobolApiException.class, () -> service.view("00000000027"));

        assertEquals("File Error: READ     on CUSTDAT   returned RESP 0000000016,RESP2 0000000000",
                error.getMessage());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, error.getStatus());
    }

    @Test
    @DisplayName("FR-16 / FR-18 / FR-19 — happy path paints every field of CACTVWA from account 27 / customer 27")
    void fr16HappyPathFieldMap() {
        chainFound();

        AccountViewResponse response = service.view("00000000027");

        assertEquals("CAVW", response.header().tranId());
        assertEquals("COACTVWC", response.header().programName());
        assertEquals("00000000027", response.accountNumber());
        assertEquals("Enter or update id of account to display", response.infoMessage());

        assertEquals("Y", response.account().activeStatus());
        assertEquals("2012-09-30", response.account().openDate());
        assertEquals("+      5,572.00", response.account().creditLimit());
        assertEquals("2025-07-13", response.account().expirationDate());
        assertEquals("+      2,075.00", response.account().cashCreditLimit());
        assertEquals("2025-07-13", response.account().reissueDate());
        assertEquals("+        284.00", response.account().currentBalance());
        assertEquals("+           .00", response.account().currentCycleCredit());
        assertEquals("+           .00", response.account().currentCycleDebit());

        assertEquals("000000027", response.customer().customerId());
        assertEquals("980-16-1210", response.customer().ssn());
        assertEquals("1986-11-08", response.customer().dateOfBirth());
        assertEquals(78, response.customer().ficoScore());
        assertEquals("Ward", response.customer().firstName());
        assertEquals("Henri", response.customer().middleName());
        assertEquals("Jones", response.customer().lastName());
        assertEquals("210 Amaya Turnpike", response.customer().addressLine1());
        assertEquals("Suite 180", response.customer().addressLine2());
        assertEquals("Port Dwight", response.customer().city());
        assertEquals("GU", response.customer().stateCode());
        assertEquals("USA", response.customer().countryCode());
        assertEquals("00000000000881558757", response.customer().governmentIssuedId());
        assertEquals("0050024139", response.customer().eftAccountId());
        assertEquals("Y", response.customer().primaryCardHolder());
    }

    @Test
    @DisplayName("DV-05 — the full 10-character ZIP and both full phone numbers are returned, not the BMS 5/13-byte windows")
    void dv05FullZipAndPhone() {
        chainFound();

        AccountViewResponse response = service.view("00000000027");

        assertEquals("07923-8822", response.customer().zipCode());
        assertEquals("(935)027-1145", response.customer().phone1());
        assertEquals("(103)537-5007", response.customer().phone2());
    }

    @Test
    @DisplayName("DV-06 / D-0038 — Account Group shows the stored ACCT-GROUP-ID 'A000000000' (cbl:490)")
    void dv06AccountGroupId() {
        chainFound();

        assertEquals("A000000000", service.view("00000000027").account().groupId());
    }

    @Test
    @DisplayName("Q-04 — a multi-card account resolves through the LOWEST card number (AIX first-record substitute)")
    void q04LowestCardNumberWins() {
        when(xrefs.findFirstByXrefAcctIdOrderByXrefCardNumberAsc(99999999999L))
                .thenReturn(Optional.of(xref("4000000000000001", 1L, 99999999999L)));
        Account account = account27();
        account.setAcctId(99999999999L);
        when(accounts.findById(99999999999L)).thenReturn(Optional.of(account));
        Customer customer = customer27();
        customer.setCustId(1L);
        when(customers.findById(1L)).thenReturn(Optional.of(customer));

        AccountViewResponse response = service.view("99999999999");

        assertEquals("99999999999", response.accountNumber());
        assertEquals("000000001", response.customer().customerId());
        verify(xrefs).findFirstByXrefAcctIdOrderByXrefCardNumberAsc(99999999999L);
        verify(customers, never()).findById(27L);
    }

    @Test
    @DisplayName("FR-21 — the program writes nothing: no save on any repository (cbl:687-870 are three READs)")
    void fr21ReadOnly() {
        chainFound();

        assertEquals("00000000027", service.view("00000000027").accountNumber());
        verify(accounts, never()).save(org.mockito.ArgumentMatchers.any());
        verify(customers, never()).save(org.mockito.ArgumentMatchers.any());
        verify(xrefs, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
