import { Component, ElementRef, HostListener, OnInit, QueryList, ViewChild, ViewChildren, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { classifyAidKey } from '../shared/invalid-key';
import { CardListAid, CardListPageState, CardListResponse, CardListRow, CardListService } from './card-list.service';

export const CARD_LIST_PAGE_SIZE = 7;
export const ACCOUNT_FILTER_LENGTH = 11;
export const CARD_FILTER_LENGTH = 16;

/**
 * Credit card list screen, equivalent of BMS map CCRDLIA (app/bms/COCRDLI.bms) driven by
 * COCRDLIC (transaction CCLI): 11/16-digit account and card filters, 7 result rows each with
 * a 1-char Select field, page number, info and error message areas. ENTER re-lists / acts on
 * a selection, F3 exits to the main menu, F7 pages backward, F8 pages forward; any other
 * function key is passed through as its PF number and treated as ENTER by the service (FR-S04-18).
 */
@Component({
  selector: 'app-card-list',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule],
  templateUrl: './card-list.component.html',
  styleUrl: './card-list.component.scss'
})
export class CardListComponent implements OnInit {
  private readonly cardListService = inject(CardListService);
  private readonly router = inject(Router);

  @ViewChild('accountInput') private accountInput?: ElementRef<HTMLInputElement>;
  @ViewChild('cardInput') private cardInput?: ElementRef<HTMLInputElement>;
  @ViewChildren('selectInput') private selectInputs?: QueryList<ElementRef<HTMLInputElement>>;

  readonly title = 'List Credit Cards';
  readonly transaction = 'CCLI';
  readonly program = 'COCRDLIC';
  readonly accountFilterLength = ACCOUNT_FILTER_LENGTH;
  readonly cardFilterLength = CARD_FILTER_LENGTH;
  readonly footer = 'F3=Exit F7=Backward  F8=Forward';

  screenNumber = 1;
  accountFilter = '';
  cardFilter = '';
  accountFilterError = false;
  cardFilterError = false;
  rows: CardListRow[] = CardListComponent.blankRows();
  selections: string[] = Array<string>(CARD_LIST_PAGE_SIZE).fill('');
  errorMessage = '';
  infoMessage = '';
  state: CardListPageState | null = null;
  busy = false;

  ngOnInit(): void {
    this.send('ENTER');
  }

  submit(): void {
    this.send('ENTER');
  }

  exit(): void {
    this.send('PF3');
  }

  backward(): void {
    this.send('PF7');
  }

  forward(): void {
    this.send('PF8');
  }

  @HostListener('window:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if (!classifyAidKey(event)) {
      return;
    }
    event.preventDefault();
    this.send(`PF${event.key.substring(1)}`);
  }

  send(aid: CardListAid): void {
    if (this.busy) {
      return;
    }
    this.busy = true;
    this.cardListService
      .list({
        aid,
        state: this.state,
        accountFilter: this.accountFilter,
        cardFilter: this.cardFilter,
        selections: this.selections.map((s) => (s === '' ? null : s))
      })
      .subscribe({
        next: (response) => {
          this.busy = false;
          this.apply(response);
        },
        error: () => {
          this.busy = false;
        }
      });
  }

  private apply(response: CardListResponse): void {
    if (response.outcome === 'exit') {
      this.router.navigateByUrl(response.target?.route ?? '/menu');
      return;
    }
    if (response.outcome === 'navigate' && response.target?.route) {
      this.router.navigate([response.target.route], {
        queryParams: { accountId: response.target.accountId, cardNumber: response.target.cardNumber }
      });
      return;
    }

    this.screenNumber = response.screenNumber;
    this.accountFilter = response.accountFilter;
    this.cardFilter = response.cardFilter;
    this.accountFilterError = response.accountFilterError;
    this.cardFilterError = response.cardFilterError;
    this.rows = response.rows;
    this.selections = response.rows.map((r) => r.selection);
    this.state = response.state;
    if (response.outcome === 'comingSoon' || response.outcome === 'notInstalled') {
      this.errorMessage = response.message ?? '';
      this.infoMessage = '';
    } else {
      this.errorMessage = response.errorMessage;
      this.infoMessage = response.infoMessage;
    }
    setTimeout(() => this.placeCursor(response.cursorField));
  }

  private placeCursor(cursorField: string): void {
    if (cursorField === 'card') {
      this.cardInput?.nativeElement.focus();
      return;
    }
    const select = /^select([1-7])$/.exec(cursorField);
    if (select) {
      this.selectInputs?.get(Number(select[1]) - 1)?.nativeElement.focus();
      return;
    }
    this.accountInput?.nativeElement.focus();
  }

  private static blankRows(): CardListRow[] {
    return Array.from({ length: CARD_LIST_PAGE_SIZE }, () => ({
      hasCard: false,
      accountId: '',
      cardNumber: '',
      activeStatus: '',
      selection: '',
      selectionError: false,
      selectionProtected: true
    }));
  }
}
