import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Router } from '@angular/router';
import { AccountViewComponent } from './account-view.component';
import { AccountViewResponse } from './account-view.service';
import { routes } from '../app.routes';
import { authGuard } from '../auth/auth.guard';
import { MSG_INVALID_KEY } from '../shared/invalid-key';

const PROMPT = 'Enter or update id of account to display';
const VIEW_URL = '/api/v1/accounts/view';

const ACCOUNT_BLOCK = {
  activeStatus: 'Y',
  openDate: '2014-11-20',
  creditLimit: '+      2,020.00',
  expirationDate: '2025-05-20',
  cashCreditLimit: '+      1,020.00',
  reissueDate: '2025-05-20',
  currentBalance: '+        194.00',
  currentCycleCredit: '+           .00',
  groupId: '',
  currentCycleDebit: '+           .00'
};

const CUSTOMER_BLOCK = {
  customerId: '000000001',
  ssn: '020-97-3888',
  dateOfBirth: '1961-06-08',
  ficoScore: '274',
  firstName: 'Immanuel',
  middleName: 'Madeline',
  lastName: 'Kessler',
  addressLine1: '618 Deshaun Route',
  addressLine2: 'Apt. 802',
  state: 'NC',
  city: 'Altenwerthshire',
  zip: '12546',
  country: 'USA',
  phone1: '(908)119-8310',
  phone2: '(373)693-8684',
  governmentIssuedId: '00000000000049368437',
  eftAccountId: '0053581756',
  primaryCardHolder: 'Y'
};

function screen(overrides: Partial<AccountViewResponse>): AccountViewResponse {
  return {
    outcome: 'found',
    accountId: '00000000001',
    accountFieldState: 'valid',
    infoMessage: PROMPT,
    errorMessage: '',
    account: null,
    customer: null,
    ...overrides
  };
}

