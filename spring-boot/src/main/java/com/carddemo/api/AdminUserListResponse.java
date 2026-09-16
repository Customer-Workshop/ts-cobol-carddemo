package com.carddemo.api;

import java.util.List;

public record AdminUserListResponse(int page, int pageSize, boolean hasNextPage,
                                    boolean hasPreviousPage, List<AdminUserResponse> users) {
}
