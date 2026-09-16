package com.carddemo.ui;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Controller
public class UiController {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @ModelAttribute
    void addHeaderFields(Model model) {
        LocalDateTime now = LocalDateTime.now();
        model.addAttribute("currentDate", now.format(DATE_FORMAT));
        model.addAttribute("currentTime", now.format(TIME_FORMAT));
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }
}
