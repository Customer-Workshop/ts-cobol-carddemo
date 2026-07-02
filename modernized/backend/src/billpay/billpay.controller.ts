import { Body, Controller, Post } from '@nestjs/common';
import { BillPayService } from './billpay.service';

@Controller('billpay')
export class BillPayController {
  constructor(private readonly billPayService: BillPayService) {}

  // POST /billpay (legacy COBIL00C) — REQ-F-150..REQ-F-167, REQ-N-002.
  @Post()
  payBill(@Body() body: unknown): ReturnType<BillPayService['payBill']> {
    return this.billPayService.payBill(body);
  }
}
