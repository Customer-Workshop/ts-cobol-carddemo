import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { UserDeleteComponent } from './user-delete.component';
import { UserAdminResponse } from './user-admin.service';
import { MSG_INVALID_KEY } from '../shared/invalid-key';

const FETCH_URL = '/api/v1/admin/users/delete/fetch';
const DELETE_URL = '/api/v1/admin/users/delete';

function response(overrides: Partial<UserAdminResponse>): UserAdminResponse {
  return { outcome: 'success', message: null, severity: null, focusField: null, user: null, ...overrides };
}

const FOUND = response({
  outcome: 'success',
  message: 'Press PF5 key to delete this user ...',
  severity: 'neutral',
  focusField: 'userId',
  user: { userId: 'ADA00001', firstName: 'Ada', lastName: 'Lovelace', userType: 'U' }
});

describe('UserDeleteComponent', () => {
  let fixture: ComponentFixture<UserDeleteComponent>;
  let component: UserDeleteComponent;
  let httpMock: HttpTestingController;
  let router: Router;

  async function setup(queryParams: Record<string, string>): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [UserDeleteComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(UserDeleteComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl');
    fixture.detectChanges();
  }

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

  function pressKey(key: string): KeyboardEvent {
    const event = new KeyboardEvent('keydown', { key, cancelable: true });
    window.dispatchEvent(event);
    fixture.detectChanges();
    return event;
  }

  function loadUser(): void {
    component.userId = 'ADA00001';
    component.fetch();
    httpMock.expectOne(FETCH_URL).flush(FOUND);
    fixture.detectChanges();
  }

  describe('direct entry (from the admin menu)', () => {
    beforeEach(async () => setup({}));

    it('does not fetch on entry and mirrors COUSR3A: User ID 8 input, names/type display-only, message 78', () => {
      httpMock.expectNone(FETCH_URL);
      expect(input('user-id-input').maxLength).toBe(8);
      expect(input('user-id-input').readOnly).toBeFalse();
      expect(input('first-name-input').maxLength).toBe(20);
      expect(input('first-name-input').readOnly).toBeTrue();
      expect(input('last-name-input').maxLength).toBe(20);
      expect(input('last-name-input').readOnly).toBeTrue();
      expect(input('user-type-input').maxLength).toBe(1);
      expect(input('user-type-input').readOnly).toBeTrue();
      expect(el().querySelector('[data-testid="password-input"]')).toBeNull();

      component.message = 'M'.repeat(90);
      fixture.detectChanges();
      expect(messageText()?.length).toBe(78);
    });

    it('ENTER with a blank User ID shows the empty-id message (FR-S12-38)', () => {
      component.fetch();

      const request = httpMock.expectOne(FETCH_URL);
      expect(request.request.body).toEqual({ userId: '' });
      request.flush(response({
        outcome: 'validationError',
        message: 'User ID can NOT be empty...',
        severity: 'error',
        focusField: 'userId'
      }));
      fixture.detectChanges();

      expect(messageText()).toBe('User ID can NOT be empty...');
      expect(document.activeElement).toBe(input('user-id-input'));
    });

    it('ENTER with a found user shows names/type and the neutral PF5 prompt (FR-S12-38)', () => {
      loadUser();

      expect(component.firstName).toBe('Ada');
      expect(component.lastName).toBe('Lovelace');
      expect(component.userType).toBe('U');
      expect(messageText()).toBe('Press PF5 key to delete this user ...');
      expect(el().querySelector('[data-testid="screen-message"]')?.classList).toContain('neutral-message');
    });

    it('ENTER not-found / lookup-error messages (FR-S12-38)', () => {
      component.userId = 'NOBODY';
      component.fetch();
      httpMock.expectOne(FETCH_URL).flush(response({
        outcome: 'notFound',
        message: 'User ID NOT found...',
        severity: 'error',
        focusField: 'userId'
      }));
      fixture.detectChanges();
      expect(messageText()).toBe('User ID NOT found...');

      component.fetch();
      httpMock.expectOne(FETCH_URL).flush(response({
        outcome: 'storeError',
        message: 'Unable to lookup User...',
        severity: 'error',
        focusField: 'firstName'
      }));
      fixture.detectChanges();
      expect(messageText()).toBe('Unable to lookup User...');
    });

    it('F5 with a blank User ID shows the empty-id message (FR-S12-39)', () => {
      pressKey('F5');

      const request = httpMock.expectOne(DELETE_URL);
      expect(request.request.body).toEqual({ userId: '' });
      request.flush(response({
        outcome: 'validationError',
        message: 'User ID can NOT be empty...',
        severity: 'error',
        focusField: 'userId'
      }));
      fixture.detectChanges();

      expect(messageText()).toBe('User ID can NOT be empty...');
    });

    it('F5 deletes the user, clears the fields and shows the green deleted message (FR-S12-39)', () => {
      loadUser();
      const event = pressKey('F5');

      expect(event.defaultPrevented).toBeTrue();
      const request = httpMock.expectOne(DELETE_URL);
      expect(request.request.body).toEqual({ userId: 'ADA00001' });
      request.flush(response({
        outcome: 'success',
        message: 'User ADA00001 has been deleted ...',
        severity: 'success',
        focusField: 'userId'
      }));
      fixture.detectChanges();

      expect(messageText()).toBe('User ADA00001 has been deleted ...');
      expect(el().querySelector('[data-testid="screen-message"]')?.classList).toContain('success-message');
      expect(component.userId).toBe('');
      expect(component.firstName).toBe('');
      expect(component.lastName).toBe('');
      expect(component.userType).toBe('');
      expect(router.navigateByUrl).not.toHaveBeenCalled();
    });

    it('F5 not-found and other delete errors keep the fields and show the source messages (FR-S12-39)', () => {
      loadUser();
      component.remove();
      httpMock.expectOne(DELETE_URL).flush(response({
        outcome: 'notFound',
        message: 'User ID NOT found...',
        severity: 'error',
        focusField: 'userId'
      }));
      fixture.detectChanges();
      expect(messageText()).toBe('User ID NOT found...');
      expect(component.userId).toBe('ADA00001');

      component.remove();
      httpMock.expectOne(DELETE_URL).flush(response({
        outcome: 'storeError',
        message: 'Unable to Update User...',
        severity: 'error',
        focusField: 'firstName'
      }));
      fixture.detectChanges();
      expect(messageText()).toBe('Unable to Update User...');
      expect(component.firstName).toBe('Ada');
    });

    it('Delete button behaves like PF5 (FR-S12-39)', () => {
      loadUser();
      el().querySelector<HTMLButtonElement>('[data-testid="delete-button"]')?.click();

      expect(httpMock.expectOne(DELETE_URL).request.body).toEqual({ userId: 'ADA00001' });
    });

    it('F3 without a caller returns to the admin menu without deleting (FR-S12-40)', () => {
      loadUser();
      pressKey('F3');

      httpMock.expectNone(DELETE_URL);
      expect(router.navigateByUrl).toHaveBeenCalledWith('/admin');
    });

    it('F12 returns to the admin menu (FR-S12-40)', () => {
      loadUser();
      pressKey('F12');

      httpMock.expectNone(DELETE_URL);
      expect(router.navigateByUrl).toHaveBeenCalledWith('/admin');
    });

    it('F4 clears the fields and the message and puts the cursor on User ID (FR-S12-40)', () => {
      loadUser();
      pressKey('F4');

      expect(component.userId).toBe('');
      expect(component.firstName).toBe('');
      expect(component.lastName).toBe('');
      expect(component.userType).toBe('');
      expect(messageText()).toBe('');
      expect(document.activeElement).toBe(input('user-id-input'));
    });

    it('other function keys show the invalid-key message (FR-S12-40)', () => {
      for (const key of ['F1', 'F2', 'F6', 'F7', 'F8', 'F9', 'F10', 'F11']) {
        component.message = '';
        pressKey(key);
        expect(messageText()).withContext(key).toBe(MSG_INVALID_KEY);
      }
      httpMock.expectNone(DELETE_URL);
      expect(router.navigateByUrl).not.toHaveBeenCalled();
    });
  });

  describe('entry from the user list (CDEMO-CU03-USR-SELECTED)', () => {
    beforeEach(async () => setup({ userId: 'ADA00001', from: 'COUSR00C' }));

    it('fetches the selected user immediately as if ENTER were pressed (FR-S12-37)', () => {
      const request = httpMock.expectOne(FETCH_URL);
      expect(request.request.body).toEqual({ userId: 'ADA00001' });
      request.flush(FOUND);
      fixture.detectChanges();

      expect(component.firstName).toBe('Ada');
      expect(messageText()).toBe('Press PF5 key to delete this user ...');
    });

    it('F3 returns to the user list, the CDEMO-FROM-PROGRAM caller (FR-S12-40, S12-B4)', () => {
      httpMock.expectOne(FETCH_URL).flush(FOUND);
      fixture.detectChanges();

      pressKey('F3');

      expect(router.navigateByUrl).toHaveBeenCalledWith('/admin/users');
    });

    it('F12 returns to the admin menu even when called from the list (FR-S12-40)', () => {
      httpMock.expectOne(FETCH_URL).flush(FOUND);
      fixture.detectChanges();

      pressKey('F12');

      expect(router.navigateByUrl).toHaveBeenCalledWith('/admin');
    });
  });
});
