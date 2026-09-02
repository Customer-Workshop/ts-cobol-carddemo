import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { BillPaymentComponent } from './bill-payment.component';
import { BillPaymentResponse } from './bill-payment.service';
import { MSG_INVALID_KEY } from '../shared/invalid-key';
import { routes } from '../app.routes';
import { authGuard } from '../auth/auth.guard';

const API = '/api/v1/bill-payment';

function response(overrides: Partial<BillPaymentResponse>): BillPaymentResponse {
  return {
    outcome: 'accountIdRequired',
    message: '',
    severity: 'error',
    cursorField: 'accountId',
    currentBalance: null,
    transactionId: null,
    clearScreen: false,
    ...overrides
  };
}

describe('BillPaymentComponent', () => {
  let fixture: ComponentFixture<BillPaymentComponent>;
  let component: BillPaymentComponent;
  let httpMock: HttpTestingController | undefined;
  let router: Router;

  async function setup(queryParams: Record<string, string> = {}): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [BillPaymentComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(BillPaymentComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl');
    fixture.detectChanges();
  }

  afterEach(() => {
    httpMock?.verify();
    httpMock = undefined;
  });

  function el<T extends HTMLElement>(testId: string): T {
    return (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${testId}"]`) as T;
  }

  function messageEl(): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector('[data-testid="bill-payment-message"]');
  }

  function pressKey(key: string): KeyboardEvent {
    const event = new KeyboardEvent('keydown', { key, cancelable: true });
    window.dispatchEvent(event);
    fixture.detectChanges();
    return event;
  }

  function submitAndFlush(body: BillPaymentResponse): void {
    component.submit();
    httpMock!.expectOne(API).flush(body);
    fixture.detectChanges();
  }

  describe('screen layout (COBIL0A)', () => {
    beforeEach(() => setup());

    it('renders the COBIL0A fields with BMS lengths: acct id 11, confirm 1, balance output, header', () => {
      expect(el<HTMLInputElement>('account-id-input').maxLength).toBe(11);
      expect(el<HTMLInputElement>('confirm-input').maxLength).toBe(1);
      expect(el('current-balance').textContent?.trim()).toBe('');
      expect(el('screen-header').textContent).toContain('CB00');
      expect(el('screen-header').textContent).toContain('COBIL00C');
      expect(fixture.nativeElement.textContent).toContain('Bill Payment');
      expect(el('enter-button')).toBeTruthy();
      expect(el('back-button').textContent).toContain('PF3');
      expect(el('clear-button').textContent).toContain('PF4');
      expect(messageEl()).toBeNull();
    });
  });

  describe('ENTER round trip', () => {
    beforeEach(() => setup());

    it('posts the typed acct id and confirm to /api/v1/bill-payment', () => {
      component.accountId = '00000000010';
      component.confirm = 'Y';
      component.submit();

      const req = httpMock!.expectOne(API);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ accountId: '00000000010', confirm: 'Y' });
      req.flush(response({ outcome: 'nothingToPay', message: 'You have nothing to pay...' }));
    });

    it('shows the blank-acct-id error in red and puts the cursor on Acct ID (FR-S11-01)', () => {
      submitAndFlush(response({ outcome: 'accountIdRequired', message: 'Acct ID can NOT be empty...' }));

      expect(messageEl()?.textContent?.trim()).toBe('Acct ID can NOT be empty...');
      expect(messageEl()?.classList).toContain('error-message');
      expect(document.activeElement).toBe(el('account-id-input'));
    });

    it('shows the Y/N error and puts the cursor on Confirm, keeping the shown balance (FR-S11-02)', () => {
      component.accountId = '00000000010';
      submitAndFlush(
        response({
          outcome: 'confirmationRequired',
          message: 'Confirm to make a bill payment...',
          cursorField: 'confirm',
          currentBalance: '+0000001234.56'
        })
      );
      component.confirm = 'Q';
      submitAndFlush(
        response({ outcome: 'invalidConfirmation', message: 'Invalid value. Valid values are (Y/N)...', cursorField: 'confirm' })
      );

      expect(messageEl()?.textContent?.trim()).toBe('Invalid value. Valid values are (Y/N)...');
      expect(el('current-balance').textContent?.trim()).toBe('+0000001234.56');
      expect(component.accountId).toBe('00000000010');
      expect(component.confirm).toBe('Q');
      expect(document.activeElement).toBe(el('confirm-input'));
    });

    it('clears all fields with no message when the user declines with N (FR-S11-03)', () => {
      component.accountId = '00000000010';
      component.currentBalance = '+0000001234.56';
      component.confirm = 'N';
      submitAndFlush(response({ outcome: 'declined', message: '', severity: null, clearScreen: true }));

      expect(component.accountId).toBe('');
      expect(component.currentBalance).toBe('');
      expect(component.confirm).toBe('');
      expect(messageEl()).toBeNull();
      expect(document.activeElement).toBe(el('account-id-input'));
    });

    it('shows not-found / lookup errors verbatim with the cursor on Acct ID (FR-S11-04, FR-S11-05)', () => {
      component.accountId = '99999999999';
      submitAndFlush(response({ outcome: 'accountNotFound', message: 'Account ID NOT found...' }));
      expect(messageEl()?.textContent?.trim()).toBe('Account ID NOT found...');
      expect(document.activeElement).toBe(el('account-id-input'));

      submitAndFlush(response({ outcome: 'accountLookupError', message: 'Unable to lookup Account...' }));
      expect(messageEl()?.textContent?.trim()).toBe('Unable to lookup Account...');
      expect(el('current-balance').textContent?.trim()).toBe('');
    });

    it('displays the 14-char balance and the nothing-to-pay message (FR-S11-06, FR-S11-07)', () => {
      component.accountId = '00000000010';
      submitAndFlush(
        response({ outcome: 'nothingToPay', message: 'You have nothing to pay...', currentBalance: '-0000000025.10' })
      );

      expect(el('current-balance').textContent?.trim()).toBe('-0000000025.10');
      expect(el('current-balance').textContent?.trim().length).toBe(14);
      expect(messageEl()?.textContent?.trim()).toBe('You have nothing to pay...');
      expect(document.activeElement).toBe(el('account-id-input'));
    });

    it('shows the balance and confirmation prompt with the cursor on Confirm (FR-S11-08)', () => {
      component.accountId = '00000000010';
      submitAndFlush(
        response({
          outcome: 'confirmationRequired',
          message: 'Confirm to make a bill payment...',
          cursorField: 'confirm',
          currentBalance: '+0000001234.56'
        })
      );

      expect(el('current-balance').textContent?.trim()).toBe('+0000001234.56');
      expect(messageEl()?.textContent?.trim()).toBe('Confirm to make a bill payment...');
      expect(messageEl()?.classList).toContain('error-message');
      expect(document.activeElement).toBe(el('confirm-input'));
    });

    it('shows the green success message with the transaction id and clears the fields (FR-S11-12)', () => {
      component.accountId = '00000000010';
      component.currentBalance = '+0000001234.56';
      component.confirm = 'Y';
      submitAndFlush(
        response({
          outcome: 'paymentSuccessful',
          message: 'Payment successful.  Your Transaction ID is 0000000000000124.',
          severity: 'success',
          transactionId: '0000000000000124',
          clearScreen: true
        })
      );

      expect(messageEl()?.textContent?.trim()).toBe('Payment successful.  Your Transaction ID is 0000000000000124.');
      expect(messageEl()?.classList).toContain('success-message');
      expect(messageEl()?.classList).not.toContain('error-message');
      expect(component.accountId).toBe('');
      expect(component.currentBalance).toBe('');
      expect(component.confirm).toBe('');
      expect(document.activeElement).toBe(el('account-id-input'));
    });

    it('shows write-path errors verbatim without clearing the screen (FR-S11-09, FR-S11-13, FR-S11-14, FR-S11-15)', () => {
      component.accountId = '00000000010';
      component.currentBalance = '+0000000100.00';
      component.confirm = 'Y';
      for (const message of [
        'Unable to lookup XREF AIX file...',
        'Tran ID already exist...',
        'Unable to Add Bill pay Transaction...',
        'Unable to Update Account...'
      ]) {
        submitAndFlush(response({ outcome: 'transactionWriteError', message }));
        expect(messageEl()?.textContent?.trim()).toBe(message);
        expect(messageEl()?.classList).toContain('error-message');
      }
      expect(component.accountId).toBe('00000000010');
      expect(el('current-balance').textContent?.trim()).toBe('+0000000100.00');
    });
  });

  describe('AID keys', () => {
    beforeEach(() => setup());

    it('Back button returns to the main menu (FR-S11-16)', () => {
      el<HTMLButtonElement>('back-button').click();
      expect(router.navigateByUrl).toHaveBeenCalledWith('/menu');
    });

    it('F3 returns to the main menu (FR-S11-16)', () => {
      const event = pressKey('F3');
      expect(event.defaultPrevented).toBeTrue();
      expect(router.navigateByUrl).toHaveBeenCalledWith('/menu');
    });

    it('F4 and the Clear button reset every field and the message (FR-S11-17)', () => {
      component.accountId = '00000000010';
      component.currentBalance = '+0000001234.56';
      component.confirm = 'Y';
      component.message = 'Confirm to make a bill payment...';
      component.messageSeverity = 'error';
      fixture.detectChanges();

      const event = pressKey('F4');

      expect(event.defaultPrevented).toBeTrue();
      expect(component.accountId).toBe('');
      expect(component.currentBalance).toBe('');
      expect(component.confirm).toBe('');
      expect(messageEl()).toBeNull();
      expect(document.activeElement).toBe(el('account-id-input'));
      expect(router.navigateByUrl).not.toHaveBeenCalled();

      component.accountId = '1';
      el<HTMLButtonElement>('clear-button').click();
      expect(component.accountId).toBe('');
    });

    it('other function keys show the invalid-key message without navigating or posting (FR-S11-18)', () => {
      for (const key of ['F1', 'F2', 'F5', 'F7', 'F8', 'F12']) {
        const event = pressKey(key);
        expect(event.defaultPrevented).toBeTrue();
        expect(messageEl()?.textContent?.trim()).toBe(MSG_INVALID_KEY);
        expect(messageEl()?.classList).toContain('error-message');
      }
      expect(router.navigateByUrl).not.toHaveBeenCalled();
      httpMock!.expectNone(API);
    });

    it('ordinary keys are ignored', () => {
      const event = pressKey('a');
      expect(event.defaultPrevented).toBeFalse();
      expect(messageEl()).toBeNull();
    });
  });

  describe('preselected account (FR-S11-20)', () => {
    it('pre-fills the acct id from the query parameter and processes it immediately', async () => {
      await setup({ accountId: '00000000010' });

      const req = httpMock!.expectOne(API);
      expect(req.request.body).toEqual({ accountId: '00000000010', confirm: '' });
      req.flush(
        response({
          outcome: 'confirmationRequired',
          message: 'Confirm to make a bill payment...',
          cursorField: 'confirm',
          currentBalance: '+0000001234.56'
        })
      );
      fixture.detectChanges();

      expect(el<HTMLInputElement>('account-id-input').value).toBe('00000000010');
      expect(el('current-balance').textContent?.trim()).toBe('+0000001234.56');
      expect(messageEl()?.textContent?.trim()).toBe('Confirm to make a bill payment...');
    });

    it('does nothing on first display without a query parameter', async () => {
      await setup();
      httpMock!.expectNone(API);
      expect(component.accountId).toBe('');
      expect(messageEl()).toBeNull();
    });
  });

  describe('route registration (FR-S11-19)', () => {
    it('is registered at /bill-payment behind authGuard only (no admin restriction in COBIL00C)', () => {
      const route = routes.find((r) => r.path === 'bill-payment');
      expect(route?.component).toBe(BillPaymentComponent);
      expect(route?.canActivate).toEqual([authGuard]);
    });
  });
});
