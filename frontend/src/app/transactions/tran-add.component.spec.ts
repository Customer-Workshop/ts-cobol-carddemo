import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { KEY_LEGEND, MSG_UNABLE_TO_ADD, RETURN_ROUTE, TranAddComponent } from './tran-add.component';
import { TRAN_ADD_FIELDS, TRAN_ADD_FIELD_LENGTHS, TranAddResponse, TranAddScreen, emptyTranAddScreen } from './tran-add.service';
import { MSG_INVALID_KEY } from '../shared/invalid-key';

const ADD_URL = '/api/v1/transactions/add';
const COPY_LAST_URL = '/api/v1/transactions/add/copy-last';

const FILLED_SCREEN: TranAddScreen = {
  accountId: '00000000050',
  cardNumber: '0500024453765740',
  typeCode: '01',
  categoryCode: '0001',
  source: 'POS TERM',
  description: 'Grocery run',
  amount: '+00000012.34',
  originalDate: '2024-03-01',
  processedDate: '2024-03-02',
  merchantId: '123456789',
  merchantName: 'Test Merchant',
  merchantCity: 'Testville',
  merchantZip: '12345',
  confirmation: ''
};

function response(overrides: Partial<TranAddResponse>): TranAddResponse {
  return {
    outcome: 'validationError',
    screen: emptyTranAddScreen(),
    message: '',
    severity: 'error',
    cursorField: 'accountId',
    transactionId: null,
    ...overrides
  };
}

function keydown(key: string): KeyboardEvent {
  const event = new KeyboardEvent('keydown', { key, cancelable: true });
  window.dispatchEvent(event);
  return event;
}

