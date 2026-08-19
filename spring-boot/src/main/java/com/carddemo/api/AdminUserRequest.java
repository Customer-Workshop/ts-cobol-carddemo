package com.carddemo.api;

public record AdminUserRequest(String userId, String firstName, String lastName,
                               String password, String userType) {
}
