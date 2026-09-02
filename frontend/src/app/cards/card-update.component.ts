import { Component, HostListener, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import {
  CardUpdateAid,
  CardUpdateDetails,
  CardUpdateFieldName,
  CardUpdateScreen,
  CardUpdateService,
  CardUpdateState
} from './card-update.service';

/**
 * Card update screen, equivalent of BMS map CCRDUPA (app/bms/COCRDUP.bms) driven by COCRDUPC
 * (transaction CCUP). Account (11) and card (16) are the search keys; name (50), status (1),
 * expiry month (2) and year (4) become editable once the card is fetched; day (2) is display-only.
 * ENTER processes, F3 exits to the menu, F5 saves after validation, F12 cancels; every other
 * function key is processed as ENTER (COCRDUPC.cbl:413-424, FR-S06-02).
 */
@Component({
  selector: 'app-card-update',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule],
  templateUrl: './card-update.component.html',
  styleUrl: './card-update.component.scss'
})
export class CardUpdateComponent implements OnInit {
  private readonly cardUpdateService = inject(CardUpdateService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly title = 'Update Credit Card Details';

  state: CardUpdateState = 'notFetched';
  original: CardUpdateDetails | null = null;

  accountId = '';
  cardNumber = '';
  embossedName = '';
  activeStatus = '';
  expiryMonth = '';
  expiryYear = '';
  expiryDay = '';

  infoMessage = 'Please enter Account and Card Number';
  errorMessage = '';
  fieldsInError: CardUpdateFieldName[] = [];
  cursorField: CardUpdateFieldName = 'accountId';
  searchEditable = true;
  detailsEditable = false;
  confirmKeysVisible = false;

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    const acctId = params.get('accountId') ?? params.get('acctId');
    const cardNum = params.get('cardNumber') ?? params.get('cardNum');
    if (acctId && cardNum) {
      this.accountId = acctId;
      this.cardNumber = cardNum;
      this.send('enter');
      return;
    }
    this.cardUpdateService.initialScreen().subscribe((screen) => this.apply(screen));
  }

  submit(): void {
    this.send('enter');
  }

  save(): void {
    this.send('pf5');
  }

  cancel(): void {
    this.send('pf12');
  }

  exit(): void {
    this.router.navigateByUrl('/menu');
  }

  isInError(field: CardUpdateFieldName): boolean {
    return this.fieldsInError.includes(field);
  }

  @HostListener('window:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if (!/^F([1-9]|1[0-2])$/.test(event.key)) {
      return;
    }
    event.preventDefault();
    switch (event.key) {
      case 'F3':
        this.exit();
        break;
      case 'F5':
        this.save();
        break;
      case 'F12':
        this.cancel();
        break;
      default:
        this.submit();
    }
  }

  private send(aid: CardUpdateAid): void {
    this.cardUpdateService
      .process({
        aid,
        state: this.state,
        accountId: this.accountId,
        cardNumber: this.cardNumber,
        original: this.original,
        input:
          this.original === null
            ? null
            : {
                embossedName: this.embossedName,
                activeStatus: this.activeStatus,
                expiryMonth: this.expiryMonth,
                expiryYear: this.expiryYear
              }
      })
      .subscribe((screen) => this.apply(screen));
  }

  private apply(screen: CardUpdateScreen): void {
    this.state = screen.state;
    this.original = screen.original;
    this.accountId = screen.accountId;
    this.cardNumber = screen.cardNumber;
    this.embossedName = screen.embossedName;
    this.activeStatus = screen.activeStatus;
    this.expiryMonth = screen.expiryMonth;
    this.expiryYear = screen.expiryYear;
    this.expiryDay = screen.expiryDay;
    this.infoMessage = screen.infoMessage;
    this.errorMessage = screen.errorMessage ?? '';
    this.fieldsInError = screen.fieldsInError;
    this.cursorField = screen.cursorField;
    this.searchEditable = screen.searchEditable;
    this.detailsEditable = screen.detailsEditable;
    this.confirmKeysVisible = screen.confirmKeysVisible;
    setTimeout(() => document.querySelector<HTMLInputElement>(`[data-field="${screen.cursorField}"]`)?.focus());
  }
}
