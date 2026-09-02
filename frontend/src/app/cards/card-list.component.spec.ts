import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Router } from '@angular/router';
import { CardListComponent } from './card-list.component';
import { CardListPageState, CardListResponse, CardListRow } from './card-list.service';

const PAGE_SIZE = 7;

function key(i: number): string {
  return String(i).padStart(16, '0');
}

function acct(i: number): string {
  return String(i).padStart(11, '0');
}

function row(i: number, overrides: Partial<CardListRow> = {}): CardListRow {
  return {
    hasCard: true,
    accountId: acct(i),
    cardNumber: key(i),
    activeStatus: i % 2 === 0 ? 'N' : 'Y',
    selection: '',
    selectionError: false,
    selectionProtected: false,
    ...overrides
  };
}

function blankRow(): CardListRow {
  return { hasCard: false, accountId: '', cardNumber: '', activeStatus: '', selection: '', selectionError: false, selectionProtected: true };
}

function state(overrides: Partial<CardListPageState> = {}): CardListPageState {
  return {
    screenNumber: 1,
    firstCardNumber: key(1),
    lastCardNumber: key(8),
    nextPageExists: true,
    lastPageShown: false,
    rows: Array.from({ length: PAGE_SIZE }, (_, i) => ({ accountId: acct(i + 1), cardNumber: key(i + 1), activeStatus: 'Y' })),
    ...overrides
  };
}

function display(overrides: Partial<CardListResponse> = {}): CardListResponse {
  return {
    outcome: 'display',
    screenNumber: 1,
    accountFilter: '',
    cardFilter: '',
    accountFilterError: false,
    cardFilterError: false,
    cursorField: 'account',
    rows: Array.from({ length: PAGE_SIZE }, (_, i) => row(i + 1)),
    errorMessage: '',
    infoMessage: 'TYPE S FOR DETAIL, U TO UPDATE ANY RECORD',
    message: null,
    severity: null,
    state: state(),
    target: null,
    ...overrides
  };
}

