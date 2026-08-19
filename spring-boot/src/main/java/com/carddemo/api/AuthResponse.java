package com.carddemo.api;

public record AuthResponse(String userId, String userType, String landingTarget) {
}
