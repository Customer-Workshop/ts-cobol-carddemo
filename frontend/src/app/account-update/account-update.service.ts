import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** CACTUPA data fields (app/bms/COACTUP.bms), one string per map field, in map order. */
export interface AccountUpdateFields {
  accountId: string;
  activeStatus: string;
  openYear: string;
  openMonth: string;
  openDay: string;
  creditLimit: string;
  expiryYear: string;
  expiryMonth: string;
  expiryDay: string;
  cashCreditLimit: string;
  reissueYear: string;
  reissueMonth: string;
  reissueDay: string;
  currentBalance: string;
  currentCycleCredit: string;
  groupId: string;
  currentCycleDebit: string;
  customerId: string;
  ssn1: string;
  ssn2: string;
  ssn3: string;
  dobYear: string;
  dobMonth: string;
  dobDay: string;
  ficoScore: string;
  firstName: string;
  middleName: string;
  lastName: string;
  addressLine1: string;
  addressLine2: string;
  state: string;
  zip: string;
  city: string;
  country: string;
  phone1Area: string;
  phone1Prefix: string;
  phone1Line: string;
  phone2Area: string;
  phone2Prefix: string;
  phone2Line: string;
  governmentId: string;
  eftAccountId: string;
  primaryCardHolder: string;
}

export type AccountUpdateOutcome =
  | 'searchError'
  | 'details'
  | 'invalid'
  | 'noChanges'
  | 'confirm'
  | 'committed'
  | 'failed'
  | 'changedByOther';

export interface AccountUpdateLookupResponse {
  outcome: AccountUpdateOutcome;
  infoMessage: string | null;
  errorMessage: string | null;
  fields: AccountUpdateFields | null;
}

export interface AccountUpdateChangeResponse {
  outcome: AccountUpdateOutcome;
  infoMessage: string | null;
  errorMessage: string | null;
  invalidFields: string[];
}

export function emptyAccountUpdateFields(): AccountUpdateFields {
  return {
    accountId: '',
    activeStatus: '',
    openYear: '',
    openMonth: '',
    openDay: '',
    creditLimit: '',
    expiryYear: '',
    expiryMonth: '',
    expiryDay: '',
    cashCreditLimit: '',
    reissueYear: '',
    reissueMonth: '',
    reissueDay: '',
    currentBalance: '',
    currentCycleCredit: '',
    groupId: '',
    currentCycleDebit: '',
    customerId: '',
    ssn1: '',
    ssn2: '',
    ssn3: '',
    dobYear: '',
    dobMonth: '',
    dobDay: '',
    ficoScore: '',
    firstName: '',
    middleName: '',
    lastName: '',
    addressLine1: '',
    addressLine2: '',
    state: '',
    zip: '',
    city: '',
    country: '',
    phone1Area: '',
    phone1Prefix: '',
    phone1Line: '',
    phone2Area: '',
    phone2Prefix: '',
    phone2Line: '',
    governmentId: '',
    eftAccountId: '',
    primaryCardHolder: ''
  };
}

@Injectable({ providedIn: 'root' })
export class AccountUpdateService {
  private readonly http = inject(HttpClient);

  lookup(accountId: string): Observable<AccountUpdateLookupResponse> {
    return this.http.post<AccountUpdateLookupResponse>('/api/v1/account-update/lookup', { accountId });
  }

  validate(original: AccountUpdateFields, updated: AccountUpdateFields): Observable<AccountUpdateChangeResponse> {
    return this.http.post<AccountUpdateChangeResponse>('/api/v1/account-update/validate', { original, updated });
  }

  save(original: AccountUpdateFields, updated: AccountUpdateFields): Observable<AccountUpdateChangeResponse> {
    return this.http.post<AccountUpdateChangeResponse>('/api/v1/account-update/save', { original, updated });
  }
}
