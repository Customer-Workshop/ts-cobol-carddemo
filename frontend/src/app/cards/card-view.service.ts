import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type CardViewOutcome = 'found' | 'inputError' | 'notFound' | 'storeError';

export type CardViewFilterState = 'valid' | 'blank' | 'notOk';

export type CardViewCursor = 'account' | 'card';

export interface CardViewDetails {
  embossedName: string;
  expiryMonth: string;
  expiryYear: string;
  activeStatus: string;
}

/** Screen state of BMS map CCRDSLA as returned by GET /api/v1/cards/view (COCRDSLC). */
export interface CardViewResponse {
  outcome: CardViewOutcome;
  message: string;
  infoMessage: string;
  accountId: string;
  cardNumber: string;
  accountFilter: CardViewFilterState;
  cardFilter: CardViewFilterState;
  cursor: CardViewCursor;
  card: CardViewDetails | null;
}

@Injectable({ providedIn: 'root' })
export class CardViewService {
  private readonly http = inject(HttpClient);

  view(accountId: string, cardNumber: string, fromCardList: boolean): Observable<CardViewResponse> {
    const params = new HttpParams()
      .set('accountId', accountId)
      .set('cardNumber', cardNumber)
      .set('fromCardList', String(fromCardList));
    return this.http.get<CardViewResponse>('/api/v1/cards/view', { params });
  }
}
