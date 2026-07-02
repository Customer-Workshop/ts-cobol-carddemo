-- Adds a PENDING job status so the API can record report generation requests
-- that the batch package picks up and executes.
ALTER TYPE "JobStatus" ADD VALUE IF NOT EXISTS 'PENDING';
