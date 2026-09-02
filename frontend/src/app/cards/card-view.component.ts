import { HttpErrorResponse } from '@angular/common/http';
import { AfterViewInit, Component, ElementRef, HostListener, OnInit, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { classifyAidKey } from '../shared/invalid-key';
import { CardViewCursor, CardViewDetails, CardViewFilterState, CardViewResponse, CardViewService } from './card-view.service';

export const MSG_PROMPT_FOR_INPUT = 'Please enter Account and Card Number';

export const ACCOUNT_ID_LENGTH = 11;
export const CARD_NUMBER_LENGTH = 16;

/**
 * Credit card detail screen, equivalent of BMS map CCRDSLA (app/bms/COCRDSL.bms):
 * 11-char account and 16-char card inputs, name / active flag / expiry outputs,
 * 40-char info and 80-char error areas, ENTER searches, Exit / F3 returns to the caller
 * (FR-S05-01, 13, 14, 16); any other function key is remapped to ENTER (FR-S05-15).
 * Entering with both keys in the query string is the COCRDLIC hand-off (S05-B2).
 */
@Component({
  selector: 'app-card-view',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule],
  templateUrl: './card-view.component.html',
  styleUrl: './card-view.component.scss',
  preserveWhitespaces: true
})
export class CardViewComponent implements OnInit, AfterViewInit {
  private readonly cardViewService = inject(CardViewService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  @ViewChild('accountInput') private accountInput?: ElementRef<HTMLInputElement>;
  @ViewChild('cardInput') private cardInput?: ElementRef<HTMLInputElement>;

  readonly title = 'View Credit Card Detail';
  readonly accountIdLength = ACCOUNT_ID_LENGTH;
  readonly cardNumberLength = CARD_NUMBER_LENGTH;

  accountId = '';
  cardNumber = '';
  accountFilter: CardViewFilterState = 'valid';
  cardFilter: CardViewFilterState = 'valid';
  infoMessage = MSG_PROMPT_FOR_INPUT;
  errorMessage = '';
  card: CardViewDetails | null = null;
  fromCardList = false;
  private returnUrl = '/menu';

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    const returnUrl = params.get('returnUrl');
    if (returnUrl && returnUrl.startsWith('/')) {
      this.returnUrl = returnUrl;
    }
    const accountId = params.get('accountId');
    const cardNumber = params.get('cardNumber');
    if (accountId !== null && cardNumber !== null) {
      this.fromCardList = true;
      this.accountId = accountId;
      this.cardNumber = cardNumber;
      this.search();
    }
  }

  ngAfterViewInit(): void {
    if (!this.fromCardList) {
      queueMicrotask(() => this.placeCursor('account'));
    }
  }

  submit(): void {
    this.search();
  }

  exit(): void {
    this.router.navigateByUrl(this.returnUrl);
  }

  @HostListener('window:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    const action = classifyAidKey(event);
    if (!action) {
      return;
    }
    event.preventDefault();
    if (action === 'exit') {
      this.exit();
    } else {
      this.submit();
    }
  }

  private search(): void {
    this.cardViewService.view(this.accountId, this.cardNumber, this.fromCardList).subscribe({
      next: (response) => this.apply(response),
      error: (error: HttpErrorResponse) => this.applyFailure(error)
    });
  }

  private apply(response: CardViewResponse): void {
    this.accountId = response.accountId;
    this.cardNumber = response.cardNumber;
    this.accountFilter = response.accountFilter;
    this.cardFilter = response.cardFilter;
    this.infoMessage = response.infoMessage;
    this.errorMessage = response.message;
    this.card = response.card;
    this.placeCursor(response.cursor);
  }

  private applyFailure(error: HttpErrorResponse): void {
    const body = error.error as Partial<CardViewResponse> | null;
    if (body && typeof body.message === 'string' && body.outcome === 'storeError') {
      this.apply({
        outcome: 'storeError',
        message: body.message,
        infoMessage: body.infoMessage ?? MSG_PROMPT_FOR_INPUT,
        accountId: body.accountId ?? this.accountId,
        cardNumber: body.cardNumber ?? this.cardNumber,
        accountFilter: body.accountFilter ?? 'notOk',
        cardFilter: body.cardFilter ?? 'valid',
        cursor: body.cursor ?? 'account',
        card: null
      });
      return;
    }
    this.card = null;
    this.accountFilter = 'notOk';
    this.cardFilter = 'valid';
    this.infoMessage = MSG_PROMPT_FOR_INPUT;
    this.errorMessage = fileErrorMessage(error.status);
    this.placeCursor('account');
  }

  private placeCursor(cursor: CardViewCursor): void {
    const target = cursor === 'card' ? this.cardInput : this.accountInput;
    target?.nativeElement.focus();
  }
}

/** WS-FILE-ERROR-MESSAGE frame (COCRDSLC.cbl:102-121) for a READ that never reached the store. */
export function fileErrorMessage(resp: number, resp2 = 0): string {
  const code = (value: number): string => String(value).padStart(9, '0').padEnd(10, ' ');
  return `File Error: READ     on CARDDAT   returned RESP ${code(resp)},RESP2 ${code(resp2)}`.slice(0, 75).trimEnd();
}
