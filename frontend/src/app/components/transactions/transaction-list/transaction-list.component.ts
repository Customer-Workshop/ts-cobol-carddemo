import { Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Transaction, TransactionType } from '../../../models/transaction.model';
import { TransactionService } from '../../../services/transaction.service';

@Component({
  selector: 'app-transaction-list',
  standalone: true,
  imports: [
    RouterLink,
    CurrencyPipe,
    DatePipe,
    FormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSortModule,
    MatTooltipModule,
  ],
  templateUrl: './transaction-list.component.html',
  styleUrl: './transaction-list.component.scss',
})
export class TransactionListComponent implements OnInit {
  private readonly transactionService = inject(TransactionService);

  allTransactions: Transaction[] = [];
  filteredTransactions: Transaction[] = [];
  transactionTypes: TransactionType[] = [];

  filterCardNumber = '';
  filterTypeCode = '';
  private currentSort: Sort | null = null;

  displayedColumns = [
    'transactionId', 'typeCode', 'source', 'description',
    'amount', 'cardNumber', 'originTimestamp', 'actions',
  ];

  ngOnInit(): void {
    this.transactionService.getTransactions().subscribe(txns => {
      this.allTransactions = txns;
      this.filteredTransactions = txns;
    });
    this.transactionService.getTransactionTypes().subscribe(types => {
      this.transactionTypes = types;
    });
  }

  applyFilter(): void {
    this.filteredTransactions = this.allTransactions.filter(t => {
      const matchCard = !this.filterCardNumber ||
        t.cardNumber.includes(this.filterCardNumber);
      const matchType = !this.filterTypeCode ||
        t.typeCode === this.filterTypeCode;
      return matchCard && matchType;
    });
    this.applySortIfActive();
  }

  clearFilters(): void {
    this.filterCardNumber = '';
    this.filterTypeCode = '';
    this.filteredTransactions = [...this.allTransactions];
    this.applySortIfActive();
  }

  getTypeName(code: string): string {
    return this.transactionTypes.find(t => t.typeCode === code)?.description ?? code;
  }

  sortData(sort: Sort): void {
    this.currentSort = sort;
    this.applySortIfActive();
  }

  private applySortIfActive(): void {
    if (!this.currentSort?.active || this.currentSort.direction === '') return;
    const sort = this.currentSort;
    this.filteredTransactions = [...this.filteredTransactions].sort((a, b) => {
      const isAsc = sort.direction === 'asc';
      const key = sort.active as keyof Transaction;
      const valA = a[key];
      const valB = b[key];
      if (typeof valA === 'number' && typeof valB === 'number') {
        return (valA - valB) * (isAsc ? 1 : -1);
      }
      return String(valA).localeCompare(String(valB)) * (isAsc ? 1 : -1);
    });
  }
}
