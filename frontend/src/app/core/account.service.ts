import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { ScreenHeader } from './auth.service';

/** ACCT-RECORD as painted into CACTVWA (app/cpy/CVACT01Y.cpy:19-33, COACTVWC.cbl:471-492). */
export interface AccountBlock {
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

/** CUSTOMER-RECORD as painted into CACTVWA (app/cpy/CVCUS01Y.cpy:19-45, COACTVWC.cbl:493-521). */
export interface CustomerBlock {
  customerId: string;
  ssn: string;
  dateOfBirth: string;
  ficoScore: number | null;
  firstName: string;
  middleName: string;
  lastName: string;
  addressLine1: string;
  addressLine2: string;
  city: string;
  stateCode: string;
  zipCode: string;
  countryCode: string;
  phone1: string;
  phone2: string;
  governmentIssuedId: string;
  eftAccountId: string;
  primaryCardHolder: string;
}

/** GET /api/accounts/{acctId} — the populated SEND MAP of CACTVWA. */
export interface AccountView {
  header: ScreenHeader;
  accountNumber: string;
  infoMessage: string;
  account: AccountBlock;
  customer: CustomerBlock;
}

/** Error body of the same endpoint; `account` is present only on the E-11 branch (FR-20). */
export interface AccountViewError {
  message?: string;
  status?: number;
  accountNumber?: string;
  account?: AccountBlock | null;
}

/** Client of the COACTVWC endpoint; the session cookie travels with every ApiService call. */
@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly api = inject(ApiService);

  view(accountId: string): Observable<AccountView> {
    return this.api.get<AccountView>(`/accounts/${encodeURIComponent(accountId)}`);
  }
}
