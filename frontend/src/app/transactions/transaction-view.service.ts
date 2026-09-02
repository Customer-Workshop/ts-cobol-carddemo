import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** The 13 output fields of BMS map COTRN1A, already edited server-side (FR-S08-08..11). */
export interface TransactionView {
  transactionId: string;
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
}

@Injectable({ providedIn: 'root' })
export class TransactionViewService {
  private readonly http = inject(HttpClient);

  view(tranId: string): Observable<TransactionView> {
    return this.http.get<TransactionView>('/api/v1/transactions/view', { params: { tranId } });
  }
}
