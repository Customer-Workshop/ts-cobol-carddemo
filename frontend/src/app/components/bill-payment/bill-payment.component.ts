import { Component, inject } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDividerModule } from '@angular/material/divider';
import { Account } from '../../models/account.model';
import { AccountService } from '../../services/account.service';

@Component({
  selector: 'app-bill-payment',
  standalone: true,
  imports: [
    CurrencyPipe,
    FormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatSnackBarModule,
    MatDividerModule,
  ],
  templateUrl: './bill-payment.component.html',
  styleUrl: './bill-payment.component.scss',
})
export class BillPaymentComponent {
  private readonly accountService = inject(AccountService);
  private readonly snackBar = inject(MatSnackBar);

  accountId = '';
  account: Account | null = null;
  confirmPayment = '';
  errorMessage = '';
  paymentSuccess = false;

  lookupAccount(): void {
    this.errorMessage = '';
    this.account = null;
    this.paymentSuccess = false;
    this.confirmPayment = '';

    if (!this.accountId) {
      this.errorMessage = 'Please enter an Account ID';
      return;
    }

    this.accountService.getAccountById(this.accountId).subscribe(account => {
      if (account) {
        this.account = account;
      } else {
        this.errorMessage = 'Account not found. Please verify the Account ID.';
      }
    });
  }

  submitPayment(): void {
    this.errorMessage = '';
    if (this.confirmPayment.toUpperCase() !== 'Y') {
      this.errorMessage = 'Payment cancelled.';
      return;
    }

    if (this.account) {
      const updated: Account = {
        ...this.account,
        currentBalance: 0,
      };
      this.accountService.updateAccount(updated).subscribe(result => {
        this.account = result;
        this.paymentSuccess = true;
        this.snackBar.open('Bill payment processed successfully!', 'Close', { duration: 5000 });
      });
    }
  }
}