describe('AccountViewComponent', () => {
  let fixture: ComponentFixture<AccountViewComponent>;
  let component: AccountViewComponent;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AccountViewComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()]
    }).compileComponents();

    fixture = TestBed.createComponent(AccountViewComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl');
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  function el(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function text(testId: string): string {
    return el().querySelector(`[data-testid="${testId}"]`)?.textContent?.trim() ?? '';
  }

  function input(): HTMLInputElement {
    return el().querySelector('[data-testid="account-id-input"]') as HTMLInputElement;
  }

  function fieldIsRed(): boolean {
    return el().querySelector('.account-field')!.classList.contains('field-error');
  }

  function submitAndRespond(accountId: string, response: AccountViewResponse, status = 200): void {
    component.accountId = accountId;
    component.submit();
    const request = httpMock.expectOne((r) => r.url === VIEW_URL);
    expect(request.request.params.get('accountId')).toBe(accountId);
    if (status === 200) {
      request.flush(response);
    } else {
      request.flush(response, { status, statusText: 'Internal Server Error' });
    }
    fixture.detectChanges();
  }

  it('renders an empty account field, the prompt, no error and no data on entry (FR-S02-01)', () => {
    expect(text('tran-name')).toBe('Tran: CAVW');
    expect(text('prog-name')).toBe('Prog: COACTVWC');
    expect(input().value).toBe('');
    expect(fieldIsRed()).toBeFalse();
    expect(text('info-message')).toBe(PROMPT);
    expect(text('error-message')).toBe('');
    expect(text('active-status')).toBe('');
    expect(text('customer-id')).toBe('');
  });

  it('limits the account field to 11 characters like ACCTSID (FR-S02-14)', () => {
    expect(input().getAttribute('maxlength')).toBe('11');
  });

  it('shows "No input received" and echoes * in red for a blank submit (FR-S02-02)', async () => {
    submitAndRespond(
      '',
      screen({ outcome: 'noInput', accountId: '*', accountFieldState: 'blank', errorMessage: 'No input received' })
    );
    await fixture.whenStable();
    fixture.detectChanges();

    expect(text('error-message')).toBe('No input received');
    expect(component.accountId).toBe('*');
    expect(input().value).toBe('*');
    expect(fieldIsRed()).toBeTrue();
    expect(text('info-message')).toBe(PROMPT);
    expect(text('current-balance')).toBe('');
  });

  it('echoes the typed text in red with the filter message for a non-numeric id (FR-S02-03)', () => {
    submitAndRespond(
      '12ab',
      screen({
        outcome: 'invalidFilter',
        accountId: '12ab',
        accountFieldState: 'invalid',
        errorMessage: 'Account Filter must  be a non-zero 11 digit number'
      })
    );

    expect(text('error-message')).toBe('Account Filter must  be a non-zero 11 digit number');
    expect(component.accountId).toBe('12ab');
    expect(fieldIsRed()).toBeTrue();
    expect(text('active-status')).toBe('');
  });

  it('renders the cross-ref not-found message with no data blocks (FR-S02-04)', () => {
    submitAndRespond(
      '00000000099',
      screen({
        outcome: 'accountNotInXref',
        accountId: '00000000099',
        accountFieldState: 'invalid',
        errorMessage: 'Account:00000000099 not found in Cross ref file.  Resp:000000013  Reas:0000'
      })
    );

    expect(text('error-message')).toBe('Account:00000000099 not found in Cross ref file.  Resp:000000013  Reas:0000');
    expect(fieldIsRed()).toBeTrue();
    expect(text('open-date')).toBe('');
    expect(text('first-name')).toBe('');
  });

  it('renders the account master not-found message with no data blocks (FR-S02-05)', () => {
    submitAndRespond(
      '00000000901',
      screen({
        outcome: 'accountNotInMaster',
        accountId: '00000000901',
        accountFieldState: 'invalid',
        errorMessage: 'Account:00000000901 not found in Acct Master file.Resp:000000013  Reas:0000'
      })
    );

    expect(text('error-message')).toBe('Account:00000000901 not found in Acct Master file.Resp:000000013  Reas:0000');
    expect(fieldIsRed()).toBeTrue();
    expect(text('credit-limit')).toBe('');
    expect(text('ssn')).toBe('');
  });

  it('keeps the account block and blanks the customer block when the customer is missing (FR-S02-06)', () => {
    submitAndRespond(
      '00000000902',
      screen({
        outcome: 'customerNotFound',
        accountId: '00000000902',
        accountFieldState: 'valid',
        errorMessage: 'CustId:000000999 not found in customer master.Resp: 000000013  REAS:0000000',
        account: ACCOUNT_BLOCK
      })
    );

    expect(text('error-message')).toBe('CustId:000000999 not found in customer master.Resp: 000000013  REAS:0000000');
    expect(fieldIsRed()).toBeFalse();
    expect(text('active-status')).toBe('Y');
    expect(text('current-balance')).toBe('+        194.00');
    expect(text('customer-id')).toBe('');
    expect(text('last-name')).toBe('');
  });

  it('renders every account and customer field from the response (FR-S02-07)', () => {
    submitAndRespond('00000000001', screen({ account: ACCOUNT_BLOCK, customer: CUSTOMER_BLOCK }));

    expect(text('error-message')).toBe('');
    expect(text('info-message')).toBe(PROMPT);
    expect(fieldIsRed()).toBeFalse();

    expect(text('active-status')).toBe('Y');
    expect(text('open-date')).toBe('2014-11-20');
    expect(text('credit-limit')).toBe('+      2,020.00');
    expect(text('expiration-date')).toBe('2025-05-20');
    expect(text('cash-credit-limit')).toBe('+      1,020.00');
    expect(text('reissue-date')).toBe('2025-05-20');
    expect(text('current-balance')).toBe('+        194.00');
    expect(text('cycle-credit')).toBe('+           .00');
    expect(text('group-id')).toBe('');
    expect(text('cycle-debit')).toBe('+           .00');

    expect(text('customer-id')).toBe('000000001');
    expect(text('ssn')).toBe('020-97-3888');
    expect(text('date-of-birth')).toBe('1961-06-08');
    expect(text('fico-score')).toBe('274');
    expect(text('first-name')).toBe('Immanuel');
    expect(text('middle-name')).toBe('Madeline');
    expect(text('last-name')).toBe('Kessler');
    expect(text('address-line1')).toBe('618 Deshaun Route');
    expect(text('address-line2')).toBe('Apt. 802');
    expect(text('state')).toBe('NC');
    expect(text('city')).toBe('Altenwerthshire');
    expect(text('zip')).toBe('12546');
    expect(text('country')).toBe('USA');
    expect(text('phone1')).toBe('(908)119-8310');
    expect(text('phone2')).toBe('(373)693-8684');
    expect(text('government-id')).toBe('00000000000049368437');
    expect(text('eft-account-id')).toBe('0053581756');
    expect(text('primary-card-holder')).toBe('Y');
  });

  it('renders the file error from a 500 body and keeps an account block it carries (FR-S02-13)', () => {
    submitAndRespond(
      '00000000001',
      screen({
        outcome: 'storeError',
        accountFieldState: 'valid',
        errorMessage: 'File Error: READ     on CUSTDAT   returned RESP 000000017 ,RESP2 000000120 ',
        account: ACCOUNT_BLOCK
      }),
      500
    );

    expect(text('error-message')).toBe('File Error: READ     on CUSTDAT   returned RESP 000000017 ,RESP2 000000120');
    expect(component.errorMessage).toBe('File Error: READ     on CUSTDAT   returned RESP 000000017 ,RESP2 000000120 ');
    expect(text('active-status')).toBe('Y');
    expect(text('customer-id')).toBe('');
  });

  it('clears previous data when a later submit fails (FR-S02-04 after FR-S02-07)', () => {
    submitAndRespond('00000000001', screen({ account: ACCOUNT_BLOCK, customer: CUSTOMER_BLOCK }));
    submitAndRespond(
      '00000000099',
      screen({
        outcome: 'accountNotInXref',
        accountId: '00000000099',
        accountFieldState: 'invalid',
        errorMessage: 'Account:00000000099 not found in Cross ref file.  Resp:000000013  Reas:0000'
      })
    );

    expect(text('active-status')).toBe('');
    expect(text('last-name')).toBe('');
  });

  it('Exit button returns to the main menu (FR-S02-11)', () => {
    (el().querySelector('[data-testid="exit-button"]') as HTMLButtonElement).click();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/menu');
  });

  it('F3 returns to the main menu (FR-S02-11)', () => {
    const event = new KeyboardEvent('keydown', { key: 'F3', cancelable: true });
    window.dispatchEvent(event);

    expect(event.defaultPrevented).toBeTrue();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/menu');
  });

  it('any other function key is treated as ENTER and shows no invalid-key text (FR-S02-12)', () => {
    component.accountId = '00000000001';
    const event = new KeyboardEvent('keydown', { key: 'F7', cancelable: true });
    window.dispatchEvent(event);

    expect(event.defaultPrevented).toBeTrue();
    const request = httpMock.expectOne((r) => r.url === VIEW_URL);
    expect(request.request.params.get('accountId')).toBe('00000000001');
    request.flush(screen({ account: ACCOUNT_BLOCK, customer: CUSTOMER_BLOCK }));
    fixture.detectChanges();

    expect(text('error-message')).toBe('');
    expect(el().textContent).not.toContain(MSG_INVALID_KEY);
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('ENTER submits the form (FR-S02-02..07 entry path)', () => {
    component.accountId = '00000000001';
    (el().querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));

    httpMock.expectOne((r) => r.url === VIEW_URL).flush(screen({ account: ACCOUNT_BLOCK, customer: CUSTOMER_BLOCK }));
    fixture.detectChanges();

    expect(text('customer-id')).toBe('000000001');
  });

  it('ignores non-function keys (FR-S02-12)', () => {
    const event = new KeyboardEvent('keydown', { key: 'a', cancelable: true });
    window.dispatchEvent(event);

    expect(event.defaultPrevented).toBeFalse();
    httpMock.expectNone((r) => r.url === VIEW_URL);
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('is routed at /accounts/view behind authGuard only (FR-S02-15)', () => {
    const route = routes.find((r) => r.path === 'accounts/view');

    expect(route?.component).toBe(AccountViewComponent);
    expect(route?.canActivate).toEqual([authGuard]);
  });
});
