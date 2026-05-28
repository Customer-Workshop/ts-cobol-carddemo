import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { Transaction, TransactionType, TransactionCategory } from '../models/transaction.model';
import { MOCK_TRANSACTIONS, MOCK_TRANSACTION_TYPES, MOCK_TRANSACTION_CATEGORIES } from './mock-data';

@Injectable({ providedIn: 'root' })
export class TransactionService {
  private transactions = [...MOCK_TRANSACTIONS];
  private transactionTypes = [...MOCK_TRANSACTION_TYPES];
  private transactionCategories = [...MOCK_TRANSACTION_CATEGORIES];

  getTransactions(): Observable<Transaction[]> {
    return of(this.transactions);
  }

  getTransactionById(transactionId: string): Observable<Transaction | undefined> {
    return of(this.transactions.find(t => t.transactionId === transactionId));
  }

  getTransactionsByCardNumber(cardNumber: string): Observable<Transaction[]> {
    return of(this.transactions.filter(t => t.cardNumber === cardNumber));
  }

  getTransactionTypes(): Observable<TransactionType[]> {
    return of(this.transactionTypes);
  }

  getTransactionCategories(): Observable<TransactionCategory[]> {
    return of(this.transactionCategories);
  }

  addTransaction(transaction: Transaction): Observable<Transaction> {
    this.transactions = [transaction, ...this.transactions];
    return of(transaction);
  }
}
