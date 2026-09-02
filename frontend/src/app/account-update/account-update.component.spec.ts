import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Router } from '@angular/router';
import { AccountUpdateComponent, MSG_PROMPT_FOR_SEARCH_KEYS } from './account-update.component';
import { AccountUpdateFields, emptyAccountUpdateFields } from './account-update.service';
import { MSG_INVALID_KEY } from '../shared/invalid-key';

const FETCHED: AccountUpdateFields = {
  ...emptyAccountUpdateFields(),
  accountId: '00000000010',
  activeStatus: 'Y',
  openYear: '2019',
  openMonth: '03',
  openDay: '15',
  creditLimit: '+     10,000.00',
  expiryYear: '2027',
  expiryMonth: '12',
  expiryDay: '31',
  cashCreditLimit: '+      2,000.00',
  reissueYear: '2024',
  reissueMonth: '12',
  reissueDay: '31',
  currentBalance: '+      1,250.75',
  currentCycleCredit: '+        300.00',
  groupId: 'ZEROAPR',
  currentCycleDebit: '+        125.50',
  customerId: '000000010',
  ssn1: '123',
  ssn2: '45',
  ssn3: '6789',
  dobYear: '1980',
  dobMonth: '06',
  dobDay: '15',
  ficoScore: '720',
  firstName: 'JOHN',
  middleName: 'Q',
  lastName: 'PUBLIC',
  addressLine1: '1 MAIN ST',
  addressLine2: 'APT 2',
  state: 'NY',
  zip: '10001',
  city: 'NEW YORK',
  country: 'USA',
  phone1Area: '212',
  phone1Prefix: '555',
  phone1Line: '1234',
  phone2Area: '718',
  phone2Prefix: '555',
  phone2Line: '9876',
  governmentId: 'DL123456',
  eftAccountId: '1234567890',
  primaryCardHolder: 'Y'
};

const MSG_PROMPT_FOR_CHANGES = 'Update account details presented above.';

