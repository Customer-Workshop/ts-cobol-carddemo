import { Component, HostListener, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MSG_INVALID_KEY, classifyAidKey } from '../shared/invalid-key';
import {
  AccountUpdateChangeResponse,
  AccountUpdateFields,
  AccountUpdateLookupResponse,
  AccountUpdateService,
  emptyAccountUpdateFields
} from './account-update.service';

export const MSG_PROMPT_FOR_SEARCH_KEYS = 'Enter or update id of account to update';

/**
 * Screen states of COACTUPC (ACUP-CHANGE-ACTION 88s, app/cbl/COACTUPC.cbl:604-618):
 * search = ACUP-DETAILS-NOT-FETCHED, details = ACUP-SHOW-DETAILS, editError = ACUP-CHANGES-NOT-OK,
 * confirm = ACUP-CHANGES-OK-NOT-CONFIRMED, done = ACUP-CHANGES-OKAYED-AND-DONE,
 * failed = ACUP-CHANGES-OKAYED-LOCK-ERROR / ACUP-CHANGES-OKAYED-BUT-FAILED.
 */
export type AccountUpdateScreenState = 'search' | 'details' | 'editError' | 'confirm' | 'done' | 'failed';

export type AccountUpdateFieldKey = keyof AccountUpdateFields;

/** Validation field names returned by the API mapped onto the map fields they cover. */
const INVALID_FIELD_KEYS: Record<string, AccountUpdateFieldKey[]> = {
  accountId: ['accountId'],
  activeStatus: ['activeStatus'],
  openDate: ['openYear', 'openMonth', 'openDay'],
  creditLimit: ['creditLimit'],
  expiryDate: ['expiryYear', 'expiryMonth', 'expiryDay'],
  cashCreditLimit: ['cashCreditLimit'],
  reissueDate: ['reissueYear', 'reissueMonth', 'reissueDay'],
  currentBalance: ['currentBalance'],
  currentCycleCredit: ['currentCycleCredit'],
  currentCycleDebit: ['currentCycleDebit'],
  ssn1: ['ssn1'],
  ssn2: ['ssn2'],
  ssn3: ['ssn3'],
  dateOfBirth: ['dobYear', 'dobMonth', 'dobDay'],
  ficoScore: ['ficoScore'],
  firstName: ['firstName'],
  middleName: ['middleName'],
  lastName: ['lastName'],
  addressLine1: ['addressLine1'],
  state: ['state'],
  zip: ['zip'],
  city: ['city'],
  country: ['country'],
  phone1: ['phone1Area', 'phone1Prefix', 'phone1Line'],
  phone2: ['phone2Area', 'phone2Prefix', 'phone2Line'],
  eftAccountId: ['eftAccountId'],
  primaryCardHolder: ['primaryCardHolder']
};

export interface AccountUpdateFieldSpec {
  key: AccountUpdateFieldKey;
  label: string;
  length: number;
}

const f = (key: AccountUpdateFieldKey, label: string, length: number): AccountUpdateFieldSpec => ({ key, label, length });

/** Rows 5-11 of CACTUPA: account section, field lengths from the DFHMDF LENGTH attributes. */
export const ACCOUNT_ROWS: AccountUpdateFieldSpec[][] = [
  [f('accountId', 'Account Number :', 11), f('activeStatus', 'Active Y/N:', 1)],
  [f('openYear', 'Opened :', 4), f('openMonth', '-', 2), f('openDay', '-', 2), f('creditLimit', 'Credit Limit        :', 15)],
  [f('expiryYear', 'Expiry :', 4), f('expiryMonth', '-', 2), f('expiryDay', '-', 2), f('cashCreditLimit', 'Cash credit Limit   :', 15)],
  [f('reissueYear', 'Reissue:', 4), f('reissueMonth', '-', 2), f('reissueDay', '-', 2), f('currentBalance', 'Current Balance     :', 15)],
  [f('currentCycleCredit', 'Current Cycle Credit:', 15), f('groupId', 'Account Group:', 10)],
  [f('currentCycleDebit', 'Current Cycle Debit :', 15)]
];

/** Rows 12-21 of CACTUPA: customer section. */
export const CUSTOMER_ROWS: AccountUpdateFieldSpec[][] = [
  [f('customerId', 'Customer id  :', 9), f('ssn1', 'SSN:', 3), f('ssn2', '-', 2), f('ssn3', '-', 4)],
  [f('dobYear', 'Date of birth:', 4), f('dobMonth', '-', 2), f('dobDay', '-', 2), f('ficoScore', 'FICO Score:', 3)],
  [f('firstName', 'First Name', 25), f('middleName', 'Middle Name:', 25), f('lastName', 'Last Name :', 25)],
  [f('addressLine1', 'Address:', 50), f('state', 'State', 2)],
  [f('addressLine2', '', 50), f('zip', 'Zip', 5)],
  [f('city', 'City', 50), f('country', 'Country', 3)],
  [f('phone1Area', 'Phone 1:', 3), f('phone1Prefix', '', 3), f('phone1Line', '', 4), f('governmentId', 'Government Issued Id Ref    :', 20)],
  [f('phone2Area', 'Phone 2:', 3), f('phone2Prefix', '', 3), f('phone2Line', '', 4), f('eftAccountId', 'EFT Account Id:', 10), f('primaryCardHolder', 'Primary Card Holder Y/N:', 1)]
];

/** ACSTNUM and ACSCTRY are ASKIP in every state (3320-SETUP-SCREEN-ATTRS, :3536, :3548-3549). */
const ALWAYS_PROTECTED: ReadonlySet<AccountUpdateFieldKey> = new Set<AccountUpdateFieldKey>(['customerId', 'country']);

