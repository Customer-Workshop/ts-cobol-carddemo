import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Router } from '@angular/router';
import { SignOnComponent, MSG_ENTER_PASSWORD, MSG_ENTER_USER_ID, MSG_THANK_YOU } from './sign-on.component';
import { MSG_INVALID_KEY } from '../shared/invalid-key';

describe('SignOnComponent', () => {
  let fixture: ComponentFixture<SignOnComponent>;
  let component: SignOnComponent;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SignOnComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()]
    }).compileComponents();

    fixture = TestBed.createComponent(SignOnComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl');
    sessionStorage.clear();
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
    sessionStorage.clear();
  });

  function errorText(): string | undefined {
    return (fixture.nativeElement as HTMLElement).querySelector('[data-testid="error-message"]')?.textContent?.trim();
  }

  it('shows the User ID required message without calling the API (FR-S01-01)', () => {
    component.userId = '';
    component.password = 'PASSWORD';
    component.submit();
    fixture.detectChanges();

    expect(errorText()).toBe(MSG_ENTER_USER_ID);
    httpMock.expectNone('/api/v1/auth/signin');
  });

  it('shows the Password required message without calling the API (FR-S01-02)', () => {
    component.userId = 'ADMIN001';
    component.password = '';
    component.submit();
    fixture.detectChanges();

    expect(errorText()).toBe(MSG_ENTER_PASSWORD);
    httpMock.expectNone('/api/v1/auth/signin');
  });

  it('routes to /admin on successful admin sign-in (FR-S01-05)', () => {
    component.userId = 'ADMIN001';
    component.password = 'PASSWORD';
    component.submit();

    httpMock.expectOne('/api/v1/auth/signin').flush({
      token: 't',
      userId: 'ADMIN001',
      userType: 'A',
      landingRoute: '/admin'
    });

    expect(router.navigateByUrl).toHaveBeenCalledWith('/admin');
  });

  it('routes to /menu on successful regular sign-in (FR-S01-06)', () => {
    component.userId = 'USER0001';
    component.password = 'PASSWORD';
    component.submit();

    httpMock.expectOne('/api/v1/auth/signin').flush({
      token: 't',
      userId: 'USER0001',
      userType: 'U',
      landingRoute: '/menu'
    });

    expect(router.navigateByUrl).toHaveBeenCalledWith('/menu');
  });

  it('shows the backend message and clears the password on auth failure (FR-S01-04)', () => {
    component.userId = 'USER0001';
    component.password = 'WRONGPWD';
    component.submit();

    httpMock
      .expectOne('/api/v1/auth/signin')
      .flush({ message: 'Wrong Password. Try again ...' }, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();

    expect(errorText()).toBe('Wrong Password. Try again ...');
    expect(component.password).toBe('');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('limits User ID and Password inputs to 8 characters like the BMS map', () => {
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[data-testid="user-id-input"]')?.getAttribute('maxlength')).toBe('8');
    const password = element.querySelector('[data-testid="password-input"]');
    expect(password?.getAttribute('maxlength')).toBe('8');
    expect(password?.getAttribute('type')).toBe('password');
  });

  it('shows the farewell message on Exit (FR-S01-08)', () => {
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('[data-testid="exit-button"]')?.click();
    fixture.detectChanges();

    const farewell = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="farewell-message"]');
    expect(farewell?.textContent?.trim()).toBe(MSG_THANK_YOU);
    expect((fixture.nativeElement as HTMLElement).querySelector('form')).toBeNull();
  });

  it('shows the invalid-key message for an unmapped function key (FR-S01-20)', () => {
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'F5' }));
    fixture.detectChanges();

    expect(errorText()).toBe(MSG_INVALID_KEY);
    httpMock.expectNone('/api/v1/auth/signin');
  });

  it('shows the farewell message on F3 like PF3 (FR-S01-08)', () => {
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'F3' }));
    fixture.detectChanges();

    const farewell = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="farewell-message"]');
    expect(farewell?.textContent?.trim()).toBe(MSG_THANK_YOU);
  });
});
