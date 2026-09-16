package com.carddemo.api;

import jakarta.validation.constraints.NotNull;

public record MenuSelectRequest(@NotNull String option) {
}