/**
 * Account Update screen, equivalent of BMS map CACTUPA (app/bms/COACTUP.bms) driven by COACTUPC:
 * account id search, account + customer detail edits, info line (row 22) and error line (row 23),
 * ENTER processes, F3 exits to the menu, F5 saves only after validation, F12 cancels back to the
 * fetched values; any other function key is an unmapped AID (S-01 parity, FR-S01-20).
 */
@Component({
  selector: 'app-account-update',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatCardModule],
  templateUrl: './account-update.component.html',
  styleUrl: './account-update.component.scss'
})
export class AccountUpdateComponent {
  private readonly accountUpdateService = inject(AccountUpdateService);
  private readonly router = inject(Router);

  readonly title = 'Update Account';
  readonly sections: { title: string; rows: AccountUpdateFieldSpec[][] }[] = [
    { title: '', rows: ACCOUNT_ROWS },
    { title: 'Customer Details', rows: CUSTOMER_ROWS }
  ];
  state: AccountUpdateScreenState = 'search';
  fields: AccountUpdateFields = emptyAccountUpdateFields();
  original: AccountUpdateFields | null = null;
  infoMessage = MSG_PROMPT_FOR_SEARCH_KEYS;
  errorMessage = '';
  invalidKeys: ReadonlySet<AccountUpdateFieldKey> = new Set();

  get saveLit(): boolean {
    return this.state === 'confirm';
  }

  get cancelLit(): boolean {
    return this.state === 'editError' || this.state === 'confirm' || this.state === 'failed';
  }

  isProtected(key: AccountUpdateFieldKey): boolean {
    if (key === 'accountId') {
      return !(this.state === 'search' || this.state === 'failed');
    }
    if (ALWAYS_PROTECTED.has(key)) {
      return true;
    }
    return this.state !== 'details' && this.state !== 'editError';
  }

  isInvalid(key: AccountUpdateFieldKey): boolean {
    return this.invalidKeys.has(key);
  }

  /** ENTER (CCARD-AID-ENTER) dispatched per 2000-DECIDE-ACTION (:2562-2642). */
  submit(): void {
    switch (this.state) {
      case 'search':
        this.lookup(this.fields.accountId);
        return;
      case 'details':
      case 'editError':
      case 'failed':
        this.validate();
        return;
      case 'done':
        this.lookup(this.original?.accountId ?? this.fields.accountId);
        return;
      case 'confirm':
        return;
    }
  }

  /** F5 (CCARD-AID-PFK05) is only meaningful in the confirm state (:2600-2612). */
  save(): void {
    if (this.state !== 'confirm' || !this.original) {
      this.invalidKey();
      return;
    }
    const original = this.original;
    this.accountUpdateService.save(original, this.fields).subscribe((result) => this.applyChangeResult(result, original));
  }

  /** F12 (CCARD-AID-PFK12) re-reads the fetched account once details are on screen (:2571-2580). */
  cancel(): void {
    if (this.state === 'search' || !this.original) {
      this.invalidKey();
      return;
    }
    this.lookup(this.original.accountId);
  }

  /** F3 (CCARD-AID-PFK03) returns to the calling menu (:927-960). */
  exit(): void {
    this.router.navigateByUrl('/menu');
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
      return;
    }
    if (event.key === 'F5' && this.state === 'confirm') {
      this.save();
      return;
    }
    if (event.key === 'F12' && this.state !== 'search') {
      this.cancel();
      return;
    }
    this.invalidKey();
  }

  private lookup(accountId: string): void {
    this.accountUpdateService.lookup(accountId).subscribe((result) => this.applyLookupResult(result));
  }

  private validate(): void {
    if (!this.original) {
      return;
    }
    const original = this.original;
    this.accountUpdateService.validate(original, this.fields).subscribe((result) => this.applyChangeResult(result, original));
  }

  private applyLookupResult(result: AccountUpdateLookupResponse): void {
    this.invalidKeys = new Set();
    this.infoMessage = result.infoMessage ?? '';
    this.errorMessage = result.errorMessage ?? '';
    if (result.outcome === 'details' && result.fields) {
      this.state = 'details';
      this.original = { ...result.fields };
      this.fields = { ...result.fields };
      return;
    }
    if (this.state === 'search') {
      this.original = null;
      this.fields = { ...emptyAccountUpdateFields(), accountId: this.fields.accountId };
    }
  }

  private applyChangeResult(result: AccountUpdateChangeResponse, original: AccountUpdateFields): void {
    this.infoMessage = result.infoMessage ?? '';
    this.errorMessage = result.errorMessage ?? '';
    this.invalidKeys = new Set();
    switch (result.outcome) {
      case 'noChanges':
      case 'changedByOther':
        this.state = 'details';
        this.fields = { ...original };
        return;
      case 'invalid':
        this.state = 'editError';
        this.flagInvalid(result.invalidFields);
        return;
      case 'confirm':
        this.state = 'confirm';
        return;
      case 'committed':
        this.state = 'done';
        return;
      case 'failed':
        this.state = 'failed';
        return;
      default:
        this.state = 'details';
        return;
    }
  }

  /** CSSETATY: invalid fields turn red and a blank invalid field is shown as '*'. */
  private flagInvalid(invalidFields: string[]): void {
    const keys = new Set<AccountUpdateFieldKey>();
    for (const name of invalidFields) {
      for (const key of INVALID_FIELD_KEYS[name] ?? []) {
        keys.add(key);
        if (this.fields[key].trim() === '') {
          this.fields = { ...this.fields, [key]: '*' };
        }
      }
    }
    this.invalidKeys = keys;
  }

  private invalidKey(): void {
    this.errorMessage = MSG_INVALID_KEY;
  }
}
