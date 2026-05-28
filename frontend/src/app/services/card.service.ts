import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { Card, CardCrossReference } from '../models/card.model';
import { MOCK_CARDS, MOCK_CARD_XREFS } from './mock-data';

@Injectable({ providedIn: 'root' })
export class CardService {
  private cards = [...MOCK_CARDS];
  private xrefs = [...MOCK_CARD_XREFS];

  getCards(): Observable<Card[]> {
    return of(this.cards);
  }

  getCardByNumber(cardNumber: string): Observable<Card | undefined> {
    return of(this.cards.find(c => c.cardNumber === cardNumber));
  }

  getCardsByAccountId(accountId: string): Observable<Card[]> {
    return of(this.cards.filter(c => c.accountId === accountId));
  }

  getCardCrossReferences(): Observable<CardCrossReference[]> {
    return of(this.xrefs);
  }

  getXrefByCardNumber(cardNumber: string): Observable<CardCrossReference | undefined> {
    return of(this.xrefs.find(x => x.cardNumber === cardNumber));
  }

  updateCard(updated: Card): Observable<Card> {
    const idx = this.cards.findIndex(c => c.cardNumber === updated.cardNumber);
    if (idx >= 0) {
      this.cards[idx] = { ...updated };
    }
    return of(updated);
  }
}
