import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { AccountViewComponent } from './account-view.component';
import { AccountBlock, CustomerBlock } from '../../core/account.service';

/**
 * COACTVWC account view slice (map CACTVWA, app/bms/COACTVW.bms). Every expectation comes from the
 * BMS map, the COBOL and functional/CardDemo/programs/COACTVWC_functional_requirement.md; the
 * displayed values are the fixture rows decoded from app/data/ASCII, never produced by the frontend.
 */
describe('AccountViewComponent (COACTVWC / CACTVWA)', () => {
  let fixture: ComponentFixture<AccountViewComponent>;
  let component: AccountViewComponent;
  let http: HttpTestingController;
  let router: Router;

  const header = {
    tranId: 'CAVW',
    programName: 'COACTVWC',
    title01: 'AWS Mainframe Modernization',
    title02: 'CardDemo',
    currentDate: '09/07/26',
    currentTime: '14:05:09',
    applId: 'CARDDEMO',
    sysId: 'CICS',
  };

  /** acctdata.txt:27 — 284.00 / 5572.00 / 2075.00, group A000000000 (DV-06). */
  const account: AccountBlock = {
    activeStatus: 'Y',
    openDate: '2012-09-30',
    creditLimit: '+      5,572.00',
    expirationDate: '2025-07-13',
    cashCreditLimit: '+      2,075.00',
    reissueDate: '2025-07-13',
    currentBalance: '+        284.00',
    currentCycleCredit: '+           .00',
    groupId: 'A000000000',
    currentCycleDebit: '+           .00',
  };

  /** custdata.txt:27 — ZIP 07923-8822 and both 15-byte phone fields (DV-05). */
  const customer: CustomerBlock = {
    customerId: '000000027',
    ssn: '980-16-1210',
    dateOfBirth: '1986-11-08',
    ficoScore: 78,
    firstName: 'Ward',
    middleName: 'Henri',
    lastName: 'Jones',
    addressLine1: '210 Amaya Turnpike',
    addressLine2: 'Suite 180',
    city: 'Port Dwight',
    stateCode: 'GU',
    zipCode: '07923-8822',
    countryCode: 'USA',
    phone1: '(935)027-1145',
    phone2: '(103)537-5007',
    governmentIssuedId: '00000000000881558757',
    eftAccountId: '0050024139',
    primaryCardHolder: 'Y',
  };

  const el = <T extends HTMLElement>(selector: string): T =>
    fixture.nativeElement.querySelector(selector) as T;
  const field = (name: string) => el(`[data-field="${name}"]`);
  const text = (selector: string) => (el(selector)?.textContent ?? '').trim();
  const input = () => field('ACCTSID') as HTMLInputElement;

  const type = (value: string) => {
    const acctsid = input();
    acctsid.value = value;
    acctsid.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  const submit = () => {
    el<HTMLFormElement>('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  };

  const expectView = (accountId: string) => http.expectOne(`/api/accounts/${accountId}`);

  const flushError = (accountId: string, body: Record<string, unknown>, status: number) => {
    expectView(accountId).flush(body, { status, statusText: status === 404 ? 'Not Found' : 'Bad Request' });
    fixture.detectChanges();
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AccountViewComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideNoopAnimations()],
    });
    fixture = TestBed.createComponent(AccountViewComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
    fixture.detectChanges();
    // 1100-SCREEN-INIT fills the header on the initial SEND MAP too (cbl:431-453).
    http.expectOne('/api/accounts/view/header').flush(header);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  describe('FR-13 — the rendered field map equals the CACTVWA map', () => {
    it('renders every named field of the map, and nothing that is not in it', () => {
      const rendered = Array.from(
        fixture.nativeElement.querySelectorAll('[data-field]') as NodeListOf<HTMLElement>,
      ).map((node) => node.dataset['field']);

      expect(rendered).toEqual([
        'TRNNAME',
        'TITLE01',
        'CURDATE',
        'PGMNAME',
        'TITLE02',
        'CURTIME',
        'ACCTSID',
        'ACSTTUS',
        'ADTOPEN',
        'ACRDLIM',
        'AEXPDT',
        'ACSHLIM',
        'AREISDT',
        'ACURBAL',
        'ACRCYCR',
        'AADDGRP',
        'ACRCYDB',
        'ACSTNUM',
        'ACSTSSN',
        'ACSTDOB',
        'ACSTFCO',
        'ACSFNAM',
        'ACSMNAM',
        'ACSLNAM',
        'ACSADL1',
        'ACSSTTE',
        'ACSADL2',
        'ACSZIPC',
        'ACSCITY',
        'ACSCTRY',
        'ACSPHN1',
        'ACSGOVT',
        'ACSPHN2',
        'ACSEFTC',
        'ACSPFLG',
        'INFOMSG',
        'ERRMSG',
      ]);
    });

    it('ACCTSID is the only editable field: 11 characters, numeric, initially empty', () => {
      const inputs = fixture.nativeElement.querySelectorAll('input') as NodeListOf<HTMLInputElement>;
      expect(Array.from(inputs).map((node) => node.dataset['field'])).toEqual(['ACCTSID']);
      expect(input().maxLength).toBe(11);
      expect(input().inputMode).toBe('numeric');
      expect(input().value).toBe('');
    });

    it('every account and customer field is display-only (rendered as <output>, never an input)', () => {
      const displayFields = [
        'ACSTTUS', 'ADTOPEN', 'ACRDLIM', 'AEXPDT', 'ACSHLIM', 'AREISDT', 'ACURBAL', 'ACRCYCR',
        'AADDGRP', 'ACRCYDB', 'ACSTNUM', 'ACSTSSN', 'ACSTDOB', 'ACSTFCO', 'ACSFNAM', 'ACSMNAM',
        'ACSLNAM', 'ACSADL1', 'ACSSTTE', 'ACSADL2', 'ACSZIPC', 'ACSCITY', 'ACSCTRY', 'ACSPHN1',
        'ACSGOVT', 'ACSPHN2', 'ACSEFTC', 'ACSPFLG',
      ];
      displayFields.forEach((name) => expect(field(name).tagName).toBe('OUTPUT'));
    });

    it('renders the static BMS literals verbatim, with INFOMSG set and ERRMSG empty', () => {
      const body = (fixture.nativeElement as HTMLElement).textContent ?? '';
      expect(text('.screen-title')).toBe('View Account');
      expect(text('label[for="acctsid"]')).toBe('Account Number :');
      expect(body).toContain('Active Y/N:');
      expect(body).toContain('Credit Limit        :');
      expect(body).toContain('Cash credit Limit   :');
      expect(body).toContain('Current Balance     :');
      expect(body).toContain('Current Cycle Credit:');
      expect(body).toContain('Current Cycle Debit :');
      expect(body).toContain('Account Group:');
      expect(body).toContain('Customer Details');
      expect(body).toContain('Government Issued Id Ref    :');
      expect(body).toContain('Primary Card Holder Y/N:');
      expect(text('.keys')).toBe('F3=Exit');
      expect(text('[data-field="INFOMSG"]')).toBe('Enter or update id of account to display');
      expect(text('[data-field="ERRMSG"]')).toBe('');
    });

    it('paints the empty map on entry: no account read is issued until ENTER (cbl:290-296)', () => {
      http.expectNone((request) => request.url.startsWith('/api/accounts/') && !request.url.endsWith('/view/header'));
      expect(text('[data-field="ACURBAL"]')).toBe('');
      expect(text('[data-field="ACSTNUM"]')).toBe('');
    });

    it('AC-ACV-01 — the header carries the titles, trancode, program and clock on entry (cbl:431-453)', () => {
      expect(text('[data-field="TRNNAME"]')).toBe('CAVW');
      expect(text('[data-field="PGMNAME"]')).toBe('COACTVWC');
      expect(text('[data-field="TITLE01"]')).toBe('AWS Mainframe Modernization');
      expect(text('[data-field="TITLE02"]')).toBe('CardDemo');
      expect(text('[data-field="CURDATE"]')).toBe('09/07/26');
      expect(text('[data-field="CURTIME"]')).toBe('14:05:09');
    });

    it('puts the cursor in ACCTSID on entry (ATTRB=IC)', async () => {
      await fixture.whenStable();
      expect(document.activeElement).toBe(input());
    });
  });

  describe('FR-14 / FR-15 — ACCTSID edit rules and their verbatim messages', () => {
    it('never holds more than 11 characters (BMS LENGTH=11)', () => {
      type('123456789012');
      expect(component.accountId).toBe('12345678901');
    });

    it('E-04 — Search with a blank field asks the empty-filter endpoint and shows "No input received"', () => {
      submit();
      flushError('', { message: 'No input received', status: 400 }, 400);

      expect(text('[data-field="ERRMSG"]')).toBe('No input received');
      expect(text('[data-field="ACURBAL"]')).toBe('');
      expect(document.activeElement).toBe(input());
      expect(router.navigateByUrl).not.toHaveBeenCalled();
    });

    it('E-04 — the blank filter is echoed as "*" in a red ACCTSID (cbl:561-565)', () => {
      submit();
      flushError('', { message: 'No input received', status: 400 }, 400);

      expect(input().value).toBe('*');
      expect(input().dataset['attrb']).toBe('DFHRED');
      expect(el('mat-form-field').classList).toContain('field-error');
      expect(document.activeElement).toBe(input());
    });

    it('E-05 — a rejected filter turns ACCTSID red and keeps the typed value (cbl:555-559)', () => {
      type('27');
      submit();
      flushError('27', { message: 'Account Filter must  be a non-zero 11 digit number', status: 400 }, 400);

      expect(input().value).toBe('27');
      expect(input().dataset['attrb']).toBe('DFHRED');
      expect(el('mat-form-field').classList).toContain('field-error');
    });

    it('E-05 / Q-02 — a filter shorter than 11 digits shows the verbatim two-space literal', () => {
      type('27');
      submit();
      flushError('27', { message: 'Account Filter must  be a non-zero 11 digit number', status: 400 }, 400);

      expect(text('[data-field="ERRMSG"]')).toBe('Account Filter must  be a non-zero 11 digit number');
    });

    it('E-05 — a non-numeric filter reaches the edit and shows the same literal', () => {
      type('0000000002A');
      submit();
      expect(component.accountId).toBe('0000000002A');
      flushError('0000000002A', { message: 'Account Filter must  be a non-zero 11 digit number', status: 400 }, 400);

      expect(text('[data-field="ERRMSG"]')).toBe('Account Filter must  be a non-zero 11 digit number');
    });

    it('E-05 — an all-zero filter shows the same literal', () => {
      type('00000000000');
      submit();
      flushError('00000000000', { message: 'Account Filter must  be a non-zero 11 digit number', status: 400 }, 400);

      expect(text('[data-field="ERRMSG"]')).toBe('Account Filter must  be a non-zero 11 digit number');
    });
  });

  describe('FR-16..FR-19 — a found account paints both blocks', () => {
    const search = (accountId = '00000000027') => {
      type(accountId);
      submit();
      expectView(accountId).flush({
        header,
        accountNumber: accountId,
        infoMessage: 'Enter or update id of account to display',
        account,
        customer,
      });
      fixture.detectChanges();
    };

    it('renders the header and every account field as the backend formatted them', () => {
      search();
      expect(text('[data-field="TRNNAME"]')).toBe('CAVW');
      expect(text('[data-field="PGMNAME"]')).toBe('COACTVWC');
      expect(text('[data-field="ACCTSID"]')).toBe('');
      expect(input().value).toBe('00000000027');
      expect(text('[data-field="ACSTTUS"]')).toBe('Y');
      expect(text('[data-field="ADTOPEN"]')).toBe('2012-09-30');
      expect(text('[data-field="ACRDLIM"]')).toBe('+      5,572.00');
      expect(text('[data-field="AEXPDT"]')).toBe('2025-07-13');
      expect(text('[data-field="ACSHLIM"]')).toBe('+      2,075.00');
      expect(text('[data-field="AREISDT"]')).toBe('2025-07-13');
      expect(text('[data-field="ACURBAL"]')).toBe('+        284.00');
      expect(text('[data-field="ACRCYCR"]')).toBe('+           .00');
      expect(text('[data-field="ACRCYDB"]')).toBe('+           .00');
    });

    it('renders every customer field, including the full SSN edit', () => {
      search();
      expect(text('[data-field="ACSTNUM"]')).toBe('000000027');
      expect(text('[data-field="ACSTSSN"]')).toBe('980-16-1210');
      expect(text('[data-field="ACSTDOB"]')).toBe('1986-11-08');
      expect(text('[data-field="ACSTFCO"]')).toBe('078');
      expect(text('[data-field="ACSFNAM"]')).toBe('Ward');
      expect(text('[data-field="ACSMNAM"]')).toBe('Henri');
      expect(text('[data-field="ACSLNAM"]')).toBe('Jones');
      expect(text('[data-field="ACSADL1"]')).toBe('210 Amaya Turnpike');
      expect(text('[data-field="ACSADL2"]')).toBe('Suite 180');
      expect(text('[data-field="ACSCITY"]')).toBe('Port Dwight');
      expect(text('[data-field="ACSSTTE"]')).toBe('GU');
      expect(text('[data-field="ACSCTRY"]')).toBe('USA');
      expect(text('[data-field="ACSGOVT"]')).toBe('00000000000881558757');
      expect(text('[data-field="ACSEFTC"]')).toBe('0050024139');
      expect(text('[data-field="ACSPFLG"]')).toBe('Y');
      expect(text('[data-field="ERRMSG"]')).toBe('');
    });

    it('DV-05 — the full ZIP and both full phone numbers are shown, not the BMS windows', () => {
      search();
      expect(text('[data-field="ACSZIPC"]')).toBe('07923-8822');
      expect(text('[data-field="ACSPHN1"]')).toBe('(935)027-1145');
      expect(text('[data-field="ACSPHN2"]')).toBe('(103)537-5007');
    });

    it('DV-06 / D-0038 — Account Group shows the stored A000000000', () => {
      search();
      expect(text('[data-field="AADDGRP"]')).toBe('A000000000');
    });

    it('MOVE CUST-FICO-CREDIT-SCORE 9(3) TO ACSTFCOO X(3) keeps the leading zero (cbl:505-506)', () => {
      search();
      // custdata.txt:27 bytes 322-324 are '078'; the API types the score as an integer (FR §3.2).
      expect(customer.ficoScore).toBe(78);
      expect(text('[data-field="ACSTFCO"]')).toBe('078');
      expect(component.ficoScoreDisplay).toBe('078');
    });

    it('leaves the cursor in ACCTSID after a successful search', () => {
      search();
      expect(document.activeElement).toBe(input());
    });
  });

  describe('FR-17 / FR-20 — the not-found branches', () => {
    it('E-09 — an account with no xref shows the verbatim text and no data', () => {
      type('00000000001');
      submit();
      flushError(
        '00000000001',
        { message: 'Account:00000000001 not found in Cross ref file.  Resp:0000000013 Reas:0000', status: 404 },
        404,
      );

      expect(text('[data-field="ERRMSG"]')).toBe(
        'Account:00000000001 not found in Cross ref file.  Resp:0000000013 Reas:0000',
      );
      expect(text('[data-field="ACURBAL"]')).toBe('');
      expect(text('[data-field="ACSTNUM"]')).toBe('');
    });

    it('DV-01 / E-10 — a missing account row shows E-10 only, with no account or customer block', () => {
      type('00000000050');
      submit();
      flushError(
        '00000000050',
        { message: 'Account:00000000050 not found in Acct Master file.Resp:0000000013 Reas:0000', status: 404 },
        404,
      );

      expect(text('[data-field="ERRMSG"]')).toBe(
        'Account:00000000050 not found in Acct Master file.Resp:0000000013 Reas:0000',
      );
      expect(text('[data-field="ACURBAL"]')).toBe('');
      expect(text('[data-field="ACSTNUM"]')).toBe('');
    });

    it('E-11 — a missing customer row keeps the account block on screen and clears the customer block', () => {
      type('00000000027');
      submit();
      flushError(
        '00000000027',
        {
          message: 'CustId:000000027 not found in customer master.Resp: 0000000013 REAS:0000000',
          status: 404,
          accountNumber: '00000000027',
          account,
        },
        404,
      );

      expect(text('[data-field="ERRMSG"]')).toBe(
        'CustId:000000027 not found in customer master.Resp: 0000000013 REAS:0000000',
      );
      expect(text('[data-field="ACURBAL"]')).toBe('+        284.00');
      expect(text('[data-field="AADDGRP"]')).toBe('A000000000');
      expect(text('[data-field="ACSTNUM"]')).toBe('');
    });

    it('E-12 — a read failure shows the program\u2019s own File Error text (D-0040)', () => {
      type('00000000027');
      submit();
      expectView('00000000027').flush(
        {
          message: 'File Error: READ     on ACCTDAT   returned RESP 0000000016,RESP2 0000000000',
          status: 500,
        },
        { status: 500, statusText: 'Server Error' },
      );
      fixture.detectChanges();

      expect(text('[data-field="ERRMSG"]')).toBe(
        'File Error: READ     on ACCTDAT   returned RESP 0000000016,RESP2 0000000000',
      );
    });
  });

  describe('DV-03 / FR-21 — keys, Exit and the route guard', () => {
    it('Exit clears the screen state and routes to /menu (XCTL COMEN01C)', () => {
      type('00000000027');
      el<HTMLButtonElement>('.actions button[mat-stroked-button]').click();
      fixture.detectChanges();

      expect(router.navigateByUrl).toHaveBeenCalledWith('/menu');
      expect(component.accountId).toBe('');
      expect(component.account).toBeNull();
      expect(component.customer).toBeNull();
      expect(component.accountIdInError).toBeFalse();
    });

    it('F3 and Escape both exit to /menu', () => {
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'F3' }));
      expect(router.navigateByUrl).toHaveBeenCalledWith('/menu');

      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      expect((router.navigateByUrl as jasmine.Spy).calls.allArgs()).toEqual([['/menu'], ['/menu']]);
    });

    it('DV-03 — F5, F7, F8 and Tab do nothing: no request, no navigation, no message', () => {
      ['F5', 'F7', 'F8', 'Tab', 'Enter'].forEach((key) =>
        window.dispatchEvent(new KeyboardEvent('keydown', { key })),
      );
      fixture.detectChanges();

      http.expectNone(() => true);
      expect(router.navigateByUrl).not.toHaveBeenCalled();
      expect(text('[data-field="ERRMSG"]')).toBe('');
    });

    it('B-0027 — a 401 sends the user back to /signon', () => {
      type('00000000027');
      submit();
      flushError('00000000027', { message: 'Authentication required', status: 401 }, 401);

      expect(router.navigateByUrl).toHaveBeenCalledWith('/signon');
    });
  });
});
