import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { UserUpdateComponent } from './user-update.component';
import { UserAdminResponse, UserUpdateRequest } from './user-admin.service';
import { MSG_INVALID_KEY } from '../shared/invalid-key';

const FETCH_URL = '/api/v1/admin/users/update/fetch';
const UPDATE_URL = '/api/v1/admin/users/update';

function response(overrides: Partial<UserAdminResponse>): UserAdminResponse {
  return { outcome: 'success', message: null, severity: null, focusField: null, user: null, ...overrides };
}

const FOUND = response({
  outcome: 'success',
  message: 'Press PF5 key to save your updates ...',
  severity: 'neutral',
  focusField: 'firstName',
  user: { userId: 'ADA00001', firstName: 'Ada', lastName: 'Lovelace', userType: 'U' }
});

describe('UserUpdateComponent', () => {
  let fixture: ComponentFixture<UserUpdateComponent>;
  let component: UserUpdateComponent;
  let httpMock: HttpTestingController;
  let router: Router;

  async function setup(queryParams: Record<string, string>): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [UserUpdateComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(UserUpdateComponent);
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

    it('does not fetch on entry without a selected user and mirrors the COUSR2A field lengths', () => {
      httpMock.expectNone(FETCH_URL);
      expect(input('user-id-input').maxLength).toBe(8);
      expect(input('first-name-input').maxLength).toBe(20);
      expect(input('last-name-input').maxLength).toBe(20);
      expect(input('password-input').maxLength).toBe(8);
      expect(input('password-input').type).toBe('password');
      expect(input('user-type-input').maxLength).toBe(1);

      component.message = 'M'.repeat(90);
      fixture.detectChanges();
      expect(messageText()?.length).toBe(78);
    });

    it('ENTER with a blank User ID shows the empty-id message with the cursor on User ID (FR-S12-25)', () => {
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

    it('ENTER with a found user populates names/type, leaves the password blank, neutral PF5 prompt (FR-S12-26, S12-B2)', () => {
      component.password = 'stale';
      loadUser();

      expect(component.firstName).toBe('Ada');
      expect(component.lastName).toBe('Lovelace');
      expect(component.userType).toBe('U');
      expect(component.password).toBe('');
      expect(messageText()).toBe('Press PF5 key to save your updates ...');
      expect(el().querySelector('[data-testid="screen-message"]')?.classList).toContain('neutral-message');
    });

    it('ENTER with an unknown user shows the not-found message (FR-S12-27)', () => {
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
      expect(component.firstName).toBe('');
    });

    it('ENTER with a lookup failure shows the lookup-error message (FR-S12-28)', () => {
      component.userId = 'ADA00001';
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

    it('F5 posts the edited fields and stays on the screen (FR-S12-29, FR-S12-34)', () => {
      loadUser();
      component.lastName = 'Byron';
      component.password = 'NEWPASS1';
      pressKey('F5');

      const request = httpMock.expectOne(UPDATE_URL);
      expect(request.request.body as UserUpdateRequest).toEqual({
        userId: 'ADA00001',
        firstName: 'Ada',
        lastName: 'Byron',
        password: 'NEWPASS1',
        userType: 'U'
      });
      request.flush(response({
        outcome: 'success',
        message: 'User ADA00001 has been updated ...',
        severity: 'success',
        focusField: 'firstName'
      }));
      fixture.detectChanges();

      expect(messageText()).toBe('User ADA00001 has been updated ...');
      expect(el().querySelector('[data-testid="screen-message"]')?.classList).toContain('success-message');
      expect(component.lastName).toBe('Byron');
      expect(router.navigateByUrl).not.toHaveBeenCalled();
    });

    it('F5 validation failure shows the message and focuses the failing field (FR-S12-29)', () => {
      loadUser();
      component.password = '';
      component.save();

      httpMock.expectOne(UPDATE_URL).flush(response({
        outcome: 'validationError',
        message: 'Password can NOT be empty...',
        severity: 'error',
        focusField: 'password'
      }));
      fixture.detectChanges();

      expect(messageText()).toBe('Password can NOT be empty...');
      expect(document.activeElement).toBe(input('password-input'));
    });

    it('F5 with nothing changed shows the please-modify message (FR-S12-30)', () => {
      loadUser();
      component.password = 'PASSWORD';
      component.save();

      httpMock.expectOne(UPDATE_URL).flush(response({
        outcome: 'noChange',
        message: 'Please modify to update ...',
        severity: 'error',
        focusField: 'firstName'
      }));
      fixture.detectChanges();

      expect(messageText()).toBe('Please modify to update ...');
      expect(el().querySelector('[data-testid="screen-message"]')?.classList).toContain('error-message');
    });

    it('F5 rewrite failures show the not-found / update-error messages (FR-S12-32)', () => {
      loadUser();
      component.firstName = 'Augusta';
      component.save();
      httpMock.expectOne(UPDATE_URL).flush(response({
        outcome: 'notFound',
        message: 'User ID NOT found...',
        severity: 'error',
        focusField: 'userId'
      }));
      fixture.detectChanges();
      expect(messageText()).toBe('User ID NOT found...');

      component.save();
      httpMock.expectOne(UPDATE_URL).flush(response({
        outcome: 'storeError',
        message: 'Unable to Update User...',
        severity: 'error',
        focusField: 'firstName'
      }));
      fixture.detectChanges();
      expect(messageText()).toBe('Unable to Update User...');
    });

    it('F3 attempts the save and then returns to the admin menu when there is no caller (FR-S12-33)', () => {
      loadUser();
      component.firstName = 'Augusta';
      const event = pressKey('F3');

      expect(event.defaultPrevented).toBeTrue();
      const request = httpMock.expectOne(UPDATE_URL);
      expect((request.request.body as UserUpdateRequest).firstName).toBe('Augusta');
      request.flush(response({
        outcome: 'noChange',
        message: 'Please modify to update ...',
        severity: 'error',
        focusField: 'firstName'
      }));

      expect(router.navigateByUrl).toHaveBeenCalledWith('/admin');
    });

    it('F3 still returns when the save call fails (FR-S12-33)', () => {
      loadUser();
      pressKey('F3');

      httpMock.expectOne(UPDATE_URL).flush('boom', { status: 500, statusText: 'Server Error' });

      expect(router.navigateByUrl).toHaveBeenCalledWith('/admin');
    });

    it('F12 returns to the admin menu without saving (FR-S12-35)', () => {
      loadUser();
      component.firstName = 'Augusta';
      pressKey('F12');

      httpMock.expectNone(UPDATE_URL);
      expect(router.navigateByUrl).toHaveBeenCalledWith('/admin');
    });

    it('F4 clears every field and the message and puts the cursor on User ID (FR-S12-36)', () => {
      loadUser();
      component.password = 'x';
      pressKey('F4');

      expect(component.userId).toBe('');
      expect(component.firstName).toBe('');
      expect(component.lastName).toBe('');
      expect(component.password).toBe('');
      expect(component.userType).toBe('');
      expect(messageText()).toBe('');
      expect(document.activeElement).toBe(input('user-id-input'));
    });

    it('other function keys show the invalid-key message (FR-S12-36)', () => {
      for (const key of ['F1', 'F2', 'F6', 'F7', 'F8', 'F9', 'F10', 'F11']) {
        component.message = '';
        pressKey(key);
        expect(messageText()).withContext(key).toBe(MSG_INVALID_KEY);
      }
      httpMock.expectNone(UPDATE_URL);
      expect(router.navigateByUrl).not.toHaveBeenCalled();
    });
  });

  describe('entry from the user list (CDEMO-CU02-USR-SELECTED)', () => {
    beforeEach(async () => setup({ userId: 'ADA00001', from: 'COUSR00C' }));

    it('fetches the selected user immediately as if ENTER were pressed (FR-S12-24)', () => {
      const request = httpMock.expectOne(FETCH_URL);
      expect(request.request.body).toEqual({ userId: 'ADA00001' });
      request.flush(FOUND);
      fixture.detectChanges();

      expect(component.userId).toBe('ADA00001');
      expect(component.firstName).toBe('Ada');
      expect(messageText()).toBe('Press PF5 key to save your updates ...');
    });

    it('F3 saves and returns to the user list, the CDEMO-FROM-PROGRAM caller (FR-S12-33, S12-B4)', () => {
      httpMock.expectOne(FETCH_URL).flush(FOUND);
      fixture.detectChanges();

      pressKey('F3');
      httpMock.expectOne(UPDATE_URL).flush(response({
        outcome: 'success',
        message: 'User ADA00001 has been updated ...',
        severity: 'success',
        focusField: 'firstName'
      }));

      expect(router.navigateByUrl).toHaveBeenCalledWith('/admin/users');
    });

    it('F12 returns to the admin menu even when called from the list (FR-S12-35)', () => {
      httpMock.expectOne(FETCH_URL).flush(FOUND);
      fixture.detectChanges();

      pressKey('F12');

      expect(router.navigateByUrl).toHaveBeenCalledWith('/admin');
    });
  });
});
