package com.carddemo.api;

import com.carddemo.service.ReportService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @PostMapping
    public ReportAcceptedResponse request(@RequestBody ReportRequest request) {
        return service.request(request);
    }
}
