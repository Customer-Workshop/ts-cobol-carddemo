package com.carddemo.api;

public record MenuOption(int number, String name, String program, String endpoint,
                         boolean implemented, boolean available) {
}
