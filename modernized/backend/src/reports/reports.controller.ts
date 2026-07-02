import { Body, Controller, Get, Post, Query } from '@nestjs/common';
import { ReportsService } from './reports.service';

@Controller('reports')
export class ReportsController {
  constructor(private readonly reportsService: ReportsService) {}

  // GET /reports — lists generated reports (produced by the batch package).
  @Get()
  listReports(
    @Query('page') page?: string,
    @Query('pageSize') pageSize?: string,
  ): ReturnType<ReportsService['listReports']> {
    return this.reportsService.listReports(page, pageSize);
  }

  // POST /reports (legacy CORPT00C) — REQ-F-356..REQ-F-370.
  @Post()
  createReport(@Body() body: unknown): ReturnType<ReportsService['createReport']> {
    return this.reportsService.createReport(body);
  }
}
