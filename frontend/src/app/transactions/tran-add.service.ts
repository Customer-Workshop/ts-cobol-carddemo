import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** The 14 COTRN2A input fields (app/cpy-bms/COTRN02.CPY), keyed by the API field names. */
export interface TranAddScreen {
  accountId: string;
  cardNumber: string;
  typeCode: string;
  categoryCode: string;
  source: string;
  description: string;
  amount: string;
  originalDate: string;
  processedDate: string;
  merchantId: string;
  merchantName: string;
  merchantCity: string;
  merchantZip: string;
  confirmation: string;
}

export type TranAddField = keyof TranAddScreen;

export const TRAN_ADD_FIELDS: readonly TranAddField[] = [
  'accountId',
  'cardNumber',
  'typeCode',
  'categoryCode',
  'source',
  'description',
  'amount',
  'originalDate',
  'processedDate',
  'merchantId',
  'merchantName',
  'merchantCity',
  'merchantZip',
  'confirmation'
];

/** BMS field lengths (app/cpy-bms/COTRN02.CPY). */
export const TRAN_ADD_FIELD_LENGTHS: Readonly<Record<TranAddField, number>> = {
  accountId: 11,
  cardNumber: 16,
  typeCode: 2,
  categoryCode: 4,
  source: 10,
  description: 60,
  amount: 12,
  originalDate: 10,
  processedDate: 10,
  merchantId: 9,
  merchantName: 30,
  merchantCity: 25,
  merchantZip: 10,
  confirmation: 1
};

export function emptyTranAddScreen(): TranAddScreen {
  return {
    accountId: '',
    cardNumber: '',
    typeCode: '',
    categoryCode: '',
    source: '',
    description: '',
    amount: '',
    originalDate: '',
    processedDate: '',
    merchantId: '',
    merchantName: '',
    merchantCity: '',
    merchantZip: '',
    confirmation: ''
  };
}

export type TranAddOutcome =
  | 'added'
  | 'confirmationRequired'
  | 'invalidConfirmation'
  | 'validationError'
  | 'keyNotFound'
  | 'lookupError'
  | 'duplicateTransactionId'
  | 'writeError';

export interface TranAddResponse {
  outcome: TranAddOutcome;
  screen: TranAddScreen;
  message: string;
  severity: 'success' | 'error';
  cursorField: TranAddField;
  transactionId: string | null;
}

@Injectable({ providedIn: 'root' })
export class TranAddService {
  private readonly http = inject(HttpClient);

  /** ENTER (COTRN02C PROCESS-ENTER-KEY). */
  add(screen: TranAddScreen): Observable<TranAddResponse> {
    return this.http.post<TranAddResponse>('/api/v1/transactions/add', screen);
  }

  /** PF5 (COTRN02C COPY-LAST-TRAN-DATA). */
  copyLast(screen: TranAddScreen): Observable<TranAddResponse> {
    return this.http.post<TranAddResponse>('/api/v1/transactions/add/copy-last', screen);
  }
}
