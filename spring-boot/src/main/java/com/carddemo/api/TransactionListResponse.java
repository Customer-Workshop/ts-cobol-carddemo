package com.carddemo.api;

import java.util.List;

public record TransactionListResponse(
        int page,
        int pageSize,
        boolean hasNextPage,
        boolean hasPreviousPage,
        List<TransactionListRow> transactions) {
}
