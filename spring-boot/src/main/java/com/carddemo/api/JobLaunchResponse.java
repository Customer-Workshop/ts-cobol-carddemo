package com.carddemo.api;

public record JobLaunchResponse(String jobName, Long executionId, String status,
                                String exitCode, String exitDescription) {
}
