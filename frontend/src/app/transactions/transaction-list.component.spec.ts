import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Router } from '@angular/router';
import { routes } from '../app.routes';
import { authGuard } from '../auth/auth.guard';
import { MSG_INVALID_KEY } from '../shared/invalid-key';
import { TransactionListComponent } from './transaction-list.component';
import { TransactionListResponse, TransactionListRow } from './transaction-list.service';

const LIST_URL = '/api/v1/transactions/list';

function id(n: number): string {
  return String(n).padStart(16, '0');
}

function row(n: number): TransactionListRow {
  return { tranId: id(n), date: '07/19/22', description: `Purchase ${n}`, amount: '+00000012.34' };
}

function page(from: number, count = 10): TransactionListRow[] {
  const rows = Array.from({ length: count }, (_, i) => row(from + i));
  while (rows.length < 10) {
    rows.push({ tranId: '', date: '', description: '', amount: '' });
  }
  return rows;
}

const PAGE_1: TransactionListResponse = {
  outcome: 'redisplay',
  message: null,
  severity: null,
  rows: page(1),
  clearSearchInput: true,
  state: { firstTranId: id(1), lastTranId: id(10), pageNumber: 1, nextPageAvailable: true },
  selectedTranId: null,
  target: null
};

describe('TransactionListComponent', () => {
  let fixture: ComponentFixture<TransactionListComponent>;
  let component: TransactionListComponent;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TransactionListComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()]
    }).compileComponents();

    fixture = TestBed.createComponent(TransactionListComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl');
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  function host(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function flushInitialPage(): void {
    const initial = httpMock.expectOne(LIST_URL);
    expect(initial.request.body).toEqual({
      action: 'enter',
      searchTranId: '',
      selectionFlag: '',
      selectedTranId: '',
      state: { firstTranId: '', lastTranId: '', pageNumber: 0, nextPageAvailable: false }
    });
    initial.flush(PAGE_1);
    fixture.detectChanges();
  }

  function messageText(): string | undefined {
    return host().querySelector('[data-testid="tran-list-message"]')?.textContent?.trim();
  }

  function rowIds(): string[] {
    return Array.from(host().querySelectorAll('[data-testid="row-tran-id"]')).map((el) => el.textContent?.trim() ?? '');
  }

  function pressKey(key: string): KeyboardEvent {
    const event = new KeyboardEvent('keydown', { key, cancelable: true });
    window.dispatchEvent(event);
    fixture.detectChanges();
    return event;
  }

  it('is routed at /transactions/list behind authGuard (FR-S07-01)', () => {
    flushInitialPage();
    const route = routes.find((r) => r.path === 'transactions/list');
    expect(route?.component).toBe(TransactionListComponent);
    expect(route?.canActivate).toEqual([authGuard]);
  });

  it('loads page 1 from the start of the file on first display (FR-S07-02, FR-S07-07)', () => {
    flushInitialPage();

    expect(host().querySelectorAll('[data-testid="tran-row"]').length).toBe(10);
    expect(rowIds()).toEqual(Array.from({ length: 10 }, (_, i) => id(i + 1)));
    expect(host().querySelector('[data-testid="page-number"]')?.textContent?.trim()).toBe('Page: 00000001');
    expect(messageText()).toBeUndefined();
  });

  it('mirrors the COTRN0A field lengths and labels (FR-S07-05, FR-S07-06)', () => {
    flushInitialPage();

    const search = host().querySelector<HTMLInputElement>('[data-testid="search-input"]');
    expect(search?.getAttribute('maxlength')).toBe('16');
    const sels = host().querySelectorAll<HTMLInputElement>('[data-testid="sel-input"]');
    expect(sels.length).toBe(10);
    sels.forEach((sel) => expect(sel.getAttribute('maxlength')).toBe('1'));
    expect(host().querySelector('mat-card-title')?.textContent?.trim()).toBe('List Transactions');
    expect(host().textContent).toContain("Type 'S' to View Transaction details from the list");
    const first = host().querySelector('[data-testid="tran-row"]')!;
    expect(first.querySelector('[data-testid="row-date"]')?.textContent?.trim()).toBe('07/19/22');
    expect(first.querySelector('[data-testid="row-amount"]')?.textContent?.trim()).toBe('+00000012.34');
    expect(first.querySelector('[data-testid="row-description"]')?.textContent?.trim()).toBe('Purchase 1');
  });

  it('sends the search id with the paging state on ENTER and clears it on a new page (FR-S07-04)', () => {
    flushInitialPage();
    component.searchTranId = id(15);
    component.submit();

    const req = httpMock.expectOne(LIST_URL);
    expect(req.request.body.action).toBe('enter');
    expect(req.request.body.searchTranId).toBe(id(15));
    expect(req.request.body.state).toEqual(PAGE_1.state);
    req.flush({
      ...PAGE_1,
      rows: page(15),
      state: { firstTranId: id(15), lastTranId: id(24), pageNumber: 1, nextPageAvailable: true }
    });
    fixture.detectChanges();

    expect(rowIds()[0]).toBe(id(15));
    expect(component.searchTranId).toBe('');
  });

  it('shows the numeric error and keeps rows, page and input (FR-S07-05)', () => {
    flushInitialPage();
    component.searchTranId = '12A';
    component.submit();

    httpMock.expectOne(LIST_URL).flush({
      ...PAGE_1,
      message: 'Tran ID must be Numeric ...',
      severity: 'error',
      rows: null,
      clearSearchInput: false
    });
    fixture.detectChanges();

    expect(messageText()).toBe('Tran ID must be Numeric ...');
    expect(host().querySelector('[data-testid="tran-list-message"]')?.classList).toContain('error-message');
    expect(rowIds()[0]).toBe(id(1));
    expect(component.searchTranId).toBe('12A');
    expect(host().querySelector('[data-testid="page-number"]')?.textContent?.trim()).toBe('Page: 00000001');
  });

  it('sends the first non-blank selection with its row id (FR-S07-15) and shows coming-soon while COTRN01C is disabled (S07-B1)', () => {
    flushInitialPage();
    component.rows[2].sel = 'S';
    component.rows[5].sel = 'X';
    component.submit();

    const req = httpMock.expectOne(LIST_URL);
    expect(req.request.body.selectionFlag).toBe('S');
    expect(req.request.body.selectedTranId).toBe(id(3));
    req.flush({
      ...PAGE_1,
      outcome: 'comingSoon',
      message: 'This option Transaction View is coming soon ...',
      severity: 'info',
      rows: null,
      clearSearchInput: false,
      selectedTranId: id(3)
    });
    fixture.detectChanges();

    expect(messageText()).toBe('This option Transaction View is coming soon ...');
    expect(host().querySelector('[data-testid="tran-list-message"]')?.classList).toContain('info-message');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('navigates to the registry route with the selected id once COTRN01C is enabled (FR-S07-15)', () => {
    flushInitialPage();
    component.rows[0].sel = 's';
    component.submit();

    httpMock.expectOne(LIST_URL).flush({
      ...PAGE_1,
      outcome: 'navigate',
      rows: null,
      clearSearchInput: false,
      selectedTranId: id(1),
      target: { id: '07', name: 'Transaction View', programKey: 'COTRN01C', route: '/transactions/view' }
    });

    expect(router.navigateByUrl).toHaveBeenCalledWith(`/transactions/view?tranId=${id(1)}`);
  });

  it('shows the invalid-selection message and redisplays the page (FR-S07-16)', () => {
    flushInitialPage();
    component.rows[1].sel = 'X';
    component.submit();

    httpMock.expectOne(LIST_URL).flush({
      ...PAGE_1,
      message: 'Invalid selection. Valid value is S',
      severity: 'error'
    });
    fixture.detectChanges();

    expect(messageText()).toBe('Invalid selection. Valid value is S');
    expect(component.rows[1].sel).toBe('X');
  });

  it('pages forward on F8 and renders the next page (FR-S07-10)', () => {
    flushInitialPage();
    const event = pressKey('F8');

    expect(event.defaultPrevented).toBeTrue();
    const req = httpMock.expectOne(LIST_URL);
    expect(req.request.body.action).toBe('pageForward');
    expect(req.request.body.state).toEqual(PAGE_1.state);
    req.flush({
      ...PAGE_1,
      rows: page(11),
      state: { firstTranId: id(11), lastTranId: id(20), pageNumber: 2, nextPageAvailable: true }
    });
    fixture.detectChanges();

    expect(rowIds()[0]).toBe(id(11));
    expect(host().querySelector('[data-testid="page-number"]')?.textContent?.trim()).toBe('Page: 00000002');
  });

  it('keeps the screen and shows the already-at-bottom message (FR-S07-11)', () => {
    flushInitialPage();
    host().querySelector<HTMLButtonElement>('[data-testid="forward-button"]')!.click();

    httpMock.expectOne(LIST_URL).flush({
      ...PAGE_1,
      message: 'You are already at the bottom of the page...',
      severity: 'error',
      rows: null,
      clearSearchInput: false,
      state: { ...PAGE_1.state, nextPageAvailable: false }
    });
    fixture.detectChanges();

    expect(messageText()).toBe('You are already at the bottom of the page...');
    expect(rowIds()[0]).toBe(id(1));
  });

  it('pages backward on F7 (FR-S07-12) and shows the top-of-page messages (FR-S07-13, FR-S07-14)', () => {
    flushInitialPage();
    pressKey('F7');

    const req = httpMock.expectOne(LIST_URL);
    expect(req.request.body.action).toBe('pageBackward');
    req.flush({
      ...PAGE_1,
      message: 'You are already at the top of the page...',
      severity: 'error',
      rows: null,
      clearSearchInput: false
    });
    fixture.detectChanges();
    expect(messageText()).toBe('You are already at the top of the page...');

    host().querySelector<HTMLButtonElement>('[data-testid="backward-button"]')!.click();
    httpMock.expectOne(LIST_URL).flush({
      ...PAGE_1,
      message: 'You have reached the top of the page...',
      severity: 'error',
      clearSearchInput: false
    });
    fixture.detectChanges();
    expect(messageText()).toBe('You have reached the top of the page...');
  });

  it('shows the bottom-of-page message with a partial page (FR-S07-08)', () => {
    flushInitialPage();
    pressKey('F8');

    httpMock.expectOne(LIST_URL).flush({
      ...PAGE_1,
      message: 'You have reached the bottom of the page...',
      severity: 'error',
      rows: page(21, 5),
      state: { firstTranId: id(21), lastTranId: id(10), pageNumber: 2, nextPageAvailable: false }
    });
    fixture.detectChanges();

    expect(messageText()).toBe('You have reached the bottom of the page...');
    expect(rowIds().slice(0, 5)).toEqual([id(21), id(22), id(23), id(24), id(25)]);
    expect(rowIds().slice(5)).toEqual(['', '', '', '', '']);
  });

  it('returns to the main menu on Exit and F3 (FR-S07-18)', () => {
    flushInitialPage();
    host().querySelector<HTMLButtonElement>('[data-testid="exit-button"]')!.click();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/menu');

    const event = pressKey('F3');
    expect(event.defaultPrevented).toBeTrue();
    expect(router.navigateByUrl).toHaveBeenCalledTimes(2);
  });

  it('shows the invalid-key message for an unmapped function key without a request (FR-S07-19)', () => {
    flushInitialPage();
    const event = pressKey('F5');

    expect(event.defaultPrevented).toBeTrue();
    expect(messageText()).toBe(MSG_INVALID_KEY);
    expect(rowIds()[0]).toBe(id(1));
    httpMock.expectNone(LIST_URL);
  });

  it('ignores ordinary keys (FR-S07-19)', () => {
    flushInitialPage();
    const event = pressKey('a');

    expect(event.defaultPrevented).toBeFalse();
    expect(messageText()).toBeUndefined();
  });

  it('shows the lookup error when the store fails (FR-S07-20)', () => {
    flushInitialPage();
    component.submit();

    httpMock.expectOne(LIST_URL).flush({ message: 'Unable to lookup transaction...' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(messageText()).toBe('Unable to lookup transaction...');
    expect(rowIds()[0]).toBe(id(1));
  });
});
