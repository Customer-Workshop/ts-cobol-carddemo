import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Router } from '@angular/router';
import { MainMenuComponent } from './main-menu.component';
import { MSG_INVALID_KEY } from '../shared/invalid-key';

const MAIN_MENU_RESPONSE = {
  menu: 'main',
  options: [
    { id: '01', name: 'Account View', enabled: false },
    { id: '02', name: 'Account Update', enabled: false },
    { id: '03', name: 'Credit Card List', enabled: false },
    { id: '04', name: 'Credit Card View', enabled: false },
    { id: '05', name: 'Credit Card Update', enabled: false },
    { id: '06', name: 'Transaction List', enabled: false },
    { id: '07', name: 'Transaction View', enabled: false },
    { id: '08', name: 'Transaction Add', enabled: false },
    { id: '09', name: 'Transaction Reports', enabled: false },
    { id: '10', name: 'Bill Payment', enabled: false },
    { id: '11', name: 'Pending Authorization View', enabled: false }
  ]
};

describe('MainMenuComponent', () => {
  let fixture: ComponentFixture<MainMenuComponent>;
  let component: MainMenuComponent;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MainMenuComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()]
    }).compileComponents();

    fixture = TestBed.createComponent(MainMenuComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl');
    sessionStorage.clear();
    fixture.detectChanges();
    httpMock.expectOne('/api/v1/menu?menu=main').flush(MAIN_MENU_RESPONSE);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
    sessionStorage.clear();
  });

  function messageText(): string | undefined {
    return (fixture.nativeElement as HTMLElement).querySelector('[data-testid="menu-message"]')?.textContent?.trim();
  }

  it('renders the 11 catalogue options in order (FR-S01-10)', () => {
    const rows = (fixture.nativeElement as HTMLElement).querySelectorAll('[data-testid="menu-option"]');
    expect(rows.length).toBe(11);
    expect(rows[0].textContent?.trim()).toBe('01. Account View');
    expect(rows[10].textContent?.trim()).toBe('11. Pending Authorization View');
  });

  it('shows the valid-option message for an invalid option (FR-S01-11)', () => {
    component.option = '99';
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

  it('shows the admin-only denial message without navigating (FR-S01-12)', () => {
    component.option = '8';
    component.submit();

    httpMock.expectOne('/api/v1/menu/select').flush({
      outcome: 'adminOnly',
      message: 'No access - Admin Only option... ',
      severity: 'error',
      target: null
    });
    fixture.detectChanges();

    expect(messageText()).toBe('No access - Admin Only option...');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('shows the coming-soon message for an unmigrated option (FR-S01-15)', () => {
    component.option = '1';
    component.submit();

    const request = httpMock.expectOne('/api/v1/menu/select');
    expect(request.request.body).toEqual({ menu: 'main', option: '1' });
    request.flush({
      outcome: 'comingSoon',
      message: 'This option Account View is coming soon ...',
      severity: 'info',
      target: null
    });
    fixture.detectChanges();

    expect(messageText()).toBe('This option Account View is coming soon ...');
  });

  it('shows the not-installed message for option 11 (FR-S01-14)', () => {
    component.option = '11';
    component.submit();

    httpMock.expectOne('/api/v1/menu/select').flush({
      outcome: 'notInstalled',
      message: 'This option Pending Authorization View is not installed...',
      severity: 'error',
      target: null
    });
    fixture.detectChanges();

    expect(messageText()).toBe('This option Pending Authorization View is not installed...');
  });

  it('navigates to the target route for an enabled option (FR-S01-13)', () => {
    component.option = '1';
    component.submit();

    httpMock.expectOne('/api/v1/menu/select').flush({
      outcome: 'navigate',
      message: null,
      severity: null,
      target: { id: '01', name: 'Account View', programKey: 'COACTVWC', route: '/accounts/view' }
    });

    expect(router.navigateByUrl).toHaveBeenCalledWith('/accounts/view');
  });

  it('returns to the sign-on screen and clears the session on Exit (FR-S01-16)', () => {
    sessionStorage.setItem('carddemo.token', 't');

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('[data-testid="exit-button"]')?.click();

    expect(sessionStorage.getItem('carddemo.token')).toBeNull();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/signin');
  });

  it('limits the option input to 2 characters like the BMS map', () => {
    const input = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="option-input"]');
    expect(input?.getAttribute('maxlength')).toBe('2');
  });

  it('shows the invalid-key message for an unmapped function key (FR-S01-20)', () => {
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'F7' }));
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
