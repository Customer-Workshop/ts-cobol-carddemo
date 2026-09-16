package com.carddemo.batch;

import com.carddemo.model.Account;
import com.carddemo.model.TransactionCategoryBalance;
import com.carddemo.repository.AccountRepository;
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
    private Long accountId;
    private final List<TransactionCategoryBalance> group = new ArrayList<>();
    private TransactionCategoryBalance lookahead;
    private boolean exhausted;

    AccountInterestReader(ItemStreamReader<TransactionCategoryBalance> delegate,
                          AccountRepository accounts, BatchJobService service) {
        this.delegate = delegate;
        this.accounts = accounts;
        this.service = service;
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
        Account account = accounts.findById(accountId).orElse(null);
        List<TransactionCategoryBalance> balances = new ArrayList<>(group);
        group.clear();
        accountId = null;
        return account == null ? readUnchecked() : service.calculateInterest(account, balances);
    }

    private BatchJobService.InterestWork readUnchecked() {
        try {
            return read();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
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
