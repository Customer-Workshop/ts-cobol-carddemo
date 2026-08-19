package com.carddemo.api;

public record MenuOption(int number, String name, String program, String endpoint,
                         String requiredUserType, boolean implemented, boolean available) {
}
