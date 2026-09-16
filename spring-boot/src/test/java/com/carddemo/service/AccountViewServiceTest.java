package com.carddemo.service;

import com.carddemo.api.AccountViewResponse;
import com.carddemo.api.CobolApiException;
import com.carddemo.model.Account;
import com.carddemo.model.CardXref;
import com.carddemo.model.Customer;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * COACTVWC resolution-path parity: every FR-S02 row maps to a named test,
 * with expectations derived from the COBOL line range cited on the row —
 * never from this implementation.
 */
class AccountViewServiceTest {

    private final CardXrefRepository xrefRepository = mock(CardXrefRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final AccountViewService service =
            new AccountViewService(xrefRepository, accountRepository, customerRepository);

    @Test
    void initialScreenIsPromptOnly_frS0201() {
        AccountViewScreen screen = service.initialScreen();

        assertThat(screen.accountEcho()).isEmpty();
        assertThat(screen.accountFieldRed()).isFalse();
        assertThat(screen.errorMessage()).isNull();
        assertThat(screen.account()).isNull();
        assertThat(screen.customer()).isNull();
    }

    @Test
    void blankAndStarInputsAreNoInputReceived_frS0202() {
        // cbl:628-633 — `*` and all-spaces are replaced by LOW-VALUES, and
        // cbl:640-642 overwrites the intermediate prompt with NO-INPUT.
        for (String input : new String[] {"", "   ", "*", "*   "}) {
            AccountViewScreen screen = service.viewScreen(input);

            assertThat(screen.errorMessage()).isEqualTo("No input received");
            assertThat(screen.accountEcho()).isEqualTo("*");
            assertThat(screen.accountFieldRed()).isTrue();
            assertThat(screen.account()).isNull();
            assertThat(screen.customer()).isNull();
        }
        assertThatThrownBy(() -> service.view("*"))
                .isInstanceOfSatisfying(CobolApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo("No input received");
                });
    }

