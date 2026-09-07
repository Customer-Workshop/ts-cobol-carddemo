import { AfterViewInit, Component, ElementRef, HostListener, OnInit, ViewChild, inject } from '@angular/core';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { ScreenHeader } from '../../core/auth.service';
import { AccountBlock, AccountService, AccountView, AccountViewError, CustomerBlock } from '../../core/account.service';

/**
 * COACTVWC account view — map CACTVWA of mapset COACTVW (app/bms/COACTVW.bms), field for field.
 * ACCTSID (POS 5,25 LENGTH=11 UNPROT,IC) is the only user-input field; every account and customer
 * field is display-only (ASKIP / PROT), INFOMSG carries the constant prompt and ERRMSG the verbatim
 * message. Keys: ENTER = Search, F3 / Esc = Exit to the menu (DV-03: no other key is bound and no
 * "invalid key" message exists). Entry paints the empty map without a read (cbl:290-296).
 */
@Component({
  selector: 'app-account-view',
  standalone: true,
  imports: [MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  templateUrl: './account-view.component.html',
  styleUrl: './account-view.component.scss',
})
export class AccountViewComponent implements OnInit, AfterViewInit {
  private readonly accountService = inject(AccountService);
  private readonly router = inject(Router);

  @ViewChild('accountInput') private accountInput?: ElementRef<HTMLInputElement>;

  /** Static text of the map, verbatim (app/bms/COACTVW.bms:33-373). */
  readonly labels = {
    tran: 'Tran:',
    date: 'Date:',
    prog: 'Prog:',
    time: 'Time:',
    title: 'View Account',
    accountNumber: 'Account Number :',
    activeStatus: 'Active Y/N: ',
    opened: 'Opened:',
    creditLimit: 'Credit Limit        :',
    expiry: 'Expiry:',
    cashCreditLimit: 'Cash credit Limit   :',
    reissue: 'Reissue:',
    currentBalance: 'Current Balance     :',
    currentCycleCredit: 'Current Cycle Credit:',
    accountGroup: 'Account Group:',
    currentCycleDebit: 'Current Cycle Debit :',
    customerDetails: 'Customer Details',
    customerId: 'Customer id  :',
    ssn: 'SSN:',
    dateOfBirth: 'Date of birth:',
    fico: 'FICO Score:',
    firstName: 'First Name',
    middleName: 'Middle Name: ',
    lastName: 'Last Name : ',
    address: 'Address:',
    state: 'State ',
    zip: 'Zip',
    city: 'City ',
    country: 'Country',
    phone1: 'Phone 1:',
    governmentId: 'Government Issued Id Ref    : ',
    phone2: 'Phone 2:',
    eftAccount: 'EFT Account Id: ',
    primaryCardHolder: 'Primary Card Holder Y/N:',
    footer: '  F3=Exit ',
  };

  /** INFOMSG is a constant on this screen (COACTVWC.cbl:452-455). */
  readonly infoMessage = 'Enter or update id of account to display';

  /** E-04, the message that marks the blank filter (COACTVWC.cbl:124, :628-633). */
  private static readonly NO_INPUT_RECEIVED = 'No input received';

  /** MOVE '*' TO ACCTSIDO on a blank filter re-enter (COACTVWC.cbl:561-565). */
  private static readonly BLANK_FILTER_ECHO = '*';

  /** ACCTSID: LENGTH=11, NUM (app/bms/COACTVW.bms:84-90). */
  readonly maxLength = 11;

  header: ScreenHeader = {
    tranId: 'CAVW',
    programName: 'COACTVWC',
    title01: '',
    title02: '',
    currentDate: 'mm/dd/yy',
    currentTime: 'hh:mm:ss',
    applId: '',
    sysId: '',
  };

  accountId = '';
  account: AccountBlock | null = null;
  customer: CustomerBlock | null = null;
  errorMessage = '';
  submitting = false;

  /** MOVE DFHRED TO ACCTSIDC when FLG-ACCTFILTER-NOT-OK (COACTVWC.cbl:555-559). */
  accountIdInError = false;

  /**
   * MOVE CUST-FICO-CREDIT-SCORE (PIC 9(3)) TO ACSTFCOO (PIC X(3)) keeps the leading zeroes the
   * numeric field carries, so score 78 paints as {@code 078} (COACTVWC.cbl:505-506). The API keeps
   * the value an integer (FR §3.2), so the three-character screen form is built here.
   */
  get ficoScoreDisplay(): string {
    const score = this.customer?.ficoScore;
    return score === null || score === undefined ? '' : String(score).padStart(3, '0');
  }

  /**
   * 1100-SCREEN-INIT paints TITLE01/TITLE02, the trancode, the program name and the current date and
   * time on every SEND MAP, the initial one included (COACTVWC.cbl:431-453), so the header is filled
   * before the first search. No account file is read on entry (:290-296).
   */
  ngOnInit(): void {
    this.accountService.header().subscribe({
      next: (header: ScreenHeader) => (this.header = header),
      error: (error: HttpErrorResponse) => {
        if (error.status === 401) {
          void this.router.navigateByUrl('/signon');
        }
      },
    });
  }

  /**
   * ATTRB=(...,IC) on ACCTSID puts the cursor in the account field on every SEND MAP. The initial
   * focus is deferred to a microtask so it lands after the first change-detection pass.
   */
  ngAfterViewInit(): void {
    void Promise.resolve().then(() => this.focusAccountId());
  }

  /**
   * ACCTSID is LENGTH=11, so the field never holds more (bms:84-90). Non-numeric characters are NOT
   * filtered out here: 2210-EDIT-ACCOUNT owns that branch and answers E-05 (cbl:666-677), which is
   * the behaviour the screen shows.
   */
  onAccountIdInput(value: string): void {
    this.accountId = value.slice(0, this.maxLength);
  }

  /** ENTER -> PROCESS-ENTER-KEY -> 2000-PROCESS-INPUTS (COACTVWC.cbl:302-321). */
  search(): void {
    if (this.submitting) {
      return;
    }
    this.errorMessage = '';
    this.accountIdInError = false;
    this.submitting = true;
    this.accountService.view(this.accountId).subscribe({
      next: (view: AccountView) => {
        this.submitting = false;
        this.header = view.header;
        this.accountId = view.accountNumber;
        this.account = view.account;
        this.customer = view.customer;
        this.focusAccountId();
      },
      error: (error: HttpErrorResponse) => {
        this.submitting = false;
        this.handleError(error);
      },
    });
  }

  /** PF3 -> EXEC CICS XCTL 'COMEN01C' (COACTVWC.cbl:322-336): the screen state is dropped. */
  exit(): void {
    this.accountId = '';
    this.account = null;
    this.customer = null;
    this.errorMessage = '';
    this.accountIdInError = false;
    void this.router.navigateByUrl('/menu');
  }

  /** DV-03: only F3 and Escape are bound; every other key falls through with no message. */
  @HostListener('window:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'F3' || event.key === 'Escape') {
      event.preventDefault();
      this.exit();
    }
  }

  /**
   * 401 is the target form of the {@code EIBCALEN = 0} refusal (cbl:266-270): back to sign-on.
   * Otherwise the screen stays up showing the verbatim message with the cursor back in ACCTSID
   * (cbl:645, :673, :755); on the E-11 branch the account block the program had already painted
   * stays on screen (FR-20) while the customer block is cleared.
   */
  private handleError(error: HttpErrorResponse): void {
    if (error.status === 401) {
      void this.router.navigateByUrl('/signon');
      return;
    }
    const body = (error.error ?? null) as AccountViewError | null;
    this.errorMessage = body?.message ?? '';
    this.account = body?.account ?? null;
    this.customer = null;
    // 1300-SETUP-SCREEN-ATTRS: a failed account filter turns the field red, and the blank filter on a
    // re-enter is echoed as '*' in the field itself (cbl:555-565).
    this.accountIdInError = error.status === 400;
    if (this.errorMessage === AccountViewComponent.NO_INPUT_RECEIVED) {
      this.accountId = AccountViewComponent.BLANK_FILTER_ECHO;
    }
    this.focusAccountId();
  }

  private focusAccountId(): void {
    this.accountInput?.nativeElement.focus();
  }
}
