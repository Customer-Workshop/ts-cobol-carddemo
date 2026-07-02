import { Body, Controller, Get, Param, Put, Query } from '@nestjs/common';
import { CardsService } from './cards.service';

@Controller('cards')
export class CardsController {
  constructor(private readonly cardsService: CardsService) {}

  // GET /cards (legacy COCRDLIC) — REQ-F-173..REQ-F-194.
  @Get()
  listCards(
    @Query('accountId') accountId?: string,
    @Query('page') page?: string,
    @Query('pageSize') pageSize?: string,
  ): ReturnType<CardsService['listCards']> {
    return this.cardsService.listCards(accountId, page, pageSize);
  }

  // GET /cards/{cardNumber} (legacy COCRDSLC) — REQ-F-202..REQ-F-211, REQ-F-251..REQ-F-257.
  @Get(':cardNumber')
  getCard(@Param('cardNumber') cardNumber: string): ReturnType<CardsService['getCard']> {
    return this.cardsService.getCard(cardNumber);
  }

  // PUT /cards/{cardNumber} (legacy COCRDUPC) — REQ-F-212..REQ-F-231.
  @Put(':cardNumber')
  updateCard(
    @Param('cardNumber') cardNumber: string,
    @Body() body: unknown,
  ): ReturnType<CardsService['updateCard']> {
    return this.cardsService.updateCard(cardNumber, body);
  }
}
