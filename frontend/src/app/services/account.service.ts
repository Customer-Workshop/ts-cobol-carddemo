import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { Account } from '../models/account.model';
import { MOCK_ACCOUNTS } from './mock-data';

@Injectable({ providedIn: 'root' })
export class AccountService {
  private accounts = [...MOCK_ACCOUNTS];

  getAccounts(): Observable<Account[]> {
    return of(this.accounts);
  }

  getAccountById(accountId: string): Observable<Account | undefined> {
    return of(this.accounts.find(a => a.accountId === accountId));
  }

  updateAccount(updated: Account): Observable<Account> {
    const idx = this.accounts.findIndex(a => a.accountId === updated.accountId);
    if (idx >= 0) {
      this.accounts[idx] = { ...updated };
    }
    return of(updated);
  }
}
