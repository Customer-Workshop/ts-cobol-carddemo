// Report listing and report job submission, forward-engineered from legacy
// CORPT00C (docs/spec/InteractiveNavigationandMenuControl §31, REQ-F-356..REQ-F-370).

import { Injectable } from '@nestjs/common';
import { reportRequestSchema } from '@carddemo/shared';
import { PrismaService } from '../prisma/prisma.service';
import { parseWith } from '../common/zod-validation';
import { PageResult, pageResult, parsePageParams } from '../common/pagination';
import { serializeJobRun, serializeReport } from '../common/serializers';

@Injectable()
export class ReportsService {
  constructor(private readonly prisma: PrismaService) {}

  async listReports(page?: string, pageSize?: string): Promise<PageResult<Record<string, unknown>>> {
    const params = parsePageParams(page, pageSize);
    const [totalItems, rows] = await this.prisma.$transaction([
      this.prisma.report.count(),
      this.prisma.report.findMany({
        orderBy: { createdAt: 'desc' },
        skip: params.skip,
        take: params.take,
      }),
    ]);
    return pageResult(params, totalItems, rows.map(serializeReport));
  }

  // REQ-F-360..REQ-F-363: date parameters are validated (format + calendar
  // correctness via the shared date validation service) before submission.
  // REQ-F-368: the accepted request is queued for the batch package to execute
  // (job_runs row with status PENDING replaces the legacy JCL TDQ submission).
  async createReport(body: unknown): Promise<Record<string, unknown>> {
    const input = parseWith(reportRequestSchema, body);
    const jobRun = await this.prisma.jobRun.create({
      data: {
        // Queued under the well-known request job name so the batch
        // transaction-report job picks it up (see batch REPORT_REQUEST_JOB_NAME).
        jobName: 'transaction-report-request',
        status: 'PENDING',
        message: JSON.stringify({ name: input.name, startDate: input.startDate, endDate: input.endDate }),
      },
    });
    // REQ-F-370: success response confirms the report was submitted.
    return serializeJobRun(jobRun);
  }
}
