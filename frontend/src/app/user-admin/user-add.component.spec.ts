import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Router } from '@angular/router';
import { UserAddComponent } from './user-add.component';
import { UserAddRequest, UserAdminResponse } from './user-admin.service';
import { MSG_INVALID_KEY } from '../shared/invalid-key';

const ADD_URL = '/api/v1/admin/users/add';

function response(overrides: Partial<UserAdminResponse>): UserAdminResponse {
  return { outcome: 'success', message: null, severity: null, focusField: null, user: null, ...overrides };
}

describe('UserAddComponent', () => {
  let fixture: ComponentFixture<UserAddComponent>;
  let component: UserAddComponent;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UserAddComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()]
    }).compileComponents();

    fixture = TestBed.createComponent(UserAddComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl');
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  function el(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function input(testId: string): HTMLInputElement {
    return el().querySelector<HTMLInputElement>(`[data-testid="${testId}"]`)!;
  }

  function messageText(): string | undefined {
    return el().querySelector('[data-testid="screen-message"]')?.textContent?.trim();
  }

  function fill(): void {
    component.firstName = 'Ada';
    component.lastName = 'Lovelace';
    component.userId = 'ADA00001';
    component.password = 'PASSWORD';
    component.userType = 'U';
  }

  function pressKey(key: string): KeyboardEvent {
    const event = new KeyboardEvent('keydown', { key, cancelable: true });
    window.dispatchEvent(event);
    fixture.detectChanges();
    return event;
  }

  it('mirrors the COUSR1A field lengths (20/20/8/8/1) and dark password, message area 78', () => {
    expect(input('first-name-input').maxLength).toBe(20);
    expect(input('last-name-input').maxLength).toBe(20);
    expect(input('user-id-input').maxLength).toBe(8);
    expect(input('password-input').maxLength).toBe(8);
    expect(input('password-input').type).toBe('password');
    expect(input('user-type-input').maxLength).toBe(1);

    component.message = 'M'.repeat(90);
    fixture.detectChanges();
    expect(messageText()?.length).toBe(78);
  });

  it('ENTER posts all five fields as typed (FR-S12-17/18)', () => {
    fill();
    component.submit();

    const body = httpMock.expectOne(ADD_URL).request.body as UserAddRequest;
    expect(body).toEqual({ firstName: 'Ada', lastName: 'Lovelace', userId: 'ADA00001', password: 'PASSWORD', userType: 'U' });
  });

  it('shows the server validation message in red and puts the cursor on the failing field (FR-S12-17)', () => {
    component.lastName = 'Lovelace';
    component.submit();

    httpMock.expectOne(ADD_URL).flush(response({
      outcome: 'validationError',
      message: 'First Name can NOT be empty...',
      severity: 'error',
      focusField: 'firstName'
    }));
    fixture.detectChanges();

    expect(messageText()).toBe('First Name can NOT be empty...');
    expect(el().querySelector('[data-testid="screen-message"]')?.classList).toContain('error-message');
    expect(document.activeElement).toBe(input('first-name-input'));
    expect(component.lastName).toBe('Lovelace');
  });

  it('clears the fields and shows the green added message on success (FR-S12-18)', () => {
    fill();
    component.submit();

    httpMock.expectOne(ADD_URL).flush(response({
      outcome: 'success',
      message: 'User ADA00001 has been added ...',
      severity: 'success',
      focusField: 'firstName'
    }));
    fixture.detectChanges();

    expect(messageText()).toBe('User ADA00001 has been added ...');
    expect(el().querySelector('[data-testid="screen-message"]')?.classList).toContain('success-message');
    expect(component.firstName).toBe('');
    expect(component.lastName).toBe('');
    expect(component.userId).toBe('');
    expect(component.password).toBe('');
    expect(component.userType).toBe('');
  });

  it('retains the fields and shows the duplicate message (FR-S12-19)', () => {
    fill();
    component.submit();

    httpMock.expectOne(ADD_URL).flush(response({
      outcome: 'duplicate',
      message: 'User ID already exist...',
      severity: 'error',
      focusField: 'firstName'
    }));
    fixture.detectChanges();

    expect(messageText()).toBe('User ID already exist...');
    expect(component.userId).toBe('ADA00001');
    expect(component.password).toBe('PASSWORD');
  });

  it('shows the add-error message on any other write failure (FR-S12-20)', () => {
    fill();
    component.submit();

    httpMock.expectOne(ADD_URL).flush(response({
      outcome: 'storeError',
      message: 'Unable to Add User...',
      severity: 'error',
      focusField: 'firstName'
    }));
    fixture.detectChanges();

    expect(messageText()).toBe('Unable to Add User...');
    expect(component.userId).toBe('ADA00001');
  });

  it('F3 returns to the admin menu without posting (FR-S12-21)', () => {
    fill();
    const event = pressKey('F3');

    expect(event.defaultPrevented).toBeTrue();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/admin');
    httpMock.expectNone(ADD_URL);
  });

  it('F4 clears every field and the message and puts the cursor on First Name (FR-S12-22)', () => {
    fill();
    component.message = 'User ID already exist...';
    component.messageSeverity = 'error';
    fixture.detectChanges();

    pressKey('F4');

    expect(component.firstName).toBe('');
    expect(component.lastName).toBe('');
    expect(component.userId).toBe('');
    expect(component.password).toBe('');
    expect(component.userType).toBe('');
    expect(messageText()).toBe('');
    expect(document.activeElement).toBe(input('first-name-input'));
    httpMock.expectNone(ADD_URL);
  });

  it('Clear button behaves like PF4 (FR-S12-22)', () => {
    fill();
    el().querySelector<HTMLButtonElement>('[data-testid="clear-button"]')?.click();
    fixture.detectChanges();

    expect(component.userId).toBe('');
    httpMock.expectNone(ADD_URL);
  });

  it('PF12 and every other function key show the invalid-key message (FR-S12-23)', () => {
    for (const key of ['F1', 'F2', 'F5', 'F6', 'F7', 'F8', 'F9', 'F10', 'F11', 'F12']) {
      component.message = '';
      pressKey(key);
      expect(messageText()).withContext(key).toBe(MSG_INVALID_KEY);
    }
    expect(router.navigateByUrl).not.toHaveBeenCalled();
    httpMock.expectNone(ADD_URL);
  });
});
