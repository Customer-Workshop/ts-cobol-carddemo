import { Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { Account } from '../../models/account.model';
import { Transaction } from '../../models/transaction.model';
import { AccountService } from '../../services/account.service';
import { TransactionService } from '../../services/transaction.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    RouterLink,
    CurrencyPipe,
    DatePipe,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatTableModule,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  private readonly accountService = inject(AccountService);
  private readonly transactionService = inject(TransactionService);

  accounts: Account[] = [];
  recentTransactions: Transaction[] = [];
  totalBalance = 0;
  totalCreditLimit = 0;
  activeAccounts = 0;

  displayedColumns = ['transactionId', 'description', 'amount', 'originTimestamp'];

  ngOnInit(): void {
    this.accountService.getAccounts().subscribe(accounts => {
      this.accounts = accounts;
      this.totalBalance = accounts.reduce((sum, a) => sum + a.currentBalance, 0);
      this.totalCreditLimit = accounts.reduce((sum, a) => sum + a.creditLimit, 0);
      this.activeAccounts = accounts.filter(a => a.activeStatus === 'Y').length;
    });

    this.transactionService.getTransactions().subscribe(transactions => {
      this.recentTransactions = transactions.slice(0, 5);
    });
  }
}
