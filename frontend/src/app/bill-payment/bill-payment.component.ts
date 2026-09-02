import { Component, ElementRef, HostListener, OnInit, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { BillPaymentCursorField, BillPaymentResponse, BillPaymentService } from './bill-payment.service';
import { MSG_INVALID_KEY, classifyAidKey } from '../shared/invalid-key';

/**
 * Bill Payment screen, equivalent of BMS map COBIL0A (app/bms/COBIL00.bms):
 * 11-char account id, 14-char current balance, 1-char Y/N confirmation, 78-char message area.
 * ENTER submits (FR-S11-01..15), F3 returns to the menu (FR-S11-16), F4 clears the screen
 * (FR-S11-17), any other function key is an unmapped AID (FR-S11-18). An `accountId` query
 * parameter mirrors CDEMO-CB00-TRN-SELECTED: pre-fill and process immediately (FR-S11-20).
 */
@Component({
  selector: 'app-bill-payment',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule],
  templateUrl: './bill-payment.component.html',
  styleUrl: './bill-payment.component.scss'
})
export class BillPaymentComponent implements OnInit {
  static readonly RETURN_ROUTE = '/menu';

  private readonly billPaymentService = inject(BillPaymentService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  @ViewChild('accountIdInput') private accountIdInput?: ElementRef<HTMLInputElement>;
  @ViewChild('confirmInput') private confirmInput?: ElementRef<HTMLInputElement>;

  readonly title = 'Bill Payment';
  readonly transactionName = 'CB00';
  readonly programName = 'COBIL00C';

  accountId = '';
  currentBalance = '';
  confirm = '';
  message = '';
  messageSeverity: 'error' | 'success' | null = null;

  ngOnInit(): void {
    const preselected = this.route.snapshot.queryParamMap.get('accountId');
    if (preselected && preselected.trim()) {
      this.accountId = preselected.trim().slice(0, 11);
      this.submit();
    }
  }

  submit(): void {
    this.billPaymentService.pay(this.accountId, this.confirm).subscribe((result) => this.apply(result));
  }

  back(): void {
    this.router.navigateByUrl(BillPaymentComponent.RETURN_ROUTE);
  }

  clear(): void {
    this.accountId = '';
    this.currentBalance = '';
    this.confirm = '';
    this.message = '';
    this.messageSeverity = null;
    this.focus('accountId');
  }

  @HostListener('window:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'F4') {
      event.preventDefault();
      this.clear();
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
      this.message = MSG_INVALID_KEY;
      this.messageSeverity = 'error';
    }
  }

  private apply(result: BillPaymentResponse): void {
    if (result.clearScreen) {
      this.accountId = '';
      this.currentBalance = '';
      this.confirm = '';
    } else if (result.currentBalance !== null) {
      this.currentBalance = result.currentBalance;
    }
    this.message = result.message;
    this.messageSeverity = result.severity;
    this.focus(result.cursorField);
  }

  private focus(field: BillPaymentCursorField): void {
    const target = field === 'confirm' ? this.confirmInput : this.accountIdInput;
    target?.nativeElement.focus();
  }
}
