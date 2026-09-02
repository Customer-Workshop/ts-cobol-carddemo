import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** COCRDLIC AIDs; any other `PFnn` is remapped to ENTER by the service (FR-S04-18). */
export type CardListAid = 'ENTER' | 'PF3' | 'PF7' | 'PF8' | string;

export interface CardListRowState {
  accountId: string;
  cardNumber: string;
  activeStatus: string;
}

/** WS-THIS-PROGCOMMAREA: opaque paging state echoed back on every AID press. */
export interface CardListPageState {
  screenNumber: number;
  firstCardNumber: string;
  lastCardNumber: string;
  nextPageExists: boolean;
  lastPageShown: boolean;
  rows: (CardListRowState | null)[];
}

export interface CardListRequest {
  aid: CardListAid;
  state: CardListPageState | null;
  accountFilter: string;
  cardFilter: string;
  selections: (string | null)[];
}

export interface CardListRow {
  hasCard: boolean;
  accountId: string;
  cardNumber: string;
  activeStatus: string;
  selection: string;
  selectionError: boolean;
  selectionProtected: boolean;
}

export interface CardListTarget {
  programKey: string;
  route: string;
  accountId: string;
  cardNumber: string;
}

export interface CardListResponse {
  outcome: 'display' | 'exit' | 'navigate' | 'comingSoon' | 'notInstalled';
  screenNumber: number;
  accountFilter: string;
  cardFilter: string;
  accountFilterError: boolean;
  cardFilterError: boolean;
  cursorField: string;
  rows: CardListRow[];
  errorMessage: string;
  infoMessage: string;
  message: string | null;
  severity: 'error' | 'info' | null;
  state: CardListPageState;
  target: CardListTarget | null;
}

@Injectable({ providedIn: 'root' })
export class CardListService {
  private readonly http = inject(HttpClient);

  list(request: CardListRequest): Observable<CardListResponse> {
    return this.http.post<CardListResponse>('/api/v1/cards/list', request);
  }
}
