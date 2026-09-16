package com.carddemo.batch;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

/** Carries the 999 abend code up to the job-level exit status. */
public final class Abend999JobListener implements JobExecutionListener {
    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() == BatchStatus.FAILED) {
            jobExecution.setExitStatus(new ExitStatus("999"));
        }
    }
}