describe('TranAddComponent', () => {
  let fixture: ComponentFixture<TranAddComponent>;
  let component: TranAddComponent;
  let httpMock: HttpTestingController;
  let router: Router;

  async function setup(queryParams: Record<string, string> = {}): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [TranAddComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(TranAddComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl');
    fixture.detectChanges();
  }

  afterEach(() => {
    httpMock.verify();
  });

  /** ngModel writes to the DOM on a microtask, and the cursor focus on a macrotask. */
  async function settle(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve));
    fixture.detectChanges();
  }

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function messageEl(): HTMLElement | null {
    return element().querySelector<HTMLElement>('[data-testid="tran-add-message"]');
  }

  function messageText(): string | undefined {
    return messageEl()?.textContent?.trim();
  }

  function input(name: string): HTMLInputElement {
    return element().querySelector<HTMLInputElement>(`input[name="${name}"]`)!;
  }

  describe('without a pre-selected card', () => {
    beforeEach(async () => {
      await setup();
    });

    it('renders the 14 COTRN2A input fields at their BMS lengths, all blank, no message (FR-S09-01)', () => {
      for (const field of TRAN_ADD_FIELDS) {
        const el = input(field);
        expect(el).withContext(field).not.toBeNull();
        expect(el.maxLength).withContext(field).toBe(TRAN_ADD_FIELD_LENGTHS[field]);
        expect(el.value).withContext(field).toBe('');
      }
      expect(element().querySelectorAll('input[matinput]').length).toBe(14);
      expect(messageEl()).toBeNull();
      expect(component.cursorField).toBe('accountId');
      expect(element().querySelector('mat-card-title')?.textContent?.trim()).toBe('Add Transaction');
      expect(element().querySelector('[data-testid="screen-header"]')?.textContent).toContain('Tran: CT02');
      expect(element().querySelector('[data-testid="screen-header"]')?.textContent).toContain('Prog: COTRN02C');
      expect(element().querySelector('[data-testid="key-legend"]')?.textContent).toBe(KEY_LEGEND);
    });

    it('ENTER posts the screen as typed and redisplays the returned screen, message and cursor (FR-S09-02, 11)', async () => {
      component.screen = { ...FILLED_SCREEN, typeCode: '' };
      component.submit();

      const req = httpMock.expectOne(ADD_URL);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ ...FILLED_SCREEN, typeCode: '' });
      req.flush(
        response({
          screen: { ...FILLED_SCREEN, typeCode: '' },
          message: 'Type CD can NOT be empty...',
          cursorField: 'typeCode'
        })
      );
      await settle();

      expect(messageText()).toBe('Type CD can NOT be empty...');
      expect(messageEl()?.classList).toContain('error-message');
      expect(component.cursorField).toBe('typeCode');
      expect(document.activeElement).toBe(input('typeCode'));
      expect(input('accountId').value).toBe('00000000050');
    });

    it('shows the key-required message when both key fields are blank (FR-S09-02)', async () => {
      component.submit();

      httpMock.expectOne(ADD_URL).flush(
        response({ message: 'Account or Card Number must be entered...', cursorField: 'accountId' })
      );
      await settle();

      expect(messageText()).toBe('Account or Card Number must be entered...');
      expect(component.cursorField).toBe('accountId');
    });

    it('echoes back the xref-filled card and the normalised amount (FR-S09-04, 15, 18)', async () => {
      component.screen = { ...FILLED_SCREEN, cardNumber: '', amount: '-1.5' };
      component.submit();

      httpMock.expectOne(ADD_URL).flush(
        response({
          outcome: 'confirmationRequired',
          screen: { ...FILLED_SCREEN, amount: '-00000001.50' },
          message: 'Confirm to add this transaction...',
          cursorField: 'confirmation'
        })
      );
      await settle();

      expect(input('cardNumber').value).toBe('0500024453765740');
      expect(input('amount').value).toBe('-00000001.50');
      expect(messageText()).toBe('Confirm to add this transaction...');
      expect(component.cursorField).toBe('confirmation');
    });

    it('shows the invalid confirmation value message (FR-S09-19)', async () => {
      component.screen = { ...FILLED_SCREEN, confirmation: 'X' };
      component.submit();

      httpMock.expectOne(ADD_URL).flush(
        response({
          outcome: 'invalidConfirmation',
          screen: { ...FILLED_SCREEN, confirmation: 'X' },
          message: 'Invalid value. Valid values are (Y/N)...',
          cursorField: 'confirmation'
        })
      );
      await settle();

      expect(messageText()).toBe('Invalid value. Valid values are (Y/N)...');
      expect(input('confirmation').value).toBe('X');
    });

    it('on success clears every field and shows the green Tran ID message (FR-S09-21)', async () => {
      component.screen = { ...FILLED_SCREEN, confirmation: 'Y' };
      component.submit();

      httpMock.expectOne(ADD_URL).flush(
        response({
          outcome: 'added',
          screen: emptyTranAddScreen(),
          message: 'Transaction added successfully.  Your Tran ID is 0000000000000043.',
          severity: 'success',
          cursorField: 'accountId',
          transactionId: '0000000000000043'
        })
      );
      await settle();

      expect(messageText()).toBe('Transaction added successfully.  Your Tran ID is 0000000000000043.');
      expect(messageEl()?.textContent).toContain('successfully.  Your');
      expect(messageEl()?.classList).toContain('success-message');
      expect(messageEl()?.classList).not.toContain('error-message');
      for (const field of TRAN_ADD_FIELDS) {
        expect(input(field).value).withContext(field).toBe('');
      }
      expect(component.cursorField).toBe('accountId');
    });

    it('keeps the typed fields on a duplicate / write error (FR-S09-22, 23)', async () => {
      component.screen = { ...FILLED_SCREEN, confirmation: 'Y' };
      component.submit();

      httpMock.expectOne(ADD_URL).flush(
        response({
          outcome: 'duplicateTransactionId',
          screen: { ...FILLED_SCREEN, confirmation: 'Y' },
          message: 'Tran ID already exist...',
          cursorField: 'accountId'
        })
      );
      await settle();

      expect(messageText()).toBe('Tran ID already exist...');
      expect(input('description').value).toBe('Grocery run');
      expect(input('confirmation').value).toBe('Y');
    });

    it('surfaces the API-edge error message, falling back to the legacy add failure text', async () => {
      component.screen = { ...FILLED_SCREEN };
      component.submit();
      httpMock.expectOne(ADD_URL).flush({ message: 'Field accountId exceeds the BMS length of 11.' }, { status: 400, statusText: 'Bad Request' });
      await settle();
      expect(messageText()).toBe('Field accountId exceeds the BMS length of 11.');

      component.submit();
      httpMock.expectOne(ADD_URL).flush('boom', { status: 500, statusText: 'Server Error' });
      await settle();
      expect(messageText()).toBe(MSG_UNABLE_TO_ADD);
      expect(messageEl()?.classList).toContain('error-message');
    });

    it('F3 / Back returns to the menu without posting anything (FR-S09-25)', () => {
      component.screen = { ...FILLED_SCREEN };

      const event = keydown('F3');

      expect(event.defaultPrevented).toBeTrue();
      expect(router.navigateByUrl).toHaveBeenCalledWith(RETURN_ROUTE);
      httpMock.expectNone(ADD_URL);
      httpMock.expectNone(COPY_LAST_URL);

      element().querySelector<HTMLButtonElement>('[data-testid="exit-button"]')!.click();
      expect(router.navigateByUrl).toHaveBeenCalledTimes(2);
    });

    it('F4 / Clear blanks all 14 fields and the message, cursor on Acct # (FR-S09-26)', async () => {
      component.screen = { ...FILLED_SCREEN, confirmation: 'N' };
      component.message = 'Confirm to add this transaction...';
      component.messageSeverity = 'error';
      component.cursorField = 'confirmation';
      fixture.detectChanges();

      const event = keydown('F4');
      await settle();

      expect(event.defaultPrevented).toBeTrue();
      for (const field of TRAN_ADD_FIELDS) {
        expect(input(field).value).withContext(field).toBe('');
      }
      expect(messageEl()).toBeNull();
      expect(component.cursorField).toBe('accountId');
      expect(document.activeElement).toBe(input('accountId'));
      httpMock.expectNone(ADD_URL);

      component.screen = { ...FILLED_SCREEN };
      await settle();
      expect(input('accountId').value).toBe('00000000050');
      element().querySelector<HTMLButtonElement>('[data-testid="clear-button"]')!.click();
      await settle();
      expect(input('accountId').value).toBe('');
    });

    it('F5 / Copy Last-Tran posts the keyed screen to copy-last and redisplays the copied data (FR-S09-27)', async () => {
      component.screen = { ...emptyTranAddScreen(), accountId: '00000000050' };

      const event = keydown('F5');

      expect(event.defaultPrevented).toBeTrue();
      const req = httpMock.expectOne(COPY_LAST_URL);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ ...emptyTranAddScreen(), accountId: '00000000050' });
      req.flush(
        response({
          outcome: 'confirmationRequired',
          screen: { ...FILLED_SCREEN, amount: '+00000099.99' },
          message: 'Confirm to add this transaction...',
          cursorField: 'confirmation'
        })
      );
      await settle();

      expect(input('typeCode').value).toBe('01');
      expect(input('amount').value).toBe('+00000099.99');
      expect(input('originalDate').value).toBe('2024-03-01');
      expect(input('merchantZip').value).toBe('12345');
      expect(messageText()).toBe('Confirm to add this transaction...');
      httpMock.expectNone(ADD_URL);

      element().querySelector<HTMLButtonElement>('[data-testid="copy-last-button"]')!.click();
      httpMock.expectOne(COPY_LAST_URL).flush(
        response({ screen: { ...FILLED_SCREEN, typeCode: '' }, message: 'Type CD can NOT be empty...', cursorField: 'typeCode' })
      );
      await settle();
      expect(messageText()).toBe('Type CD can NOT be empty...');
    });

    it('F5 with no key first fails the key edits like ENTER (FR-S09-27 → FR-S09-02)', async () => {
      keydown('F5');

      httpMock.expectOne(COPY_LAST_URL).flush(
        response({ message: 'Account or Card Number must be entered...', cursorField: 'accountId' })
      );
      await settle();

      expect(messageText()).toBe('Account or Card Number must be entered...');
    });

    it('any other function key shows the invalid-key message and preserves the screen (FR-S09-29)', async () => {
      component.screen = { ...FILLED_SCREEN, confirmation: 'N' };
      await settle();

      for (const key of ['F1', 'F2', 'F6', 'F7', 'F8', 'F9', 'F10', 'F11', 'F12']) {
        component.message = '';
        const event = keydown(key);
        await settle();

        expect(event.defaultPrevented).withContext(key).toBeTrue();
        expect(messageText()).withContext(key).toBe(MSG_INVALID_KEY);
        expect(messageEl()?.classList).withContext(key).toContain('error-message');
      }
      expect(input('accountId').value).toBe('00000000050');
      expect(input('description').value).toBe('Grocery run');
      expect(input('confirmation').value).toBe('N');
      expect(router.navigateByUrl).not.toHaveBeenCalled();
      httpMock.expectNone(ADD_URL);
      httpMock.expectNone(COPY_LAST_URL);
    });

    it('ignores non-function keys', () => {
      const event = keydown('Enter');
      expect(event.defaultPrevented).toBeFalse();
      expect(messageEl()).toBeNull();
      httpMock.expectNone(ADD_URL);
    });

    it('ignores a second ENTER while the first is in flight', () => {
      component.screen = { ...FILLED_SCREEN };
      component.submit();
      component.submit();

      const requests = httpMock.match(ADD_URL);
      expect(requests.length).toBe(1);
      requests[0].flush(response({ screen: { ...FILLED_SCREEN }, message: 'Confirm to add this transaction...', cursorField: 'confirmation' }));
    });
  });

  describe('entered with a pre-selected card (CDEMO-CT02-TRN-SELECTED)', () => {
    it('places the card in Card # and runs ENTER processing immediately (FR-S09-30)', async () => {
      await setup({ cardNumber: '0500024453765740' });

      const req = httpMock.expectOne(ADD_URL);
      expect(req.request.body).toEqual({ ...emptyTranAddScreen(), cardNumber: '0500024453765740' });
      req.flush(
        response({
          screen: { ...emptyTranAddScreen(), accountId: '00000000050', cardNumber: '0500024453765740' },
          message: 'Type CD can NOT be empty...',
          cursorField: 'typeCode'
        })
      );
      await settle();

      expect(input('accountId').value).toBe('00000000050');
      expect(input('cardNumber').value).toBe('0500024453765740');
      expect(messageText()).toBe('Type CD can NOT be empty...');
    });

    it('treats a blank pre-selection as a plain first display (FR-S09-01)', async () => {
      await setup({ cardNumber: '   ' });

      httpMock.expectNone(ADD_URL);
      expect(input('cardNumber').value).toBe('');
      expect(messageEl()).toBeNull();
    });
  });
});
