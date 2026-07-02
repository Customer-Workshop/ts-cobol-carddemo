-- Add PENDING to JobStatus so the backend can queue report requests in
-- job_runs for the batch transaction-report job to pick up.
ALTER TYPE "JobStatus" ADD VALUE 'PENDING';
