import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Router } from '@angular/router';
import { UserListComponent } from './user-list.component';
import { UserListRequest, UserListResponse, UserListRow } from './user-admin.service';
import { MSG_INVALID_KEY } from '../shared/invalid-key';

const LIST_URL = '/api/v1/admin/users/list';

function row(userId: string, userType = 'U'): UserListRow {
  return { userId, firstName: `F-${userId}`, lastName: `L-${userId}`, userType };
}

function page(rows: UserListRow[], overrides: Partial<UserListResponse> = {}): UserListResponse {
  return {
    rows,
    pageNum: 1,
    nextPage: true,
    firstUserId: rows[0]?.userId ?? null,
    lastUserId: rows[rows.length - 1]?.userId ?? null,
    searchUserId: null,
    message: null,
    severity: null,
    navigate: null,
    ...overrides
  };
}

const FIRST_PAGE = page(Array.from({ length: 10 }, (_, i) => row(`USER00${i}`)));

describe('UserListComponent', () => {
  let fixture: ComponentFixture<UserListComponent>;
  let component: UserListComponent;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UserListComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()]
    }).compileComponents();

    fixture = TestBed.createComponent(UserListComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl');
    spyOn(router, 'navigate');
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  function el(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function messageText(): string | undefined {
    return el().querySelector('[data-testid="screen-message"]')?.textContent?.trim();
  }

  function flushInitial(response: UserListResponse = FIRST_PAGE): UserListRequest {
    const request = httpMock.expectOne(LIST_URL);
    request.flush(response);
    fixture.detectChanges();
    return request.request.body as UserListRequest;
  }

  function pressKey(key: string): KeyboardEvent {
    const event = new KeyboardEvent('keydown', { key, cancelable: true });
    window.dispatchEvent(event);
    fixture.detectChanges();
    return event;
  }

  it('requests the first page on entry with an ENTER action from low-values (FR-S12-01)', () => {
    const body = flushInitial();

    expect(body.action).toBe('enter');
    expect(body.searchUserId).toBe('');
    expect(body.pageNum).toBe(0);
    expect(body.selections).toEqual([]);
    expect(el().querySelectorAll('[data-testid="user-row"]').length).toBe(10);
    expect(el().querySelector('[data-testid="page-number"]')?.textContent?.trim()).toContain('1');
    expect(el().querySelectorAll('[data-testid="row-user-id"]')[0].textContent?.trim()).toBe('USER000');
  });

  it('mirrors the BMS field lengths: search 8, SEL 1, message area 78', () => {
    flushInitial();

    const search = el().querySelector<HTMLInputElement>('[data-testid="search-input"]');
    const sel = el().querySelector<HTMLInputElement>('[data-testid="select-input"]');
    expect(search?.maxLength).toBe(8);
    expect(sel?.maxLength).toBe(1);

    component.message = 'X'.repeat(100);
    fixture.detectChanges();
    expect(messageText()?.length).toBe(78);
  });

  it('ENTER with a search key re-reads from that key and echoes the cleared search field (FR-S12-02)', () => {
    flushInitial();
    component.searchUserId = 'USER005';
    component.submit();

    const request = httpMock.expectOne(LIST_URL);
    expect((request.request.body as UserListRequest).searchUserId).toBe('USER005');
    expect((request.request.body as UserListRequest).action).toBe('enter');
    request.flush(page([row('USER005'), row('USER006')], { nextPage: false, message: 'You have reached the bottom of the page...', severity: 'error' }));
    fixture.detectChanges();

    expect(component.searchUserId).toBe('');
    expect(el().querySelectorAll('[data-testid="user-row"]').length).toBe(2);
    expect(messageText()).toBe('You have reached the bottom of the page...');
  });

  it('sends every row selection in screen order so the server honours the first non-blank one (FR-S12-06)', () => {
    flushInitial();
    component.rows[3].select = 'D';
    component.rows[1].select = 'U';
    component.submit();

    const body = httpMock.expectOne(LIST_URL).request.body as UserListRequest;
    expect(body.selections.length).toBe(10);
    expect(body.selections[1]).toEqual({ userId: 'USER001', flag: 'U' });
    expect(body.selections[3]).toEqual({ userId: 'USER003', flag: 'D' });
    httpMock.expectNone(LIST_URL);
  });

  it('navigates to the update screen with the selected user and FROM-PROGRAM COUSR00C on U (FR-S12-03)', () => {
    flushInitial();
    component.rows[2].select = 'u';
    component.submit();

    httpMock.expectOne(LIST_URL).flush(page([], { navigate: { programKey: 'COUSR02C', userId: 'USER002' } }));
    fixture.detectChanges();

    expect(router.navigate).toHaveBeenCalledWith(['/admin/users/update'], {
      queryParams: { userId: 'USER002', from: 'COUSR00C' }
    });
    expect(el().querySelectorAll('[data-testid="user-row"]').length).toBe(10);
  });

  it('navigates to the delete screen with the selected user on D (FR-S12-04)', () => {
    flushInitial();
    component.rows[0].select = 'D';
    component.submit();

    httpMock.expectOne(LIST_URL).flush(page([], { navigate: { programKey: 'COUSR03C', userId: 'USER000' } }));
    fixture.detectChanges();

    expect(router.navigate).toHaveBeenCalledWith(['/admin/users/delete'], {
      queryParams: { userId: 'USER000', from: 'COUSR00C' }
    });
  });

  it('shows the invalid-selection message and re-renders the refreshed page 1 (FR-S12-05)', () => {
    flushInitial();
    component.rows[0].select = 'X';
    component.submit();

    httpMock.expectOne(LIST_URL).flush(page(FIRST_PAGE.rows, { message: 'Invalid selection. Valid values are U and D', severity: 'error' }));
    fixture.detectChanges();

    expect(messageText()).toBe('Invalid selection. Valid values are U and D');
    expect(el().querySelector('[data-testid="screen-message"]')?.classList).toContain('error-message');
    expect(router.navigate).not.toHaveBeenCalled();
    expect(component.rows.every((r) => r.select === '')).toBeTrue();
  });

  it('F8 sends pageForward with the COMMAREA state and renders the next page (FR-S12-07)', () => {
    flushInitial();
    pressKey('F8');

    const request = httpMock.expectOne(LIST_URL);
    const body = request.request.body as UserListRequest;
    expect(body.action).toBe('pageForward');
    expect(body.pageNum).toBe(1);
    expect(body.nextPage).toBeTrue();
    expect(body.firstUserId).toBe('USER000');
    expect(body.lastUserId).toBe('USER009');

    const second = Array.from({ length: 10 }, (_, i) => row(`USER01${i}`));
    request.flush(page(second, { pageNum: 2 }));
    fixture.detectChanges();

    expect(component.pageNum).toBe(2);
    expect(el().querySelectorAll('[data-testid="row-user-id"]')[0].textContent?.trim()).toBe('USER010');
  });

  it('F8 at the bottom keeps the rows and shows the already-at-bottom message (FR-S12-08)', () => {
    flushInitial();
    pressKey('F8');

    httpMock.expectOne(LIST_URL).flush(page([], {
      firstUserId: 'USER000',
      lastUserId: 'USER009',
      message: 'You are already at the bottom of the page...',
      severity: 'error'
    }));
    fixture.detectChanges();

    expect(messageText()).toBe('You are already at the bottom of the page...');
    expect(el().querySelectorAll('[data-testid="user-row"]').length).toBe(10);
    expect(component.pageNum).toBe(1);
  });

  it('shows the reached-bottom message on a short forward page (FR-S12-09)', () => {
    flushInitial();
    component.pageForward();

    httpMock.expectOne(LIST_URL).flush(page([row('USER010'), row('USER011')], {
      pageNum: 2,
      nextPage: false,
      message: 'You have reached the bottom of the page...',
      severity: 'error'
    }));
    fixture.detectChanges();

    expect(messageText()).toBe('You have reached the bottom of the page...');
    expect(el().querySelectorAll('[data-testid="user-row"]').length).toBe(2);
    expect(component.nextPage).toBeFalse();
  });

  it('F7 sends pageBackward and renders the previous page (FR-S12-10)', () => {
    flushInitial(page(FIRST_PAGE.rows, { pageNum: 2 }));
    pressKey('F7');

    const request = httpMock.expectOne(LIST_URL);
    expect((request.request.body as UserListRequest).action).toBe('pageBackward');
    expect((request.request.body as UserListRequest).pageNum).toBe(2);
    request.flush(page(FIRST_PAGE.rows, { pageNum: 1 }));
    fixture.detectChanges();

    expect(component.pageNum).toBe(1);
  });

  it('F7 on page 1 keeps the rows and shows the already-at-top message (FR-S12-11)', () => {
    flushInitial();
    pressKey('F7');

    httpMock.expectOne(LIST_URL).flush(page([], {
      firstUserId: 'USER000',
      lastUserId: 'USER009',
      message: 'You are already at the top of the page...',
      severity: 'error'
    }));
    fixture.detectChanges();

    expect(messageText()).toBe('You are already at the top of the page...');
    expect(el().querySelectorAll('[data-testid="user-row"]').length).toBe(10);
  });

  it('renders the reached-top message and page 1 when the backward read hits start of file (FR-S12-12)', () => {
    flushInitial(page(FIRST_PAGE.rows, { pageNum: 2 }));
    component.pageBackward();

    httpMock.expectOne(LIST_URL).flush(page(FIRST_PAGE.rows, {
      pageNum: 1,
      message: 'You have reached the top of the page...',
      severity: 'error'
    }));
    fixture.detectChanges();

    expect(messageText()).toBe('You have reached the top of the page...');
    expect(component.pageNum).toBe(1);
  });

  it('renders no rows and the at-top message when the search key is beyond the last user (FR-S12-13)', () => {
    flushInitial();
    component.searchUserId = 'ZZZZZZZZ';
    component.submit();

    httpMock.expectOne(LIST_URL).flush(page([], {
      pageNum: 0,
      nextPage: false,
      message: 'You are at the top of the page...',
      severity: 'error'
    }));
    fixture.detectChanges();

    expect(messageText()).toBe('You are at the top of the page...');
    expect(el().querySelectorAll('[data-testid="user-row"]').length).toBe(0);
    expect(component.pageNum).toBe(0);
  });

  it('keeps the current rows when the lookup fails (FR-S12-14)', () => {
    flushInitial();
    component.pageForward();

    httpMock.expectOne(LIST_URL).flush(page([], {
      firstUserId: 'USER000',
      lastUserId: 'USER009',
      message: 'Unable to lookup User...',
      severity: 'error'
    }));
    fixture.detectChanges();

    expect(messageText()).toBe('Unable to lookup User...');
    expect(el().querySelectorAll('[data-testid="user-row"]').length).toBe(10);
  });

  it('F3 returns to the admin menu (FR-S12-15)', () => {
    flushInitial();
    const event = pressKey('F3');

    expect(event.defaultPrevented).toBeTrue();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/admin');
    httpMock.expectNone(LIST_URL);
  });

  it('Exit button returns to the admin menu (FR-S12-15)', () => {
    flushInitial();
    el().querySelector<HTMLButtonElement>('[data-testid="exit-button"]')?.click();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/admin');
  });

  it('other function keys show the invalid-key message without a request (FR-S12-16)', () => {
    flushInitial();
    for (const key of ['F1', 'F2', 'F4', 'F5', 'F6', 'F9', 'F10', 'F11', 'F12']) {
      component.message = '';
      pressKey(key);
      expect(messageText()).withContext(key).toBe(MSG_INVALID_KEY);
    }
    httpMock.expectNone(LIST_URL);
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('ordinary typing is not treated as an AID', () => {
    flushInitial();
    pressKey('a');
    pressKey('Enter');

    expect(messageText()).toBe('');
    httpMock.expectNone(LIST_URL);
  });
});
