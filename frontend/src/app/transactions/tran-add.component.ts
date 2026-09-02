import { Component, ElementRef, HostListener, OnInit, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Observable } from 'rxjs';
import { MSG_INVALID_KEY, classifyAidKey } from '../shared/invalid-key';
import {
  TRAN_ADD_FIELD_LENGTHS,
  TranAddField,
  TranAddResponse,
  TranAddScreen,
  TranAddService,
  emptyTranAddScreen
} from './tran-add.service';

/** COTRN02C's fallback when the write path fails outside the mapped RESP codes (COTRN02C.cbl:745). */
export const MSG_UNABLE_TO_ADD = 'Unable to Add Transaction...';

/** PF-key legend line of the map (app/bms/COTRN02.bms:300-303). */
export const KEY_LEGEND = 'ENTER=Continue  F3=Back  F4=Clear  F5=Copy Last-Tran.';

/** Return target when there is no calling program in the COMMAREA (COTRN02C.cbl:503-505 → COMEN01C). */
export const RETURN_ROUTE = '/menu';

/**
 * Add-transaction screen, equivalent of BMS map COTRN2A (app/bms/COTRN02.bms): 14 input fields at their
 * BMS lengths, 78-char message line (red on error, green on success), ENTER validates/adds,
 * F3 returns to the menu, F4 clears, F5 copies the last transaction; any other function key is an
 * unmapped AID (FR-S09-01, 25, 26, 27, 29, 30).
 */
@Component({
  selector: 'app-tran-add',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule],
  templateUrl: './tran-add.component.html',
  styleUrl: './tran-add.component.scss'
})
export class TranAddComponent implements OnInit {
  private readonly tranAddService = inject(TranAddService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly transactionCode = 'CT02';
  readonly programName = 'COTRN02C';
  readonly title = 'Add Transaction';
  readonly keyLegend = KEY_LEGEND;
  readonly lengths = TRAN_ADD_FIELD_LENGTHS;

  screen: TranAddScreen = emptyTranAddScreen();
  message = '';
  messageSeverity: 'error' | 'success' | null = null;
  cursorField: TranAddField = 'accountId';
  busy = false;

  ngOnInit(): void {
    // CDEMO-CT02-TRN-SELECTED: a caller pre-selected card is placed in Card # and ENTER runs at once (COTRN02C.cbl:124-129).
    const selected = this.route.snapshot.queryParamMap.get('cardNumber');
    if (selected && selected.trim()) {
      this.screen.cardNumber = selected.trim();
      this.submit();
    }
  }

  /** ENTER. */
  submit(): void {
    this.perform(this.tranAddService.add(this.screen));
  }

  /** PF3: return to the caller (the main menu shell); nothing is written. */
  exit(): void {
    this.router.navigateByUrl(RETURN_ROUTE);
  }

  /** PF4: CLEAR-CURRENT-SCREEN — every field and the message blank, cursor on Acct # (COTRN02C.cbl:751-779). */
  clear(): void {
    this.screen = emptyTranAddScreen();
    this.message = '';
    this.messageSeverity = null;
    this.cursorField = 'accountId';
    this.focusCursorField();
  }

  /** PF5: copy the last transaction's data fields, then ENTER processing (COTRN02C.cbl:469-495). */
  copyLast(): void {
    this.perform(this.tranAddService.copyLast(this.screen));
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
      this.copyLast();
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

  private perform(request: Observable<TranAddResponse>): void {
    if (this.busy) {
      return;
    }
    this.busy = true;
    this.message = '';
    this.messageSeverity = null;
    request.subscribe({
      next: (response) => {
        this.busy = false;
        this.screen = { ...response.screen };
        this.message = response.message;
        this.messageSeverity = response.severity;
        this.cursorField = response.cursorField;
        this.focusCursorField();
      },
      error: (error: HttpErrorResponse) => {
        this.busy = false;
        this.message = typeof error.error?.message === 'string' ? error.error.message : MSG_UNABLE_TO_ADD;
        this.messageSeverity = 'error';
        this.cursorField = 'accountId';
        this.focusCursorField();
      }
    });
  }

  private focusCursorField(): void {
    setTimeout(() => {
      const input = this.host.nativeElement.querySelector<HTMLInputElement>(`[name="${this.cursorField}"]`);
      input?.focus();
    });
  }
}
