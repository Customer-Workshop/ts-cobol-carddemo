// Batch job audit trail (job_runs table) — replaces Control-M execution history.
// Every job records name, params, start/finish, status, and record counts.
// Return-code semantics follow the legacy specs: 0 = success, 12 = error,
// 16 = end-of-file (handled internally, never surfaced as an exit code).
import { JobStatus } from '@prisma/client';
import { prisma } from './prisma';
import { createLogger, Logger } from './logger';

export const RC_SUCCESS = 0;
export const RC_ERROR = 12;

export interface JobResult {
  counts: Record<string, number>;
  message?: string;
}

export type JobFn = (logger: Logger, params: Record<string, string>) => Promise<JobResult>;

export async function runJob(
  jobName: string,
  params: Record<string, string>,
  fn: JobFn,
): Promise<number> {
  const logger = createLogger(jobName);
  const run = await prisma.jobRun.create({
    data: {
      jobName,
      status: JobStatus.RUNNING,
      message: JSON.stringify({ params }),
    },
  });
  logger.info(`started (jobRun #${run.id}) params=${JSON.stringify(params)}`);
  try {
    const result = await fn(logger, params);
    await prisma.jobRun.update({
      where: { id: run.id },
      data: {
        status: JobStatus.SUCCEEDED,
        completedAt: new Date(),
        message: JSON.stringify({ params, counts: result.counts, note: result.message }),
      },
    });
    logger.info(`succeeded counts=${JSON.stringify(result.counts)}`);
    return RC_SUCCESS;
  } catch (err) {
    const msg = err instanceof Error ? err.message : String(err);
    await prisma.jobRun.update({
      where: { id: run.id },
      data: {
        status: JobStatus.FAILED,
        completedAt: new Date(),
        message: JSON.stringify({ params, error: msg }),
      },
    });
    logger.error(`failed: ${msg}`);
    return RC_ERROR;
  }
}
