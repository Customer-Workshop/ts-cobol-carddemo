package com.carddemo.api;

public record ReportRequest(String reportName, String startDate, String endDate,
                            String confirmation) {
}
