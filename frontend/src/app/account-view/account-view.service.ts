import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type AccountViewOutcome =
  | 'initial'
  | 'noInput'
  | 'invalidFilter'
  | 'accountNotInXref'
  | 'accountNotInMaster'
  | 'customerNotFound'
  | 'found'
  | 'storeError';

export type AccountFieldState = 'blank' | 'invalid' | 'valid';

export interface AccountViewAccount {
  activeStatus: string;
  openDate: string;
  creditLimit: string;
  expirationDate: string;
  cashCreditLimit: string;
  reissueDate: string;
  currentBalance: string;
  currentCycleCredit: string;
  groupId: string;
  currentCycleDebit: string;
}

export interface AccountViewCustomer {
  customerId: string;
  ssn: string;
  dateOfBirth: string;
  ficoScore: string;
  firstName: string;
  middleName: string;
  lastName: string;
  addressLine1: string;
  addressLine2: string;
  state: string;
  city: string;
  zip: string;
  country: string;
  phone1: string;
  phone2: string;
  governmentIssuedId: string;
  eftAccountId: string;
  primaryCardHolder: string;
}

export interface AccountViewResponse {
  outcome: AccountViewOutcome;
  accountId: string;
  accountFieldState: AccountFieldState;
  infoMessage: string;
  errorMessage: string;
  account: AccountViewAccount | null;
  customer: AccountViewCustomer | null;
}

@Injectable({ providedIn: 'root' })
export class AccountViewService {
  private readonly http = inject(HttpClient);

  view(accountId: string): Observable<AccountViewResponse> {
    const params = new HttpParams().set('accountId', accountId);
    return this.http.get<AccountViewResponse>('/api/v1/accounts/view', { params });
  }
}