describe('CardListComponent', () => {
  let fixture: ComponentFixture<CardListComponent>;
  let component: CardListComponent;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CardListComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()]
    }).compileComponents();

    fixture = TestBed.createComponent(CardListComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl');
    spyOn(router, 'navigate');
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function text(testId: string): string {
    return element().querySelector(`[data-testid="${testId}"]`)?.textContent?.trim() ?? '';
  }

  function input(testId: string): HTMLInputElement {
    return element().querySelector(`[data-testid="${testId}"]`) as HTMLInputElement;
  }

  function expectList(): TestRequest {
    return httpMock.expectOne('/api/v1/cards/list');
  }

  function openPageOne(): void {
    expectList().flush(display());
    fixture.detectChanges();
  }

  /** ngModel applies value/disabled bindings asynchronously. */
  async function settle(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('requests a fresh page 1 on entry with no state and blank filters (FR-S04-01)', () => {
    const request = expectList();

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      aid: 'ENTER',
      state: null,
      accountFilter: '',
      cardFilter: '',
      selections: Array(PAGE_SIZE).fill(null)
    });

    request.flush(display());
    fixture.detectChanges();

    expect(text('page-number')).toBe('Page 1');
    expect(text('account-1')).toBe(acct(1));
    expect(text('card-1')).toBe(key(1));
    expect(text('active-1')).toBe('Y');
    expect(text('active-2')).toBe('N');
    expect(text('card-7')).toBe(key(7));
    expect(text('info-message')).toBe('TYPE S FOR DETAIL, U TO UPDATE ANY RECORD');
    expect(text('error-message')).toBe('');
    expect(input('account-filter').value).toBe('');
    expect(input('card-filter').value).toBe('');
  });

  it('lays the screen out as CCRDLIA: 11/16-wide filters, 7 rows with 1-char Select, footer (FR-S04-02)', async () => {
    openPageOne();
    await settle();

    expect(text('tran')).toBe('Tran: CCLI');
    expect(text('prog')).toBe('Prog: COCRDLIC');
    expect(element().querySelector('mat-card-title')?.textContent?.trim()).toBe('List Credit Cards');
    expect(input('account-filter').maxLength).toBe(11);
    expect(input('card-filter').maxLength).toBe(16);
    expect(element().querySelectorAll('[data-testid="card-row"]').length).toBe(7);
    for (let i = 1; i <= 7; i++) {
      expect(input(`select-${i}`).maxLength).toBe(1);
      expect(input(`select-${i}`).disabled).toBeFalse();
    }
    expect(text('footer')).toBe('F3=Exit F7=Backward  F8=Forward');
    expect(element().querySelector('[data-testid="error-message"]')?.getAttribute('role')).toBe('alert');
  });

  it('sends ENTER with the echoed state, filters and selections (FR-S04-01/05)', async () => {
    openPageOne();
    await settle();
    input('account-filter').value = acct(3);
    input('account-filter').dispatchEvent(new Event('input'));
    input('select-2').value = 'S';
    input('select-2').dispatchEvent(new Event('input'));
    fixture.detectChanges();

    element().querySelector('form')!.dispatchEvent(new Event('submit'));

    const request = expectList();
    expect(request.request.body).toEqual({
      aid: 'ENTER',
      state: state(),
      accountFilter: acct(3),
      cardFilter: '',
      selections: [null, 'S', null, null, null, null, null]
    });
    request.flush(display({ accountFilter: acct(3), rows: [row(3), ...Array.from({ length: 6 }, blankRow)], errorMessage: 'NO MORE RECORDS TO SHOW' }));
    await settle();

    expect(text('card-1')).toBe(key(3));
    expect(text('card-2')).toBe('');
    expect(input('select-1').disabled).toBeFalse();
    expect(input('select-2').disabled).toBeTrue();
    expect(text('error-message')).toBe('NO MORE RECORDS TO SHOW');
    expect(text('info-message')).toBe('TYPE S FOR DETAIL, U TO UPDATE ANY RECORD');
  });

  it('shows the account filter error, echoes the value, keeps rows and protects Select (FR-S04-03/20/21)', fakeAsync(() => {
    openPageOne();
    component.accountFilter = '123';
    component.submit();

    expectList().flush(
      display({
        accountFilter: '123',
        accountFilterError: true,
        cursorField: 'account',
        errorMessage: 'ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER',
        infoMessage: '',
        rows: Array.from({ length: PAGE_SIZE }, (_, i) => row(i + 1, { selectionProtected: true }))
      })
    );
    fixture.detectChanges();
    tick();

    expect(text('error-message')).toBe('ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER');
    expect(text('info-message')).toBe('');
    expect(input('account-filter').value).toBe('123');
    expect(input('account-filter').classList).toContain('field-error');
    expect(text('card-1')).toBe(key(1));
    for (let i = 1; i <= 7; i++) {
      expect(input(`select-${i}`).disabled).toBeTrue();
    }
    expect(document.activeElement).toBe(input('account-filter'));
  }));

  it('shows the card filter error with the cursor on the card filter (FR-S04-04/20)', fakeAsync(() => {
    openPageOne();
    component.cardFilter = 'ABC';
    component.submit();

    expectList().flush(
      display({
        cardFilter: 'ABC',
        cardFilterError: true,
        cursorField: 'card',
        errorMessage: 'CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER',
        infoMessage: ''
      })
    );
    fixture.detectChanges();
    tick();

    expect(text('error-message')).toBe('CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER');
    expect(input('card-filter').value).toBe('ABC');
    expect(input('card-filter').classList).toContain('field-error');
    expect(document.activeElement).toBe(input('card-filter'));
  }));

  it('shows no-records-found with blank rows and no info message (FR-S04-06/19)', async () => {
    openPageOne();
    component.submit();

    expectList().flush(
      display({
        rows: Array.from({ length: PAGE_SIZE }, blankRow),
        errorMessage: 'NO RECORDS FOUND FOR THIS SEARCH CONDITION.',
        infoMessage: ''
      })
    );
    await settle();

    expect(text('error-message')).toBe('NO RECORDS FOUND FOR THIS SEARCH CONDITION.');
    expect(text('info-message')).toBe('');
    expect(element().querySelectorAll('[data-testid="card-row"]').length).toBe(7);
    for (let i = 1; i <= 7; i++) {
      expect(text(`card-${i}`)).toBe('');
      expect(input(`select-${i}`).disabled).toBeTrue();
    }
  });

  it('pages forward with F8 / Forward button and shows page 2 (FR-S04-07)', () => {
    openPageOne();

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'F8' }));

    const request = expectList();
    expect(request.request.body.aid).toBe('PF8');
    expect(request.request.body.state).toEqual(state());
    request.flush(
      display({
        screenNumber: 2,
        rows: Array.from({ length: PAGE_SIZE }, (_, i) => row(i + 8)),
        state: state({ screenNumber: 2, firstCardNumber: key(8), lastCardNumber: key(15) })
      })
    );
    fixture.detectChanges();

    expect(text('page-number')).toBe('Page 2');
    expect(text('card-1')).toBe(key(8));

    element().querySelector<HTMLButtonElement>('[data-testid="forward-button"]')!.click();
    const next = expectList();
    expect(next.request.body.aid).toBe('PF8');
    expect(next.request.body.state.screenNumber).toBe(2);
    next.flush(display({ screenNumber: 3 }));
  });

  it('shows NO MORE RECORDS TO SHOW then NO MORE PAGES TO DISPLAY on repeated F8 (FR-S04-08/09)', async () => {
    openPageOne();

    component.forward();
    expectList().flush(
      display({
        screenNumber: 2,
        rows: [row(8), row(9), row(10), blankRow(), blankRow(), blankRow(), blankRow()],
        errorMessage: 'NO MORE RECORDS TO SHOW',
        state: state({ screenNumber: 2, nextPageExists: false, lastPageShown: true })
      })
    );
    await settle();
    expect(text('error-message')).toBe('NO MORE RECORDS TO SHOW');
    expect(text('info-message')).toBe('TYPE S FOR DETAIL, U TO UPDATE ANY RECORD');
    expect(input('select-4').disabled).toBeTrue();

    component.forward();
    const again = expectList();
    expect(again.request.body.state.lastPageShown).toBeTrue();
    again.flush(
      display({
        screenNumber: 2,
        rows: [row(8), row(9), row(10), blankRow(), blankRow(), blankRow(), blankRow()],
        errorMessage: 'NO MORE PAGES TO DISPLAY',
        infoMessage: '',
        state: state({ screenNumber: 2, nextPageExists: false, lastPageShown: true })
      })
    );
    fixture.detectChanges();
    expect(text('error-message')).toBe('NO MORE PAGES TO DISPLAY');
    expect(text('info-message')).toBe('');
  });

  it('pages backward with F7 / Backward button and shows NO PREVIOUS PAGES on page 1 (FR-S04-10/11)', () => {
    openPageOne();

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'F7' }));

    const request = expectList();
    expect(request.request.body.aid).toBe('PF7');
    request.flush(display({ errorMessage: 'NO PREVIOUS PAGES TO DISPLAY', infoMessage: '' }));
    fixture.detectChanges();

    expect(text('page-number')).toBe('Page 1');
    expect(text('error-message')).toBe('NO PREVIOUS PAGES TO DISPLAY');
    expect(text('info-message')).toBe('');

    element().querySelector<HTMLButtonElement>('[data-testid="backward-button"]')!.click();
    expect(expectList().request.body.aid).toBe('PF7');
  });

  it('marks the offending Select rows and places the cursor for selection errors (FR-S04-12/13)', fakeAsync(() => {
    openPageOne();
    component.selections[1] = 'X';
    component.submit();

    expectList().flush(
      display({
        cursorField: 'select2',
        errorMessage: 'INVALID ACTION CODE',
        rows: Array.from({ length: PAGE_SIZE }, (_, i) => row(i + 1, i === 1 ? { selection: 'X', selectionError: true } : {}))
      })
    );
    fixture.detectChanges();
    tick();

    expect(text('error-message')).toBe('INVALID ACTION CODE');
    expect(input('select-2').value).toBe('X');
    expect(input('select-2').classList).toContain('field-error');
    expect(input('select-1').classList).not.toContain('field-error');
    expect(document.activeElement).toBe(input('select-2'));

    component.selections[0] = 'S';
    component.selections[2] = 'U';
    component.submit();
    expectList().flush(
      display({
        errorMessage: 'PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE',
        rows: Array.from({ length: PAGE_SIZE }, (_, i) =>
          row(i + 1, i === 0 ? { selection: 'S', selectionError: true } : i === 2 ? { selection: 'U', selectionError: true } : {})
        )
      })
    );
    fixture.detectChanges();
    tick();

    expect(text('error-message')).toBe('PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE');
    expect(input('select-1').classList).toContain('field-error');
    expect(input('select-3').classList).toContain('field-error');
    expect(input('select-2').classList).not.toContain('field-error');
  }));

  it('shows the coming-soon message when the detail target is still disabled (FR-S04-15, S04-B1)', async () => {
    openPageOne();
    component.selections[3] = 'S';
    component.submit();

    expectList().flush(
      display({
        outcome: 'comingSoon',
        message: 'This option Credit Card View is coming soon ...',
        severity: 'info',
        infoMessage: '',
        rows: Array.from({ length: PAGE_SIZE }, (_, i) => row(i + 1, i === 3 ? { selection: 'S' } : {})),
        target: { programKey: 'COCRDSLC', route: '', accountId: acct(4), cardNumber: key(4) }
      })
    );
    await settle();

    expect(text('error-message')).toBe('This option Credit Card View is coming soon ...');
    expect(input('select-4').value).toBe('S');
    expect(text('card-4')).toBe(key(4));
    expect(router.navigate).not.toHaveBeenCalled();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('navigates to the update target with the selected card once it is enabled (FR-S04-16)', () => {
    openPageOne();
    component.selections[0] = 'U';
    component.submit();

    expectList().flush(
      display({
        outcome: 'navigate',
        target: { programKey: 'COCRDUPC', route: '/cards/update', accountId: acct(1), cardNumber: key(1) }
      })
    );

    expect(router.navigate).toHaveBeenCalledWith(['/cards/update'], {
      queryParams: { accountId: acct(1), cardNumber: key(1) }
    });
  });

  it('exits to the main menu on F3 / Exit button (FR-S04-17)', () => {
    openPageOne();

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'F3' }));

    const request = expectList();
    expect(request.request.body.aid).toBe('PF3');
    request.flush(
      display({ outcome: 'exit', target: { programKey: 'COMEN01C', route: '/menu', accountId: '', cardNumber: '' } })
    );

    expect(router.navigateByUrl).toHaveBeenCalledWith('/menu');

    element().querySelector<HTMLButtonElement>('[data-testid="exit-button"]')!.click();
    const viaButton = expectList();
    expect(viaButton.request.body.aid).toBe('PF3');
    viaButton.flush(display({ outcome: 'exit', target: null }));
    expect(router.navigateByUrl).toHaveBeenCalledTimes(2);
  });

  it('passes any other function key through as its PF number for the service to treat as ENTER (FR-S04-18)', () => {
    openPageOne();

    const event = new KeyboardEvent('keydown', { key: 'F5', cancelable: true });
    window.dispatchEvent(event);

    expect(event.defaultPrevented).toBeTrue();
    const request = expectList();
    expect(request.request.body.aid).toBe('PF5');
    request.flush(display());
    fixture.detectChanges();
    expect(text('error-message')).toBe('');
    expect(text('card-1')).toBe(key(1));
  });

  it('ignores non-function keys', () => {
    openPageOne();

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'a' }));

    httpMock.expectNone('/api/v1/cards/list');
  });

  it('does not send a second request while one is in flight', () => {
    openPageOne();

    component.forward();
    component.forward();

    expectList().flush(display({ screenNumber: 2 }));
    fixture.detectChanges();
    expect(text('page-number')).toBe('Page 2');
  });
});
