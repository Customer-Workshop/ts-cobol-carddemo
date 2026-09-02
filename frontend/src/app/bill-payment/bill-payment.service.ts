import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type BillPaymentOutcome =
  | 'accountIdRequired'
  | 'invalidConfirmation'
  | 'declined'
  | 'accountNotFound'
  | 'accountLookupError'
  | 'nothingToPay'
  | 'confirmationRequired'
  | 'cardNotFound'
  | 'cardLookupError'
  | 'transactionLookupError'
  | 'duplicateTransaction'
  | 'transactionWriteError'
  | 'accountUpdateError'
  | 'paymentSuccessful';

export type BillPaymentCursorField = 'accountId' | 'confirm';

/** One ENTER round trip of COBIL00C PROCESS-ENTER-KEY (POST /api/v1/bill-payment). */
export interface BillPaymentResponse {
  outcome: BillPaymentOutcome;
  message: string;
  severity: 'error' | 'success' | null;
  cursorField: BillPaymentCursorField;
  /** CURBAL edit (+9999999999.99); null leaves the displayed balance untouched. */
  currentBalance: string | null;
  transactionId: string | null;
  /** INITIALIZE-ALL-FIELDS: clear account id, balance, confirm. */
  clearScreen: boolean;
}

@Injectable({ providedIn: 'root' })
export class BillPaymentService {
  private readonly http = inject(HttpClient);

  pay(accountId: string, confirm: string): Observable<BillPaymentResponse> {
    return this.http.post<BillPaymentResponse>('/api/v1/bill-payment', { accountId, confirm });
  }
}
