import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Router } from '@angular/router';
import { AdminMenuComponent } from './admin-menu.component';
import { MSG_INVALID_KEY } from '../shared/invalid-key';

const ADMIN_MENU_RESPONSE = {
  menu: 'admin',
  options: [
    { id: '01', name: 'User List (Security)', enabled: false },
    { id: '02', name: 'User Add (Security)', enabled: false },
    { id: '03', name: 'User Update (Security)', enabled: false },
    { id: '04', name: 'User Delete (Security)', enabled: false },
    { id: '05', name: 'Transaction Type List/Update (Db2)', enabled: false },
    { id: '06', name: 'Transaction Type Maintenance (Db2)', enabled: false }
  ]
};

describe('AdminMenuComponent', () => {
  let fixture: ComponentFixture<AdminMenuComponent>;
  let component: AdminMenuComponent;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminMenuComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()]
    }).compileComponents();

    fixture = TestBed.createComponent(AdminMenuComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl');
    sessionStorage.clear();
    fixture.detectChanges();
    httpMock.expectOne('/api/v1/menu?menu=admin').flush(ADMIN_MENU_RESPONSE);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
    sessionStorage.clear();
  });

  function messageText(): string | undefined {
    return (fixture.nativeElement as HTMLElement).querySelector('[data-testid="menu-message"]')?.textContent?.trim();
  }

  it('renders the 6 admin catalogue options in order (FR-S01-17)', () => {
    const rows = (fixture.nativeElement as HTMLElement).querySelectorAll('[data-testid="menu-option"]');
    expect(rows.length).toBe(6);
    expect(rows[0].textContent?.trim()).toBe('01. User List (Security)');
    expect(rows[5].textContent?.trim()).toBe('06. Transaction Type Maintenance (Db2)');
  });

  it('shows the valid-option message for an invalid option (FR-S01-18)', () => {
    component.option = '7';
    component.submit();

    httpMock.expectOne('/api/v1/menu/select').flush({
      outcome: 'invalidOption',
      message: 'Please enter a valid option number...',
      severity: 'error',
      target: null
    });
    fixture.detectChanges();

    expect(messageText()).toBe('Please enter a valid option number...');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('shows the coming-soon message for an unmigrated admin option', () => {
    component.option = '1';
    component.submit();

    const request = httpMock.expectOne('/api/v1/menu/select');
    expect(request.request.body).toEqual({ menu: 'admin', option: '1' });
    request.flush({
      outcome: 'comingSoon',
      message: 'This option User List (Security) is coming soon ...',
      severity: 'info',
      target: null
    });
    fixture.detectChanges();

    expect(messageText()).toBe('This option User List (Security) is coming soon ...');
  });

  it('navigates to the target route for an enabled admin option (FR-S01-19)', () => {
    component.option = '1';
    component.submit();

    httpMock.expectOne('/api/v1/menu/select').flush({
      outcome: 'navigate',
      message: null,
      severity: null,
      target: { id: '01', name: 'User List (Security)', programKey: 'COUSR00C', route: '/admin/users' }
    });

    expect(router.navigateByUrl).toHaveBeenCalledWith('/admin/users');
  });

  it('returns to the sign-on screen and clears the session on Exit (FR-S01-16)', () => {
    sessionStorage.setItem('carddemo.token', 't');

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('[data-testid="exit-button"]')?.click();

    expect(sessionStorage.getItem('carddemo.token')).toBeNull();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/signin');
  });

  it('shows the invalid-key message for an unmapped function key (FR-S01-20)', () => {
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'F12' }));
    fixture.detectChanges();

    expect(messageText()).toBe(MSG_INVALID_KEY);
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('returns to the sign-on screen on F3 like PF3 (FR-S01-16)', () => {
    sessionStorage.setItem('carddemo.token', 't');

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'F3' }));

    expect(sessionStorage.getItem('carddemo.token')).toBeNull();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/signin');
  });
});
