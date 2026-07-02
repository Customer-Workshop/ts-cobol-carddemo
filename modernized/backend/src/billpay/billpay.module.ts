import { Module } from '@nestjs/common';
import { BillPayController } from './billpay.controller';
import { BillPayService } from './billpay.service';

@Module({
  controllers: [BillPayController],
  providers: [BillPayService],
})
export class BillPayModule {}
