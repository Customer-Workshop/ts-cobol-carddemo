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
    private static final String DATE_MASK = "YYYY-MM-DD";

    private final BatchJobLauncherService launcher;
    private final DateValidationService dateValidation;

    public ReportService(BatchJobLauncherService launcher,
                         DateValidationService dateValidation) {
        this.launcher = launcher;
        this.dateValidation = dateValidation;
    }

    /**
     * CORPT00C PROCESS-ENTER-KEY (app/cbl/CORPT00C.cbl:208-456): the
     * report-type EVALUATE picks the first non-blank flag, the custom
     * branch runs the six field edits in source order, and a clean pass
     * flows to SUBMIT-JOB-TO-INTRDR — the confirm gate, then the TDQ
     * submit, which lands here as the {@link #request} launch seam
     * (S10-B1). Every SEND-TRNRPT-SCREEN ends the turn (:556-591), so the
     * first failing edit is the message the screen shows.
     */
    public ReportScreen enter(ReportForm form) {
        if (nonBlank(form.monthly())) {
            YearMonth month = YearMonth.now();
            return submit("Monthly", month.atDay(1), month.atEndOfMonth(), form);
        }
        if (nonBlank(form.yearly())) {
            int year = LocalDate.now().getYear();
            return submit("Yearly", LocalDate.of(year, 1, 1),
                    LocalDate.of(year, 12, 31), form);
        }
        if (!nonBlank(form.custom())) {
            return ReportScreen.preserved(form, CobolMessages.REPORT_TYPE_REQUIRED,
                    "monthly");
        }
        String empty = firstBlank(form);
        if (empty != null) {
            return ReportScreen.preserved(form, emptyMessage(empty),
                    empty.toLowerCase());
        }
        // NUMVAL-C pass: fields below carry the normalized values.
        ReportForm normalized = form.normalized();
        String invalid = firstInvalid(normalized);
        if (invalid != null) {
            return ReportScreen.preserved(normalized, invalidMessage(invalid),
                    invalid.toLowerCase());
        }
        String start = iso(normalized.sdtyyyy(), normalized.sdtmm(), normalized.sdtdd());
        String end = iso(normalized.edtyyyy(), normalized.edtmm(), normalized.edtdd());
        if (!accepted(dateValidation.validate(start, DATE_MASK))) {
            return ReportScreen.preserved(normalized,
                    CobolMessages.REPORT_START_INVALID, "sdtmm");
        }
        if (!accepted(dateValidation.validate(end, DATE_MASK))) {
            return ReportScreen.preserved(normalized,
                    CobolMessages.REPORT_END_INVALID, "edtmm");
        }
        // D-1: CORPT00C never compares the range; the target rejects an
        // inverted one (strictly safer than the legacy silent empty report).
        if (LocalDate.parse(end).isBefore(LocalDate.parse(start))) {
            return ReportScreen.preserved(normalized,
                    CobolMessages.REPORT_RANGE_INVALID, "edtmm");
        }
        return submit("Custom", LocalDate.parse(start), LocalDate.parse(end),
                normalized);
    }

    /**
     * SUBMIT-JOB-TO-INTRDR (:462-535): confirm-blank prompt, the Y/N/other
     * EVALUATE, then the job write — a launch failure maps to the WRITEQ
     * non-NORMAL outcome.
     */
    private ReportScreen submit(String name, LocalDate start, LocalDate end,
                                ReportForm echo) {
        String confirm = echo.confirm() == null ? "" : echo.confirm().trim();
        if (confirm.isEmpty()) {
            return ReportScreen.preserved(echo, CobolMessages.reportConfirm(name),
                    "confirm");
        }
        if ("y".equalsIgnoreCase(confirm)) {
            try {
                request(new ReportRequest(name, start.toString(), end.toString(), "Y"));
            } catch (RuntimeException exception) {
                return ReportScreen.preserved(echo,
                        CobolMessages.REPORT_TDQ_WRITE_FAILED, "monthly");
            }
            return ReportScreen.submitted(name);
        }
        if ("n".equalsIgnoreCase(confirm)) {
            return ReportScreen.blank();
        }
        return ReportScreen.preserved(echo,
                CobolMessages.reportConfirmInvalid(confirm), "confirm");
    }

    // First failing blank edit, in source order (:259-303). Fields named
    // after the map fields so they double as cursor targets.
    private String firstBlank(ReportForm form) {
        if (!nonBlank(form.sdtmm())) return "SDTMM";
        if (!nonBlank(form.sdtdd())) return "SDTDD";
        if (!nonBlank(form.sdtyyyy())) return "SDTYYYY";
        if (!nonBlank(form.edtmm())) return "EDTMM";
        if (!nonBlank(form.edtdd())) return "EDTDD";
        if (!nonBlank(form.edtyyyy())) return "EDTYYYY";
        return null;
    }

    // First failing numeric/range edit, in source order (:329-378).
    private String firstInvalid(ReportForm form) {
        if (!numericUpTo(form.sdtmm(), 12)) return "SDTMM";
        if (!numericUpTo(form.sdtdd(), 31)) return "SDTDD";
        if (!numeric(form.sdtyyyy())) return "SDTYYYY";
        if (!numericUpTo(form.edtmm(), 12)) return "EDTMM";
        if (!numericUpTo(form.edtdd(), 31)) return "EDTDD";
        if (!numeric(form.edtyyyy())) return "EDTYYYY";
        return null;
    }

    private String emptyMessage(String field) {
        return switch (field) {
            case "SDTMM" -> CobolMessages.REPORT_START_MONTH_EMPTY;
            case "SDTDD" -> CobolMessages.REPORT_START_DAY_EMPTY;
            case "SDTYYYY" -> CobolMessages.REPORT_START_YEAR_EMPTY;
            case "EDTMM" -> CobolMessages.REPORT_END_MONTH_EMPTY;
            case "EDTDD" -> CobolMessages.REPORT_END_DAY_EMPTY;
            default -> CobolMessages.REPORT_END_YEAR_EMPTY;
        };
    }

    private String invalidMessage(String field) {
        return switch (field) {
            case "SDTMM" -> CobolMessages.REPORT_START_MONTH_INVALID;
            case "SDTDD" -> CobolMessages.REPORT_START_DAY_INVALID;
            case "SDTYYYY" -> CobolMessages.REPORT_START_YEAR_INVALID;
            case "EDTMM" -> CobolMessages.REPORT_END_MONTH_INVALID;
            case "EDTDD" -> CobolMessages.REPORT_END_DAY_INVALID;
            default -> CobolMessages.REPORT_END_YEAR_INVALID;
        };
    }

    // CSUTLDTC accept rule (:396-405): sev '0000', or msg '2513' — the
    // range-tolerance cell kept from the source contract.
    private static boolean accepted(DateValidationResult result) {
        return "0000".equals(result.severity()) || "2513".equals(result.messageNumber());
    }

    private static boolean numeric(String value) {
        return value != null && value.matches("\\d+");
    }

    private static boolean numericUpTo(String value, int max) {
        return numeric(value) && Integer.parseInt(value) <= max;
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String iso(String year, String month, String day) {
        return year + "-" + month + "-" + day;
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
