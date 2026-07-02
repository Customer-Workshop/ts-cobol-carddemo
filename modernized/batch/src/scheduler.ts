// scheduler — node-cron based runner replacing Control-M workload automation.
// Encodes the Job Dependencies orderings from the specs
// (TransactionProcessingandValidation / StatementandReportGeneration:
// CBTRN02C ↔ POSTTRAN and CBACT04C ↔ INTCALC share the account master and
// category balance stores, so posting must settle before interest, which must
// settle before statements and reports; archival runs last):
//   post-transactions → interest-calc → generate-statements
//     → transaction-report → archive-data → export-masters
// Each job writes its own job_runs audit row; a non-zero job return code
// aborts the remainder of the pipeline (legacy COND semantics).
import * as cron from 'node-cron';
import { runJob, RC_SUCCESS } from './lib/jobRun';
import { createLogger } from './lib/logger';
import { postTransactions } from './jobs/postTransactions';
import { interestCalc } from './jobs/interestCalc';
import { generateStatements } from './jobs/generateStatements';
import { transactionReport } from './jobs/transactionReport';
import { archiveData } from './jobs/archiveData';
import { exportMasters } from './jobs/exportMasters';

export const PIPELINE = [
  { name: 'post-transactions', fn: postTransactions },
  { name: 'interest-calc', fn: interestCalc },
  { name: 'generate-statements', fn: generateStatements },
  { name: 'transaction-report', fn: transactionReport },
  { name: 'archive-data', fn: archiveData },
  { name: 'export-masters', fn: exportMasters },
] as const;

export const DEFAULT_CRON = '0 2 * * *'; // nightly batch window, 02:00

/** Run the full dependency-ordered pipeline once. Returns 0 or 12. */
export async function runPipeline(params: Record<string, string> = {}): Promise<number> {
  const logger = createLogger('scheduler');
  for (const job of PIPELINE) {
    logger.info(`running ${job.name}`);
    const rc = await runJob(job.name, params, job.fn);
    if (rc !== RC_SUCCESS) {
      logger.error(`${job.name} failed with rc=${rc}; aborting pipeline`);
      return rc;
    }
  }
  logger.info('pipeline complete');
  return RC_SUCCESS;
}

/** Start the cron scheduler (never resolves; jobs run on the schedule). */
export function startScheduler(cronExpr: string = DEFAULT_CRON): void {
  const logger = createLogger('scheduler');
  if (!cron.validate(cronExpr)) {
    throw new Error(`invalid cron expression: ${cronExpr}`);
  }
  logger.info(`scheduler started with cron "${cronExpr}"`);
  cron.schedule(cronExpr, () => {
    void runPipeline().catch((err) => {
      logger.error(`pipeline error: ${err instanceof Error ? err.message : String(err)}`);
    });
  });
}
