package com.carddemo.api;

/** XCTL target: the program to transfer to plus the caller it returns to. */
public record TranTypeNavigation(String program, String fromProgram) {
}
