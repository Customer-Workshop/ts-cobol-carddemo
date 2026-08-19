package com.carddemo.service;

import com.carddemo.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Service
public class ReportService {
    public ReportAcceptedResponse request(ReportRequest request) {
        if (request == null || request.reportName() == null || request.reportName().isBlank()) {
            throw bad(CobolMessages.REPORT_TYPE_REQUIRED);
        }
        LocalDate start = parse(request.startDate(), CobolMessages.REPORT_START_INVALID);
        LocalDate end = parse(request.endDate(), CobolMessages.REPORT_END_INVALID);
        if (end.isBefore(start)) throw bad(CobolMessages.REPORT_RANGE_INVALID);
        if (!"Y".equalsIgnoreCase(request.confirmation())) throw bad(
                CobolMessages.reportConfirm(request.reportName().trim()));
        return new ReportAcceptedResponse(request.reportName().trim(), start.toString(), end.toString(),
                "accepted; Spring Batch launch pending");
    }

    private LocalDate parse(String value, String message) {
        try {
            if (value == null || value.isBlank()) throw new DateTimeParseException("", value, 0);
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw bad(message);
        }
    }

    private CobolApiException bad(String message) {
        return new CobolApiException(HttpStatus.BAD_REQUEST, message);
    }
}
