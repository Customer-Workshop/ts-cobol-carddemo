package com.carddemo.batch;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

/**
 * Logs the START/END OF EXECUTION banners and maps any step failure to the
 * CEE3ABD ABCODE 999 equivalent (S17-B4 / B-004): 'ABENDING PROGRAM' is
 * displayed and the step exit code becomes "999".
 */
public final class ReadVerifyStepListener implements StepExecutionListener {
    private final String program;

    public ReadVerifyStepListener(String program) {
        this.program = program;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        ReadVerifySupport.logStart(program);
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (stepExecution.getStatus() == BatchStatus.FAILED) {
            ReadVerifySupport.SYSOUT.info("ABENDING PROGRAM");
            return new ExitStatus("999");
        }
        ReadVerifySupport.logEnd(program);
        return null;
    }
}
