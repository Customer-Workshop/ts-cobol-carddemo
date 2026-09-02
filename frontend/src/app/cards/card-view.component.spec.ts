import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { CardViewComponent, MSG_PROMPT_FOR_INPUT, fileErrorMessage } from './card-view.component';
import { CardViewResponse } from './card-view.service';
import { MSG_INVALID_KEY } from '../shared/invalid-key';

const SEED_ACCOUNT = '00000000050';
const SEED_CARD = '0500024453765740';

const FOUND_RESPONSE: CardViewResponse = {
  outcome: 'found',
  message: '',
  infoMessage: '   Displaying requested details',
  accountId: SEED_ACCOUNT,
  cardNumber: SEED_CARD,
  accountFilter: 'valid',
  cardFilter: 'valid',
  cursor: 'account',
  card: { embossedName: 'Aniya Von', expiryMonth: '03', expiryYear: '2023', activeStatus: 'Y' }
};

describe('CardViewComponent', () => {
  let fixture: ComponentFixture<CardViewComponent>;
  let component: CardViewComponent;
  let httpMock: HttpTestingController;
  let router: Router;

  async function setup(queryParams: Record<string, string> = {}): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [CardViewComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CardViewComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl');
    await settle();
  }

  afterEach(() => {
    httpMock.verify();
  });

  async function settle(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function element<T extends HTMLElement>(testId: string): T {
    return (fixture.nativeElement as HTMLElement).querySelector<T>(`[data-testid="${testId}"]`)!;
  }

  function text(testId: string): string {
    return element(testId).textContent ?? '';
  }

  function accountInput(): HTMLInputElement {
    return element<HTMLInputElement>('account-input');
  }

  function cardInput(): HTMLInputElement {
    return element<HTMLInputElement>('card-input');
  }

  function expectView(): TestRequest {
    return httpMock.expectOne((request) => request.url === '/api/v1/cards/view');
  }

  function search(accountId: string, cardNumber: string): TestRequest {
    component.accountId = accountId;
    component.cardNumber = cardNumber;
    component.submit();
    return expectView();
  }

  function inputError(overrides: Partial<CardViewResponse>): CardViewResponse {
    return {
      outcome: 'inputError',
      message: '',
      infoMessage: MSG_PROMPT_FOR_INPUT,
      accountId: SEED_ACCOUNT,
      cardNumber: SEED_CARD,
      accountFilter: 'valid',
      cardFilter: 'valid',
      cursor: 'account',
      card: null,
      ...overrides
    };
  }

  describe('entered from the menu', () => {
    beforeEach(async () => {
      await setup();
    });

    it('shows the empty prompt screen with the cursor on Account (FR-S05-01)', async () => {
      expect(text('info-message')).toBe(MSG_PROMPT_FOR_INPUT);
      expect(text('error-message')).toBe('');
      expect(accountInput().value).toBe('');
      expect(cardInput().value).toBe('');
      expect(accountInput().readOnly).toBeFalse();
      expect(cardInput().readOnly).toBeFalse();
      expect(text('card-name')).toBe('');
      expect(text('card-status')).toBe('');
      expect(document.activeElement).toBe(accountInput());
    });

    it('mirrors the BMS map: title, labels, field lengths and PF footer (FR-S05-16)', async () => {
      const host = fixture.nativeElement as HTMLElement;
      expect(host.querySelector('mat-card-title')?.textContent?.trim()).toBe('View Credit Card Detail');
      expect(accountInput().getAttribute('maxlength')).toBe('11');
      expect(cardInput().getAttribute('maxlength')).toBe('16');
      const labels = Array.from(host.querySelectorAll('.field-label')).map((l) => l.textContent);
      expect(labels).toEqual([
        'Account Number    :',
        'Card Number       :',
        'Name on card      :',
        'Card Active Y/N   :',
        'Expiry Date       :'
      ]);
      expect(element('search-button').textContent?.trim()).toBe('ENTER=Search Cards');
      expect(element('exit-button').textContent?.trim()).toBe('F3=Exit');
    });

    it('sends both keys to the API on ENTER without the card-list flag', async () => {
      const request = search(SEED_ACCOUNT, SEED_CARD);

      expect(request.request.params.get('accountId')).toBe(SEED_ACCOUNT);
      expect(request.request.params.get('cardNumber')).toBe(SEED_CARD);
      expect(request.request.params.get('fromCardList')).toBe('false');
      request.flush(FOUND_RESPONSE);
    });

    it('shows a blank account as a red "*" with the account prompt (FR-S05-02)', async () => {
      search('', SEED_CARD).flush(
        inputError({ message: 'Account number not provided', accountId: '*', accountFilter: 'blank', cursor: 'account' })
      );
      await settle();

      expect(text('error-message')).toBe('Account number not provided');
      expect(accountInput().value).toBe('*');
      expect(accountInput().classList).toContain('field-error');
      expect(cardInput().value).toBe(SEED_CARD);
      expect(cardInput().classList).not.toContain('field-error');
      expect(document.activeElement).toBe(accountInput());
    });

    it('clears and flags a non-numeric account (FR-S05-03)', async () => {
      search('12345', SEED_CARD).flush(
        inputError({
          message: 'ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER',
          accountId: '',
          accountFilter: 'notOk',
          cursor: 'account'
        })
      );
      await settle();

      expect(text('error-message')).toBe('ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER');
      expect(accountInput().value).toBe('');
      expect(accountInput().classList).toContain('field-error');
      expect(document.activeElement).toBe(accountInput());
    });

    it('shows a blank card as a red "*" with the cursor on Card (FR-S05-04)', async () => {
      search(SEED_ACCOUNT, '').flush(
        inputError({ message: 'Card number not provided', cardNumber: '*', cardFilter: 'blank', cursor: 'card' })
      );
      await settle();

      expect(text('error-message')).toBe('Card number not provided');
      expect(cardInput().value).toBe('*');
      expect(cardInput().classList).toContain('field-error');
      expect(accountInput().classList).not.toContain('field-error');
      expect(document.activeElement).toBe(cardInput());
    });

    it('clears and flags a non-numeric card (FR-S05-05)', async () => {
      search(SEED_ACCOUNT, '1234').flush(
        inputError({
          message: 'CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER',
          cardNumber: '',
          cardFilter: 'notOk',
          cursor: 'card'
        })
      );
      await settle();

      expect(text('error-message')).toBe('CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER');
      expect(cardInput().value).toBe('');
      expect(cardInput().classList).toContain('field-error');
      expect(document.activeElement).toBe(cardInput());
    });

    it('shows "No input received" with both fields flagged (FR-S05-06)', async () => {
      search('', '').flush(
        inputError({
          message: 'No input received',
          accountId: '*',
          cardNumber: '*',
          accountFilter: 'blank',
          cardFilter: 'blank',
          cursor: 'account'
        })
      );
      await settle();

      expect(text('error-message')).toBe('No input received');
      expect(accountInput().value).toBe('*');
      expect(cardInput().value).toBe('*');
      expect(accountInput().classList).toContain('field-error');
      expect(cardInput().classList).toContain('field-error');
      expect(document.activeElement).toBe(accountInput());
    });

    it('displays the card details and the info message when found (FR-S05-09)', async () => {
      search(SEED_ACCOUNT, SEED_CARD).flush(FOUND_RESPONSE);
      await settle();

      expect(text('card-name')).toBe('Aniya Von');
      expect(text('card-status')).toBe('Y');
      expect(text('expiry-month')).toBe('03');
      expect(text('expiry-year')).toBe('2023');
      expect(text('info-message')).toBe('   Displaying requested details');
      expect(text('error-message')).toBe('');
      expect(accountInput().value).toBe(SEED_ACCOUNT);
      expect(cardInput().value).toBe(SEED_CARD);
      expect(accountInput().classList).not.toContain('field-error');
      expect(cardInput().classList).not.toContain('field-error');
      expect(document.activeElement).toBe(accountInput());
    });

    it('flags both fields, keeps the values and blanks the details when not found (FR-S05-10)', async () => {
      search(SEED_ACCOUNT, SEED_CARD).flush(FOUND_RESPONSE);
      await settle();
      search(SEED_ACCOUNT, '9999999999999999').flush({
        ...inputError({
          message: 'Did not find cards for this search condition',
          cardNumber: '9999999999999999',
          accountFilter: 'notOk',
          cardFilter: 'notOk'
        }),
        outcome: 'notFound'
      });
      await settle();

      expect(text('error-message')).toBe('Did not find cards for this search condition');
      expect(text('info-message')).toBe(MSG_PROMPT_FOR_INPUT);
      expect(accountInput().value).toBe(SEED_ACCOUNT);
      expect(cardInput().value).toBe('9999999999999999');
      expect(accountInput().classList).toContain('field-error');
      expect(cardInput().classList).toContain('field-error');
      expect(text('card-name')).toBe('');
      expect(text('card-status')).toBe('');
      expect(text('expiry-month')).toBe('');
      expect(text('expiry-year')).toBe('');
    });

    it('shows the file-error message returned by the API on a store error (FR-S05-11)', async () => {
      const message = 'File Error: READ     on CARDDAT   returned RESP 2148734208,RESP2 000000000';
      search(SEED_ACCOUNT, SEED_CARD).flush(
        { ...inputError({ message, accountFilter: 'notOk' }), outcome: 'storeError' },
        { status: 500, statusText: 'Internal Server Error' }
      );
      await settle();

      expect(text('error-message')).toBe(message);
      expect(accountInput().classList).toContain('field-error');
      expect(cardInput().classList).not.toContain('field-error');
      expect(text('card-name')).toBe('');
    });

    it('frames an unreachable API as a CARDDAT file error (FR-S05-11)', async () => {
      search(SEED_ACCOUNT, SEED_CARD).error(new ProgressEvent('error'), { status: 0 });
      await settle();

      expect(text('error-message')).toBe('File Error: READ     on CARDDAT   returned RESP 000000000 ,RESP2 000000000');
      expect(fileErrorMessage(13, 80)).toBe('File Error: READ     on CARDDAT   returned RESP 000000013 ,RESP2 000000080');
      expect(accountInput().classList).toContain('field-error');
    });

    it('returns to the main menu on Exit when there is no caller (FR-S05-14)', async () => {
      element('exit-button').click();

      expect(router.navigateByUrl).toHaveBeenCalledWith('/menu');
    });

    it('returns to the main menu on F3 like PF3 (FR-S05-14)', async () => {
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'F3' }));

      expect(router.navigateByUrl).toHaveBeenCalledWith('/menu');
    });

    it('treats an unmapped function key as ENTER without an invalid-key message (FR-S05-15)', async () => {
      component.accountId = SEED_ACCOUNT;
      component.cardNumber = SEED_CARD;

      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'F7' }));

      const request = expectView();
      expect(request.request.params.get('accountId')).toBe(SEED_ACCOUNT);
      request.flush(FOUND_RESPONSE);
      await settle();

      expect(text('error-message')).toBe('');
      expect((fixture.nativeElement as HTMLElement).textContent).not.toContain(MSG_INVALID_KEY);
      expect(router.navigateByUrl).not.toHaveBeenCalled();
    });

    it('ignores non-function keys', async () => {
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'a' }));

      httpMock.expectNone((request) => request.url === '/api/v1/cards/view');
      expect(router.navigateByUrl).not.toHaveBeenCalled();
    });
  });

  describe('entered from the card list (S05-B2)', () => {
    it('reads the selected card immediately with protected inputs (FR-S05-13)', async () => {
      await setup({ accountId: SEED_ACCOUNT, cardNumber: SEED_CARD, returnUrl: '/cards/list' });

      const request = expectView();
      expect(request.request.params.get('accountId')).toBe(SEED_ACCOUNT);
      expect(request.request.params.get('cardNumber')).toBe(SEED_CARD);
      expect(request.request.params.get('fromCardList')).toBe('true');
      request.flush(FOUND_RESPONSE);
      await settle();

      expect(accountInput().readOnly).toBeTrue();
      expect(cardInput().readOnly).toBeTrue();
      expect(text('card-name')).toBe('Aniya Von');
      expect(text('info-message')).toBe('   Displaying requested details');
    });

    it('returns to the calling screen on Exit / F3 (FR-S05-14)', async () => {
      await setup({ accountId: SEED_ACCOUNT, cardNumber: SEED_CARD, returnUrl: '/cards/list' });
      expectView().flush(FOUND_RESPONSE);

      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'F3' }));

      expect(router.navigateByUrl).toHaveBeenCalledWith('/cards/list');
    });

    it('falls back to the main menu for a non-local return url (FR-S05-14)', async () => {
      await setup({ returnUrl: 'https://example.invalid/' });

      element('exit-button').click();

      expect(router.navigateByUrl).toHaveBeenCalledWith('/menu');
    });
  });
});
