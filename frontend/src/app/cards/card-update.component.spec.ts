import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { CardUpdateComponent } from './card-update.component';
import { CardUpdateScreen } from './card-update.service';

const ENDPOINT = '/api/v1/cards/update';

const FRESH_SCREEN: CardUpdateScreen = {
  state: 'notFetched',
  infoMessage: 'Please enter Account and Card Number',
  errorMessage: null,
  accountId: '',
  cardNumber: '',
  embossedName: '',
  activeStatus: '',
  expiryMonth: '',
  expiryYear: '',
  expiryDay: '',
  original: null,
  fieldsInError: [],
  cursorField: 'accountId',
  searchEditable: true,
  detailsEditable: false,
  confirmKeysVisible: false
};

const ORIGINAL = {
  accountId: '00000000050',
  cardNumber: '0500024453765740',
  embossedName: 'ANIYA VON',
  expiryYear: '2023',
  expiryMonth: '03',
  expiryDay: '09',
  activeStatus: 'Y'
};

const SHOW_DETAILS: CardUpdateScreen = {
  ...FRESH_SCREEN,
  state: 'showDetails',
  infoMessage: 'Details of selected card shown above',
  accountId: ORIGINAL.accountId,
  cardNumber: ORIGINAL.cardNumber,
  embossedName: ORIGINAL.embossedName,
  activeStatus: ORIGINAL.activeStatus,
  expiryMonth: ORIGINAL.expiryMonth,
  expiryYear: ORIGINAL.expiryYear,
  expiryDay: ORIGINAL.expiryDay,
  original: ORIGINAL,
  cursorField: 'embossedName',
  searchEditable: false,
  detailsEditable: true
};

const CONFIRM: CardUpdateScreen = {
  ...SHOW_DETAILS,
  state: 'changesOkNotConfirmed',
  infoMessage: 'Changes validated.Press F5 to save',
  embossedName: 'NEW NAME',
  cursorField: 'accountId',
  detailsEditable: false,
  confirmKeysVisible: true
};

