package com.carddemo.api;

import java.util.List;

public record CardListResponse(int page, int pageSize, boolean hasNextPage,
                               boolean hasPreviousPage, List<CardListRow> cards) {
}
