package com.carddemo.api;

public record ReportAcceptedResponse(String reportName, String startDate, String endDate,
                                     String status, Long jobExecutionId) {
}
