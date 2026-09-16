package com.carddemo.service;

import com.carddemo.api.*;
import com.carddemo.batch.BatchJobLauncherService;
import org.springframework.batch.core.JobExecution;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@Service
public class ReportService {
    private final BatchJobLauncherService launcher;

    public ReportService(BatchJobLauncherService launcher) {
        this.launcher = launcher;
    }

    public ReportAcceptedResponse request(ReportRequest request) {
        if (request == null || request.reportName() == null || request.reportName().isBlank()) {
            throw bad(CobolMessages.REPORT_TYPE_REQUIRED);
        }
        String report = request.reportName().trim();
        LocalDate start;
        LocalDate end;
        if ("monthly".equalsIgnoreCase(report)) {
            YearMonth month = YearMonth.now();
            start = month.atDay(1);
            end = month.atEndOfMonth();
            report = "Monthly";
        } else if ("yearly".equalsIgnoreCase(report)) {
            int year = LocalDate.now().getYear();
            start = LocalDate.of(year, 1, 1);
            end = LocalDate.of(year, 12, 31);
            report = "Yearly";
        } else if ("custom".equalsIgnoreCase(report)) {
            start = parse(request.startDate(), CobolMessages.REPORT_START_INVALID);
            end = parse(request.endDate(), CobolMessages.REPORT_END_INVALID);
            report = "Custom";
        } else {
            throw bad(CobolMessages.REPORT_TYPE_REQUIRED);
        }
        if (end.isBefore(start)) throw bad(CobolMessages.REPORT_RANGE_INVALID);
        if (!"Y".equalsIgnoreCase(request.confirmation())) throw bad(
                CobolMessages.reportConfirm(report));
        JobExecution execution = launcher.launch("cbtrn03Job",
                java.util.Map.of("startDate", start.toString(), "endDate", end.toString()));
        return new ReportAcceptedResponse(report, start.toString(), end.toString(),
                "accepted; Spring Batch launch pending", execution.getId());
    }

    private LocalDate parse(String value, String message) {
        if (value == null || value.isBlank()) throw bad(message);
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw bad(message);
        }
    }

    private CobolApiException bad(String message) {
        return new CobolApiException(HttpStatus.BAD_REQUEST, message);
    }
}
