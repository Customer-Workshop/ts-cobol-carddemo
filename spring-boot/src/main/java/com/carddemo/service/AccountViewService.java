package com.carddemo.service;

import com.carddemo.api.AccountViewResponse;
import com.carddemo.api.CobolApiException;
import com.carddemo.api.CobolMessages;
import com.carddemo.model.Account;
import com.carddemo.model.CardXref;
import com.carddemo.model.Customer;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.CustomerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AccountViewService {
    private final CardXrefRepository cardXrefRepository;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;

    public AccountViewService(CardXrefRepository cardXrefRepository, AccountRepository accountRepository,
                              CustomerRepository customerRepository) {
        this.cardXrefRepository = cardXrefRepository;
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
    }

    public AccountViewResponse view(String rawAccountId) {
        if (rawAccountId == null || !rawAccountId.matches("\\d{11}")
                || rawAccountId.chars().allMatch(character -> character == '0')) {
            throw new CobolApiException(HttpStatus.BAD_REQUEST, CobolMessages.ACCOUNT_FILTER_INVALID);
        }
        Long accountId = Long.parseLong(rawAccountId);
        List<CardXref> xrefs = cardXrefRepository.findByXrefAcctId(accountId);
        if (xrefs.isEmpty()) {
            throw new CobolApiException(HttpStatus.NOT_FOUND, CobolMessages.xrefNotFound(rawAccountId));
        }
        CardXref xref = xrefs.getFirst();
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new CobolApiException(HttpStatus.NOT_FOUND,
                        CobolMessages.accountNotFound(rawAccountId)));
        Customer customer = customerRepository.findById(xref.getXrefCustId())
                .orElseThrow(() -> new CobolApiException(HttpStatus.NOT_FOUND,
                        CobolMessages.customerNotFound(String.valueOf(xref.getXrefCustId()))));
        return map(account, customer);
    }

    private AccountViewResponse map(Account account, Customer customer) {
        return new AccountViewResponse(account.getAcctId(), account.getAcctActiveStatus(),
                account.getAcctCurrBal(), account.getAcctCreditLimit(), account.getAcctCashCreditLimit(),
                account.getAcctOpenDate(), account.getAcctExpirationDate(), account.getAcctReissueDate(),
                account.getAcctCurrCycCredit(), account.getAcctCurrCycDebit(), account.getAcctGroupId(),
                customer.getCustId(), formatSsn(customer.getCustSsn()), customer.getCustDob(),
                customer.getCustFicoCreditScore(), customer.getCustFirstName(), customer.getCustMiddleName(),
                customer.getCustLastName(), customer.getCustAddrLine1(), customer.getCustAddrLine2(),
                customer.getCustAddrLine3(), customer.getCustAddrStateCode(), customer.getCustAddrZip(),
                customer.getCustAddrCountryCode(), customer.getCustPhoneNum1(), customer.getCustPhoneNum2(),
                customer.getCustGovernmentIssuedId(), customer.getCustEftAccountId(),
                customer.getCustPrimaryCardHolderIndicator());
    }

    private String formatSsn(Long ssn) {
        if (ssn == null) {
            return null;
        }
        String value = "%09d".formatted(ssn);
        return value.substring(0, 3) + "-" + value.substring(3, 5) + "-" + value.substring(5);
    }
}
