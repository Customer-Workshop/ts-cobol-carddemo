import { Component, inject, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Transaction, TransactionType, TransactionCategory } from '../../../models/transaction.model';
import { TransactionService } from '../../../services/transaction.service';

@Component({
  selector: 'app-transaction-add',
  standalone: true,
  imports: [
    RouterLink,
    FormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatSnackBarModule,
  ],
  templateUrl: './transaction-add.component.html',
  styleUrl: './transaction-add.component.scss',
})
export class TransactionAddComponent implements OnInit {
  private readonly transactionService = inject(TransactionService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  transactionTypes: TransactionType[] = [];
  transactionCategories: TransactionCategory[] = [];

  newTransaction: Partial<Transaction> = {
    typeCode: '01',
    categoryCode: '0001',
    source: 'POS TERM',
    description: '',
    amount: 0,
    merchantId: '',
    merchantName: '',
    merchantCity: '',
    merchantZip: '',
    cardNumber: '',
  };

  ngOnInit(): void {
    this.transactionService.getTransactionTypes().subscribe(types => {
      this.transactionTypes = types;
    });
    this.transactionService.getTransactionCategories().subscribe(cats => {
      this.transactionCategories = cats;
    });
  }

  onSubmit(): void {
    const now = new Date().toISOString().replace('T', ' ').substring(0, 19);
    const txnId = Array.from({ length: 16 }, () => Math.floor(Math.random() * 10)).join('');

    const transaction: Transaction = {
      transactionId: txnId,
      typeCode: this.newTransaction.typeCode ?? '01',
      categoryCode: this.newTransaction.categoryCode ?? '0001',
      source: this.newTransaction.source ?? '',
      description: this.newTransaction.description ?? '',
      amount: this.newTransaction.amount ?? 0,
      merchantId: this.newTransaction.merchantId ?? '',
      merchantName: this.newTransaction.merchantName ?? '',
      merchantCity: this.newTransaction.merchantCity ?? '',
      merchantZip: this.newTransaction.merchantZip ?? '',
      cardNumber: this.newTransaction.cardNumber ?? '',
      originTimestamp: now,
      processedTimestamp: '',
    };

    this.transactionService.addTransaction(transaction).subscribe(() => {
      this.snackBar.open('Transaction added successfully', 'Close', { duration: 3000 });
      this.router.navigate(['/transactions']);
    });
  }
}
