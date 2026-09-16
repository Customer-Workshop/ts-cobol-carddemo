package com.carddemo.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

/**
 * Target equivalent of COBSWAIT's {@code CALL 'MVSWAIT'} (app/cbl/COBSWAIT.cbl:36-38):
 * sleeps for a centisecond count carried by the {@code waitCentiseconds} job
 * parameter, which plays the SYSIN-card role (WAITSTEP.jcl supplies 00003600 =
 * 36.00 s). The wait gives the file close/open bracketing in the scheduler chain
 * wall-clock settle time; in the target it is kept as an explicit step so the
 * daily runbook keeps its posting → wait → reopen shape. Interruption maps to
 * step failure, matching MVSWAIT's no-feedback contract.
 */
public class WaitStepTasklet implements Tasklet {
    private static final Logger log = LoggerFactory.getLogger(WaitStepTasklet.class);

    /** Default from WAITSTEP.jcl SYSIN {@code 00003600} centiseconds = 36.00 s. */
    public static final long DEFAULT_WAIT_CENTISECONDS = 3600L;

    private final long waitCentiseconds;

    public WaitStepTasklet(long waitCentiseconds) {
        this.waitCentiseconds = Math.max(0, waitCentiseconds);
    }

    /**
     * Parses the SYSIN-card contract: ACCEPT reads an 8-byte card into X(8), so
     * only the first 8 characters count, and the MOVE to 9(8) COMP keeps leading
     * digits while blanks/garbage land at 0 (near-instant return).
     */
    public static long centiseconds(String parm) {
        if (parm == null) {
            return 0;
        }
        String card = parm.length() <= 8 ? parm : parm.substring(0, 8);
        String stripped = card.strip();
        int end = 0;
        while (end < stripped.length() && Character.isDigit(stripped.charAt(end))) {
            end++;
        }
        return end == 0 ? 0 : Long.parseLong(stripped.substring(0, end));
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext)
            throws Exception {
        if (waitCentiseconds > 0) {
            log.debug("WAITSTEP sleeping {} centiseconds", waitCentiseconds);
            try {
                Thread.sleep(waitCentiseconds * 10L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw exception;
            }
        }
        return RepeatStatus.FINISHED;
    }
}
