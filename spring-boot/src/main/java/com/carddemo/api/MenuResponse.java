package com.carddemo.api;

import java.util.List;

public record MenuResponse(String menu, List<MenuOption> options) {
}
