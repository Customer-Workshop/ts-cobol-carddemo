package com.carddemo.api;

import jakarta.validation.constraints.NotNull;

public record AuthRequest(@NotNull String userId, @NotNull String password) {
}
