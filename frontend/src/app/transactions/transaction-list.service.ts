import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { MenuNavigationTarget } from '../menu/menu.service';

/** AID keys COTRN00C handles: ENTER, PF7 (backward), PF8 (forward). */
export type TransactionListAction = 'enter' | 'pageBackward' | 'pageForward';

/** CDEMO-CT00-INFO paging state, round-tripped with every request (S07-B5). */
export interface TransactionListState {
  firstTranId: string;
  lastTranId: string;
  pageNumber: number;
  nextPageAvailable: boolean;
}

export const INITIAL_TRANSACTION_LIST_STATE: TransactionListState = {
  firstTranId: '',
  lastTranId: '',
  pageNumber: 0,
  nextPageAvailable: false
};

export interface TransactionListRequest {
  action: TransactionListAction;
  searchTranId: string;
  selectionFlag: string;
  selectedTranId: string;
  state: TransactionListState;
}

export interface TransactionListRow {
  tranId: string;
  date: string;
  description: string;
  amount: string;
}

export interface TransactionListResponse {
  outcome: 'redisplay' | 'comingSoon' | 'notInstalled' | 'navigate';
  message: string | null;
  severity: 'error' | 'info' | null;
  /** Replacement for the ten screen rows; null means the rows on screen are kept. */
  rows: TransactionListRow[] | null;
  clearSearchInput: boolean;
  state: TransactionListState;
  selectedTranId: string | null;
  target: MenuNavigationTarget | null;
}

@Injectable({ providedIn: 'root' })
export class TransactionListService {
  private readonly http = inject(HttpClient);

  list(request: TransactionListRequest): Observable<TransactionListResponse> {
    return this.http.post<TransactionListResponse>('/api/v1/transactions/list', request);
  }
}
