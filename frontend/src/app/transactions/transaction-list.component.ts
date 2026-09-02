import { Component, HostListener, OnInit, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MSG_INVALID_KEY, classifyAidKey } from '../shared/invalid-key';
import {
  INITIAL_TRANSACTION_LIST_STATE,
  TransactionListAction,
  TransactionListResponse,
  TransactionListRow,
  TransactionListService,
  TransactionListState
} from './transaction-list.service';

export const MSG_LOOKUP_ERROR = 'Unable to lookup transaction...';

export const TRAN_LIST_PAGE_SIZE = 10;
export const TRAN_ID_LENGTH = 16;

export interface TransactionListScreenRow extends TransactionListRow {
  sel: string;
}

function blankRow(): TransactionListScreenRow {
  return { sel: '', tranId: '', date: '', description: '', amount: '' };
}

/**
 * Transaction list screen, equivalent of BMS map COTRN0A (app/bms/COTRN00.bms):
 * 16-char search id, ten rows of Sel(1)/Tran ID(16)/Date(8)/Description(26)/Amount(12),
 * 8-digit page number, 78-char message area. ENTER searches/selects, PF7/PF8 page,
 * Exit / F3 returns to the main menu (FR-S07-02..19); other function keys are unmapped AIDs.
 */
@Component({
  selector: 'app-transaction-list',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule],
  templateUrl: './transaction-list.component.html',
  styleUrl: './transaction-list.component.scss'
})
export class TransactionListComponent implements OnInit {
  private readonly transactionListService = inject(TransactionListService);
  private readonly router = inject(Router);

  readonly title = 'List Transactions';
  readonly tranIdLength = TRAN_ID_LENGTH;

  searchTranId = '';
  rows: TransactionListScreenRow[] = Array.from({ length: TRAN_LIST_PAGE_SIZE }, blankRow);
  state: TransactionListState = { ...INITIAL_TRANSACTION_LIST_STATE };
  message = '';
  messageSeverity: 'error' | 'info' | null = null;

  get pageNumber(): string {
    return String(this.state.pageNumber).padStart(8, '0');
  }

  ngOnInit(): void {
    this.send('enter');
  }

  submit(): void {
    this.send('enter');
  }

  pageBackward(): void {
    this.send('pageBackward');
  }

  pageForward(): void {
    this.send('pageForward');
  }

  exit(): void {
    this.router.navigateByUrl('/menu');
  }

  @HostListener('window:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'F7' || event.key === 'F8') {
      event.preventDefault();
      this.send(event.key === 'F7' ? 'pageBackward' : 'pageForward');
      return;
    }
    const action = classifyAidKey(event);
    if (!action) {
      return;
    }
    event.preventDefault();
    if (action === 'exit') {
      this.exit();
    } else {
      this.message = MSG_INVALID_KEY;
      this.messageSeverity = 'error';
    }
  }

  private send(action: TransactionListAction): void {
    const selected = this.rows.find((row) => row.sel.trim() !== '');
    this.transactionListService
      .list({
        action,
        searchTranId: this.searchTranId,
        selectionFlag: selected?.sel ?? '',
        selectedTranId: selected?.tranId ?? '',
        state: this.state
      })
      .subscribe({
        next: (response) => this.apply(response),
        error: (error: HttpErrorResponse) => {
          const body: unknown = error.error;
          const serverMessage =
            typeof body === 'object' && body !== null && 'message' in body && typeof body.message === 'string'
              ? body.message
              : null;
          this.message = serverMessage ?? MSG_LOOKUP_ERROR;
          this.messageSeverity = 'error';
        }
      });
  }

  private apply(response: TransactionListResponse): void {
    if (response.outcome === 'navigate' && response.target?.route) {
      const tranId = encodeURIComponent(response.selectedTranId ?? '');
      this.router.navigateByUrl(`${response.target.route}?tranId=${tranId}`);
      return;
    }
    this.state = response.state;
    if (response.rows) {
      this.rows = response.rows.map((row, index) => ({ ...row, sel: this.rows[index]?.sel ?? '' }));
    }
    if (response.clearSearchInput) {
      this.searchTranId = '';
    }
    this.message = response.message ?? '';
    this.messageSeverity = response.severity;
  }
}
