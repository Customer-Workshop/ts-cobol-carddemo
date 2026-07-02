#!/usr/bin/env node
// carddemo-batch CLI — entry point for all modernized batch jobs, replacing
// the legacy JCL job deck (POSTTRAN, INTCALC, CREASTMT, TRANREPT, TRANBKP,
// READACCT/READCARD/READXREF/READCUST) and the Control-M scheduler.
// Exit codes follow the legacy return-code semantics: 0 success, 12 error
// (EOF/16 is handled internally by each job).
import { Command } from 'commander';
import { prisma } from './lib/prisma';
import { runJob } from './lib/jobRun';
import { postTransactions } from './jobs/postTransactions';
import { interestCalc } from './jobs/interestCalc';
import { generateStatements } from './jobs/generateStatements';
import { transactionReport } from './jobs/transactionReport';
import { archiveData } from './jobs/archiveData';
import { exportMasters } from './jobs/exportMasters';
import { runPipeline, startScheduler, DEFAULT_CRON } from './scheduler';

const program = new Command();
program
  .name('carddemo-batch')
  .description('CardDemo modernized batch jobs (replaces the legacy JCL/Control-M suite)');

async function execute(jobName: string, params: Record<string, string>): Promise<never> {
  const rc = await runJob(jobName, params, async (logger, p) => {
    switch (jobName) {
      case 'post-transactions':
        return postTransactions(logger);
      case 'interest-calc':
        return interestCalc(logger);
      case 'generate-statements':
        return generateStatements(logger, p);
      case 'transaction-report':
        return transactionReport(logger, p);
      case 'archive-data':
        return archiveData(logger, p);
      case 'export-masters':
        return exportMasters(logger);
      default:
        throw new Error(`unknown job: ${jobName}`);
    }
  });
  await prisma.$disconnect();
  process.exit(rc);
}

program
  .command('post-transactions')
  .description('Validate and post pending daily transactions (CBTRN02C/POSTTRAN)')
  .action(() => execute('post-transactions', {}));

program
  .command('interest-calc')
  .description('Compute and post monthly interest, reset cycle totals (CBACT04C/INTCALC)')
  .action(() => execute('interest-calc', {}));

program
  .command('generate-statements')
  .description('Generate text + HTML account statements (CBSTM03A/CBSTM03B)')
  .option('--start <date>', 'period start (YYYY-MM-DD), default cycle start')
  .option('--end <date>', 'period end (YYYY-MM-DD), default cycle end')
  .action((opts: { start?: string; end?: string }) =>
    execute('generate-statements', {
      ...(opts.start ? { start: opts.start } : {}),
      ...(opts.end ? { end: opts.end } : {}),
    }),
  );

program
  .command('transaction-report')
  .description('Date-range transaction detail report (CBTRN03C/TRANREPT)')
  .option('--start <date>', 'start date (YYYY-MM-DD), default cycle start')
  .option('--end <date>', 'end date (YYYY-MM-DD), default cycle end')
  .action((opts: { start?: string; end?: string }) =>
    execute('transaction-report', {
      ...(opts.start ? { start: opts.start } : {}),
      ...(opts.end ? { end: opts.end } : {}),
    }),
  );

program
  .command('archive-data')
  .description('Versioned snapshots of transactions and category balances (GDG backups)')
  .option('--retention <n>', 'generations to keep (default 5)')
  .action((opts: { retention?: string }) =>
    execute('archive-data', opts.retention ? { retention: opts.retention } : {}),
  );

program
  .command('export-masters')
  .description('Sequential labeled dump of accounts, cards, xref, customers (CBACT01C-03C/CBCUS01C)')
  .action(() => execute('export-masters', {}));

program
  .command('run-pipeline')
  .description('Run the full dependency-ordered batch pipeline once')
  .action(async () => {
    const rc = await runPipeline();
    await prisma.$disconnect();
    process.exit(rc);
  });

program
  .command('scheduler')
  .description('Start the node-cron scheduler running the nightly pipeline')
  .option('--cron <expr>', `cron expression (default "${DEFAULT_CRON}")`)
  .action((opts: { cron?: string }) => {
    startScheduler(opts.cron ?? DEFAULT_CRON);
  });

program.parseAsync(process.argv).catch(async (err: unknown) => {
  console.error(err);
  await prisma.$disconnect();
  process.exit(12);
});
