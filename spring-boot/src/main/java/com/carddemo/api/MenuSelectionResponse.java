package com.carddemo.api;

public record MenuSelectionResponse(int option, String name, String program, String endpoint,
                                    boolean implemented, String message) {
}
