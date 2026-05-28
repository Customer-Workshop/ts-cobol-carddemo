import { Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CurrencyPipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatSortModule, Sort } from '@angular/material/sort';
import { Account } from '../../../models/account.model';
import { AccountService } from '../../../services/account.service';

@Component({
  selector: 'app-account-list',
  standalone: true,
  imports: [
    RouterLink,
    CurrencyPipe,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatChipsModule,
    MatSortModule,
  ],
  templateUrl: './account-list.component.html',
  styleUrl: './account-list.component.scss',
})
export class AccountListComponent implements OnInit {
  private readonly accountService = inject(AccountService);

  accounts: Account[] = [];
  displayedColumns = [
    'accountId', 'activeStatus', 'currentBalance',
    'creditLimit', 'openDate', 'expirationDate', 'actions',
  ];

  ngOnInit(): void {
    this.accountService.getAccounts().subscribe(accounts => {
      this.accounts = accounts;
    });
  }

  sortData(sort: Sort): void {
    if (!sort.active || sort.direction === '') {
      return;
    }
    this.accounts = [...this.accounts].sort((a, b) => {
      const isAsc = sort.direction === 'asc';
      const key = sort.active as keyof Account;
      const valA = a[key];
      const valB = b[key];
      if (typeof valA === 'number' && typeof valB === 'number') {
        return (valA - valB) * (isAsc ? 1 : -1);
      }
      return String(valA).localeCompare(String(valB)) * (isAsc ? 1 : -1);
    });
  }
}
