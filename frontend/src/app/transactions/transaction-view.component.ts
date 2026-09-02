import { HttpErrorResponse } from '@angular/common/http';
import { AfterViewInit, Component, ElementRef, HostListener, OnInit, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router } from '@angular/router';
import { MenuService } from '../menu/menu.service';
import { MSG_INVALID_KEY, classifyAidKey } from '../shared/invalid-key';
import { TransactionView, TransactionViewService } from './transaction-view.service';

export const MSG_TRAN_LOOKUP_ERROR = 'Unable to lookup Transaction...';

/** Main-menu option of COTRN00C (Transaction List) in app/cpy/COMEN02Y.cpy — the PF5 XCTL target. */
export const TRANSACTION_LIST_MENU_OPTION = '06';

export const DEFAULT_RETURN_URL = '/menu';

export interface DetailField {
  key: keyof TransactionView;
  label: string;
  length: number;
}

/** Output fields of BMS map COTRN1A in screen order with their map lengths (app/bms/COTRN01.bms). */
export const DETAIL_FIELDS: readonly DetailField[] = [
  { key: 'transactionId', label: 'Transaction ID:', length: 16 },
  { key: 'cardNumber', label: 'Card Number:', length: 16 },
  { key: 'typeCode', label: 'Type CD:', length: 2 },
  { key: 'categoryCode', label: 'Category CD:', length: 4 },
  { key: 'source', label: 'Source:', length: 10 },
  { key: 'description', label: 'Description:', length: 60 },
  { key: 'amount', label: 'Amount:', length: 12 },
  { key: 'originalDate', label: 'Orig Date:', length: 10 },
  { key: 'processedDate', label: 'Proc Date:', length: 10 },
  { key: 'merchantId', label: 'Merchant ID:', length: 9 },
  { key: 'merchantName', label: 'Merchant Name:', length: 30 },
  { key: 'merchantCity', label: 'Merchant City:', length: 25 },
  { key: 'merchantZip', label: 'Merchant Zip:', length: 10 }
];

const EMPTY_DETAIL: TransactionView = {
  transactionId: '',
  cardNumber: '',
  typeCode: '',
  categoryCode: '',
  source: '',
  description: '',
  amount: '',
  originalDate: '',
  processedDate: '',
  merchantId: '',
  merchantName: '',
  merchantCity: '',
  merchantZip: ''
};

/**
 * View Transaction screen, equivalent of BMS map COTRN1A (app/bms/COTRN01.bms) driven by
 * COTRN01C (app/cbl/COTRN01C.cbl): 16-char Tran ID input, 13 read-only detail fields,
 * 78-char message area. ENTER fetches, F3 returns to the caller, F4 clears, F5 transfers
 * to the transaction list through the route registry; any other function key is an
 * unmapped AID (FR-S08-02..08, 12..17).
 */
@Component({
  selector: 'app-transaction-view',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule],
  templateUrl: './transaction-view.component.html',
  styleUrl: './transaction-view.component.scss'
})
export class TransactionViewComponent implements OnInit, AfterViewInit {
  private readonly transactionViewService = inject(TransactionViewService);
  private readonly menuService = inject(MenuService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  @ViewChild('tranIdInput') private tranIdInput?: ElementRef<HTMLInputElement>;

  readonly title = 'View Transaction';
  readonly transactionCode = 'CT01';
  readonly programName = 'COTRN01C';
  readonly footer = 'ENTER=Fetch  F3=Back  F4=Clear  F5=Browse Tran.';
  readonly fields = DETAIL_FIELDS;

  tranId = '';
  detail: TransactionView = EMPTY_DETAIL;
  message = '';
  messageSeverity: 'error' | 'info' | null = null;

  ngOnInit(): void {
    const selected = this.route.snapshot.queryParamMap.get('tranId');
    if (selected && selected.trim().length > 0) {
      this.tranId = selected;
      this.fetch();
    }
  }

  ngAfterViewInit(): void {
    setTimeout(() => this.focusTranId());
  }

  submit(): void {
    this.fetch();
  }

  back(): void {
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
    this.router.navigateByUrl(returnUrl && returnUrl.startsWith('/') && !returnUrl.startsWith('//') ? returnUrl : DEFAULT_RETURN_URL);
  }

  clear(): void {
    this.tranId = '';
    this.detail = EMPTY_DETAIL;
    this.setMessage('', null);
    this.focusTranId();
  }

  browseTransactions(): void {
    this.setMessage('', null);
    this.menuService.select('main', TRANSACTION_LIST_MENU_OPTION).subscribe((result) => {
      if (result.outcome === 'navigate' && result.target?.route) {
        this.router.navigateByUrl(result.target.route);
        return;
      }
      this.setMessage(result.message ?? '', result.severity);
    });
  }

  @HostListener('window:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'F4') {
      event.preventDefault();
      this.clear();
      return;
    }
    if (event.key === 'F5') {
      event.preventDefault();
      this.browseTransactions();
      return;
    }
    const action = classifyAidKey(event);
    if (!action) {
      return;
    }
    event.preventDefault();
    if (action === 'exit') {
      this.back();
    } else {
      this.setMessage(MSG_INVALID_KEY, 'error');
    }
  }

  private fetch(): void {
    this.setMessage('', null);
    if (this.tranId.trim().length === 0) {
      this.setMessage('Tran ID can NOT be empty...', 'error');
      this.focusTranId();
      return;
    }

    this.detail = EMPTY_DETAIL;
    this.transactionViewService.view(this.tranId).subscribe({
      next: (detail) => {
        this.detail = detail;
        this.focusTranId();
      },
      error: (error: HttpErrorResponse) => {
        const message: unknown = error.error?.message;
        this.setMessage(typeof message === 'string' && message.length > 0 ? message : MSG_TRAN_LOOKUP_ERROR, 'error');
        this.focusTranId();
      }
    });
  }

  private setMessage(message: string, severity: 'error' | 'info' | null): void {
    this.message = message;
    this.messageSeverity = severity;
  }

  private focusTranId(): void {
    this.tranIdInput?.nativeElement.focus();
  }
}
