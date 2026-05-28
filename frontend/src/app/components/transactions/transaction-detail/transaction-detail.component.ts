import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { Transaction, TransactionType } from '../../../models/transaction.model';
import { TransactionService } from '../../../services/transaction.service';

@Component({
  selector: 'app-transaction-detail',
  standalone: true,
  imports: [
    RouterLink,
    CurrencyPipe,
    DatePipe,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatDividerModule,
  ],
  templateUrl: './transaction-detail.component.html',
  styleUrl: './transaction-detail.component.scss',
})
export class TransactionDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly transactionService = inject(TransactionService);

  transaction: Transaction | null = null;
  transactionTypes: TransactionType[] = [];

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    this.transactionService.getTransactionTypes().subscribe(types => {
      this.transactionTypes = types;
    });
    if (id) {
      this.transactionService.getTransactionById(id).subscribe(txn => {
        if (txn) {
          this.transaction = txn;
        } else {
          this.router.navigate(['/transactions']);
        }
      });
    }
  }

  getTypeName(code: string): string {
    return this.transactionTypes.find(t => t.typeCode === code)?.description ?? code;
  }
}