    @Test
    void nonElevenDigitInputsAreFilterErrors_frS0203() {
        // cbl:666-676 — the X(11) field edits: "0000000001 " keeps its
        // trailing space so it fails NUMERIC; "000000000012" truncates to
        // "00000000001" before the edits and proceeds to the reads.
        for (String input : new String[] {"123", "1234567890a", "00000000000", "0000000001 "}) {
            AccountViewScreen screen = service.viewScreen(input);

            assertThat(screen.errorMessage())
                    .isEqualTo("Account Filter must  be a non-zero 11 digit number");
            assertThat(screen.accountEcho()).isEqualTo(input.replaceAll("\\s+$", ""));
            assertThat(screen.accountFieldRed()).isTrue();
            assertThat(screen.account()).isNull();
        }
        assertThatThrownBy(() -> service.view("123"))
                .isInstanceOfSatisfying(CobolApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage())
                            .isEqualTo("Account Filter must  be a non-zero 11 digit number");
                });
        verify(xrefRepository, never()).findByXrefAcctId(anyLong());

        when(xrefRepository.findByXrefAcctId(1L)).thenReturn(List.of());
        AccountViewScreen truncated = service.viewScreen("000000000012");
        assertThat(truncated.accountEcho()).isEqualTo("00000000001");
        assertThat(truncated.errorMessage()).contains("Cross ref file");
    }

    @Test
    void xrefNotFoundRendersVerbatimMessage_frS0204() {
        when(xrefRepository.findByXrefAcctId(2L)).thenReturn(List.of());

        AccountViewScreen screen = service.viewScreen("00000000002");

        assertThat(screen.errorMessage()).isEqualTo(
                "Account:00000000002 not found in Cross ref file.  Resp:000000013  Reas:0000");
        assertThat(screen.accountEcho()).isEqualTo("00000000002");
        assertThat(screen.accountFieldRed()).isTrue();
        assertThat(screen.account()).isNull();
        assertThat(screen.customer()).isNull();
        // cbl:697 — the acct and customer reads are skipped on this failure.
        verify(accountRepository, never()).findById(anyLong());
        verify(customerRepository, never()).findById(anyLong());

        assertThatThrownBy(() -> service.view("00000000002"))
                .isInstanceOfSatisfying(CobolApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo(
                            "Account:00000000002 not found in Cross ref file.  Resp:000000013  Reas:0000");
                });
    }

    @Test
    void acctNotFoundRendersVerbatimMessage_frS0205() {
        when(xrefRepository.findByXrefAcctId(3L))
                .thenReturn(List.of(xref("1111222233334444", 1L, 3L)));
        when(accountRepository.findById(3L)).thenReturn(Optional.empty());

        AccountViewScreen screen = service.viewScreen("00000000003");

        assertThat(screen.errorMessage()).isEqualTo(
                "Account:00000000003 not found in Acct Master file.Resp:000000013  Reas:0000");
        assertThat(screen.accountEcho()).isEqualTo("00000000003");
        assertThat(screen.accountFieldRed()).isTrue();
        assertThat(screen.account()).isNull();
        assertThat(screen.customer()).isNull();

        assertThatThrownBy(() -> service.view("00000000003"))
                .isInstanceOfSatisfying(CobolApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo(
                            "Account:00000000003 not found in Acct Master file.Resp:000000013  Reas:0000");
                });
    }

    @Test
    void custNotFoundKeepsAccountBlockWithoutRed_frS0206() {
        when(xrefRepository.findByXrefAcctId(4L))
                .thenReturn(List.of(xref("1111222233334444", 9L, 4L)));
        when(accountRepository.findById(4L)).thenReturn(Optional.of(account(4L)));
        when(customerRepository.findById(9L)).thenReturn(Optional.empty());

        AccountViewScreen screen = service.viewScreen("00000000004");

        assertThat(screen.errorMessage()).isEqualTo(
                "CustId:000000009 not found in customer master.Resp: 000000013  REAS:0000000");
        assertThat(screen.accountEcho()).isEqualTo("00000000004");
        // cbl:840-841 — FLG-CUSTFILTER-NOT-OK is set, not the acct flag.
        assertThat(screen.accountFieldRed()).isFalse();
        assertThat(screen.account()).isNotNull();
        assertThat(screen.account().currentBalance()).isEqualTo("+        194.00");
        assertThat(screen.customer()).isNull();

        assertThatThrownBy(() -> service.view("00000000004"))
                .isInstanceOfSatisfying(CobolApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo(
                            "CustId:000000009 not found in customer master.Resp: 000000013  REAS:0000000");
                });
    }

    @Test
    void fullHitRendersBothBlocksAndRestView_frS0207() {
        when(xrefRepository.findByXrefAcctId(1L))
                .thenReturn(List.of(xref("1111222233334444", 1L, 1L)));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(1L)));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L)));

        AccountViewScreen screen = service.viewScreen("00000000001");

        assertThat(screen.errorMessage()).isNull();
        assertThat(screen.accountEcho()).isEqualTo("00000000001");
        assertThat(screen.accountFieldRed()).isFalse();
        assertThat(screen.account().activeStatus()).isEqualTo("Y");
        assertThat(screen.account().openDate()).isEqualTo("2020-01-01");
        assertThat(screen.account().creditLimit()).isEqualTo("+      2,020.00");
        assertThat(screen.account().expirationDate()).isEqualTo("2025-01-01");
        assertThat(screen.account().cashCreditLimit()).isEqualTo("+      1,020.00");
        assertThat(screen.account().reissueDate()).isEqualTo("2025-01-01");
        assertThat(screen.account().currentBalance()).isEqualTo("+        194.00");
        assertThat(screen.account().accountGroup()).isEqualTo("02108");
        assertThat(screen.customer().customerId()).isEqualTo("000000001");
        assertThat(screen.customer().ssn()).isEqualTo("123-45-6789");
        assertThat(screen.customer().ficoScore()).isEqualTo("800");

        AccountViewResponse response = service.view("00000000001");
        assertThat(response.accountId()).isEqualTo(1L);
        assertThat(response.currentBalance()).isEqualByComparingTo("194.00");
        assertThat(response.ssn()).isEqualTo("123-45-6789");
    }

    @Test
    void signedAmountEditorMatchesCobolPicout_frS0208() {
        // '+ZZZ,ZZZ,ZZZ.99' (bms:118): zero-suppression blanks the leading
        // commas too, and a tenth integer digit is dropped on overflow.
        assertThat(CobolFormat.editSignedAmount(new BigDecimal("0")))
                .isEqualTo("+           .00");
        assertThat(CobolFormat.editSignedAmount(new BigDecimal("1940.00")))
                .isEqualTo("+      1,940.00");
        assertThat(CobolFormat.editSignedAmount(new BigDecimal("-12.5")))
                .isEqualTo("-         12.50");
        assertThat(CobolFormat.editSignedAmount(new BigDecimal("9999999999.99")))
                .isEqualTo("+999,999,999.99");
    }

    @Test
    void customerBlockDerivations_frS0209() {
        // cbl:493-523 — SSN is STRINGed nnn-nn-nnnn, numeric fields print
        // zero-padded to their map widths, and alphanumerics truncate.
        Customer wide = customer(9L);
        wide.setCustFicoCreditScore(80);
        wide.setCustAddrZip("0210812345");
        wide.setCustPhoneNum1("123456789012345");
        wide.setCustPhoneNum2(null);
        wide.setCustFirstName("ABCDEFGHIJKLMNOPQRSTUVWXYZ");

        when(xrefRepository.findByXrefAcctId(4L))
                .thenReturn(List.of(xref("1111222233334444", 9L, 4L)));
        when(accountRepository.findById(4L)).thenReturn(Optional.of(account(4L)));
        when(customerRepository.findById(9L)).thenReturn(Optional.of(wide));

        AccountViewScreen.CustomerBlock block = service.viewScreen("00000000004").customer();

        assertThat(block.customerId()).isEqualTo("000000009");
        assertThat(block.ssn()).isEqualTo("123-45-6789");
        assertThat(block.ficoScore()).isEqualTo("080");
        assertThat(block.zip()).isEqualTo("02108");
        assertThat(block.phoneNumber1()).isEqualTo("1234567890123");
        assertThat(block.phoneNumber2()).isEmpty();
        assertThat(block.firstName()).isEqualTo("ABCDEFGHIJKLMNOPQRSTUVWXY");
        assertThat(block.dateOfBirth()).isEqualTo("1815-12-10");
    }

    @Test
    void storeFailuresRenderFileErrorPerFile_frS0213() {
        // S02-B2 / WS-FILE-ERROR-MESSAGE (cbl:121-127): a non-NOTFND RESP on
        // each keyed read renders the fixed file-error layout; only a
        // customer-side failure keeps the account block.
        when(xrefRepository.findByXrefAcctId(1L))
                .thenThrow(new DataAccessResourceFailureException("store down"));
        AccountViewScreen xrefFail = service.viewScreen("00000000001");
        assertThat(xrefFail.errorMessage()).isEqualTo(
                "File Error: READ     on CXACAIX   returned RESP 000000017 ,RESP2 000000120 ");
        assertThat(xrefFail.accountFieldRed()).isTrue();
        assertThat(xrefFail.account()).isNull();

        when(xrefRepository.findByXrefAcctId(2L))
                .thenReturn(List.of(xref("1111222233334444", 1L, 2L)));
        when(accountRepository.findById(2L))
                .thenThrow(new DataAccessResourceFailureException("store down"));
        AccountViewScreen acctFail = service.viewScreen("00000000002");
        assertThat(acctFail.errorMessage()).isEqualTo(
                "File Error: READ     on ACCTDAT   returned RESP 000000017 ,RESP2 000000120 ");
        assertThat(acctFail.accountFieldRed()).isTrue();
        assertThat(acctFail.account()).isNull();

        when(xrefRepository.findByXrefAcctId(4L))
                .thenReturn(List.of(xref("1111222233334444", 9L, 4L)));
        when(accountRepository.findById(4L)).thenReturn(Optional.of(account(4L)));
        when(customerRepository.findById(9L))
                .thenThrow(new DataAccessResourceFailureException("store down"));
        AccountViewScreen custFail = service.viewScreen("00000000004");
        assertThat(custFail.errorMessage()).isEqualTo(
                "File Error: READ     on CUSTDAT   returned RESP 000000017 ,RESP2 000000120 ");
        assertThat(custFail.accountFieldRed()).isFalse();
        assertThat(custFail.account()).isNotNull();
        assertThat(custFail.customer()).isNull();

        // The REST surface propagates the store failure (S02-B2 maps the
        // RESP-OTHER branch to the platform error path, not a 404).
        assertThatThrownBy(() -> service.view("00000000004"))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    private Account account(long id) {
        Account account = new Account();
        account.setAcctId(id);
        account.setAcctActiveStatus("Y");
        account.setAcctCurrBal(new BigDecimal("194.00"));
        account.setAcctCreditLimit(new BigDecimal("2020.00"));
        account.setAcctCashCreditLimit(new BigDecimal("1020.00"));
        account.setAcctCurrCycCredit(BigDecimal.ZERO);
        account.setAcctCurrCycDebit(BigDecimal.ZERO);
        account.setAcctOpenDate(LocalDate.of(2020, 1, 1));
        account.setAcctExpirationDate(LocalDate.of(2025, 1, 1));
        account.setAcctReissueDate(LocalDate.of(2025, 1, 1));
        account.setAcctGroupId("02108");
        return account;
    }

    private CardXref xref(String cardNumber, long custId, long acctId) {
        CardXref xref = new CardXref();
        xref.setXrefCardNumber(cardNumber);
        xref.setXrefCustId(custId);
        xref.setXrefAcctId(acctId);
        return xref;
    }

    private Customer customer(long id) {
        Customer customer = new Customer();
        customer.setCustId(id);
        customer.setCustFirstName("Ada");
        customer.setCustMiddleName("Lovelace");
        customer.setCustLastName("Byron");
        customer.setCustAddrLine1("1 Main");
        customer.setCustAddrStateCode("MA");
        customer.setCustAddrCountryCode("USA");
        customer.setCustAddrZip("02108");
        customer.setCustPhoneNum1("555");
        customer.setCustSsn(123456789L);
        customer.setCustGovernmentIssuedId("GOV");
        customer.setCustDob(LocalDate.of(1815, 12, 10));
        customer.setCustEftAccountId("EFT");
        customer.setCustPrimaryCardHolderIndicator("Y");
        customer.setCustFicoCreditScore(800);
        return customer;
    }
}
