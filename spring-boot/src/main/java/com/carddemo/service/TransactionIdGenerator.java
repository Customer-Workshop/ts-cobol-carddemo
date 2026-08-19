package com.carddemo.service;

import com.carddemo.model.Transaction;
import com.carddemo.repository.TransactionRepository;
import org.springframework.stereotype.Component;

@Component
public class TransactionIdGenerator {
    private final TransactionRepository repository;

    public TransactionIdGenerator(TransactionRepository repository) {
        this.repository = repository;
    }

    public String nextId() {
        Transaction last = repository.findTopByOrderByTranIdDesc();
        if (last == null) {
            return "0000000000000001";
        }
        return nextIdAfter(last.getTranId());
    }

    public String nextIdAfter(String currentId) {
        return "%016d".formatted(Long.parseLong(currentId) + 1);
    }
}
