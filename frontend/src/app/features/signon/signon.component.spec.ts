import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { SignonComponent } from './signon.component';

/**
 * COSGN00C sign-on slice (map COSGN0A, app/bms/COSGN00.bms). Expectations come from the BMS
 * map, the COBOL and functional/CardDemo/programs/COSGN00C_functional_requirement.md.
 */
describe('SignonComponent (COSGN00C / COSGN0A)', () => {
  let fixture: ComponentFixture<SignonComponent>;
  let component: SignonComponent;
  let http: HttpTestingController;
  let router: Router;

  const header = {
    tranId: 'CC00',
    programName: 'COSGN00C',
    title01: 'AWS Mainframe Modernization',
    title02: 'CardDemo',
    currentDate: '09/07/26',
    currentTime: '14:05:09',
    applId: 'CARDDEMO',
    sysId: 'CICS',
  };

  const el = <T extends HTMLElement>(selector: string): T =>
    fixture.nativeElement.querySelector(selector) as T;
  const field = (name: string) => el(`[data-field="${name}"]`);
  const text = (selector: string) => (el(selector)?.textContent ?? '').trim();

  const flushHeader = () => {
    http.expectOne('/api/auth/header').flush(header);
    fixture.detectChanges();
  };

  const typeUserId = (value: string) => {
    const input = field('USERID') as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  const typePassword = (value: string) => {
    const input = field('PASSWD') as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  const submit = () => {
    el<HTMLFormElement>('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  };

  const failSignon = (status: number, message: string) => {
    http.expectOne('/api/auth/signon').flush({ message, status, timestamp: '2026-09-07T00:00:00Z' }, {
      status,
      statusText: 'error',
    });
    fixture.detectChanges();
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [SignonComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideNoopAnimations()],
    });
    fixture = TestBed.createComponent(SignonComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  describe('FR-01 / FR-25 — rendered field map equals the BMS-derived map', () => {
    it('renders every display-only field of COSGN0A from the header endpoint', () => {
      flushHeader();
      expect(text('[data-field="TRNNAME"]')).toBe('CC00');
      expect(text('[data-field="PGMNAME"]')).toBe('COSGN00C');
      expect(text('[data-field="TITLE01"]')).toBe('AWS Mainframe Modernization');
      expect(text('[data-field="TITLE02"]')).toBe('CardDemo');
      expect(text('[data-field="CURDATE"]')).toBe('09/07/26');
      expect(text('[data-field="CURTIME"]')).toBe('14:05:09');
      expect(text('[data-field="APPLID"]')).toBe('CARDDEMO');
      expect(text('[data-field="SYSID"]')).toBe('CICS');
    });

    it('display-only fields are read-only <output> elements, not inputs', () => {
      flushHeader();
      for (const name of ['TRNNAME', 'PGMNAME', 'TITLE01', 'TITLE02', 'CURDATE', 'CURTIME', 'APPLID', 'SYSID']) {
        expect(field(name).tagName).withContext(name).toBe('OUTPUT');
      }
      const inputs = fixture.nativeElement.querySelectorAll('input') as NodeListOf<HTMLInputElement>;
      expect(Array.from(inputs).map((i) => i.dataset['field'])).toEqual(['USERID', 'PASSWD']);
    });

    it('renders the static BMS text verbatim (labels, prompt, hints, footer)', () => {
      flushHeader();
      const body = (fixture.nativeElement as HTMLElement).textContent ?? '';
      expect(body).toContain('Tran :');
      expect(body).toContain('Prog :');
      expect(body).toContain('Date :');
      expect(body).toContain('Time :');
      expect(body).toContain('AppID:');
      expect(body).toContain('SysID:');
      expect(body).toContain('This is a Credit Card Demo Application for Mainframe Modernization');
      expect(body).toContain('Type your User ID and Password, then press ENTER:');
      expect(text('label[for="userId"]')).toBe('User ID     :');
      expect(text('label[for="password"]')).toBe('Password    :');
      expect(body).toContain('(8 Char)');
      expect(text('.keys')).toBe('ENTER=Sign-on  F3=Exit');
      expect(text('.banner')).toContain('NATIONAL RESERVE NOTE');
      expect(text('[data-field="ERRMSG"]')).toBe('');
    });

    it('keeps the map defaults for date/time when the header call fails', () => {
      http.expectOne('/api/auth/header').flush({ message: 'down' }, { status: 500, statusText: 'error' });
      fixture.detectChanges();
      expect(text('[data-field="TRNNAME"]')).toBe('CC00');
      expect(text('[data-field="PGMNAME"]')).toBe('COSGN00C');
      expect(text('[data-field="CURDATE"]')).toBe('mm/dd/yy');
    });
  });

  describe('edit rules of the UNPROT fields (BMS LENGTH=8; COSGN00C.cbl:132-136)', () => {
    beforeEach(flushHeader);

    it('USERID has maxlength 8, autofocus (IC) and upper-cases input (FR-04, Q-13)', () => {
      const input = field('USERID') as HTMLInputElement;
      expect(input.maxLength).toBe(8);
      expect(input.type).toBe('text');
      expect(input.hasAttribute('autofocus')).toBeTrue();
      typeUserId('user0001');
      expect(component.userId).toBe('USER0001');
    });

    it('USERID truncates anything beyond 8 characters', () => {
      typeUserId('abcdefghij');
      expect(component.userId).toBe('ABCDEFGH');
    });

    it('PASSWD is masked (DRK -> type=password) with maxlength 8 and is not upper-cased client-side', () => {
      const input = field('PASSWD') as HTMLInputElement;
      expect(input.type).toBe('password');
      expect(input.maxLength).toBe(8);
      typePassword('password');
      expect(component.password).toBe('password');
    });
  });

  describe('ENTER -> POST /api/auth/signon (PROCESS-ENTER-KEY, COSGN00C.cbl:105-140)', () => {
    beforeEach(flushHeader);

    it('FR-05 — sends the upper-cased user id and the password as typed; routes to landingTarget /menu', fakeAsync(() => {
      typeUserId('user0001');
      typePassword('password');
      submit();
      const req = http.expectOne('/api/auth/signon');
      expect(req.request.method).toBe('POST');
      expect(req.request.withCredentials).toBeTrue();
      expect(req.request.body).toEqual({ userId: 'USER0001', password: 'password' });
      req.flush({ userId: 'USER0001', userType: 'U', landingTarget: '/menu' });
      tick();
      expect(router.navigateByUrl).toHaveBeenCalledWith('/menu');
      expect(text('[data-field="ERRMSG"]')).toBe('');
    }));

    it('FR-05 / Q-01 — an admin (userType A) is routed to /menu as well', fakeAsync(() => {
      typeUserId('ADMIN001');
      typePassword('PASSWORD');
      submit();
      http.expectOne('/api/auth/signon').flush({ userId: 'ADMIN001', userType: 'A', landingTarget: '/menu' });
      tick();
      expect(router.navigateByUrl).toHaveBeenCalledWith('/menu');
    }));

    it('FR-02 — blank user id: ERRMSG shows E-01 verbatim, cursor on USERID (COSGN00C.cbl:117-120)', () => {
      typePassword('PASSWORD');
      submit();
      failSignon(400, 'Please enter User ID ...');
      expect(text('[data-field="ERRMSG"]')).toBe('Please enter User ID ...');
      expect(document.activeElement).toBe(field('USERID'));
      expect(router.navigateByUrl).not.toHaveBeenCalled();
    });

    it('FR-03 — blank password: ERRMSG shows E-02 verbatim, cursor on PASSWD (COSGN00C.cbl:122-125)', () => {
      typeUserId('USER0001');
      submit();
      failSignon(400, 'Please enter Password ...');
      expect(text('[data-field="ERRMSG"]')).toBe('Please enter Password ...');
      expect(document.activeElement).toBe(field('PASSWD'));
    });

    it('FR-06 — user not found: ERRMSG shows E-07 verbatim (COSGN00C.cbl:247-251)', () => {
      typeUserId('NOBODY');
      typePassword('PASSWORD');
      submit();
      failSignon(401, 'User not found. Try again ...');
      expect(text('[data-field="ERRMSG"]')).toBe('User not found. Try again ...');
      expect(document.activeElement).toBe(field('USERID'));
    });

    it('FR-06 — wrong password: ERRMSG shows E-06 verbatim (COSGN00C.cbl:241-246)', () => {
      typeUserId('USER0001');
      typePassword('WRONG');
      submit();
      failSignon(401, 'Wrong Password. Try again ...');
      expect(text('[data-field="ERRMSG"]')).toBe('Wrong Password. Try again ...');
    });

    it('FR-07 — technical error: ERRMSG shows E-13 verbatim (COSGN00C.cbl:252-257)', () => {
      typeUserId('USER0001');
      typePassword('PASSWORD');
      submit();
      failSignon(500, 'Unable to verify the User ...');
      expect(text('[data-field="ERRMSG"]')).toBe('Unable to verify the User ...');
    });

    it('a new ENTER clears the previous ERRMSG before the request (fresh SEND MAP)', () => {
      typeUserId('USER0001');
      submit();
      failSignon(400, 'Please enter Password ...');
      typePassword('PASSWORD');
      submit();
      expect(text('[data-field="ERRMSG"]')).toBe('');
      http.expectOne('/api/auth/signon').flush({ userId: 'USER0001', userType: 'U', landingTarget: '/menu' });
    });
  });

  describe('FR-08 — Exit (PF3, COSGN00C.cbl:86-88, :162-172)', () => {
    beforeEach(flushHeader);

    const expectSignedOff = () => {
      const req = http.expectOne('/api/auth/signoff');
      expect(req.request.method).toBe('POST');
      req.flush({ message: 'Thank you for using CardDemo application...      ' });
      fixture.detectChanges();
      expect(text('[data-field="THANKYOU"]')).toContain('Thank you for using CardDemo application...');
      expect(field('USERID')).toBeNull();
      expect(router.navigateByUrl).not.toHaveBeenCalled();
    };

    it('the Exit button posts /api/auth/signoff and shows the thank-you text', () => {
      const exit = Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
        .find((b) => b.textContent?.trim() === 'Exit');
      expect(exit).toBeDefined();
      exit?.click();
      fixture.detectChanges();
      expectSignedOff();
    });

    it('F3 maps to Exit (DV-03 keyboard mapping)', () => {
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'F3' }));
      fixture.detectChanges();
      expectSignedOff();
    });

    it('Escape maps to Exit (DV-03 keyboard mapping)', () => {
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      fixture.detectChanges();
      expectSignedOff();
    });

    it('"Sign on again" returns to an empty sign-on screen (EIBCALEN = 0)', () => {
      typeUserId('USER0001');
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'F3' }));
      fixture.detectChanges();
      expectSignedOff();
      el<HTMLButtonElement>('.signed-off button').click();
      fixture.detectChanges();
      expect((field('USERID') as HTMLInputElement).value).toBe('');
      expect(component.userId).toBe('');
    });
  });

  describe('DV-03 — no "invalid key" state (deviation from COSGN00C.cbl:89-95)', () => {
    beforeEach(flushHeader);

    it('F5 (and other unbound keys) change nothing: no request, no message, fields kept', () => {
      typeUserId('USER0001');
      typePassword('PASSWORD');
      for (const key of ['F5', 'F12', 'PageDown', 'a']) {
        window.dispatchEvent(new KeyboardEvent('keydown', { key }));
      }
      fixture.detectChanges();
      http.expectNone('/api/auth/signon');
      http.expectNone('/api/auth/signoff');
      expect(text('[data-field="ERRMSG"]')).toBe('');
      expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Invalid key pressed');
      expect(component.userId).toBe('USER0001');
      expect(component.password).toBe('PASSWORD');
      expect(component.signedOff).toBeFalse();
    });
  });
});
