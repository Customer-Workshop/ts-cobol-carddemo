package com.carddemo.batch;

import com.carddemo.model.Account;
import com.carddemo.model.TransactionCategoryBalance;
import com.carddemo.repository.AccountRepository;
import com.carddemo.service.TransactionIdGenerator;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.ItemStreamReader;

import java.util.ArrayList;
import java.util.List;

class DailyTransactionReader implements ItemStreamReader<DailyTransactionRecord> {
    private final FlatFileItemReader<String> delegate;

    DailyTransactionReader(FlatFileItemReader<String> delegate) {
        this.delegate = delegate;
    }

    @Override
    public DailyTransactionRecord read() throws Exception {
        String line;
        while ((line = delegate.read()) != null) {
            if (!line.isBlank()) {
                return DailyTransactionRecord.parse(BatchFileSupport.pad(line, 350));
            }
        }
        return null;
    }

    @Override
    public void open(ExecutionContext context) {
        try {
            delegate.open(context);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Override
    public void update(ExecutionContext context) {
        delegate.update(context);
    }

    @Override
    public void close() {
        delegate.close();
    }
}

class RepositorySequenceReader implements ItemStreamReader<Object> {
    private final List<ItemStreamReader<?>> readers;
    private int index;

    RepositorySequenceReader(List<ItemStreamReader<?>> readers) {
        this.readers = readers;
    }

    @Override
    public Object read() throws Exception {
        while (index < readers.size()) {
            Object value = readers.get(index).read();
            if (value != null) {
                return value;
            }
            index++;
        }
        return null;
    }

    @Override
    public void open(ExecutionContext context) {
        index = 0;
        readers.forEach(reader -> {
            try {
                reader.open(context);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    @Override
    public void update(ExecutionContext context) {
        readers.forEach(reader -> {
            try {
                reader.update(context);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    @Override
    public void close() {
        for (ItemStreamReader<?> reader : readers) {
            try {
                reader.close();
            } catch (RuntimeException exception) {
                throw exception;
            }
        }
    }
}

class AccountInterestReader implements ItemStreamReader<BatchJobService.InterestWork> {
    private final ItemStreamReader<TransactionCategoryBalance> delegate;
    private final AccountRepository accounts;
    private final BatchJobService service;
    private final InterestTransactionIds tranIds;
    private Long accountId;
    private final List<TransactionCategoryBalance> group = new ArrayList<>();
    private TransactionCategoryBalance lookahead;
    private boolean exhausted;

    AccountInterestReader(ItemStreamReader<TransactionCategoryBalance> delegate,
                          AccountRepository accounts, BatchJobService service,
                          String parmDate, TransactionIdGenerator ids) {
        this.delegate = delegate;
        this.accounts = accounts;
        this.service = service;
        this.tranIds = new InterestTransactionIds(parmDate, ids);
    }

    @Override
    public BatchJobService.InterestWork read() throws Exception {
        if (exhausted && group.isEmpty()) {
            return null;
        }
        while (true) {
            TransactionCategoryBalance value = lookahead;
            lookahead = null;
            if (value == null) {
                value = delegate.read();
            }
            if (value == null) {
                exhausted = true;
                return flush();
            }
            Long valueAccount = value.getId().getAcctId();
            if (accountId == null) {
                accountId = valueAccount;
            }
            if (!accountId.equals(valueAccount)) {
                lookahead = value;
                return flush();
            }
            group.add(value);
        }
    }

    private BatchJobService.InterestWork flush() {
        if (group.isEmpty()) {
            return null;
        }
        // 1100-GET-ACCT-DATA (CBACT04C.cbl:372-391): the keyed ACCTFILE read at
        // each account break. INVALID KEY (status 23) displays 'ACCOUNT NOT
        // FOUND' and abends — a missing account fails the step (S15-B7).
        Account account = accounts.findById(accountId).orElseThrow(() ->
                new InterestAbendException("ACCOUNT NOT FOUND: " + accountId
                        + " - ACCTFILE read status 23"));
        List<TransactionCategoryBalance> balances = new ArrayList<>(group);
        group.clear();
        accountId = null;
        return service.calculateInterest(account, balances, tranIds::next);
    }

    @Override
    public void open(ExecutionContext context) {
        try {
            delegate.open(context);
        } catch (RuntimeException exception) {
            throw exception;
        }
        accountId = null;
        group.clear();
        lookahead = null;
        exhausted = false;
    }

    @Override
    public void update(ExecutionContext context) {
        delegate.update(context);
    }

    @Override
    public void close() {
        delegate.close();
    }
}
