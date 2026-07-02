import { Body, Controller, Get, Param, Put } from '@nestjs/common';
import { AccountsService } from './accounts.service';

@Controller('accounts')
export class AccountsController {
  constructor(private readonly accountsService: AccountsService) {}

  // GET /accounts/{accountId} (legacy COACTVWC) — REQ-F-064..REQ-F-068, REQ-F-121..REQ-F-131.
  @Get(':accountId')
  getAccount(@Param('accountId') accountId: string): ReturnType<AccountsService['getAccount']> {
    return this.accountsService.getAccount(accountId);
  }

  // PUT /accounts/{accountId} (legacy COACTUPC) — REQ-F-005..REQ-F-080.
  @Put(':accountId')
  updateAccount(
    @Param('accountId') accountId: string,
    @Body() body: unknown,
  ): ReturnType<AccountsService['updateAccount']> {
    return this.accountsService.updateAccount(accountId, body);
  }
}