describe('CardUpdateComponent', () => {
  let fixture: ComponentFixture<CardUpdateComponent>;
  let component: CardUpdateComponent;
  let httpMock: HttpTestingController;
  let router: Router;
  let queryParams: Record<string, string>;

  beforeEach(() => {
    queryParams = {};
  });

  async function setup(initial: CardUpdateScreen | null = FRESH_SCREEN): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [CardUpdateComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CardUpdateComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl');
    fixture.detectChanges();
    if (initial) {
      httpMock.expectOne((req) => req.method === 'GET' && req.url === ENDPOINT).flush(initial);
      fixture.detectChanges();
    }
  }

  afterEach(() => {
    httpMock.verify();
  });

  function el<T extends HTMLElement>(testId: string): T | null {
    return (fixture.nativeElement as HTMLElement).querySelector<T>(`[data-testid="${testId}"]`);
  }

  function input(testId: string): HTMLInputElement {
    return el<HTMLInputElement>(testId)!;
  }

  async function settle(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function fetchCard(): Promise<void> {
    component.accountId = ORIGINAL.accountId;
    component.cardNumber = ORIGINAL.cardNumber;
    component.submit();
    httpMock.expectOne((req) => req.method === 'POST').flush(SHOW_DETAILS);
    await settle();
  }

  function pressKey(key: string): KeyboardEvent {
    const event = new KeyboardEvent('keydown', { key, cancelable: true });
    window.dispatchEvent(event);
    fixture.detectChanges();
    return event;
  }

  it('renders the fresh screen with search keys editable and details protected (FR-S06-01)', async () => {
    await setup();

    expect(el('info-message')?.textContent?.trim()).toBe('Please enter Account and Card Number');
    expect(el('error-message')).toBeNull();
    expect(input('account-input').readOnly).toBeFalse();
    expect(input('card-input').readOnly).toBeFalse();
    expect(input('name-input').readOnly).toBeTrue();
    expect(input('status-input').readOnly).toBeTrue();
    expect(input('month-input').readOnly).toBeTrue();
    expect(input('year-input').readOnly).toBeTrue();
    expect(el('save-button')).toBeNull();
    expect(el('cancel-button')).toBeNull();
  });

  it('mirrors the BMS field lengths and keeps the day read-only (FR-S06-29)', async () => {
    await setup();

    expect(input('account-input').maxLength).toBe(11);
    expect(input('card-input').maxLength).toBe(16);
    expect(input('name-input').maxLength).toBe(50);
    expect(input('status-input').maxLength).toBe(1);
    expect(input('month-input').maxLength).toBe(2);
    expect(input('year-input').maxLength).toBe(4);
    expect(input('day-input').maxLength).toBe(2);
    expect(input('day-input').readOnly).toBeTrue();
  });

  it('sends ENTER with the typed keys and shows the returned error (FR-S06-04..09)', async () => {
    await setup();
    component.accountId = '';
    component.cardNumber = '0500024453765740';
    component.submit();

    const request = httpMock.expectOne((req) => req.method === 'POST' && req.url === ENDPOINT);
    expect(request.request.body).toEqual({
      aid: 'enter',
      state: 'notFetched',
      accountId: '',
      cardNumber: '0500024453765740',
      original: null,
      input: null
    });
    request.flush({
      ...FRESH_SCREEN,
      errorMessage: 'Account number not provided',
      accountId: '*',
      cardNumber: '0500024453765740',
      fieldsInError: ['accountId']
    });
    await settle();

    expect(el('error-message')?.textContent?.trim()).toBe('Account number not provided');
    expect(input('account-input').value).toBe('*');
    expect(input('account-input').classList).toContain('field-error');
    expect(input('card-input').classList).not.toContain('field-error');
  });

  it('shows the fetched card with details editable and search keys protected (FR-S06-10)', async () => {
    await setup();
    await fetchCard();

    expect(el('info-message')?.textContent?.trim()).toBe('Details of selected card shown above');
    expect(input('name-input').value).toBe('ANIYA VON');
    expect(input('status-input').value).toBe('Y');
    expect(input('month-input').value).toBe('03');
    expect(input('year-input').value).toBe('2023');
    expect(input('day-input').value).toBe('09');
    expect(input('account-input').readOnly).toBeTrue();
    expect(input('card-input').readOnly).toBeTrue();
    expect(input('name-input').readOnly).toBeFalse();
    expect(input('day-input').readOnly).toBeTrue();
  });

  it('round-trips the original image and typed values on the next ENTER (FR-S06-13..20)', async () => {
    await setup();
    await fetchCard();
    component.embossedName = 'NEW NAME';
    component.submit();

    const request = httpMock.expectOne((req) => req.method === 'POST');
    expect(request.request.body).toEqual({
      aid: 'enter',
      state: 'showDetails',
      accountId: ORIGINAL.accountId,
      cardNumber: ORIGINAL.cardNumber,
      original: ORIGINAL,
      input: { embossedName: 'NEW NAME', activeStatus: 'Y', expiryMonth: '03', expiryYear: '2023' }
    });
    request.flush(CONFIRM);
    await settle();

    expect(el('info-message')?.textContent?.trim()).toBe('Changes validated.Press F5 to save');
    expect(input('name-input').readOnly).toBeTrue();
    expect(el('save-button')).not.toBeNull();
    expect(el('cancel-button')).not.toBeNull();
  });

  it('flags every field the backend reports and keeps the edits (FR-S06-19)', async () => {
    await setup();
    await fetchCard();
    component.embossedName = '';
    component.activeStatus = 'Q';
    component.submit();

    httpMock.expectOne((req) => req.method === 'POST').flush({
      ...SHOW_DETAILS,
      state: 'changesNotOk',
      infoMessage: 'Update card details presented above.',
      errorMessage: 'Card name not provided',
      embossedName: '*',
      activeStatus: 'Q',
      fieldsInError: ['embossedName', 'activeStatus'],
      cursorField: 'embossedName'
    });
    await settle();

    expect(el('error-message')?.textContent?.trim()).toBe('Card name not provided');
    expect(input('name-input').value).toBe('*');
    expect(input('name-input').classList).toContain('field-error');
    expect(input('status-input').classList).toContain('field-error');
    expect(input('month-input').classList).not.toContain('field-error');
    expect(input('name-input').readOnly).toBeFalse();
  });

  it('sends PF5 from the Save button and shows the commit message (FR-S06-22)', async () => {
    await setup();
    await fetchCard();
    component.embossedName = 'NEW NAME';
    component.submit();
    httpMock.expectOne((req) => req.method === 'POST').flush(CONFIRM);
    await settle();

    el<HTMLButtonElement>('save-button')!.click();
    const request = httpMock.expectOne((req) => req.method === 'POST');
    expect(request.request.body.aid).toBe('pf5');
    expect(request.request.body.state).toBe('changesOkNotConfirmed');
    request.flush({
      ...CONFIRM,
      state: 'changesDone',
      infoMessage: 'Changes committed to database',
      confirmKeysVisible: false
    });
    await settle();

    expect(el('info-message')?.textContent?.trim()).toBe('Changes committed to database');
    expect(el('save-button')).toBeNull();
    expect(input('name-input').readOnly).toBeTrue();
    expect(input('account-input').readOnly).toBeTrue();
  });

  it('sends PF5 on the F5 key (FR-S06-22)', async () => {
    await setup();
    await fetchCard();

    const event = pressKey('F5');

    expect(event.defaultPrevented).toBeTrue();
    const request = httpMock.expectOne((req) => req.method === 'POST');
    expect(request.request.body.aid).toBe('pf5');
    request.flush(SHOW_DETAILS);
  });

  it('sends PF12 on the F12 key and restores the fetched values (FR-S06-27)', async () => {
    await setup();
    await fetchCard();
    component.embossedName = 'BAD1';

    pressKey('F12');

    const request = httpMock.expectOne((req) => req.method === 'POST');
    expect(request.request.body.aid).toBe('pf12');
    expect(request.request.body.input.embossedName).toBe('BAD1');
    request.flush({ ...SHOW_DETAILS, errorMessage: 'Card name can only contain alphabets and spaces' });
    await settle();

    expect(input('name-input').value).toBe('ANIYA VON');
    expect(el('error-message')?.textContent?.trim()).toBe('Card name can only contain alphabets and spaces');
  });

  it('shows the failure info and reopens the search keys after a failed save (FR-S06-24/25)', async () => {
    await setup();
    await fetchCard();
    component.embossedName = 'NEW NAME';
    component.submit();
    httpMock.expectOne((req) => req.method === 'POST').flush(CONFIRM);
    await settle();

    el<HTMLButtonElement>('save-button')!.click();
    httpMock.expectOne((req) => req.method === 'POST').flush({
      ...CONFIRM,
      state: 'changesFailed',
      infoMessage: 'Changes unsuccessful. Please try again',
      errorMessage: 'Could not lock record for update',
      confirmKeysVisible: false,
      searchEditable: true
    });
    await settle();

    expect(el('info-message')?.textContent?.trim()).toBe('Changes unsuccessful. Please try again');
    expect(el('error-message')?.textContent?.trim()).toBe('Could not lock record for update');
    expect(input('account-input').readOnly).toBeFalse();
    expect(input('name-input').readOnly).toBeTrue();
  });

  it('resets to the fresh screen when ENTER follows a completed update (FR-S06-26)', async () => {
    await setup();
    await fetchCard();
    component.submit();
    httpMock.expectOne((req) => req.method === 'POST').flush({
      ...CONFIRM,
      state: 'changesDone',
      infoMessage: 'Changes committed to database',
      confirmKeysVisible: false
    });
    await settle();

    component.submit();
    const request = httpMock.expectOne((req) => req.method === 'POST');
    expect(request.request.body.state).toBe('changesDone');
    request.flush(FRESH_SCREEN);
    await settle();

    expect(input('account-input').value).toBe('');
    expect(input('name-input').value).toBe('');
    expect(input('account-input').readOnly).toBeFalse();
    expect(el('info-message')?.textContent?.trim()).toBe('Please enter Account and Card Number');
  });

  it('navigates to the menu on Exit / F3 (FR-S06-03)', async () => {
    await setup();

    el<HTMLButtonElement>('exit-button')!.click();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/menu');

    const event = pressKey('F3');
    expect(event.defaultPrevented).toBeTrue();
    expect(router.navigateByUrl).toHaveBeenCalledTimes(2);
  });

  it('processes any other function key as ENTER, never as an invalid key (FR-S06-02)', async () => {
    await setup();
    component.accountId = ORIGINAL.accountId;
    component.cardNumber = ORIGINAL.cardNumber;

    const event = pressKey('F7');

    expect(event.defaultPrevented).toBeTrue();
    const request = httpMock.expectOne((req) => req.method === 'POST');
    expect(request.request.body.aid).toBe('enter');
    request.flush(SHOW_DETAILS);
    await settle();
    expect(el('error-message')).toBeNull();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('ignores non-function keys (FR-S06-02)', async () => {
    await setup();

    const event = pressKey('a');

    expect(event.defaultPrevented).toBeFalse();
    httpMock.expectNone((req) => req.method === 'POST');
  });

  it('fetches the card immediately when entered with account and card query params (FR-S06-28)', async () => {
    queryParams = { acctId: ORIGINAL.accountId, cardNum: ORIGINAL.cardNumber };
    await setup(null);

    const request = httpMock.expectOne((req) => req.method === 'POST' && req.url === ENDPOINT);
    expect(request.request.body).toEqual({
      aid: 'enter',
      state: 'notFetched',
      accountId: ORIGINAL.accountId,
      cardNumber: ORIGINAL.cardNumber,
      original: null,
      input: null
    });
    request.flush(SHOW_DETAILS);
    await settle();

    httpMock.expectNone((req) => req.method === 'GET');
    expect(input('name-input').value).toBe('ANIYA VON');
    expect(input('name-input').readOnly).toBeFalse();
  });
});
