import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';

describe('AuthService (COSGN00C endpoints)', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('POST /api/auth/signon carries {userId, password} with credentials', () => {
    let result: unknown;
    service.signOn('USER0001', 'PASSWORD').subscribe((r) => (result = r));
    const req = http.expectOne('/api/auth/signon');
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBeTrue();
    expect(req.request.body).toEqual({ userId: 'USER0001', password: 'PASSWORD' });
    req.flush({ userId: 'USER0001', userType: 'U', landingTarget: '/menu' });
    expect(result).toEqual({ userId: 'USER0001', userType: 'U', landingTarget: '/menu' });
  });

  it('POST /api/auth/signoff with credentials', () => {
    service.signOff().subscribe();
    const req = http.expectOne('/api/auth/signoff');
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBeTrue();
    req.flush({ message: 'Thank you for using CardDemo application...      ' });
  });

  it('GET /api/auth/header', () => {
    service.header().subscribe();
    const req = http.expectOne('/api/auth/header');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });
});
