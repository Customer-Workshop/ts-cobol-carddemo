package com.carddemo.service;

import com.carddemo.api.CobolMessages;
import com.carddemo.api.PendingAuthKey;
import com.carddemo.model.AuthFraud;
import com.carddemo.model.PendingAuthDetail;
import com.carddemo.repository.AuthFraudRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * COPAUS2C: the AUTHFRDS journal write the detail screen LINKs to. The
 * INSERT-then-(-803)->UPDATE pair becomes a find-then-insert-or-update
 * upsert on the (card_num, auth_ts) key — same observable contract
 * (S19-B5), 'ADD SUCCESS' for the insert and 'UPDT SUCCESS' for the retry.
 */
@Service
public class AuthFraudService {

    public record FraudResult(boolean success, String message) {
    }

    private final AuthFraudRepository repository;

    public AuthFraudService(AuthFraudRepository repository) {
        this.repository = repository;
    }

    public FraudResult journal(Long acctId, Long custId, PendingAuthDetail record, char action) {
        LocalDateTime authTs = PendingAuthKey.realTimestamp(record);
        AuthFraud.Id id = new AuthFraud.Id(record.getCardNum(), authTs);
        try {
            AuthFraud existing = repository.findById(id).orElse(null);
            if (existing != null) {
                // FRAUD-UPDATE (COPAUS2C.cbl:222-246): flag + CURRENT DATE.
                existing.setAuthFraud(String.valueOf(action));
                existing.setFraudRptDate(LocalDate.now());
                repository.save(existing);
                return new FraudResult(true, CobolMessages.PENDING_AUTH_UPDT_SUCCESS);
            }
            AuthFraud row = new AuthFraud();
            row.setId(id);
            row.setAuthType(record.getAuthType());
            row.setCardExpiryDate(record.getCardExpiryDate());
            row.setMessageType(record.getMessageType());
            row.setMessageSource(record.getMessageSource());
            row.setAuthIdCode(record.getAuthIdCode());
            row.setAuthRespCode(record.getAuthRespCode());
            row.setAuthRespReason(record.getAuthRespReason());
            row.setProcessingCode(record.getProcessingCode() == null
                    ? null : "%06d".formatted(record.getProcessingCode()));
            row.setTransactionAmt(record.getTransactionAmt());
            row.setApprovedAmt(record.getApprovedAmt());
            row.setMerchantCategoryCode(record.getMerchantCategoryCode());
            row.setAcqrCountryCode(record.getAcqrCountryCode());
            row.setPosEntryMode(record.getPosEntryMode());
            row.setMerchantId(record.getMerchantId());
            row.setMerchantName(record.getMerchantName());
            row.setMerchantCity(record.getMerchantCity());
            row.setMerchantState(record.getMerchantState());
            row.setMerchantZip(record.getMerchantZip());
            row.setTransactionId(record.getTransactionId());
            row.setMatchStatus(record.getMatchStatus());
            row.setAuthFraud(String.valueOf(action));
            row.setFraudRptDate(LocalDate.now());
            row.setAcctId(acctId);
            row.setCustId(custId);
            repository.save(row);
            return new FraudResult(true, CobolMessages.PENDING_AUTH_ADD_SUCCESS);
        } catch (DataAccessException exception) {
            return new FraudResult(false,
                    CobolMessages.pendingAuthDb2Error("-00001", "HY000"));
        }
    }
}
