import { Component, HostListener, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { classifyAidKey } from '../shared/invalid-key';
import {
  AccountFieldState,
  AccountViewAccount,
  AccountViewCustomer,
  AccountViewResponse,
  AccountViewService
} from './account-view.service';

export const ACCOUNT_VIEW_PROMPT = 'Enter or update id of account to display';
export const ACCOUNT_VIEW_TRANSACTION = 'CAVW';
export const ACCOUNT_VIEW_PROGRAM = 'COACTVWC';
export const ACCOUNT_VIEW_EXIT_ROUTE = '/menu';

/**
 * View Account screen, equivalent of BMS map CACTVWA (app/bms/COACTVW.bms) driven by
 * COACTVWC: 11-char account field, account and customer detail blocks, 45-char info line,
 * 78-char error line (FR-S02-01..07, 13, 14). ENTER submits; F3 / Exit returns to the
 * main menu (FR-S02-11); every other function key is treated as ENTER (FR-S02-12).
 */
@Component({
  selector: 'app-account-view',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule],
  templateUrl: './account-view.component.html',
  styleUrl: './account-view.component.scss'
})
export class AccountViewComponent {
  private readonly accountViewService = inject(AccountViewService);
  private readonly router = inject(Router);

  readonly transaction = ACCOUNT_VIEW_TRANSACTION;
  readonly program = ACCOUNT_VIEW_PROGRAM;
  readonly title = 'View Account';

  accountId = '';
  accountFieldState: AccountFieldState = 'blank';
  infoMessage = ACCOUNT_VIEW_PROMPT;
  errorMessage = '';
  account: AccountViewAccount | null = null;
  customer: AccountViewCustomer | null = null;
  reentered = false;

  /** 1300-SETUP-SCREEN-ATTRS: DFHRED when the filter is not ok, or blank on re-entry. */
  get accountFieldRed(): boolean {
    return this.accountFieldState === 'invalid' || (this.accountFieldState === 'blank' && this.reentered);
  }

  submit(): void {
    this.accountViewService.view(this.accountId).subscribe({
      next: (response) => this.apply(response),
      error: (error: HttpErrorResponse) => {
        const body = error.error as Partial<AccountViewResponse> | null;
        if (body && typeof body.errorMessage === 'string' && body.outcome === 'storeError') {
          this.apply(body as AccountViewResponse);
        }
      }
    });
  }

  exit(): void {
    this.router.navigateByUrl(ACCOUNT_VIEW_EXIT_ROUTE);
  }

  @HostListener('window:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    const action = classifyAidKey(event);
    if (!action) {
      return;
    }
    event.preventDefault();
    if (action === 'exit') {
      this.exit();
    } else {
      this.submit();
    }
  }

  private apply(response: AccountViewResponse): void {
    this.reentered = true;
    this.accountId = response.accountId;
    this.accountFieldState = response.accountFieldState;
    this.infoMessage = response.infoMessage;
    this.errorMessage = response.errorMessage;
    this.account = response.account;
    this.customer = response.customer;
  }
}
