import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type CardUpdateAid = 'enter' | 'pf5' | 'pf12';

export type CardUpdateState =
  | 'notFetched'
  | 'showDetails'
  | 'changesNotOk'
  | 'changesOkNotConfirmed'
  | 'changesDone'
  | 'changesFailed';

export type CardUpdateFieldName =
  | 'accountId'
  | 'cardNumber'
  | 'embossedName'
  | 'activeStatus'
  | 'expiryMonth'
  | 'expiryYear';

export interface CardUpdateDetails {
  accountId: string;
  cardNumber: string;
  embossedName: string;
  expiryYear: string;
  expiryMonth: string;
  expiryDay: string;
  activeStatus: string;
}

export interface CardUpdateInput {
  embossedName: string;
  activeStatus: string;
  expiryMonth: string;
  expiryYear: string;
}

export interface CardUpdateRequest {
  aid: CardUpdateAid;
  state: CardUpdateState;
  accountId: string;
  cardNumber: string;
  original: CardUpdateDetails | null;
  input: CardUpdateInput | null;
}

export interface CardUpdateScreen {
  state: CardUpdateState;
  infoMessage: string;
  errorMessage: string | null;
  accountId: string;
  cardNumber: string;
  embossedName: string;
  activeStatus: string;
  expiryMonth: string;
  expiryYear: string;
  expiryDay: string;
  original: CardUpdateDetails | null;
  fieldsInError: CardUpdateFieldName[];
  cursorField: CardUpdateFieldName;
  searchEditable: boolean;
  detailsEditable: boolean;
  confirmKeysVisible: boolean;
}

@Injectable({ providedIn: 'root' })
export class CardUpdateService {
  private readonly http = inject(HttpClient);

  initialScreen(): Observable<CardUpdateScreen> {
    return this.http.get<CardUpdateScreen>('/api/v1/cards/update');
  }

  process(request: CardUpdateRequest): Observable<CardUpdateScreen> {
    return this.http.post<CardUpdateScreen>('/api/v1/cards/update', request);
  }
}
