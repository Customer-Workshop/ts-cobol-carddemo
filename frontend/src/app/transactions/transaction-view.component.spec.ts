import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { routes } from '../app.routes';
import { authGuard } from '../auth/auth.guard';
import { MSG_INVALID_KEY } from '../shared/invalid-key';
import { DETAIL_FIELDS, TransactionViewComponent } from './transaction-view.component';
import { TransactionView } from './transaction-view.service';

const VIEW_URL = '/api/v1/transactions/view';

const SEEDED: TransactionView = {
  transactionId: '0000000000683580',
  cardNumber: '4859452612877065',
  typeCode: '01',
  categoryCode: '0001',
  source: 'POS TERM',
  description: 'Purchase at Abshire-Lowe',
  amount: '+00000504.77',
  originalDate: '2022-06-10',
  processedDate: '',
  merchantId: '800000000',
  merchantName: 'Abshire-Lowe',
  merchantCity: 'North Enoshaven',
  merchantZip: '72112'
};

describe('TransactionViewComponent', () => {
  let fixture: ComponentFixture<TransactionViewComponent>;
  let component: TransactionViewComponent;
  let httpMock: HttpTestingController;
  let router: Router;

  async function setup(queryParams: Record<string, string> = {}): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [TransactionViewComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(TransactionViewComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl');
    await settle();
  }

  /** Runs change detection and lets ngModel / focus timers scheduled in the zone drain. */
  async function settle(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  afterEach(() => {
    httpMock.verify();
  });

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function messageText(): string | undefined {
    return element().querySelector('[data-testid="tran-view-message"]')?.textContent?.trim();
  }

  function fieldText(key: keyof TransactionView): string | undefined {
    return element().querySelector(`[data-testid="field-${key}"]`)?.textContent?.trim();
  }

  function input(): HTMLInputElement {
    return element().querySelector<HTMLInputElement>('[data-testid="tran-id-input"]')!;
  }

  async function fetchAndFlush(tranId: string, detail: TransactionView): Promise<void> {
    component.tranId = tranId;
    component.submit();
    httpMock.expectOne((r) => r.url === VIEW_URL && r.params.get('tranId') === tranId).flush(detail);
    await settle();
  }

  function expectAllDetailsBlank(): void {
    for (const field of DETAIL_FIELDS) {
      expect(fieldText(field.key)).withContext(field.key).toBe('');
    }
  }

  it('renders a blank screen with focus on Tran ID and issues no lookup on first entry (FR-S08-02)', async () => {
    await setup();

    expect(input().value).toBe('');
    expectAllDetailsBlank();
    expect(messageText()).toBeUndefined();
    expect(document.activeElement).toBe(input());
    httpMock.expectNone((r) => r.url === VIEW_URL);
  });

  it('pre-fills and fetches the selected transaction on arrival from the list (FR-S08-03)', async () => {
    await setup({ tranId: '0000000000683580' });

    const request = httpMock.expectOne((r) => r.url === VIEW_URL);
    expect(request.request.params.get('tranId')).toBe('0000000000683580');
    request.flush(SEEDED);
    await settle();

    expect(input().value).toBe('0000000000683580');
    expect(fieldText('transactionId')).toBe('0000000000683580');
  });

  it('shows the empty-id message, keeps the details and does not call the API on a blank id (FR-S08-04)', async () => {
    await setup();
    await fetchAndFlush('0000000000683580', SEEDED);

    component.tranId = '   ';
    component.submit();
    fixture.detectChanges();

    expect(messageText()).toBe('Tran ID can NOT be empty...');
    expect(fieldText('transactionId')).toBe('0000000000683580');
    expect(fieldText('amount')).toBe('+00000504.77');
    httpMock.expectNone((r) => r.url === VIEW_URL);
    expect(document.activeElement).toBe(input());
  });

  it('clears the detail area before the lookup for a non-blank id (FR-S08-05)', async () => {
    await setup();
    await fetchAndFlush('0000000000683580', SEEDED);

    component.tranId = '0000000001774260';
    component.submit();
    fixture.detectChanges();

    expectAllDetailsBlank();
    httpMock.expectOne((r) => r.url === VIEW_URL).flush({ ...SEEDED, transactionId: '0000000001774260' });
    fixture.detectChanges();
    expect(fieldText('transactionId')).toBe('0000000001774260');
  });

  it('shows the not-found message with an empty detail area and keeps the typed id (FR-S08-06)', async () => {
    await setup();
    await fetchAndFlush('0000000000683580', SEEDED);

    component.tranId = 'NOPE000000000001';
    component.submit();
    httpMock
      .expectOne((r) => r.url === VIEW_URL)
      .flush({ message: 'Transaction ID NOT found...' }, { status: 404, statusText: 'Not Found' });
    await settle();

    expect(messageText()).toBe('Transaction ID NOT found...');
    expectAllDetailsBlank();
    expect(input().value).toBe('NOPE000000000001');
    expect(document.activeElement).toBe(input());
  });

  it('shows the lookup-error message for a store failure, also when the response has no body (FR-S08-07)', async () => {
    await setup();

    component.tranId = '0000000000683580';
    component.submit();
    httpMock
      .expectOne((r) => r.url === VIEW_URL)
      .flush({ message: 'Unable to lookup Transaction...' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(messageText()).toBe('Unable to lookup Transaction...');
    expectAllDetailsBlank();

    component.submit();
    httpMock.expectOne((r) => r.url === VIEW_URL).error(new ProgressEvent('error'));
    fixture.detectChanges();
    expect(messageText()).toBe('Unable to lookup Transaction...');
  });

  it('displays all 13 detail fields verbatim and no message on a successful fetch (FR-S08-08)', async () => {
    await setup();

    await fetchAndFlush('0000000000683580', SEEDED);

    for (const field of DETAIL_FIELDS) {
      expect(fieldText(field.key)).withContext(field.key).toBe(SEEDED[field.key]);
    }
    expect(messageText()).toBeUndefined();
    expect(document.activeElement).toBe(input());
  });

  it('limits the Tran ID input to 16 characters and sends it verbatim (FR-S08-12)', async () => {
    await setup();

    expect(input().getAttribute('maxlength')).toBe('16');

    component.tranId = 'abcdefghijklmnop';
    component.submit();
    const request = httpMock.expectOne((r) => r.url === VIEW_URL);
    expect(request.request.params.get('tranId')).toBe('abcdefghijklmnop');
    request.flush({ message: 'Transaction ID NOT found...' }, { status: 404, statusText: 'Not Found' });
  });

  it('returns to the main menu on Back / F3 when no caller is recorded (FR-S08-13)', async () => {
    await setup();

    element().querySelector<HTMLButtonElement>('[data-testid="back-button"]')?.click();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/menu');

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'F3' }));
    expect(router.navigateByUrl).toHaveBeenCalledTimes(2);
  });

  it('returns to the recorded caller on Back when an internal returnUrl is given (FR-S08-13)', async () => {
    await setup({ returnUrl: '/transactions/list' });

    component.back();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/transactions/list');
  });

  it('ignores an external returnUrl and falls back to the main menu (FR-S08-13)', async () => {
    await setup({ returnUrl: 'https://example.invalid/x' });

    component.back();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/menu');
  });

  it('clears the id, all details and the message on Clear / F4 (FR-S08-14)', async () => {
    await setup();
    await fetchAndFlush('0000000000683580', SEEDED);
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'F7' }));
    fixture.detectChanges();
    expect(messageText()).toBe(MSG_INVALID_KEY);

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'F4' }));
    await settle();

    expect(input().value).toBe('');
    expectAllDetailsBlank();
    expect(messageText()).toBeUndefined();
    expect(document.activeElement).toBe(input());
    httpMock.expectNone((r) => r.url === VIEW_URL);
  });

  it('resolves Browse Tran. / F5 through the registry and shows coming-soon while disabled (FR-S08-15)', async () => {
    await setup();
    await fetchAndFlush('0000000000683580', SEEDED);

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'F5' }));
    const request = httpMock.expectOne('/api/v1/menu/select');
    expect(request.request.body).toEqual({ menu: 'main', option: '06' });
    request.flush({
      outcome: 'comingSoon',
      message: 'This option Transaction List is coming soon ...',
      severity: 'info',
      target: null
    });
    fixture.detectChanges();

    expect(messageText()).toBe('This option Transaction List is coming soon ...');
    expect(element().querySelector('[data-testid="tran-view-message"]')?.classList).toContain('info-message');
    expect(fieldText('transactionId')).toBe('0000000000683580');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('navigates to the transaction list route once the registry enables it (FR-S08-15)', async () => {
    await setup();

    element().querySelector<HTMLButtonElement>('[data-testid="browse-button"]')?.click();
    httpMock.expectOne('/api/v1/menu/select').flush({
      outcome: 'navigate',
      message: null,
      severity: null,
      target: { id: '06', name: 'Transaction List', programKey: 'COTRN00C', route: '/transactions/list' }
    });

    expect(router.navigateByUrl).toHaveBeenCalledWith('/transactions/list');
  });

  it('shows the invalid-key message and keeps the screen for an unmapped function key (FR-S08-16)', async () => {
    await setup();
    await fetchAndFlush('0000000000683580', SEEDED);

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'F7' }));
    fixture.detectChanges();

    expect(messageText()).toBe(MSG_INVALID_KEY);
    expect(fieldText('transactionId')).toBe('0000000000683580');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('renders the title, transaction/program header, field labels and footer verbatim (FR-S08-17)', async () => {
    await setup();

    expect(element().querySelector('[data-testid="screen-title"]')?.textContent?.trim()).toBe('View Transaction');
    expect(element().querySelector('[data-testid="screen-header"]')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      'Tran: CT01 Prog: COTRN01C'
    );
    expect(element().querySelector('[data-testid="screen-footer"]')?.textContent).toBe(
      'ENTER=Fetch  F3=Back  F4=Clear  F5=Browse Tran.'
    );
    const labels = Array.from(element().querySelectorAll('[data-testid="detail-fields"] dt')).map((dt) => dt.textContent?.trim());
    expect(labels).toEqual([
      'Transaction ID:',
      'Card Number:',
      'Type CD:',
      'Category CD:',
      'Source:',
      'Description:',
      'Amount:',
      'Orig Date:',
      'Proc Date:',
      'Merchant ID:',
      'Merchant Name:',
      'Merchant City:',
      'Merchant Zip:'
    ]);
  });
});

describe('transaction view route', () => {
  it('is registered at /transactions/view behind authGuard (FR-S08-01)', () => {
    const route = routes.find((r) => r.path === 'transactions/view');
    expect(route?.component).toBe(TransactionViewComponent);
    expect(route?.canActivate).toEqual([authGuard]);
  });
});
