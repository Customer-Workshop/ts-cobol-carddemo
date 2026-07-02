import { Body, Controller, Get, Param, Post, Query } from '@nestjs/common';
import { TransactionsService } from './transactions.service';

@Controller('transactions')
export class TransactionsController {
  constructor(private readonly transactionsService: TransactionsService) {}

  // GET /transactions (legacy COTRN00C) — REQ-F-389..REQ-F-408.
  @Get()
  listTransactions(
    @Query('cardNumber') cardNumber?: string,
    @Query('accountId') accountId?: string,
    @Query('page') page?: string,
    @Query('pageSize') pageSize?: string,
  ): ReturnType<TransactionsService['listTransactions']> {
    return this.transactionsService.listTransactions(cardNumber, accountId, page, pageSize);
  }

  // POST /transactions (legacy COTRN02C) — REQ-F-474..REQ-F-494.
  @Post()
  createTransaction(@Body() body: unknown): ReturnType<TransactionsService['createTransaction']> {
    return this.transactionsService.createTransaction(body);
  }

  // GET /transactions/{transactionId} (legacy COTRN01C) — REQ-F-414..REQ-F-420.
  @Get(':transactionId')
  getTransaction(
    @Param('transactionId') transactionId: string,
  ): ReturnType<TransactionsService['getTransaction']> {
    return this.transactionsService.getTransaction(transactionId);
  }
}
