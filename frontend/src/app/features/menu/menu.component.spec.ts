import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MenuComponent } from './menu.component';
import { MenuOption } from '../../core/menu.service';

/**
 * COMEN01C menu slice (map COMEN1A, app/bms/COMEN01.bms). Expectations come from the BMS map,
 * app/cpy/COMEN02Y.cpy, the COBOL and
 * functional/CardDemo/programs/COMEN01C_functional_requirement.md.
 */
describe('MenuComponent (COMEN01C / COMEN1A)', () => {
  let fixture: ComponentFixture<MenuComponent>;
  let component: MenuComponent;
  let http: HttpTestingController;
  let router: Router;

  const header = {
    tranId: 'CM00',
    programName: 'COMEN01C',
    title01: 'AWS Mainframe Modernization',
    title02: 'CardDemo',
    currentDate: '09/07/26',
    currentTime: '14:05:09',
    applId: 'CARDDEMO',
    sysId: 'CICS',
  };

  /** All 11 rows of app/cpy/COMEN02Y.cpy:25-89, in table order. */
  const copybookRows: Array<[number, string, string]> = [
    [1, 'Account View', 'COACTVWC'],
    [2, 'Account Update', 'COACTUPC'],
    [3, 'Credit Card List', 'COCRDLIC'],
    [4, 'Credit Card View', 'COCRDSLC'],
    [5, 'Credit Card Update', 'COCRDUPC'],
    [6, 'Transaction List', 'COTRN00C'],
    [7, 'Transaction View', 'COTRN01C'],
    [8, 'Transaction Add', 'COTRN02C'],
    [9, 'Transaction Reports', 'CORPT00C'],
    [10, 'Bill Payment', 'COBIL00C'],
    [11, 'Pending Authorization View', 'COPAUS0C'],
  ];

  const options: MenuOption[] = copybookRows.map(([number, name, program]) => ({
    number,
    name,
    program,
    endpoint: number === 1 ? '/api/accounts/{acctId}' : null,
    route: number === 1 ? '/accounts/view' : null,
    implemented: number === 1,
    userType: 'U',
  }));

  const el = <T extends HTMLElement>(selector: string): T =>
    fixture.nativeElement.querySelector(selector) as T;
  const field = (name: string) => el(`[data-field="${name}"]`);
  const text = (selector: string) => (el(selector)?.textContent ?? '').trim();
  const optionButton = (number: number) =>
    el<HTMLButtonElement>(`[data-option="${number}"]`);

  const flushMenu = (userType: 'A' | 'U' = 'U') => {
    http.expectOne('/api/menu').flush({ header, options });
    http.expectOne('/api/auth/session').flush({ userId: 'USER0001', userType });
    fixture.detectChanges();
  };

  const typeOption = (value: string) => {
    const input = field('OPTION') as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  const submit = () => {
    el<HTMLFormElement>('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  };

  const expectSelect = (option: string) => {
    const request = http.expectOne('/api/menu/select');
    expect(request.request.body).toEqual({ option });
    return request;
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MenuComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideNoopAnimations()],
    });
    fixture = TestBed.createComponent(MenuComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  describe('FR-09 / FR-25 — rendered field map equals the BMS/COMEN02Y-derived map', () => {
    it('renders every display-only header field of COMEN1A', () => {
      flushMenu();
      expect(text('[data-field="TRNNAME"]')).toBe('CM00');
      expect(text('[data-field="PGMNAME"]')).toBe('COMEN01C');
      expect(text('[data-field="TITLE01"]')).toBe('AWS Mainframe Modernization');
      expect(text('[data-field="TITLE02"]')).toBe('CardDemo');
      expect(text('[data-field="CURDATE"]')).toBe('09/07/26');
      expect(text('[data-field="CURTIME"]')).toBe('14:05:09');
      expect(field('APPLID')).toBeNull();
      expect(field('SYSID')).toBeNull();
    });

    it('renders OPTN001..OPTN011 as "nn. name" in table order, and no OPTN012', () => {
      flushMenu();
      const rendered = Array.from(
        fixture.nativeElement.querySelectorAll('[data-option]') as NodeListOf<HTMLElement>,
      ).map((row) => (row.textContent ?? '').trim());

      expect(rendered).toEqual([
        '01. Account View',
        '02. Account Update',
        '03. Credit Card List',
        '04. Credit Card View',
        '05. Credit Card Update',
        '06. Transaction List',
        '07. Transaction View',
        '08. Transaction Add',
        '09. Transaction Reports',
        '10. Bill Payment',
        '11. Pending Authorization View',
      ]);
      expect(field('OPTN001')).not.toBeNull();
      expect(field('OPTN011')).not.toBeNull();
      expect(field('OPTN012')).toBeNull();
    });

    it('OPTION is the only input: 2 characters, numeric, initially empty', () => {
      flushMenu();
      const inputs = fixture.nativeElement.querySelectorAll('input') as NodeListOf<HTMLInputElement>;
      expect(Array.from(inputs).map((i) => i.dataset['field'])).toEqual(['OPTION']);
      const option = inputs[0];
      expect(option.maxLength).toBe(2);
      expect(option.inputMode).toBe('numeric');
      expect(option.value).toBe('');
    });

    it('renders the static BMS text verbatim (labels, title, prompt, footer) and an empty ERRMSG', () => {
      flushMenu();
      const body = (fixture.nativeElement as HTMLElement).textContent ?? '';
      expect(body).toContain('Tran:');
      expect(body).toContain('Prog:');
      expect(body).toContain('Date:');
      expect(body).toContain('Time:');
      expect(text('.screen-title')).toBe('Main Menu');
      expect(text('label[for="option"]')).toBe('Please select an option :');
      expect(text('.keys')).toBe('ENTER=Continue  F3=Exit');
      expect(text('[data-field="ERRMSG"]')).toBe('');
    });
  });

  describe('FR-10 — option edit rules and E-08', () => {
    it('keeps at most 2 characters in the option field (BMS LENGTH=2)', () => {
      flushMenu();
      typeOption('123');
      expect(component.option).toBe('12');
    });

    it('shows E-08 verbatim and the normalised echo 00 for a blank option', () => {
      flushMenu();
      submit();
      expectSelect('').flush(
        { message: 'Please enter a valid option number...', status: 400, timestamp: '2026-09-07T00:00:00Z', option: '00' },
        { status: 400, statusText: 'Bad Request' },
      );
      fixture.detectChanges();

      expect(text('[data-field="ERRMSG"]')).toBe('Please enter a valid option number...');
      expect((field('OPTION') as HTMLInputElement).value).toBe('00');
      expect(router.navigateByUrl).not.toHaveBeenCalled();
    });

    it('shows E-08 and the echo 0A for a non-numeric option', () => {
      flushMenu();
      typeOption('A');
      submit();
      expectSelect('A').flush(
        { message: 'Please enter a valid option number...', status: 400, timestamp: '2026-09-07T00:00:00Z', option: '0A' },
        { status: 400, statusText: 'Bad Request' },
      );
      fixture.detectChanges();

      expect(text('[data-field="ERRMSG"]')).toBe('Please enter a valid option number...');
      expect(component.option).toBe('0A');
    });

    it('shows E-08 and the echo 12 for an option above the table count', () => {
      flushMenu();
      typeOption('12');
      submit();
      expectSelect('12').flush(
        { message: 'Please enter a valid option number...', status: 400, timestamp: '2026-09-07T00:00:00Z', option: '12' },
        { status: 400, statusText: 'Bad Request' },
      );
      fixture.detectChanges();

      expect(text('[data-field="ERRMSG"]')).toBe('Please enter a valid option number...');
      expect(component.option).toBe('12');
    });

    it('sends the option exactly as typed: normalisation belongs to the backend (cbl:117-125)', () => {
      flushMenu();
      typeOption(' 1');
      submit();
      expectSelect(' 1').flush({
        option: '01',
        program: 'COACTVWC',
        endpoint: '/api/accounts/{acctId}',
        route: '/accounts/view',
        implemented: true,
        message: null,
      });
      fixture.detectChanges();
      expect(component.option).toBe('01');
    });
  });

  describe('FR-11 / Q-12 — dispatch and the unavailable facade', () => {
    it('navigates to /accounts/view for option 1 typed and submitted', () => {
      flushMenu();
      typeOption('1');
      submit();
      expectSelect('1').flush({
        option: '01',
        program: 'COACTVWC',
        endpoint: '/api/accounts/{acctId}',
        route: '/accounts/view',
        implemented: true,
        message: null,
      });
      fixture.detectChanges();

      expect(router.navigateByUrl).toHaveBeenCalledWith('/accounts/view');
      expect(text('[data-field="ERRMSG"]')).toBe('');
    });

    it('a click on row 1 takes the same dispatch path as typing 01', () => {
      flushMenu();
      optionButton(1).click();
      fixture.detectChanges();
      expectSelect('01').flush({
        option: '01',
        program: 'COACTVWC',
        endpoint: '/api/accounts/{acctId}',
        route: '/accounts/view',
        implemented: true,
        message: null,
      });
      fixture.detectChanges();

      expect(router.navigateByUrl).toHaveBeenCalledWith('/accounts/view');
    });

    it('renders rows 2..11 disabled', () => {
      flushMenu();
      expect(optionButton(1).disabled).toBeFalse();
      for (let number = 2; number <= 11; number += 1) {
        expect(optionButton(number).disabled).withContext(`option ${number}`).toBeTrue();
        expect(optionButton(number).classList).withContext(`option ${number}`).toContain('unavailable');
      }
    });

    it('shows the facade text and stays on the menu when an excluded option is submitted', () => {
      flushMenu();
      typeOption('5');
      submit();
      expectSelect('5').flush({
        option: '05',
        program: 'COCRDUPC',
        endpoint: null,
        route: null,
        implemented: false,
        message: 'Option not available in this release',
      });
      fixture.detectChanges();

      expect(text('[data-field="ERRMSG"]')).toBe('Option not available in this release');
      expect(component.option).toBe('05');
      expect(router.navigateByUrl).not.toHaveBeenCalled();
    });

    it('a disabled row cannot be clicked into a request', () => {
      flushMenu();
      optionButton(11).click();
      fixture.detectChanges();
      http.expectNone('/api/menu/select');
      expect(router.navigateByUrl).not.toHaveBeenCalled();
    });
  });

  describe('FR-12 / DV-03 — Exit and key handling', () => {
    const expectSignOff = () => {
      const request = http.expectOne('/api/auth/signoff');
      request.flush({ message: 'Thank you for using CardDemo application...      ' });
      fixture.detectChanges();
    };

    it('the Exit button signs off and routes to /signon', () => {
      flushMenu();
      const exitButton = Array.from(
        fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
      ).find((button) => (button.textContent ?? '').trim() === 'Exit');
      exitButton?.click();
      expectSignOff();

      expect(router.navigateByUrl).toHaveBeenCalledWith('/signon');
    });

    it('F3 signs off and routes to /signon (PF3, cbl:96-98)', () => {
      flushMenu();
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'F3' }));
      expectSignOff();
      expect(router.navigateByUrl).toHaveBeenCalledWith('/signon');
    });

    it('Escape signs off and routes to /signon (Q-06)', () => {
      flushMenu();
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      expectSignOff();
      expect(router.navigateByUrl).toHaveBeenCalledWith('/signon');
    });

    it('DV-03 — an unbound key (F5) does nothing and shows no invalid-key message', () => {
      flushMenu();
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'F5' }));
      fixture.detectChanges();

      http.expectNone('/api/auth/signoff');
      expect(text('[data-field="ERRMSG"]')).toBe('');
      expect(text('[data-field="ERRMSG"]')).not.toContain('Invalid key pressed');
      expect(router.navigateByUrl).not.toHaveBeenCalled();
    });
  });

  describe('Q-01 / B-0027 — session behaviour', () => {
    it('shows the admin banner for a userType A session and the same 11 rows', () => {
      flushMenu('A');
      expect(text('[data-field="ADMINBANNER"]')).toBe('Administration is not available in this release');
      expect(fixture.nativeElement.querySelectorAll('[data-option]').length).toBe(11);
      expect(optionButton(1).disabled).toBeFalse();
    });

    it('shows no banner for a regular user', () => {
      flushMenu('U');
      expect(field('ADMINBANNER')).toBeNull();
    });

    it('routes to /signon when the menu request answers 401 (target form of cbl:82-84)', () => {
      http.expectOne('/api/menu').flush(
        { message: 'Authentication required', status: 401, timestamp: '2026-09-07T00:00:00Z' },
        { status: 401, statusText: 'Unauthorized' },
      );
      http.expectOne('/api/auth/session').flush(
        { message: 'Authentication required', status: 401, timestamp: '2026-09-07T00:00:00Z' },
        { status: 401, statusText: 'Unauthorized' },
      );
      fixture.detectChanges();

      expect(router.navigateByUrl).toHaveBeenCalledWith('/signon');
    });

    it('routes to /signon when the session expires between render and selection', () => {
      flushMenu();
      typeOption('1');
      submit();
      expectSelect('1').flush(
        { message: 'Authentication required', status: 401, timestamp: '2026-09-07T00:00:00Z' },
        { status: 401, statusText: 'Unauthorized' },
      );
      fixture.detectChanges();

      expect(router.navigateByUrl).toHaveBeenCalledWith('/signon');
    });
  });
});