describe('AccountUpdateComponent', () => {
  let fixture: ComponentFixture<AccountUpdateComponent>;
  let component: AccountUpdateComponent;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AccountUpdateComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()]
    }).compileComponents();

    fixture = TestBed.createComponent(AccountUpdateComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl');
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function input(testId: string): HTMLInputElement {
    return element().querySelector<HTMLInputElement>(`[data-testid="${testId}"]`)!;
  }

  function infoText(): string | undefined {
    return element().querySelector('[data-testid="info-message"]')?.textContent?.trim();
  }

  function errorText(): string | undefined {
    return element().querySelector('[data-testid="error-message"]')?.textContent?.trim();
  }

  async function settle(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function pressKey(key: string): Promise<KeyboardEvent> {
    const event = new KeyboardEvent('keydown', { key, cancelable: true });
    window.dispatchEvent(event);
    await settle();
    return event;
  }

  async function fetchDetails(): Promise<void> {
    component.fields.accountId = '00000000010';
    component.submit();
    httpMock.expectOne('/api/v1/account-update/lookup').flush({
      outcome: 'details',
      infoMessage: MSG_PROMPT_FOR_CHANGES,
      errorMessage: null,
      fields: FETCHED
    });
    await settle();
  }

  async function validateWith(body: object): Promise<void> {
    component.submit();
    httpMock.expectOne('/api/v1/account-update/validate').flush(body);
    await settle();
  }

  async function confirmChanges(): Promise<void> {
    component.fields.lastName = 'CITIZEN';
    await validateWith({ outcome: 'confirm', infoMessage: 'Changes validated.Press F5 to save', errorMessage: null, invalidFields: [] });
  }

  it('opens in the search state with the prompt and only the account id editable (FR-S03-01)', async () => {
    expect(component.state).toBe('search');
    expect(infoText()).toBe(MSG_PROMPT_FOR_SEARCH_KEYS);
    expect(errorText()).toBeUndefined();
    expect(input('accountId').readOnly).toBeFalse();
    expect(input('activeStatus').readOnly).toBeTrue();
    expect(input('lastName').readOnly).toBeTrue();
    expect(input('save-button').disabled).toBeTrue();
    expect(input('cancel-button').disabled).toBeTrue();
  });

  it('renders the CACTUPA field lengths (app/bms/COACTUP.bms)', async () => {
    const lengths: Record<string, number> = {
      accountId: 11,
      activeStatus: 1,
      openYear: 4,
      openMonth: 2,
      openDay: 2,
      creditLimit: 15,
      groupId: 10,
      customerId: 9,
      ssn1: 3,
      ssn2: 2,
      ssn3: 4,
      ficoScore: 3,
      firstName: 25,
      addressLine1: 50,
      state: 2,
      zip: 5,
      country: 3,
      phone1Area: 3,
      phone1Prefix: 3,
      phone1Line: 4,
      governmentId: 20,
      eftAccountId: 10,
      primaryCardHolder: 1
    };
    for (const [key, length] of Object.entries(lengths)) {
      expect(input(key).maxLength).withContext(key).toBe(length);
    }
    expect(element().querySelectorAll('input.screen-input').length).toBe(43);
  });

  it('posts the typed id on ENTER and shows the search error while staying in search (FR-S03-02/03)', async () => {
    component.fields.accountId = '12AB';
    component.submit();

    const request = httpMock.expectOne('/api/v1/account-update/lookup');
    expect(request.request.body).toEqual({ accountId: '12AB' });
    request.flush({
      outcome: 'searchError',
      infoMessage: MSG_PROMPT_FOR_SEARCH_KEYS,
      errorMessage: 'Account Number if supplied must be a 11 digit Non-Zero Number',
      fields: null
    });
    await settle();

    expect(component.state).toBe('search');
    expect(errorText()).toBe('Account Number if supplied must be a 11 digit Non-Zero Number');
    expect(infoText()).toBe(MSG_PROMPT_FOR_SEARCH_KEYS);
    expect(input('accountId').value).toBe('12AB');
    expect(input('accountId').readOnly).toBeFalse();
  });

  it('shows the fetched details with the map protections once found (FR-S03-07)', async () => {
    await fetchDetails();

    expect(component.state).toBe('details');
    expect(infoText()).toBe(MSG_PROMPT_FOR_CHANGES);
    expect(errorText()).toBeUndefined();
    expect(input('creditLimit').value).toBe('+     10,000.00');
    expect(input('ssn3').value).toBe('6789');
    expect(input('accountId').readOnly).toBeTrue();
    expect(input('customerId').readOnly).toBeTrue();
    expect(input('country').readOnly).toBeTrue();
    expect(input('activeStatus').readOnly).toBeFalse();
    expect(input('lastName').readOnly).toBeFalse();
    expect(input('save-button').disabled).toBeTrue();
    expect(input('cancel-button').disabled).toBeFalse();
  });

  it('sends original and updated values to validate and restores originals on no change (FR-S03-08)', async () => {
    await fetchDetails();
    component.fields.lastName = 'public';
    component.submit();

    const request = httpMock.expectOne('/api/v1/account-update/validate');
    expect(request.request.body).toEqual({ original: FETCHED, updated: { ...FETCHED, lastName: 'public' } });
    request.flush({
      outcome: 'noChanges',
      infoMessage: MSG_PROMPT_FOR_CHANGES,
      errorMessage: 'No change detected with respect to values fetched.',
      invalidFields: []
    });
    await settle();

    expect(component.state).toBe('details');
    expect(errorText()).toBe('No change detected with respect to values fetched.');
    expect(input('lastName').value).toBe('PUBLIC');
  });

  it('shows the first edit message, highlights every invalid field and stars blank ones (FR-S03-24)', async () => {
    await fetchDetails();
    component.fields.activeStatus = '';
    component.fields.openMonth = '13';
    component.fields.ficoScore = '100';
    await validateWith({
      outcome: 'invalid',
      infoMessage: MSG_PROMPT_FOR_CHANGES,
      errorMessage: 'Account Status must be supplied.',
      invalidFields: ['activeStatus', 'openDate', 'ficoScore']
    });

    expect(component.state).toBe('editError');
    expect(errorText()).toBe('Account Status must be supplied.');
    expect(input('activeStatus').value).toBe('*');
    expect(input('activeStatus').getAttribute('aria-invalid')).toBe('true');
    expect(input('openMonth').value).toBe('13');
    expect(input('openYear').getAttribute('aria-invalid')).toBe('true');
    expect(input('ficoScore').getAttribute('aria-invalid')).toBe('true');
    expect(input('lastName').getAttribute('aria-invalid')).toBe('false');
    expect(input('activeStatus').readOnly).toBeFalse();
    expect(input('save-button').disabled).toBeTrue();
  });

  it('enters the confirm state with everything protected and F5 lit; ENTER there does nothing (FR-S03-23)', async () => {
    await fetchDetails();
    await confirmChanges();

    expect(component.state).toBe('confirm');
    expect(infoText()).toBe('Changes validated.Press F5 to save');
    expect(errorText()).toBeUndefined();
    expect(input('lastName').value).toBe('CITIZEN');
    expect(input('lastName').readOnly).toBeTrue();
    expect(input('accountId').readOnly).toBeTrue();
    expect(input('save-button').disabled).toBeFalse();

    component.submit();
    httpMock.expectNone('/api/v1/account-update/validate');
    expect(component.state).toBe('confirm');
  });

  it('saves on F5 from confirm and shows the committed message (FR-S03-25)', async () => {
    await fetchDetails();
    await confirmChanges();

    const event = await pressKey('F5');
    const request = httpMock.expectOne('/api/v1/account-update/save');
    expect(event.defaultPrevented).toBeTrue();
    expect(request.request.body).toEqual({ original: FETCHED, updated: { ...FETCHED, lastName: 'CITIZEN' } });
    request.flush({ outcome: 'committed', infoMessage: 'Changes committed to database', errorMessage: null, invalidFields: [] });
    await settle();

    expect(component.state).toBe('done');
    expect(infoText()).toBe('Changes committed to database');
    expect(input('lastName').readOnly).toBeTrue();
    expect(input('accountId').readOnly).toBeTrue();
    expect(input('save-button').disabled).toBeTrue();
  });

  it('shows the lock/update failure with the account id editable again (FR-S03-26/27/29)', async () => {
    await fetchDetails();
    await confirmChanges();
    component.save();
    httpMock.expectOne('/api/v1/account-update/save').flush({
      outcome: 'failed',
      infoMessage: 'Changes unsuccessful. Please try again',
      errorMessage: 'Could not lock account record for update',
      invalidFields: []
    });
    await settle();

    expect(component.state).toBe('failed');
    expect(errorText()).toBe('Could not lock account record for update');
    expect(infoText()).toBe('Changes unsuccessful. Please try again');
    expect(input('lastName').value).toBe('CITIZEN');
    expect(input('lastName').readOnly).toBeTrue();
    expect(input('accountId').readOnly).toBeFalse();
  });

  it('falls back to the fetched originals when someone else changed the record (FR-S03-28)', async () => {
    await fetchDetails();
    await confirmChanges();
    component.save();
    httpMock.expectOne('/api/v1/account-update/save').flush({
      outcome: 'changedByOther',
      infoMessage: MSG_PROMPT_FOR_CHANGES,
      errorMessage: 'Record changed by some one else. Please review',
      invalidFields: []
    });
    await settle();

    expect(component.state).toBe('details');
    expect(errorText()).toBe('Record changed by some one else. Please review');
    expect(input('lastName').value).toBe('PUBLIC');
    expect(input('lastName').readOnly).toBeFalse();
  });

  it('re-reads the account on ENTER after a commit (FR-S03-30)', async () => {
    await fetchDetails();
    await confirmChanges();
    component.save();
    httpMock.expectOne('/api/v1/account-update/save').flush({
      outcome: 'committed',
      infoMessage: 'Changes committed to database',
      errorMessage: null,
      invalidFields: []
    });
    await settle();

    component.submit();
    const request = httpMock.expectOne('/api/v1/account-update/lookup');
    expect(request.request.body).toEqual({ accountId: '00000000010' });
    request.flush({ outcome: 'details', infoMessage: MSG_PROMPT_FOR_CHANGES, errorMessage: null, fields: { ...FETCHED, lastName: 'CITIZEN' } });
    await settle();

    expect(component.state).toBe('details');
    expect(input('lastName').value).toBe('CITIZEN');
    expect(input('lastName').readOnly).toBeFalse();
  });

  it('F12 re-reads the account, discards edits and clears the messages (FR-S03-31)', async () => {
    await fetchDetails();
    component.fields.firstName = 'JANE';
    component.fields.ficoScore = '';
    await validateWith({
      outcome: 'invalid',
      infoMessage: MSG_PROMPT_FOR_CHANGES,
      errorMessage: 'FICO Score must be supplied.',
      invalidFields: ['ficoScore']
    });

    const event = await pressKey('F12');
    const request = httpMock.expectOne('/api/v1/account-update/lookup');
    expect(event.defaultPrevented).toBeTrue();
    expect(request.request.body).toEqual({ accountId: '00000000010' });
    request.flush({ outcome: 'details', infoMessage: MSG_PROMPT_FOR_CHANGES, errorMessage: null, fields: FETCHED });
    await settle();

    expect(component.state).toBe('details');
    expect(input('firstName').value).toBe('JOHN');
    expect(input('ficoScore').value).toBe('720');
    expect(input('ficoScore').getAttribute('aria-invalid')).toBe('false');
    expect(errorText()).toBeUndefined();
  });

  it('F3 returns to the menu from any state without writing (FR-S03-32)', async () => {
    const event = await pressKey('F3');
    expect(event.defaultPrevented).toBeTrue();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/menu');

    await fetchDetails();
    await confirmChanges();
    element().querySelector<HTMLButtonElement>('[data-testid="exit-button"]')!.click();
    expect(router.navigateByUrl).toHaveBeenCalledTimes(2);
    httpMock.expectNone('/api/v1/account-update/save');
  });

  it('treats other function keys, F5 outside confirm and F12 before fetch as invalid AIDs (FR-S03-33)', async () => {
    let event = await pressKey('F7');
    expect(event.defaultPrevented).toBeTrue();
    expect(errorText()).toBe(MSG_INVALID_KEY);
    expect(component.state).toBe('search');

    component.errorMessage = '';
    await pressKey('F12');
    expect(errorText()).toBe(MSG_INVALID_KEY);
    httpMock.expectNone('/api/v1/account-update/lookup');

    await fetchDetails();
    event = await pressKey('F5');
    expect(errorText()).toBe(MSG_INVALID_KEY);
    expect(component.state).toBe('details');
    httpMock.expectNone('/api/v1/account-update/save');

    const plain = await pressKey('a');
    expect(plain.defaultPrevented).toBeFalse();
  });
});
